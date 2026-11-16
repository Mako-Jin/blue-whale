# whale-file 能力扩展（批次 26：定时任务分布式互斥）

> **已被批次 27 取代**：项目既定架构为定时任务统一由 whale-job 调度（`@ScheduledJob`），
> 本批次的表级互斥属重复建设，已在批次 27 中撤销（端口、实现、Mapper 方法与 V8 迁移均已删除）。
> 本文保留为方案评估记录。

## 一、范围与决策

文件服务有三个 `@Scheduled` 任务（回收站到期清理、存量版本清理、孤儿对象对账）。多实例部署时
每个实例都会在同一时刻执行全量扫描，重复清理/对账会放大数据库与存储压力；现有 Javadoc 只说明
「重复执行不会误删」，并未避免重复劳动。本批次增加任务级分布式互斥，同一时刻只允许一个实例执行。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 互斥范围 | ✅ | 回收站清理、存量版本清理、孤儿对账 |
| 锁实现 | ✅ | 数据库表 `file_task_lock` + 租约（`locked_until`），不依赖 Redis |
| 持锁方式 | ❌ | 不把整个任务包进一个长事务（批量任务会放大事务与回滚段） |
| 抢锁失败 | ✅ | 直接跳过本轮并打日志，不重试、不阻塞调度线程 |
| 异常释放 | ✅ | try-with-resources 保证异常/提前返回时释放 |
| 进程崩溃 | ✅ | 租约到期后可被其他实例抢占 |
| 权限/接口 | ❌ | 不新增 |
| 数据库变更 | ✅ | 新增表 `file_task_lock`（V8 迁移） |

**为什么不用 `GET_LOCK`**：命名锁与数据库会话绑定，而任务跨多条语句、每条语句可能取用不同连接；
若为此把整个批量任务包进单事务，会长时间持有连接并放大回滚段。表锁配合租约与短事务更稳妥。

## 二、设计

### 2.1 表结构（`V8__file_task_lock.sql`）

```sql
CREATE TABLE IF NOT EXISTS file_task_lock (
    lock_name    VARCHAR(64) NOT NULL COMMENT '任务锁名称',
    owner        VARCHAR(64) NOT NULL COMMENT '持有者标识（随机串，用于校验归属）',
    locked_until DATETIME    NOT NULL COMMENT '租约到期时间，过期后可被其他实例抢占',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (lock_name)
) COMMENT='定时任务分布式互斥锁表';
```

增量表由 Flyway 创建，不写入 `init.sql`（与 `audit_log`、`file_share` 口径一致）。

### 2.2 端口与实现

- 领域端口 `com.bluewhale.file.domain.lock.ScheduledTaskLock`：
  `Optional<Handle> tryAcquire(String taskName, Duration ttl)`；`Handle#close()` 释放；
- 基础设施实现 `MybatisScheduledTaskLock`：
  1. `INSERT ... ON DUPLICATE KEY UPDATE`：仅当 `locked_until < NOW()`（或首次插入）时把
     `owner`/`locked_until` 改写为当前持有者与 `NOW() + ttl`；
  2. `SELECT COUNT(*) ... WHERE lock_name = ? AND owner = ?` 校验确实持有（避免并发抢占时误判）；
  3. 释放：`DELETE ... WHERE lock_name = ? AND owner = ?`，失败仅告警；
- 锁名常量由各任务自持：`file:recycle-purge`、`file:version-cleanup`、`file:orphan-reconcile`。

### 2.3 任务接入

- 三个任务先做配置开关判断（关闭/非法配置直接返回，不触达锁表），再 `tryAcquire`；
- 未抢到锁：`log.info` 记录跳过，直接返回；
- 抢到锁：`try (Handle ignored = ...)` 包裹原执行体，结束时释放；
- 租约取值：回收站清理 30 分钟、版本清理 60 分钟、孤儿对账 2 小时；按批上限估算，超时说明任务异常，
  租约到期后允许其他实例接手。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 仓储/适配器 | 抢锁成功返回句柄；`owner` 校验失败返回空；释放按锁名与 `owner` 删除，异常仅告警 |
| 应用任务 | 配置关闭不触达锁；抢锁失败跳过业务；抢锁成功后执行并在完成/异常时释放 |
| 迁移 | 既有 Flyway 命名与破坏性语句校验通过 |
| 回归 | 既有清理/对账/版本清理用例保持通过 |

## 四、完成情况

- 迁移：新增 `V8__file_task_lock.sql`（`file_task_lock`，`CREATE TABLE IF NOT EXISTS` 幂等）；
  增量表按既有约定不写入 `init.sql`；
- 领域端口：`com.bluewhale.file.domain.lock.ScheduledTaskLock`，`tryAcquire(name, ttl)` 返回
  `Optional<Handle>`，`Handle#close()` 释放；
- 基础设施实现：`MybatisScheduledTaskLock` 以 `INSERT ... ON DUPLICATE KEY UPDATE` 抢锁，
  按「锁名 + 属主」回查确认归属，释放失败仅告警；每条语句独立提交，不包长事务；
- 任务接入：`RecyclePurgeTask`（30 分钟租约）、`VersionCleanupTask`（60 分钟）、
  `OrphanReconcileTask`（2 小时）在配置校验通过后抢锁，未抢到直接跳过，执行体由
  try-with-resources 包裹，异常时同样释放；
- `FileInfoMapper` 新增 `tryAcquireTaskLock`、`countTaskLockOwner`、`releaseTaskLock`。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
386 项单测零失败（批次 25 为 376，本轮新增 10 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 原计划的 `GET_LOCK` 与数据库会话绑定，任务跨多条语句、连接不固定，若为保证同一连接而把
  整个批量任务包进单事务，会长时间占用连接并放大回滚段；改用表锁 + 租约实现，避免该缺陷。

## 七、待做项

- 锁等待指标与告警（连续多轮未抢到锁说明单轮耗时超租约）。
- 清理/回滚审计（业务级审计表）。

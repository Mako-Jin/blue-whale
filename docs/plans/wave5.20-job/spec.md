# Wave 5.20 — whale-job「继续完成」规格说明（spec）

- 状态：待批准
- 日期：2026-10-19
- 关联需求：REQ-FUNC-006（定时任务管理）、REQ-ARCH-001（DDD/分层）、REQ-QA-003（Javadoc 门禁）
- 关联文档：`docs/plans/roadmap.md` §2.5、`docs/plans/task-breakdown.md` Wave 5.19、`docs/plans/whale-auth-backlog.md` AUTH-09

## 1. 目标

把 whale-job 从「可跑骨架」补齐为「可交付服务」，覆盖用户已确认的四项范围：

| 编号 | 轨道 | 目标 |
| --- | --- | --- |
| R1 | 调度健壮性 | 实例内互斥执行、Redisson 分布式锁防多实例重复执行、`misfire_policy` 落地（由死字段变为生效）、handler Bean 存在性校验、status 取值校验 |
| R2 | 可观测与异步化 | `JobInfoVO` 增加 `nextFireTime`；`runOnce` 改为线程池异步触发 |
| R3 | 日志归档清理 | `whale.job.log-retention-days` + `jobLogCleanupHandler`（逻辑删除过期 `job_log`）+ 种子任务 |
| R4 | 单测基线 | 新增 6 个测试类，覆盖新增/重构逻辑 |

**非目标（本轮不做）**：`job_log` 归档导出至对象存储/whale-file（依赖 whale-file，仍阻塞，保持 backlog）。

## 2. 现状与缺口（已核实）

- `JobScheduler` 依赖 `jobInfoMapper`、`jobLogMapper`、`applicationContext`；`execute(...)` 为 private 同步执行；`resolveHandler` 内联在 Scheduler；**无任何并发防重**。
- `JobInfoServiceImpl` 存 `misfirePolicy` 但调度器只读不用 → 死字段；`create/update` 不校验 `handlerBean` 是否真实存在。
- `JobInfoVO` 无下次执行时间字段。
- 测试目录 `src/test/java/.gitkeep` 为空 → 0 用例。
- `pom.xml` 无 redis/redisson；`application.yml` 无 redis 配置。
- Checkstyle：`includeTestSourceDirectory=false`（测试免 Javadoc）；main 每个类型/方法（含 private、构造器）必须有非空 Javadoc；行宽 ≤150；圈复杂度 ≤15；方法长度 ≤80。

## 3. 设计决策

### 3.1 实例内互斥 + misfire 语义（R1）

- 新增 `JobExecutionGuard`（`scheduler` 包，`@Component`），持有两个 `ConcurrentHashMap.newKeySet()`：
  - `running`：当前实例正在执行的 jobId 集合；
  - `pendingOnce`：待补跑一次标记集合。
- 方法：`tryEnter(Long)`（CAS 语义的 `Set.add` 返回布尔）、`exit(Long)`、`markPendingOnce(Long)`、`consumePendingOnce(Long)`（remove 并返回是否命中）、`isRunning(Long)`。
- misfire 触发点：cron 触发时 `tryEnter` 失败（上次执行未结束 → 重叠/错过）：
  - `DO_NOTHING` → 记录日志并跳过；
  - `FIRE_ONCE` / `SMART` → `markPendingOnce`，当前执行结束后补跑**一次**（补跑不再补偿）。
- 补跑消费：`drainPendingOnce(job)` 循环 `consumePendingOnce`，每次先 `tryEnter`，失败则 `markPendingOnce` 放回并返回，保证不无限递归、栈深有界。

### 3.2 分布式锁（R1）

- 注入 `ObjectProvider<RedissonClient>`（whale-job 引 redisson 后可用；缺依赖时降级）。
- 锁键 `lock:job:execute:{jobId}`；`lock.tryLock(0, TimeUnit.SECONDS)`（waitTime=0 立即返回，lease 用看门狗续期）。
- 语义：拿不到锁 → 说明已被其他实例执行 → **跳过本次且不补偿**；`InterruptedException` → 恢复中断位并跳过；Redisson 不可用（`getIfAvailable()==null`）→ 降级为仅实例内互斥。
- 释放：`finally` 中 `lock.isHeldByCurrentThread()` 才 `unlock()`。

### 3.3 handler 注册表（R1）

- 新增 `JobHandlerRegistry`（`@Component`），封装 `applicationContext`：
  - `resolve(String beanName)` → `JobHandler`，空名/缺 Bean/类型不符抛 `BizException`；
  - `exists(String beanName)` → boolean（供 service 层创建/更新校验）。
- `JobScheduler` 与其 service 层共用该注册表，替换 `JobScheduler.resolveHandler`。

### 3.4 可观测与异步化（R2）

- `JobInfoVO` 增 `private LocalDateTime nextFireTime;`（`@Data`）。
- `JobInfoServiceImpl.toVo` 计算：`StringUtils.hasText(cron) && CronExpression.isValidExpression(cron)` 时 `CronExpression.parse(cron).next(LocalDateTime.now())`，否则 `null`。
- `runOnce`：保留同步 `selectById` 存在性校验（不存在抛 `NOT_FOUND`），随后 `taskScheduler.execute(() -> execute(job))` 异步触发，请求线程不再阻塞。

### 3.5 日志归档清理（R3）

- 新增 `JobProperties`（`config` 包，`@ConfigurationProperties(prefix="whale.job")`）：`logRetentionDays` 默认 30。
- `WhaleJobApplication` 加 `@ConfigurationPropertiesScan` 使该配置生效。
- 新增 `JobLogCleanupHandler`（`@Component("jobLogCleanupHandler")`，实现 `JobHandler`）：删除 `job_log` 中 `start_time < now - logRetentionDays` 的记录（`@TableLogic` 逻辑删除），返回清理条数摘要。
- `init.sql` 增种子任务（id=1，`0 0 3 * * ?`，`handler_bean='jobLogCleanupHandler'`，`misfire_policy='SMART'`，status=1）。

### 3.6 依赖与配置（R1/R3）

- `whale-job/pom.xml` 增 `spring-boot-starter-data-redis` 与 `redisson-spring-boot-starter`（版本由 whale-parent 管理，不写 version）。
- `application.yml` 增 `spring.data.redis.host/port`（`${REDIS_HOST:127.0.0.1}` / `${REDIS_PORT:6379}`，不硬编码密码，密码走 `SPRING_DATA_REDIS_PASSWORD` 环境变量，同 auth 服务范式）与 `whale.job.log-retention-days: ${JOB_LOG_RETENTION_DAYS:30}`。

## 4. 变更清单（精确到文件/符号）

### 新增（main）

| 文件 | 关键符号 |
| --- | --- |
| `.../job/config/JobProperties.java` | `JobProperties`、`logRetentionDays` |
| `.../job/scheduler/JobExecutionGuard.java` | `JobExecutionGuard`、`tryEnter`、`exit`、`markPendingOnce`、`consumePendingOnce`、`isRunning` |
| `.../job/scheduler/JobHandlerRegistry.java` | `JobHandlerRegistry`、`resolve`、`exists` |
| `.../job/scheduler/handler/JobLogCleanupHandler.java` | `JobLogCleanupHandler.execute` |

### 修改（main）

| 文件 | 变更 |
| --- | --- |
| `.../job/scheduler/JobScheduler.java` | 注入 `JobHandlerRegistry`/`JobExecutionGuard`/`ObjectProvider<RedissonClient>`；`execute(JobInfo)` 包可见（guard+lock+misfire+补跑）；`doExecute(JobInfo)` 包可见（原落日志执行体）；`runOnce` 异步；新增 `acquireLock`/`releaseLock`/`handleOverlap`/`drainPendingOnce`；删除 `resolveHandler` |
| `.../job/service/impl/JobInfoServiceImpl.java` | 注入 `JobHandlerRegistry`；`create`/`update` 增 `validateHandler`；`toVo` 增 `nextFireTime` 计算 |
| `.../job/vo/JobInfoVO.java` | 增 `nextFireTime` 字段 |
| `.../job/WhaleJobApplication.java` | 增 `@ConfigurationPropertiesScan` |
| `.../job/pom.xml` | 增 redis + redisson 依赖 |
| `.../job/src/main/resources/application.yml` | 增 `spring.data.redis`、`whale.job.log-retention-days` |
| `.../job/src/main/resources/db/init.sql` | 增清理任务种子行 |

### 新增（test）

`.../job/scheduler/JobExecutionGuardTest.java`、`JobHandlerRegistryTest.java`、`JobSchedulerTest.java`、`.../handler/JobLogCleanupHandlerTest.java`、`.../handler/DemoJobHandlerTest.java`、`.../service/impl/JobInfoServiceImplTest.java`

### 文档

`docs/plans/task-breakdown.md`（追加 Wave 5.20）、`docs/plans/roadmap.md`（勾选）、`docs/devlog/2026-10-19.md`（追加 Wave 5.20）、`docs/reviews/2026-10-19-wave5.20-job-completion.md`（新增审查报告）

## 5. 验证方案

1. `mvn -pl whale-backend/whale-services/whale-job test`（禁用沙箱，`JAVA_HOME`→JDK21，`-Dmaven.repo.local=D:\repository\maven`）——预期新增用例全绿，checkstyle error 级通过。
2. `mvn clean install -T 1C`（全量反应堆）——预期 28 模块 SUCCESS。
3. 启动冒烟：`bluewhale_job` 建库并 source `init.sql`；以 `SPRING_DATA_REDIS_PASSWORD` 等环境变量启动 8082，`ApplicationReadyEvent` 应加载种子任务并注册；调用 `/job/info/page`（经网关 8080）返回 `code=0` 且含 `nextFireTime`；重复实例/并发触发路径日志显示互斥生效（Distributed 锁键存在）。
4. 文档与提交：devlog/roadmap/task-breakdown/审查报告更新后 `git push origin HEAD`（Gitee）。

## 6. 风险与回退

- Redisson 缺失 → `ObjectProvider` 降级为实例内互斥，不阻断启动。
- 补跑递归 → 由 `consumePendingOnce` 一次性消费 + 栈深有界保证。
- 逻辑删除清理不物理删数据 → 可通过 DBA 手工归档，满足 AUTH-09 的本地保留策略部分。
- 回退：全部为新增文件 + 局部改动，`git revert` 单提交即可回退。

## 7. 待批准事项

- 确认上述四项范围与非目标（归档导出继续阻塞）无异议后，按 `tasks.md` 执行。

# whale-file 能力扩展（批次 27：定时任务迁入 whale-job）

## 一、范围与决策

批次 26 为三个 `@Scheduled` 任务加了 `file_task_lock` 表级互斥，但项目既定架构是「定时任务统一由
whale-job 调度」：业务服务保留任务方法，用 `@ScheduledJob` 声明，由调度中心经内网 HTTP 触发，
具备执行锁、重试、告警、流水与日志回捞。whale-file 的三个任务是在 2026-11-08 迁移收口之后新增的，
从未进入迁移清单，因此批次 26 的互斥属于重复建设。

本批次按既定架构迁移三个任务，并撤掉批次 26 的互斥实现。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| `file.recycle-cleanup` | ✅ | 回收站到期清理 |
| `file.version-cleanup` | ✅ | 存量超限版本清理 |
| `file.orphan-reconcile` | ✅ | 孤儿对象对账 |
| 注解 | ✅ | `@Scheduled` → `@ScheduledJob`，方法返回执行摘要 |
| whale-job 种子 | ✅ | `V14__seed_file_jobs.sql`，`init.sql` 同步同源 |
| 部署 | ✅ | `bw-file` 注入 `WHALE_SCHEDULE_*` |
| 批次 26 互斥 | ❌ 撤销 | 删除端口/实现/Mapper 方法与 `V8__file_task_lock.sql`（未在任何环境执行） |
| 业务逻辑/配置项 | ✅ 不变 | cron 占位符、开关与保留期口径保持原样 |
| 数据库（file 库） | ✅ 无 | 删除未执行的 V8 |

**红线**：删除旧 `@Scheduled`，避免 `whale.schedule.local-enabled=true` 时双跑；`@EnableScheduling`
在 file 服务已无其他用途，一并移除（`@ScheduledJob` 的本地模式使用 starter 自带调度器）。

## 二、设计

- 三个任务改为返回 `String` 执行摘要，直接落 `job_info` 对应的 `job_log.result`：
  - 回收站清理：`skipped: purge disabled` / `skipped: retention-days<=0` / `purged=n`；
  - 版本清理：`skipped: cleanup disabled` / `skipped: max-versions<=0` / `pruned=n`；
  - 孤儿对账：`skipped: orphan reconcile disabled` / `orphanObjects=n, danglingMetadata=n, deleted=n`。
- 配置关闭时**返回摘要而不是静默 return**，让运维能从执行流水区分「未开启」与「无数据」；
- 整轮异常保持上抛（与既有一致：单条失败只计数），由调度中心记录失败并按 `max_retry` 重试；
- whale-job 种子：`executor_app=whale-file`、`handler_bean=httpJobHandler`、
  `misfire_policy=DO_NOTHING`、`max_retry=1`，超时按任务体量设置（30 分钟 / 60 分钟 / 2 小时）；
- 撤销批次 26：删除 `ScheduledTaskLock` 端口、`MybatisScheduledTaskLock`、Mapper 三个锁方法与
  `V8__file_task_lock.sql`。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 任务 | 三个任务关闭时返回 `skipped` 摘要、开启时返回计数摘要；异常按原语义上抛 |
| 归档 | 删除批次 26 的锁测试与锁相关断言 |
| whale-job | 迁移脚本命名/递增校验通过；种子三行与 `init.sql` 同源 |
| 回归 | file 模块全量单测、whale-job 单测通过 |

## 四、完成情况

- `RecyclePurgeTask#purgeExpired`、`VersionCleanupTask#cleanup`、`OrphanReconcileTask#reconcile`
  改为 `@ScheduledJob`（`file.recycle-cleanup` / `file.version-cleanup` / `file.orphan-reconcile`），
  cron 继续读取 `whale.file.*` 占位符，方法返回执行摘要（`purged=n` / `pruned=n` /
  `orphanObjects=n, danglingMetadata=n, deleted=n` 或 `skipped: ...`）；
- 孤儿对账的介质列举失败由「记录指标后静默返回」改为上抛，调度中心可据此标记失败并告警；
- 移除 `WhaleFileApplication` 的 `@EnableScheduling`（服务内已无本地 `@Scheduled`）；
- 撤销批次 26：删除 `ScheduledTaskLock`、`MybatisScheduledTaskLock` 及其测试、
  `FileInfoMapper` 三个锁方法、`V8__file_task_lock.sql`；file 库无新增表；
- whale-job：新增 `V14__seed_file_jobs.sql`（三行，按 `job_code` 幂等），`init.sql` 同步；
- 部署：`bw-file` 注入 `WHALE_SCHEDULE_*` 与 `file-joblogs` 命名卷。

## 五、验证结果

- `mvn -o test`（`whale-service-file`）→ 13 个模块 BUILD SUCCESS，383 项单测零失败
  （批次 26 为 386，删除锁测试后净减 3），checkstyle 0 violation；
- `mvn -o test`（`whale-job`）→ BUILD SUCCESS，102 项单测零失败，含迁移脚本命名/递增与
  `init.sql` 同源校验；
- 全仓检索确认 `ScheduledTaskLock` / `file_task_lock` 仅存在于历史方案文档。

## 六、已修正问题项

- 批次 26 以表级互斥替代调度中心，与既定架构重复建设；本批次撤销并回归 `@ScheduledJob` 通道。
- 孤儿对账列举失败原为静默返回，调度侧会记为成功；改为上抛后失败可见。

## 七、待做项

- whale-file 多实例部署验证（执行器注册、远程触发、日志回捞）。

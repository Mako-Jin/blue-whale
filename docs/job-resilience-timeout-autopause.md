# whale-job 韧性增强：任务超时中断 / 连续失败自动暂停 / 优雅停机

> 对应扩展路线图第 2、3、4 项。目标是封住生产事故的三个常见来源：慢任务长期占线程、
> 坏任务按 cron 无限失败刷屏、进程停机把在途任务连同执行流水一起丢在半路。

## 1. 需求项

| 编号 | 能力 | 现状问题 |
|---|---|---|
| R-2 | 任务级超时 + 调度侧强制中断 | `handlerParam.timeoutMillis` 只是 HTTP 客户端读超时；对端可能仍在跑，调度线程也无法中断执行 |
| R-3 | 连续失败自动暂停（熔断） | `markFailure` 只告警不停任务，一个坏任务会按 cron 无限失败、无限告警、无限重试 |
| R-4 | 执行器优雅停机 + 孤儿日志对账 | 业务服务停机时在途任务随进程退出被杀，`job_log.status` 永远停在 `0`（执行中） |

约束：保持既有任务零配置可用（超时、熔断默认关闭），不改动 DAG / 分片 / 重试的既有语义。

## 2. 数据库变更

新增迁移脚本 `V16__job_resilience.sql`（增量、非破坏性，仅 `ALTER TABLE ... ADD COLUMN`）：

| 列 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `job_info.timeout_seconds` | `INT NOT NULL` | `0` | 单次执行超时秒数；`0` 表示沿用全局默认，全局也为 `0` 时不限制 |
| `job_info.fail_threshold` | `INT NOT NULL` | `0` | 连续失败自动暂停阈值；`0` 表示关闭熔断 |
| `job_info.fail_count` | `INT NOT NULL` | `0` | 当前连续失败次数，成功执行后清零 |

`timeout_seconds` 与 `fail_threshold` 同时加入新增/更新 DTO 与视图对象；更新请求未传时保留原值
（与 `maxRetry` 等字段口径一致）。`fail_count` 只由调度引擎维护，不开放写接口。

## 3. 任务级超时（R-2）

### 3.1 执行模型

- 新增独立的执行线程池 `whale-job-task-*`（`whale.job.executor.task-pool-size`，默认 8，守护线程）。
  超时等待发生在调度线程上，实际执行在任务线程池里，两者不共用线程，避免「等自己」死锁。
- 超时解析优先级：`job_info.timeout_seconds > 0` → 用它；否则 `whale.job.executor.task-timeout-seconds`
  （默认 `0`，即不限制）。
- 未配置超时：仍在调度线程内直接调用执行器（与改造前完全一致，零行为变化）。
- 配置超时：`Future.get(timeout)` 等待；超时后 `future.cancel(true)` 中断任务线程，并抛出
  `JobTimeoutException`。

### 3.2 失败语义

- 超时按失败终态落库（`status=-1`），`error_msg` 为「执行超时：超过 N 秒未完成，已请求中断」。
- **超时不重试**：超时说明单次执行已超出预期，重试只会放大对端压力并继续占线程。
- 告警区分类型：`JobAlertNotifier#notifyTimeout` 默认退化为失败告警，Webhook 实现使用
  「任务执行超时」摘要与 `level=ERROR`，运维可按消息区分超时与异常。
- 中断只是「尽力而为」：`cancel(true)` 给执行线程打中断标记，能否真正停下取决于执行器是否响应中断
  （如 HTTP 任务可能在 socket 读上阻塞到客户端超时）。因此 HTTP 任务仍应保留
  `handlerParam.timeoutMillis`，两者配合使用：客户端超时负责尽早断开，任务级超时负责兜底。

### 3.3 线程与租户

任务线程池执行前把调度线程的 `TenantContext` 复制进工作线程，结束后恢复，避免租户上下文丢失
（`JobExecutionContext` 已显式携带租户，双重保障）。

## 4. 连续失败自动暂停（R-3）

- 每次「一次完整执行」结束（全部分片执行完）后判定：存在失败分片则 `fail_count = fail_count + 1`，
  全部成功则清零；自增使用 SQL 表达式，避免多实例并发覆盖。
- `fail_threshold > 0` 且 `fail_count >= fail_threshold` 时自动暂停：
  1. 条件更新 `status=0`（仅当当前仍为运行中，保证多实例只有一个执行生效）；
  2. 取消本实例调度并广播其余实例；
  3. 写一条 `audit_log`，`username=SYSTEM`、`module=whale-job`、`operation=任务连续失败自动暂停`，
     与人工「启停定时任务」区分；
  4. 通过 `JobAlertNotifier#notifyAutoPaused` 发一次「已自动暂停」告警（`job_alert.level=WARN`）。
- 恢复：管理端把任务重新启用（`PUT /job/info/{id}/status`，`status=1`）时 `fail_count` 清零，
  否则恢复后第一次失败就会再次触发熔断。若需人工确认前保持暂停，可不启用，仅重置失败计数由
  下次启用隐式完成。
- 计数粒度是「一次执行」而非「一次分片」：分片任务部分失败只加 1，避免一次执行把阈值打满。

## 5. 优雅停机与孤儿日志（R-4）

### 5.1 业务侧执行器

- `RemoteJobRegistry` 维护在途执行计数（进入 `invoke` 自增，退出自减），提供 `awaitIdle(timeout)`。
- `JobExecutorRegistrar#destroy` 顺序调整为：**先注销（停止被新派发）→ 等待在途执行清零（有界）
  → 关闭心跳线程**。等待时长 `whale.schedule.executor.shutdown-await-seconds`，默认 30 秒。
- 建议业务服务同时开启 `server.shutdown=graceful`，让 Web 容器在关闭 Bean 前先排空请求；
  两者叠加才能覆盖「请求已到达、任务正在跑」的窗口。

### 5.2 调度侧对账

- `JobScheduler` 启动（`ApplicationReadyEvent`）时扫描 `job_log`：`status=0` 且
  `start_time < now - whale.job.orphan-log-timeout-minutes`（默认 120 分钟）的记录，
  回写 `status=-1`、`end_time=now`、`error_msg=实例已退出，执行中断（启动对账补记）`。
- 阈值必须大于最长合法执行时长，否则会误判执行中的长任务；默认 120 分钟，可按业务调整，
  配置为 `0` 关闭该对账。

## 6. 配置项

| 配置 | 默认 | 作用 |
|---|---|---|
| `whale.job.executor.task-pool-size` | `8` | 超时执行线程池大小 |
| `whale.job.executor.task-timeout-seconds` | `0` | 全局默认任务超时（0=不限制） |
| `whale.job.orphan-log-timeout-minutes` | `120` | 启动对账判定「执行中日志已中断」的阈值（0=关闭） |
| `whale.schedule.executor.shutdown-await-seconds` | `30` | 业务执行器停机等待在途任务的秒数 |

## 7. 测试要点

- 超时：执行器睡眠 5s、任务超时 1s → 1s 左右返回，`status=-1` 且 `error_msg` 含「执行超时」，
  不进入重试。
- 未配置超时：执行器仍在调用线程执行（线程名不含 `whale-job-task-`），行为不变。
- 熔断：阈值 2，连续两次失败后 `status` 置 0、告警一次、审计一条；成功一次后 `fail_count` 清零。
- 启用重置：`changeStatus(id, 1)` 后 `fail_count=0`。
- 停机等待：`RemoteJobRegistry` 在途计数归零前 `awaitIdle` 阻塞、归零后立即返回。
- 孤儿日志：启动对账把 `status=0` 且早于阈值的记录回写失败。

## 8. 上线与回滚

- 上线：先执行 Flyway（`V16` 仅加列，旧代码可继续运行）；新代码发布后默认行为与之前一致，
  需要超时/熔断的任务再逐条配置。
- 回滚：代码回滚即可，新增列无副作用；已自动暂停的任务需人工重新启用。
- 注意：`fail_count` 为长期计数，启用熔断前建议先观察一段 `job_execute_total{status="failure"}`，
  选择合适的阈值（默认建议 3~5）。

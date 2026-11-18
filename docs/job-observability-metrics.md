# 定时任务可观测性：Metrics 与健康检查

> 日期：2026-11-18 ｜ 服务：`whale-job`（8082）｜ 范围：扩展路线图第 1 项
> 关联：`docs/reviews/2026-11-18-job-review-remediation.md`

## 一、需求项

当前 `actuator` 与 `micrometer-registry-prometheus` 已就绪、`health,info,prometheus` 已暴露，但：
调度执行成败/耗时、调度延迟、线程池水位、执行器在线数与注册次数、广播收发与降级全部只能靠日志排查。
本轮补齐自定义指标与一个调度健康项，**不改表结构、不改调度语义**。

## 二、指标清单

| 指标 | 类型 | 标签 | 埋点位置 | 说明 |
| --- | --- | --- | --- | --- |
| `job_execute_total` | Counter | `job`,`status=success\|failure` | `JobScheduler#executeShard` | 每次最终执行结果计一次（重试耗尽也只计一次） |
| `job_execute_duration_seconds` | Timer | `job` | `JobScheduler#executeShard` | 含重试与等待时长的端到端耗时 |
| `job_schedule_delay_seconds` | Timer | `job` | `JobScheduler#execute` | 实际触发时间 - cron 预期时间，负数归零；首次触发不采样 |
| `job_schedule_pool_size` | Gauge | - | `JobScheduler#init` | 调度线程池当前线程数 |
| `job_schedule_pool_active` | Gauge | - | `JobScheduler#init` | 活跃线程数 |
| `job_schedule_pool_queue_size` | Gauge | - | `JobScheduler#init` | 排队任务数 |
| `job_scheduled_jobs` | Gauge | - | `JobScheduler#init` | 本实例已注册调度数 |
| `job_executor_online` | Gauge | - | `JobExecutorServiceImpl` | 心跳未过期的在线执行器总数 |
| `job_executor_register_total` | Counter | `action=register\|deregister` | `JobExecutorServiceImpl` | 注册与注销次数 |
| `job_executor_dispatch_mismatch_total` | Counter | `reason=not-in-nacos\|mismatch` | `JobExecutorServiceImpl#dispatchableAddresses` | 注册表与 Nacos 不一致的回退次数 |
| `job_broadcast_total` | Counter | `action`,`result=success\|failure` | `JobScheduleBroadcaster#publish` | 调度失效广播发布结果 |
| `job_broadcast_receive_total` | Counter | `result=applied` | `JobScheduleBroadcaster#handleMessage` | 成功应用到本地的广播数 |
| `job_broadcast_fallback_total` | Counter | - | `JobScheduleBroadcaster` | 无 Redisson / 订阅失败 / 发布时主题缺失的降级次数 |

`management.metrics.distribution.percentiles-histogram` 对执行耗时与调度延迟开启直方图，供后续 P95 查询。

## 三、健康检查

`GET /actuator/health` 新增 `jobScheduler` 组件（`JobSchedulerHealthIndicator`）：

- `UP`：调度线程池已初始化且未关闭；附带 `scheduledJobs`、`poolSize`、`activeThreads`、`queueSize`。
- `DOWN`：线程池未初始化或已关闭。

Redis、数据库沿用现有自动健康项；广播降级属可接受运行态，不设为 DOWN，由 `job_broadcast_fallback_total` 指标观察。

## 四、实现约定

- 统一指标名与标签键放在 `JobMetricNames`，避免各埋点字符串漂移。
- `job` 标签优先取任务名，任务名为空时回落任务ID，避免标签值为空。
- 指标采集不得影响调度主流程：所有埋点不抛异常（Micrometer 本身不抛，计数器取值全部为内存读取；仅在线执行器 Gauge 在抓取时查一次注册表）。
- 在线执行器 Gauge 在无租户上下文时统计全局，符合「平台视角」语义。
- 调度延迟以「上次触发时间 → cron 下一次预期时间」计算，属于近似值，用于趋势观察而非精确对账。

## 五、非目标（本轮不做）

- 指标驱动的自动扩缩容、熔断、自动暂停（第 2/3 项）。
- 依赖拓扑、历史统计、批量操作等管理端能力（第 5~8 项）。
- 引入新的监控组件或依赖（沿用 actuator + Prometheus）。

## 六、验证

- 单测：`whale-service-job` 全量 `mvn -o test` → **110 项、0 失败**，`BUILD SUCCESS`，checkstyle 0 violation。
  - `JobSchedulerTest`：成功/失败各计一次 `job_execute_total`；
  - `JobExecutorServiceImplTest`：注册计一次 `job_executor_register_total{action=register}`；
  - `JobSchedulerHealthIndicatorTest`：线程池未就绪为 DOWN，就绪时 UP 且携带调度水位。
- 人工核验（部署后）：`/actuator/prometheus` 可检索到上述指标族，`/actuator/health` 出现 `jobScheduler` 组件。

## 七、实现说明

- Spring Boot 4 的健康契约已迁到 `org.springframework.boot.health.contributor`，健康项与测试按新包引用。
- 调度延迟为近似值：以「上次触发时间 → cron 下一次预期时间」计算，首次触发不采样。
- 执行耗时统计覆盖重试全过程，`job_execute_total` 只计最终结果，避免重试放大失败率。
- 在线执行器 Gauge 在 Prometheus 抓取时查询一次注册表（表规模等于实例数），无租户上下文时统计全局。

# 检查报告：whale-job 多实例调度失效广播

> 日期：2026-10-26 ｜ 服务：`whale-job`（8082）｜ 结论：**PASS（双实例实机验证通过）**
> 关联：[todo-inventory 2026-10-23](../plans/archive/todo-inventory-2026-10-23.md) 第 3 项残余、`next-steps` §五「多实例失效广播」

---

## 一、问题与范围

`whale-job` 的调度注册表（`scheduledFutures`）是**进程内**状态。多实例部署时，任一端通过 `/job/info` 增删改、启停任务或调整依赖，只影响当前实例：其余实例仍按旧 cron 触发，甚至继续触发已在库中置为「已停止」的任务（分布式锁只能防重复执行，不能阻止已停任务被触发）。

本轮补齐「变更广播」：任务调度变更后经 Redisson Pub/Sub 通知其余实例重新注册或取消本地调度。执行链路、锁、重试、分片、依赖编排行为不变。

## 二、实现

| 文件 | 变更 |
|---|---|
| `job/scheduler/JobScheduleBroadcaster.java`（新增） | Redisson `RTopic` 主题 `job:schedule:invalidate`；消息体 `实例标识\|动作\|任务ID`（纯文本避免跨版本序列化差异）；`RESCHEDULE` / `CANCEL` 两种动作；实例标识跳过自身回声；Redisson 不可用或订阅/发布异常时仅告警，保留单实例语义 |
| `job/scheduler/JobScheduler.java` | 注入广播器；`init()` 注册本地消费者；`reschedule` / `cancel` 拆为「本地执行 + 广播」；`applyBroadcast` 处理远端事件复用同一本地逻辑，避免回声重复发布 |
| `JobSchedulerTest` / `JobSchedulerChainTest` | 构造器补广播器（未初始化主题 → 广播空操作，不改变既有断言） |
| `JobScheduleBroadcasterTest`（新增，5 用例） | Redisson 缺失降级、消息体格式、跳过自身回声并应用远端动作、非法消息忽略、关闭注销监听 |

设计取舍：

- **发布方先本地生效再广播**，广播失败不影响本次 CRUD；接收方只做幂等的「取消/重注册」，重复消息无副作用。
- **不用数据库轮询/版本号**：任务定义变更频率低，Pub/Sub 足够；库仍是事实源，接收方重注册时会重新读取最新定义（已停止的任务重注册后不会注册 cron）。
- **不做全局对账扫描**：实例在 Redis 断连窗口内可能漏收消息，此时分布式锁仍保证不重复执行，且下次任何一次变更会重新对齐；为避免过度设计本轮不引入周期全量比对。

## 三、验证

### 3.1 单元测试

`mvn -o -pl whale-backend/whale-services/whale-job -am package` **通过**；whale-job 共 **77** 个用例、**0** 失败（含新增 5 个广播用例）。

### 3.2 双实例实机验证

环境：MySQL 8.0.26 / Redis 5.0.14 / Nacos 2.4.0；同一 jar 起两实例——A `8082`（`instanceId=a5b12fc7…`）、B `8098`（`instanceId=26f604da…`），两实例均订阅主题并注册 `jobId=1/2`。

| 步骤 | 结果 |
|---|---|
| A 调用 `PUT /job/info/1/status {"status":0}` | A 本地「定时任务已取消: jobId=1」；B 收到 `收到调度失效广播: jobId=1, action=CANCEL` 并取消本地调度 |
| A 调用 `PUT /job/info/1/status {"status":1}` | A 本地重新注册；B 收到 `action=RESCHEDULE` 并按库中最新 cron `0 0 3 * * ?` 重新注册 |
| A 日志是否出现自身回声 | 未出现「收到调度失效广播」，自身消息被实例标识过滤 |
| 任务状态复原 | `job_info.id=1` 已恢复 `status=1`，与验证前一致 |

## 四、环境前置（非代码变更）

本地 `bluewhale_job` 库为早期初始化状态：缺 `job_dependency` / `job_alert` 两表，`job_info` / `job_log` 缺 `tenant_id`，导致新 jar 启动时 `Unknown column 'tenant_id'` 失败。已按 `db/init.sql` + `db/migration-5.54-tenant-business.sql` 口径补齐（建表 + `tenant_id` 列与索引），属本机环境修复，仓库脚本无需改动。

## 五、结论与遗留

多实例调度失效广播已落地并实机验证通过，`todo-inventory` 第 3 项调度侧残余关闭。

遗留（明确不在本轮范围）：

- `whale-auth` 权限缓存的「多实例失效广播 / Canal 演进」是另一条独立残余（见 `next-steps` §五），本轮未涉及。
- Redis 断连期间的漏收消息依赖「下一次变更重对齐」兜底，如需强一致可在规模化后引入周期对账。

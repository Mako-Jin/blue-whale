# 定时任务迁移到 whale-job 方案

> 状态：实施中（27 个目标已迁 8 个，见 §8/§9） ｜ 编写：2026-11-08 ｜ 涉及：`whale-spring-boot-starter`、`whale-job`、17 个业务模块

## 0. 先澄清一个前提

**不是把业务代码搬进 `whale-job` 服务。** `whale-job` 是独立库（`bluewhale_job`）、独立部署，搬进去会跨库直连，破坏服务边界（ArchUnit 也会拦）。

正确形态是：**`whale-job` 只做「什么时候跑」（cron / 重试 / 分片 / 依赖 / 告警 / 日志），业务服务保留「跑什么」（业务逻辑 + 自己的库）**，两者之间用内网 HTTP 触发。

```
whale-job (8082)                       业务服务 (crm/oa/doc/...)
  job_info: cron + handler_bean    →    POST /internal/job/{code}/run
  JobScheduler: 锁/重试/分片/日志         LocalJobRegistry → 原方法体
  job_log / job_alert ←─────────────────  返回 {code:0, data:"摘要"}
```

好处：调度能力（多副本互斥、失败重试、执行日志、告警、依赖编排、手工触发）集中一处；业务代码零搬迁，只把 `@Scheduled` 换成可远程触发的注解。

**这套拓扑就是 xxl-job 的拓扑**，不是简化版：xxl-job 的 `admin` 也是独立部署、独立库，业务代码同样以 SDK（`xxl-job-core`）+ `@XxlJob` 的形式留在业务应用里，admin 从不加载业务类。两者的差别不在「独立与否」，而在中间有没有**执行器注册与寻址**这一层——见 §3.4，这是本方案必须补齐的部分。

---

## 1. 现状盘点

| 项 | 实测值 |
|---|---|
| 业务侧 `@Scheduled` | **37 个方法**（29 个 cron、8 个 fixedDelay），分布在 **17 个模块** |
| 已有的调度服务 | `backend/whale-services/whale-job`，端口 8082，库 `bluewhale_job`，表 `job_info / job_log / job_alert / job_dependency` |
| 调度能力 | Redisson 锁 `lock:job:execute:{jobId}`、misfire 策略、失败重试（≤5）、分片（≤16）、依赖链、多实例广播 + 5 分钟 reconcile、Prometheus 指标 |
| 手工/接口 | `POST /job/info/{id}/run` 手动触发、启停、日志分页、告警分页、依赖配置 |
| 前端 | `frontend/admin/src/pages/job` 已有管理页，无需新做 |
| 容器化 | **`whale-job` 尚未进 `deploy/backend/docker-compose.services.yml`**，需补 |
| 执行器 | 只有 `DemoJobHandler / JobLogArchiveHandler / JobLogCleanupHandler`，**没有通用 HTTP 执行器，也没有执行器注册表**（`job_group` 只是分类标签，非执行器分组） |

当前 37 个任务全部靠进程内 `@Scheduled`，无分布式锁 → 多副本 N 倍执行。

---

## 2. 先分类：不是 37 个都该迁

这是本方案最重要的一节。**「进程内工作泵」迁到远程调度是反模式**——它们要的是「每个实例各跑自己的」，而不是「全局只跑一次」；秒级精度走远程 HTTP 只会放大延迟和故障面。

### 2.1 应保留在进程内（10 个，不迁）

| 任务 | 周期 | 为什么保留 |
|---|---|---|
| `MesOutboxDispatcher.dispatchPending` | 30s | 事务性发件箱泵，依赖本进程刚写的事务 |
| `MessageCallbackDispatcher.dispatch` | 15s | 同上（回执回调 outbox） |
| `PayNotifyServiceImpl.retryDueTasks` | 30s | 支付通知重试泵，延迟敏感 |
| `MailSyncServiceImpl.scheduledSync` | 5min | 邮箱增量拉取，各实例拉自己的账户 |
| `LuceneMailSearchServiceImpl.flush` | 30s | 操作**内存 IndexWriter**，跨进程无意义 |
| `AuthPermissionCacheReconcileJob.reconcile` | 30s | 本地缓存与库对账，本质是实例自愈 |
| `JobScheduleReconcileJob.reconcile` | 5min | job 服务自身的多实例调度重对齐 |
| `MusicPlayCountFlushScheduler.flush` | 5min | 内存计数器刷盘 |
| `VideoPlayCountFlushScheduler.flush` | 5min | 同上 |
| `SignTaskReconcileJob.reconcile` | 60s | 电子签外部状态轮询，60s 远程调度成本过高，靠幂等兜底 |

**这 10 个的正确解法不是搬走，而是「每实例都跑 + 保证幂等」**：现状大体已满足（outbox 有事件键、计数是累加但不重复、缓存对账幂等）。遗留风险是重复 RPC 与重复计数，建议后续单独给「读改写」型加锁，不纳入本次迁移。

### 2.2 应迁移到 whale-job（27 个）

**按「副作用是否全局唯一」判断：只要一次执行会改动全局业务状态、且多跑一次没有收益，就该由调度中心保证只跑一次。**

| # | 模块 | 任务 code（建议） | 类.方法 | cron |
|---|---|---|---|---|
| 1 | crm | `crm.analytics.snapshot` | `AnalyticsSnapshotJob.generateSnapshots` | `0 0 2 * * ?` |
| 2 | crm | `crm.analytics.action-plan` | `AnalyticsSnapshotJob.generateActionItems` | `0 30 2 * * ?` |
| 3 | crm | `crm.pool.recycle` | `CrmPoolRecycleJob.recycle` | `0 0 2 * * ?` |
| 4 | crm | `crm.ar.snapshot` | `CrmArSnapshotJob.sync` | `0 30 2 * * ?` |
| 5 | crm | `crm.contract.sync` | `CrmContractSyncJob.reconcile` | `0 0 3 * * ?` |
| 6 | crm | `crm.order.push` | `CrmOrderSyncJob.pushPending` | `0 * * * * ?` |
| 7 | crm | `crm.order.reconcile` | `CrmOrderSyncJob.reconcile` | `0 */10 * * * ?` |
| 8 | crm | `crm.return.push` | `CrmReturnSyncJob.pushPending` | `0 */5 * * * ?` |
| 9 | crm | `crm.return.reconcile` | `CrmReturnSyncJob.reconcile` | `0 0/30 * * * ?` |
| 10 | crm | `crm.approval.reconcile` | `CrmApprovalReconcileJob.reconcile` | `0 */2 * * * ?` |
| 11 | oa | `oa.purchase.push` | `PurchaseOrderPushJob.push` | `0 */10 * * * *` |
| 12 | oa | `oa.expense.payable-push` | `ExpensePayablePushJob.push` | `0 */10 * * * *` |
| 13 | oa | `oa.approval.auto-urge` | `ApprovalMaintenanceJob.autoUrge` | `0 0 */2 * * *` |
| 14 | oa | `oa.approval.reconcile` | `ApprovalMaintenanceJob.reconcile` | `0 */30 * * * *` |
| 15 | oa | `oa.trip.report-reminder` | `TripReportReminderJob.remind` | `0 0 9 * * *` |
| 16 | lms | `lms.urge.daily` | `LmsUrgeJob.urgeDaily` | `0 0 9 * * *` |
| 17 | party | `party.notice.scan` | `PartyNoticeJob.scan` | `0 0 9 * * *` |
| 18 | pdm | `pdm.change.effective` | `PdmChgEffectiveJob.scheduledRun` | `0 0 2 * * ?` |
| 19 | audit | `audit.log.retention` | `LogRetentionScheduler.cleanExpiredLogs` | `${whale.audit.retention.cron}` |
| 20 | archive | `archive.sync` | `ArchiveSyncScheduler.sync` | `0 */5 * * * *` |
| 21 | archive | `archive.file-lock-reconcile` | `ArchiveFileLockReconcileScheduler.reconcile` | `0 30 3 * * *` |
| 22 | doc | `doc.recycle.cleanup` | `DocRecycleCleanupJob.cleanup` | `0 30 3 * * *` |
| 23 | doc | `doc.orphan-attachment.cleanup` | `DocOrphanAttachmentCleanupJob.cleanup` | `0 30 4 * * *` |
| 24 | music | `music.play-history.cleanup` | `PlayHistoryCleanupScheduler.purgeExpiredHistory` | `0 0 3 * * ?` |
| 25 | video | `video.license-expiry.offline` | `MovieLicenseExpiryScheduler.offlineExpired` | `0 10 3 * * ?` |
| 26 | video | `video.watch-history.cleanup` | `WatchHistoryCleanupScheduler.cleanup` | `0 40 4 * * ?` |
| 27 | auth | `auth.password.expiry-notice` | `PasswordExpiryNoticeJob.notifyExpiringPasswords` | `0 0 2 * * ?` |

> 迁移顺序建议：**先「只读/可重入」**（18/20/21/22/23/24/25/26 清理与对账类）→ **再「快照/生成类」**（1/2/3/4/5/10/19/27）→ **最后「有外部副作用的推送类」**（6/7/8/9/11/12/13/14/15/16/17）。推送类一旦重复会向 ERP/MES 重复推单，必须最后动、且逐个验证幂等。

---

## 3. 迁移形态：加一个注解，双模式可回滚

### 3.1 业务侧：`@Scheduled` → `@ScheduledJob`（每处只改 1 行）

在 `whale-spring-boot-starter` 新增：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduledJob {
    /** 全局唯一任务编码，job_info.handler_param 里引用 */
    String code();
    /** cron，支持 ${} 占位符 */
    String cron();
    /** 单次执行超时（秒），用于注册表返回给调度方参考 */
    int timeoutSeconds() default 300;
}
```

配套 `ScheduledJobRegistrar`（`SmartInitializingSingleton`）扫描容器内所有带 `@ScheduledJob` 的方法：

- **`whale.schedule.local-enabled=true`（默认，等于今天的行为）**：注册到本地 `ThreadPoolTaskScheduler`，与 `@Scheduled` 完全等价；
- **`whale.schedule.local-enabled=false`（迁移后）**：不注册本地调度，只登记到 `RemoteJobRegistry`（`code → Method`）。

`RemoteJobRegistry` + `JobTriggerController`（`POST /internal/job/{code}/run`）由 starter 提供，受 `whale.schedule.trigger-enabled`（**默认 false**）控制开关，只有迁移的服务显式打开。

**为什么这样设计**：① 每个任务只改一行注解，27 个任务的改动机械且可 review；② 一个配置项就能整体回滚到进程内调度；③ 同一份代码同时具备「本地跑」和「被远程触发」两种能力，灰度期可双跑对照。

**红线**：迁移提交内必须**同时删除**旧 `@Scheduled`，否则 local 模式下会双跑。

### 3.2 调度侧：`whale-job` 新增通用 HTTP 执行器

```java
@Component("httpJobHandler")   // Bean 名即 job_info.handler_bean
public class HttpJobHandler implements JobHandler {
    // handler_param 形如：
    // {"url":"http://bw-crm:9007/internal/job/crm.analytics.snapshot",
    //  "method":"POST","timeoutMillis":120000,"body":"{}"}
}
```

实现要点（每条都对应一个已踩过的坑）：

| 要点 | 做法 |
|---|---|
| 内网鉴权 | 用 `InternalTokenSupport.restClient()`，自动带 `X-Internal-Token`；业务侧 `InternalTokenInterceptor` 默认拦 `/**/internal/**` |
| 租户上下文 | 带 `X-Tenant-Id: {job.tenantId}`，业务侧 `RequestContextFilter` 会据此设 `TenantContext`（`JobScheduler` 已按 `job_info.tenant_id` 设本进程上下文，但对端需要显式头） |
| **业务码判定** | 业务方是「HTTP 200 + 业务码」，**必须解析响应体 `code`，非 0 抛异常**走 job 重试。只看 HTTP 状态会把失败记成成功（2026-11-08 回执派发器同款缺陷） |
| 超时 | 显式设 connect/read timeout（默认 60s，按任务可覆盖）。`JobScheduler` 调度池只有 4 线程，无超时会把整个调度器拖死 |
| 结果摘要 | 把响应体 `data` 写入 `job_log.result`（≤2000 字），便于对照原日志 |
| 网关 | 新增端点都在 `/{svc}/internal/job/**` 下，已被网关 `internal-deny` 的 `/xxx/internal/**` 覆盖；缺的域（如 `/oa/internal/**`）需补齐 |

### 3.3 `job_info` 扩列与种子示例

新增两列（V-next 迁移）：`executor_app`（执行器所属应用名，对应 Nacos 服务名）、`job_code`（任务编码，即 `@ScheduledJob.code()`）。**目标地址不再写进任务参数**——地址由注册表 + Nacos 解析，见 §3.4。

```sql
INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
VALUES (9100000000000000001, 1, 'CRM 经营快照', 'CRM', 'whale-crm', 'crm.analytics.snapshot',
        '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":600000}',
        'DO_NOTHING', 2, 300, 1, 1, '迁移自 AnalyticsSnapshotJob#generateSnapshots');
```

- `executor_app` 填 Nacos 服务名（`whale-crm`），不是容器名或 IP；容器名/地址由发现层给出。
- `misfire_policy` 默认 `DO_NOTHING`（与 Spring `@Scheduled` 语义一致）；**只有需要补跑的**（如 AR 快照）才设 `FIRE_ONCE`。
- `max_retry` 与原实现对齐；原来没有重试的清理类给 0～1，推送类给 2（配 `retry_interval_seconds`）。
- `shard_total` 一律先给 1；接注册表后可设 `-1` 表示「按在线实例数动态」，本期不动。

### 3.4 补齐「执行器」这一层（对齐 xxl-job 的关键）

这是当前 `whale-job` 与 xxl-job 唯一的实质性差距：**它会调度，但不知道「谁在线、谁有这个任务」**。现状只有 `job_info.job_group`（纯分类标签），既无实例地址表，也无心跳。

**目标**：调度中心不感知容器名/IP，只认「哪个应用、哪个任务编码」。

**① 注册表（whale-job 侧，新表）**

```sql
CREATE TABLE job_executor (
    id            BIGINT PRIMARY KEY,
    tenant_id     BIGINT      NOT NULL,
    app_name      VARCHAR(64) NOT NULL COMMENT '执行器应用名（Nacos 服务名）',
    address       VARCHAR(128) NOT NULL COMMENT '实例地址 host:port',
    job_codes     VARCHAR(2048) DEFAULT NULL COMMENT '该实例支持的任务编码，逗号分隔',
    last_beat     DATETIME    NOT NULL COMMENT '最近心跳',
    status        TINYINT     NOT NULL DEFAULT 1 COMMENT '1 在线 / 0 离线',
    ...
    UNIQUE KEY uk_tenant_app_address (tenant_id, app_name, address)
);
```

**② 注册与心跳（starter 侧）**

`JobExecutorRegistrar` 在 `ApplicationReadyEvent` 时：

- 解析本机可达地址：优先 `spring.cloud.nacos.discovery.ip:port`，回落 `server.port`；
- `POST /job/executor/register`，body `{appName, address, jobCodes[]}`——`jobCodes` 直接取自 `@ScheduledJob` 的扫描结果，**这份数据就是「谁有这个任务」的权威答案**；
- 之后每 30s 心跳（`PUT /job/executor/beat`），优雅停机（`ContextClosedEvent`）时 `DELETE /job/executor/register`；
- 超过 3 个心跳周期未见心跳 → 调度侧把实例置 `status=0`（等价 xxl-job 的「摘除」）。

**③ 寻址（whale-job 侧，`HttpJobHandler` 内）**

```
job_info(executor_app, job_code)
  → job_executor 里该 app 的在线实例（过滤 30s 内无心跳）
  → 与 Nacos DiscoveryClient 的实例列表取交集（双保险：注册表说在、Nacos 也说在）
  → 选择实例：不分片取轮询；分片取 shardIndex % 实例数
  → POST http://{address}/internal/job/{job_code}/run
```

**④ 为什么不用「只查 Nacos」**

Nacos 只能回答「这个服务有几个实例」，回答不了「这个实例有没有 `crm.analytics.snapshot` 这个任务」。灰度期不同实例可能跑不同版本，只靠 Nacos 会把请求打到没有该任务的实例上（404 → 误判失败）。注册表带上 `job_codes` 才能精确路由。

**⑤ 分片（后续）**

`shard_total=-1` 时按在线实例数动态分片，每个实例收 `shardIndex`（0..n-1），由 `@ScheduledJob` 方法从上下文取。本期不启用，但注册表落地后具备条件。

---

## 4. 实施步骤

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P0 前置** | 建库 `bluewhale_job`（`db/init.sql`）；compose 增 `bw-job`（8082，Redis/MySQL 依赖同其他服务）；`SPRING_FLYWAY_BASELINE_VERSION=0` 若库已手工建过；构建镜像 | `bw-job` healthy，`/job/info/page` 可访问 |
| **P1 基础设施** | ① starter：`@ScheduledJob` + `ScheduledJobRegistrar` + `RemoteJobRegistry` + `JobTriggerController`（默认关）+ `JobExecutorRegistrar`（注册/心跳，默认关）；② job：`job_executor` 表 + `job_info` 扩 `executor_app/job_code` + 注册/心跳/摘除接口 + `HttpJobHandler` 按注册表寻址（Nacos 取交集）+ 单测；③ 网关 deny 补缺口 | 默认行为不变（引入 starter 的 20+ 服务零感知）；`job_executor` 能看到试点应用实例与 `job_codes` |
| **P2 试点** | 迁 3 个：`crm.analytics.snapshot`、`crm.analytics.action-plan`、`oa.trip.report-reminder`。灰度做法：`local-scheduling=false` + job_info 启用，观察 2 天日志与数据 | 双跑对照表（快照条数、行动项条数、提醒条数一致） |
| **P3 批量** | 剩余 24 个，按模块分提交（crm 一批、oa 一批、清理类一批、推送类一批） | 每批独立验证 |
| **P4 收尾** | 全量切 `local-scheduling=false`；旧 cron 配置项标注 deprecated；更新 `docs/devlog`、扫描报告、记忆 | 37 处告警收敛为 27 个受管任务 |

预估：P0+P1 约 1.5 人日，P2 0.5 人日，P3 2～3 人日，P4 0.5 人日。

---

## 5. 风险与回滚

| 风险 | 影响 | 应对 |
|---|---|---|
| starter 改动波及 20+ 服务 | 全站启动异常 | 新注解、注册器、控制器**全部默认关闭/空实现**；`local-scheduling` 缺省 `true`；先只在本机验证再合主干 |
| 调度池仅 4 线程 | 02:00 有 6 个任务撞车，长任务排队 | 迁移后按 `job_log` 与 `job_alert` 观察；必要时把 `POOL_SIZE` 提到 8，或错峰 cron（快照 02:00/02:30/03:00 已在错峰，仍需实测） |
| HTTP 无超时 | 一个卡死任务占满调度池 | `HttpJobHandler` 强制 read timeout，`timeoutMillis` 必填或取默认 |
| 双跑（新旧注解并存 / 双模式误配） | 数据重复（快照重复、推送重复） | 迁移提交内删旧注解；灰度期只在一个实例开 `local-scheduling=true`，或用隔离实例对照 |
| 租户丢失 | 任务对空租户执行，改错数据 | `job_info.tenant_id` 必填；`HttpJobHandler` 强制带 `X-Tenant-Id`；试点先核对日志里的 tenantId |
| 地址漂移 / 灰度版本不一致 | 请求打到没有该任务的实例，404 被误判为业务失败 | 注册表 `job_codes` 精确路由 + Nacos 实例交集；404 记明确错误（「目标实例无此任务」）而非泛化失败；灰度期实例数控制在 1～2 |
| 注册表脏数据（实例已销毁未摘除） | 调度持续打到死实例 | 心跳超 3 周期置离线；调度前再与 Nacos 取交集，双重过滤 |
| 回滚 | — | 两步：① 目标服务 `whale.schedule.local-enabled=true`（立即回到进程内 cron）；② `job_info` 该任务 `status=0` 停用。**不需要回滚代码** |

---

## 6. 验证口径

迁移一个任务，按这张表逐项打勾：

1. `job_info.status=1` 后，`job_log` 出现 cron 时刻的记录，`status=1`，`result` 非空；
2. 目标服务日志有对应业务日志，**只出现一次**（多副本时验证锁生效：另一实例无执行记录）；
3. 手工触发 `POST /job/info/{id}/run` 成功，且不产生第二条并发执行；
4. 把目标服务停机，等一个周期 → `job_log.status=-1`、`job_alert` 有记录、按 `max_retry` 重试；
5. 停服期间 `misfire_policy=DO_NOTHING` 的任务不补跑；设 `FIRE_ONCE` 的任务补跑一次；
6. 业务数据与迁移前一致（快照条数、行动项条数、清理前后行数）；
7. 回滚演练：`local-scheduling=true` 后本地 cron 恢复，`job_info` 停用后不再触发。

---

## 7. 不在本次范围

- **保留在进程内的 10 个任务**的幂等加固（重复计数、重复 RPC 的读改写竞争），建议单独立项；
- 真实分布式锁替代（`@DistributedLock` 语义是「抢不到就抛」，不适用于「抢不到就跳过」）；
- 任务分片（`shard_total>1`）的实际启用：需业务侧支持按分片号取数，属于性能优化；
- 前端管理页增强（任务 DAG 可视化等）。

---

## 8. 实施进展（2026-11-08 18:20–19:00）

**P0 + P1 + P2 已落地并实机验证**，试点两个任务已由调度中心托管。

### 8.1 交付清单

| 阶段 | 落地内容 |
|---|---|
| P0 | `bw-job` 容器化（8082，库 `bluewhale_job`）；`SPRING_DATASOURCE_URL` 覆盖（yml 硬编码 127.0.0.1）、`SPRING_DATA_REDIS_HOST`、`SPRING_FLYWAY_BASELINE_VERSION=0` |
| P1-a | `V2__job_executor.sql`：新表 `job_executor`（`uk(tenant,app,address)`）+ `job_info.executor_app / job_code`；实体/Mapper/Service/内网接口 `POST /job/internal/executor/register`、`PUT /beat`、`DELETE /register`、`GET /list` |
| P1-b | `HttpJobHandler`（Bean 名 `httpJobHandler`）：注册表寻址 → Nacos 实例交集 → 轮询/分片取模；内网令牌、`X-Tenant-Id`、解业务码、强制读超时 |
| P1-c | starter：`@ScheduledJob`、`RemoteJobRegistry`、`ScheduledJobRegistrar`（双模式）、`JobTriggerController`（`POST /internal/job/{code}/run`，默认关）、`JobExecutorRegistrar`（注册+30s 心跳+停机注销，默认关）；`JobScheduleAutoConfiguration` 已进 `AutoConfiguration.imports` |
| P2 | CRM `AnalyticsSnapshotJob` 两个方法迁移；`job_info` 两条任务记录；网关 `internal-deny` 补 `/job/internal/**` |

**与方案的差异（均为实现时的判断）**：

1. 配置前缀用 `whale.schedule.*` 而非 `whale.job.*`——`whale.job.*` 已被调度中心自身的 `JobProperties` 占用，同前缀两个 `@ConfigurationProperties` 容易误读。
2. 新增**能力校验**：`onlineAddresses(tenant, app, jobCode)` 会按实例上报的 `job_codes` 过滤；应用在线但都不支持该任务时，直接报「执行器在线但不支持该任务」，不再发请求等 404。
3. 执行器注册默认按 `job_info.tenant_id` 传租户，业务侧 `RequestContextFilter` 从 `X-Tenant-Id` 建立上下文。

### 8.2 实机结果

| 验证项 | 结果 |
|---|---|
| Flyway | `V0 baseline` / `V1` / `V2` 全部 success，`job_executor` 与两列就位 |
| 执行器注册 | CRM 启动 5s 内注册 `app=whale-crm, address=172.18.0.12:9007, codes=[crm.analytics.snapshot, crm.analytics.action-plan]` |
| 手动触发 | `triggerType=MANUAL`、`status=1`、`result="OK: snapshot 2026-11-07"` |
| cron 触发 | 18:44:00 自动执行，`triggerType=CRON`、`status=1` |
| 业务码为失败 | 故意改 `job_code` 为不存在的值 → `status=-1, retryCount=1, err="业务方返回失败: code=400, message=未注册的任务编码..."` |
| 能力校验 | 同一场景重跑（新镜像）→ 不再发 HTTP，直接 `err="执行器在线但不支持该任务: app=whale-crm, jobCode=..."` |
| 心跳判活 | 手工注册的假地址 `172.18.0.5:9007` 在 90s 后 `online=false`，调度只选 `172.18.0.12` |
| Nacos 交集 | 注册表同时存在真假两条地址时，实际请求只打到 Nacos 中真实存在的实例 |

### 8.3 踩到的坑（都已修复并记录）

1. **Spring Boot 4 + Redisson 空密码**：`spring.data.redis.password: ${SPRING_DATA_REDIS_PASSWORD:}` 解析为空串，Redisson 视为「已配置密码」并发送 `AUTH`，Redis 回 `ERR AUTH called without any password configured`，服务直接起不来。**修法：yml 里不写 `password` 键**，需要密码时用命令行/Nacos 显式覆盖。
2. **Jackson 3**：本项目为 Spring Boot 4，Jackson 包名是 `tools.jackson.*`，`com.fasterxml.jackson.*` 没有默认 Bean（CRM 靠 `Jackson2CompatConfig` 补了一个）。新代码一律用 `tools.jackson.databind.{ObjectMapper,JsonNode}`。
3. **既有库无 flyway 历史**：`bluewhale_job` 由 `db/init.sql` 建、无 `flyway_schema_history`，需 `SPRING_FLYWAY_BASELINE_VERSION=0`，否则 V1/V2 全被跳过。
4. **迁移测试语义**：原断言「init.sql 建表集合 = V1」，加入 V2 新表后必失败。改为「init.sql = V1 ∪ 后续迁移脚本建表」，更能发现新库/老库漂移。
5. **job 服务此前未容器化**：compose 无 `bw-job`；且其 `application.yml` 数据源地址硬编码 `127.0.0.1`，容器内必须整条覆盖 URL。

### 8.4 门禁

`whale-job` 83 单测、`whale-spring-boot-starter` 139 单测、`whale-crm` 346 单测，Checkstyle 均 0 违规，ArchUnit 全绿。

### 8.5 下一步

- 剩余 **25 个**任务按 §2.2 顺序分批迁移（先清理/对账类，最后推送类）；
- 执行日志回捞（executor 落文件、调度中心代理读取）；
- `shard_total=-1` 动态分片（按在线实例数）；
- **生产必须配置 `WHALE_INTERNAL_TOKEN`**：当前 dev 环境下令牌为空时过滤器 fail-open。

---

## 9. 第二批：清理类 6 个（2026-11-08 19:00–19:40）

按 §2.2 的迁移顺序，第二批取「只读 / 可重入」的清理类，共 6 个任务、4 个服务。

### 9.1 改动清单

| 服务 | 任务 code | 类.方法 | cron | 摘要字段 |
|---|---|---|---|---|
| whale-doc | `doc.recycle.cleanup` | `DocRecycleCleanupJob#cleanup` | `${whale.doc.recycle-cleanup-cron:0 30 3 * * *}` | `removed=n` |
| whale-doc | `doc.orphan-attachment.cleanup` | `DocOrphanAttachmentCleanupJob#cleanup` | `${whale.doc.orphan-cleanup-cron:0 30 4 * * *}` | 行数（`int`） |
| whale-audit | `audit.log.retention` | `LogRetentionScheduler#cleanExpiredLogs` | `${whale.audit.retention.cron}` | `clean=…, archive=…, days=…` |
| whale-music | `music.play-history.cleanup` | `PlayHistoryCleanupScheduler#purgeExpiredHistory` | `0 0 3 * * ?` | `removed=n, before=…` |
| whale-video | `video.license-expiry.offline` | `MovieLicenseExpiryScheduler#offlineExpired` | `0 10 3 * * ?` | `scanned/offline/failed` |
| whale-video | `video.watch-history.cleanup` | `WatchHistoryCleanupScheduler#cleanup` | `0 40 4 * * ?` | `deleted=n, batches=n` |

配套：

1. `V3__seed_migrated_jobs.sql`：8 条 `job_info` 种子（首批 CRM 2 + 本批 6），**按 `job_code` 判重**（`INSERT … SELECT … WHERE NOT EXISTS`），老库里已有的手工行不会被重复插入；同源内容写进 `db/init.sql`，保证新库可复现。
2. 四个服务的部署侧 env 与 CRM 一致（`WHALE_SCHEDULE_LOCAL_ENABLED=false / TRIGGER_ENABLED=true / EXECUTOR_REGISTER_ENABLED=true / EXECUTOR_SERVER_URL=http://bw-job:8082`）；yml 不写死，走 relaxed binding。
3. 返回值改「执行摘要字符串」（`void` → `String`），直接落 `job_log.result`；唯一例外见下条。

### 9.2 三处实现判断

- **`DocOrphanAttachmentCleanupJob` 保留 `int` 返回**：内网运维端点 `POST /doc/internal/ops/orphan-attachment-cleanup` 直接把行数回给调用方，改成字符串会破坏该契约。
- **`WatchHistoryCleanupScheduler` 整轮失败改为上抛**：原来 `catch` 后只打 warn，调度中心会把「清理静默停摆」长期记成成功。现在整轮异常 rethrow（进程内模式由 `ScheduledJobRegistrar` 兜底捕获，不会中断调度线程），调度侧按失败重试/告警。单条失败仍计数不抛（`MovieLicenseExpiryScheduler`）与逐租户隔离（doc 两个任务）保持原样。
- **cron 仍用 `${}` 占位符**：`ScheduledJobRegistrar` 用 `Environment.resolvePlaceholders` 解析，进程内与远程两种模式的 cron 口径一致；`job_info.cron_expression` 存解析后的字面量。

### 9.3 实机结果

| 验证项 | 结果 |
|---|---|
| Flyway V3 | `bw-job` 重启后 `Migrating to 3 - seed migrated jobs` → success，job 表 10 条（2 内置 + 8 迁移），CRM 原有两条**未重复插入**（id 仍为 `2119379191092592641`） |
| 执行器注册 | `whale-audit` → `172.18.0.14:9101 codes=[audit.log.retention]`；`whale-music` → `172.18.0.15:8096 codes=[music.play-history.cleanup]` |
| 远程触发（audit） | `status=1`、`triggerType=MANUAL`、`result="clean=false, archive=true, days=30"`，audit 侧日志 `远程触发任务完成 costMs=408`，`api_log` 记录该内网调用 200 |
| 远程触发（music） | `status=1`、`result="removed=0, before=2026-08-10T19:26:20"`，music 侧 `播放历史清理完成 retentionDays=90` |
| 无本地双跑 | 两服务日志均无本地 cron 注册记录，只有 `远程触发任务完成` |

### 9.4 本批暴露的两个既有问题（未在本次修复）

1. **`PlayAppServiceImplTest.shouldRejectTicketForInvisibleContent` 在 HEAD 上就是红的**：期望 `CONTENT_NOT_FOUND(40401)`，实际 `CONTENT_NOT_PLAYABLE(40303)`——草稿内容对作者本人 `visibleTo` 为真，走到「不可播放」分支。属代码与测试口径分歧（实现合理、测试过期或反之），与本次迁移无关，单独决策后再改。`whale-video` 其余 150 例通过。
2. **dev 环境缺 `bluewhale_doc` / `bluewhale_video` 库**：`infrastructure/docker/mysql/init/01-init-databases.sql` 未创建这两个库，`bw-video-verify` 启动即 `Unknown database 'bluewhale_video'`。因此 doc / video 的这 4 个任务只做到「编译 + 单测 + 种子就位」，端到端触发验证要等库补齐（属环境缺口，不是迁移代码问题）。

### 9.5 门禁

`whale-job` 83、`whale-doc` 416、`whale-music` 198、`whale-audit` 77 单测全绿，`whale-video` 调度相关 7 例全绿（模块 151 例中 1 例为上述既有失败），Checkstyle 0。

### 9.6 进度

已迁 8 / 27（CRM 2 + 清理类 6），**剩余 19 个**：CRM 7（回收/对账/快照）、OA 5、LMS 1、Party 1、PDM 1、Archive 2、Auth 1、CRM 推送 3 + OA 推送 2（最后一批）。


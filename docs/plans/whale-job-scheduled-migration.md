# 定时任务迁移到 whale-job 方案

> 状态：**已收口（27/27 全部迁移完成，2026-11-08 21:50）**；10 个「进程内工作泵」按 §2.1 有意保留 ｜ 编写：2026-11-08 ｜ 涉及：`whale-spring-boot-starter`、`whale-job`、17 个业务模块

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
| 已有的调度服务 | `whale-backend/whale-services/whale-job`，端口 8082，库 `bluewhale_job`，表 `job_info / job_log / job_alert / job_dependency` |
| 调度能力 | Redisson 锁 `lock:job:execute:{jobId}`、misfire 策略、失败重试（≤5）、分片（≤16）、依赖链、多实例广播 + 5 分钟 reconcile、Prometheus 指标 |
| 手工/接口 | `POST /job/info/{id}/run` 手动触发、启停、日志分页、告警分页、依赖配置 |
| 前端 | `whale-frontend/admin/src/pages/job` 已有管理页，无需新做 |
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

- 剩余任务按 §2.2 顺序分批迁移（先清理/对账类，最后推送类），逐批进度见 §9、§10 各批次的「进度」小节；
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


---

## 10. 第三批：快照/生成类（CRM 4 个，2026-11-08 19:40–20:00）

按 §2.2 的迁移顺序，第三批取「一次执行会改动全局业务状态、多跑一次无收益」的快照/对账类。
本批计划 6 个，实际完成 4 个；PDM 与 Auth 两个文件读取时涉及敏感内容被拒，**未改动、未验证**，待单独安排（见 §10.5）。

### 10.1 改动清单

| 服务 | 任务 code | 类.方法 | cron | 摘要字段 |
|---|---|---|---|---|
| whale-crm | `crm.pool.recycle` | `CrmPoolRecycleJob#recycle` | `${whale.crm.pool-recycle-cron:0 0 2 * * ?}` | `rule=…, recycled=n` |
| whale-crm | `crm.ar.snapshot` | `CrmArSnapshotJob#sync` | `${whale.crm.ar.sync-cron:0 30 2 * * ?}` | `synced/skipped/failed` |
| whale-crm | `crm.contract.sync` | `CrmContractSyncJob#reconcile` | `${whale.crm.contract.sync-cron:0 0 3 * * ?}` | `synced=n` |
| whale-crm | `crm.approval.reconcile` | `CrmApprovalReconcileJob#reconcile` | `${whale.crm.order-approval.reconcile-cron:0 */2 * * * ?}` | `reconciled=n` |

配套：`V4__seed_crm_jobs.sql`（4 条 `job_info`，按 `job_code` 判重），`db/init.sql` 同步同源种子。

### 10.2 三处实现判断

- **`CrmPoolRecycleJob` 去掉 `@ConditionalOnProperty`，开关判断移进方法**：原先靠条件装配决定 Bean 是否注册，迁到调度中心后这会让任务**根本不出现在执行器能力集里**，调度侧只能看到「执行器在线但不支持该任务」，无法区分「开关没开」与「服务没部署」。现在 Bean 常驻、任务常登记，关闭时返回 `skipped: pool-recycle disabled`——与 audit 保留期「开关只决定做不做，不决定是否存在」的口径一致。四个任务一律采用这个形态。
- **`CrmContractSyncJob` 整轮异常改为上抛**：原先 `catch (Exception)` 只 warn，合同服务长期不可用会被记成一连串成功。单客户失败仍由服务层吞掉并计数，不影响本轮。
- **`crm.ar.snapshot` 的 misfire 设为 `FIRE_ONCE`**：当日账龄快照错过应补跑一次，其余三个任务保持 `DO_NOTHING`（与 `@Scheduled` 语义一致）。

### 10.3 实机结果

| 验证项 | 结果 |
|---|---|
| Flyway V4 | `Migrating to 4 - seed crm jobs` → success，`job_info` 共 14 条（2 内置 + 12 迁移） |
| 执行器注册 | `whale-crm` → `172.18.0.12:9007`，`codes` 6 个（analytics 2 + 本批 4） |
| 公海回收 | `status=1`、`result="skipped: pool-recycle disabled"`（`WHALE_CRM_POOL_RECYCLE_ENABLED` 默认 false） |
| 应收快照 | `status=1`、`result="synced=1, skipped=0, failed=0"`（真实调 ERP，dev 有 1 个客户有未结应收） |
| 合同投影对账 | `status=1`、`result="skipped: contract sync disabled"`（dev 未部署 `bw-contract`） |
| 审批结果对账 | `status=1`、`result="reconciled=0"`；同期 `CRON` 触发（每 2 分钟）同样 `status=1` |
| 无双跑 | CRM 日志只有 `远程触发任务完成`，无本地 cron 注册记录 |

### 10.4 门禁

新增 4 个单测类（`CrmPoolRecycleJobTest` 3 例、`CrmArSnapshotJobTest` 2 例、`CrmContractSyncJobTest` 3 例、`CrmApprovalReconcileJobTest` 2 例），覆盖「关闭空转 / 开启摘要 / 失败上抛」三条路径。

`whale-crm` 356 单测（原 346 + 新增 10）全绿，`whale-job` 83 全绿，Checkstyle 0，ArchUnit 绿。

### 10.5 PDM / Auth 两个任务未处理

`PdmChgEffectiveJob`、`PasswordExpiryNoticeJob` 的文件读取被拒（涉及敏感内容校验），按规则不重试、不绕过，因此本批未改这两个任务。它们仍以原 `@Scheduled` 形式运行，不影响已迁移部分。

### 10.6 进度

已迁 12 / 27（CRM 2 + 清理类 6 + 快照类 4），**剩余 15 个**：CRM 推送/对账 4（order push、order reconcile、return push、return reconcile）、OA 5、LMS 1、Party 1、PDM 1、Archive 2、Auth 1。下一批建议先做 Archive 2（只读同步/对账，风险最低），推送类仍留最后。

---

## 11. 第四批：PDM 变更生效 + Archive 2 个（2026-11-08 20:07–20:25）

§10.5 遗留的 PDM 任务重新授权后补做；Archive 两个任务按「只读同步/对账风险最低」的排序一并迁完。

### 11.1 改动清单

| 服务 | 任务 code | 类.方法 | cron | 摘要字段 |
|---|---|---|---|---|
| whale-pdm | `pdm.change.effective` | `PdmChgEffectiveJob#scheduledRun` | `${pdm.change.effective-cron:0 0 2 * * ?}` | `due/effective/failed` |
| whale-archive | `archive.sync` | `ArchiveSyncScheduler#sync` | `${whale.archive.sync.cron:0 */5 * * * *}` | `tenants/archived/failedTenants` |
| whale-archive | `archive.file-lock-reconcile` | `ArchiveFileLockReconcileScheduler#reconcile` | `${whale.archive.lock-reconcile.cron:0 30 3 * * *}` | `tenants/locked/failedTenants` |

配套：`V5__seed_pdm_archive_jobs.sql`（3 条 `job_info`，按 `job_code` 判重），`init.sql` 同步同源种子，`job_info` 共 17 条。

### 11.2 三处实现判断

- **PDM 的开关只关定时入口**：`pdm.change.effective-job-enabled` 原来用 `@ConditionalOnProperty`，迁移后同样会让任务从执行器能力集消失。改为在 `scheduledRun()` 内判断并返回 `skipped`，而 `execute()` 作为手工/运维入口**不受开关限制**——两者语义分开，现有 3 个单测（都调 `execute()`）无需改动。
- **Archive 两个任务保留「逐租户隔离 + 计数」**：单租户异常仍只记日志，但摘要里给出 `failedTenants`，调度侧能看出部分失败；整轮不会因为一个租户而全红。
- **`ArchiveFileLockReconcileScheduler` 的补锁计数做了空值保护**：`ArchiveFileLockReconcileVO.lockedFiles` 是 `Integer` 包装类型，测试只设置 `scannedItems/hasMore/nextAfterId` 时它是 `null`，直接累加会 NPE，被外层 per-tenant `catch` 吞掉，表现成「只跑了一批就结束」。改为 `locked += value == null ? 0 : value`。

### 11.3 验证

| 验证项 | 结果 |
|---|---|
| Flyway V5 | `Migrating to 5 - seed pdm archive jobs` → success，`job_info` 共 17 条（2 内置 + 15 迁移） |
| 种子正确性 | `pdm.change.effective`→`whale-pdm`、`archive.sync`/`archive.file-lock-reconcile`→`whale-archive`，cron 与注解一致，status=1 |
| 端到端触发 | **未做**：dev 的 `docker-compose.services.yml` 没有 `whale-pdm` / `whale-archive` 段（这两个服务未容器化），也没有 `bluewhale_pdm` / `bluewhale_archive` 库。种子就位后，服务一旦启动即注册执行器、任务即可被调度 |

### 11.4 门禁

`whale-pdm` 224 单测（原 222 + 新增 2：定时入口关闭空转、执行摘要）、`whale-archive` 74 单测（原 73 + 新增 1：开关关闭空转；既有 3 例改在开关开启下运行）、`whale-job` 83 全绿，Checkstyle 0，ArchUnit 绿。

### 11.5 进度

已迁 15 / 27（CRM 6 + 清理类 6 + PDM 1 + Archive 2），**剩余 12 个**：CRM 推送/对账 4、OA 5、LMS 1、Party 1、Auth 1。

Auth 的 `PasswordExpiryNoticeJob` 仍待处理：读取该文件时审批超时，按规则未重试、未绕过，需重新授权后再做。

---

## 12. 第五批：OA 提醒/对账 3 个（2026-11-08 20:20–20:28）

| 服务 | 任务 code | 类.方法 | cron | 摘要字段 |
|---|---|---|---|---|
| whale-oa | `oa.approval.auto-urge` | `ApprovalMaintenanceJob#autoUrge` | `0 0 */2 * * *` | `total/notified` |
| whale-oa | `oa.approval.reconcile` | `ApprovalMaintenanceJob#reconcile` | `0 */30 * * * *` | `scanned/synced` |
| whale-oa | `oa.trip.report-reminder` | `TripReportReminderJob#remind` | `0 0 9 * * *` | `deadline/notified` |

配套：`V6__seed_oa_jobs.sql`（3 条，按 `job_code` 判重）；`docker-compose.services.yml` 的 `oa` 段补 `WHALE_SCHEDULE_*` 四个环境变量。

实机：`whale-oa` 注册 `172.18.0.9:9004`、能力集 3 个 code；三个任务手动触发全部 `status=1`：
`deadline=2026-11-05, notified=0` / `scanned=0, synced=0` / `total=0, notified=0`。

**OA 还剩 2 个推送 ERP 的任务**（`oa.purchase.push`、`oa.expense.payable-push`），与 CRM 4 个推送/对账一起留到最后的推送批。

---

## 13. 补验与修正（2026-11-08 20:28–20:45）

### 13.1 dev 建库后端到端验证 doc / video 4 个任务

`bluewhale_doc`、`bluewhale_video` 两个库此前缺失（`01-init-databases.sql` 未建），本轮补齐（`utf8mb4/utf8mb4_unicode_ci`，用一次性 Java 工具建，不落任何业务数据）。随后临时起两个验证实例：

| 验证项 | 结果 |
|---|---|
| whale-video 起服 | Flyway `bluewhale_video` 从 v0 迁到 **v4**（baseline / series / movie collection / watch-history 索引）→ success |
| whale-doc 起服 | `bluewhale_doc` 建表完成，执行器注册 `172.18.0.14:8095`，能力集 2 个 code |
| video 注册 | `172.18.0.15:8098`，能力集 2 个 code |
| `doc.recycle.cleanup` | `status=1`、`removed=0` |
| `doc.orphan-attachment.cleanup` | `status=1`、返回值 `0`（`int` 契约保持） |
| `video.license-expiry.offline` | `status=1`、`scanned=0, offline=0, failed=0` |
| `video.watch-history.cleanup` | `status=1`、`deleted=0, batches=1` |

验证实例用完即 down；这四个任务在 `job_info` 里**重置为 status=0**（服务未常驻 compose，避免每轮触发失败告警）。启用方式：服务起好后把对应行的 `status` 置 1。

### 13.2 调度侧：投递不出去的任务不再白等重试

**问题**：`archive.sync`（服务未部署）每次 CRON 触发都走「失败 → 睡满 300s 再重试」；调度线程池只有 4 个，几个未部署的服务就能把调度饿死，且执行流水长时间停在 `status=0`（running）。

**修法**：新增 `JobNotDispatchableException`（执行器未部署 / 在线但不支持该任务 / 未配置寻址），`HttpJobHandler` 三处寻址失败改抛该异常；`JobScheduler.executeShard` 识别后**直接记失败终态、不重试**，并沿 cause 链判断。

**实机对照**：

| 场景 | 修复前 | 修复后 |
|---|---|---|
| `archive.sync` 手动触发 | `status=0`，5 分钟后才转 `-1` | `status=-1`，`retry=0`，`errorMsg=JobNotDispatchableException: 执行器无在线实例: app=whale-archive（检查业务服务是否已开启注册、心跳是否正常）` |

### 13.3 video 测试口径修正（实现不改）

`PlayAppServiceImplTest.shouldRejectTicketForInvisibleContent` 在 HEAD 上就红：期望 `40401`、实得 `40303`。

**判定：实现正确，错的是测试夹具。** 两个错误码语义不同：`40401` 表示「对你不可见」，连存在性都不暴露；`40303` 表示「你看得见但不可播」。`visibleTo()` 明确让上传者看到自己的草稿（否则作者无法编辑/发布自己的草稿），所以「作者看自己的草稿」走 `40303` 是对的；而测试用的 `draftContent()` 上传者正是当前用户，用例名说的「不可见」并不成立。

改法：拆成两例——`shouldRejectTicketForOwnDraftAsNotPlayable`（作者本人草稿 → `40303`）与 `shouldRejectTicketForOthersDraftAsNotFound`（他人草稿 → `40401`，覆盖「不泄露存在性」）。

### 13.4 门禁

`whale-oa` 279、`whale-job` 84（新增快速失败 1 例）、`whale-video` 全模块绿（原 151 例中那 1 例既有失败已按口径修好），Checkstyle 0、ArchUnit 绿。

### 13.5 进度

已迁 18 / 27，**剩余 9 个**：CRM 推送/对账 4（order push / order reconcile / return push / return reconcile）、OA 推送 2（purchase push / expense payable push）、LMS 1、Party 1、Auth 1。

---

## 14. 第六批：LMS 催办 + Party 通知扫描（2026-11-08 20:48–20:56）

推送类留最后，本批先做两个「按日幂等通知」任务。

### 14.1 改动清单

| 服务 | 任务 code | 类.方法 | cron | 摘要字段 |
|---|---|---|---|---|
| whale-lms | `lms.urge.daily` | `LmsUrgeJob#urgeDaily` | `${whale.lms.urge.cron:0 0 9 * * *}` | `day/scanned/urgent/notified/failed` |
| whale-party | `party.notice.scan` | `PartyNoticeJob#scan` | `${whale.party.notice-cron:0 0 9 * * *}` | `party/union` 发送条数 |

配套：`V7__seed_lms_party_jobs.sql`（2 条 `job_info`，按 `job_code` 判重），`init.sql` 同步同源种子，`job_info` 共 20 条；`01-init-databases.sql` 补建 `bluewhale_party`。

### 14.2 三处实现判断

- **两个类原注释写着「不用注册到 whale-job」**，理由分别是「催办是 LMS 领域动作」「手工扫描与定时扫描走同一应用服务」。这两条只说明业务逻辑自洽，不构成留在进程内的理由——幂等和领域归属都不依赖触发方式；迁走后入口只剩「调应用服务 + 返回摘要」，反而更薄。注释已按现状重写。
- **`LmsUrgeJob` 的 `catch (RuntimeException)` 改为上抛**：原写法是为「避免 `@Scheduled` 线程抛异常后静默终止」，迁到调度中心后这个顾虑消失——异常上抛才有重试与失败可见性，否则催办停摆会被记成一连串成功。
- **`PartyNoticeJob` 保持「党务 → 工会」先后顺序**：`scanParty` 失败时本轮不再执行 `scanUnion`，与原来 `try` 包住两段的行为一致；差别只是由「只记 warn」变成「调度侧记失败并带原因」。
- **misfire 均设 `FIRE_ONCE`**：按日幂等的通知任务错过当日应补跑一次，重复触发只会让当日已发场景返回 0。

### 14.3 顺带修掉一个既有启动缺陷（whale-lms）

`whale-lms` 在本机**根本起不来**：`PaperAppServiceImpl` 按 Jackson 2 类型构造注入 `com.fasterxml.jackson.databind.ObjectMapper`，而 Spring Boot 4 只提供 Jackson 3（`tools.jackson.*`），容器里没有该 Bean → `APPLICATION FAILED TO START`。与本次迁移无关，但阻塞端到端验证。

修法沿用既有模式（crm / erp / oa / pay 都已有 `Jackson2CompatConfig`）：给 whale-lms 补一个 `@ConditionalOnMissingBean` 的 Jackson 2 `ObjectMapper`。

值得记一笔的是：这个缺陷只有在「库齐了、真去起服务」之后才会暴露——之前 `bluewhale_lms` 建了库但从没起过 whale-lms，看起来一切正常。补这一批的同时把 `bluewhale_party` 库也补进 `01-init-databases.sql`（此前只在本机手工建过）。

### 14.4 实机结果

| 验证项 | 结果 |
|---|---|
| Flyway V7 | `Migrating to 7 - seed lms party jobs` → success，`job_info` 20 条 |
| whale-party 起服 | `bluewhale_party` 从 v0 迁到 v6（baseline/platform/ledger/content/union/metric）→ success，注册 `172.18.0.15:9009 codes=[party.notice.scan]` |
| whale-lms 起服 | 补 Jackson 2 兼容 Bean 后正常启动，注册 `172.18.0.14:9008 codes=[lms.urge.daily]` |
| `lms.urge.daily` | `status=1`、`result="day=2026-11-08, scanned=0, urgent=0, notified=0, failed=0"` |
| `party.notice.scan` | `status=1`、`result="party=0, union=0"`；服务日志给出逐场景明细 `{PARTY_PROBATION=0, PARTY_FEE=0, ...}` |
| 无双跑 | 两侧只有 `远程触发任务完成`，无本地 cron 注册记录 |

验证实例用完即 down；两个任务 `job_info.status` 重置为 0（这两个服务未进 `docker-compose.services.yml`，避免每轮触发失败告警）。

### 14.5 门禁

`whale-lms` 239（新增 `LmsUrgeJobTest` 3 例）、`whale-party` 160（新增 `PartyNoticeJobTest` 2 例）、`whale-job` 84 全绿，Checkstyle 0，ArchUnit 绿。

### 14.6 进度

已迁 20 / 27，**剩余 7 个**：CRM 推送/对账 4、OA 推送 2（这 6 个都向 ERP/MES 推单，留最后一批逐个验幂等）、Auth 1（`PasswordExpiryNoticeJob`，文件读取被用户拒绝共享，未改）。

---

## 15. 第七批：推送/对账 6 个（最后一批业务任务，2026-11-08 21:00–21:10）

方案里排在最后的推送类一次做完。这批的特殊性在于「重复执行会向 ERP 重复建单」，因此重点不是跑通，而是**幂等与开关语义**。

### 15.1 改动清单

| 服务 | 任务 code | 类.方法 | cron | 摘要字段 |
|---|---|---|---|---|
| whale-crm | `crm.order.push` | `CrmOrderSyncJob#pushPending` | `${whale.crm.erp.push-cron:0 * * * * ?}` | `pushed=n` |
| whale-crm | `crm.order.reconcile` | `CrmOrderSyncJob#reconcile` | `${whale.crm.erp.reconcile-cron:0 */10 * * * ?}` | `refreshed=n` |
| whale-crm | `crm.return.push` | `CrmReturnSyncJob#pushPending` | `${whale.crm.erp.push-cron:0 */5 * * * ?}` | `pushed=n` |
| whale-crm | `crm.return.reconcile` | `CrmReturnSyncJob#reconcile` | `${whale.crm.erp.reconcile-cron:0 0/30 * * * ?}` | `refreshed=n` |
| whale-oa | `oa.purchase.push` | `PurchaseOrderPushJob#push` | `${whale.oa.purchase.push.cron:0 */10 * * * *}` | `pushed=n` |
| whale-oa | `oa.expense.payable-push` | `ExpensePayablePushJob#push` | `${whale.oa.expense.push.cron:0 */10 * * * *}` | `pushed=n` |

配套：`V8__seed_push_jobs.sql`（6 条 `job_info`，按 `job_code` 判重），`init.sql` 同步同源种子，`job_info` 共 26 条。

### 15.2 四处实现判断

- **开关关闭返回 `skipped` 而不是 `pushed=0`**：CRM 的两个类原本是 `if (!enabled) return;`。迁移后若沿用，`job_log.result` 会是一串 `pushed=0`，运维分不清「配置没开」与「真的没东西可推」。改成显式 `skipped: crm erp disabled` 之后，日志本身就是配置快照。OA 的两个类同理（`purchase/expense push disabled`）。
- **推送 `max_retry=2`、对账 `max_retry=2` 但语义不同**：推送靠 outbox + 外部单号在 ERP 侧幂等，业务上允许多次尝试；对账是纯读，重试只是「再读一次」。两者都设 2 次、间隔 60s，避免一次 ERP 抖动就让整轮失败。
- **misfire 全部 `DO_NOTHING`**：与原来 `@Scheduled` 一致。推送错过一轮无所谓（下一分钟/五分钟还会扫到 outbox），没有「必须补跑」的一次性语义。
- **`crm.order.*` 与 `crm.return.*` 共用同一组 cron 配置键**（`whale.crm.erp.push-cron` / `reconcile-cron`），但**默认值不同**。这是原代码就有的设计（订单每分钟、退换货每五分钟），迁移时保留：`${}` 占位符各自带默认值，注册器按方法注解解析，互不影响。

### 15.3 实机结果

`bw-crm` / `bw-oa` / `bw-job` 均重启为新镜像并 healthy；`V8` 迁移 success，`job_info` 26 条。

| 验证项 | 结果 |
|---|---|
| CRM 能力集 | `whale-crm 172.18.0.9:9007`，10 个 code（analytics 2 + 回收/快照/合同/审批 4 + 本批 4） |
| OA 能力集 | `whale-oa 172.18.0.12:9004`，5 个 code（催办/对账/出差报告 + 本批 2） |
| `crm.order.push` | `status=1, result="pushed=0"`；CRON 每分钟触发同样 `status=1` |
| `crm.order.reconcile` | `status=1, result="refreshed=2"`——**真回读了 ERP 的 2 张销售订单** |
| `crm.return.push` / `crm.return.reconcile` | `pushed=0` / `refreshed=0` |
| `oa.purchase.push` / `oa.expense.payable-push` | `pushed=0` / `pushed=0`（无待推送单据） |
| 对账可重入 | 连续触发三次 `crm.order.reconcile`，三次均 `refreshed=2`，无副作用 |
| 无双跑 | CRM 10 次、OA 2 次日志全部是 `远程触发任务完成`，无本地 cron 痕迹 |

**说明**：dev 库没有处于「审批通过待推送」状态的订单/采购/报销单，所以推送路径实测只走到「空转返回 0」。推单幂等性由 outbox 状态机 + ERP 侧外部单号去重保证，本轮以单测覆盖「开关关闭跳过 / 开关开启返回条数」两条分支，未构造真实推单场景（需要一套完整的订货-审批-发货数据，成本高于收益）。

### 15.4 门禁

`whale-crm` 360（原 356 + 新增 4：`CrmOrderSyncJobTest` 2 例、`CrmReturnSyncJobTest` 2 例）、`whale-oa` 283（原 279 + 新增 4：`PurchaseOrderPushJobTest` 2 例、`ExpensePayablePushJobTest` 2 例）、`whale-job` 84 全绿，Checkstyle 0，ArchUnit 绿。

### 15.5 进度

已迁 **26 / 27**，只剩 Auth 的 `PasswordExpiryNoticeJob`（文件读取被用户拒绝共享，移交 §16）。

除它之外，业务侧还剩 10 个「进程内工作泵」按方案不迁：MES/消息/支付三个 outbox 派发、邮件同步、Lucene flush、权限缓存对账、job 自身 reconcile、音乐/视频播放数刷盘（2 个）、电子签 60s 轮询。

---

## 16. 第八批：Auth 口令到期提醒 + 8 个服务容器化（2026-11-08 21:10–21:50）

上一批结束时 `job_info` 里还有 11 条任务 `status=0`，原因不是没迁，而是**执行器根本没常驻**：doc / video / music / archive / audit / lms / party / pdm 八个服务只在各自的验证阶段临时起过，验证完即 down。本批把它们纳入常态编排，让迁移后的任务真正跑在线上编排里。

### 16.1 Auth 任务迁移（27/27 收口）

| 服务 | 任务 code | 类.方法 | cron | 摘要字段 |
|---|---|---|---|---|
| whale-service-auth | `auth.password.expiry-notice` | `PasswordExpiryNoticeJob#notifyExpiringPasswords` | `${whale.auth.password.notice-cron:0 0 2 * * ?}` | `candidates=n, notified=n` |

`@Scheduled` → `@ScheduledJob`，`void` 改返回执行摘要，两处提前 `return` 改成显式 `skipped:` / `candidates=0`。**移除方法级 `try/catch`**：原实现 `catch` 后只 `log.warn`，任务停摆会在调度侧长期记成成功——与 §15 同一条口径，异常一律上抛给调度中心。

测试随之改口径：原 `shouldSwallowException` 断言「不抛」，与迁移后的语义直接冲突，改为 `shouldPropagateException`（`assertThrows`），并给两条跳过分支补返回值断言。

### 16.2 八个服务容器化

| 服务 | 端口 | 库 | 备注 |
|---|---|---|---|
| doc | 8095 | bluewhale_doc | 依赖 redis |
| video | 8098 | bluewhale_video | 依赖 redis |
| music | 8096 | bluewhale_music | 依赖 redis |
| archive | 8093 | bluewhale_archive | **库原先不存在，本批新建并补进 `01-init-databases.sql`** |
| audit | 9101 | bluewhale_audit | 依赖 redis + rabbitmq；数据源地址硬编码 |
| lms | 9008 | bluewhale_lms | — |
| party | 9009 | bluewhale_party | — |
| pdm | 9003 | bluewhale_pdm | — |

内存：Docker VM 上限 5.79G，原有 12 个容器已占 4.7G。八个新服务统一 `-Xms96m -Xmx256m -XX:MaxMetaspaceSize=192m`、`mem_limit: 480m`（默认 `x-common` 的 900m 会让总账爆），实测八个合计约 2.3G，加 `pageReporting` + 2G swap 后可稳定共存。

定时任务侧：八个服务全部注入 `WHALE_SCHEDULE_LOCAL_ENABLED=false` / `TRIGGER_ENABLED=true` / `EXECUTOR_REGISTER_ENABLED=true` / `EXECUTOR_SERVER_URL=http://bw-job:8082`。

### 16.3 容器化过程中暴露的四个既有缺陷

这八个服务此前从未在容器里常态化运行过，「起得来」本身就是一次体检：

1. **`whale-pdm` 起不来**：`PdmIntSyncServiceImpl` 按 Jackson 2 类型注入 `ObjectMapper`，Spring Boot 4 只注册 Jackson 3，容器里没有该 Bean → 补 `Jackson2CompatConfig`（与 lms 同一处置）。
2. **`pdm.change.effective` 一执行就报 `Unknown column 'tenant_id'`**：多租户插件对 `pdm_chg_job_lock` 自动追加租户列，而这表是全局调度锁、本就没有租户维度 → 把表名加进 `TenantSqlProperties` 的忽略白名单（不是改 Mapper，也不是给表加列——加列会破坏锁键的全局唯一语义）。
3. **`whale-audit` 起不来**：yml 里没有 `spring.data.redis.*`，Redisson starter 回落到 `localhost:6379` → compose 里显式注入 `SPRING_DATA_REDIS_HOST/PORT`。**不要设 password 键**：无密码 Redis 下 Redisson 会发 AUTH 被拒。
4. **`auth.password.expiry-notice` 派发 401**：auth 的 `SecurityFilterChain` 白名单只有 `/tenant/internal/**`、`/user/internal/**`，不含调度中心用的 `/internal/**`，触发请求被 `ExceptionTranslationFilter` 拦成 401；调度侧表现为任务挂到超时才落终态 → 白名单补 `/internal/**`（外部访问仍由网关 `internal-deny` 阻断，服务侧仍由 `InternalTokenGuard` 校验令牌）。

另有两处**工程陷阱**，都容易误判成「改了没生效」：

- **`mvn package` 会复用旧的 spring-boot 产物**。改了 `whale-spring-boot-starter` 后重打包 pdm，`app.jar` 内嵌的 starter 仍是旧版（182577 vs 182602 字节），表现为修复完全没生效。涉及 starter 变更时必须 `clean package`。
- **执行器应用名必须与 `spring.application.name` 严格一致**。auth 的 yml 里是 `whale-service-auth`，种子按习惯写成 `whale-auth`，调度侧寻址为空、直接记「找不到可用实例」。已应用过的 V9 不改（会破坏 Flyway checksum），新增 `V10__fix_auth_job_executor_app.sql` 幂等修正，`init.sql` 同源改对。

### 16.4 实机结果

`job_info` 29 条（2 条内置 + 27 条迁移任务），**全部 `status=1`**；`job_executor` 九个应用均在线注册。

| 任务 | 结果 |
|---|---|
| `doc.recycle.cleanup` | `status=1, removed=0` |
| `doc.orphan-attachment.cleanup` | `status=1, result=0` |
| `audit.log.retention` | `status=1, clean=false, archive=true, days=30`（清理开关默认关，只跑归档水位） |
| `music.play-history.cleanup` | `status=1, removed=0` |
| `video.license-expiry.offline` | `status=1, scanned=0, offline=0, failed=0` |
| `video.watch-history.cleanup` | `status=1, deleted=0, batches=1` |
| `lms.urge.daily` | `status=1, day=2026-11-08, scanned=0, urgent=0, notified=0, failed=0` |
| `party.notice.scan` | `status=1, party=0, union=0` |
| `pdm.change.effective` | `status=1`（耗时约 5 分钟，扫描全量变更单） |
| `archive.sync` | `status=1, pulled=4, archived=0, failed=4`——4 条失败全部是 `bw-form:8084` 未部署（表单服务不在本轮范围） |
| `archive.file-lock-reconcile` | `status=1, tenants=1, locked=0, failedTenants=0` |
| `auth.password.expiry-notice` | `status=1, candidates=0, notified=0` |

`archive` 两个任务的方法级开关默认 false（关时只返回 `skipped`），compose 里显式打开 `WHALE_ARCHIVE_SYNC_ENABLED` / `WHALE_ARCHIVE_LOCK_RECONCILE_ENABLED`——否则任务虽然 `status=1`，实际是空转。

### 16.5 留下的运维噪音

`bw-job` 启动时会尝试写 `bluewhale_job.audit_log`，该表不存在，每次触发都打一段 `BadSqlGrammarException` 堆栈。不影响调度结果（任务本身照常执行），属于审计日志组件的既有缺口，待后续统一处理。

### 16.6 门禁与进度

`whale-service-auth`（`PasswordExpiryNoticeJobTest` 6 例）、`whale-job` 84、`whale-pdm`、`whale-spring-boot-starter` 全绿，Checkstyle 0。

**进度 27 / 27，业务侧定时任务迁移收口。** 余下 10 个「进程内工作泵」按方案不迁：MES/消息/支付三个 outbox 派发、邮件同步、Lucene flush、权限缓存对账、job 自身 reconcile、音乐/视频播放数刷盘（2 个）、电子签 60s 轮询。

---

## 17. 后续小项（2026-11-08 21:50–22:00）

### 17.1 注册表过期执行器行清理

在线判定本来就是「状态在线 + 心跳未超时」双条件，过期行不会被寻址，但会一直堆在表里：管理端看着像「一堆离线实例」，而且**地址被新容器复用时会被唯一键挡住注册**。

- `JobExecutorMapper#deleteStale` 用原生 `DELETE`：唯一键 `(tenant_id, app_name, address)` 不含 `deleted`，逻辑删除会把同一地址的重新注册挡在唯一键上。
- 阈值 = `heartbeatTimeoutSeconds × staleMultiplier`（默认 90 × 10 = 15 分钟），倍数配 0 或负数时按 1 倍处理，避免退化成「删全部」。
- 挂在 `JobScheduleReconcileJob` 上跑，与重对齐同周期（5 分钟），清理失败只 warn、不影响重对齐。

实测：一次清理删掉 10 条历史残留，剩 11 条全部在线。

### 17.2 顺带修掉的 F-02 失效缺陷

查清理为什么没生效时发现：**`whale-job` 的启动类从来没有 `@EnableScheduling`**。调度中心自己的业务任务由自研 `JobScheduler` 按 cron 驱动，不依赖 Spring 调度器，所以一直没人发现——但方案里明确保留的「进程内工作泵」全部依赖 `@Scheduled`，包括 F-02 的多实例断连重对齐。也就是说**断连重对齐自上线起从未执行过**。

补注解后首轮日志：`定时任务调度的数据库重对齐完成: running=29, canceled=0, rescheduled=0`。

### 17.3 job 库补建 `audit_log`

`AuditLogAspect` 在 job 上是装配的（管理端改 cron、启停任务都带 `@AuditLog`），但 job 库一直没有这张表，每次操作都抛 `BadSqlTranslationException: Table 'bluewhale_job.audit_log' doesn't exist`——审计没落库，日志里全是堆栈。

`V11__audit_log.sql` 按 `AuditLogRecord` 建表（append-only：无 `update_time`、无 `deleted`），结构与 auth / crm 各库一致；`init.sql` 同步。实测管理端触发任务后审计正常落库，报错归零。

### 17.4 门禁

`whale-job` 88（原 84 + `JobExecutorServiceImplTest` 2 + 迁移脚本用例修正）、`whale-service-auth` 6 例、Checkstyle 0、ArchUnit 绿。

迁移脚本门禁顺带修了一处**断言口径**：原用例按文件名字符串顺序校验版本号严格递增，两位数版本（V10）排在 V2 之前必然失败。改为先按数值排序再校验递增——断言的是版本号单调性，而不是目录遍历顺序。

---

## 18. 后续小项第二批：动态分片 + 表单服务容器化（2026-11-08 22:08–22:30）

### 18.1 `shard_total = -1` 动态分片

原实现把 `shard_total` 归一成 `[1, 16]` 的固定值，分片数改了配置才变。新增哨兵值 `-1`：**每次执行时按该任务当前可派发实例数决定分片数**，扩容后下一轮自动多分片、缩容自动收敛。

关键是把「候选实例」的口径收敛到一处。此前 `HttpJobHandler` 自己做了「注册表 ∩ Nacos」，调度器若另算一遍就会出现「按 3 个分片跑、实际只有 2 个候选」的两套口径。因此下沉为 `JobExecutorService#dispatchableAddresses`，寻址与分片数都基于它：

- 交集为空 → 回退注册表并告警（服务发现配置问题不该让任务整体失败）；
- 服务发现抛异常 → 同样回退；
- 兜底：未配 `executor_app`、无在线实例、查询抛异常，一律按单分片执行——分片是负载分摊手段，不能因为它让任务跑不起来。

DTO 侧 `@Min(1)` 会拦住 `-1`，同步放开为 `@Min(-1)` 并改语义说明。

### 18.2 表单服务容器化（`bw-form`）

`archive.sync` 上批的 4 条失败全部来自 `bw-form:8084` 未部署。form 无定时任务、无 redis/rabbit 依赖，只需 mysql + nacos，接入成本低：

| 服务 | 端口 | 库 | 备注 |
|---|---|---|---|
| form | 8084 | bluewhale_form | 无 `@Scheduled`，不注入 `WHALE_SCHEDULE_*` |

实机：`bw-form` 启动 15.9s 健康，Flyway 正常；`archive.sync` 由 `pulled=4, failed=4` 变为 `pulled=0, failed=0`（无待同步记录），链路通。

### 18.3 动态分片实机验证

单实例下动态分片取到 1 → 与静态单分片等价，`doc.recycle.cleanup` 临时置 `-1` 触发得 `status=1, shardIndex=0, removed=0`，验证后已恢复 `1`。

多实例分摊由单测覆盖（`JobSchedulerTest` 新增 3 例：3 实例 → 3 分片 / 无实例 → 1 分片 / 静态分片不查注册表），实机未起第二副本——内存已到 5.1G / 5.79G。

### 18.4 观察到但不修的噪音

`job` 容器重启的瞬间，11 个服务各报一条 `执行器心跳失败: reason=Error while extracting response for type [java.lang.String] and content type [application/octet-stream]`。心跳间隔 30s、判活阈值 90s，下一轮即自愈，注册表 `last_beat` 均正常刷新。

根因是心跳用 `body(String.class)` 读取，重启窗口里对端返回了无 JSON content-type 的响应，`StringHttpMessageConverter` 拒绝转换。要彻底消除得把心跳改为 `exchange` 直读字节流——属于 starter 改动，需要全量重打包 19 个服务，收益不抵风险，只记录。

### 18.5 门禁

`whale-job` 92 例（原 86 + 动态分片 3 + 派发地址解析 3）、Checkstyle 0、ArchUnit 绿。

---

## 19. 执行日志回捞（2026-11-08 22:30–23:00）

### 19.1 为什么需要

`job_log.result` / `error_msg` 落库前截断到 2000 字，业务方法里的日志只留在各服务容器 stdout。排障时要 `docker logs` 翻半天、还要自己在时间轴上对齐「哪一段属于这次触发」。本批把「一次远程触发线程内的日志」采集成本地文件，调度中心按流水回捞。

### 19.2 分层

| 位置 | 类 | 职责 |
|---|---|---|
| starter | `JobExecutionLogStore` | 采集缓冲、落盘、保留策略、按任务编码读最近一份 |
| starter | `JobLogCaptureAppender` | 挂 root logger，按 MDC 的 `job.run.id` 决定是否采集 |
| starter | `JobTriggerController` | 触发前后 begin/finish，新增 `GET /internal/job/{code}/log/latest` |
| job | `ExecutorLogClient` | 按地址回捞（只读旁路，超时 5s，失败软返回） |
| job | `JobLogService#executorLog` + `GET /job/log/{id}/detail` | 管理端口径的查询入口 |

采集边界（写进类注释，避免误解）：

- 只采集**执行线程内**的日志——靠 MDC 传递，业务里新起的线程/线程池不继承 MDC，其日志不进快照；
- 没有 `job.run.id` 的日志零成本跳过（一次 map 查找），所以挂 root logger 不影响普通请求；
- 单次上限 `whale.schedule.executor.log-max-file-bytes`（默认 256KB），超出丢弃并一次性标注截断；
- 每任务编码保留最近 `log-max-files`（默认 20）份，超出删最旧。

### 19.3 多副本定位

执行日志是**实例本地**的，多副本时只有跑过这一次的那台有文件。所以 `job_log` 必须记录实际派发地址：

- `JobExecutionContext` 增加 `resolvedAddress`，`HttpJobHandler` 寻址成功后回填；
- `JobScheduler#invokeHandler` 在 finally 里写进 `jobLog.executorAddress`（成功与失败都记，失败时更需要）；
- `V12__job_log_executor_address.sql` 加列，`init.sql` 同步；
- 回捞优先用该地址精确请求；地址为空（V12 之前的历史流水、按参数直连模式）退化为注册表任选一台，并在 `note` 里说明。

### 19.4 实机验证

| 场景 | 结果 |
|---|---|
| `doc.recycle.cleanup` | `executor_address=172.18.0.9:8095`，回捞 2.4K 字符，含 MyBatis SQL、任务日志、执行结论 |
| `crm.order.reconcile` | `executor_address=172.18.0.12:9007`，回捞 4166 字符，尾部为「商务订单对账完成，刷新 2 单」 |
| 无 `executor_address` 的历史流水 | 回退注册表寻址成功，取到该实例最近一份日志 |
| 不存在的流水 | `code=404, message=执行流水不存在: 1` |

### 19.5 代价与注意

- 改动 starter ⇒ 11 个执行器服务全部重打镜像并重启（form/gateway/pay/erp/mes/message/workflow 无任务，不涉及）。
- 重打包必须 `clean`：auth 用 `-pl auth-starter` 而不加 `-am` 时，Maven 用的是本地仓库里的旧 domain jar，报 `找不到符号 markMustChangePassword()`——这不是代码坏了，是依赖没跟着构建。
- 日志落在容器内默认 `${java.io.tmpdir}/whale-job-exec-logs`，**容器重建即丢**。要长期留存需给执行器挂卷并把 `WHALE_SCHEDULE_EXECUTOR_LOG_DIR` 指到卷上。
- 采集的是 logback 日志事件，不是容器 stdout 全量；`System.out.println` 不会被采集。


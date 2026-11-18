# whale-job 代码评审整改记录

> 日期：2026-11-18 ｜ 服务：`whale-job`（8082）｜ 来源：外部评审报告（17 项 + 4 个确认项）
> 关联：[JobScheduler 多实例广播检查报告](2026-10-26-p5-job-schedule-broadcast.md)、`docs/devlog/2026-11-18.md`

---

## 一、评审结论复核

复核以代码事实为准，不沿用评审原文的严重级别：

| 编号 | 评审结论 | 复核结果 | 依据 |
| --- | --- | --- | --- |
| P0-1 | 创建任务不写 tenant_id | **不成立**（保留防御性赋值） | 全局 `TenantLineInnerInterceptor` 默认开启，`job_info` 不在忽略白名单；insert 自动补租户 |
| P0-2 | 日志/告警查询无租户过滤 | **不成立** | `job_log/job_alert` 均有 `tenant_id` 列，插件按表名自动追加条件 |
| P0-3 | update 未传 status 会停任务 | **成立（本轮唯一 P0）** | MapStruct 对 status 无条件覆盖 null，服务层再置 0 |
| P1-4 | Webhook 冷却期为实例内状态 | 成立 | `ConcurrentHashMap` 实例内存，多实例失效且不清理 |
| P1-5 | 调度线程池 4 个 vs 超时 60s | 成立 | `POOL_SIZE` 硬编码、默认超时 60s |
| P1-6 | Flyway 拼接 SQL | 成立（低风险） | 表名来自配置、未做标识符转义 |
| P1-7 | 内网执行器接口租户取值 | 成立 | 注册器不发 `X-Tenant-Id`，内网请求上下文为空后回落租户 1 |
| P2-8 | handlerParam 列宽不匹配 | **不成立** | 列为 `VARCHAR(512)`，与 `@Size(max=512)` 一致 |
| P2-9 | appName 无长度校验 | 成立 | 内网 list 接口无 `@Size` |
| P2-10 | joinCodes 截断切半个 code | 成立 | `substring(0, 2048)` 按字符硬切 |
| P2-11 | message 拼接 null | 理论成立、实际不可达 | `job_name` 为 `NOT NULL` |
| P2-12 | saveDependencies 缺 @Valid | 成立 | 与其他写接口不一致 |
| P2-13 | logPage/alertPage 租户 | **不成立** | 同 P0-2 |
| P3-14 | destroy 用 shutdownNow | 成立 | 中断在途 webhook，可能丢告警 |
| P3-15 | 两个 DTO 高度重复 | 成立（本轮不改） | 抽父类影响 MapStruct 映射与接口文档，收益低于风险 |
| P3-16 | 启动期多余查询 | 成立 | 未命中时仍执行 `findStaleScripts` |
| P3-17 | run 的审计类型 | 部分成立（本轮不改） | `OperateType` 无 EXECUTE/ACTION，只能改 OTHER 反而丢失语义 |

补充发现（评审遗漏）：

| 编号 | 问题 | 级别 |
| --- | --- | --- |
| R-1 | `create/update/changeStatus` 在事务提交前广播调度失效，其他实例回查数据库拿到旧值/空行，新建或「停→启」的任务在其余实例最长 5 分钟后才生效 | P1 |
| R-2 | `update` 对空 `jobGroup/misfirePolicy` 回落默认值，而 `maxRetry` 等保留原值，两套语义并存 | P2（本轮已修复） |

确认项答复：

1. `job_log/job_alert` **有** `tenant_id` 列，实体无字段不影响按表名生效的租户插件。
2. `job_info.tenant_id` 有 `DEFAULT 1`，且插件会写入上下文租户。
3. `job_info.handler_param` 列宽 **VARCHAR(512)**。
4. `httpTimeoutMillis=60000` 为默认值，单任务可用 `handlerParam.timeoutMillis` 覆盖。

## 二、本轮需求项

1. 编辑任务不得改变运行状态（P0-3）。
2. 告警外发冷却在多实例下语义一致，且本地兜底状态有界（P1-4）。
3. 内网执行器接口能按调用方租户工作（P1-7）。
4. 调度变更只在事务提交后对外广播（R-1）。
5. 调度线程池与默认 HTTP 超时可配置，避免全池饥饿（P1-5）。
6. Flyway 修复逻辑对表名做标识符转义并消除重复查询（P1-6、P3-16）。
7. 执行器编码上报不产生半个 code（P2-10）。
8. 内网 list 接口补齐长度校验（P2-9）；依赖保存补 `@Valid`（P2-12）。
9. Webhook 线程池优雅关闭（P3-14）。
10. 编辑任务未传分组/失火策略时保留原值（R-2）；告警消息对空任务名兜底（P2-11）。

## 三、实现说明

| 文件 | 变更 |
| --- | --- |
| `JobInfoServiceImpl` | update 先保存原 status/分组/失火策略，请求未传时还原；create 在存在租户上下文时显式回填 `tenantId`（防御性）；所有调度变更改为 `afterCommit` 执行，失败仅告警等对账自愈 |
| `JobInfoCreateRequest` / `JobInfoUpdateRequest` | status 补 `@Min(0)` / `@Max(1)` |
| `WebhookJobAlertNotifier` | 冷却资格改为 Redis `SETNX + TTL`（键含租户与任务）；Redis 不可用降级为实例内 Map，超过阈值清理过期项；失败消息对空任务名兜底；关闭改为 `shutdown` + 等待 |
| `JobExecutorRegistrar`（starter） | 注册/心跳/注销请求补 `X-Tenant-Id` 头，内网接口据此建立租户上下文 |
| `JobExecutorInternalController` | 类加 `@Validated`，`appName` 补 `@Size(max=64)` |
| `JobProperties` | 新增 `executor.pool-size`（默认 8）、`http-timeout-millis` 默认改为 15000 |
| `JobScheduler` | 线程池大小改读配置并归一为最小 1 |
| `FlywayLegacyRepairConfig` | 表名按元数据引用符转义；`tableExists` 支持 schema 前缀；删除与统计合并为一次查询，未命中不再多查 |
| `JobExecutorServiceImpl` | `joinCodes` 改为按 code 粒度累加，超列宽时停止追加并告警 |
| `JobController` | `saveDependencies` 补 `@Valid` |

设计取舍：

- **不在应用层手写租户过滤**：租户隔离由全局插件统一承担，重复实现会形成两套事实源；仅在创建时补防御性赋值，防止插件被关闭后静默落默认租户。
- **冷却状态选 Redis 而非 Caffeine**：项目已有 Redisson，且冷却语义天然跨实例；Redis 故障时保留本地降级，避免告警通道被单点放大。
- **调度变更整体延后到 afterCommit**：本地注册也一并延后，保证「库中状态」与「已注册调度」在提交后才对外可见；提交后失败由 5 分钟对账任务自愈。
- **P3-15 / P3-17 不改**：前者是 DTO 结构重构，后者受枚举取值限制，改动收益低于回归风险，已在本记录中留痕。

## 四、验证

### 4.1 单测与构建

- `whale-service-job`：`mvn -o test` → **108 项用例、0 失败**，`BUILD SUCCESS`，checkstyle 0 violation。
- `whale-spring-boot-starter`（注册器改动所在模块）：`mvn -o -DskipTests compile` → `BUILD SUCCESS`，checkstyle 0 violation。
- 本轮新增用例：
  - `JobInfoServiceImplTest#shouldPreserveStatusWhenUpdateWithoutStatus`：编辑不传 status 时保留运行状态；
  - `JobInfoServiceImplTest#shouldPreserveGroupAndMisfirePolicy`：编辑不传分组/失火策略时保留原值；
  - `JobInfoServiceImplTest#shouldRescheduleAfterCommit`：调度注册在事务提交后才执行；
  - `WebhookJobAlertNotifierTest#shouldUseRedisForCooldown`：Redis SETNX 决定外放资格，第二次仅落库；
  - `WebhookJobAlertNotifierTest#shouldFallbackToLocalCooldownWhenRedisFails`：Redis 异常时本地冷却仍去重；
  - `JobExecutorServiceImplTest#shouldTruncateJobCodesOnCodeBoundary`：超列宽时按完整 code 截断。

### 4.2 实机验证（本地 Redis 5.0.14.1 @ 127.0.0.1:6379）

| 验证项 | 方式 | 结果 |
| --- | --- | --- |
| 多实例共享 webhook 冷却 | `LiveWebhookCooldownIT`（`-Dwhale.live.it=true`）：两个通知器实例共用同一 Redisson 客户端，本地 HTTP 服务计数 | 通过：冷却期内两次失败只外发 1 次、落库 2 次；冷却键存在且 TTL>0；删除键后再次外发 |
| 执行器注册租户透传 | `JobExecutorRegistrarTest`：本地 HTTP 桩捕获注册/心跳/注销请求 | 通过：POST `/register`、PUT `/beat`、DELETE `/register` 均带 `X-Tenant-Id: 2` |

说明：冷却共享已由同一 JVM 内两个独立通知器实例 + 真实 Redis 验证；双 JVM 并行部署未单独执行，
该场景与本验证的差异仅在于进程边界，共享状态完全由 Redis 承担。

### 4.3 实施过程中修正的问题

| 问题 | 处理 |
| --- | --- |
| `RBucket#setIfAbsent(V, long, TimeUnit)` 在当前 Redisson 版本不存在 | 改用 `setIfAbsent(V, Duration)`，测试同步调整 |
| `JobArchitectureTest` 仍扫描已改名的 `job.entity` 包，空 should 导致构建失败（评审前既有问题） | 规则与注释同步改为 `job.po`，恢复分层守护 |
| Flyway 清理改造后，仅有 `success=0` 残行而无匹配脚本时会被漏删 | 预查询条件与 DELETE 条件保持一致（脚本 LIKE + 失败版本） |

### 4.4 未采纳项

- **P3-15（DTO 抽公共父类）**：会影响 MapStruct 映射与接口文档，收益低于回归风险；已在 `JobInfoUpdateRequest` 注释中明确「为空保留原值」语义。
- **P3-17（run 审计类型）**：`OperateType` 无 EXECUTE/ACTION，改 `OTHER` 会丢失「修改」与「执行」的区分，保留 `UPDATE`。

### 4.5 上线注意

- 多实例环境需保证各业务服务配置的 `whale.job.executor.tenant-id` 与其所属租户一致；注册器已随请求头传递该租户。
- 冷却键为 `job:alert:webhook:cooldown:{tenantId}:{jobId}`，TTL 等于 `whale.job.alert.cooldown-seconds`，无需额外清理。
- 调度线程池默认由 4 提升到 8、HTTP 默认超时由 60s 降为 15s，长耗时任务请在任务参数中显式设置 `timeoutMillis`。

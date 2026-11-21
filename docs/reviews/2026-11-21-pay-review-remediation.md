# whale-pay 支付模块评审整改（2026-11-21）

> 范围：`whale-services/whale-pay`、`whale-service-auth` 权限迁移、pay 库迁移
> 评审输入：支付模块分级评审（P0 阻断 / P1 高 / P2 中 / P3 低）
> 结论：P0/P1 全部收口（P0-2 已由启动拦截升级为完整的三段式事务改造）；P2/P3 除少数需产品/前端配合的项外已处理，剩余项明确列为待做。

## 一、需求项

1. 内网接口必须做入站鉴权，租户头不得被无凭证调用方伪造。
2. 渠道调用与本地事务的边界必须明确，禁止在 mock 关闭时把不一致风险带上线。
3. 内网创建支付单必须一次性写入 `bizType`/`notifyUrl`，幂等返回不得缺字段。
4. 退款流水号必须与支付流水号区分。
5. 通知重试的租户扫描口径必须显式化，不能依赖隐式插件行为。
6. `applyRefund` 必须兼容历史 `refund_amount = NULL`。
7. 创建支付单时必须校验「白名单 + 适配器存在」。
8. 内网 DTO 金额必须与管理端 DTO 使用同等 Bean Validation。
9. `notifyUrl` 格式必须在创建阶段校验。
10. 通知重试必须使用独立权限码。
11. 停留在 `PAYING` 的支付单必须能自动查询渠道并收敛终态，不得长期悬挂。

## 二、完成项

| 编号 | 问题 | 处理 |
| --- | --- | --- |
| P0-1 | 内网接口无入站鉴权 | 确认 starter `InternalTokenAutoConfiguration` 已对 `/**/internal/**` 注册 `InternalTokenFilter`（`X-Internal-Token`，非 dev 未配置令牌由 `InternalTokenGuard` 拒绝启动）；`PayInternalController` 每个入口新增 `TenantContext.requireId()`，无租户头直接 400；类注释写明两层防护与残余风险 |
| P0-2 | 渠道调用在本地事务内 | 支付/退款改为「本地状态提交 → 事务外调渠道 → 新事务落结果」三段式（详见第三节）；新增 `PayOrderStatus.PAYING` 中间态；删除 `PayChannelStartupGuard`，`mock=false` 不再拦截启动 |
| P0-3 | `createInternal` 幂等返回不完整 | 改为一次性组装 `PayOrderPo` 并插入，`bizType`/`notifyUrl` 首插即落库；并发撞唯一键时回读原单返回；不再二次 `updateById`，查询次数由 4 次降到 2 次 |
| P1-4 | Mock 退款复用支付流水号 | 退款独立生成 `MOCK-RF-<uuid>` |
| P1-5 | 通知定时任务租户上下文 | 新增 `PayNotifyTaskMapper#selectDueForRetry`，显式 `@InterceptorIgnore(tenantLine = "true")` 做平台级跨租户扫描；`pageTasks` 保持管理端请求上下文自动租户过滤 |
| P1-6 | `applyRefund` 对 NULL 不健壮 | SQL 全部改为 `COALESCE(refund_amount, 0)`；新增 pay 库 `V4__pay_refund_amount_not_null.sql` 回填 NULL 并收紧为 `NOT NULL DEFAULT 0.00` |
| P1-7 | 只校验白名单不校验适配器 | `PayChannelGateway#isSupported` 改为「白名单 + 适配器存在」，新增 `ensureAvailable`；创建支付单改用 `ensureAvailable`，错误信息区分「不支持」与「未接入适配器」；白名单渠道缺适配器时启动即失败；默认白名单收敛为 `MOCK` |
| P1-8 | 内网 DTO 金额缺校验 | 内网创建/退款请求补 `@DecimalMin("0.01")`、`@Digits(integer=16, fraction=2)` |
| P1-9 | `notifyUrl` 创建时不校验 | 创建阶段复用 `splitNotifyUrl` 同逻辑校验，非法直接 `PARAM_ERROR` |
| P1-10 | 通知重试权限码语义不匹配 | 改用 `sys:pay:notify:retry`；auth 库新增 `V40__pay_notify_retry_permission.sql` 与 `init.sql` 权限点，并授予既有 `tenant_admin` |
| P0-2 残留 | `PAYING` 停留单无自动收敛 | 新增跨租户扫描 + 定时对账（见 3.4）：渠道明确 `PAID` 才条件收敛并补流水/通知，其余状态保持 `PAYING` 记录告警，不自动回退 |

## 三、P0-2 资金一致性改造（本轮）

**目标**：渠道网络往返不持有数据库事务，杜绝「渠道已扣款/已退款、本地事务回滚」的静默不一致。

### 3.1 支付（`PayOrderServiceImpl#pay`）

| 阶段 | 事务 | 动作 | 失败处理 |
| --- | --- | --- | --- |
| ① 抢占 | 独立事务，立即提交 | `update pay_order set status='PAYING' where id=? and status='CREATED'` | 0 行即并发抢占失败，`PARAM_ERROR` |
| ② 渠道 | 无事务 | `payChannelGateway.pay(order)` | 渠道明确失败 → 新事务 `PAYING → CREATED` 后抛出原异常，允许重试 |
| ③ 记账 | 独立事务 | `PAID` + 渠道流水号/支付时间 + `pay_transaction` + 通知入队 + 提交后投递 | 记账失败 → 订单保持 `PAYING`（渠道可能已扣款，不回退），由对账暴露 |

- 新增 `PayOrderStatus.PAYING`：`pay` 前置校验遇到 `PAYING` 直接返回「支付处理中」，不再重复发起渠道请求；
- `PAYING` 计入度支殿「待支付」与「超时」口径（`countPending`），长期停留会被概览告警暴露；
- 取消仍只允许 `CREATED → CANCELLED`，`PAYING` 不可取消，避免与渠道结果竞争。

### 3.2 退款（`PayRefundServiceImpl#doRefund`）

| 阶段 | 事务 | 动作 | 失败处理 |
| --- | --- | --- | --- |
| ① 建单 | 独立事务，立即提交 | 幂等判重 + 插入退款单 `CREATED` | 撞唯一键回读原单按幂等回放；其他异常直接抛出 |
| ② 渠道 | 无事务 | `payChannelGateway.refund(order, refundNo, amount)` | 渠道失败 → 新事务把退款单置 `FAILED` 后抛出原异常 |
| ③ 记账 | 独立事务 | `applyRefund` 原子累计 → 退款单 `SUCCESS` + 渠道退款流水号 → `pay_transaction` → 通知 | 累计 0 行或写库失败 → 退款单置 `FAILED`，作为对账入口 |

- `PayRefundStatus.FAILED` 从「未使用」变为真实终态：渠道失败与「渠道可能成功但本地记账失败」都会落 FAILED 单；
- 幂等回放只返回已有退款单，不再重复调用渠道；并发同号请求由 `uk_tenant_refund_no` 兜底；
- `PayOrderServiceImpl#refund` 去掉外层 `@Transactional`，事务边界完全下沉到退款服务。

### 3.3 配置与文档同步

- 删除 `PayChannelStartupGuard`；`application.yml`、`WhalePayApplication`、`PayChannelProperties` 注释改为「渠道调用已在事务外，失败回退/留 FAILED 供对账」；
- 新增 i18n 文案 `pay.order.paying`（中/英/默认三份）；
- 单测新增：渠道失败回退 `PAYING → CREATED`、`PAYING` 拒绝重复支付、退款渠道失败置 `FAILED`、记账失败置 `FAILED`。

### 3.4 `PAYING` 停留单自动对账（本轮）

**背景**：记账事务失败或进程在「渠道已返回、本地未落账」之间崩溃时，支付单会停在 `PAYING`。
概览只能把它算进「待支付/超时」，没有任何组件会去问渠道到底有没有扣款。

**实现**：

| 环节 | 做法 |
| --- | --- |
| 扫描 | `PayOrderMapper#selectStalePaying`：`status = 'PAYING' AND update_time < 阈值`，`@InterceptorIgnore(tenantLine = "true")` 做平台级跨租户扫描，按 `update_time` 升序限量 |
| 租户 | 扫描结果带回 `tenant_id`，逐单 `TenantContext.set` 后再加载/更新订单，确保流水与通知任务落在原租户；处理完 `clear`，避免线程池串租户 |
| 查询 | 逐单调 `payChannelGateway.queryStatus`；查询异常只记 ERROR，订单保持 `PAYING` |
| 收敛 | **仅渠道明确返回 `PAID`** 时，用条件更新 `PAYING → PAID` 抢占（多实例只有一个成功），随后补 `pay_transaction` 与 `PAID` 通知 |
| 不回退 | 渠道返回 `CREATED`/未知状态时**不自动回退**：渠道状态可能滞后，回退成可支付会造成重复扣款；保持 `PAYING` 记 WARN，交人工用管理端对账接口确认 |
| 配置 | `whale.pay.reconcile.stale-seconds`（默认 300）、`batch-size`（默认 50）、`interval-ms`（默认 60000） |

**边界**：`MockPayChannelAdapter#queryStatus` 镜像本地状态，因此 mock 模式下停留单会一直返回 `PAYING`，对账为 no-op，属预期；
`queryStatus` 只返回状态码，自动收敛时拿不到渠道流水号，`channel_trade_no` 为空并在日志中提示，接真实渠道后需评估扩展 `queryResult`。

## 四、P2 / P3 处理

- P2-11 控制器路径统一：`PayRefundController` 改为类级 `@RequestMapping("/pay")`。
- P2-12 `businessKey` 语义：内网创建不再把 `bizType` 塞进 `businessKey`，该字段保持为空。
- P2-14 实例选择：通知投递按游标轮询 Nacos 实例，不再固定 `instances.get(0)`。
- P2-15 适配器重复注册：渠道号重复时构造阶段直接抛异常，不再静默保留第一个。
- P2-16 配置校验：`PayChannelProperties` 增加 `@Validated` + `@NotEmpty`。
- P2-17 未使用配置/字段：删除 `PayNotifyProperties.failFast`；`PayChannelResult` 删除从未读取的 `channelStatus`，只保留渠道流水号（状态查询统一走 `queryStatus`）。
- P2-18 重复查询：`refundFull` 复用已加载支付单，`refundByNo` 与 `refundFull` 共用 `doRefund`，少一次按单号查询。
- P2-19 `ensureStatus` + `claimTransition`：保留（前者负责报错文案，后者负责并发抢占）。
- P2-20 线程池停机：通知线程池增加 `waitForTasksToCompleteOnShutdown` 与 `awaitTerminationSeconds(30)`。
- P2-13 手工重试三态：`retryManually` 改为返回 `PayNotifyRetryResultVO`，状态为 `SUCCESS` / `IN_PROGRESS` / `RETRY_PENDING`；任务不存在抛 404；前端 generated schema 同步为 `ResultPayNotifyRetryResultVO`。
- 接口版本化：whale-pay 全部 HTTP 接口统一为 `/pay/v1/**`（内网同步为 `/pay/v1/internal/**`），新增 `ApiVersionConfig` 解析路径版本段；网关仍按 `Path=/pay/**` 转发、`/*/*/internal/**` 拦截内网；权限元数据由 `V41__pay_api_path_versioning.sql` 迁移。
- P3 导入清理：`PayNotifyServiceImpl` 去除全限定类名并补齐 import。
- P3 路径约束：支付单详情改为 `/{id:\d+}`，与 `/page` 明确区分。
- P3 ArchUnit：包名由 `entity` 修正为 `po`，新增「service 不得依赖 controller」规则。
- P3 数值转换：`PayOverviewProvider` 告警笔数改用 `Math.toIntExact`，超范围直接失败而非静默截断。

## 五、已修正问题项（实现过程中发现）

1. 工作区 `PayRefundController` 仍引用已删除的 `com.bluewhale.pay.entity.PayNotifyTask`，与 PO 重构不一致，已改为 `PayNotifyTaskPo`。
2. `PayArchitectureTest` 仍以 `com.bluewhale.pay.entity..` 作为分层包名，已同步为 `com.bluewhale.pay.po..`。
3. 默认 `allowed-channels` 含 `BALANCE/ALIPAY/WECHAT/UNIONPAY` 但无适配器；收敛为 `MOCK`，避免启动校验与创建校验互相矛盾。
4. 实现 P0-2 时发现 `PayOrderServiceImpl#refund` 的外层 `@Transactional` 会把退款渠道调用重新包进事务，已一并移除。
5. 实现 P0-2 时发现退款单 `FAILED` 状态此前从未落库、`applyRefund` 失败整笔回滚后不留痕，已改为失败/记账异常都落 FAILED 单。
6. P0-1 代码侧补齐：`whale-spring-boot-starter` 的内网令牌新增「按服务签发 + 租户白名单」模式（`issuers` / `issuer-id` / `issuer-secret`），共享令牌默认行为不变；`InternalTokenGuard` 对缺密钥、非平台服务无租户白名单、身份只配一半等配置 fail-fast。

## 六、待做项

1. **P0-1 部署侧切换**：starter 已支持按服务签发令牌 + 租户白名单（见 `docs/design/network-boundary.md` 第 0.1 节），但各服务实例尚未配置 `issuers`/`issuer-id`/`issuer-secret`，当前仍运行在共享令牌模式。需按服务分批下发身份与租户白名单，并配套 K8s NetworkPolicy/安全组限制 8086 直连。
2. **PAYING 自动对账的运维补强**：3.4 只自动收敛渠道明确 `PAID` 的单；渠道 `CREATED`/未知/查询异常一律保持 `PAYING` 等人工确认。接真实渠道后需补「三方流水号回填」（`queryStatus` 只返回状态）与告警接入。
3. **P3 并发/幂等集成测试**：重复 pay、同 `refundNo` 并发退款、通知重试到 DEAD、`applyRefund` 并发超额，建议用 Testcontainers/MySQL 集成测试覆盖。
4. **P3 其他**：Jackson 3 迁移后删除 `Jackson2CompatConfig`。

## 七、验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `mvn -q -DskipTests compile`（whale-pay） | 通过 |
| 单测 | `mvn -q test`（whale-pay） | 通过（订单/退款/通知/架构/迁移/概览，含三段式补偿与 PAYING 对账用例） |
| Checkstyle | `mvn -q checkstyle:check`（whale-pay） | 通过 |
| 内网令牌 starter | `mvn -q test` + `mvn -q checkstyle:check`（whale-spring-boot-starter） | 通过（含按服务令牌与租户白名单用例） |
| Auth 迁移测试 | `mvn -q -Dtest=FlywayMigrationScriptTest test`（auth-starter） | 通过 |

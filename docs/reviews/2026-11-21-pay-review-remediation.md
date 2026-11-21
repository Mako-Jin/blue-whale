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

## 四、P2 / P3 处理

- P2-11 控制器路径统一：`PayRefundController` 改为类级 `@RequestMapping("/pay")`。
- P2-12 `businessKey` 语义：内网创建不再把 `bizType` 塞进 `businessKey`，该字段保持为空。
- P2-14 实例选择：通知投递按游标轮询 Nacos 实例，不再固定 `instances.get(0)`。
- P2-15 适配器重复注册：渠道号重复时构造阶段直接抛异常，不再静默保留第一个。
- P2-16 配置校验：`PayChannelProperties` 增加 `@Validated` + `@NotEmpty`。
- P2-17 未使用配置：删除 `PayNotifyProperties.failFast`。
- P2-18 重复查询：`refundFull` 复用已加载支付单，`refundByNo` 与 `refundFull` 共用 `doRefund`，少一次按单号查询。
- P2-19 `ensureStatus` + `claimTransition`：保留（前者负责报错文案，后者负责并发抢占）。
- P2-20 线程池停机：通知线程池增加 `waitForTasksToCompleteOnShutdown` 与 `awaitTerminationSeconds(30)`。
- P3 导入清理：`PayNotifyServiceImpl` 去除全限定类名并补齐 import。
- P3 路径约束：支付单详情改为 `/{id:\d+}`，与 `/page` 明确区分。
- P3 ArchUnit：包名由 `entity` 修正为 `po`，新增「service 不得依赖 controller」规则。

## 五、已修正问题项（实现过程中发现）

1. 工作区 `PayRefundController` 仍引用已删除的 `com.bluewhale.pay.entity.PayNotifyTask`，与 PO 重构不一致，已改为 `PayNotifyTaskPo`。
2. `PayArchitectureTest` 仍以 `com.bluewhale.pay.entity..` 作为分层包名，已同步为 `com.bluewhale.pay.po..`。
3. 默认 `allowed-channels` 含 `BALANCE/ALIPAY/WECHAT/UNIONPAY` 但无适配器；收敛为 `MOCK`，避免启动校验与创建校验互相矛盾。
4. 实现 P0-2 时发现 `PayOrderServiceImpl#refund` 的外层 `@Transactional` 会把退款渠道调用重新包进事务，已一并移除。
5. 实现 P0-2 时发现退款单 `FAILED` 状态此前从未落库、`applyRefund` 失败整笔回滚后不留痕，已改为失败/记账异常都落 FAILED 单。

## 六、待做项

1. **P0-1 令牌与租户强绑定**：当前为服务间共享令牌，无法阻止持有令牌的服务伪造其他租户；需按服务/租户签发令牌，并配套 K8s NetworkPolicy/安全组限制 8086 直连。
2. **PAYING 停留单的对账闭环**：当前停留在 `PAYING` 的支付单只会通过概览「待支付/超时」暴露，尚未提供自动查询渠道并推进终态的定时任务；接真实渠道前需补「渠道路由查询 + 人工确认」的运维入口。
3. **P2-13 手工重试三态**：`retryManually` 仍返回 boolean，任务正在投递时返回 false 语义不够清晰；需改为三态结果并同步前端。
4. **P3 并发/幂等集成测试**：重复 pay、同 `refundNo` 并发退款、通知重试到 DEAD、`applyRefund` 并发超额，建议用 Testcontainers/MySQL 集成测试覆盖。
5. **P3 其他**：`PayOverviewProvider` 强转可用 `Math.toIntExact`；Jackson 3 迁移后删除 `Jackson2CompatConfig`。

## 七、验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `mvn -q -DskipTests compile`（whale-pay） | 通过 |
| 单测 | `mvn -q test`（whale-pay） | 通过（订单/退款/通知/架构/迁移/概览，含三段式补偿用例） |
| Checkstyle | `mvn -q checkstyle:check`（whale-pay） | 通过 |
| Auth 迁移测试 | `mvn -q -Dtest=FlywayMigrationScriptTest test`（auth-starter） | 通过 |

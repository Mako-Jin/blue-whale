# whale-pay 真实渠道接入骨架设计（回调 / 对账 / 入站鉴权）

| 项 | 内容 |
|---|---|
| 需求来源 | 2026-11-22 支付模块完善与扩展评审（完善 8 项 / 扩展 10 项 / 必做三项） |
| 状态 | 设计定稿，实现待启动 |
| 最后更新 | 2026-11-22 |
| 作者 | BlueWhale |
| 落点 | `whale-services/whale-pay`、`whale-sdk/whale-pay-client`（新建）、`whale-spring-boot-starter`（令牌已具备） |
| 前置文档 | [支付评审整改 2026-11-21](../reviews/2026-11-21-pay-review-remediation.md)、[网络边界](../design/network-boundary.md)、[分布式基础设施](distributed.md) |

---

## 0. 判断与边界

现有实现对「单渠道、mock、单租户、低并发」是自洽的：三段式事务（抢占 → 事务外调渠道 → 记账）已经把 P0-2 的本地回滚风险挡住，`PAYING` 停留单也有定时对账兜底。

但要走真实资金，缺的不是代码量，而是三块骨架：

| 骨架 | 缺口的本质 | 不做的后果 |
|---|---|---|
| 渠道异步回调入站 | 只有出站通知，没有入站回调 | 真实渠道 `pay()` 只能拿到「受理中」，订单永久停在 `PAYING` |
| 批量对账 + 差异闭环 | 只有单笔手工比对，mock 下永远一致 | 渠道与本地不一致时无人发现，资金差异不可追溯 |
| 内网入站鉴权 + 客户端 SDK | 共享令牌 + 调用方手工拼头 | 租户头可被伪造；业务方接入成本高、各行其是 |

**设计原则**

1. **单一状态推进入口**：支付成功/退款成功只允许经由 `PaySettlementService` 推进，`pay()`、回调、对账三处复用同一套条件更新与记账逻辑，禁止各写一份。
2. **不一致优先暴露，不猜测**：任何金额、币种、单号不匹配一律落差异/告警，不做「看起来像就补偿」。
3. **资金动作幂等靠数据库**：唯一键 + 条件更新，不依赖应用层判重。
4. **设计先行**：本文定稿后再改 Service，避免在现有事务模型上反复打补丁。

**非目标（本轮不做）**：组合支付/余额抵扣、手续费与结算分账、多币种折算、事件总线化、风控限流。这些在第五节列为后续演进，不进入本轮实现范围。

---

## 1. 骨架一：渠道异步回调入站

### 1.1 目标

真实渠道（支付宝/微信）以异步通知为准。本骨架负责：验签 → 幂等 → 定位支付单 → **复用统一状态推进** → 触发业务通知 → 全程留痕。

### 1.2 接口契约

```
POST /pay/v1/callback/{channel}
Content-Type: application/x-www-form-urlencoded | application/json
```

| 项 | 约定 |
|---|---|
| 鉴权 | **不走** `X-Internal-Token`、**不信任** `X-Tenant-Id`；仅凭渠道验签，租户由支付单反查 |
| 网关 | 新增白名单：`Path=/pay/v1/callback/**`，方法 `POST`，**剥离**权限头与租户头，不透传 `X-Permission-Codes` |
| 响应 | 成功返回渠道要求的固定体（如支付宝 `success`、微信 `{"code":"SUCCESS"}`），失败返回渠道要求的失败体，HTTP 一律 200（渠道按响应体判断重发） |
| 限流 | 网关侧按 IP + 渠道维度限流，防止伪造回调打满数据库 |

请求体不定义为强类型 DTO，统一转成 `ChannelCallbackRequest`：

```java
public record ChannelCallbackRequest(
        String channel,          // 路径参数，如 ALIPAY
        String channelTradeNo,   // 渠道流水号（幂等键）
        String outTradeNo,       // 商户支付单号（= pay_order.order_no）
        String event,            // PAID / REFUNDED / CLOSED
        BigDecimal amount,       // 渠道实收金额
        String currency,
        String rawBody,          // 原始报文（留痕，脱敏后入库）
        Map<String, String> headers
) {}
```

由 `PayChannelAdapter#parseCallback(rawBody, headers)` 解析，**签名校验在适配器内完成**，解析失败或验签失败直接抛业务异常。

### 1.3 回调日志表

```sql
CREATE TABLE IF NOT EXISTS pay_channel_callback_log (
    id                BIGINT       NOT NULL            COMMENT '主键ID（雪花）',
    tenant_id         BIGINT       DEFAULT NULL        COMMENT '租户ID（由支付单反查，未匹配为 NULL）',
    channel           VARCHAR(32)  NOT NULL            COMMENT '渠道编码',
    channel_trade_no  VARCHAR(64)  DEFAULT NULL        COMMENT '渠道流水号',
    out_trade_no      VARCHAR(64)  DEFAULT NULL        COMMENT '商户支付单号',
    event             VARCHAR(32)  NOT NULL            COMMENT '事件：PAID/REFUNDED/CLOSED/UNKNOWN',
    idempotency_key   VARCHAR(128) NOT NULL            COMMENT '幂等键：channel+tradeNo+event',
    result            VARCHAR(24)  NOT NULL            COMMENT '结果：PROCESSED/DUPLICATE/REJECTED/FAILED',
    reject_reason     VARCHAR(64)  DEFAULT NULL        COMMENT '拒绝原因：SIGN_INVALID/AMOUNT_MISMATCH/ORDER_NOT_FOUND/STATUS_CONFLICT',
    amount            DECIMAL(18,2) DEFAULT NULL       COMMENT '渠道金额',
    currency          VARCHAR(8)   DEFAULT NULL        COMMENT '渠道币种',
    raw_body          TEXT         NULL                COMMENT '原始报文（敏感字段脱敏后截断）',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency (idempotency_key),
    KEY idx_tenant_trade (tenant_id, channel_trade_no),
    KEY idx_out_trade (out_trade_no),
    KEY idx_result_time (result, create_time)
) COMMENT='渠道回调日志表';
```

- **幂等键**：`channel + ':' + channelTradeNo + ':' + event`。插入撞唯一键即视为重发，读原记录返回渠道成功响应，不再推进状态。
- **先落日志再处理**：验签通过即插入 `PROCESSED` 之外的初始记录，处理结果回写 `result`；验签失败也落库（`REJECTED/SIGN_INVALID`），用于攻击面观测。
- 日志表**不参与**租户插件自动过滤（`tenant_id` 可空），管理端查询显式带租户条件。

### 1.4 统一状态推进服务

新增 `PaySettlementService`，把现有 `settlePayment` / `settleRefund` / `settleReconciledPayment` 三处重复逻辑收敛：

```java
public interface PaySettlementService {
    /** 渠道已确认支付：PAYING|CREATED → PAID（条件更新），写流水 + PAID 通知。 */
    SettleResult markPaid(PayOrderPo order, ChannelSettlement settlement);

    /** 渠道已确认退款：退款单 → SUCCESS，原子累计退款额，写流水 + REFUNDED 通知。 */
    SettleResult markRefunded(PayOrderPo order, PayRefundPo refund, ChannelSettlement settlement);
}
```

`ChannelSettlement` 承载：渠道流水号、金额、币种、渠道时间、来源（`SYNC` / `CALLBACK` / `RECONCILE`）。

| 调用方 | 入参状态 | 结果 |
|---|---|---|
| `PayOrderServiceImpl#pay`（同步受理成功） | `PAYING` | `markPaid` 直接收敛 |
| 回调 `PAID` | `PAYING` / `CREATED` / 已 `PAID` | 收敛或幂等返回 |
| 停留单对账 | `PAYING` | 收敛或保持 |
| 退款回调 | 退款单 `CREATED` | 收敛 |

**并发控制**：`markPaid` 仍是条件更新 `WHERE id=? AND status IN ('CREATED','PAYING')`，影响行数 0 时读最新状态：
- 已是 `PAID` / `REFUNDED` → 幂等成功（回调重发）；
- 其他 → `STATUS_CONFLICT`，落差异日志并告警。

### 1.5 回调处理流程

```
验签 → 插入 callback_log(幂等键)
  ├─ 撞唯一键 → 返回渠道成功（DUPLICATE）
  ├─ 验签失败 → REJECTED/SIGN_INVALID + 告警
  ├─ 按 out_trade_no 查支付单（显式忽略租户拦截，查到后 set TenantContext）
  │   ├─ 不存在 → REJECTED/ORDER_NOT_FOUND + 告警
  │   ├─ 金额/币种与本地不符 → REJECTED/AMOUNT_MISMATCH + 告警（禁止自动补偿）
  │   └─ 一致 → markPaid / markRefunded（独立事务）
  └─ 回写 result → 返回渠道成功体
```

**为什么金额不符不自动补偿**：金额不一致说明渠道与本地对同一笔资金的理解已经分裂，自动改本地等于掩盖问题；必须进差异表由人工核实。

### 1.6 与 `pay()` 的关系

`pay()` 的同步返回值只代表「渠道受理」，真实结果以回调为准：

| 渠道返回 | `pay()` 处理 | 后续 |
|---|---|---|
| 明确成功（mock / 同步返回 SUCCESS） | `markPaid` | 无 |
| 受理中（`ACCEPTED`） | 保持 `PAYING` | 等回调或对账 |
| 明确失败 | `PAYING → CREATED` | 允许重试 |

这要求把 `PayChannelResult` 扩展为 `PayChannelResult(status, providerTradeNo, amount, currency, rawStatus)`，保留原 `providerTradeNo` 访问器以免影响现有调用方；`ACCEPTED` 为新增枚举值。

---

## 2. 骨架二：批量对账 + 差异闭环

### 2.1 目标

按日/按渠道把渠道账单与本地 `pay_order`、`pay_refund`、`pay_transaction` 比对，产出差异，支持人工确认、以渠道为准补偿、挂起，并把差异数接入概览告警。

### 2.2 表设计

```sql
-- 对账批次
CREATE TABLE IF NOT EXISTS pay_reconcile_batch (
    id             BIGINT      NOT NULL,
    tenant_id      BIGINT      NOT NULL DEFAULT 1,
    channel        VARCHAR(32) NOT NULL            COMMENT '渠道编码，ALL 表示全渠道',
    biz_date       DATE        NOT NULL            COMMENT '对账业务日',
    status         VARCHAR(16) NOT NULL DEFAULT 'RUNNING'
                                                   COMMENT 'RUNNING/SUCCESS/FAILED/PARTIAL',
    local_count    INT         NOT NULL DEFAULT 0  COMMENT '本地记录数',
    channel_count  INT         NOT NULL DEFAULT 0  COMMENT '渠道记录数',
    diff_count     INT         NOT NULL DEFAULT 0  COMMENT '差异数',
    started_at     DATETIME    NOT NULL,
    finished_at    DATETIME    DEFAULT NULL,
    error_message  VARCHAR(512) DEFAULT NULL,
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch (tenant_id, channel, biz_date),
    KEY idx_status_time (status, create_time)
) COMMENT='对账批次表';

-- 差异明细
CREATE TABLE IF NOT EXISTS pay_reconcile_diff (
    id                BIGINT        NOT NULL,
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    batch_id          BIGINT        NOT NULL            COMMENT '批次ID',
    channel           VARCHAR(32)   NOT NULL,
    biz_date          DATE          NOT NULL,
    order_no          VARCHAR(64)   DEFAULT NULL        COMMENT '支付单号',
    channel_trade_no  VARCHAR(64)   DEFAULT NULL        COMMENT '渠道流水号',
    diff_type         VARCHAR(24)   NOT NULL            COMMENT 'LOCAL_ONLY/CHANNEL_ONLY/STATUS_MISMATCH/AMOUNT_MISMATCH',
    local_status      VARCHAR(16)   DEFAULT NULL,
    channel_status    VARCHAR(16)   DEFAULT NULL,
    local_amount      DECIMAL(18,2) DEFAULT NULL,
    channel_amount    DECIMAL(18,2) DEFAULT NULL,
    currency          VARCHAR(8)    DEFAULT NULL,
    handle_status     VARCHAR(16)   NOT NULL DEFAULT 'OPEN'
                                                       COMMENT 'OPEN/CONFIRMED/COMPENSATED/SUSPENDED',
    handle_remark     VARCHAR(255)  DEFAULT NULL,
    handled_by        BIGINT        DEFAULT NULL,
    handled_at        DATETIME      DEFAULT NULL,
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diff (batch_id, channel, order_no, diff_type),
    KEY idx_tenant_open (tenant_id, handle_status, create_time),
    KEY idx_order (order_no)
) COMMENT='对账差异表';
```

### 2.3 差异分类与处置

| 类型 | 含义 | 自动补偿 | 处置 |
|---|---|---|---|
| `LOCAL_ONLY` | 本地有、渠道无 | 否 | 人工核实（可能渠道延迟入账）→ 确认本地 / 挂起 |
| `CHANNEL_ONLY` | 渠道有、本地无 | 否（需补单，涉及资金入账） | 人工核实 → 补建支付单或挂起 |
| `STATUS_MISMATCH` | 单号一致、状态不同 | **允许**（渠道 PAID 且本地 `PAYING`） | 自动/人工调 `markPaid` |
| `AMOUNT_MISMATCH` | 单号一致、金额不同 | **禁止** | 必须人工，落审计 |

`handle_status` 状态机：

```
OPEN ──确认本地无误──> CONFIRMED
  │
  ├──以渠道为准/人工触发──> COMPENSATED（仅 STATUS_MISMATCH 可走自动补偿）
  └──暂不处理──> SUSPENDED（超过 N 天仍未处置 → 继续告警）
```

### 2.4 任务编排

| 环节 | 做法 |
|---|---|
| 触发 | `@Scheduled(cron = "0 30 2 * * ?")` 日切后跑 T-1；另提供管理端手动触发 `POST /pay/v1/reconcile/batch` |
| 多实例 | ShedLock（与通知重试共用锁表），同一时刻仅一个实例执行 |
| 取数 | 渠道适配器新增 `List<ChannelBillItem> downloadBill(LocalDate bizDate)`；不支持账单下载的渠道降级为「按本地订单逐笔 `queryStatus`」，批次标记 `PARTIAL` |
| 租户 | 批次按租户拆分：先跨租户列出 `pay_order` 的 `tenant_id`，逐租户还原上下文比对，避免流水写错租户 |
| 幂等 | 唯一键 `uk_batch(tenant_id, channel, biz_date)`，重复触发读已有批次直接返回 |
| 降级 | 渠道账单接口异常 → 批次 `FAILED`，记录错误，不产出差异，避免把「取不到账单」误判成「渠道无单」 |

### 2.5 管理端接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/pay/v1/reconcile/batch/page` | `sys:pay:reconcile` | 批次列表 |
| GET | `/pay/v1/reconcile/diff/page` | `sys:pay:reconcile` | 差异列表，支持 `handleStatus`/`diffType`/`bizDate` 过滤 |
| POST | `/pay/v1/reconcile/diff/{id}/handle` | `sys:pay:reconcile:handle`（新增） | 处置：`CONFIRM` / `COMPENSATE` / `SUSPEND` |
| POST | `/pay/v1/reconcile/batch` | `sys:pay:reconcile:run`（新增） | 手动触发某日/某渠道 |

处置接口要求：`COMPENSATE` 仅对 `STATUS_MISMATCH` 放行，其余返回 400；每次处置写 `audit_log`。

### 2.6 与现有对账的关系

| 机制 | 频率 | 作用 | 关系 |
|---|---|---|---|
| `reconcileStalePaying`（已有） | 每 60s | 实时收敛 `PAYING` 停留单 | 保留，是「秒级兜底」 |
| 单笔 `GET /order/{id}/reconcile`（已有） | 手工 | 单笔排查 | 保留 |
| 批量对账（本节） | 每日 | 全量核对 + 差异闭环 | 新增，是「日级兜底」 |

三者共用 `PaySettlementService.markPaid`，不重复实现状态推进。

### 2.7 概览告警

`PayOverviewStatQuery` 增加 `openDiffCount(tenantId)`；`PayOverviewProvider` 增加：

- KPI：`reconcileDiff`（未处置差异数），`>0` 时 `OverviewTone.WARN`；
- 告警：`>= 1` 时 `WARNING`「存在 N 笔对账差异待处理」；`AMOUNT_MISMATCH` 数 `>0` 时 `CRITICAL`。

---

## 3. 骨架三：内网入站鉴权 + 客户端 SDK

### 3.1 现状与缺口

| 项 | 现状 | 缺口 |
|---|---|---|
| 令牌 | starter 已支持共享令牌 + 按服务签发（`issuers`/`issuer-id`/`issuer-secret`）+ 租户白名单 | 部署侧仍跑共享令牌，未下发身份与白名单 |
| 租户 | `PayInternalController` 已强制 `TenantContext.requireId()` | 共享令牌下租户头与调用方未强绑定 |
| 调用方 | 业务方手写 `RestClient`，自行拼 `X-Internal-Token`、`X-Tenant-Id` | 无统一重试、幂等键、异常映射 |

### 3.2 鉴权落地（部署侧为主）

1. 为每个调用方服务签发 `issuer-id` / `issuer-secret`，在 `whale-pay` 侧配置 `whale.internal-token.issuers`（服务 → secret）与该服务允许的租户白名单。
2. 网关对 `/pay/v1/internal/**` 继续 403；K8s NetworkPolicy / 安全组只放行白名单服务的 8086 直连。
3. `pay` 侧对「租户不在白名单」的请求返回 403 并记录审计，不依赖调用方自觉。
4. 共享令牌模式保留为本地开发默认，生产禁止。

**验收**：跨租户越权用例——服务 A 携带不属于自己的 `X-Tenant-Id` 调用内网接口，必须 403；未带令牌 403；令牌正确但租户越界 403。

### 3.3 `whale-pay-client` SDK

新建模块 `whale-backend/whale-sdk/whale-pay-client`，与 `whale-auth-sdk` 同级。

**依赖与配置**

```yaml
whale:
  pay:
    client:
      base-url: http://whale-pay:8086      # 或经 Nacos 服务发现
      internal-token: ${PAY_INTERNAL_TOKEN}
      tenant-id: ${TENANT_ID}
      connect-timeout: 2s
      read-timeout: 5s
      max-retries: 2                        # 仅对幂等接口重试（GET / create / refund）
```

**API**

```java
public interface WhalePayClient {
    PayOrderView create(PayCreateCommand command);      // 幂等：orderNo
    PayOrderView pay(String orderNo);                   // 非幂等重试需业务确认
    PayOrderView cancel(String orderNo);                // 幂等
    PayOrderView getByOrderNo(String orderNo);
    PayRefundView refund(PayRefundCommand command);     // 幂等：refundNo
}
```

**关键约定**

| 项 | 做法 |
|---|---|
| 头部 | 自动注入 `X-Internal-Token`、`X-Tenant-Id`、`X-Trace-Id`（取当前 trace，无则生成） |
| 幂等键 | `create` 未传 `orderNo` 时按 `bizType + businessKey` 生成；`refund` 未传 `refundNo` 时生成并记录日志，保证重试可复用 |
| 重试 | 仅 `connect timeout` / `5xx` / `Result.code == SYSTEM_ERROR` 重试；`PARAM_ERROR` / `NOT_FOUND` 不重试 |
| 异常 | `Result.code != 0` 抛 `PayClientException(code, messageKey, message)`，不透传 HTTP 细节 |
| 版本 | 默认 `/pay/v1`，可配置，便于后续 v2 灰度 |

**接入成本**：业务方从「手写 RestClient + 拼头 + 解析 Result」降为注入一个 Bean，P0-1 的鉴权顺势由 SDK 统一落地。

---

## 4. 次优三件（设计要点，随后续迭代）

### 4.1 支付超时自动关单

- `pay_order` 增加 `close_reason VARCHAR(32)`（`TIMEOUT` / `MANUAL` / `CHANNEL_CLOSED`）。
- 扫描 `CREATED AND create_time < now - timeout`（默认 30 分钟，可按渠道/业务方配置）。
- 支持关单的渠道调 `close(order)`；不支持的渠道直接置 `CANCELLED` 并记录 `close_reason=TIMEOUT`。
- 关单失败进重试（复用通知任务的退避模型或独立 `pay_close_task`），不阻塞扫描。
- 概览 `staleOrders` 从「只统计」升级为「统计 + 自动处置计数」。

### 4.2 通知可观测 + ShedLock

| 项 | 做法 |
|---|---|
| 指标 | Micrometer：`pay.notify.pending`(Gauge)、`pay.notify.dead`(Gauge)、`pay.notify.oldest_pending_age_seconds`、`pay.notify.delivery`(Counter，tag: event/bizType/targetService/result)、`pay.notify.delivery_duration`(Timer) |
| traceId | `enqueue` 时把当前 trace 写入 `payload.traceId`；投递时透传 `X-Trace-Id`，异步线程不再断链 |
| ShedLock | 通知重试与批量对账共用 `shedlock` 表，`@SchedulerLock(name="pay-notify-retry", lockAtMostFor="5m")`，避免 N 个实例每 30s 全表扫 |
| 死信运维 | `notify/page` 增加 `bizType` 过滤；新增 `POST /pay/v1/notify/retry-batch`（按 ID 列表，去重保序，返回逐项结果）；任务详情返回 `payload` |

### 4.3 管理端部分退款 + 渠道能力矩阵

- 新增 `POST /pay/v1/order/{id}/refund/partial`：`{ amount, reason, clientRequestId }`，`clientRequestId` 作为 `refundNo` 透传，复用现有幂等链路。
- 支付单 VO 增加 `refundableAmount = amount - refundAmount`；退款单列表按 `payOrderNo` 聚合展示累计退款进度。
- 适配器新增能力声明：

```java
public interface PayChannelAdapter {
    Set<ChannelFeature> features();   // PARTIAL_REFUND / REFUND_QUERY / ASYNC_CALLBACK / CLOSE
}
```

创建/退款时按能力校验（不支持部分退款直接 400）；`GET /pay/v1/channel/capabilities` 供前端渲染。

---

## 5. 完善项与骨架的耦合点

以下项不单独实施，但必须在上述骨架落地时一并处理，否则骨架会带病上线：

| 项 | 处理时机 | 要求 |
|---|---|---|
| `Money` 值对象 | 骨架一、二施工前 | `pay_order`/`pay_reconcile_diff` 的金额比较统一走 `Money`，封装 compare/相加与精度校验；统计 SQL 在多币种上线前必须 `GROUP BY currency`，当前单币种可先加注释约束 |
| 状态机显式化 | 骨架一 | `PayOrderStatus.allowedTransitions()`，`markPaid`/`cancel`/`markRefunded` 从状态机取合法目标，非法转换统一报错 |
| 配置校验 | 骨架一、三 | `PayNotifyProperties` 加 `@Min/@Max`；`mock=false` 且白名单含无适配器渠道时拒绝启动（网关已有校验，补 `@PostConstruct` 显式守卫） |
| `PayChannelResult` 扩展 | 骨架一 | 增加 `status`（`SUCCESS`/`ACCEPTED`/`FAILED`）与金额、币种；保留 `providerTradeNo()` |
| 数据生命周期 | 骨架二之后 | `pay_transaction`、`pay_notify_task` 按 `create_time` 归档到 `*_history`；`SUCCESS` 通知保留 30 天，`DEAD` 永久保留；补 `idx_tenant_create_time` |
| 测试补齐 | 每块骨架 | 见第六节 |

---

## 6. 验收与测试矩阵

| 类别 | 用例 | 归属 |
|---|---|---|
| 回调幂等 | 同一 `channelTradeNo` 重发 3 次，只产生 1 条流水、1 条通知 | 骨架一 |
| 回调验签 | 篡改签名 → `REJECTED/SIGN_INVALID`，状态不变，告警 | 骨架一 |
| 回调金额不符 | 渠道金额 ≠ 本地 → `REJECTED/AMOUNT_MISMATCH`，禁止改状态 | 骨架一 |
| 回调未知单号 | `out_trade_no` 不存在 → `REJECTED/ORDER_NOT_FOUND` | 骨架一 |
| 回调并发 | 回调与同步 `pay()` 同时到达 → 只有一个 `markPaid` 成功，另一个幂等 | 骨架一 |
| 对账差异 | 构造 LOCAL_ONLY / CHANNEL_ONLY / STATUS_MISMATCH / AMOUNT_MISMATCH 各 1 笔 | 骨架二 |
| 对账幂等 | 同日同渠道重复触发，不产生重复批次/差异 | 骨架二 |
| 对账补偿 | `STATUS_MISMATCH` 补偿后订单 `PAID`，差异 `COMPENSATED` | 骨架二 |
| 对账降级 | 渠道账单接口超时 → 批次 `FAILED`，无差异产出 | 骨架二 |
| 跨租户越权 | 服务 A 用他人租户头调内网接口 → 403 | 骨架三 |
| SDK 幂等 | 同 `clientRequestId` 重复退款 → 返回原退款单，不重复扣款 | 骨架三 |
| 并发退款 | 同 `refundNo` 并发 → 唯一键兜底，仅一笔成功 | 既有 + 骨架三 |
| ArchUnit | callback 控制器不直接依赖 Mapper；渠道适配器不被 controller 直接引用 | 全部 |

集成测试需 MySQL（本机 Docker 未启动时以 `SKIP` 写入 Step Summary，不静默通过）。

---

## 7. 实施顺序

```text
阶段 0  文档定稿（本文） ✅
阶段 1  统一状态推进 PaySettlementService + PayChannelResult 扩展 + 状态机
阶段 2  回调入站（表 + 控制器 + 适配器 parseCallback + 幂等 + 日志）
阶段 3  批量对账（表 + 任务 + 差异处置接口 + 概览告警）
阶段 4  内网鉴权部署侧切换 + whale-pay-client SDK
阶段 5  次优三件（超时关单、通知指标 + ShedLock、部分退款 + 能力矩阵）
阶段 6  数据生命周期与归档
```

**依赖**：阶段 2、3 都依赖阶段 1，否则状态推进会重复实现三份；阶段 4 可与 2、3 并行（改动面不重叠）。

---

## 8. 需求项 / 完成项 / 待做项 / 已修正问题项

### 需求项

1. 渠道异步回调入站，支持验签、幂等、留痕、统一状态推进。
2. 批量对账与差异闭环（批次、差异表、处置、告警）。
3. 内网入站鉴权落地 + 客户端 SDK。
4. 支付超时自动关单。
5. 通知投递可观测 + ShedLock。
6. 管理端部分退款 + 渠道能力矩阵。
7. 金额/币种值对象、状态机显式化、配置校验、数据生命周期。

### 完成项

| 项 | 状态 |
|---|---|
| 支付/退款三段式事务（P0-2） | 已完成，见 [支付评审整改](../reviews/2026-11-21-pay-review-remediation.md) |
| `PAYING` 停留单定时对账 | 已完成 |
| 单笔手工对账接口 | 已完成 |
| 内网令牌 starter 按服务签发 + 租户白名单 | 已完成（代码侧） |
| 本设计文档 | 已完成 |

### 待做项

1. 阶段 1～6 全部实现（按第 7 节顺序）。
2. 部署侧下发 `issuer-id`/`issuer-secret` 与租户白名单，配置 NetworkPolicy。
3. 并发/幂等/故障注入集成测试（需 MySQL 环境）。
4. `queryStatus` 扩展为可返回渠道流水号的 `queryResult`，补齐自动收敛时 `channel_trade_no` 为空的问题。

### 已修正问题项

1. 设计中发现 `settlePayment`、`settleReconciledPayment`、退款记账三处状态推进逻辑重复，若直接加回调会变成四份；已决定先抽 `PaySettlementService`（阶段 1）。
2. 设计中发现概览统计 SQL `SUM(amount)` 未按币种分组，多币种上线会算错；已纳入骨架二施工前约束。
3. 设计中发现 `PayChannelResult` 只有流水号、无法表达「受理中」，无法支撑真实渠道；已列入阶段 1 扩展项。
4. 设计中发现回调路径若复用 `X-Internal-Token` 会与渠道验签冲突，已明确回调独立鉴权、网关剥离权限头。

---

## 9. 风险与未决问题

| 风险 | 影响 | 缓解 |
|---|---|---|
| 渠道账单格式差异大 | 每个渠道都要单独实现 `downloadBill` | 先接一个渠道跑通，抽象 `ChannelBillItem` 再复制 |
| 回调路径暴露公网 | 伪造回调刷库 | 验签 + 网关限流 + 幂等表唯一键；异常回调只记日志不改状态 |
| 差异自动补偿误操作 | 把错误数据写正 | 仅 `STATUS_MISMATCH` 允许自动补偿，其余强制人工 + 审计 |
| ShedLock 依赖 | 新增中间件表 | 复用现有 MySQL，不引入 Redis 锁，避免与 `whale-auth` 的 Redisson 混用 |
| 阶段 1 改动面大 | 影响现有支付/退款 | 先补单测锁定现状行为，再替换内部实现，对外接口不变 |

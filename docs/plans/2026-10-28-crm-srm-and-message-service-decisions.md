# 架构决策：供应商管理边界、统一消息服务与外部接入口径（2026-10-28）

> 状态：**已决并实施（供应商主数据已落 ERP；消息服务 M0 + M1 已完成）**
> 关联：[待办清单](pending-issues-2026-10-27.md)、[外部接入作业单](../ops/external-integration-onboarding.md)、
> [服务与端口清单](../service-catalog.md)、[CI/CD 方案](../design/cicd.md)
> 决策人：BlueWhale ｜ 记录日期：2026-10-28

---

## 0. 本轮结论速览

| # | 问题 | 结论 | 落地方式 |
|---|---|---|---|
| 1 | 供应商管理是不是 CRM？要不要新增 CRM？ | **不是**。供应商准入/资质/绩效/风险属于 **SRM/采购/供应链**，或 ERP 的供应商主数据；CRM 的语义是客户、联系人、商机、销售漏斗 | 本期**不新增 CRM**；已按用户指令落 **ERP 供应商主数据**（`erp_supplier` + `/erp/supplier/**`）；确需独立域时再新建 `whale-srm` |
| 2 | 统一消息 SDK/service 是否合理？ | **合理**。应建独立 `whale-message` **服务**统一模板、路由、偏好、去重、重试、投递记录；SMS/EMAIL/IM/站内信作为**渠道适配器** | 已落 `whale-message` **M0**（发送内核 + 四通道适配器 + mock 模式）与 **M1**（模板 CRUD、失败重试、站内信收件箱）；SDK 仅做客户端契约，不复制实现 |
| 3 | Gitee Go 流水线实机触发 | **不做**（won't do） | 完全依赖 GitHub Actions；在 [cicd.md](../design/cicd.md) 记录冻结口径 |
| 4 | 合同供应商沙箱联调 | **不做真实沙箱**，仅 mock | 保留 loopback 协议沙箱与契约测试；真实供应商接入待商务条件成熟后另立任务 |
| 5 | 合同电子签凭据 | **仅模拟** | 本地 `local` 渠道 + 自签测试 P12/TSA/CA；生产凭据不进入本轮范围 |
| 6 | P0 外部接入阻塞项 | **接受 deferred/mock** | S-04d-2、S-04e-2、F-03b、V-06、V-11 转为「部署侧验收/契约测试」口径，不再作为代码待办 |

---

## 1. Architecture Guidance Result

- **Phase**: Architecture Guidance
- **Status**: completed
- **Inputs**
  - 用户 2026-10-28 范围决策（真实实名/意愿供应商短期不接入；短信/消息统一服务；Gitee Go 不做；合同沙箱与凭据仅 mock）
  - 仓库证据：`whale-backend/whale-apps`（contract/erp/mes/oa/pdm/pm）、`whale-backend/whale-services`（agent/archive/file/form/gateway/im/job/pay/auth/workflow）、`whale-backend/whale-ops`（alert/audit/monitor）
  - 现有消息能力：`whale-alert`（EMAIL/SMS/Webhook/去重/静默）、`whale-im`（聊天会话/消息/已读/在线/机器人）、Portal（待办/公告）
  - 现有供应商相关能力：仅合同域「相对方」与电子签渠道；无 CRM/SRM/供应商主数据模块
- **Summary**
  - 供应商管理不应放入 CRM；CRM 与 SRM 是两个不同的 Bounded Context。
  - 统一消息服务方向成立，但必须与 IM 聊天域、告警域、业务通知/待办域划清边界。
  - “SDK”只承载客户端契约；核心模板、路由、去重、投递记录仍归服务端。
  - 本轮不新增 CRM；新增 `whale-message` M0，供应商域待 `whale-srm` 单独立项。
- **Assumptions**
  - 当前没有真实实名/意愿、CA/TSA、短信、邮件、合同供应商账号，且短期不会具备（用户明确确认）。
  - 系统内部消息/站内信只需要“可查询的投递记录”，不要求即时聊天语义。
  - IM 聊天历史与会话状态继续由 `whale-im` 独占。
- **Decisions**
  1. **不新增 `whale-crm` 承载供应商管理**；CRM 仅在出现客户/联系人/商机/销售漏斗需求时立项。
  2. 供应商管理优先落在 ERP 供应商主数据或独立 `whale-srm`；合同域「相对方」继续作为合同签署视角的引用，不复制供应商主数据。
  3. **新增 `whale-message` 服务**：统一消息模板、渠道路由、幂等、投递记录；SMS/EMAIL/IM/INAPP 为渠道适配器。
  4. `whale-message` 通过 `/message/internal/send` 接受服务间投递；外部查询走 `/message/record/**`，受 `sys:message:record:list` 权限保护。
  5. M0 默认 `mock=true`：外部通道只记录 `MOCKED/SKIPPED`，不发起真实网络请求；生产通过环境变量显式关闭 mock 并配置渠道。
  6. 不新增消息 SDK 模块到本期；对外契约以 HTTP DTO + 文档形式提供，后续如有 Java 消费方再加 `whale-message-sdk`。
- **Constraints**
  - 消息记录、投递记录、模板均带 `tenant_id`，由 starter 的租户插件行级隔离。
  - 领域/应用层不依赖具体渠道 SDK；渠道适配器负责协议与凭据。
  - 发送流程不在长事务中跨出站 HTTP；先落消息与投递记录，再逐通道调用并回写结果。
  - 幂等键 `(tenant_id, idempotency_key)` 唯一；重复请求返回既有消息，不重复投递。
  - 外部渠道失败只记 `FAILED` 与错误摘要，不阻断其它通道，也不回滚消息记录。
- **Evidence**
  - `docs/service-catalog.md`：现有 21 个后端服务，无 CRM/SRM/消息中台。
  - `whale-backend/whale-ops/whale-alert/.../AlertNotifyServiceImpl.java`：告警域自带 EMAIL/SMS/Webhook，证明通道能力存在但服务于告警，不是通用消息中心。
  - `whale-backend/whale-services/whale-im/.../ImInternalController.java`：IM 已有内网站内通知入口，但语义是机器人会话投递，不承担模板/偏好/多渠道路由。
  - `whale-backend/whale-common/whale-spring-boot-starter/.../MybatisPlusConfig.java`：租户插件全表默认隔离，新增消息表可直接复用。
- **Open Questions**
  - `whale-srm` 是否单独立项，还是先扩展现有 ERP 供应商主数据？需要采购/供应链业务确认主数据归属与编码规则。
  - 若未来出现客户/商机管理，`whale-crm` 与合同「相对方」、ERP 客户主数据的主键映射由谁维护？
  - 消息偏好（用户免打扰、渠道优先级）与 Portal 待办已读状态是否需要统一，需要产品确认。
- **Artifacts**
  - 本文档
  - [`whale-message` M0 检查报告](../reviews/2026-10-28-wave5.68-message-m0.md)
  - `whale-backend/whale-services/whale-message/`（M0）
  - `docs/service-catalog.md` 新增 `whale-message` 行与网关路由
- **Recommended Next Step**
  - 先以 `whale-message` M0 接入 1–2 个真实业务生产者（建议 PM 站内通知、合同到期提醒），验证模板与幂等；再决定是否补 `whale-message-sdk`。
  - `whale-srm` 等采购域需求明确后单独立项，不在 CRM 下承载。
- **Handoff Notes**
  - 告警通道继续留在 `whale-alert`，避免把告警去重/静默语义搬进消息中心；后续可让 `whale-alert` 作为生产者调用 `whale-message`，但需另立迁移任务。
  - Portal 待办/公告是业务功能，不是投递通道；消息中心只负责“送达”，不拥有业务待办状态。

---

## 2. 为什么“供应商管理 ≠ CRM”

| 维度 | CRM（客户关系） | SRM（供应商关系） |
|---|---|---|
| 核心对象 | 客户、联系人、商机、销售漏斗、客户服务 | 供应商、准入、资质、询价/报价、绩效、风险 |
| 主要使用者 | 销售、市场、客服 | 采购、供应链、质量、财务 |
| 生命周期 | 线索 → 商机 → 合同 → 回款/服务 | 注册 → 准入 → 合作 → 绩效评估 → 淘汰 |
| 与合同的关系 | 合同是销售结果之一 | 合同是采购/合作协议之一 |
| 数据归属 | 客户主数据 | 供应商主数据（常在 ERP/供应链） |

**结论**：仅因“合同电子签供应商/相对方”就新增 CRM，会造成 Bounded Context 语义错误：后续客户、商机、销售漏斗会被迫塞进同一个“CRM”，而供应商准入、绩效、风险又缺位。正确路径是：

1. **本期**：合同域继续维护「相对方」快照，电子签渠道配置留在合同域；不新增 CRM。
2. **供应商主数据**：优先由 ERP 承载；若采购流程复杂（准入、询比价、绩效、黑名单），再新建 `whale-srm`。
3. **CRM**：只有在客户/联系人/商机/销售漏斗成为真实业务需求时立项，不复用供应商模型。

---

## 3. 统一消息服务 `whale-message` 边界

### 3.1 职责

| 归属 | 能力 |
|---|---|
| `whale-message` | 消息模板、变量渲染、渠道选择、幂等键、投递记录、失败隔离、mock 模式、后续重试/偏好 |
| `whale-im` | 会话、聊天消息、已读游标、在线状态、WebSocket、机器人会话；**不承担**模板/多渠道路由 |
| `whale-alert` | 告警规则、指纹去重、静默、Alertmanager 入站；可作为消息中心的生产者，但告警语义不迁移 |
| Portal/业务应用 | 待办、公告、业务状态；消息中心只负责“送达”，不拥有业务状态 |

### 3.2 渠道适配器

| 渠道 | M0 行为 | 生产扩展 |
|---|---|---|
| `INAPP` | 落 `msg_record`，供查询/后续 Portal 拉取 | 增加已读状态、未读计数 |
| `EMAIL` | `mock=true` 时 `MOCKED`；配置 `spring.mail.*` 后走 JavaMail | SMTP 账号、模板审核、退信处理 |
| `SMS` | `mock=true` 时 `MOCKED`；否则 POST 部署侧统一 HTTP 网关 | 供应商签名算法放网关，平台不持 AccessKey |
| `IM` | `mock=true` 时 `MOCKED`；否则 POST `whale-im /im/internal/notify` | 群发、机器人模板、送达回执 |

### 3.3 M0 数据模型

- `msg_template`：模板编码、标题模板、内容模板、启用状态。
- `msg_record`：一条逻辑消息（业务类型/业务ID/模板/标题/内容/渠道/状态/幂等键）。
- `msg_delivery`：每个渠道 × 接收人的投递结果（`SENT/FAILED/SKIPPED/MOCKED` + 错误摘要）。

### 3.4 API 契约

| 方法 | 路径 | 用途 | 鉴权 |
|---|---|---|---|
| `POST` | `/message/internal/send` | 服务间发送 | 网关 `internal-deny` 403，仅内网 |
| `GET` | `/message/record/page` | 消息记录分页 | `sys:message:record:list` |
| `GET` | `/message/record/{id}` | 消息详情 + 投递明细 | `sys:message:record:list` |

请求示例：

```json
{
  "tenantId": 1,
  "bizType": "PM_TASK",
  "bizId": "T-1001",
  "idempotencyKey": "PM_TASK:T-1001:ASSIGN",
  "templateCode": "SYSTEM_NOTICE",
  "title": "任务指派",
  "content": "你有一个新任务：${taskName}",
  "payload": { "taskName": "需求评审" },
  "channels": ["INAPP", "IM"],
  "recipients": [
    { "userId": 1001, "email": "user@example.com", "phone": "13800000000" }
  ]
}
```

### 3.5 SDK 定位

- **服务端是唯一实现**；SDK 只做请求/响应 DTO、错误码、调用封装。
- SDK 不得内置模板渲染、渠道选择、重试策略，否则会形成第二套实现。
- 本期先以 HTTP 契约 + 文档交付；出现 2 个以上 Java 消费方后再加 `whale-message-sdk`。

---

## 4. 外部接入口径调整

| 项 | 原口径 | 新口径（本轮） | 后续触发条件 |
|---|---|---|---|
| S-04d-2 持牌 CA/TSA | 实机联调 | **仅模拟**：本地自签 P12/CA/TSA，真实凭据不进入本轮 | 商务签约、拿到 PEM/CRL/TSA 账号 |
| S-04e-2 实名/意愿供应商 | 接入真实供应商 | **仅模拟**：本地登录态 + 勾选意愿 + 回调字段映射 | 供应商协议与测试账号到位 |
| F-03b 告警短信 | 真实送达 | **统一 HTTP 网关 + loopback 契约测试**；真实送达转部署侧验收 | 部署侧提供网关与模板报备 |
| V-06 告警全链路 | 真实 SMTP/短信/Alertmanager | **mock/契约覆盖**；真实全链路转部署验收清单 | 运维提供 SMTP、短信账号、Alertmanager 令牌 |
| V-11 合同供应商沙箱 | 真实三家沙箱 | **不做**；保留 loopback 协议沙箱 | 商务条件成熟后另立任务 |
| V-07 Gitee Go | 实机触发 | **不做（won't do）**；完全依赖 GitHub Actions | 无（除非组织强制要求） |

---

## 5. 风险与缓解

| # | 风险 | 缓解 |
|---|---|---|
| R1 | `whale-message` 变成“业务逻辑垃圾桶” | 只接收已完成业务决策的“发送请求”；模板/渠道/投递是边界，业务状态留在生产者 |
| R2 | mock 默认开启被误用于生产 | 生产部署清单要求 `WHALE_MESSAGE_MOCK=false` 并配置渠道；启动日志打印 mock 状态 |
| R3 | 消息重复投递 | 幂等键唯一索引 + 重复请求返回既有记录；渠道侧仍要求供应商幂等 |
| R4 | 与 `whale-alert` 通道重复建设 | 告警语义留在 alert；后续迁移以“alert 作为生产者”方式收口，不直接删除告警通道 |
| R5 | 新增 CRM 造成域模型漂移 | 本文档明确 CRM/SRM 边界；新增 CRM 需另立 Domain Modeling |

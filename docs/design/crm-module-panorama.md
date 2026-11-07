# CRM 功能模块全景：领域模型与架构设计

> 状态：**M0 底座 + M1 客户主数据 + M2 线索/商机/漏斗/报价/预测/目标已实施（后端，2026-11-04/11-05）**；M3–M6 仍为规划
> 实施结果与偏差：[Wave 5.83 计划 §12](../plans/2026-11-04-wave5.83-crm-panorama.md)
> 输入：用户《CRM 功能模块全景》六大模块 29 个子模块，以及 CRM 与 ERP / OA / 合同 / 消息 / 知识库的边界说明
> 关联计划：[Wave 5.83 CRM 全景实施计划](../plans/2026-11-04-wave5.83-crm-panorama.md)
> 关联需求：REQ-FUNC-080..086、REQ-ARCH-024、REQ-SEC-034
> 关联事实：[服务清单](../service-catalog.md) · [CRM 服务设计稿](crm-service.md) · [ERP 全景设计稿](erp-module-panorama.md) · [MES 全景设计稿](mes-module-panorama.md) · [PDM 全景设计稿](pdm-module-panorama.md)
> 基线：`whale-crm` 门禁骨架（9007 / `/crm/**` / `bluewhale_crm` / 前端 8113 未创建）
>
> 说明：§二“现状基线”保留 **2026-11-04 首切片实施前** 的实测快照，用于解释差距来源；当前代码事实以 [CRM 服务设计稿](crm-service.md) §3 为准。

---

## 一、结论摘要

1. **现状覆盖率很低**：`whale-crm` 只有 3 个 Java 文件、4 张业务表、1 个只读端点、18 个已登记权限、0 个菜单、0 个前端页面。用户全景 6 大模块 29 个子模块中，只有「客户档案」「联系人」「商机」「跟进记录」有表结构雏形，其余均为绿地。
2. **保持模块化单体，不拆微服务**：继续 `whale-crm`（9007、`/crm/**`、`bluewhale_crm`、包 `com.bluewhale.crm`、前端 8113），内部按 `customer / sales / commerce / service / marketing / analytics` 六个业务上下文 + `integration / common` 技术分区组织。
3. **首个切片 = M0 底座 + M1 客户主数据**：客户是全链路的外键和权限归属载体，没有客户分级、查重、公海、跟进和 360，后面的商机、合同、工单、营销都无法闭环。
4. **不复制相邻域能力**：合同正文/模板/审批/签署/归档归 `whale-contract`；发货/库存/应收/信用/记账归 ERP；审批引擎归 `whale-workflow`；消息投递归 `whale-message`；附件字节归 `whale-file`；知识正文归 `whale-doc`；聊天归 `whale-im`。
5. **全景与旧门禁冲突，已由 D1 决策打开**：旧门禁第 4 条明确「本期不做线索评分、营销自动化、客服工单」，与本全景的线索、营销、工单直接冲突。按 D1 采用全景口径，`whale.crm.demand-gate-passed` 已默认为 `true`（`WHALE_CRM_DEMAND_GATE_PASSED=false` 可一键回退）；线索、营销、工单在 M2–M6 分批交付。
6. **客户主数据归属仍未闭环**：ERP 已存在 `erp_customer` 业务主数据，ERP 设计稿 D9 尚未最终定稿。本稿推荐「CRM 是客户关系主数据上游，ERP 保留开票/信用/AR 业务扩展字段」；首切片已按该推荐落地（CRM 不向 ERP 复制客户主数据），契约仍需在 M3 前由 D2 定稿，否则会出现客户双头。
7. **合同/订单/回款/发票必须切清**：CRM 管商务侧合同台账、销售订单、回款计划、开票申请与寄送；ERP 管履约订单、发货、库存、应收核销、信用与财务记账。销售订单通过内网契约幂等下发 ERP，不做分布式事务。
8. **编号避让**：ERP 已占权限 355–599 / 菜单 63–119；PDM 规划占 900–1099 / 180–219；MES 规划占 1100–1399 / 240–299；OA 规划占 1600–1799 / 360–409 / 25123–25127。CRM 使用权限 **1800–1899**、菜单 **410–449**、角色 **25128–25132**，避免四方撞号。

---

## 二、现状基线（2026-11-04 实测）

| 项 | 现状 |
|---|---|
| 后端模块 | `backend/whale-apps/whale-crm`，3 个 Java 文件：启动类、`CrmProperties`、`CrmMetaController` |
| 端口 / 路由 / 库 | 9007、`/crm/**`、`bluewhale_crm` |
| 数据 | `crm_customer`、`crm_contact`、`crm_opportunity`、`crm_follow_up` + `audit_log` / `api_log`；无业务种子 |
| API | 仅 `GET /crm/meta`（`sys:crm:browse`），不读写业务表 |
| 权限 | `migration-5.77-crm.sql` 登记 337–354（18 项）；**未注册菜单** |
| 角色 | 25108 `crm_admin`、25109 `crm_member` |
| 前端 | `frontend/platform-apps/whale-crm` **未创建**；服务目录中的 8113 为规划态 |
| 网关 | `lb://whale-crm`，无 `StripPrefix`；`/crm/internal/**` 由 internal-deny 403 |
| 测试 | 8 项（Controller 3、Flyway 2、ArchUnit 3）；无业务用例 |
| 配置开关 | `whale.crm.demand-gate-passed=false`、客户编码前缀 `CUS`、跟进分页上限 100、合同引用开关 `false` |

**基线结论**：当前 CRM 只是「可构建、可启动、可观测」的门禁骨架，不能把 4 张表误判为已有客户管理能力。

---

## 三、全景差距矩阵

状态：✅ 已有可用能力；△ 有表结构或部分能力；❌ 未开始。

### 3.1 客户管理

| 子模块 | 现状 | 目标能力 | 归属 |
|---|---|---|---|
| 客户档案 | △ `crm_customer` 有编码/名称/状态/负责人/部门/等级/来源/行业/地址/标签 | 增简称、官网、规模、税号、省市区、归一化名称、版本号；客户 360 | CRM `customer` |
| 客户分级 | △ `level` 仅 KEY/NORMAL/POTENTIAL | A/B/C/D 四级；手工评级 + 规则建议；变更留痕 | CRM `customer` |
| 客户公海 | ❌ | 回收规则、自动掉公海、领取/释放/分配、领取上限、并发占位、历史 | CRM `customer` |
| 客户查重 | ❌ | 名称/电话/邮箱/税号/域名归一化匹配；硬冲突阻断、软冲突提示；合并与引用重写 | CRM `customer` |
| 客户跟进 | △ `crm_follow_up` 追加式，有渠道/内容/下次跟进 | 电话/拜访/邮件/IM/微信记录、下次动作、提醒、附件引用、商机关联 | CRM `customer` |
| 客户画像 | ❌ | 偏好、痛点、决策链角色、关键人关系、标签；结构化存储与版本 | CRM `customer` |

### 3.2 销售过程管理

| 子模块 | 现状 | 目标能力 | 归属 |
|---|---|---|---|
| 线索管理 | △ `crm_lead` / `crm_lead_activity` 已建，采集/查重/活动/转化/无效已实现；自动分配与回收未做 | 分配规则、自动回收、线索评分（后续增强） | CRM `sales` |
| 商机管理 | △ `crm_opportunity` 补概率/来源/竞争/阶段时间/乐观锁，阶段机与阶段历史已实现；停留时长统计未做 | 停留时长、竞争态势分析 | CRM `sales` |
| 销售漏斗 | △ 实时聚合已实现（数量/金额/环比转化率，LOST 单列）；停留时长与多维钻取未做 | 线索→接触→方案→谈判→赢单全链路分析、停留时长、按人/团队/来源/行业钻取 | CRM `sales` + `analytics` |
| 报价管理 | △ `crm_quote` / `crm_quote_line` / `crm_quote_approval` 已建，头/行/版本/折扣审批/锁定/改价新版本已实现；转订单未做 | 转订单、报价历史与到期提醒 | CRM `commerce`；审批走 workflow |
| 销售预测 | ❌ | 管道/最佳/承诺三类；按阶段概率加权；月度/季度快照；手工调整留痕 | CRM `sales` |
| 销售目标 | ❌ | 个人/部门/月/季目标；完成率；口径可配置；目标调整审计 | CRM `sales` |

### 3.3 合同与订单管理

| 子模块 | 现状 | 目标能力 | 归属 |
|---|---|---|---|
| 合同管理 | ❌ | 合同模板、合同审批、合同归档、到期提醒；CRM 只保留客户关联台账与续约任务 | `whale-contract` 主体；CRM 只读投影 |
| 订单管理 | ❌ | 从合同/报价生成商务订单；审批后幂等下发 ERP；回读履约状态 | CRM `commerce` 建单；ERP 履约 |
| 回款管理 | ❌ | 回款计划/分期、承诺还款、催收任务、逾期优先级、ERP 应收快照对账 | CRM `commerce` 跟催；ERP 应收台账 |
| 发票管理 | ❌ | 开票申请、审批、发票记录、寄送跟踪；票面记账/税额归财务 | CRM `commerce` 申请与寄送；ERP/财务记账 |

### 3.4 售后服务

| 子模块 | 现状 | 目标能力 | 归属 |
|---|---|---|---|
| 工单管理 | △ `crm_ticket` / `crm_ticket_action` / `crm_ticket_sla` / `crm_service_calendar` 已建，建单/分派/处理/暂停恢复/升级/解决/关闭/重开/取消与逾期派生已实现 | 报修/投诉建单、分派、SLA、处理记录、升级、关闭、重开 | CRM `service` |
| 知识库 | △ `crm_kb_ref` 已建，whale-doc 版本引用、可用状态与命中统计已实现；正文预览未做 | FAQ 元数据、产品/故障标签、文档引用、搜索、命中统计；正文不复制 | CRM `service` 元数据；正文 `whale-doc` |
| 满意度调查 | △ `crm_satisfaction_survey` / `crm_satisfaction_response` 已建，令牌问卷、答卷、低分回访标记已实现；匿名 H5 未做 | 工单关闭触发、令牌化问卷、评分/评论、低分回访任务 | CRM `service` |
| 退换货 | △ `crm_return_request` / `crm_return_item` 已建，申请/审批/清单、outbox 幂等下发 ERP 退货、ERP 单号与状态回读、重试与对账已实现；换货执行未做（字段预留） | 退货/换货申请、审批、ERP 销售退货执行、库存/应收红冲回读 | CRM `service` 申请；ERP 执行 |

### 3.5 营销管理

| 子模块 | 现状 | 目标能力 | 归属 |
|---|---|---|---|
| 营销活动 | ❌ | 展会/直播/促销；预算/实际费用、目标人群、周期、线索归因、ROI | CRM `marketing` |
| 线索培育 | ❌ | 沉默客户/低意向线索的培育任务、内容引用、触达记录；不做复杂自动化 | CRM `marketing` |
| 邮件/短信营销 | ❌ | 模板、批次、限流、重试、退订、黑名单、送达状态 | CRM `marketing` 编排；`whale-message` 投递 |
| 渠道管理 | ❌ | 代理商/经销商档案、授权、报单、冲突校验、业绩统计；佣金结算不做 | CRM `marketing` |

### 3.6 数据分析与驾驶舱

| 子模块 | 现状 | 目标能力 | 归属 |
|---|---|---|---|
| 销售看板 | ❌ | 今日新增线索、本周签单、本月目标完成率、预测缺口、风险商机 | CRM `analytics` |
| 销售漏斗分析 | ❌ | 环节流失、转化率、停留时长、按人/团队/来源/行业钻取 | CRM `analytics` |
| 客户分析 | ❌ | 行业/区域/来源分布、新增/流失、A/B/C/D 结构、集中度 | CRM `analytics` |
| 销售排行 | ❌ | 个人/团队签单额、合同数、回款率；口径与权限受控 | CRM `analytics` |
| 回款分析 + 计划建议 | ❌ | 逾期金额/账龄、承诺履约率、重点逾期客户、催收优先级与行动清单 | CRM `analytics` + `commerce` |

---

## 四、战略建模

### 4.1 业务能力与子域分类

| 子域 | 业务能力 | 类型 | 说明 |
|---|---|---|---|
| 客户关系 | 客户档案、联系人、地址、画像、分级、查重、公海、跟进、360 | **核心域** | 客户身份与关系过程是 CRM 的存在理由 |
| 销售过程 | 线索、商机、漏斗、报价、预测、目标 | **核心域** | 直接决定赢单率与收入预测 |
| 商务履约 | 合同台账、销售订单、回款计划、开票申请 | 支撑域 | 商务侧闭环，但财务/库存履约归 ERP |
| 售后服务 | 工单、SLA、知识引用、满意度、退换货 | 支撑域 | 客户生命周期延伸，依赖订单/合同 |
| 营销 | 活动、培育、触达、渠道 | 支撑域 | 线索上游；投递走消息服务 |
| 分析 | 看板、漏斗、客户、排行、回款、行动计划 | 通用/支撑 | 只读投影 + 规则建议，不做数据仓库 |

### 4.2 限界上下文

| 上下文 | 包 | 聚合 | 对外能力 |
|---|---|---|---|
| `customer` | `com.bluewhale.crm.customer` | Customer、Contact、Address、CustomerProfile、PoolRule | 客户主数据、分级、查重、公海、跟进、360 |
| `sales` | `com.bluewhale.crm.sales` | Lead、Opportunity、SalesTarget、ForecastSnapshot | 线索、商机、漏斗、预测、目标 |
| `commerce` | `com.bluewhale.crm.commerce` | Quote、ContractRef、SalesOrder、CollectionPlan、InvoiceRequest | 报价、合同台账、订单下发、回款计划、开票申请 |
| `service` | `com.bluewhale.crm.service` | Ticket、SlaPolicy、KnowledgeRef、SatisfactionSurvey、ReturnRequest | 工单、SLA、知识引用、满意度、退换货 |
| `marketing` | `com.bluewhale.crm.marketing` | Campaign、NurtureTask、MarketingSend、Partner | 活动、培育、批量触达、渠道 |
| `analytics` | `com.bluewhale.crm.analytics` | MetricSnapshot、ActionPlan、ActionItem | 看板、分析、规则建议、导出 |
| `integration` | `com.bluewhale.crm.integration` | OutboxEvent、SyncLog、ExternalRef | 合同/ERP/工作流/消息/文件/文档客户端 |
| `common` | `com.bluewhale.crm.common` | Money、CustomerRef、DataScope、CrmErrorCode | 共享内核与技术设施 |

### 4.3 上下文映射

| 上游 | 下游 | 关系 | 契约 |
|---|---|---|---|
| CRM | ERP | 客户（上游）→ 业务主数据（下游） | 推荐 CRM 发布 `crmCustomerId` + 最小字段；ERP 保留税号/信用/应收扩展；需 D2 确认 |
| CRM | ERP | 商务订单（上游）→ 履约订单（下游） | `POST /erp/internal/scm/sales-order`，幂等键 `crmOrderNo`，回写 `erpOrderNo` |
| ERP | CRM | 发货/应收/信用（上游）→ 只读投影（下游） | 内网查询或回调；CRM 不写库存/应收台账 |
| `whale-contract` | CRM | 合同（上游）→ 客户关联台账（下游） | `ct_contract.bizType=CRM_CUSTOMER` / `bizId=crmCustomerId`；CRM 只读摘要 |
| CRM | `whale-contract` | 合同发起（下游）→ 合同生命周期（上游） | 创建合同草稿由合同服务承接；CRM 只存 `contractId` |
| CRM | `whale-workflow` | 审批发起方 | 报价折扣、合同、订单、退货、开票申请；存 `approvalInstanceId` |
| CRM | `whale-message` | 投递编排方 | 邮件/短信/站内信/IM；模板、限流、重试、退订 |
| CRM | `whale-file` | 附件引用方 | 只存 `fileId`，字节不落 CRM |
| CRM | `whale-doc` | 知识正文引用方 | 只存文档 ID/版本/分类元数据，不复制正文 |
| CRM | `whale-im` | 会话引用方 | 跟进记录可存会话 ID，不复制聊天消息 |
| CRM | auth | 遵奉者 | 用户、角色、权限、租户、部门数据范围 |

### 4.4 跨域硬边界（不可协商）

1. **合同正文、模板、条款、印章、签署、归档、版本、审批实例**归 `whale-contract`；CRM 不建合同正文表，不复制合同文件。
2. **发货、库存、批次、应收台账、信用占用、坏账、记账凭证**归 ERP；CRM 不写库存/应收/凭证，只读投影。
3. **审批流定义、会签、加签、审批待办**归 `whale-workflow`；CRM 只存实例 ID 与状态快照。
4. **邮件/短信/IM 的实际投递**归 `whale-message`；CRM 不直连 SMTP/SMS 网关。
5. **附件字节**归 `whale-file`；CRM 不落二进制。
6. **知识正文、Wiki、AI 问答**归 `whale-doc` / `whale-agent`；CRM 只维护服务知识元数据与引用。
7. **项目/任务/甘特**归 `whale-pm`；CRM 不建项目管理。
8. **佣金结算、开票记账、税务申报**归财务/ERP；CRM 只做开票申请与寄送跟踪。
9. **禁止跨服务数据库 JOIN、跨上下文 Mapper 访问、跨服务共享表**；只走内网 REST / 事件。
10. **CRM 不保存客户敏感明文日志**；手机号/邮箱/税号在列表、导出、审计中脱敏。

---

## 五、战术建模

### 5.1 聚合与不变量

| 聚合 | 关键不变量 |
|---|---|
| `Customer` | `customer_code` 租户内唯一；`MERGED` 客户只读且指向 `merged_into_id`；有未关闭商机/合同时禁止物理语义删除；公海客户不可被多人同时领取 |
| `Contact` | 每客户最多一个主联系人；主联系人变更需事务内串行；手机/邮箱归一化后参与查重 |
| `Address` | 每客户最多一个默认收货地址、一个默认账单地址 |
| `CustomerProfile` | 画像为结构化版本，修改留痕；决策链角色不能指向已删除联系人 |
| `Lead` | 一个线索只能转化一次；转化必须幂等；无效线索需原因；已转化线索只读 |
| `Opportunity` | 阶段只能按状态机推进；`WON` 必须有金额和客户；`LOST` 必须填原因；赢单后金额不可改，只能通过合同/订单更正 |
| `Quote` | 版本只追加；已审批报价不可改，改价必须新版本；行金额 = 单价 × 数量 ×（1 − 行折扣率），汇总 = 行合计 + 税 − 整单折扣；折扣率按折前未税金额计算 |
| `SalesTarget` | 同一人/部门同一周期唯一；目标调整留痕；完成口径固定为签约合同额（可配置） |
| `ContractRef` | 只读投影；`contractId` 唯一；合同金额/状态/到期日以合同服务为准；不允许 CRM 手工改合同状态 |
| `SalesOrder` | 金额 > 0；至少一行；审批后不可改；下发 ERP 幂等；已发货不可取消，只能走退货/关闭 |
| `CollectionPlan` | 分期金额合计 = 合同可回款金额；已收金额来自 ERP 快照；逾期由计划到期日与实收计算 |
| `InvoiceRequest` | 申请金额 ≤ 合同/订单可开票金额；已开票不可改；红冲走单独流程 |
| `Ticket` | 状态机不可跳转；关闭必须有解决方案；SLA 暂停仅在等待客户；重开保留原工单号 |
| `ReturnRequest` | 申请数量 ≤ 原订单可退数量；审批后不可改；库存/应收由 ERP 执行 |
| `Campaign` | 预算 ≥ 0；结束日期 ≥ 开始日期；人群快照不可变；同一活动同一联系人只触达一次 |
| `MarketingSend` | 幂等键 `campaignId + contactId + channel`；退订/黑名单必须排除 |
| `Partner` | 伙伴编码租户内唯一；报单同一客户同一项目唯一活跃；冲突需人工裁决 |
| `MetricSnapshot` | `metricCode + statDate + dimensionType + dimensionId` 唯一；重跑幂等 |
| `ActionItem` | 去重键防止重复生成；条件解除自动关闭；人工关闭需原因 |

### 5.2 值对象（共享内核 `common`）

| 值对象 | 约束 |
|---|---|
| `Money` | 金额 `DECIMAL(18,2)` + 币种；禁止 double；跨币种必须显式汇率 |
| `CustomerRef` | `customerId + customerCode + name` 快照，仅用于展示，不替代客户聚合 |
| `ContactRef` | `contactId + name + phoneMasked` |
| `Address` | 省/市/区/详细地址；敏感信息脱敏输出 |
| `Percentage` | 0–100，保留 2 位小数 |
| `DateRange` | 开始 ≤ 结束；时区固定 `Asia/Shanghai` |
| `SlaPolicy` | 优先级 + 响应/解决时限 + 工作日历编码 |
| `AudienceRule` | 行业/区域/等级/来源/标签/最近跟进时间的结构化条件 |
| `MetricKey` | 指标编码 + 统计日 + 维度类型/ID |
| `SourceRef` | `sourceType + sourceId + sourceNo`，用于幂等与追溯 |

### 5.3 状态机

```text
线索 Lead：
NEW → CONTACTED → QUALIFIED → CONVERTED
  └──────────────┴───────────┴──► INVALID（必须填原因）

商机 Opportunity：
CONTACT → PROPOSAL → NEGOTIATION → WON
   └────────┴───────────┴────────► LOST（必须填原因）
（V2 将历史 LEAD 迁移为 CONTACT；线索是独立聚合）

报价 Quote：
DRAFT → PENDING_APPROVAL → APPROVED / REJECTED
APPROVED 只读；APPROVED / REJECTED 可改价生成新版本（version+1，root/parent 指向首版）。
转订单（CONVERTED）与到期（EXPIRED）随 M3 商务订单增量补齐。

销售订单 SalesOrder：
DRAFT → APPROVING → APPROVED → PUSHING → PUSHED → PARTIALLY_SHIPPED → SHIPPED → CLOSED
  └──────┴────────► REJECTED / CANCELLED（仅未发货可取消）
PUSHING 失败 → PUSH_FAILED → 重试；CRM 本地不因 ERP 不可用而丢单。

回款计划 CollectionPlan：
PLANNED → DUE → PARTIAL → RECEIVED / OVERDUE → CLOSED
实收来自 ERP；CRM 只维护计划与承诺。

开票申请 InvoiceRequest：
DRAFT → APPROVING → APPROVED → ISSUED → MAILED
  └──────┴────────► REJECTED / VOID

工单 Ticket：
NEW → ASSIGNED → PROCESSING → WAITING_CUSTOMER → RESOLVED → CLOSED
  └──────────────┴──────────────┴────────────► REOPENED → PROCESSING
  └──────────────────────────────────────────► CANCELLED（需原因）

退货 ReturnRequest：
DRAFT → APPROVING → APPROVED → EXECUTING → COMPLETED
  └──────┴────────► REJECTED / CANCELLED

营销活动 Campaign：
DRAFT → SCHEDULED → RUNNING → FINISHED → ARCHIVED
  └──────┴────────► CANCELLED
```

### 5.4 异常与错误码

- 统一使用 `BizException` + `ErrorCode`；CRM 新增 `CrmErrorCode`，编号段 `CRM-40xxx`：
  - `CRM_CUSTOMER_DUPLICATE`（硬冲突）
  - `CRM_CUSTOMER_MERGED`（已合并只读）
  - `CRM_POOL_CLAIM_CONFLICT`（已被领取）
  - `CRM_LEAD_ALREADY_CONVERTED`（重复转化）
  - `CRM_OPPORTUNITY_STAGE_INVALID`（非法阶段）
  - `CRM_QUOTE_LOCKED`（已审批不可改）
  - `CRM_ORDER_PUSH_FAILED`（ERP 下发失败）
  - `CRM_COLLECTION_AMOUNT_MISMATCH`（分期金额不平）
  - `CRM_TICKET_STATE_INVALID`（工单状态非法）
  - `CRM_RETURN_QTY_EXCEEDED`（退货数量超限）
  - `CRM_CAMPAIGN_AUDIENCE_LOCKED`（人群已锁定）
  - `CRM_SURVEY_TOKEN_INVALID`（问卷令牌失效）
- 跨租户一律返回 404，不泄露存在性；无权限 403；业务规则冲突 400/409 按语义区分。
- 外部系统失败统一包装为 `CRM_INTEGRATION_UNAVAILABLE`，保留上游 traceId，不把上游堆栈透给前端。

---

## 六、数据模型规划

### 6.1 命名与库

- 库：`bluewhale_crm`；表前缀 `crm_`；公共字段 `id / tenant_id / create_time / update_time / deleted`。
- 金额 `DECIMAL(18,2)`，百分比 `DECIMAL(5,2)`，时间 `DATETIME`，日期 `DATE`。
- Flyway 版本化，空库自动建表；脚本禁止 `DROP` / `TRUNCATE` / `DELETE`，只允许 `CREATE IF NOT EXISTS`、`ALTER` 与幂等 `INSERT`。
- 租户隔离由 starter 行插件自动追加/填充 `tenant_id`；业务唯一键必须显式带 `tenant_id`。
- 不建跨服务外键；本地只保留 `customer_id` / `contract_id` / `erp_order_id` 等逻辑引用与快照字段。

### 6.2 分期表清单

| 阶段 | 迁移 | 新增/变更表 |
|---|---|---|
| M0 | `V2__crm_foundation.sql` | `crm_common_outbox`、`crm_int_sync_log`、`crm_external_ref`、`crm_idempotency`；`ALTER crm_customer` 补 `short_name/website/scale/tax_no/province/city/region/normalized_name/normalized_domain/normalized_tax_no/version/pool_status/pool_at/last_follow_up_at`；`ALTER crm_contact` 补 `wechat/decision_role/is_decision_maker/normalized_phone/normalized_email/version`；`ALTER crm_follow_up` 补 `result/next_action`；补索引 |
| M1 | `V3__customer_profile.sql` | `crm_customer_address`、`crm_customer_profile`、`crm_customer_merge_log`、`crm_customer_pool_rule`、`crm_customer_owner_log`、`crm_customer_import_task` |
| M2 | `V4__sales_process.sql` + `V5__quote.sql` + `V6__forecast_target.sql` | **已落**：`crm_lead`、`crm_lead_activity`、`crm_opportunity_stage_history`、`crm_quote`、`crm_quote_line`、`crm_quote_approval`、`crm_forecast_snapshot`、`crm_forecast_adjustment`、`crm_sales_target`、`crm_sales_target_adjustment`，`crm_opportunity` 补 `probability/source/competitor/next_action_at/stage_entered_at/version`（转化结果直接写 `crm_lead.customer_id/opportunity_id`，不再单建 `crm_lead_convert_log`） |
| M3 | `V7__commerce.sql`（7f-1）+ `V8__commerce_order.sql`（7f-2）+ `V9__invoice.sql`（7f-3） | **已落**：`crm_contract`、`crm_collection_plan`、`crm_collection_action`（7f-1）；`crm_sales_order`、`crm_sales_order_line`、`crm_order_push_log`（7f-2）；`crm_invoice_request`、`crm_invoice_item`、`crm_ar_snapshot`（7f-3，开票申请 / 申请行 / 客户应收快照） |
| M4 | `V10__service.sql` | `crm_ticket`、`crm_ticket_action`、`crm_ticket_sla`、`crm_service_calendar`、`crm_kb_ref`、`crm_satisfaction_survey`、`crm_satisfaction_response`、`crm_return_request`、`crm_return_item` |
| M5 | `V11__marketing.sql` | `crm_campaign`、`crm_campaign_channel`、`crm_campaign_member`、`crm_campaign_cost`、`crm_nurture_task`、`crm_nurture_log`、`crm_marketing_send`、`crm_unsubscribe`、`crm_partner`、`crm_partner_authorization`、`crm_deal_registration`、`crm_partner_achievement` |
| M6 | `V12__analytics.sql` | `crm_metric_snapshot`、`crm_action_plan`、`crm_action_item`、`crm_export_task`、`crm_dashboard_config` |

> 合同模板与合同正文归 `whale-contract`；CRM 只建台账投影表 `crm_contract`，不复制正文。ERP 销售订单来源字段已在 ERP `V17__scm_crm_order_source.sql` 落地（`source_type/source_id/crm_order_no` + `uk_so_crm_order_no`），CRM 库不建 ERP 订单镜像表。ERP 客户映射（D2 决策）未定，7f-2 仅以 `customer_code` 快照调用 ERP 内网接口，不写客户同步代码。

### 6.3 关键索引与数据量假设

| 表 | 关键索引 | 假设 |
|---|---|---|
| `crm_customer` | `uk(tenant_id, customer_code)`、`idx(tenant_id, owner_id, status)`、`idx(tenant_id, dept_id, status)`、`idx(tenant_id, normalized_name)`、`idx(tenant_id, normalized_tax_no)`、`idx(tenant_id, normalized_domain)`、`idx(tenant_id, pool_status, pool_at)` | 单租户 1 万–10 万客户 |
| `crm_contact` | `idx(tenant_id, customer_id, is_primary)`、`idx(tenant_id, normalized_phone)`、`idx(tenant_id, normalized_email)` | 每客户 1–5 联系人 |
| `crm_follow_up` | `idx(tenant_id, customer_id, create_time)`、`idx(tenant_id, owner_id, next_action_at)` | 单租户每年 10 万–100 万条 |
| `crm_lead` | `uk(tenant_id, lead_no)`、`idx(tenant_id, status, owner_id)`、`idx(tenant_id, source, create_time)` | 每年 1 万–10 万 |
| `crm_opportunity` | `idx(tenant_id, customer_id, stage)`、`idx(tenant_id, owner_id, expected_close_date)`、`idx(tenant_id, stage, expected_close_date)` | 每年 1 万–5 万 |
| `crm_quote` | `uk(tenant_id, quote_no, version)`、`idx(tenant_id, opportunity_id, status)` | 每年 1 万–5 万 |
| `crm_sales_order` | `uk(tenant_id, order_no)`、`uk(tenant_id, source_order_no)`、`idx(tenant_id, customer_id, status)`、`idx(tenant_id, erp_order_no)` | 每年 1 万–3 万 |
| `crm_invoice_request` | `uk(tenant_id, invoice_no)`、`idx(tenant_id, customer_id, status)`、`idx(tenant_id, contract_id, status)`、`idx(tenant_id, origin_invoice_id)`、`idx(tenant_id, approval_instance_id)` | 每年 1 万–3 万 |
| `crm_invoice_item` | `uk(tenant_id, request_id, line_no)` | 每申请 0–20 行 |
| `crm_ar_snapshot` | `uk(tenant_id, customer_id, snapshot_date, currency)`、`idx(tenant_id, snapshot_date)`、`idx(tenant_id, customer_id, snapshot_date)` | 每客户每日 1 条 |
| `crm_collection_installment` | `idx(tenant_id, plan_id, due_date)`、`idx(tenant_id, status, due_date)` | 每合同 1–12 期 |
| `crm_ticket` | `uk(tenant_id, ticket_no)`、`idx(tenant_id, status, assignee_id)`、`idx(tenant_id, sla_due_at, status)`、`idx(tenant_id, customer_id, create_time)` | 每年 1 万–5 万 |
| `crm_campaign_member` | `uk(tenant_id, campaign_id, contact_id)`、`idx(tenant_id, campaign_id, status)` | 单活动 1 千–10 万人 |
| `crm_metric_snapshot` | `uk(tenant_id, metric_code, stat_date, dimension_type, dimension_id)`、`idx(tenant_id, stat_date, metric_code)` | 单租户每年 100 万–500 万行 |
| `crm_action_item` | `uk(tenant_id, dedupe_key)`、`idx(tenant_id, owner_id, status, due_date)` | 每人每天 1–20 条 |

- 保留策略：跟进/工单动作/营销发送记录默认保留 3 年；指标快照保留 5 年；审计日志按平台策略。业务删除一律逻辑删除，归档由后续任务处理。
- 敏感字段：手机号、邮箱、税号、地址、满意度评论。原值可存库，但列表/导出/日志/审计只输出脱敏值；DB 访问最小化。字段级加密作为 D10 可选项，不在 MVP 承诺。

---

## 七、接口规划

### 7.1 约定

- 路径统一 `/crm/**`；返回 `Result<T>` / `PageResult<T>`；分页参数兼容 `current/size` 与 `pageNum/pageSize`。
- 写接口必须带 `@RequirePermission` + `@AuditLog`；幂等写接口增加 `Idempotency-Key` 或业务唯一键。
- 外部调用不在本地事务内；先落本地状态与 outbox，再异步/重试推送。
- 内网接口统一 `/crm/internal/**`，网关 403；调用方需带 `X-Internal-Token`（环境变量注入）与 `X-Trace-Id`。

### 7.2 客户域

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/crm/customer/page` | 客户分页（数据范围、等级、来源、行业、公海状态） |
| GET | `/crm/customer/{id}` | 客户详情 |
| POST | `/crm/customer` | 新建（查重硬冲突阻断） |
| PUT | `/crm/customer/{id}` | 编辑 |
| PUT | `/crm/customer/{id}/status` | 停用/启用 |
| GET | `/crm/customer/{id}/360` | 客户 360（合同/订单/工单/回款降级聚合） |
| GET | `/crm/customer/duplicate-check` | 查重预检 |
| POST | `/crm/customer/{id}/merge` | 客户合并（引用重写、审计） |
| PUT | `/crm/customer/{id}/level` | 评级 |
| GET/PUT | `/crm/customer/{id}/profile` | 画像 |
| GET/POST/PUT/DELETE | `/crm/customer/{id}/contact/**` | 联系人 |
| GET/POST/PUT/DELETE | `/crm/customer/{id}/address/**` | 地址 |
| POST | `/crm/customer/{id}/claim` / `/release` / `/assign` | 公海领取/释放/分配 |
| GET/POST/PUT | `/crm/customer/pool-rule/**` | 公海规则 |
| GET/POST | `/crm/follow-up/**` | 跟进记录（追加式） |
| POST | `/crm/customer/import` | 导入预检/执行 |
| GET | `/crm/customer/export` | 导出（权限 + 脱敏 + 审计） |

### 7.3 销售域

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST/PUT | `/crm/lead/**` | 线索 CRUD、分配、无效、回收 |
| POST | `/crm/lead/{id}/convert` | 转客户/商机（幂等） |
| GET/POST/PUT | `/crm/opportunity/**` | 商机 CRUD |
| PUT | `/crm/opportunity/{id}/stage` | 推进阶段 |
| GET | `/crm/opportunity/funnel` | 漏斗实时视图 |
| GET/POST/PUT | `/crm/quote/**` | 报价分页 / 详情 / 新建 / 编辑（已实现） |
| POST | `/crm/quote/{id}/submit` | 提交审批（已实现） |
| POST | `/crm/quote/{id}/revise` | 改价新版本（已实现） |
| POST | `/crm/quote/{id}/convert` | 转订单（M3） |
| GET/PUT | `/crm/sales-target/**` | 销售目标 |
| GET | `/crm/forecast/**` | 预测与快照 |

### 7.4 商务域

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/crm/contract/page` / `/{id}` | 合同台账只读投影 |
| POST | `/crm/contract/{id}/renewal-task` | 生成续约任务 |
| GET/POST/PUT | `/crm/sales-order/**` | 商务订单 |
| POST | `/crm/sales-order/{id}/submit` / `/push` | 审批 / 下发 ERP |
| GET/POST/PUT | `/crm/collection-plan/**` | 回款计划/分期 |
| GET/POST | `/crm/collection-record/**` | 催收记录与承诺 |
| GET/POST/PUT | `/crm/invoice-request/**` | 开票申请、记录、寄送 |
| GET | `/crm/ar/overdue` | ERP 应收快照（只读） |

### 7.5 售后域

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST/PUT | `/crm/ticket/**` | 工单 |
| POST | `/crm/ticket/{id}/assign` / `/process` / `/resolve` / `/close` / `/reopen` | 状态流转 |
| GET/POST | `/crm/ticket/{id}/action` | 处理记录（追加式） |
| GET/POST/PUT | `/crm/kb-ref/**` | 知识引用与 FAQ 元数据 |
| GET/POST/PUT | `/crm/satisfaction/**` | 问卷模板与结果 |
| POST | `/crm/public/satisfaction/{token}` | 客户填写问卷（签名令牌、限流、一次性） |
| GET/POST/PUT | `/crm/return-request/**` | 退换货申请 |

### 7.6 营销域

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST/PUT | `/crm/campaign/**` | 活动 |
| POST | `/crm/campaign/{id}/audience/preview` / `/lock` | 人群预览/锁定 |
| POST | `/crm/campaign/{id}/send` | 批量触达（消息服务） |
| GET/POST/PUT | `/crm/nurture/**` | 培育任务 |
| GET | `/crm/marketing-send/**` | 发送记录与状态 |
| POST | `/crm/unsubscribe` / `GET /crm/unsubscribe/list` | 退订/黑名单 |
| GET/POST/PUT | `/crm/partner/**` | 渠道伙伴、授权、报单、业绩 |

### 7.7 分析域

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/crm/analytics/dashboard` | 销售看板 |
| GET | `/crm/analytics/funnel` | 漏斗分析 |
| GET | `/crm/analytics/customer` | 客户分析 |
| GET | `/crm/analytics/ranking` | 销售排行 |
| GET | `/crm/analytics/collection` | 回款分析 |
| GET | `/crm/analytics/action-plan` | 行动计划（含建议） |
| POST | `/crm/analytics/export` | 异步导出 |

### 7.8 内网与公开接口

| 方法 | 路径 | 调用方 | 说明 |
|---|---|---|---|
| POST | `/crm/internal/pool/recycle` | `whale-job` 或 CRM 定时任务 | 公海回收，幂等 |
| POST | `/crm/internal/metric/snapshot` | 定时任务 | 指标快照重跑 |
| POST | `/crm/internal/contract/changed` | `whale-contract` | 合同摘要变更回调，幂等 |
| POST | `/crm/internal/erp/ar-changed` | ERP | 应收/发货状态回调，幂等 |
| POST | `/crm/internal/erp/return-changed` | ERP | 退货执行结果回调 |
| POST | `/crm/public/satisfaction/{token}` | 客户 | HMAC 令牌、一次性、限流、无 PII |

### 7.9 事件与出站

- 本地 outbox 事件：`CrmCustomerChanged`、`CrmLeadConverted`、`CrmQuoteApproved`、`CrmOrderApproved`、`CrmOrderPushRequested`、`CrmTicketClosed`、`CrmReturnApproved`、`CrmCampaignFinished`。
- MVP 采用「outbox + 内网 REST 重试」，不新增 Kafka/RabbitMQ 拓扑；如后续需要事件总线，由 D11 另行决策。
- 重试策略：指数退避 + 最大次数 + 死信标记；每次重试带 `sourceEventId`，消费方必须幂等。

---

## 八、架构决策

### 8.1 为什么保持模块化单体

- 客户、商机、报价、订单、工单、营销共享客户与人员权限，拆微服务会带来大量跨服务事务与数据一致性成本。
- 当前团队规模与部署方式适合单服务多上下文；模块化单体可在后续按上下文边界平滑拆分。
- 与 ERP/PDM/MES 的规划口径一致：不拆微服务，只通过内网 REST 集成。

### 8.2 包结构（目标态）

```text
com.bluewhale.crm
├── customer/
│   ├── domain/            Customer、Contact、Address、Profile、PoolRule
│   ├── application/       客户应用服务、查重/合并/公海策略
│   ├── infrastructure/    Mapper、Repository、ERP/合同/文件客户端
│   └── interfaces/        客户 REST、VO、Assembler
├── sales/                 Lead、Opportunity、Quote、Target、Forecast
├── commerce/              ContractRef、SalesOrder、Collection、Invoice
├── service/               Ticket、SLA、KnowledgeRef、Survey、Return
├── marketing/             Campaign、Nurture、Send、Partner
├── analytics/             MetricSnapshot、ActionPlan、Export
├── integration/           Outbox、SyncLog、ExternalRef、Feign 客户端
└── common/                Money、CustomerRef、DataScope、CrmErrorCode
```

- 每个上下文严格四层；`domain` 不依赖 Spring/MyBatis；`interfaces` 不直接调用其他上下文的 Mapper。
- 跨上下文只经应用服务接口或领域事件；禁止跨上下文表 JOIN。

### 8.3 模块协作规则（ArchUnit 固化）

1. `domain` 不得依赖 `infrastructure` / `interfaces` / `application`。
2. `interfaces` 不得依赖其他上下文的 `infrastructure`。
3. `infrastructure` 不得依赖其他上下文的 `domain`。
4. 禁止 `crm_*Mapper` 被非所属上下文引用。
5. 禁止在 SQL 中 JOIN 其他上下文的表。
6. 外部客户端只能出现在 `integration` 或本上下文 `infrastructure`。
7. `/crm/internal/**` 不得暴露给前端路由。

### 8.4 事务与一致性

- 单个聚合的写操作使用本地事务；跨聚合编排用应用服务 + 状态机，不做分布式事务。
- 本地事务提交后写 outbox；由重试任务推送外部系统。外部成功后再回写本地状态。
- 所有外部写接口必须幂等：CRM 下发订单用 `crmOrderNo`，ERP 回调用 `sourceEventId`，合同回调用 `contractId + version`。
- 对账：每日比对 CRM 订单与 ERP 订单状态、CRM 回款计划与 ERP 应收、CRM 退货与 ERP 退货；差异进入 `crm_int_sync_log` 并告警。
- 读投影允许短暂陈旧；360/看板标注数据时间，外部不可用时降级为空并提示，不阻塞主流程。

### 8.5 前端

- 新建 `frontend/platform-apps/whale-crm`，端口 8113，base `/crm`，接入基座菜单与权限码。
- 页面按模块分区：客户、销售、合同订单、售后、营销、分析；共用客户选择器、金额/日期、脱敏展示、权限按钮、导入导出组件。
- 大列表默认分页 + 服务端筛选；导出走异步任务，避免浏览器超时。
- 移动端不单独建 App；一线销售/售后的轻量操作优先保证桌面端可用，后续再评估小程序/H5。

### 8.6 部署与配置

- 单服务 `whale-crm`，端口 9007；前端 8113；网关路由保持 `lb://whale-crm`。
- 新增配置：`whale.crm.integration.*`（合同/ERP/消息/文档地址与超时）、`whale.crm.pool.*`（回收扫描批次）、`whale.crm.analytics.*`（快照时间/保留天数）、`whale.crm.survey.*`（令牌密钥/有效期）。
- 所有外部地址走 Nacos/环境变量，不写死；密钥不进 Git。
- 里程碑开关：除总门禁外，按模块增加 `whale.crm.module.customer-enabled` 等开关，默认 `false`，灰度时逐模块打开。

---

## 九、权限、菜单与角色预留

### 9.1 编号避让

| 域 | 权限号段 | 菜单号段 | 说明 |
|---|---|---|---|
| ERP | 355–599 | 63–119 | 已规划/部分落地 |
| PDM | 900–1099 | 180–219 | 规划 |
| MES | 1100–1399 | 240–299 | 规划 |
| OA | 1600–1799 | 360–409 | 规划 |
| **CRM 新增** | **1800–1899** | **410–449** | 本稿预留 |
| CRM 存量 | 337–354 | 57–62（未启用） | 保留原语义，不重复分配 |

> 同日多份全景规划存在号段重叠（MES 与 PDM 都曾声明 180–239 / 900–999；OA 与 CRM 也曾同时声明 1600–1799 / 360–409）。CRM 主动避让到 1800+ / 410+；规划收口时应统一复核一次，避免实施时撞号。

### 9.2 分段启用

| 阶段 | 权限 | 菜单 | 角色 |
|---|---|---|---|
| M0 底座 | 1800–1809 | 410–412 | 登记 25128–25132（按阶段启用） |
| M1 客户 | 1810–1839 | 413–420 | 25128 `crm_sales`、25129 `crm_sales_manager` |
| M2 销售 | 1840–1859 | 421–426 | 复用上述 + 25132 `crm_analyst`（只读） |
| M3 商务 | 1860–1880 | 427–431 | 25129 |
| M4 售后 | 1881–1890 | 432–436 | 25130 `crm_service` |
| M5 营销 | 1891–1898 | 437–440 | 25131 `crm_marketing` |
| M6 分析 | 1899 + 1900–1905（占用预留段） | 441–449 | 25132 `crm_analyst` |
| 预留 | 1900–1999 | 450–469 | 后续增强 |

- 权限码命名：`sys:crm:<context>:<action>`，例如 `sys:crm:customer:list`、`sys:crm:pool:claim`、`sys:crm:order:push`、`sys:crm:invoice:issue`、`sys:crm:ticket:assign`、`sys:crm:campaign:send`、`sys:crm:analytics:export`。
- 号段占用：7f-1 用 1860–1865，7f-2 用 1866–1873，7f-3 用 1874–1880（`sys:crm:invoice:list/create/submit/issue/send/red-flush` + `sys:crm:ar:reconcile`）；M4 由 1875–1884 顺延为 1881–1890，M5 / M6 同步顺延，M6 已触及 1899 上限需启用 1900+ 预留段，实施前统一复核。
- 菜单只注册已交付页面；未交付页面不挂空入口，避免点击即 403。
- 迁移脚本幂等：`INSERT ... SELECT ... WHERE NOT EXISTS`；只增不删；角色授权按最小权限。

### 9.3 数据范围

- 销售：本人 + 本部门（主管含下级部门）；服务：本人处理/被分配 + 客户归属部门；市场：活动/线索范围；分析：按角色只读全域或本部门。
- 公海：所有人可见可领，但领取受上限与并发控制；释放/分配需权限。
- 合同/订单/回款：按客户归属与负责人收口；财务字段只读。
- 导出：独立权限码 + 脱敏 + 审计；超过阈值走异步任务。

---

## 十、非功能与安全

| 项 | 要求 |
|---|---|
| 租户隔离 | 所有业务表带 `tenant_id`；跨租户一律 404；内网接口也校验租户 |
| 功能权限 | 每个写接口 `@RequirePermission`；未授权 403；前端按钮同步隐藏 |
| 数据范围 | 应用层统一 `DataScope` 解析；禁止在 Controller 手写越权判断；D10 决定部门数据来源 |
| 敏感数据 | 手机/邮箱/税号/地址列表脱敏；日志/审计不记录明文；导出默认脱敏，明文导出需额外权限 |
| 审计 | 客户合并、公海领取/释放、报价审批、订单下发、回款承诺、发票、工单流转、退货、活动发送、导出全部 `@AuditLog` |
| 公开问卷 | HMAC 令牌、一次性、有效期、限流、无 PII 入 URL、防枚举；网关仅开放 `/crm/public/satisfaction/**` |
| 内网接口 | 网关 403 + `X-Internal-Token` + traceId；不暴露给前端；失败不泄露堆栈 |
| 文件 | 附件只存 `fileId`；下载/预览走 `whale-file` 票据；CRM 不代理字节 |
| 性能 | 列表 P95 < 500ms；360 P95 < 1s（外部降级）；看板 P95 < 3s；导出异步 |
| 可用性 | 合同/ERP/消息/文档不可用时，CRM 主流程可继续；外部状态标注“数据延迟/不可用” |
| 可观测 | 每个集成调用有耗时/成功率/重试次数指标；outbox 积压、死信、对账差异告警 |
| 保留 | 跟进/工单动作/发送记录 3 年；指标快照 5 年；审计按平台策略；逻辑删除，归档任务后续实现 |
| 测试 | 领域单测 + 应用服务测试 + Controller 测试 + ArchUnit + Flyway 空库/重复执行 + 前端 lint/build/test + 关键 E2E |

---

## 十一、开放问题（编码前必须确认）

| # | 问题 | 推荐/默认 | 阻塞 |
|---|---|---|---|
| D1 | 是否以本全景替换旧门禁「不做线索/营销/工单」的限制？六模块是否全做，还是分期裁剪？ | 六模块全规划，M1/M2 先做；营销自动化/线索评分降为增强 | M0 |
| D2 | 客户主数据归属：CRM 唯一主数据，还是 ERP 继续持有业务主数据？ | CRM 为客户关系上游，ERP 保留税号/信用/AR 扩展；ERP 增 `crm_customer_id`；历史数据按税号/名称人工确认映射 | M0/M1/M3 |
| D3 | 合同/订单边界：CRM 是否建商务订单并下发 ERP？ | 是；CRM `crm_sales_order` → ERP 履约订单，幂等键 `crmOrderNo`；合同正文归合同服务 | M3 |
| D4 | 回款/发票边界：CRM 是否管实收与开票记账？ | CRM 只做回款计划/催收/开票申请/寄送；实收核销、税额、记账归 ERP/财务 | M3 |
| D5 | 目标与预测口径：签约合同额、订单额还是回款额？概率权重如何取？ | 默认签约合同额；概率按阶段固定（CONTACT 20%、PROPOSAL 50%、NEGOTIATION 75%、WON 100%），可配置 | M2 |
| D6 | 报价折扣审批阈值与审批链？ | 折扣 ≤ 5% 主管批；5%–15% 销售总监；>15% 总经理；走 workflow | M2 |
| D7 | 售后 SLA：优先级、响应/解决时限、工作日历、是否支持节假日？ | 默认 P1 2h/8h、P2 4h/24h、P3 8h/72h；先按 7×8 工作时段，不做节假日表 | M4 |
| D8 | 知识库：CRM 建 FAQ 正文，还是只引用 `whale-doc`？ | 只建元数据 + 文档引用；正文归 `whale-doc`；AI 问答归 `whale-agent` | M4 |
| D9 | 营销触达：哪些渠道可用？退订/黑名单/频率如何控制？ROI 用收入还是毛利？ | 邮件/短信/站内信可用；默认每联系人每活动一次、每月不超过 N 次；ROI 默认收入口径，毛利可选 | M5 |
| D10 | 数据范围从哪取部门树？字段级加密是否纳入 MVP？ | 推荐 auth 提供 `/auth/internal/user/{id}/data-scope`；字段加密不进 MVP，先脱敏 + 最小权限 | M0 |
| D11 | 定时任务与事件：用 `whale-job` 还是 CRM 内置调度？outbox 是否升级 MQ？ | 优先 `whale-job` 处理器调用 CRM 内网端点；若改动窗口不允许，CRM `@Scheduled` + Redisson 锁，二选一；MVP 不上 MQ | M0 |
| D12 | 分析口径与行动计划：指标定义、快照时间、保留期、建议规则由谁维护？ | 指标定义进本稿附录/配置；每日 02:00 快照；规则配置化，产品确认优先级 | M6 |

---

## 十二、变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-04 | 初稿：基于用户《CRM 功能模块全景》建立 6 上下文、29 子模块差距矩阵、V2–V8 迁移草案、权限 1800–1899 / 菜单 410–449 / 角色 25128–25132 预留、D1–D12 开放问题；规划零代码 |
| 2026-11-04 | M2 核心落地：线索 / 线索活动 / 转化 / 商机阶段机 / 阶段历史 / 漏斗；V4 新增三表并补商机七列；权限 1840–1849；报价、预测、目标仍为规划 |
| 2026-11-04 | M2 报价增量：`commerce` 上下文落地报价头/行/审批三表（V5），状态机 `DRAFT→PENDING_APPROVAL→APPROVED/REJECTED`，折扣审批对接 workflow，审批后锁定与改价新版本；权限 1850–1854；转订单仍为规划 |
| 2026-11-05 | M2 预测 / 目标增量：`sales` 上下文落地预测快照 / 预测调整 / 销售目标 / 目标调整四表（V6），月季周期、个人 / 部门范围、手工调整留痕、完成率（暂按赢单商机金额）；权限 1855–1859；M3–M6 迁移顺延 V7–V10 |
| 2026-11-07 | M3 7f-3 发票与应收对账：`commerce` 上下文落地 `crm_invoice_request` / `crm_invoice_item` / `crm_ar_snapshot` 三表（V9），开票申请状态机、workflow 审批、开票 / 寄送 / 红冲、ERP 按客户应收只读快照与账龄对账；权限 1874–1880、菜单 430；M4–M6 迁移顺延 V10–V12，M4 权限顺延 1881–1890 |
| 2026-11-07 | M4 7f-4a 工单与 SLA：`service` 上下文落地工单 / 处理记录 / SLA 策略 / 工作日历，V10 一次建齐 M4 九表（含 4b/4c 预留）；工单状态机（NEW→ASSIGNED→PROCESSING/WAITING_CUSTOMER→RESOLVED→CLOSED，重开/取消）、7×8 日历按工作分钟推进（含午休）、暂停恢复顺延、逾期只读派生；权限 1881–1890、菜单 432–436；4b 知识引用与满意度、4c 退换货待继续 |
| 2026-11-07 | M4 7f-4b 知识引用与满意度：知识保存调用 whale-doc `/doc/internal/page/{id}` 校验并记录可用版本（不可用降级 `doc_available=false`，不复制正文），命中 `hit_count+1`；工单关闭自动签发 `CSAT-yyyyMMdd-%04d` 一次性令牌（HMAC-SHA256 含租户，默认 7 天），答卷一次后置 USED，重复 / 过期抛 41068，同 IP+工单 10 分钟 5 次限流；低分（总分 ≤3 或任一维度 ≤2）写 `follow_up_required` 并向原工单追加 `FOLLOW_UP` 动作；错误码 41067/41068、权限 1886–1888、前端知识库与满意度两页；4c 退换货待继续 |
| 2026-11-08 | M4 7f-4c 退换货：复用 `V10` 已建 `crm_return_request` / `crm_return_item`；`ReturnRequest` 状态机（DRAFT→PENDING_APPROVAL→APPROVED/REJECTED→EXECUTING→COMPLETED/FAILED，CANCELLED），可退量按 `已申请量 + 本行 ≤ 订单行数量` 校验；审批回调同事务写 `crm_common_outbox`，按 `RET-yyyyMMdd-XXXX` 幂等下发 ERP `POST /erp/internal/scm/sales-return`（ERP `V18__scm_crm_return_source.sql` 增 `crm_return_no/source_type/source_id` 与 `uk_sr_crm_return_no`，复用退货 `create/post` 过账），失败指数退避重试、按单号回查对账并回写 `erpReturnNo/erpStatus`；错误码 41069–41073、权限 1889–1890、菜单 436、前端退换货页；提交 `e56e4994` |

# 术语表

> 统一中英文口径，避免同一概念在不同文档里换名。新增术语先登记再使用。
> 出处列给出**权威定义所在**，本表只做索引与一句话解释。

## 1. 平台与架构

| 术语 | 英文 / 缩写 | 说明 | 出处 |
|---|---|---|---|
| 限界上下文 | Bounded Context | 一个业务边界的建模单元；跨上下文只通过内网 REST/事件交互，不共享表 | [architecture.md](architecture.md) |
| 遵奉者 | Conformist | 下游直接接受上游模型、不做翻译的关系；本平台业务服务对 `whale-auth` 均为遵奉者 | [ddd-whale-auth.md](design/ddd-whale-auth.md) |
| 主数据 | Master Data | 单一归属、被其他域引用的核心实体（客户归 CRM、供应商归 ERP、员工归 ERP） | [crm-service.md](design/crm-service.md) §2 |
| 投影 | Projection | 只读副本/快照，不拥有写入权；如 CRM 合同台账投影、ERP 客户应收快照 | [crm-service.md](design/crm-service.md) |
| 发件箱 | Outbox | 本地事务内写事件表、异步投递的模式；用于跨服务最终一致 | [distributed.md](design/distributed.md) |
| 租户 | Tenant | 数据隔离单位；请求头 `X-Tenant-Id`，缺省 1，由租户行插件自动追加与填充 | [backend.md](backend.md) |
| 权限码 | Permission Code | 形如 `sys:<域>:<动作>` 的授权标识，网关注入 `X-Permission-Codes`，服务侧注解消费 | [api.md](api.md) |
| RBAC / ABAC | Role/Attribute-Based Access Control | 角色授权 + 属性（数据范围）约束；六层级：系统→菜单→接口→按钮→数据→属性 | [architecture.md](architecture.md) |
| 数据权限 | Data Scope | 同一接口按部门/本人/自定义范围过滤数据行 | [database.md](database.md) |
| 内部令牌 | Internal Token | `X-Internal-Token`；服务间 `/**/internal/**` 调用凭证，网关强制剥离外部同名头 | [api.md](api.md) |
| 统一返回体 | `Result<T>` / `PageResult<T>` | `{code,message,data}` 与 `{records,total,size,current}` | [api.md](api.md) |
| 幂等键 | Idempotency Key | 防重复提交；`@Idempotent` 基于 Redis SETNX，键 `{prefix}:{biz}:{token}` | [api.md](api.md) §幂等 |
| 门禁骨架 | Gate Skeleton | 服务只暴露 `/xxx/meta` 与最小底座，业务端点未开放的状态 | [service-catalog.md](service-catalog.md) |
| 需求门禁 | Demand Gate | 编码前必须通过的评审；CRM 以 `whale.crm.demand-gate-passed` 唯一开关落地 | [ADR-003](design/adr/ADR-003-crm-boundary-and-demand-gate.md) |
| 冻结模块 | Frozen Module | 暂停投入的可选能力，解冻需满足 F 编号列出的全部条件 | [frozen-optional-modules.md](design/frozen-optional-modules.md) |
| 波次 | Wave | 一次有 spec / 计划 / 检查报告 / devlog 的最小交付批次 | [plans/INDEX.md](plans/INDEX.md) |
| SLO | Service Level Objective | 服务可用性与时延目标，配套 PromQL 与告警 | [ops/slo.md](ops/slo.md) |
| 事实层 / 知识桶 | Fact Layer / Knowledge Bucket | 知识 Agent 的结构化事实与文档入库白名单；白名单 default-deny | [ai.md](ai.md) |
| 逻辑删除 | Logical Delete | `deleted` 标记；唯一编码软删时改写为 `原值#D{id}` 释放唯一键 | [database.md](database.md) |

## 2. 场景（whale-scene）

| 术语 | 英文 / 缩写 | 说明 | 出处 |
|---|---|---|---|
| 场景 | Scene | 租户内可发布的城池/空间树 | [scene-service.md](design/scene-service.md) |
| 场景节点 | Scene Node | 区域、建筑、入口等树节点；`node_key` 场景内唯一、禁止环 | scene-service.md §4.1 |
| 入口绑定 | Node Binding | 节点到系统/页面/知识库/外链的映射，含 `permission_code` 与 URL 白名单 | scene-service.md §4.4 |
| 可见性 | Visibility | 角色/用户/全体 + 允许/拒绝；DENY 优先 | scene-service.md §4.3 |
| 布局快照 | Layout Version | 发布时生成的布局 JSON + checksum，支持回溯 | scene-service.md §5 |
| 2D 降级 | 2D Fallback | 无 WebGL/低配/首屏超预算/FPS 低时回退 2D 导航 | [ADR-002](design/adr/ADR-002-scene-2d-first-3d-optional.md) |

## 3. 客户关系（whale-crm）

| 术语 | 英文 / 缩写 | 说明 | 出处 |
|---|---|---|---|
| 客户 | Customer | 客户主数据聚合根；`customer_code` 租户内唯一 | [crm-service.md](design/crm-service.md) §4 |
| 联系人 | Contact | 客户下的联系人，每客户至多一个主联系人 | 同上 |
| 线索 | Lead | 未转化的潜在客户；`NEW→CONTACTED→QUALIFIED→CONVERTED`，可转 `INVALID` | 同上 |
| 商机 | Opportunity | 有金额与阶段的销售机会；`CONTACT→PROPOSAL→NEGOTIATION→WON/LOST` | 同上 |
| 销售阶段 | Stage | 商机生命周期状态，流转留痕于 `crm_opportunity_stage_history` | 同上 |
| 漏斗 | Funnel | 按阶段聚合的商机金额/数量视图 | [crm-module-panorama.md](design/crm-module-panorama.md) |
| 公海 | Public Pool | 无归属客户的共享池，按规则回收/领取 | crm-service.md §4 |
| 跟进 | Follow-up | 追加式（append-only）客户互动记录 | 同上 |
| 报价版本 | Quote Version | 报价以 `quote_no + version` 版本化，审批后只读，改价生成新版本 | 同上 |
| 商务订单 | Commerce Order | 报价/合同转单后经 workflow 审批，按 `CRM-SO-{orderNo}` 幂等下发 ERP | [service-catalog.md](service-catalog.md) §二 |
| 合同台账 | Contract Ledger | CRM 侧合同投影，正文与签署归 `whale-contract` | crm-service.md §2 |
| 回款计划 | Collection Plan | 合同应收的分期计划与催收动作 | crm-service.md §2 |
| CSAT | Customer Satisfaction Score | 工单关闭后一次性令牌问卷满意度 | crm-service.md §4 |
| SLA | Service Level Agreement | 售后工单响应/解决时限，按 7×8 工作日历计算逾期 | crm-service.md §4 |
| SRM | Supplier Relationship Management | 供应商关系管理；供应商主数据归 ERP/SRM，**不归 CRM** | ADR-003 §2.3 |

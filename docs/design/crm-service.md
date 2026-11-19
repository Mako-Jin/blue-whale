# 客户关系服务设计（whale-crm）

> 状态：**M0–M6 已落地（Wave 5.83，后端 + 前端 8113）**：含 M1-4 客户合并、M1-9 CSV 导入导出、M5 营销、M6 分析
> 关联需求：REQ-FUNC-080..086、REQ-ARCH-024、REQ-SEC-034
> 关联规划：[CRM 功能模块全景](crm-module-panorama.md) / [Wave 5.83 计划](../plans/2026-11-04-wave5.83-crm-panorama.md)
> 关联决策：[CRM/SRM 与消息服务边界决策](../plans/2026-10-28-crm-srm-and-message-service-decisions.md)、REQ-ARCH-011
> 实现位置：`whale-backend/whale-apps/whale-crm`（后端）· `whale-frontend/platform-apps/whale-crm`（前端 8113，base `/crm`）

> **2026-11-04 门禁与范围更新**：收到用户《CRM 功能模块全景》后，D1 采纳全景口径（六上下文全规划、M1/M2 优先），
> `whale.crm.demand-gate-passed` 已改为默认 `true`；§1 的旧门禁结论与“不做线索/营销/工单”承诺已由
> [CRM 全景设计稿](crm-module-panorama.md) 与 [Wave 5.83 计划](../plans/2026-11-04-wave5.83-crm-panorama.md) 取代。
> 一键回退仍可用 `WHALE_CRM_DEMAND_GATE_PASSED=false` 恢复到仅 `/crm/meta` 的维护态。

---

## 1. 门禁结论

| 项 | 结论 |
|---|---|
| 需求门禁 D2 | **已通过（2026-11-04，D1 采纳全景口径）**：用户提供 6 大模块 29 子模块全景；客户主数据归属、验收口径与里程碑已在 [全景设计稿](crm-module-panorama.md) 与 [Wave 5.83 计划](../plans/2026-11-04-wave5.83-crm-panorama.md) 定稿 |
| 本波范围 | M0 底座 + M1 客户主数据 + M2 线索/商机/漏斗核心 + 报价单（版本 / 折扣审批 / 锁定 / 改价新版本）；预测、目标与 M3–M6 按计划推进 |
| 不做什么（本波） | 不做客户合并、CSV 导入导出、前端 8113、报价转订单；不登记菜单 413–426；不写业务种子数据 |
| 唯一开关 | `whale.crm.demand-gate-passed`（默认 `true`）；置 `false` 即回退到仅 `/crm/meta` 的维护态 |
| 下一步 | M1 收尾（合并 / 导入导出 / 前端）→ M3 商务履约（报价转订单 / 订单 / 回款） |

### 1.1 进入条件（历史记录，均已满足或被 D1 取代）

1. ~~出现真实的客户 / 联系人 / 商机 / 销售漏斗需求~~ → 已由用户《CRM 功能模块全景》满足；
2. 客户主数据归属：CRM 拥有客户，ERP 只拥有供应商，合同域「相对方」只保存 `crmCustomerId` 快照（D2 待 M3 前正式回填 ADR-003）；
3. Domain Modeling 复核与 ADR-003 定稿：域模型已在全景设计稿完成，ADR 回填待 M2/M3；
4. ~~产品确认本期不做线索评分、营销自动化、客户服务工单~~ → 已由 D1 决策取消该限制，改为 M2–M6 交付。

---

## 2. 职责边界

| 拥有（M1 起逐步启用） | 不拥有 |
|---|---|
| 客户、联系人、商机、销售阶段、跟进记录 | 供应商主数据（ERP / 未来 `whale-srm`） |
| 客户 360 只读视图 | 合同正文、签署、印章（`whale-contract`） |
| 客户编码规则与数据权限范围 | 审批（`whale-workflow`）、消息投递（`whale-message`）、附件字节（`whale-file`） |
| —— | 线索评分、营销自动化、客服工单（明确不做） |

上下文映射：`whale-crm → auth` 为遵奉者；合同 / 消息 / 工作流 / 文件是下游内网 REST；
`whale-crm` 与 `whale-erp` **无关系**，客户 ≠ 供应商，禁止共享主数据表。

---

## 3. 已落地内容（Wave 5.83 M0–M6）

| 项 | 内容 |
|---|---|
| 模块 | `whale-backend/whale-apps/whale-crm`，端口 **9007**，前缀 `/crm/**`，库 `bluewhale_crm` |
| 启动类 | `WhaleCrmApplication`（`@EnableScheduling`，供公海回收任务使用） |
| 配置 | `CrmProperties`：门禁（默认 `true`）、客户编码前缀、跟进分页上限、合同引用开关、默认客户等级 `C`、公海回收开关/表达式/批大小、CSV 导入/导出行数上限；`CrmMarketingProperties`（`whale.crm.marketing`）、`CrmAnalyticsProperties`（`whale.crm.analytics`） |
| 元信息接口 | `GET /crm/meta`（权限 `sys:crm:browse`）→ 门禁状态、说明、里程碑、边界约束 |
| DDL | `V1__baseline.sql` 四表 + 审计表；`V2__crm_foundation.sql` 补客户/联系人/跟进字段与归一化索引、集成四表；`V3__customer_profile.sql` 地址/画像/公海规则/负责人留痕；`V4__sales_process.sql` 线索/线索活动/商机阶段历史 + 商机补概率/来源/竞争/下次行动/阶段时间/乐观锁；`V5__quote.sql` 报价头/行/审批记录三表；`V6__forecast_target.sql` 预测快照/预测调整/销售目标/目标调整四表；`V7`–`V10` 合同台账/订单/发票/售后；`V11__marketing.sql` 营销 8 表；`V12__analytics.sql` 分析 4 表 |
| 权限 | `migration-5.77-crm.sql`：337–354 保留；`migration-5.83-crm.sql`：1800 敏感明文 + 1810–1830 客户主数据；`migration-5.84-crm-sales.sql`：1840–1849 线索与商机；`migration-5.85-crm-quote.sql`：1850–1854 报价；`migration-5.86-crm-forecast-target.sql`：1855–1859 预测与目标；`migration-5.83-crm-m1-merge-import.sql`：1831–1833 合并/导入/导出；`migration-5.83-crm-m5-marketing.sql`：1891–1898；`migration-5.83-crm-m6-analytics.sql`：1899–1905 |
| 角色 | 25108 `crm_admin`、25109 `crm_member` 保留；新增 25128 `crm_sales`、25129 `crm_sales_manager`、25130 `crm_service`、25131 `crm_marketing`、25132 `crm_analyst` |
| 网关 | `lb://whale-crm`，不配 `StripPrefix`；`/crm/internal/**` 由 internal-deny 403 |
| 前端 | 已创建：`whale-frontend/platform-apps/whale-crm`，8113，base `/crm`，已接入基座菜单与 qiankun |
| 测试 | 329 项（架构、迁移、公共、客户（含合并/导入导出）、销售、报价、预测、目标、商务、售后、营销、分析、Controller）；Checkstyle 通过 |

### 3.1 分层与上下文

```text
com.bluewhale.crm
├── common            # CrmActor / CrmMasking / CrmPermissions / CrmErrorCode（共享内核）
├── config            # CrmProperties
├── interfaces        # 仅 /crm/meta
├── customer          # M1 客户主数据（domain / application / infrastructure / interfaces）
├── sales             # M2 线索 + 商机 + 漏斗（domain / application / infrastructure / interfaces）
├── integration       # M1 只需商机摘要端口适配器
├── commerce          # M2 报价（domain / application / infrastructure / interfaces）；M3 订单/回款/发票预留
├── service / marketing / analytics   # M4 售后 / M5 营销 / M6 分析（domain / application / infrastructure / interfaces）
└── WhaleCrmApplication
```

- 依赖方向：`interfaces → application → domain`，`infrastructure` 实现 `domain` 端口；业务上下文**不得**直接依赖 `integration`。
- ArchUnit 规则：六上下文四层归属、领域层无 Spring/MyBatis、应用层不碰接口/持久化、接口层不碰持久化、客户上下文不反向依赖其他上下文、共享内核不依赖业务上下文。

### 3.2 M1 已交付接口

| 方法 | 路径 | 用途 | 权限码 |
|---|---|---|---|
| `GET` | `/crm/customer/page` | 客户分页（数据范围收口） | `sys:crm:customer:list` |
| `GET` | `/crm/customer/duplicate-check` | 查重（硬冲突 / 软冲突候选） | `sys:crm:customer:duplicate` |
| `GET` | `/crm/customer/{id}`, `/crm/customer/{id}/360` | 详情 / 客户 360 | `sys:crm:customer:list` |
| `POST` | `/crm/customer` | 新建（编码 `CUS-{yyyy}-{6位}`） | `sys:crm:customer:create` |
| `PUT` | `/crm/customer/{id}` | 编辑 | `sys:crm:customer:update` |
| `PUT` | `/crm/customer/{id}/status`, `.../level` | 启停 / 分级（A/B/C/D） | `sys:crm:customer:status` / `...:level` |
| `DELETE` | `/crm/customer/{id}` | 逻辑删除 | `sys:crm:customer:delete` |
| `GET`/`PUT` | `/crm/customer/{id}/profile` | 画像查询 / 保存（版本递增） | `sys:crm:profile:view` / `...:save` |
| `GET`/`POST`/`PUT`/`DELETE` | `/crm/customer/{id}/contact/**` | 联系人（主联系人唯一） | `sys:crm:contact:list` / `...:manage` |
| `GET`/`POST`/`PUT`/`DELETE` | `/crm/customer/{id}/address/**` | 地址（默认收货/账单唯一） | `sys:crm:address:list` / `...:manage` |
| `GET`/`POST` | `/crm/follow-up/page`, `/crm/follow-up` | 跟进查询 / 追加新增（回写最近跟进时间） | `sys:crm:follow-up:list` / `...:create` |
| `GET` | `/crm/pool/page` | 公海分页 | `sys:crm:pool:view` |
| `POST` | `/crm/pool/{id}/claim`, `.../release` | 领取（原子更新 + 每日上限）/ 释放 | `sys:crm:pool:claim` / `...:release` |
| `POST` | `/crm/pool/{id}/assign` | 分配（全量数据范围） | `sys:crm:pool:assign` |
| `POST` | `/crm/pool/recycle` | 手动回收 | `sys:crm:pool:recycle` |
| `GET`/`PUT` | `/crm/pool/rule` | 公海规则查询 / 保存 | `sys:crm:pool:rule` |
| `GET` | `/crm/pool/{id}/owner-logs` | 负责人变更留痕 | `sys:crm:pool:view` |

### 3.3 M2 已交付接口（线索、商机与报价）

| 方法 | 路径 | 用途 | 权限码 |
|---|---|---|---|
| `GET` | `/crm/lead/page` | 线索分页（数据范围收口） | `sys:crm:lead:list` |
| `GET` | `/crm/lead/duplicate-check` | 线索查重（名称 / 电话 / 邮箱归一化） | `sys:crm:lead:list` |
| `GET` | `/crm/lead/{id}` | 线索详情（电话 / 邮箱脱敏） | `sys:crm:lead:list` |
| `POST` | `/crm/lead` | 新建线索（编号 `LEAD-{yyyy}-{6位}`，重复需确认） | `sys:crm:lead:create` |
| `PUT` | `/crm/lead/{id}` | 编辑线索（已转化为只读） | `sys:crm:lead:update` |
| `PUT` | `/crm/lead/{id}/status` | 状态推进（`NEW→CONTACTED→QUALIFIED`） | `sys:crm:lead:update` |
| `PUT` | `/crm/lead/{id}/assign` | 分配负责人（全量数据范围） | `sys:crm:lead:assign` |
| `PUT` | `/crm/lead/{id}/invalid` | 标记无效（原因必填） | `sys:crm:lead:invalid` |
| `POST` | `/crm/lead/{id}/convert` | 转客户 / 商机（一次且幂等） | `sys:crm:lead:convert` |
| `GET`/`POST` | `/crm/lead/{id}/activity` | 线索活动分页 / 追加（NEW 自动转 CONTACTED） | `sys:crm:lead:list` / `sys:crm:lead:update` |
| `GET` | `/crm/opportunity/page` | 商机分页（数据范围收口） | `sys:crm:opportunity:list` |
| `GET` | `/crm/opportunity/funnel` | 销售漏斗（阶段数量 / 金额 / 环比转化率） | `sys:crm:opportunity:list` |
| `GET` | `/crm/opportunity/{id}`, `/{id}/history` | 商机详情 / 阶段历史 | `sys:crm:opportunity:list` |
| `POST` | `/crm/opportunity` | 新建商机（客户必填，初始 `CONTACT`） | `sys:crm:opportunity:create` |
| `PUT` | `/crm/opportunity/{id}` | 编辑商机（已关闭拒绝） | `sys:crm:opportunity:update` |
| `PUT` | `/crm/opportunity/{id}/stage` | 阶段流转（严格顺序；WON 需金额、LOST 需原因） | `sys:crm:opportunity:stage` |
| `GET` | `/crm/quote/page` | 报价分页（数据范围收口） | `sys:crm:quote:list` |
| `GET` | `/crm/quote/{id}` | 报价详情（含行与审批状态） | `sys:crm:quote:list` |
| `POST` | `/crm/quote` | 新建草稿（编号 `QUOTE-{yyyy}-{6位}`，版本 1） | `sys:crm:quote:create` |
| `PUT` | `/crm/quote/{id}` | 编辑草稿（全量替换行） | `sys:crm:quote:update` |
| `POST` | `/crm/quote/{id}/submit` | 提交审批（折扣档位写入 workflow 变量） | `sys:crm:quote:submit` |
| `POST` | `/crm/quote/{id}/revise` | 改价新版本（仅 `APPROVED` / `REJECTED`） | `sys:crm:quote:revise` |
| `POST` | `/crm/internal/quote/approval-callback` | 审批结果回调（幂等；内网 403） | 无权限码 |
| `GET` | `/crm/forecast/page`, `/{id}`, `/{id}/adjustments` | 预测快照分页 / 详情 / 调整记录 | `sys:crm:forecast:list` |
| `POST` | `/crm/forecast/generate` | 生成 / 重算预测快照（同周期同范围覆盖，幂等） | `sys:crm:forecast:manage` |
| `PUT` | `/crm/forecast/{id}/adjust` | 手工调整管道 / 最佳 / 承诺金额并留痕 | `sys:crm:forecast:manage` |
| `GET` | `/crm/sales-target/page`, `/{id}`, `/{id}/completion`, `/{id}/adjustments` | 目标分页 / 详情 / 完成率 / 调整记录 | `sys:crm:target:list` |
| `POST` | `/crm/sales-target` | 新建个人 / 部门目标（同周期同范围唯一） | `sys:crm:target:manage` |
| `PUT` | `/crm/sales-target/{id}` | 调整目标金额（变更须填原因并留痕） | `sys:crm:target:manage` |
| `DELETE` | `/crm/sales-target/{id}` | 逻辑删除目标 | `sys:crm:target:delete` |

### 3.4 与计划的偏差（本波明确）

| 项 | 计划 | 本波实现 | 原因 |
|---|---|---|---|
| M0-5 数据范围 | auth 内网 `data-scope` 接口 + 部门树 | 角色判定：`tenant_admin/crm_admin/crm_sales_manager/crm_analyst` 全量，其余仅本人 | auth 内网数据范围接口尚未提供，先安全降级为本人范围 |
| M0-6 集成底座 | 合同/ERP/工作流/消息/文件/文档 6 个客户端 + outbox 重试 | V2 已建 outbox / 同步日志 / 外部引用 / 幂等四表；仅商机摘要端口真实接读，其余在 360 中声明“不可用” | 按里程碑分阶段接入，避免未联调的空壳客户端 |
| M1-4 客户合并 | 合并预览 + 引用重写 | **已实现**：合并预览 + 执行，`MergeReferencePort` 端口下发 `sales` / `commerce` / `service` 三个适配器重写引用；合同域由 `ContractServiceMergeReferenceAdapter` 调 `whale-contract` 内网 `references/count`、`references/rewrite`（`biz_type=CRM_CUSTOMER`）重写「合同服务」引用，受 `whale.crm.contract.enabled` 开关控制（默认 `false`） | 权限 1831 |
| M1-9 导入导出 | CSV 预检 + 异步导出 | **已实现**：CSV 解析/预检/落库与导出，受 `customer-import-max-rows` / `customer-export-max-rows` 限制，展示侧掩码 | 权限 1832–1833；异步任务与 `whale-file` 联通后置 |
| M1-10 前端 | 8113 页面 | **已创建**：客户/销售/商务/售后/营销/分析/行动计划/客户工具页面，接入基座菜单与 qiankun | — |
| M1-5 自动回收 | 定时回收默认开启 | 已实现任务，`whale.crm.pool-recycle-enabled` **默认 false** | 灰度先手动触发验证回收规则，再打开定时 |
| M1-11 审计 | 全部写操作 `@AuditLog` | 18 个写接口已加注解（`crm_customer` / `crm_contact` / `crm_customer_address` / `crm_customer_profile` / 公海 / 跟进）；画像与联系人写接口 `recordParams=false`，避免备注、微信等自由文本落库 | 审计切面按字段名脱敏，自由文本无规则可依，直接关闭参数记录 |
| M2 报价 | 报价头/行/版本、折扣审批、锁定、转订单 | **头/行/版本、折扣审批、审批后锁定、改价新版本已交付**；转订单未做 | 转订单依赖 M3 商务订单与 ERP 下发；权限 1850–1854 |
| M2 预测 / 目标 | 管道/最佳/承诺预测、阶段概率加权、个人/部门目标 | **已交付**：预测快照（管道/最佳/承诺/加权、月季、个人/部门、手工调整留痕）+ 目标（个人/部门、月季、调整留痕、完成率）；完成率暂按同期赢单商机金额近似 | 合同台账 M3 上线后切换为签约额口径；概率取商机当前 `probability` 字段，可复算 |
| M2 线索分配规则 | 轮询 / 区域 / 行业自动分配 | 仅手工分配（`assign`，需全量数据范围） | 规则引擎后置；手工分配先满足主管派单 |
| M2 阶段停留时长 | 漏斗展示每阶段平均停留 | 阶段历史已落 `changed_at`，未做停留统计 | 留待 M6 分析口径统一后计算，避免重复定义 |

> `/crm/meta` 不读写任何客户表；门禁关闭时业务接口统一返回 `41000`。

---

## 4. 数据模型（M1 按 V1–V3、M2 按 V4/V5 落地；M3–M6 表见全景设计稿 §六）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `crm_customer` | `customer_code` / `name` / `status` / `owner_id` / `dept_id` / `level` / `source` / `industry` / `address` / `tags` | 客户聚合根；`customer_code` 租户内唯一 |
| `crm_contact` | `customer_id` / `name` / `phone` / `email` / `title` / `is_primary` / `status` | 每客户至多一个主联系人 |
| `crm_lead` | `lead_no` / `name` / `status` / `source` / `owner_id` / `dept_id` / `normalized_name` / `normalized_phone` / `normalized_email` / `customer_id` / `opportunity_id` | 线索聚合根；`lead_no` 租户内唯一；转化后只读 |
| `crm_lead_activity` | `lead_id` / `channel` / `content` / `result` / `next_action_at` / `created_by` | 线索活动追加式 |
| `crm_opportunity` | `customer_id` / `name` / `amount` / `stage` / `probability` / `source` / `competitor` / `next_action_at` / `stage_entered_at` / `expected_close_date` / `owner_id` / `lost_reason` / `version` | 商机阶段状态机；`version` 乐观锁 |
| `crm_opportunity_stage_history` | `opportunity_id` / `from_stage` / `to_stage` / `amount` / `probability` / `lost_reason` / `operator_id` / `changed_at` | 阶段流转留痕 |
| `crm_quote` | `quote_no` / `version` / `root_quote_id` / `parent_quote_id` / `opportunity_id` / `customer_id` / `status` / `subtotal_amount` / `tax_amount` / `discount_amount` / `total_amount` / `discount_rate` / `approval_instance_id` / `lock_version` | 报价聚合根；`uk(tenant_id, quote_no, version)`；审批后只读 |
| `crm_quote_line` | `quote_id` / `line_no` / `product_name` / `quantity` / `unit_price` / `discount_rate` / `tax_rate` / `line_amount` | 报价行；行金额快照 |
| `crm_quote_approval` | `quote_id` / `quote_version` / `instance_id` / `status` / `approval_level` / `discount_rate` / `comment` / `submitter_id` / `decided_at` | 审批记录；`uk(tenant_id, instance_id)` 保证回调幂等 |
| `crm_follow_up` | `customer_id` / `opportunity_id` / `channel` / `content` / `next_action_at` / `created_by` | 追加式（append-only） |

### 4.1 ER 图（M1–M2 核心，M3–M6 见全景稿 §六）

```text
crm_customer (客户聚合根)
  │ 1
  ├── N  crm_contact (每客户至多 1 个主联系人: is_primary)
  ├── N  crm_opportunity (商机)
  │        ├── N  crm_opportunity_stage_history (阶段流转留痕)
  │        ├── N  crm_quote (报价, quote_no + version 版本链)
  │        │        ├── N  crm_quote_line (报价行)
  │        │        └── N  crm_quote_approval (审批记录)
  │        └── N  crm_follow_up (跟进, 可选挂商机)
  │
crm_lead (线索聚合根)
  ├── N  crm_lead_activity (线索活动)
  └── 0..1 → crm_customer / crm_opportunity (转化后只读, 存 customer_id/opportunity_id)

crm_customer ── N  crm_follow_up (仅挂客户的跟进)
crm_quote ── root_quote_id / parent_quote_id → crm_quote (版本链自关联)
```

关系约束：

- 所有表带 `tenant_id`，由租户行插件自动追加；跨租户引用视为数据错误。
- `crm_lead.customer_id` / `opportunity_id` 为**转化结果引用**，转化后线索只读，不回写客户档案。
- 报价版本链：首版 `root_quote_id = 自身 id`，改价生成 `version+1` 且 `parent_quote_id` 指向上一版；审批后只读。
- 客户合并时按已登记的重写清单更新下游引用 ID（以实现为准），**不复制客户档案**。

### 4.2 索引说明

| 表 | 唯一键 | 主要查询索引 | 说明 |
|---|---|---|---|
| `crm_customer` | `uk_crm_customer_code(tenant_id, customer_code)` | `idx_crm_customer_owner(tenant_id, owner_id)`、`idx_crm_customer_dept(tenant_id, dept_id)`、`idx_crm_customer_status(tenant_id, status, update_time)` | 负责人/部门数据权限过滤 + 状态分页 |
| `crm_contact` | —— | `idx_crm_contact_customer(tenant_id, customer_id, is_primary)`、`idx_crm_contact_phone(tenant_id, phone)` | 主联系人排序；按电话查重 |
| `crm_lead` | `uk_crm_lead_no(tenant_id, lead_no)` | `idx_crm_lead_owner(tenant_id, owner_id, status)`、`idx_crm_lead_status(tenant_id, status, update_time)`、归一化三索引 `normalized_name/phone/email` | 查重走归一化字段，避免原值大小写/格式差异漏判 |
| `crm_lead_activity` | —— | `idx_crm_lead_activity_lead(tenant_id, lead_id, create_time)` | 追加式按时间读取 |
| `crm_opportunity` | —— | `idx_crm_opportunity_customer(tenant_id, customer_id, stage)`、`idx_crm_opportunity_owner(tenant_id, owner_id)`、`idx_crm_opportunity_close(tenant_id, expected_close_date)` | 漏斗按阶段聚合；预计成交日驱动提醒 |
| `crm_opportunity_stage_history` | —— | `idx_crm_opportunity_history(tenant_id, opportunity_id, changed_at)` | 阶段停留时长计算 |
| `crm_quote` | `uk_crm_quote_no_version(tenant_id, quote_no, version)` | `idx_crm_quote_opp(tenant_id, opportunity_id, status)`、`idx_crm_quote_owner(tenant_id, owner_id, status)`、`idx_crm_quote_root(tenant_id, root_quote_id, version)` | 版本链查询与「当前有效版本」判定 |
| `crm_quote_line` | —— | `idx_crm_quote_line_quote(tenant_id, quote_id, line_no)` | 行按序读取 |
| `crm_quote_approval` | `uk_crm_quote_approval_instance(tenant_id, instance_id)` | `idx_crm_quote_approval_quote(tenant_id, quote_id, status)` | `instance_id` 唯一保证审批回调幂等 |
| `crm_follow_up` | —— | `idx_crm_follow_customer(tenant_id, customer_id, create_time)`、`idx_crm_follow_opportunity(tenant_id, opportunity_id)` | 时间线读取；append-only 不更新 |

> M3–M6 表（合同台账/订单/发票/售后/营销/分析）的索引以各迁移脚本为准，口径见 [crm-module-panorama.md](crm-module-panorama.md) §六 与 `service-catalog.md` 的迁移清单。

阶段状态机（已实现）：

```text
线索 Lead：
NEW → CONTACTED → QUALIFIED → CONVERTED
  └──────────────┴───────────┴──► INVALID（必须填原因）

商机 Opportunity：
CONTACT → PROPOSAL → NEGOTIATION → WON
   └────────┴───────────┴────────► LOST（必须填写 lost_reason）

报价 Quote：
DRAFT → PENDING_APPROVAL → APPROVED / REJECTED
APPROVED 只读；APPROVED / REJECTED 可 revise 生成新版本（version+1，root/parent 指向首版）
```

- 非法跳转返回业务错误 `41023` / `41027`；
- `WON` 必须金额 > 0（缺失返回 `41028`），`LOST` 必须填原因（缺失返回 `41029`）；
- 线索 `CONVERTED` 与商机 `WON` / `LOST` 为终态，终态不可再编辑或流转；
- 报价非草稿状态返回 `41032`；审批回调按 `instanceId + PENDING` 幂等（`41035` / `41036` 为审批不可用 / 状态非法）；
- 客户存在未关闭商机时禁止停用/删除。

---

## 5. API 清单（M1 / M2 已实现；M3–M6 见计划 §4）

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| `GET` | `/crm/meta` | 门禁状态（**已实现**） | `sys:crm:browse` |
| `GET` | `/crm/customer/page` | 客户分页（**已实现**） | `sys:crm:customer:list` |
| `GET` | `/crm/customer/{id}` | 客户详情（**已实现**） | `sys:crm:customer:list` |
| `GET` | `/crm/customer/{id}/360` | 客户 360（**已实现**） | `sys:crm:customer:list` |
| `GET` | `/crm/customer/duplicate-check` | 查重（**已实现**） | `sys:crm:customer:duplicate` |
| `POST` | `/crm/customer` | 新建客户（**已实现**） | `sys:crm:customer:create` |
| `PUT` | `/crm/customer/{id}` | 编辑客户（**已实现**） | `sys:crm:customer:update` |
| `PUT` | `/crm/customer/{id}/status` | 停用 / 启用（**已实现**） | `sys:crm:customer:status` |
| `PUT` | `/crm/customer/{id}/level` | 客户分级（**已实现**） | `sys:crm:customer:level` |
| `DELETE` | `/crm/customer/{id}` | 逻辑删除（**已实现**） | `sys:crm:customer:delete` |
| `GET`/`PUT` | `/crm/customer/{id}/profile` | 画像（**已实现**） | `sys:crm:profile:view` / `sys:crm:profile:save` |
| `GET`/`POST`/`PUT`/`DELETE` | `/crm/customer/{id}/contact/**` | 联系人（**已实现**） | `sys:crm:contact:list` / `sys:crm:contact:manage` |
| `GET`/`POST`/`PUT`/`DELETE` | `/crm/customer/{id}/address/**` | 地址（**已实现**） | `sys:crm:address:list` / `sys:crm:address:manage` |
| `GET`/`POST` | `/crm/follow-up/page`、`/crm/follow-up` | 跟进（**已实现**） | `sys:crm:follow-up:list` / `sys:crm:follow-up:create` |
| `GET`/`POST`/`PUT` | `/crm/pool/**` | 公海（**已实现**） | `sys:crm:pool:view/claim/release/assign/rule/recycle` |
| `GET` | `/crm/lead/page`、`/{id}`、`/{id}/activity` | 线索分页 / 详情 / 活动（**已实现**） | `sys:crm:lead:list` |
| `GET` | `/crm/lead/duplicate-check` | 线索查重（**已实现**） | `sys:crm:lead:list` |
| `POST` | `/crm/lead` | 新建线索（**已实现**） | `sys:crm:lead:create` |
| `PUT` | `/crm/lead/{id}`、`/{id}/status` | 编辑 / 状态推进（**已实现**） | `sys:crm:lead:update` |
| `PUT` | `/crm/lead/{id}/assign`、`/{id}/invalid` | 分配 / 标记无效（**已实现**） | `sys:crm:lead:assign` / `sys:crm:lead:invalid` |
| `POST` | `/crm/lead/{id}/convert`、`/{id}/activity` | 转化（幂等）/ 追加活动（**已实现**） | `sys:crm:lead:convert` / `sys:crm:lead:update` |
| `GET` | `/crm/opportunity/page`、`/crm/opportunity/funnel` | 商机分页 / 漏斗（**已实现**） | `sys:crm:opportunity:list` |
| `GET` | `/crm/opportunity/{id}`、`/{id}/history` | 商机详情 / 阶段历史（**已实现**） | `sys:crm:opportunity:list` |
| `POST`/`PUT` | `/crm/opportunity`、`/crm/opportunity/{id}`、`/{id}/stage` | 新建 / 编辑 / 阶段流转（**已实现**） | `sys:crm:opportunity:create` / `...:update` / `...:stage` |
| `GET`/`POST`/`PUT` | `/crm/quote/**`、`/crm/internal/quote/approval-callback` | 报价分页 / 详情 / 新建 / 编辑 / 提交 / 改价（**已实现**） | `sys:crm:quote:list/create/update/submit/revise` |
| `GET`/`POST`/`PUT`/`DELETE` | `/crm/forecast/**`、`/crm/sales-target/**` | 预测 / 目标（**M2 增量已交付**） | 1855–1859 |

> 完整请求/响应字段以 `customer.application.view` / `sales.application.view` / `commerce.application.view` 与 `interfaces.controller` 为准；权限码与 `CrmPermissions`、`migration-5.83-crm.sql` / `migration-5.84-crm-sales.sql` / `migration-5.85-crm-quote.sql` / `migration-5.86-crm-forecast-target.sql` 一致。

**权限编号 1800–1899**（已用 1800 敏感明文、1810–1830 客户主数据、1831–1833 合并/导入/导出、1834–1839 分析、1840–1849 线索/商机、1850–1854 报价、1855–1859 预测与目标、1889–1890 退换货、1891–1898 营销；337–354 为历史登记，保留不动）；
**菜单 410–449**（413–426 为客户/销售/商务/售后页面，437–440 营销、441–449 分析）。
不提前注册菜单，避免用户看到点击即 403 的空入口。

---

## 6. 安全与数据权限（M1 已落地，M2 起扩展）

1. 跨租户访问一律 404，不泄露存在性；
2. 客户可见范围按本人 / 本部门 / 授权范围收口（数据权限）；
3. 联系人手机号 / 邮箱在列表、导出与审计中脱敏；
4. 客户与商机写操作落 `@AuditLog`，不记录敏感明文；
5. 合同只引用 `crmCustomerId` 快照，不复制客户主数据；
6. 所有导出 / 列表接口受权限码控制。

---

## 7. 验收标准（M1 已自检；M2 起按里程碑复核）

1. `customer_code` 租户内唯一，重复新增 / 改码返回业务错误；
2. 只能看到本人 / 本部门 / 授权范围客户，跨租户 404；
3. 手机号 / 邮箱按规则脱敏；
4. 商机阶段只能按状态机流转，非法跳转返回 400；
5. 有未关闭商机的客户禁止停用 / 删除；合并客户保留引用可追溯；
6. 合同相对方通过 `crmCustomerId` 引用客户；
7. 客户 / 商机写操作落审计；
8. 后端单测、Checkstyle、ArchUnit、前端 lint/build、E2E 通过。

---

## 8. 风险与缓解

| # | 风险 | 缓解 |
|---|---|---|
| R5 | 需求未确认即编码 → 域模型漂移、返工 | 门禁四项条件 + ADR-003 + Domain Modeling 复核 |
| R6 | 客户主数据与 ERP/合同重复 | CRM 唯一客户主数据；合同只存引用快照；供应商归 ERP/SRM |
| R10 | 权限/菜单编号冲突 | 权限 1800–1899（已用 1800、1810–1830）、菜单 410–449（413–420 预留）；迁移脚本幂等、只增不删 |

---

## 9. 变更触发清单（M2 起同步）

1. `whale.crm.demand-gate-passed=true`（环境变量 `WHALE_CRM_DEMAND_GATE_PASSED`）；
2. ADR-003 定稿与 Domain Modeling 复核记录；
3. 前端交付时追加菜单 413–420 迁移脚本（幂等）；
4. 新建前端 `whale-frontend/platform-apps/whale-crm`（8113、base `/crm`）并接入基座；
5. CI 镜像矩阵与部署契约计数同步（后端已有 `whale-crm`；前端新增时再 +1）；
6. 每个里程碑完成后更新本设计稿状态与 `docs/reviews/` 检查报告、`docs/devlog/` 记录（M1 已完成）。

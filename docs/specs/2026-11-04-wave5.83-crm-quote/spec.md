# Wave 5.83 · M2 增量：报价单与审批回调 规格

- 状态：**待批准**（批准后进入实施）
- 上级计划：[2026-11-04-wave5.83-crm-panorama.md](../../plans/2026-11-04-wave5.83-crm-panorama.md)（M2 增量）
- 目标模块：`whale-backend/whale-apps/whale-crm`
- 业务上下文：`commerce`（商务：报价 / 订单）
- 关联服务：`whale-workflow`（审批发起，CRM 不复制审批引擎）
- 范围外：报价转订单（M3）、销售预测 / 目标、前端 8113

## 1. 背景与现状

- M0 底座 + M1 客户主数据 + M2 线索 / 商机 / 漏斗核心已交付并提交（`de2f502a`，123 项测试全绿）。
- M2 剩余增量：报价（头 / 行 / 版本 / 折扣审批 / 审批后锁定 / 改价新版本）、预测、目标。本规格只做**报价**。
- 已确认决策：
  - D6 折扣审批阈值：**≤5% 主管；5%–15% 销售总监；>15% 总经理**（作为 workflow 变量）。
  - 已审批报价**只读**；改价必须 `revise` 生成新版本（版本只追加，不原地升级）。
  - 报价必须挂可见商机；复用 `OpportunityAppService.detail(id, actor)` 做数据范围校验。
- 复用范式：`ContractApprovalClient`（`POST /workflow/instance` 发起 + 身份/权限码头透传）、`DocApprovalServiceImpl`（回调按 `(instanceId, PENDING)` 条件更新实现幂等）。

## 2. 设计决策

### D-1 报价编号与版本
- 编号 `QUOTE-{yyyy}-{6位序号}`，按租户 + 年份取 `crm_quote.quote_no` 最大序号加一。
- `version` 从 1 开始，是**业务版本号**；另设 `lock_version` 作为 MyBatis-Plus `@Version` 乐观锁字段，避免业务版本与并发控制混用。
- 唯一键 `uk_crm_quote_no_version (tenant_id, quote_no, version)`；同号多版本共存。
- 首版 `root_quote_id = 自身 id`；`revise` 时 `root_quote_id` 保持首版 id、`parent_quote_id` 指向**首版**、`version = 源版本 + 1`。

### D-2 状态机
`QuoteStatus`：`DRAFT → PENDING_APPROVAL → APPROVED / REJECTED`。

| 操作 | 允许状态 | 结果 |
|---|---|---|
| `create` | — | `DRAFT`，version=1 |
| `update` | `DRAFT` | 覆盖头与行（全量替换） |
| `submit` | `DRAFT` | `PENDING_APPROVAL` + 写审批记录 + 发起 workflow |
| `revise` | `APPROVED` / `REJECTED` | 克隆为新 `DRAFT`，version+1 |
| 回调通过 | `PENDING_APPROVAL` | `APPROVED` + 写 `approved_at`，此后只读 |
| 回调驳回 | `PENDING_APPROVAL` | `REJECTED`，可 `revise` |

`APPROVED` 报价禁止 `update` / `submit`；`REJECTED` 禁止 `update` / `submit`（只能 `revise`）。非法状态一律 `QUOTE_STATE_CONFLICT`。

### D-3 金额与折扣口径
- 行金额：`lineAmount = unitPrice × quantity × (1 − discountRate/100)`，四舍五入 2 位。
- 头：`subtotalAmount = Σ lineAmount`；`taxAmount = Σ lineAmount × taxRate/100`；`discountAmount = 整单折扣金额（请求显式填写，≥0）`；`totalAmount = subtotalAmount + taxAmount − discountAmount`。
- 折扣率（审批阈值）：`discountRate = (折前未税金额 − subtotalAmount + discountAmount) / 折前未税金额 × 100`，其中 `折前未税金额 = Σ unitPrice × quantity`；四舍五入 2 位。
- `discountAmount` 不得超过 `subtotalAmount + taxAmount`；`totalAmount` 必须 > 0；否则 `QUOTE_AMOUNT_INVALID`。
- 审批层级：`≤5% → SUPERVISOR`；`5% < rate ≤ 15% → DIRECTOR`；`>15% → GENERAL_MANAGER`。

### D-4 审批发起
- 配置 `whale.crm.quote-approval`：`enabled`（默认 true）、`definition-id`、`workflow-url`（默认 `http://127.0.0.1:8083`）、连接/读取超时。
- 发起走 `POST /workflow/instance`，请求体 `definitionId / title / businessKey / variables`；业务键 `quote:{quoteId}:v{version}`。
- 透传可信身份头：`X-User-Id`、`X-Username`、`X-Permission-Codes`、`X-User-Roles`、`X-Tenant-Id`（与 `ContractApprovalClient` 一致）。
- 变量固定包含：`quoteId / quoteNo / version / opportunityId / customerId / totalAmount / discountRate / approvalLevel / applicant`。
- 发起失败抛 `SYSTEM_ERROR("报价审批发起失败，请稍后重试")`；**先发起后落库**，失败时事务回滚，报价保持 `DRAFT`。
- `enabled=false` 或 `definition-id` 为空：`QUOTE_APPROVAL_UNAVAILABLE`，不做静默直通。

### D-5 回调与幂等
- 内网接口 `POST /crm/internal/quote/approval-callback`，网关对 `/crm/internal/**` 外部 403，不加权限码。
- 请求 `instanceId`（必填）、`status`（1 通过 / 2 驳回 / 3 撤销）、`comment`。
- 按 `instanceId` 查 `crm_quote_approval.status = PENDING`：不存在或已终态直接返回 `false`（幂等，不报错）。
- 命中后条件更新审批记录与报价状态（`quote.status = PENDING_APPROVAL` 条件），更新 0 行返回 `false`。
- `status=3` 撤销按 `REJECTED` 处理；`status` 越界抛 `QUOTE_APPROVAL_STATE_CONFLICT`。

### D-6 权限与数据范围
- 权限号段 1850–1854（1855 预留，不登记内部回调）：
  - 1850 `sys:crm:quote:list` → `GET /crm/quote/page,/crm/quote/{id}`
  - 1851 `sys:crm:quote:create` → `POST /crm/quote`
  - 1852 `sys:crm:quote:update` → `PUT /crm/quote/{id}`
  - 1853 `sys:crm:quote:submit` → `POST /crm/quote/{id}/submit`
  - 1854 `sys:crm:quote:revise` → `POST /crm/quote/{id}/revise`
- 授权：`crm_admin / tenant_admin / crm_sales_manager / crm_sales` 全量 1850–1854；`crm_marketing / crm_service / crm_analyst` 仅 1850。
- 数据范围沿用 `CrmActor.allDataScope()`：非全量角色只能看 / 改本人 `owner_id` 的报价；越权按不存在处理。
- 写接口均加 `@RequirePermission` + `@AuditLog`；回调仅内部，不加权限注解。

## 3. 数据库变更

新增 `whale-backend/whale-apps/whale-crm/src/main/resources/db/migration/V5__quote.sql`
（`CREATE TABLE IF NOT EXISTS`；不写 DROP / TRUNCATE / DELETE；无业务种子数据；表间逻辑外键不建物理 FK）。

### 3.1 `crm_quote`
`id / tenant_id / create_time / update_time / deleted` 沿用约定，另含：

| 列 | 类型 | 说明 |
|---|---|---|
| `quote_no` | VARCHAR(64) NOT NULL | 报价编号 |
| `version` | INT NOT NULL DEFAULT 1 | 业务版本号 |
| `root_quote_id` | BIGINT NULL | 首版 id |
| `parent_quote_id` | BIGINT NULL | 来源版本（首版 id） |
| `opportunity_id` | BIGINT NOT NULL | 商机 |
| `customer_id` | BIGINT NOT NULL | 客户（来自商机） |
| `title` | VARCHAR(128) NOT NULL | 报价标题 |
| `status` | VARCHAR(20) NOT NULL DEFAULT 'DRAFT' | DRAFT / PENDING_APPROVAL / APPROVED / REJECTED |
| `currency` | VARCHAR(8) NOT NULL DEFAULT 'CNY' | 币种 |
| `valid_until` | DATE NULL | 有效期 |
| `subtotal_amount` | DECIMAL(18,2) NOT NULL DEFAULT 0 | 行合计 |
| `tax_amount` | DECIMAL(18,2) NOT NULL DEFAULT 0 | 税额 |
| `discount_amount` | DECIMAL(18,2) NOT NULL DEFAULT 0 | 整单折扣 |
| `total_amount` | DECIMAL(18,2) NOT NULL DEFAULT 0 | 含税总价 |
| `discount_rate` | DECIMAL(5,2) NOT NULL DEFAULT 0 | 综合折扣率 |
| `remark` | VARCHAR(500) NULL | 备注 |
| `owner_id` / `dept_id` | BIGINT NULL | 负责人 / 部门 |
| `approval_instance_id` | BIGINT NULL | 当前审批实例 |
| `submitted_at` / `approved_at` | DATETIME NULL | 提交 / 通过时间 |
| `lock_version` | INT NOT NULL DEFAULT 0 | 乐观锁 |

索引：`uk_crm_quote_no_version (tenant_id, quote_no, version)`、`idx_crm_quote_opp (tenant_id, opportunity_id, status)`、`idx_crm_quote_owner (tenant_id, owner_id, status)`、`idx_crm_quote_root (tenant_id, root_quote_id, version)`。

### 3.2 `crm_quote_line`
`id / tenant_id / create_time / update_time / deleted` +：

| 列 | 类型 | 说明 |
|---|---|---|
| `quote_id` | BIGINT NOT NULL | 报价 |
| `line_no` | INT NOT NULL | 行号 |
| `product_name` | VARCHAR(128) NOT NULL | 产品 / 服务 |
| `specification` | VARCHAR(128) NULL | 规格 |
| `unit` | VARCHAR(16) NULL | 单位 |
| `quantity` | DECIMAL(18,4) NOT NULL | 数量 |
| `unit_price` | DECIMAL(18,4) NOT NULL | 单价 |
| `discount_rate` | DECIMAL(5,2) NOT NULL DEFAULT 0 | 行折扣率 0–100 |
| `tax_rate` | DECIMAL(5,2) NOT NULL DEFAULT 13 | 税率 0–100 |
| `line_amount` | DECIMAL(18,2) NOT NULL | 行金额快照 |
| `remark` | VARCHAR(255) NULL | 行备注 |

索引：`idx_crm_quote_line_quote (tenant_id, quote_id, line_no)`。

### 3.3 `crm_quote_approval`
`id / tenant_id / create_time / update_time / deleted` +：

| 列 | 类型 | 说明 |
|---|---|---|
| `quote_id` | BIGINT NOT NULL | 报价 |
| `quote_version` | INT NOT NULL | 提交时版本 |
| `instance_id` | BIGINT NOT NULL | workflow 实例 |
| `status` | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| `approval_level` | VARCHAR(32) NULL | SUPERVISOR / DIRECTOR / GENERAL_MANAGER |
| `discount_rate` | DECIMAL(5,2) NULL | 提交时折扣率快照 |
| `comment` | VARCHAR(500) NULL | 审批意见 |
| `submitter_id` | BIGINT NULL | 提交人 |
| `submitted_at` / `decided_at` | DATETIME NULL | 提交 / 决策时间 |

索引：`uk_crm_quote_approval_instance (tenant_id, instance_id)`、`idx_crm_quote_approval_quote (tenant_id, quote_id, status)`。

## 4. 代码变更清单

### 4.1 domain（`com.bluewhale.crm.commerce.domain`）
- `model/Quote.java`：聚合根，含 `updateAmounts()`、`submit(instanceId, rate, level, at)`、`applyApproval(approved, comment, at)`、`revise(...)`、`locked()`；`revise`/`applyApproval` 内做状态校验并抛 `CrmErrorCode`。
- `model/QuoteLine.java`：行模型 + `calculateAmount()`（`unitPrice × quantity × (1 − discountRate/100)`，2 位）。
- `model/QuoteStatus.java`：`DRAFT / PENDING_APPROVAL / APPROVED / REJECTED` + `from(String)`。
- `model/QuoteApprovalStatus.java`：`PENDING / APPROVED / REJECTED`。
- `model/QuoteDiscountLevel.java`：`resolve(BigDecimal rate)`：≤5 主管 / ≤15 总监 / 其余总经理。
- `model/QuoteQuery.java`：继承 `PageQuery`，字段 `keyword / opportunityId / customerId / status / ownerId`。
- `model/QuoteApproval.java`：审批记录模型。
- `repository/QuoteRepository.java`：`page / count / findById / findLineByQuoteId / insertHeader / insertLines / update / reviseInsert / maxSequence(prefix)`。
- `repository/QuoteApprovalRepository.java`：`insert / findPendingByInstanceId / markDecided`。
- `port/QuoteApprovalPort.java`：`Long start(Long definitionId, String title, String businessKey, Map<String,Object> variables, CrmActor actor)`。

### 4.2 application（`com.bluewhale.crm.commerce.application`）
- `command/QuoteSaveCommand.java`：`opportunityId / title / currency / validUntil / discountAmount / remark / lines`，`@NotNull` / `@Size` 校验。
- `command/QuoteLineCommand.java`：`productName / specification / unit / quantity / unitPrice / discountRate / taxRate / remark`。
- `command/QuoteApprovalCallbackCommand.java`：`instanceId / status / comment`。
- `service/QuoteAppService.java`：`page / detail / create / update / submit / revise / handleApprovalCallback`。
- `service/impl/QuoteAppServiceImpl.java`：
  - 门禁 `properties.isDemandGatePassed()`；
  - 数据范围 `applyDataScope`、`requireQuote`；
  - 商机校验 `opportunityAppService.detail(opportunityId, actor)`，从视图取 `customerId / ownerId`；
  - `create` 生成编号、汇总金额、落头行；
  - `update` 仅 DRAFT，全量替换行；
  - `submit` 先调 `QuoteApprovalPort.start`，成功后写审批记录 + 更新报价为 `PENDING_APPROVAL`；
  - `revise` 校验 `APPROVED/REJECTED`，克隆头行生成 version+1；
  - `handleApprovalCallback` 按 D-5 幂等处理。
- `view/QuoteView.java`、`view/QuoteLineView.java`：含全部金额、状态、审批字段与行列表。

### 4.3 infrastructure（`com.bluewhale.crm.commerce.infrastructure`）
- `persistence/entity/QuoteEntity.java`：`@TableName("crm_quote")`，`lockVersion` 标注 `@Version`。
- `persistence/entity/QuoteLineEntity.java`、`QuoteApprovalEntity.java`。
- `persistence/mapper/QuoteMapper.java`、`QuoteLineMapper.java`、`QuoteApprovalMapper.java`。
- `persistence/repository/QuoteRepositoryImpl.java`、`QuoteApprovalRepositoryImpl.java`。
- `client/WorkflowQuoteApprovalClient.java`：实现 `QuoteApprovalPort`，`InternalTokenSupport.builder()` + `SimpleClientHttpRequestFactory`，`POST /workflow/instance`，解析 `code/data.id`，失败抛 `SYSTEM_ERROR("发起审批失败: ...")`。

### 4.4 config / common
- 新增 `config/CrmQuoteApprovalProperties.java`（`@ConfigurationProperties("whale.crm.quote-approval")`）。
- `WhaleCrmApplication` 的 `@EnableConfigurationProperties` 增加该类。
- `application.yml` 增加 `whale.crm.quote-approval` 配置与环境变量占位。
- `CrmErrorCode` 增加 41031–41036：`QUOTE_NOT_FOUND / QUOTE_STATE_CONFLICT / QUOTE_LINE_REQUIRED / QUOTE_AMOUNT_INVALID / QUOTE_APPROVAL_UNAVAILABLE / QUOTE_APPROVAL_STATE_CONFLICT`。
- `CrmPermissions` 增加 `QUOTE_LIST / QUOTE_CREATE / QUOTE_UPDATE / QUOTE_SUBMIT / QUOTE_REVISE`。

### 4.5 interfaces（`com.bluewhale.crm.commerce.interfaces.controller`）
- `QuoteController.java`：`/crm/quote`，`page / detail / create / update / submit / revise`，分页沿用 `PageParams`。
- `QuoteInternalController.java`：`/crm/internal/quote`，`POST /approval-callback`。

### 4.6 权限脚本
新增 `whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.85-crm-quote.sql`：
- 1850–1854 五条权限，全部 `INSERT ... SELECT ... WHERE NOT EXISTS`。
- 授权按 D-6；授权 ID `CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED)`，沿用既有范式。

## 5. 测试与验收

### 5.1 单元测试 `QuoteAppServiceImplTest`
覆盖：
1. 门禁关闭拒绝；
2. 缺行 / 金额非法拒绝；
3. 新建：编号格式、首版 version=1、金额与折扣率计算；
4. 编辑：非 DRAFT 拒绝；
5. 提交：`APPROVED` 状态不可提交；workflow 发起失败保持 DRAFT（事务回滚断言）；
6. 折扣阈值变量：5% → SUPERVISOR，10% → DIRECTOR，20% → GENERAL_MANAGER；
7. 回调：通过 → APPROVED 且锁定；驳回 → REJECTED；重复回调返回 false；未知 instance 返回 false；
8. 改价：APPROVED 可 revise 生成 version+1、root/parent 指向首版；
9. 数据范围：普通销售看不到他人报价，管理者可见。

### 5.2 迁移测试
`CrmFlywayMigrationScriptTest` 增加 V5 用例：
- 三表集合 `{crm_quote, crm_quote_line, crm_quote_approval}`；
- 无 `INSERT INTO CRM_` 种子；
- 含 `UNIQUE KEY uk_crm_quote_no_version` 与 `uk_crm_quote_approval_instance`。

### 5.3 命令
```powershell
.\mvnw.cmd -pl :whale-crm validate
.\mvnw.cmd -pl :whale-crm test
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-doc-consistency.ps1
```
验收：0 失败、Checkstyle 0 违反、`DOC-CONSISTENCY-PASS`。

## 6. 文档与提交

更新：
- `docs/design/crm-service.md`：M2 状态、§3.3 报价接口、§3.4 偏差移除“报价未实现”、§4 数据模型、§5 API、权限号段。
- `docs/plans/2026-11-04-wave5.83-crm-panorama.md` §13.2：报价改为已交付。
- `docs/design/crm-module-panorama.md` §3.2 / §6.2 / §12。
- `docs/requirements/register.md` REQ-FUNC-082：报价已交付；预测 / 目标待增量。
- `docs/devlog/2026-11-04.md` 追加报价小节。
- 新增 `docs/reviews/2026-11-04-wave5.83-crm-quote.md`。

提交（仅本波）：
- 禁止 `git add -A`；共享文档（devlog / register）保留 HEAD 其余段落，只暂存本波新增内容。
- 提交信息：`feat(crm): Wave 5.83 M2 报价单与审批回调落地`
- 正文注明：V5 三表、权限 1850–1854、测试数、未含转订单 / 预测 / 目标 / 前端。

## 7. 风险与约束

- workflow 发起失败或回调缺失时，报价停留 `PENDING_APPROVAL`；本片不做定时对账（`fetchStatus` 同步留待审批对账片）。
- 并行波次仍有未提交改动，提交阶段必须逐文件暂存并核对 `git diff --cached`。
- 迁移只允许新增；`crm_quote.quote_no` 序号按租户 + 年份，不保证跨年连续。

## 8. 批准项

请确认以下三点后进入实施：
1. 报价归属 `commerce` 上下文，权限号段 **1850–1854**（不登记 1855 内部回调）。
2. 金额口径：行折扣 + 整单折扣，折扣率按**折前未税金额**计算；`discountAmount` 为显式金额。
3. 审批关闭（`enabled=false`）时**失败关闭**（`QUOTE_APPROVAL_UNAVAILABLE`），不做直通。

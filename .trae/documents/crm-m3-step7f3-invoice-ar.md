# Step 7f-3：CRM M3 发票与应收对账实施计划

## Context

Wave 5.83 CRM M3 已交付 7f-1（合同台账投影 / 回款计划）与 7f-2（商务订单 → workflow 审批 → outbox 幂等下发 ERP）。M3 仅剩「发票与应收对账」（计划 §14.2）。

**目标**：CRM 完成开票申请 → 审批 → 开票记录 → 寄送跟踪 → 红冲；ERP 提供按客户应收只读内网契约；CRM 落账龄对账快照。票面记账/税额/实收/核销仍归 ERP，CRM 不写应收台账。

**已确认决策**：
- 发票审批走 `whale-workflow`（与报价/订单一致，回调幂等，缺 `definition-id` 时提交失败关闭）。
- 权限扩到 **1874–1880**；M4 1875–1884 → 1881–1890，M5/M6 同步顺延并在文档注明号段已接近 1899 上限。

**硬纪律**：只追加 Flyway 迁移、不改历史迁移；不写客户同步代码（D2 未定，仅用 `customerCode` 快照调 ERP）；精确 `git add`；文档回填后 `scripts/check-doc-consistency.ps1` 必须 PASS；提交后推送 `origin/main`。

## 现状事实（已核对）

- CRM 迁移已到 `V8__commerce_order.sql`，下一个是 **`V9__invoice.sql`**（设计稿误标 V12，需同步修正 M4=V10 / M5=V11 / M6=V12）。
- CRM 权限已用 1866–1873（7f-2），1874+ 空闲；菜单已用到 429，430 可用。
- CRM 错误码已用到 `41055`；`CrmPermissions` 为常量类；`CrmActor.allDataScope()` 控制数据范围。
- ERP 已有应收域：[Receivable.java](file:///f:/blue-whale/backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/finance/domain/model/Receivable.java)（`remaining()` / `overdueDays()`）、`ReceivableRepository.listOutstanding()`、`ArApAppServiceImpl.receivableAging()`、`AgingCalculator`（未到期/1–30/31–60/61–90/90+）、`AgingView`；`CustomerRepository.findByCode(String)` 可把 CRM `customerCode` 解析为 ERP `customerId`。
- ERP 内网控制器在 `com.bluewhale.erp.controller`（如 [ErpInternalScmController.java](file:///f:/blue-whale/backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/controller/ErpInternalScmController.java)），无权限注解，网关已 deny `/erp/internal/**`。
- CRM 内网客户端模式见 [ErpOrderInternalClient.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/commerce/infrastructure/client/ErpOrderInternalClient.java)（`InternalTokenSupport` + `RestClient` + 统一响应体 `code==0` + 租户头）。
- 应用服务模式见 [SalesOrderAppServiceImpl.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/commerce/application/service/impl/SalesOrderAppServiceImpl.java)（门禁、数据范围、审计、状态机、回调幂等）。
- 前端为 React + antd：菜单/路由集中在 [App.tsx](file:///f:/blue-whale/frontend/platform-apps/whale-crm/src/App.tsx)，API 集中在 [commerce.ts](file:///f:/blue-whale/frontend/platform-apps/whale-crm/src/api/commerce.ts)。

## 实施步骤

### 1. ERP：按客户应收只读内网契约
- `ReceivableRepository` 增 `listOutstandingByCustomer(Long customerId)`；实现复用 `listOutstanding()` 的查询条件加 `eq(customerId)`。
- `ArApAppService` / `ArApAppServiceImpl` 增 `customerReceivable(String customerCode)`：
  - `CustomerRepository.findByCode` 解析客户；不存在返回空摘要（不报错，便于 CRM 降级）。
  - 组装新增 `CustomerReceivableView`（customerId / customerCode / customerName / currency / receivableCount / totalRemaining / notDue / overdue1To30 / 31To60 / 61To90 / over90 / `List<ReceivableView>` / `snapshotTime`）；分桶复用 `AgingCalculator.empty/accumulate`。
- 新增 `com.bluewhale.erp.controller.ErpInternalFinanceController`：
  - `GET /erp/internal/finance/receivable/by-customer?customerCode=` → `Result<CustomerReceivableView>`。
  - 只读、不写库；无 `@RequirePermission`。
- ERP 单测：按客户过滤、账龄分桶、客户不存在返回空、不同客户不串数据。

### 2. CRM 迁移 `V9__invoice.sql`（仅 `CREATE TABLE IF NOT EXISTS`，无种子）
- `crm_invoice_request`：
  - `invoice_no`（申请单号 `INV-yyyyMMdd-xxxx`）、`request_type`（NORMAL / RED_FLUSH）、`origin_invoice_id`；
  - 来源：`source_type`（CONTRACT / ORDER）、`source_id`、`source_order_no`、`contract_id`、`contract_no`；
  - 客户快照：`customer_id`、`customer_code`、`customer_name`；
  - 金额：`currency`、`amount`（申请含税金额）、`tax_amount`、`invoice_type`（VAT_SPECIAL/VAT_NORMAL/ELECTRONIC）、`title_type`、`invoice_title`、`tax_no`；
  - 状态：`status`（DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/ISSUED/SENT/RED_FLUSHED/CANCELLED）、`approval_instance_id`、`submitted_at`、`approved_at`；
  - 开票记录：`invoice_code`、`invoice_number`、`issued_at`、`issued_amount`；
  - 寄送：`courier`、`tracking_no`、`sent_at`；
  - 红冲：`red_flush_reason`、`red_flushed_at`；
  - `owner_id`、`dept_id`、`remark`、`lock_version`、审计字段。
  - 唯一键：`uk(tenant_id, invoice_no)`、`uk(tenant_id, source_type, source_id, request_type, amount)` 防重复申请（或按业务改为 `uk(tenant_id, invoice_no)` + 业务校验）。
- `crm_invoice_item`：`request_id`、`line_no`、`item_name`、`specification`、`quantity`、`unit_price`、`tax_rate`、`tax_amount`、`amount`、`remark`；`uk(tenant_id, request_id, line_no)`。
- `crm_ar_snapshot`：`customer_id`、`customer_code`、`customer_name`、`snapshot_date`、`currency`、`receivable_count`、`total_remaining`、5 个账龄桶、`source`、`last_sync_at`；`uk(tenant_id, customer_id, snapshot_date, currency)`。

### 3. CRM 领域 / 应用 / 端口 / 客户端（`commerce` 包内，沿用 7f-2 四层）
- domain/model：`InvoiceRequest`、`InvoiceItem`、`InvoiceStatus`、`InvoiceType`、`InvoiceRequestType`、`InvoiceQuery`、`ArSnapshot`（含 `fromErp` 映射与桶合计校验）。
- domain/repository：`InvoiceRequestRepository`（含行）、`ArSnapshotRepository`。
- domain/port：`ErpReceivablePort` + `ErpReceivableSummary` / `ErpReceivableItem`。
- application/command：`InvoiceSaveCommand`、`InvoiceSubmitCommand`、`InvoiceIssueCommand`、`InvoiceSendCommand`、`InvoiceRedFlushCommand`、`InvoiceApprovalCallbackCommand`。
- application/service：`InvoiceAppService`、`ArReconcileAppService` + impl。
  - 可开票金额 = `crm_contract.amount` − 已占用（PENDING_APPROVAL/APPROVED/ISSUED/SENT 的正向申请含税金额）− 已红冲金额；`amount ≤ 可开票余额`，否则 `INVOICE_AMOUNT_EXCEEDED`。
  - 合同金额未同步（`crm_contract` 无记录）拒绝创建。
  - 状态机：DRAFT→PENDING_APPROVAL→APPROVED/REJECTED；APPROVED→ISSUED→SENT；ISSUED/SENT→RED_FLUSHED（仅通过红冲单）；DRAFT/REJECTED→CANCELLED。
  - 红冲：原申请必须 ISSUED/SENT，红冲金额 ≤ 原 `issued_amount`，金额取负；同一原申请累计红冲不超过已开票金额。
  - 审批回调：`POST /crm/internal/invoice/approval-callback` 按实例 ID 幂等；非待审批状态不重复变更。
  - 数据范围：非 `allDataScope()` 按 `owner_id` 过滤；跨租户按不存在。
  - 全部写操作 `@AuditLog`（创建/提交/开票/寄送/红冲/取消）。
- infrastructure/persistence：entity / mapper / repository impl（行表整批替换）。
- infrastructure/client：
  - `ErpReceivableInternalClient`（`/erp/internal/finance/receivable/by-customer`）。
  - `WorkflowInvoiceApprovalClient`（复用 `WorkflowOrderApprovalClient` 的请求/头模式）。
- interfaces/controller：`CrmInvoiceController`（`/crm/invoice`：page/detail/create/update/submit/issue/send/red-flush/cancel）、`CrmInvoiceInternalController`、`CrmArController`（`/crm/ar/page`、`/crm/ar/sync`）。
- interfaces/scheduler：`CrmArSnapshotJob`（默认关闭，`whale.crm.ar.sync-cron`），按客户批量拉取并 upsert 当日快照。
- 配置：新增 `CrmInvoiceApprovalProperties`（`whale.crm.invoice-approval.*`）与 `CrmArProperties`（`whale.crm.ar.enabled/base-url/timeouts/sync-cron/batch-size`），并在启动类 `@EnableConfigurationProperties` 注册；`application.yml` 补默认关闭项。
- 错误码 `CrmErrorCode` 追加 `41056–41062`：发票不存在 / 状态冲突 / 金额超限 / 行非法 / 审批不可用 / 寄送参数非法 / 应收对账失败，并补默认提示语。

### 4. Auth 权限脚本 `migration-5.83-crm-m3-invoice.sql`
- 权限 1874–1880：
  - 1874 `sys:crm:invoice:list`（列表/详情）
  - 1875 `sys:crm:invoice:create`（新建/编辑）
  - 1876 `sys:crm:invoice:submit`
  - 1877 `sys:crm:invoice:issue`
  - 1878 `sys:crm:invoice:send`
  - 1879 `sys:crm:invoice:red-flush`
  - 1880 `sys:crm:ar:reconcile`
- 菜单 430 `发票管理`（`/crm/invoice`，component `crm/invoice/index`，权限 `sys:crm:invoice:list`）；应收对账复用 430 页面内 Tab，不另建菜单。
- 授权：`tenant_admin / crm_admin / crm_sales_manager / crm_sales` 全量；`crm_analyst` 仅 1874、1880；`crm_service` 仅 1874。全部 `WHERE NOT EXISTS` 幂等。

### 5. 前端（`frontend/platform-apps/whale-crm`）
- `api/commerce.ts` 追加发票与应收对账类型/API（枚举、分页、详情、新建、提交、开票、寄送、红冲、取消、快照分页、手动同步）。
- 新增 `pages/invoice/index.tsx`：筛选、详情抽屉、新建（选合同、行明细、金额校验提示）、提交、开票登记、寄送登记、红冲、取消。
- 新增 `pages/ar/index.tsx`：客户应收快照列表 + 账龄桶汇总 + 手动同步 + ERP 不可用降级提示（「数据暂不可用」）。
- `App.tsx`：菜单 `/invoice`、`/ar` 与路由；标题栏副标题补「发票与对账」。

### 6. 测试与验证
- CRM：
  - `InvoiceRequestTest`：状态机合法/非法、红冲金额边界、已开票不可改。
  - `InvoiceAppServiceImplTest`：可开票金额上限、合同金额未同步拒绝、回调幂等、红冲累计上限、数据范围过滤、门禁关闭。
  - `ArReconcileAppServiceImplTest`：快照 upsert 幂等、ERP 不可用返回降级结果不抛 500、账龄桶合计=总额。
  - 迁移测试补 V9 三表、唯一键、无种子、无 DROP/TRUNCATE/DELETE。
  - ArchUnit 既有规则应通过（新增类遵守 application 不依赖 infrastructure）。
- ERP：`ArApAppServiceImplTest` 或新增 `ErpInternalFinanceControllerTest` 覆盖按客户查询与分桶。
- 命令：
  - `mvn -pl whale-apps/whale-crm -am test`（在 `backend/` 下）
  - `mvn -pl whale-apps/whale-erp -am test`
  - `pnpm --filter whale-crm lint && pnpm --filter whale-crm build`
  - Checkstyle（构建内置）0 告警。

### 7. 文档回填
- 计划 `docs/plans/2026-11-04-wave5.83-crm-panorama.md`：§14.2 标注「发票与应收对账」已完成，新增 §14.3 已交付表；顶部状态更新。
- 设计 `docs/design/crm-module-panorama.md`：§6.2 迁移版本修正（7f-3 = `V9__invoice.sql`，M4=V10、M5=V11、M6=V12）；§6.3 补三表索引；§6.x 权限号段说明 1874–1880 与 M4+ 顺延。
- `docs/service-catalog.md`（如登记 CRM 迁移/权限，则同步）。
- `docs/plans/roadmap.md` 状态同步。
- 运行 `scripts/check-doc-consistency.ps1` 必须 PASS。

### 8. 提交
- 精确 `git add`：CRM 后端/迁移、ERP 内网契约与单测、Auth 脚本、前端页面、文档。
- 排除：`.trae/documents/*.md`、`whale-mall/**`、`backend/whale-services/pom.xml`、`whale-gateway/application.yml`、`deploy/monitoring/prometheus.yml`、`infrastructure/docker/mysql/init/01-init-databases.sql`、`whale-map/**`、`migration-5.91-map-m0.sql`。
- 提交并推送 `origin/main`；输出四段式报告（需求项 / 完成项 / 待做项 / 已修正问题项）。

## 不在本切片
- D2 客户主数据同步（未定稿）。
- 目标完成率切「签约合同额」口径（`§14.2` 列为 7f-3 之后）。
- 合同变更事件回调（需合同侧事件通道）。
- 发票与财务记账/税额联动（归 ERP/财务）。

# Wave 5.78 ERP M2 剩余 B 第二期：采购申请 + 销售报价 Spec

## Why

M2 供应链交易主链路（采购订单 / 销售订单 / 收货 / 发货 / 退货）已闭环，但订单前置环节缺失：采购缺少申请单与审批痕迹，销售缺少报价与客户确认过程，业务只能直接建订单。本期补齐「采购申请 → 采购订单」「销售报价 → 销售订单」两个转单闭环，保持模块化单体（9001 / `/erp/**` / `bluewhale_erp` / 前端 8102）。

## What Changes

- 新增迁移 `V10__scm_requisition_quotation.sql`（4 张表 + 4 个唯一键，仅 `CREATE TABLE IF NOT EXISTS`）。
- 新增 `scm` 领域聚合 `PurchaseRequisition` / `PurchaseRequisitionLine`、`SalesQuotation` / `SalesQuotationLine`，状态机 `DRAFT → APPROVED/ACCEPTED → CONVERTED`，非 `CONVERTED` 可取消。
- 新增两组应用服务：建单、审核（接受）、转单（复用既有 `PurchaseAppService.create` / `SalesAppService.create` 生成草稿订单）、取消、分页与详情。
- 新增两组控制器（各 6 端点），权限 515–526、菜单 100–101，复用角色 `erp_scm`。
- 前端新增采购申请、销售报价两页并接入基座菜单。
- 不涉及凭证、库存与应收应付（转单只生成草稿订单，不触发记账）。

## Impact

- Affected specs：`REQ-FUNC-038`（供应链交易与库存）。
- Affected code：`whale-backend/whale-apps/whale-erp`（`scm` 包、`V10` 迁移、迁移测试）、`whale-service-auth-starter`（权限脚本）、`whale-frontend/platform-apps/whale-erp`、`whale-frontend/platform-apps/whale-platform/src/router/menu.ts`。

## ADDED Requirements

### Requirement: 采购申请单

系统 SHALL 提供采购申请单的建单、审核、转单与取消，申请单行金额 = 预估单价 × 数量（2 位 HALF_UP），单据号 `PA-yyyyMMdd-%04d`。

#### Scenario: 建单与审核

- **WHEN** 提交含供应商、收货仓库、申请日期与至少一条分录（物料编码、数量 > 0、预估单价 > 0）的申请单
- **THEN** 生成 `DRAFT` 申请单，`totalAmount` 为行金额合计；`approve` 后状态为 `APPROVED`

#### Scenario: 转采购订单

- **WHEN** 对 `APPROVED` 申请单执行 `convert`
- **THEN** 以 `docDate = 今天`、`expectedDate = requiredDate`、同供应商/仓库/分录生成 `DRAFT` 采购订单，申请单置 `CONVERTED` 并回写 `convertedOrderNo`；重复转单或非 `APPROVED` 状态抛状态冲突

#### Scenario: 取消

- **WHEN** 对 `DRAFT/APPROVED` 申请单执行 `cancel`
- **THEN** 状态置 `CANCELLED`；`CONVERTED` 拒绝取消

### Requirement: 销售报价单

系统 SHALL 提供销售报价单的建单、客户接受、转单与取消，单据号 `SQ-yyyyMMdd-%04d`。

#### Scenario: 建单与接受

- **WHEN** 提交含客户、发货仓库、报价日期、有效期与至少一条分录的报价单
- **THEN** 生成 `DRAFT` 报价单；`accept` 后状态为 `ACCEPTED`

#### Scenario: 转销售订单

- **WHEN** 对 `ACCEPTED` 报价单执行 `convert`
- **THEN** 以同客户/仓库/分录、`deliveryDate = validUntil` 生成 `DRAFT` 销售订单，报价单置 `CONVERTED` 并回写 `convertedOrderNo`；重复转单或非 `ACCEPTED` 抛状态冲突

### Requirement: 数据模型

`erp_scm_purchase_requisition` / `erp_scm_purchase_requisition_line` / `erp_scm_sales_quotation` / `erp_scm_sales_quotation_line`。

- 头：`doc_no`、`supplier_id`/`customer_id` 及名称快照、`doc_date`、`required_date`/`valid_until`、`warehouse_id`、`status`、`currency`、`total_amount`、`converted_order_no`、`remark`、`create_time`、`update_time`、`deleted`。
- 行：`line_no`、`item_id`、`item_code`、`item_name`、`uom`、`quantity DECIMAL(18,4)`、`unit_price DECIMAL(18,4)`、`amount DECIMAL(18,2)`、`create_time`、`update_time`、`deleted`。
- 唯一键：`uk_pa_doc_no(tenant_id, doc_no)`、`uk_pa_line(tenant_id, requisition_id, line_no)`、`uk_sq_doc_no(tenant_id, doc_no)`、`uk_sq_line(tenant_id, quotation_id, line_no)`。

### Requirement: 接口与权限

| 端点 | 权限 |
|---|---|
| `GET /erp/scm/purchase-requisition/page` | 515 `sys:erp:purchase-requisition:list` |
| `GET /erp/scm/purchase-requisition/{id}` | 516 `:detail` |
| `POST /erp/scm/purchase-requisition` | 517 `:create` |
| `POST /erp/scm/purchase-requisition/{id}/approve` | 518 `:approve` |
| `POST /erp/scm/purchase-requisition/{id}/convert` | 519 `:convert` |
| `POST /erp/scm/purchase-requisition/{id}/cancel` | 520 `:cancel` |
| `GET /erp/scm/sales-quotation/page` | 521 `sys:erp:sales-quotation:list` |
| `GET /erp/scm/sales-quotation/{id}` | 522 `:detail` |
| `POST /erp/scm/sales-quotation` | 523 `:create` |
| `POST /erp/scm/sales-quotation/{id}/accept` | 524 `:accept` |
| `POST /erp/scm/sales-quotation/{id}/convert` | 525 `:convert` |
| `POST /erp/scm/sales-quotation/{id}/cancel` | 526 `:cancel` |

菜单 100 采购申请 `/erp/scm/purchase-requisition`、101 销售报价 `/erp/scm/sales-quotation`；`tenant_admin`/`erp_manager`/`erp_scm` 全量，`erp_member` 仅 list/detail。

### Requirement: 前端

`whale-erp` 新增两页：列表 + 状态筛选 + 新建弹窗 + 详情 + 审核/接受 + 转单 + 取消，按钮按 `hasPermission` 控制；基座 `whale-platform` 菜单在库存盘点后补两项。

## 验收与门禁

- 后端 `.\mvnw.cmd -pl :whale-erp test` 与 `"-Pcoverage-gate" verify` 通过（Checkstyle 0、ArchUnit 全绿）。
- 迁移测试断言 V10 4 表与 4 唯一键。
- 前端 `pnpm --filter whale-erp lint|test -- --run|build`、`pnpm --filter whale-platform lint|build` 通过。
- `scripts/check-doc-consistency.ps1` 与 `scripts/ci/bw-deploy-contract-check.ps1` 通过。

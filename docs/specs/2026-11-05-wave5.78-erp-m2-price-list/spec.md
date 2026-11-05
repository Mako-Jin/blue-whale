# Wave 5.78 ERP M2 剩余 B 第三期：价格表 Spec

## Why

采购/销售订单与报价的单价完全由人工录入，缺少统一价目表，容易错价且无法沉淀价格策略。本期落地「客户售价 + 供应商采购价」价目表，并在订单/报价建单时按价目表兜底取价。

## What Changes

- 新增迁移 `V11__scm_price_list.sql`：`erp_scm_price_list` 1 张表 + 唯一键 `uk_price_list(tenant_id, price_type, partner_id, item_id)`。
- 新增领域 `PriceList`（价格类型 `SALES/PURCHASE`，`price > 0`，有效期 `validTo >= validFrom`，启停）。
- 新增仓储、应用服务（分页/详情/新建/更新/启停/取价）。
- 订单与报价建单兜底：行 `unitPrice` 为空时按 客户/供应商 + 物料 + 单据日期 取价；取不到抛 `PRICE_LIST_NOT_FOUND`。
- 新增接口 `/erp/scm/price-list`（6 端点）、权限 527–532、菜单 102。
- 前端新增价格表页面（销售价/采购价两个页签）。
- 不涉及凭证与库存。

## Impact

- Affected specs：`REQ-FUNC-038`。
- Affected code：`whale-erp` `scm` 包、`V11` 迁移、`SalesAppServiceImpl` / `PurchaseAppServiceImpl` / `SalesQuotationAppServiceImpl` 建单取价、`whale-service-auth-starter` 权限脚本、`whale-erp` 前端、`whale-platform` 菜单。

## ADDED Requirements

### Requirement: 价目表

系统 SHALL 按「价格类型 + 往来方 + 物料」维护唯一价目记录，支持启停与有效期。

#### Scenario: 新建价目

- **WHEN** 提交 `priceType`（`SALES`/`PURCHASE`）、启用状态的客户或供应商、启用物料、单价 > 0、有效期合法
- **THEN** 生成 `ENABLED` 价目；同「类型 + 往来方 + 物料」重复新建抛 `PRICE_LIST_CONFLICT`

#### Scenario: 取价

- **WHEN** 调用 `GET /erp/scm/price-list/resolve?priceType=SALES&partnerId=1&itemId=2&date=2026-11-05`
- **THEN** 返回该日期内 `ENABLED` 且有效期覆盖该日的价目单价；不存在抛 `PRICE_LIST_NOT_FOUND`

### Requirement: 建单兜底取价

订单与报价建单时，行单价为空则按单据日期取价；价目表无匹配则拒绝建单。

#### Scenario: 销售订单取价

- **WHEN** 销售订单分录 `unitPrice` 为空
- **THEN** 使用客户售价；取不到抛 `PRICE_LIST_NOT_FOUND`；显式传入单价时以传入值为准

## 数据模型

`erp_scm_price_list`：`price_type`、`partner_id`、`partner_name`、`item_id`、`item_code`、`item_name`、`uom`、`price DECIMAL(18,4)`、`currency`、`valid_from`、`valid_to`、`status`、`remark`、`create_time`、`update_time`、`deleted`。

## 接口与权限

| 端点 | 权限 |
|---|---|
| `GET /erp/scm/price-list/page` | 527 `sys:erp:price-list:list` |
| `GET /erp/scm/price-list/{id}` | 528 `:detail` |
| `POST /erp/scm/price-list` | 529 `:create` |
| `PUT /erp/scm/price-list/{id}` | 530 `:update` |
| `POST /erp/scm/price-list/{id}/toggle` | 531 `:toggle` |
| `GET /erp/scm/price-list/resolve` | 532 `:resolve` |

菜单 102 价格表 `/erp/scm/price-list`；`tenant_admin/erp_manager/erp_scm` 全量，`erp_member` 527/528/532。

## 验收与门禁

- 后端 `test` 与 `"-Pcoverage-gate" verify` 通过；迁移测试断言 1 表 + 1 唯一键。
- 前端 `whale-erp lint|test -- --run|build`、`whale-platform lint|build` 通过。
- 文档一致性与部署契约通过。

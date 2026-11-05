# Tasks

## T1 迁移与错误码

- [x] `V11__scm_price_list.sql`：1 张表 + `uk_price_list`；仅 `CREATE TABLE IF NOT EXISTS`
- [x] `ErpErrorCode` 增 `PRICE_LIST_NOT_FOUND=40440`、`PRICE_LIST_CONFLICT=40940` 与消息

## T2 领域与仓储

- [x] `scm/domain/model/PriceList.java`、`PriceType.java`、`PriceListQuery.java`
- [x] `PriceListRepository` + 实体/Mapper/实现（`page`/`count`/`findById`/`find(type, partnerId, itemId)`/`insert`/`update`）

## T3 应用服务

- [x] `PriceListAppService`：`page`/`detail`/`create`/`update`/`toggle`/`resolve`
- [x] 取价规则：`status=ENABLED` 且 `validFrom <= date <= validTo`（有效期可空视为不限）

## T4 建单兜底取价

- [x] `SalesAppServiceImpl`、`PurchaseAppServiceImpl`、`SalesQuotationAppServiceImpl`：行单价为空时按单据日期取价，取不到抛 `PRICE_LIST_NOT_FOUND`
- [x] 命令去掉行单价的 `@NotNull`（保留正数校验）

## T5 接口与权限

- [x] `PriceListController` 6 端点（含 `GET /resolve`），`@RequirePermission` + `@AuditLog`
- [x] `migration-5.78-erp-price-list.sql`：权限 527–532、菜单 102

## T6 前端

- [x] `api/scm.ts` 类型 + 6 函数
- [x] `PriceListPage.tsx`：销售价/采购价页签、列表、新建/编辑、启停
- [x] `App.tsx` 路由与菜单；`whale-platform` 菜单

## T7 测试

- [x] 领域：价格校验、有效期、启停
- [x] 应用服务：新建重复拒绝、取价命中/未命中、启停后取价失效
- [x] 订单兜底：单价为空取价成功、无价目报错
- [x] 迁移：V11 1 表 + 1 唯一键

## T8 文档与提交

- [x] 计划 §12.7、设计 §3.2、检查报告、devlog、service-catalog、register
- [x] 门禁全部通过后提交本期与并行区剩余改动

# Task Dependencies

- T2 依赖 T1；T3 依赖 T2；T4 依赖 T3；T5 依赖 T3；T6 依赖 T5 契约；T7/T8 依赖实现完成。

# Tasks

## T1 数据迁移与错误码

- [x] 新增 `V10__scm_requisition_quotation.sql`：4 张表 + 4 唯一键，仅 `CREATE TABLE IF NOT EXISTS`，无物理外键
- [x] `ErpErrorCode` 增 `PURCHASE_REQUISITION_NOT_FOUND=40437`、`SALES_QUOTATION_NOT_FOUND=40438` 及消息映射

## T2 领域模型

- [x] `PurchaseRequisition` / `PurchaseRequisitionLine`：`calculateTotal()`、`approve()`、`convert(orderNo)`、`cancel()`
- [x] `SalesQuotation` / `SalesQuotationLine`：`calculateTotal()`、`accept()`、`convert(orderNo)`、`cancel()`
- [x] `ScmStatus` 补 `CONVERTED`（如缺）

## T3 仓储

- [x] `PurchaseRequisitionRepository` / `SalesQuotationRepository`（`page`/`count`/`findById`/`insert`/`update`/`nextDocNo`）
- [x] 实体、Mapper、实现（单号 `PA-` / `SQ-` + `yyyyMMdd` + `-%04d`）

## T4 应用服务

- [x] `PurchaseRequisitionAppService`：`page`/`detail`/`create`/`approve`/`convert`/`cancel`
- [x] `SalesQuotationAppService`：`page`/`detail`/`create`/`accept`/`convert`/`cancel`
- [x] 转单复用 `PurchaseAppService.create` / `SalesAppService.create`，回写 `convertedOrderNo`

## T5 接口与权限

- [x] `PurchaseRequisitionController` / `SalesQuotationController` 各 6 端点，`@RequirePermission` + `@AuditLog`
- [x] `migration-5.78-erp-requisition-quotation.sql`：权限 515–526、菜单 100–101、角色授权

## T6 前端

- [x] `api/scm.ts` 增视图/输入类型与 12 个函数
- [x] `PurchaseRequisitionPage.tsx` / `SalesQuotationPage.tsx`：列表 + 筛选 + 新建 + 详情 + 审核/接受 + 转单 + 取消
- [x] `App.tsx` 路由分支与菜单项；`whale-platform` 菜单补两项

## T7 测试

- [x] 领域测试：金额汇总、状态机（非法跳转/重复转单/取消限制）
- [x] 应用服务测试：建单/审核/转单/取消/分页/不存在
- [x] 迁移测试：V10 4 表 + 4 唯一键

## T8 文档

- [x] 计划 §12.6、设计 §3.2、检查报告、devlog
- [x] service-catalog（表数 62→66、端点 +12）、register（REQ-FUNC-038 备注）

## T9 门禁与提交

- [x] 后端 test + coverage-gate verify；前端 whale-erp lint/test/build、whale-platform lint/build；文档一致性与部署契约
- [x] 选择性提交（不含 MES/PDM/OA/CRM/Workflow 并行改动）

# Task Dependencies

- T2 依赖 T1；T3 依赖 T1/T2；T4 依赖 T2/T3；T5 依赖 T4；T6 依赖 T5 契约；T7/T8/T9 依赖实现完成。

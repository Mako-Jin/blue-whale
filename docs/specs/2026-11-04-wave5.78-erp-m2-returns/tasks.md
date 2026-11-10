# 任务清单：Wave 5.78 ERP M2 剩余 B 第一期（采购退货 + 销售退货）

> 对应规格：[spec.md](./spec.md)。全部任务完成后跑门禁并选择性提交。

## T1 数据库迁移

- [x] 新增 `whale-backend/whale-apps/whale-erp/src/main/resources/db/migration/V9__scm_returns.sql`
  - 4 张表：`erp_scm_purchase_return(_line)`、`erp_scm_sales_return(_line)`
  - 唯一键 `uk_pr_doc_no`/`uk_sr_doc_no`/`uk_pr_line`/`uk_sr_line`
  - 仅 `CREATE TABLE IF NOT EXISTS`，无破坏性语句

## T2 领域层

- [x] `ScmStatus` 增加 `SOURCE_PURCHASE_RETURN` / `SOURCE_SALES_RETURN`
- [x] 新增 `PurchaseReturn(+Line)`、`SalesReturn(+Line)` 聚合（状态机 DRAFT→POSTED/CANCELLED）
- [x] `StockBalance.outboundAt(quantity, unitCost)`
- [x] `Payable.offsetReturn`、`Receivable.offsetReturn`
- [x] 新增 `PurchaseReturnRepository` / `SalesReturnRepository` 接口

## T3 基础设施层

- [x] 实体/Mapper：退货头、行各 2 套
- [x] 仓储实现：`page/count/findById/insert/update/nextDocNo/sumReturnedQuantity`

## T4 应用层

- [x] 命令 `PurchaseReturnCreateCommand`、`SalesReturnCreateCommand`
- [x] 视图 `ReturnLineView`、`PurchaseReturnView`、`SalesReturnView`
- [x] 服务接口与实现：`create/post/cancel/page/detail`
- [x] `ArApAppService` + 实现：`offsetPayable` / `offsetReceivable`
- [x] `AutoVoucherAppService` + 实现：`postPurchaseReturn` / `postSalesReturn`

## T5 接口层

- [x] `PurchaseReturnController` / `SalesReturnController`（5 端点各一）
- [x] `ErpErrorCode`：40023、40435、40436、40936–40938 + 文案

## T6 权限脚本

- [x] 新增 `whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.78-erp-returns.sql`
  - 权限 507–514、菜单 98–99、角色授权幂等

## T7 前端

- [x] `api/scm.ts`：退货类型 + 10 个 API 函数
- [x] `pages/PurchaseReturnPage.tsx`、`pages/SalesReturnPage.tsx`
- [x] `App.tsx` 菜单/路由/渲染
- [x] `whale-platform/src/router/menu.ts` 追加两条（保留既有全部条目）

## T8 测试

- [x] 后端：采购/销售退货应用服务单测、冲减单测
- [x] Flyway：V9 断言用例
- [x] 前端：`pnpm --filter whale-erp test -- --run`

## T9 门禁

- [x] `.\mvnw.cmd -pl :whale-erp test`
- [x] `.\mvnw.cmd -pl :whale-erp "-Pcoverage-gate" verify`
- [x] `pnpm --filter whale-erp lint|test|build`
- [x] `pnpm --filter whale-platform lint|build`
- [x] `powershell -File scripts/check-doc-consistency.ps1`
- [x] `powershell -File scripts/ci/bw-deploy-contract-check.ps1`

## T10 文档与提交

- [x] 计划 §12.4 / §9、设计 §3.2 / 变更记录、评审报告、devlog
- [x] service-catalog、register（共享文档，提交时按备份→checkout→仅写本期→提交→恢复）
- [x] 选择性提交（不含 MES/PDM/OA/CRM/Workflow 并行改动）

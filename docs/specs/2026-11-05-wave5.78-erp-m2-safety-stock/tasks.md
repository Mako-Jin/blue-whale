# 任务清单：M2 剩余 B 第四期（安全库存 / 缺料预警 / 呆滞分析）

> 关联：[spec.md](./spec.md)。完成后逐项勾选，并在检查报告中回填证据。

- [x] **T1 迁移**：`V12__scm_safety_stock.sql` 建 `erp_scm_safety_stock`；`ErpFlywayMigrationScriptTest` 补 V12 断言。
- [x] **T2 领域**：`SafetyStock` + `validate()`；`SafetyStockQuery`；`SafetyStockRepository` 接口。
- [x] **T3 仓储**：`SafetyStockEntity/Mapper/RepositoryImpl`；`StockRepository` 增 `listBalances`、`lastOutboundAt` 及实现。
- [x] **T4 应用**：`SafetyStockAppService(+Impl)`：分页/详情/新增/更新/删除；缺料预警；呆滞分析。
- [x] **T5 接口**：`SafetyStockController` 6 端点 + `@AuditLog`；`ErpErrorCode` 追加 40024/40439/40941。
- [x] **T6 权限**：`migration-5.78-erp-safety-stock.sql`（权限 533–538、菜单 103、角色授权）。
- [x] **T7 测试**：`SafetyStockTest`、`SafetyStockAppServiceImplTest`。
- [x] **T8 前端 API**：`api/scm.ts` 类型与 6 个函数。
- [x] **T9 前端页面**：`SafetyStockPage.tsx` 三页签 + `App.tsx` 路由 + 基座 `menu.ts`。
- [x] **T10 门禁**：后端 verify（覆盖率/Checkstyle/ArchUnit）、前端 lint/test/build、文档一致性、部署契约。
- [x] **T11 文档回填**：计划 §12.8 与变更记录 v1.2、设计 §3.2 库存行、检查报告、devlog、service-catalog、register、本清单勾选。
- [x] **T12 提交**：单条提交 `feat(erp): M2 剩余 B 第四期 安全库存与呆滞分析`。

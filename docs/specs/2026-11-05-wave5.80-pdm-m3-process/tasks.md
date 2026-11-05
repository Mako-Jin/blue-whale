# Wave 5.80 PDM M3 任务清单

> 状态：全部完成（2026-11-05）
> 关联规格：[spec.md](spec.md)

| 编号 | 任务 | 交付物 | 状态 |
|---|---|---|---|
| M3-T1 | PDM 工艺迁移 | `V10__process_route.sql`：5 张 `pdm_prc_*` 表与索引/唯一键 | 完成 |
| M3-T2 | MES 接收迁移 | `V9__mes_process_route.sql`：`mes_process_route` | 完成 |
| M3-T3 | 实体与 Mapper | `PdmPrcRoute/Step/Tooling/Sop/Publish` + 5 个 Mapper | 完成 |
| M3-T4 | 状态与工具 | `PdmPrcStatus`、`PdmPrcPublishStatus`、`PdmPrcToolingTypes`、`PdmPrcVersionNo`、`PdmPrcChecksum`、`PdmPrcRequestContext` | 完成 |
| M3-T5 | 路线 CRUD 与版本 | 草稿维护、同零件版本递增、同零件同版本唯一、发布后冻结 | 完成 |
| M3-T6 | 工序/工装/SOP | 序号唯一、工时非负、SOP 仅发布版本、删除级联 | 完成 |
| M3-T7 | 发布 / 重试 / 撤回 | 发布置旧版 OBSOLETE、MES 失败不阻塞、重试与撤回 | 完成 |
| M3-T8 | MES 客户端 | `PdmPrcMesClient` 内网令牌 + DiscoveryClient，下发/撤回 | 完成 |
| M3-T9 | 控制器 | `/pdm/process/route` 15 端点 + `@RequirePermission` + `@AuditLog` | 完成 |
| M3-T10 | SOP 内容内网端点 | `/pdm/internal/document/{id}/versions/{v}/content`，仅 RELEASED + 审计 | 完成 |
| M3-T11 | MES 接收端 | `POST /mes/internal/process-route` 与 `/withdraw`，checksum 幂等 | 完成 |
| M3-T12 | 权限/菜单/角色 | 970–976、菜单 195–197、角色 21106；幂等授权 | 完成 |
| M3-T13 | 前端 | `api/processRoute.ts`、`pages/process/index.tsx`、`App.tsx` 菜单与路由 | 完成 |
| M3-T14 | 单测 | PDM 9 项（状态机/序号/工时/SOP/下发/重试/撤回）、MES 4 项（幂等/覆盖/墓碑） | 完成 |
| M3-T15 | 迁移测试适配 | PDM 迁移脚本按版本数值排序；MES 累计表数 68 | 完成 |
| M3-T16 | 文档 | 规格、任务、检查报告、devlog、计划状态、需求登记 | 完成 |

# Wave 5.80 M2-4..M2-8：任务清单

> 状态：**全部完成（2026-11-05）**
> 规格：[spec.md](spec.md) · 检查报告：[2026-11-05-wave5.80-pdm-m2-ecn.md](../../reviews/2026-11-05-wave5.80-pdm-m2-ecn.md)

| 编号 | 任务 | 交付物 | 状态 |
|---|---|---|---|
| T1 | `V9__change_notice.sql`：4 张表 + ECO/ECR 审批字段幂等扩列 | 迁移脚本 | 完成 |
| T2 | 实体/Mapper：`PdmChgNotice`、`PdmChgTrace`、`PdmChgNotifyLog`、`PdmChgJobLock`（含锁 SQL） | entity / mapper | 完成 |
| T3 | 状态常量：`PdmChgNoticeStatus`、`PdmChgBizType`、`PdmChgTraceAction`、`PdmChgApprovalStatus`、`PdmChgApprovalSource` | support 常量类 | 完成 |
| T4 | 追溯记录器 `PdmChgTraceRecorder`（业务/影响对象/对象级三种写入） | support | 完成 |
| T5 | ECR/ECO 服务接入追溯 + 发起审批 + VO 审批字段 | 两个 ServiceImpl 改造 | 完成 |
| T6 | `PdmChgWorkflowClient`（`POST /workflow/instance`，未配置降级 MANUAL） | client | 完成 |
| T7 | `PdmChgMessageClient`（`POST /message/internal/send`） | client | 完成 |
| T8 | ECN 服务：创建/修改/删除/通知/生效/关闭/取消/重试/日志/到期扫描 | `PdmChgNoticeServiceImpl` | 完成 |
| T9 | 追溯服务：分页 + 对象时间线 | `PdmChgTraceServiceImpl` | 完成 |
| T10 | 审批回调服务：实例定位、状态幂等、流转 + 追溯 | `PdmChgApprovalCallbackServiceImpl` | 完成 |
| T11 | 到期生效任务 + 单实例锁 + 失败告警 | `PdmChgEffectiveJob` | 完成 |
| T12 | 控制器：ECN 11 端点、追溯 2 端点、内网回调 1 端点 | 3 个 Controller | 完成 |
| T13 | `@EnableScheduling` 开启定时任务 | `WhalePdmApplication` | 完成 |
| T14 | 单元测试：ECN 9、回调 4、追溯 3、定时任务 3 | 4 个测试类 | 完成 |
| T15 | 权限/菜单迁移：963–969、菜单 192–194、角色授权 | `migration-5.80-pdm-ecn.sql` | 完成 |
| T16 | 前端：`api/changeNotice.ts`、`api/changeTrace.ts`、`pages/change/notice.tsx`、`pages/change/trace.tsx`、菜单与路由 | 8106 | 完成 |
| T17 | 文档：计划状态、服务清单、需求登记、devlog、本 spec/tasks、检查报告 | docs | 完成 |
| T18 | 验证：后端全量测试、前端 lint/build | 命令输出 | 完成 |

## 验证记录

- 后端 `mvn -pl whale-backend/whale-apps/whale-pdm test`：**152 tests / 0 failures / BUILD SUCCESS**。
- 前端 `pnpm --filter whale-pdm lint`：通过；`tsc -b && vite build`：通过。

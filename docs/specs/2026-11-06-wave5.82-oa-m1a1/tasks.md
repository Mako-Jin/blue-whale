# Wave 5.82 OA M1A-1（审批中心）任务清单

> 状态：**全部完成（2026-11-06）**
> 规格：[spec.md](./spec.md)

## 一、后端 · Workflow 契约

| ID | 任务 | 状态 | 落地 |
|---|---|---|---|
| wf-1.1 | 抄送查询支持候选收件人多值 IN | 完成 | `ProcessCcQueryRequest#recipientCandidates` |
| wf-1.2 | 候选 trim/去重/上限 10，空列表回退单值 | 完成 | `ProcessCcServiceImpl#page` |
| wf-1.3 | 新增抄送按 ID 查询内网接口 | 完成 | `WorkflowInternalCcController#get` |
| wf-1.4 | 完成命令记录操作人类型 | 完成 | `TaskCompleteRequest#operatorType`、`CompleteCommand#resolveOperatorType` |
| wf-1.5 | 抄送候选与按 ID 查询单测 | 完成 | `ProcessCcServiceImplTest`（4 例） |

## 二、后端 · OA 审批中心

| ID | 任务 | 状态 | 落地 |
|---|---|---|---|
| oa-1.1 | 领域快照与查询命令 | 完成 | `approval/domain/model/*`（7 个快照/查询/命令 + `ApprovalActor`） |
| oa-1.2 | Workflow 内网网关端口 | 完成 | `WorkflowApprovalGateway` |
| oa-1.3 | RestClient 网关实现与错误映射 | 完成 | `WorkflowApprovalGatewayImpl`（50301 降级、租户/用户头透传） |
| oa-1.4 | 本地实例按流程实例 ID 查询 | 完成 | `ApprovalInstanceRepository#findByWorkflowInstanceId(s)` |
| oa-1.5 | 本地结果同步仅在真实迁移时落库 | 完成 | `ApprovalInstanceAppServiceImpl#syncResult` |
| oa-2.1 | 四类列表、详情数据范围、动作归属与幂等 | 完成 | `ApprovalCenterAppServiceImpl` |
| oa-2.2 | 催办/撤销/抄送已读/监控/本地终态同步 | 完成 | `ApprovalCenterAppServiceImpl` |
| oa-2.3 | 业务详情 SPI 与 LEAVE 白名单实现 | 完成 | `ApprovalBusinessDetailProvider`、`LeaveApprovalBusinessDetailProvider` |
| oa-2.4 | 代理规则本人 CRUD | 完成 | `ApprovalDelegateAppServiceImpl` |
| oa-2.5 | 审批中心/监控/代理控制器与审计 | 完成 | 3 个 `*Controller` |
| oa-2.6 | 新增错误码 40301/40411/40412/40413/50301 | 完成 | `OaErrorCode` |
| oa-2.7 | 列表过滤、动作归属、幂等、撤销鉴权单测 | 完成 | `ApprovalCenterAppServiceImplTest`（4 例） |

## 三、权限与菜单

| ID | 任务 | 状态 | 落地 |
|---|---|---|---|
| oa-3.1 | 权限 1642–1647（幂等） | 完成 | `migration-5.82-oa-m1a-approval-center.sql` |
| oa-3.2 | 菜单 360–362 | 完成 | 同上 |
| oa-3.3 | 角色授权（admin 三角色 / manager / member） | 完成 | 同上 |

## 四、前端

| ID | 任务 | 状态 | 落地 |
|---|---|---|---|
| fe-1.1 | 审批中心 API 与类型 | 完成 | `src/api/approval.ts` |
| fe-1.2 | 四类列表工作台（筛选/分页/抄送已读高亮） | 完成 | `src/pages/approval/index.tsx` |
| fe-1.3 | 详情抽屉与动作弹窗 | 完成 | `src/pages/approval/detail.tsx` |
| fe-1.4 | 代理设置页 | 完成 | `src/pages/approval/delegate.tsx` |
| fe-1.5 | 流程监控页（只读） | 完成 | `src/pages/approval/monitor.tsx` |
| fe-1.6 | 菜单/路由/默认落地/标题 | 完成 | `src/App.tsx`、`src/index.css` |

## 五、验证

| ID | 检查 | 结果 |
|---|---|---|
| qa-1.1 | `whale-workflow` 全量单测 | **183 通过 / 0 失败** |
| qa-1.2 | `whale-oa` 全量单测（含 ArchUnit、Flyway 脚本校验） | **81 通过 / 0 失败** |
| qa-1.3 | Checkstyle | 0 violations |
| qa-1.4 | 前端 `pnpm --filter whale-oa lint` | 通过 |
| qa-1.5 | 前端 `pnpm --filter whale-oa build` | 通过 |

## 六、文档

| ID | 任务 | 状态 |
|---|---|---|
| doc-1.1 | spec / tasks / review | 完成 |
| doc-1.2 | 计划进度回写 | 完成 |
| doc-1.3 | service-catalog / requirements register / devlog | 完成 |

## 七、后续（不在本期）

- M1A-4：通知模板、自动催办、审批对账 Job、附件强校验。
- 前端业务详情路由跳转（当前详情抽屉展示白名单字段 + 路由提示）。
- 其他业务类型的 `ApprovalBusinessDetailProvider`。

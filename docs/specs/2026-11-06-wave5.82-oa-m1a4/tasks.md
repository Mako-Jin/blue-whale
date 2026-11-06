# Wave 5.82 OA M1A-4 任务清单

> 状态：全部完成（2026-11-06）
> 对应规格：[spec.md](spec.md)

## 后端

- [x] 新增 `ApprovalNotifyGateway` / `UserDirectoryGateway` 领域端口与通知消息模型。
- [x] 新增 `ApprovalNotifyGatewayImpl`（`POST /message/internal/send`，模板 `OA_APPROVAL_*`）。
- [x] 新增 `UserDirectoryGatewayImpl`（Auth `/user/internal/directory`，账号精确匹配）。
- [x] `WorkflowApprovalGateway` 增加 `listInstanceStatuses`（≤100 个实例）。
- [x] 新增 `ApprovalNotificationAppService` 与实现（TODO/RESULT/CC/URGE/AUTO_URGE）。
- [x] 新增 `ApprovalWorkflowAttachedEvent` / `ApprovalUrgedEvent` 及监听器。
- [x] `ApprovalInstanceAppServiceImpl#attachWorkflow` 发布绑定事件。
- [x] `ApprovalCenterAppServiceImpl#urge` 发布催办事件。
- [x] 仓储新增 `findRunningSubmittedBefore` / `findRunningWithWorkflow`。
- [x] 新增 `ApprovalMaintenanceJob`（自动催办 + 对账），启动类启用调度。
- [x] 请假 `LeaveSaveCommand` 增加 `fileIds` / `keepAttachmentIds`。
- [x] `LeaveAppServiceImpl` 附件强校验、引用同步、删除清理、详情回显。
- [x] 新增 `LeaveAttachmentView` 并挂到 `LeaveView`。
- [x] `migration-5.82-oa-m1a4.sql` 为 OA 角色授权权限 32（`sys:file:upload`）。

## 前端

- [x] `api/client.ts` 增加 `upload`（FormData 原样透传）。
- [x] 新增 `api/file.ts`（`uploadOaFile` → `/file/upload`）。
- [x] `api/oa.ts` 增加 `OaLeaveAttachment`、`fileIds`、`keepAttachmentIds`。
- [x] `pages/Form.tsx` 增加附件上传控件、20MB/5 个限制、病假必填校验、编辑回显与保留。

## 验证

- [x] 后端 `mvn -pl whale-apps/whale-oa -am test`：BUILD SUCCESS，OA 新增通知 7 例、Job 4 例、请假附件 4 例、催办事件 1 例；ArchUnit 7 例通过；Checkstyle 0。
- [x] 前端 `pnpm lint` / `pnpm build`：通过。

## 文档

- [x] 新增 spec / tasks / review。
- [x] 回写全景计划、需求登记、服务目录、开发日志。

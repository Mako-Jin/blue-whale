# OA M1A-4 通知、自动催办、对账与附件校验实施计划

> 状态：**已实施（2026-11-06，见 [检查报告](../../docs/reviews/2026-11-06-wave5.82-oa-m1a4.md)）**
> 上游：Wave 5.82 OA 全景计划 §6.2 M1A-4；M1A-1 审批中心已完成。

## 一、范围

1. 审批通知：待办（TODO）、结果（RESULT）、抄送（CC）、催办（URGE）四类站内信。
2. 自动催办：运行中且超过阈值的实例，按天去重提醒当前待办人。
3. 审批对账 Job：本地 `RUNNING` 实例与 Workflow 状态批量比对，终态回写本地并触发业务结果同步。
4. 请假附件强校验：`requires_attachment=1` 的类型必须带附件；附件 1–5 个且属于当前申请人；编辑时替换引用。

## 二、关键决策

- 通知走 `whale-message` 内网 `POST /message/internal/send`，使用 M0 已种模板：`OA_APPROVAL_TODO / OA_APPROVAL_RESULT / OA_APPROVAL_CC / OA_APPROVAL_URGE`。
- 通知一律 best-effort：失败只记 warn，不阻断审批动作；统一在应用层捕获异常。
- 消息幂等键：
  - TODO：`OA_TODO:{workflowInstanceId}:{taskId}:{userId}`
  - RESULT：`OA_RESULT:{approvalInstanceId}:{status}`
  - CC：`OA_CC:{workflowInstanceId}:{ccId}:{userId}`
  - 手动 URGE：`OA_URGE:{workflowInstanceId}:{epochSecond}`
  - 自动催办：`OA_AUTO_URGE:{workflowInstanceId}:{yyyyMMdd}`
- 账号→用户ID：调用 Auth 现有 `/user/internal/directory?keyword=<account>&current=1&size=10`，仅接受 `username` 精确匹配；解析不到则跳过并 warn。
- 自动催办不建 OA 表；依赖消息服务幂等键按天去重。
- 对账 Job 走 Workflow 现有 `GET /workflow/internal/instance/status`（最多 100 个 ID），仅同步 1/2/3 终态。
- 附件元数据保存到既有 `oa_file_ref`（`biz_type=LEAVE`、`biz_id=leaveId`），不新增表；上传走 `/file/upload`。
- 新增能力放在 `approval/application` 与 `approval/interfaces/job`，不得让 `infrastructure` 反向依赖 `application`（ArchUnit 约束）。

## 三、后端落地清单

### 3.1 通知

- `approval/domain/gateway/ApprovalNotifyGateway`：
  `void send(NotifyMessage message)`，或方法参数化；内部组装 `tenantId/bizType/bizId/idempotencyKey/templateCode/payload/userIds/channels`。
- `approval/infrastructure/client/ApprovalNotifyGatewayImpl`：`DiscoveryClient("whale-message")` + `InternalTokenSupport.restClient()`，解析 `Result`，异常 warn。
- `approval/domain/gateway/UserDirectoryGateway`：`Optional<Long> findUserIdByUsername(String username)`。
- `approval/infrastructure/client/UserDirectoryGatewayImpl`：调用 Auth 内网 directory，取 `username` 精确匹配。
- `approval/application/service/ApprovalNotificationAppService` + Impl：
  - `notifyTodo(Long workflowInstanceId)`
  - `notifyResult(String businessType, Long businessId, String status)`
  - `notifyUrge(Long workflowInstanceId, String targetAccount, String comment, String urger)`
  - `notifyCc(Long workflowInstanceId)`（供对账/扫描任务调用）
  - `notifyAutoUrge(Long workflowInstanceId)`（按天幂等）
- 事件：
  - `ApprovalWorkflowAttachedEvent(approvalInstanceId, workflowInstanceId)`，在 `ApprovalInstanceAppServiceImpl#attachWorkflow` 实际绑定成功时发布 → TODO。
  - `ApprovalResultSyncedEvent` 已有 → RESULT。
  - `ApprovalUrgedEvent(workflowInstanceId, targetAccount, comment, urger)`，在 `ApprovalCenterAppServiceImpl#urge` 成功后发布 → URGE。
- 监听器 `approval/application/listener/ApprovalNotificationListener`，`@EventListener`，内部调用通知服务。

### 3.2 自动催办/对账 Job

- `whale-oa` 启动类补 `@EnableScheduling`（若未启用）。
- `approval/interfaces/job/ApprovalMaintenanceJob`（一个类两个 `@Scheduled` 方法，或拆两个）：
  - 自动催办：`${whale.oa.approval.auto-urge.cron:0 0 */2 * * *}`；开关 `${...enabled:true}`、阈值 `${...threshold-hours:24}`、批量 `${...batch-size:100}`。
  - 对账：`${whale.oa.approval.reconcile.cron:0 */30 * * * *}`；开关、批量。
- 仓储新增：
  - `ApprovalInstanceRepository#findRunningSubmittedBefore(LocalDateTime, int limit)`
  - `ApprovalInstanceRepository#findRunningWithWorkflow(int limit)`
- 网关新增：`List<WorkflowInstanceStatusSnapshot> listInstanceStatuses(List<Long> ids)`；模型 `instanceId/status`。
- 对账：`Workflow 1→APPROVED、2→REJECTED、3→CANCELLED`；调用 `ApprovalInstanceAppService#syncResult`，未变化不落库。
- 自动催办只发通知，不重复调用 Workflow urge 接口。

### 3.3 附件强校验

- `LeaveSaveCommand` 新增 `List<Long> fileIds`（`@Size(max=5)`）。
- `LeaveAppServiceImpl#create/update`：
  - 类型 `requiresAttachment=true` 且 `fileIds` 为空 → `LEAVE_INVALID`。
  - 数量 > 5 → `LEAVE_INVALID`。
  - 校验每个 `fileId` 通过 `FileRefAppService` 注册（`bizType=LEAVE`、`bizId`）；重复注册按既有幂等处理。
  - 编辑时先删除该业务既有引用再注册新列表，避免残留。
- `LeaveView`/`LeaveViewConverter` 补 `fileIds`/附件列表（只读展示）。
- 附件接口复用 `/oa/file/**`（若已有）或新增 `GET /oa/leave/{id}/attachments`。

### 3.4 错误码

- 复用 `LEAVE_INVALID=40009`，消息补充“病假等类型必须上传附件（1–5 个）”。

## 四、前端落地清单

- `src/api/client.ts` 新增 `upload<T>(url, FormData)`（FormData 原样透传）。
- `src/api/file.ts` 新增 `uploadFile(file)` → `/file/upload`。
- `src/api/oa.ts`：`OaLeaveInput` 增 `fileIds?: number[]`；`OaLeave` 增 `attachments?: { id:number; fileName:string }[]`。
- `src/pages/Form.tsx`：
  - 新增 `Upload`（最多 5 个，单文件 ≤20MB），上传成功收集 fileId；
  - 选择 `SICK` 或类型要求附件时显示必填提示；
  - 初始化编辑时回显已有附件（只读文件名 + 删除）。
- 无需新增页面/路由。

## 五、测试

- `ApprovalNotificationAppServiceImplTest`：TODO 幂等键、账号解析失败跳过、结果通知、催办目标选择、异常不外抛。
- `ApprovalMaintenanceJobTest`：自动催办阈值/开关、对账终态映射、批量上限、异常不中断。
- `LeaveAppServiceImplTest`：需附件类型无附件拒绝、超 5 个拒绝、正常注册、编辑替换。
- 既有 `ApprovalCenterAppServiceImplTest` 补催办事件发布断言。
- ArchUnit 全绿；Checkstyle 0；前端 lint/build。

## 六、文档与提交

- 新增 `docs/specs/2026-11-06-wave5.82-oa-m1a4/{spec,tasks}.md`、`docs/reviews/2026-11-06-wave5.82-oa-m1a4.md`。
- 回写计划 M1A-4 状态、requirements register、service-catalog、devlog 2026-11-06。
- 显式 `git add`，中文 commit，`git push origin main`。

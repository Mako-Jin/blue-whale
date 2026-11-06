# OA M1A-1 审批中心实施计划

> 状态：**已实施并验证（2026-11-06）**。规格与任务见 `docs/specs/2026-11-06-wave5.82-oa-m1a1/`，评审见 `docs/reviews/2026-11-06-wave5.82-oa-m1a1.md`。
> 验证：Workflow 183 用例、OA 81 用例全绿；Checkstyle 0；前端 lint/build 通过。

## Context

Wave 5.82 的 OA M0 审批底座与 M1A-2/3/5/6/7 已完成，但**审批中心（M1A-1）仍是空壳**：普通员工没有「我发起的 / 待我审批 / 我已审批 / 抄送我的」入口，也无法在 OA 内同意、驳回、转审、加签、退回、撤销、催办。计划 §6.2 明确 M1A-1 的范围是「四类列表 + 详情时间线 + 动作代理 + 业务详情注册表 + 代理设置 + 流程监控」，且审批中心**不向员工暴露 `sys:workflow:*`**，由 OA 后端代理 Workflow 内网门面。

本计划完成 M1A-1 后端、前端、权限脚本、单测与文档，随后提交并推送 `main`。**不包含** M1A-4（通知模板、自动催办、对账 Job、附件校验）；**不新增 OA 数据库表**：任务、抄送、代理、催办状态均归 Workflow，OA 只保存业务审批实例与业务台账。

## 关键决策

1. **读接口统一走 Workflow**：四类列表以 Workflow 实例/任务/抄送为事实源；用 OA `oa_approval_instance`（按 `workflow_instance_id` 批量匹配）补充 `businessType / businessTitle / businessCode / businessUrl`。这样「我发起的」能覆盖所有在途实例，且不依赖各业务表单是否已调用本地 register。
2. **动作路径带 `instanceId + taskId`**：Workflow 无按 taskId 查询的内网接口；审批前用实例详情定位任务，校验 `assignee = 当前账号` 与 `status = 0`，避免越权与重复动作。
3. **审批动作调用 `/workflow/internal/task/{id}/complete`**：该端点会落 `operator_id`，能保证「我已审批」在转办后仍可查；同时补 `operatorType=USER`、`source=OA`，避免记为 SYSTEM。
4. **抄送查询补 `recipientCandidates`**：发起级抄送存用户 ID、节点级抄送存账号；单值 `recipient` 无法查全。给 Workflow CC page 增加候选列表 IN 过滤（去重、trim、限长，空列表不生成 `IN ()`）。
5. **分层**：出网端口 `WorkflowApprovalGateway` 放 `approval/domain/gateway`，实现放 `approval/infrastructure/client`；查询/结果模型用纯 POJO 放 `approval/domain/model`。应用层只依赖端口，满足 ArchUnit。
6. **业务详情 SPI**：接口 `ApprovalBusinessDetailProvider` 放 `approval/application/service`，返回白名单 `ApprovalBusinessDetailView`；`hr` 侧实现 LEAVE，避免把 `LeaveView` 敏感字段直出。
7. **本地状态同步**：动作成功后 best-effort 拉取实例详情；仅当本地实例处于 RUNNING 且 Workflow 为终态时调用 `ApprovalInstanceAppService.syncResult`。撤销必须同步 `CANCELLED`，保证额度冻结被释放。`syncResult` 改为**仅真实迁移时发布结果事件**，防止重复动作重复发副作用。

## 后端改动

### 1. Workflow 契约补齐（`backend/whale-services/whale-workflow`）

- `dto/ProcessCcQueryRequest.java`：新增 `List<String> recipientCandidates`；非空时优先于 `recipient`。
- `service/impl/ProcessCcServiceImpl.java`：候选列表 `trim + distinct + limit(10)` 后 `.in(...)`；空列表回退单值等值。
- `service/ProcessCcService.java` + 实现 + `controller/WorkflowInternalCcController.java`：新增 `GET /workflow/internal/cc/{id}`（返回 `ProcessCcVO`，不存在抛既有 404），供 OA 校验已读归属。
- `dto/TaskCompleteRequest.java` + `service/impl/task/CompleteCommand.java`：新增可选 `operatorType`，默认 SYSTEM；OA 传 USER + `source=OA`。不改公共 approve/reject 语义。
- 测试：`ProcessCcServiceImplTest` 覆盖候选过滤（空列表、去重）与按 ID 查询。

### 2. OA 领域端口与客户端

新增：
- `approval/domain/gateway/WorkflowApprovalGateway.java`：任务/实例/抄送分页、实例详情、完成、转办、加签、退回、催办、撤销、抄送已读、代理规则 CRUD。
- `approval/domain/model/WorkflowTaskSnapshot.java`、`WorkflowInstanceSnapshot.java`、`WorkflowCcSnapshot.java`、`WorkflowDelegateRuleSnapshot.java`、`ApprovalActor.java` 及查询/命令 POJO。
- `approval/infrastructure/client/WorkflowApprovalGatewayImpl.java`：
  - `DiscoveryClient("whale-workflow")` + `InternalTokenSupport.restClient()`；
  - 透传 `WebHeaders.TENANT_ID / USER_ID / USERNAME`；解析 `Result{code,message,data}`；
  - 非 0 业务码抛 `BizException`；不可达/HTTP 异常抛 `APPROVAL_WORKFLOW_UNAVAILABLE`；
  - 分页用 `ObjectMapper` 转 `PageResult<T>`。

### 3. 仓储补充

- `ApprovalInstanceRepository` 增加 `findByWorkflowInstanceId(Long)`、`findByWorkflowInstanceIds(Collection<Long>)`；实现用 MyBatis-Plus `LambdaQueryWrapper.in`，过滤 `workflow_instance_id is not null`。
- `ApprovalInstanceAppServiceImpl.syncResult`：仅 `markResult` 返回 true 时 `update + publish`；重复同步不落库、不发事件。
- `OaErrorCode` 增加 `APPROVAL_ACTION_FORBIDDEN=40301`、`APPROVAL_TASK_NOT_FOUND=40411`、`APPROVAL_DELEGATE_NOT_FOUND=40412`、`APPROVAL_CC_NOT_FOUND=40413`、`APPROVAL_WORKFLOW_UNAVAILABLE=50301`。

### 4. 应用服务

新增 `ApprovalCenterAppService` + `ApprovalCenterAppServiceImpl`：

- `pageInitiated(actor, query)`：Workflow instance page（`initiator=actor.username`，可按标题/状态过滤），本地实例批量补充业务字段。
- `pagePending(actor, query)`：task page（`assignee=actor.username, status=0`），补充业务字段并计算 `canOperate`。
- `pageApproved(actor, query)`：task page（`actorAccount=actor.username, actorId=actor.userId, handled=true`）。
- `pageCc(actor, query)`：cc page（`recipientCandidates=[username, String.valueOf(userId)]`）。
- `detail(instanceId, actor, monitor)`：实例详情 + 该实例抄送（≤200）+ 业务详情；
  - 数据范围：发起人、任务 `assignee/ownerId/delegateFrom`、抄送收件人，或监控模式；否则抛 404（不泄露存在性）。
  - 每个待办任务输出 `canOperate`；实例输出 `canCancel`（RUNNING 且发起人本人）、`canUrge`（RUNNING 且发起人本人）。
- `approve/reject(taskId, command, actor)`：定位任务 → 校验归属/状态 → 调 `/complete` → `refreshLocal`。
- `transfer/addSign/rollback`：仅任务当前 assignee、仅待办；转办目标不得等于本人；加签账号非空且 ≤10。
- `urge(instanceId, command, actor)`：仅发起人、仅 RUNNING；`urger=actor.username`。
- `cancel(instanceId, actor)`：仅发起人；已终态时 `CANCELLED` 幂等成功、其他终态 409；成功后强制本地同步 `CANCELLED`。
- `markCcRead(ccId, actor)`：`GET /cc/{id}` 校验 `recipient ∈ {username, userId}` 后标记已读。
- `pageMonitor(query)` + `monitorDetail(instanceId)`：实例分页 + 本地补充；详情走监控权限。

新增 `ApprovalDelegateAppService` + 实现：分页/新增/修改/删除代理规则；`fromAccount` 强制为当前账号；修改/删除先用 `page(fromAccount=本人, size=200)` 校验归属，未命中抛 404。

### 5. 命令/视图/控制器

- 命令：`ApprovalActionCommand`（意见/变量）、`ApprovalTransferCommand`、`ApprovalAddSignCommand`、`ApprovalRollbackCommand`、`ApprovalUrgeCommand`、`ApprovalDelegateRuleCommand`、`ApprovalCenterQuery`。
- 视图：`ApprovalTaskView`、`ApprovalCcView`、`ApprovalInstanceRowView`、`ApprovalTimelineTaskView`、`ApprovalDetailView`、`ApprovalBusinessDetailView`、`ApprovalDelegateRuleView`。
- 控制器：
  - `ApprovalCenterController`（`/oa/approval/center`）
    - `GET /initiated/page`、`/pending/page`、`/approved/page`、`/cc/page`
    - `GET /instance/{workflowInstanceId}`
    - `POST /instance/{workflowInstanceId}/task/{taskId}/approve|reject|transfer|add-sign|rollback`
    - `POST /instance/{workflowInstanceId}/urge|cancel`
    - `POST /cc/{ccId}/read`
  - `ApprovalMonitorController`（`/oa/approval/monitor`）：`GET /page`、`GET /instance/{workflowInstanceId}`
  - `ApprovalDelegateController`（`/oa/approval/delegate`）：`GET /page`、`POST /`、`PUT /{id}`、`DELETE /{id}`
- 权限注解：列表/详情 `sys:oa:approval:list|view`；任务动作复用现有 `sys:oa:approval:action`；催办 `urge`；代理 `delegate`；监控 `monitor`；撤销 `cancel`。变更类接口加 `@AuditLog`。

### 6. 业务详情注册表

- `ApprovalBusinessDetailProvider`：`supports(businessType)` + `detail(businessId)`，返回 `{businessType,businessId,summary,status,fields[],url}`。
- `hr/application/service/impl/LeaveApprovalBusinessDetailProvider`：按 `LEAVE` 调 `LeaveAppService.detail`，只映射假期类型、起止时间、天数、状态、事由等白名单字段；查不到返回 null。
- 审批中心用 `ObjectProvider<ApprovalBusinessDetailProvider>` 查找，未命中返回 null，前端展示「暂无业务详情」。

### 7. 权限与菜单迁移

新增 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.82-oa-m1a-approval-center.sql`（**不改已执行的 m1a 脚本**）：

| ID | 权限码 | 用途 |
|---|---|---|
| 1642 | `sys:oa:approval:list` | 四类列表 |
| 1643 | `sys:oa:approval:view` | 审批详情 |
| 1644 | `sys:oa:approval:urge` | 催办 |
| 1645 | `sys:oa:approval:delegate` | 代理设置 |
| 1646 | `sys:oa:approval:monitor` | 流程监控 |
| 1647 | `sys:oa:approval:cancel` | 撤销 |

- 任务动作复用 1640 `sys:oa:approval:action`（`@RequirePermission` 按码校验，路径字段仅元数据）。
- 菜单（`sys_menu` 360–364 未被占用）：360 审批中心 `/oa/approval`、361 代理设置 `/oa/approval/delegate`、362 流程监控 `/oa/approval/monitor`。
- 授予：admin 三角色 1642–1647；`oa_manager` 1642/1643/1644/1646/1647 + 1640；`oa_member` 1642/1643/1647 + 1640。全部 `NOT EXISTS` 幂等，禁止 DROP/TRUNCATE/DELETE。

## 前端改动（`frontend/platform-apps/whale-oa`）

- `src/api/approval.ts`：台账/任务/抄送/详情/动作/代理接口与 TS 类型。
- `src/pages/approval/index.tsx`：`Tabs` 四类列表（筛选、分页），行点击打开详情 Drawer。
- `src/pages/approval/detail.tsx`：复用详情抽屉组件（antd `Descriptions + Timeline`）；展示业务详情、审批时间线、抄送；按 `canOperate / canCancel / canUrge` 与权限码显示操作；同意/驳回（意见）、转审（账号）、加签（逗号分隔账号）、退回（节点键）、催办、撤销（Popconfirm）弹窗；提交后刷新列表与详情。
- `src/pages/approval/delegate.tsx`：本人代理规则表格 + 新增/编辑/删除（流程编码、受托账号、起止时间、备注、启用）。
- `src/pages/approval/monitor.tsx`：全量实例表格 + 监控详情（只读）。
- `src/App.tsx`：菜单新增审批中心/代理设置/流程监控（按权限过滤）；注册 3 组路由；默认重定向改为「首个可见菜单」，避免只有审批权限的用户落到无权限页；标题改为「Whale OA - 办公协同」。
- 抄送行点击先 `markCcRead` 再打开详情，未读行加粗高亮。

## 验证

1. 后端：
   - `f:\blue-whale\mvnw.cmd -o -pl backend/whale-services/whale-workflow -am test`
   - `f:\blue-whale\mvnw.cmd -o -pl backend/whale-apps/whale-oa -am test`（含 ArchUnit、Checkstyle）
2. 新增单测：列表补充、详情越权（404）、动作非本人（403）、重复动作幂等、撤销同步 CANCELLED、代理归属校验、CC 候选过滤、`syncResult` 重复不发事件。
3. 前端（cwd `f:\blue-whale\frontend`）：`pnpm --filter whale-oa lint`、`pnpm --filter whale-oa build`。
4. 人工路径（工作区联调）：审批权限账号打开 OA → 审批中心四 Tab → 详情时间线 → 同意/驳回 → 状态与「我已审批」更新；代理设置增删改；监控账号只读查看。

## 文档与交付

- 新建 `docs/specs/2026-11-06-wave5.82-oa-m1a1/spec.md`、`tasks.md`；`docs/reviews/2026-11-06-wave5.82-oa-m1a1.md`；`docs/devlog/2026-11-06.md`。
- 更新 `docs/plans/2026-11-04-wave5.82-oa-panorama.md`（状态行 + §6.2 M1A-1 标记完成）、`docs/service-catalog.md`（OA M1A 审批中心落地）、`docs/requirements/register.md`（REQ-FUNC-071 状态）。
- 显式 `git add` 逐个路径（禁用 `git add -A`），中文多段 `-m` 提交，`git push origin main`。

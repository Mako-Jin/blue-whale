# Wave 5.82 OA M1A-1（审批中心）规格

> 状态：**已实现并验证（2026-11-06）**
> 上级计划：[Wave 5.82 OA 全景计划](../../plans/2026-11-04-wave5.82-oa-panorama.md) §6.2
> 关联需求：REQ-FUNC-071（审批流程中心）、REQ-ARCH-023（OA 模块化单体）、REQ-SEC-033（OA 数据安全）

---

## 一、目标

在 `whale-oa`（9004 / `/oa/**` / `bluewhale_oa` / 前端 8103）内交付普通员工可用的审批中心：四类列表、审批详情时间线、同意/驳回/转办/加签/退回/撤销/催办、业务详情白名单、抄送已读、代理设置与流程监控。

审批中心不直接暴露 Workflow 给员工：由 OA 后端以 `WorkflowApprovalGateway` 代理 Workflow 内网门面，统一做数据范围、动作鉴权与业务信息补全。普通员工只需要 OA 权限，不需要 `sys:workflow:*`。

## 二、约束

- C-1 不新增 OA 数据表；任务、抄送、代理、催办状态均归 Workflow，OA 只读本地 `oa_approval_instance` 补充业务字段。
- C-2 四类列表事实源统一为 Workflow 内网门面；本地实例按 `workflowInstanceId` 批量匹配，未登记业务实例仍显示流程标题。
- C-3 动作接口路径必须带 `workflowInstanceId + taskId`；动作前用实例详情定位任务并校验 `assignee = 当前账号`、`status = 0`。
- C-4 抄送查询兼容两种收件人口径：发起级存用户 ID 文本、节点级存账号；使用 `recipientCandidates` 多值 IN 过滤。
- C-5 审批动作调用 `/workflow/internal/task/{id}/complete` 并传 `operatorType=USER`、`source=OA`，保证「我已审批」在转办后仍可查。
- C-6 撤销仅发起人；已撤销幂等成功，其他终态 409；成功后同步本地 `CANCELLED`，释放额度冻结。
- C-7 本地结果同步只在真实状态迁移时落库并发事件，重复动作不重复产生业务副作用。
- C-8 业务详情由 SPI 返回白名单字段；本切片只实现 `LEAVE`，不复制 HR 敏感字段。
- C-9 不包含 M1A-4（通知模板、自动催办、对账 Job、附件强校验）。

## 三、Workflow 契约补齐

| 位置 | 变更 |
|---|---|
| `ProcessCcQueryRequest` | 新增 `List<String> recipientCandidates`；非空时优先于单值 `recipient` |
| `ProcessCcServiceImpl#page` | 候选 `trim + distinct + limit(10)` 后 `.in(...)`；空列表回退等值查询 |
| `ProcessCcService` / `WorkflowInternalCcController` | 新增 `GET /workflow/internal/cc/{id}`，返回 `ProcessCcVO`，不存在抛 404 |
| `TaskCompleteRequest` / `CompleteCommand` | 新增可选 `operatorType`；默认 `SYSTEM`，仅识别 `USER` |

## 四、OA 分层

- `approval/domain/model`：`ApprovalActor`、`WorkflowInstanceSnapshot`、`WorkflowTaskSnapshot`、`WorkflowCcSnapshot`、`WorkflowDelegateRuleSnapshot` 及查询/命令 POJO。
- `approval/domain/gateway/WorkflowApprovalGateway`：实例/任务/抄送分页、实例详情、完成、转办、加签、退回、催办、撤销、抄送已读、代理规则 CRUD。
- `approval/infrastructure/client/WorkflowApprovalGatewayImpl`：`DiscoveryClient("whale-workflow")` + `InternalTokenSupport.restClient()`；透传租户/用户头；`Result{code,message,data}` 解包；不可达映射 `50301`；分页上限 200。
- `approval/application`：`ApprovalCenterAppService`、`ApprovalDelegateAppService`、`ApprovalBusinessDetailProvider`、命令与视图。
- `approval/interfaces`：`ApprovalCenterController`、`ApprovalMonitorController`、`ApprovalDelegateController`。
- `hr/application/service/impl/LeaveApprovalBusinessDetailProvider`：实现 LEAVE 白名单详情。

## 五、接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/oa/approval/center/initiated/page` | `sys:oa:approval:list` | 我发起的 |
| GET | `/oa/approval/center/pending/page` | `sys:oa:approval:list` | 待我审批 |
| GET | `/oa/approval/center/approved/page` | `sys:oa:approval:list` | 我已审批 |
| GET | `/oa/approval/center/cc/page` | `sys:oa:approval:list` | 抄送我的 |
| GET | `/oa/approval/center/instance/{id}` | `sys:oa:approval:view` | 详情（数据范围校验） |
| POST | `/oa/approval/center/instance/{id}/task/{taskId}/approve\|reject` | `sys:oa:approval:action` | 同意/驳回（重复动作幂等） |
| POST | `.../transfer\|add-sign\|rollback` | `sys:oa:approval:action` | 转办/加签/退回 |
| POST | `/oa/approval/center/instance/{id}/urge` | `sys:oa:approval:urge` | 催办（仅发起人） |
| POST | `/oa/approval/center/instance/{id}/cancel` | `sys:oa:approval:cancel` | 撤销（仅发起人） |
| POST | `/oa/approval/center/cc/{ccId}/read` | `sys:oa:approval:view` | 抄送已读（校验收件人） |
| GET | `/oa/approval/monitor/page`、`/instance/{id}` | `sys:oa:approval:monitor` | 全量监控（只读） |
| GET/POST/PUT/DELETE | `/oa/approval/delegate/**` | `sys:oa:approval:delegate` | 本人代理规则 |

错误码新增：`APPROVAL_ACTION_FORBIDDEN=40301`、`APPROVAL_TASK_NOT_FOUND=40411`、`APPROVAL_DELEGATE_NOT_FOUND=40412`、`APPROVAL_CC_NOT_FOUND=40413`、`APPROVAL_WORKFLOW_UNAVAILABLE=50301`。

## 六、权限与菜单

`migration-5.82-oa-m1a-approval-center.sql`（幂等，不改已执行脚本）：

| ID | 权限码 | 用途 |
|---|---|---|
| 1642 | `sys:oa:approval:list` | 四类列表 |
| 1643 | `sys:oa:approval:view` | 详情与抄送已读 |
| 1644 | `sys:oa:approval:urge` | 催办 |
| 1645 | `sys:oa:approval:delegate` | 代理设置 |
| 1646 | `sys:oa:approval:monitor` | 流程监控 |
| 1647 | `sys:oa:approval:cancel` | 撤销 |

- 任务动作复用既有 1640 `sys:oa:approval:action`。
- 菜单：360 审批中心 `/oa/approval`、361 代理设置 `/oa/approval/delegate`、362 流程监控 `/oa/approval/monitor`。
- 授权：admin 三角色 1642–1647；`oa_manager` 1642/1643/1644/1646/1647；`oa_member` 1642/1643/1647。

## 七、前端

- `src/api/approval.ts`：四类列表、详情、动作、催办/撤销、抄送已读、监控、代理规则与 TS 类型。
- `src/pages/approval/index.tsx`：四 Tab 工作台，筛选/分页；抄送未读行加粗，点击先标记已读。
- `src/pages/approval/detail.tsx`：详情抽屉（Descriptions + Timeline）；业务白名单、动作弹窗（意见/账号/加签列表/退回节点键）、催办、撤销。
- `src/pages/approval/delegate.tsx`：代理规则增删改查（流程编码、代理人、生效时段、启用、备注）。
- `src/pages/approval/monitor.tsx`：全量实例筛选（标题/状态/流程定义）+ 只读详情。
- `src/App.tsx`：新增三项菜单与路由；默认落地页改为首个可见菜单；标题「Whale OA - 办公协同」。

## 八、验收

1. 四类列表数据范围正确；「我发起的」可覆盖未登记本地业务实例。
2. 详情只对发起人、任务审批人/归属人、抄送人、监控权限开放；其他用户返回 404。
3. 非任务归属人动作返回 40301；重复同意/驳回不重复提交、不重复产生副作用。
4. 撤销后本地实例为 `CANCELLED`，请假冻结额度释放；催办仅发起人且仅运行中。
5. 抄送候选账号与用户 ID 均能查到；未读点击后置为已读。
6. 代理规则只能查改删本人规则；自代理与非法时段拒绝。
7. 后端测试全绿、Checkstyle 0、前端 lint/build 通过。

## 九、不在本期范围

- M1A-4 通知模板、自动催办、审批对账 Job、附件强制校验。
- 移动端专项适配与端到端冒烟脚本。
- 除 LEAVE 外的其他业务详情 Provider。

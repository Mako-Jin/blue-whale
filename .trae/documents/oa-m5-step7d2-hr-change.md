# OA M5-7d-2 人员异动实施计划

## Context

M5-7d-1（文化/投票/建议）已交付并推送（`4f3b8a0c`）。本批补齐 OA 全景计划 §7.5 的剩余部分：**人员异动**（入职/转正/调岗/离职/返聘），目标是形成「异动申请 → 审批 → 审批通过后一次性同步 Auth 与员工档案」的闭环，满足：

- 异动只同步一次（幂等）；
- 停用不删除账号；
- 四眼原则（申请人与执行人不能同一人）；
- 离职清单（交接/账号/资产/档案）任务表；
- Auth 补齐入转调离内网生命周期端点。

设计基线沿用计划：`V14__hr_change.sql` 2 表（`oa_employee_change`、`oa_employee_change_task`）；权限 1788–1789。OA 菜单段 395–397 已被 7d-1 用尽，398+ 预留给 M6，因此**不新增 `sys_menu` 行**，复用现有「员工档案」菜单 368，异动页面入口加在 OA 子应用内部导航。

## 关键现状（已核实）

- OA 四层范式参考 `com.bluewhale.oa.culture`；HR 现有 `EmployeeProfileController`、`EmployeeProfileAppServiceImpl`、`EmployeeProfileRepository`。
- 审批接入范式：`ApprovalInstanceAppService.register(ApprovalInstanceRegisterCommand)` → 监听 `ApprovalResultSyncedEvent` → 执行业务副作用；审批详情实现 `ApprovalBusinessDetailProvider`（参考 `document` 模块）。
- 请求上下文：`UserContext.requireUserId(request)`、`WebHeaders.username(request)`。
- 单据号：`SequenceAppService.nextCode(bizType, prefix)`。
- Auth 现有 `UserInternalController` 仅 exists/directory/by-role；`UserAppService` 具备 create/updateStatus/资料变更能力；`User` 聚合有 `changeProfile` / `changeStatus` / `disable` / `enable`。
- Auth 内网调用客户端参考 `CultureUserDirectoryGatewayImpl`：`DiscoveryClient` + `InternalTokenSupport.restClient()`。
- 迁移测试 `OaFlywayMigrationScriptTest` 强校验版本递增、无 DROP/TRUNCATE/DELETE、每版建表集合。
- 前端：`api/hr.ts`、`pages/hr/profile.tsx`、`App.tsx` 的 `MENU_ITEMS` + `Routes`。

## 实施内容

### 1. 数据库（whale-oa）

新增 `backend/whale-apps/whale-oa/src/main/resources/db/migration/V14__hr_change.sql`：

- `oa_employee_change`：
  - 主键/租户/逻辑删除/时间戳；
  - `change_no`、`change_type`（ONBOARD/REGULAR/TRANSFER/LEAVE/REHIRE）、`user_id`、`employee_name`、`username`、`from_dept_id`、`to_dept_id`、`from_status`、`to_status`、`effective_date`、`reason`、`status`（DRAFT/SUBMITTED/APPROVED/REJECTED/EXECUTED/EXEC_FAILED）、`approval_instance_id`、`applicant_id/applicant_name`、`executor_id/executor_name`、`executed_time`、`execute_result`、`remark`；
  - 唯一键 `uk_oa_employee_change_no(tenant_id, change_no)`；索引 `(tenant_id,user_id)`、`(tenant_id,status)`。
- `oa_employee_change_task`：`change_id`、`task_code`、`task_name`、`assignee_id/assignee_name`、`status`（PENDING/DONE）、`done_time`、`remark`；唯一键 `uk_oa_employee_change_task(tenant_id, change_id, task_code)`。
- 种子：`oa_approval_config` 业务类型 `HR_CHANGE` → 流程定义 `OA_HR_CHANGE`，标题模板 `{applicant}的人事异动申请`，业务路由 `/oa/hr/change`；`oa_approval_route_rule` 两条：部门负责人 `DEPT_LEADER`、HR `ROLE:oa_hr_admin`。
- 规则：`CREATE TABLE IF NOT EXISTS`、`INSERT ... WHERE NOT EXISTS`，禁止破坏性语句。

### 2. Auth 服务

新增权限迁移 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.82-oa-m5-hr-change.sql`：

- 权限 1788 `sys:oa:hr:change:list`（GET `/oa/hr/change/page,/oa/hr/change/{id}`）；
- 权限 1789 `sys:oa:hr:change:manage`（`/oa/hr/change,/oa/hr/change/{id},/oa/hr/change/{id}/submit,/oa/hr/change/{id}/execute,/oa/hr/change/{id}/tasks/{taskId}/done`）；
- 授权：`oa_admin`、`tenant_admin`、`oa_manager` 获 1788+1789；`oa_member` 获 1788；
- 不新增菜单行（复用 `sys_menu` 368「员工档案」）。

新增内网生命周期能力：

- `application/user/UserLifecycleAppService.java`：动作 `ONBOARD/REGULAR/TRANSFER/LEAVE/REHIRE`；
  - ONBOARD：`userId` 为空时用 `TemporaryPasswordGenerator` 生成临时口令并调用 `UserAppService.create(...)`，返回一次性 `temporaryPassword`；有 `userId` 时确保账号启用并同步部门；
  - TRANSFER：保留原资料，仅改 `deptId`；
  - LEAVE：`changeStatus(0)`，发布 `USER_DISABLED` 事件（撤销令牌/失效缓存）；
  - REHIRE：`changeStatus(1)`，可选同步 `deptId`；
  - REGULAR：不改 Auth，仅回读状态。
- `application/user/command/UserLifecycleCommand.java`、`application/user/UserLifecycleVO.java`。
- `controller/UserLifecycleInternalController.java`：`POST /user/internal/lifecycle`，沿用 `/user/internal/**` 网关禁外约定，不返回敏感字段。
- 单测：`UserLifecycleAppServiceTest`（ONBOARD 创建、LEAVE 停用、TRANSFER 改部门、动作非法）。

### 3. OA 人员异动模块（`com.bluewhale.oa.hr`）

新增：

- domain：
  - `model/EmployeeChange.java`（校验 + 状态机 + 四眼断言）；
  - `model/EmployeeChangeTask.java`；
  - `model/EmployeeChangeType.java`、`EmployeeChangeStatus.java`、`EmployeeChangeTaskStatus.java`；
  - `model/EmployeeChangeQuery.java`（继承 `PageQuery`）；
  - `repository/EmployeeChangeRepository.java`；
  - `gateway/EmployeeLifecycleGateway.java`（端口 + `LifecycleResult`）。
- application：
  - `command/EmployeeChangeCommands.java`（保存命令、任务完成命令）；
  - `view/EmployeeChangeViews.java`（列表/详情/任务）；
  - `service/EmployeeChangeAppService.java` + `service/impl/EmployeeChangeAppServiceImpl.java`；
  - `service/impl/EmployeeChangeApprovalBusinessDetailProvider.java`；
  - `listener/EmployeeChangeApprovalResultListener.java`（`HR_CHANGE` → `syncApprovalResult`）。
- infrastructure：
  - `persistence/entity/EmployeeChangeEntity.java`、`EmployeeChangeTaskEntity.java`；
  - `persistence/mapper/EmployeeChangeMapper.java`、`EmployeeChangeTaskMapper.java`；
  - `persistence/repository/EmployeeChangeRepositoryImpl.java`；
  - `client/EmployeeLifecycleGatewayImpl.java`（调 Auth `/user/internal/lifecycle`）。
- interfaces：`controller/EmployeeChangeController.java`。

核心行为：

- `create`：DRAFT；单号 `sequenceAppService.nextCode("HR_CHANGE", "OA-HC")`；申请人取当前用户；离职类型自动生成 4 条清单任务（HANDOVER/ACCOUNT/ASSET/FILE）。
- `update`：仅 DRAFT/REJECTED/EXEC_FAILED 可改。
- `submit`：DRAFT/REJECTED → 登记审批实例 → SUBMITTED。
- `syncApprovalResult`：
  - 仅 SUBMITTED 可迁移（重复事件直接忽略，保证幂等）；
  - REJECTED → REJECTED；
  - APPROVED → 调 Auth 生命周期 + 同步 `oa_employee_profile` → EXECUTED；失败 → EXEC_FAILED 并记录 `execute_result`。
- `execute`（手工重试）：仅 APPROVED/EXEC_FAILED；校验 `operatorId != applicantId`（四眼）；成功后 EXECUTED。
- 档案同步：ONBOARD 入职日期/部门/试用；REGULAR 转正日期/REGULAR；TRANSFER 部门；LEAVE `LEFT` + 离职日期；REHIRE 恢复 `ACTIVE` 并清空离职日期。
- 端点与权限：page/detail（1788），create/update/submit/execute/task-done（1789）。

### 4. 前端（whale-oa）

- `frontend/platform-apps/whale-oa/src/api/hr.ts`：新增 `EmployeeChange*` 类型与 `pageChanges/getChange/createChange/updateChange/submitChange/executeChange/doneChangeTask`。
- 新增 `frontend/platform-apps/whale-oa/src/pages/hr/change.tsx`：筛选列表、新建/编辑弹窗、提交审批、执行、离职清单任务完成（Drawer）。
- `frontend/platform-apps/whale-oa/src/App.tsx`：引入页面、`MENU_ITEMS` 加 `{ key: '/hr/change', label: '人员异动', permission: 'sys:oa:hr:change:list' }`、`Routes` 加 `/hr/change`。

### 5. 测试与文档

- `OaFlywayMigrationScriptTest` 新增 V14 用例：2 表集合、关键列、唯一键、`HR_CHANGE` / `OA_HR_CHANGE` 种子。
- 新增 `EmployeeChangeAppServiceImplTest`：创建（离职任务生成）、提交、审批通过幂等执行、审批拒绝、四眼拦截。
- 文档回填：
  - `docs/plans/2026-11-04-wave5.82-oa-panorama.md`（M5 状态 + 7d-2 进度 + 版本）；
  - `docs/plans/2026-11-06-five-services-backlog-stepwise.md`（7d-2 完成）；
  - `docs/service-catalog.md`（OA 表数、端点、权限、前端页）；
  - `docs/devlog/2026-11-07.md`（Step 7d-2 小节）。

## 验证

1. `mvn -o -pl whale-apps/whale-oa test "-Dcheckstyle.skip=true"`（cwd `backend`）。
2. `mvn -o -pl whale-services/whale-service-auth test "-Dcheckstyle.skip=true"`（cwd `backend`）。
3. `pnpm --filter whale-oa lint`、`pnpm --filter whale-oa build`（cwd `frontend`）。
4. `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1`（repo root）。

## 提交范围

只暂存本批新增/修改的代码、迁移、前端与 4 份文档；**排除** `.trae/documents/**`、`docs/reviews/2026-11-07-workspace-scan.md`、`whale-mall` 相关目录、whale-pay/5.89 邮箱/5.91 地图未提交文件。提交信息：

`feat(oa): M5 人员异动（V14 2 表 / 权限 1788-1789 / Auth 生命周期端点 / 前端 1 页）`

推送 `origin/main` 后输出四段式报告（需求项/完成项/待做项/已修正问题项）。

## 待确认假设

1. 复用菜单 368、不新增 `sys_menu`（OA 菜单段已用尽）。
2. ONBOARD 若未提供 `userId`，由 Auth 生成一次性临时口令，仅在本次响应返回，不落库。
3. 四眼原则在 OA 执行入口校验「申请人与执行人不同」；审批人差异由 Workflow 审批配置保证。

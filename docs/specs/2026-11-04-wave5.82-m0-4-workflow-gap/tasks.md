# Wave 5.82 · M0-4 Workflow 缺口补齐 任务清单

> 规格：[spec.md](./spec.md)。按顺序执行，每项完成后立即更新状态。
> 所有路径相对 `backend/whale-services/whale-workflow/`。

## T1 数据库迁移与实体
- [x] 新增 `src/main/resources/db/migration/V3__cc_delegate_urge.sql`
  - 守卫 ALTER：`process_instance.add_column assignee_overrides_json TEXT NULL`
  - `CREATE TABLE IF NOT EXISTS process_cc`（含唯一键 `uk_cc`）
  - `CREATE TABLE IF NOT EXISTS process_delegate`
  - `CREATE TABLE IF NOT EXISTS process_urge`
- [x] 新增 `entity/ProcessCc.java`、`entity/ProcessDelegate.java`、`entity/ProcessUrge.java`（继承 `BaseEntity`）
- [x] `entity/ProcessInstance.java` 增加 `assigneeOverridesJson`
- [x] 新增 `mapper/ProcessCcMapper.java`、`mapper/ProcessDelegateMapper.java`、`mapper/ProcessUrgeMapper.java`

**验证**：SQL 中无 `DROP/TRUNCATE/DELETE`；列名与实体字段一一对应。

## T2 模型与状态机支持 CC_TASK
- [x] `support/ProcessNodeType.java` 增加 `CC_TASK` + `isCopy()`
- [x] `support/ValidationCode.java` 增加 `CC_TASK_RECIPIENTS_REQUIRED`
- [x] `support/ProcessModelParser.java#readNode` 校验 CC 收件人非空
- [x] `support/ProcessModelValidator.java#checkNodes` 增加 `checkCcTask`
- [x] `support/machine/TransitionResult.java` 增加 `ccNodes` 与 `at(nodes, ccNodes)`
- [x] `support/machine/StateMachine.java#trace` 收集 `CC_TASK` 并继续推进

**验证**：单测 `StateMachineCcTest`：CC 节点被跳过且收件人进入 `ccNodes`，停在后续 `USER_TASK`。

## T3 审批人覆盖
- [x] 新增 `support/AssigneeOverrides.java`（parse/write/validate，常量 20/64）
- [x] `dto/ProcessInstanceStartRequest.java` 增加 `assigneeOverrides`、`ccUserIds`
- [x] 新增 `dto/InternalProcessStartRequest.java`
- [x] `dto/DefinitionEnsureRequest.java` 同步（见 T7）

**验证**：单测 `AssigneeOverridesTest`：未知节点/空列表/会签节点均抛 400；合法输入去重。

## T4 任务激活与代理
- [x] 新增 `service/impl/task/TaskActivationCommand.java`
  - 任务创建支持覆盖、每任务一次代理改派
  - CC 节点写 `process_cc`，捕获 `DuplicateKeyException`
- [x] `service/impl/task/TaskCommandSupport.java` 删除 `createTasks/createTask`
- [x] `service/impl/ProcessInstanceServiceImpl.java` 改用 `taskCommandSupport` + `activationCommand`
- [x] `service/impl/task/ApproveCommand.java#applyResult` 改用 `activationCommand`

**验证**：单测 `TaskActivationCommandTest`：覆盖生成 2 条任务；代理只跳一次；CC 不建任务。

## T5 抄送服务与门面
- [x] 新增 `dto/ProcessCcQueryRequest.java`、`vo/ProcessCcVO.java`
- [x] 新增 `service/ProcessCcService.java` + `service/impl/ProcessCcServiceImpl.java`
- [x] 新增 `controller/WorkflowInternalCcController.java`（`/workflow/internal/cc`）
- [x] `converter/WorkflowConverter.java` 增加 `toCcVo`

**验证**：接口返回分页；`markRead` 二次调用幂等。

## T6 代理规则
- [x] 新增 `dto/DelegateRuleSaveRequest.java`、`vo/DelegateRuleVO.java`
- [x] 新增 `service/ProcessDelegateService.java` + `service/impl/ProcessDelegateServiceImpl.java`
- [x] 新增 `controller/WorkflowInternalDelegateController.java`（`/workflow/internal/delegate`）
- [x] `converter/WorkflowConverter.java` 增加 `toDelegateVo`

**验证**：`start<end`、`from!=to` 校验；`resolveActive` 窗口外返回空；重叠取 `start_time` 最大。

## T7 催办与定义 ensure
- [x] 新增 `dto/UrgeRequest.java`、`vo/ProcessUrgeVO.java`
- [x] 新增 `service/ProcessUrgeService.java` + `service/impl/ProcessUrgeServiceImpl.java`
- [x] `controller/WorkflowInternalTaskController.java` 增加 `POST /{instanceId}/urge`
- [x] `service/ProcessDefinitionService.java` + `Impl` 增加 `ensure`
- [x] 新增 `controller/WorkflowInternalDefinitionController.java`
- [x] `converter/WorkflowConverter.java` 增加 `toUrgeVo`

**验证**：催办 1 小时内二次 400；`ensure` 重复调用返回同一 id。

## T8 查询过滤与内网门面
- [x] `dto/ProcessInstanceQueryRequest.java` 增加 `initiator`
- [x] `dto/ProcessTaskQueryRequest.java` 增加 `operatorId/handled/actorAccount/actorId`
- [x] `service/impl/ProcessInstanceServiceImpl.java#page` 增加 initiator 过滤
- [x] `service/impl/task/TaskQueryCommand.java` 增加 handled/actor 组合过滤
- [x] `service/ProcessInstanceService.java` + `Impl` 增加 `startByCode`、`batchStatus`
- [x] 新增 `vo/InstanceStatusVO.java`
- [x] `controller/WorkflowInternalController.java` 增加 `start/page/{id}/cancel/status`

**验证**：`initiator` 只返回本人实例；`handled+actor` 只返回已同意/已驳回。

## T9 测试与回归
- [x] `StateMachineCcTest`
- [x] `AssigneeOverridesTest`
- [x] `TaskActivationCommandTest`
- [x] `ProcessDelegateServiceImplTest`
- [x] `ProcessUrgeServiceImplTest`
- [x] `WorkflowFlywayMigrationScriptTest#v3ShouldAddCcDelegateAndUrge`（断言 3 张表 + 1 列）
- [x] 运行 `.\mvnw.cmd -pl :whale-workflow -am -DskipTests compile`
- [x] 运行 `.\mvnw.cmd -pl :whale-workflow test`，确认 0 失败、Checkstyle 0 违规
- [x] 回归 `.\mvnw.cmd -pl :whale-oa test`（确认 M0-2/M0-3 未被破坏）

## T10 文档同步
- [x] `docs/devlog/2026-11-04.md` 追加 M0-4 完成记录
- [x] 计划 `docs/plans/2026-11-04-wave5.82-oa-panorama.md` M0-4 状态标记（仅在用户批准执行后）

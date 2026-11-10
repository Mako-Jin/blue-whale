# Wave 5.82 · M0-4 Workflow 缺口补齐 规格

- 状态：已批准并实施（2026-11-04）
- 上级计划：[2026-11-04-wave5.82-oa-panorama.md](../../plans/2026-11-04-wave5.82-oa-panorama.md)（M0-4）
- 目标模块：`whale-backend/whale-services/whale-workflow`
- 关联上下文：OA 审批底座（M0-3 已完成）

## 1. 背景与问题

OA 审批中心依赖 Workflow 作为流程引擎，但当前实现存在 7 个缺口（计划 §四 差距表）：

| 缺口 | 现状 | 本规格交付 |
|---|---|---|
| 动态审批人 | 节点 `assignee` 为静态账号 | 发起时 `assigneeOverrides`（nodeKey → 账号列表），按实例持久化 |
| 抄送 | 无表、无节点类型、无查询 | `CC_TASK` 非阻塞节点 + 发起级 `ccUserIds` + `process_cc` 查询/已读 |
| 代理 | 仅单任务转办/委派 | `process_delegate` 规则表，任务创建时自动改派，禁止链式 |
| 催办 | 无端点 | 内网催办端点 + 1 小时频控 + `process_urge` 留痕 |
| 查询过滤 | 实例仅 title/status/code；任务仅 assignee/status/instanceId | 实例加 `initiator`；任务加 `operatorId`/`handled`/`actorAccount`/`actorId` |
| 内网门面 | 仅归档读取 | 发起、查询、动作、抄送、代理、催办、批量状态、定义 ensure |
| 定义 ensure | 无 | 按 `code` 幂等 ensure，重复执行不重复建定义 |

## 2. 设计决策

### D-1 抄送必须有独立表，不产生待办
`CC_TASK` 是**非阻塞节点**：状态机 `trace` 遇到它时记录收件人后继续推进，不加入 `active`。收件人写入 `process_cc`，不写 `process_task`。验收：任一流程实例的 `process_task` 中不存在 `node_key` 命中 CC 节点的行。

### D-2 收件人统一为 `recipient` 字符串
`process_cc.recipient VARCHAR(64) NOT NULL`：发起级 `ccUserIds` 按 `String.valueOf(id)` 落库；CC 节点按账号落库。查询与已读都以 `recipient` 精确匹配。发起级抄送 `node_key` 固定为 `START`，`node_name` 固定为 `发起抄送`（避免唯一键 NULL 失效）。

### D-3 `assigneeOverrides` 持久化在实例上
`process_instance.assignee_overrides_json TEXT`：发起时校验并写入，后续节点推进（`ApproveCommand`）复用时无需重新传入。解析/序列化集中在 `support/AssigneeOverrides`。

校验规则（发起时，不合法抛 `PARAM_ERROR`）：
- 只允许模型内 `USER_TASK` / `CC_TASK` 节点；
- 节点必须存在；账号列表非空；单节点 ≤ 20 个账号；
- 账号去首尾空白、去重，长度 ≤ 64；
- `COUNTER_SIGN` 节点出现覆盖 → 400。

### D-4 代理只在“任务创建”时解析一次
`TaskActivationCommand` 为每个待办账号调用 `ProcessDelegateService.resolveActive(definitionCode, account, now)`。命中规则时把 `assignee` 改为 `to_account`、`delegate_from` 记录原账号。**只解析一次**，不判断目标人是否也有代理，从机制上杜绝链式代理。

规则解析优先级：同一 `from_account + definition_code` 的多条生效规则取 `start_time` 最大者；相同则取 `id` 最大者。

### D-5 催办只做“留痕 + 频控”，消息发送留到 M1A-4
M0-4 验收只要求“催办频控”。`process_urge` 记录 `instance_id / task_id / urger / target_account / comment`。同租户同实例同催办人 60 分钟内二次调用 → `PARAM_ERROR("催办过于频繁，1 小时内仅允许一次")`。消息模板与 Whale Job 自动催办在 M1A-4 实现（本规格不写 Message 调用）。

### D-6 新能力全部走内网门面
新增端点统一挂载 `/workflow/internal/**`（网关注入 403），**不新增权限码**，避免与 M0-7 的 OA 权限种子冲突。公开 `ProcessTaskController` 仅新增查询过滤字段，沿用既有 `sys:workflow:task:list`。

### D-7 任务创建逻辑收敛为单一实现
删除 `ProcessInstanceServiceImpl` 内重复的 `createTasks/createTask/cancelPendingTasks`，改为注入 `TaskCommandSupport`（已存在同类实现）。新增 `TaskActivationCommand` 作为任务+抄送写的唯一入口，`ProcessInstanceServiceImpl` 与 `ApproveCommand` 共同调用。

## 3. 数据库变更

新增 Flyway：`whale-backend/whale-services/whale-workflow/src/main/resources/db/migration/V3__cc_delegate_urge.sql`
（全部 `CREATE TABLE IF NOT EXISTS` + `information_schema` 守卫 ALTER，保证幂等，不写 DROP/TRUNCATE/DELETE）

### 3.1 `process_instance` 增列
| 列 | 类型 | 说明 |
|---|---|---|
| `assignee_overrides_json` | TEXT NULL | 发起时审批人覆盖快照 |

### 3.2 `process_cc`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` / `tenant_id` / `create_time` / `update_time` / `deleted` | 同既有约定 | BaseEntity |
| `instance_id` | BIGINT NOT NULL | 流程实例 |
| `node_key` | VARCHAR(64) NOT NULL | CC 节点键；发起级为 `START` |
| `node_name` | VARCHAR(128) NULL | 节点名 |
| `recipient` | VARCHAR(64) NOT NULL | 账号或用户ID文本 |
| `read_flag` | TINYINT NOT NULL DEFAULT 0 | 0 未读 / 1 已读 |
| `read_time` | DATETIME NULL | 已读时间 |

索引：唯一 `uk_cc (tenant_id, instance_id, node_key, recipient)`；普通 `idx_recipient (tenant_id, recipient, read_flag)`、`idx_instance (tenant_id, instance_id)`。

### 3.3 `process_delegate`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` / `tenant_id` / `create_time` / `update_time` / `deleted` | 同既有约定 | BaseEntity |
| `definition_code` | VARCHAR(64) NOT NULL | 流程编码（精确匹配） |
| `from_account` | VARCHAR(64) NOT NULL | 委托人 |
| `to_account` | VARCHAR(64) NOT NULL | 代理人 |
| `start_time` | DATETIME NOT NULL | 生效起 |
| `end_time` | DATETIME NOT NULL | 生效止 |
| `enabled` | TINYINT NOT NULL DEFAULT 1 | 1 启用 / 0 停用 |
| `remark` | VARCHAR(255) NULL | 备注 |

索引：`idx_from (tenant_id, from_account, enabled)`、`idx_def (tenant_id, definition_code)`。

### 3.4 `process_urge`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` / `tenant_id` / `create_time` / `update_time` / `deleted` | 同既有约定 | BaseEntity |
| `instance_id` | BIGINT NOT NULL | 流程实例 |
| `task_id` | BIGINT NULL | 指定任务催办 |
| `urger` | VARCHAR(64) NOT NULL | 催办人账号 |
| `target_account` | VARCHAR(64) NULL | 被催办人；NULL 表示全部待办 |
| `comment` | VARCHAR(255) NULL | 催办语 |

索引：`idx_freq (tenant_id, instance_id, urger, create_time)`、`idx_task (task_id)`。

## 4. 代码变更清单

> 路径均相对 `whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/`。

### 4.1 模型与状态机

| 文件 | 变更 |
|---|---|
| `support/ProcessNodeType.java` | 新增枚举值 `CC_TASK`；新增 `isCopy()`（`this == CC_TASK`）；`isApproval()` 保持 `USER_TASK/COUNTER_SIGN` 不变；`from()` 自动支持（按名称匹配） |
| `support/ValidationCode.java` | 新增 `CC_TASK_RECIPIENTS_REQUIRED(ERROR, "抄送节点缺少 recipients")` |
| `support/ProcessModelParser.java` | `readNode`：`CC_TASK` 复用 `assignees` 作为收件人列表，为空抛 `CC_TASK_RECIPIENTS_REQUIRED`；不要求 `assignee` |
| `support/ProcessModelValidator.java` | `checkNodes`：`CC_TASK` 校验 `assignees` 非空（新增 `checkCcTask`），其余规则不变 |
| `support/machine/TransitionResult.java` | 新增字段 `List<ProcessModelNode> ccNodes` + getter；新增静态工厂 `at(List<ProcessModelNode> nodes, List<ProcessModelNode> ccNodes)`；既有工厂委托到新工厂（cc 为空） |
| `support/machine/StateMachine.java` | `trace`：节点类型为 `CC_TASK` 时加入 `ccCollector` 并继续沿出流推进；`advance` 用 `at(active, cc)` 返回 |

### 4.2 新增支撑类

| 文件 | 内容 |
|---|---|
| `support/AssigneeOverrides.java` | `Map<String, List<String>> parse(String json)`（空/非法返回空 Map）；`String write(Map)`；`void validate(ProcessModel model, Map<String, List<String>> overrides)`（D-3 规则，异常 `BizException(PARAM_ERROR, ...)`）；常量 `MAX_PER_NODE=20`、`MAX_ACCOUNT_LENGTH=64` |

### 4.3 实体与 Mapper（新增）

| 文件 | 说明 |
|---|---|
| `entity/ProcessCc.java` | `@TableName("process_cc")`，字段同 3.2 |
| `entity/ProcessDelegate.java` | `@TableName("process_delegate")`，字段同 3.3 |
| `entity/ProcessUrge.java` | `@TableName("process_urge")`，字段同 3.4 |
| `mapper/ProcessCcMapper.java` | `BaseMapper<ProcessCc>` |
| `mapper/ProcessDelegateMapper.java` | `BaseMapper<ProcessDelegate>` |
| `mapper/ProcessUrgeMapper.java` | `BaseMapper<ProcessUrge>` |
| `entity/ProcessInstance.java` | 新增 `String assigneeOverridesJson` |

### 4.4 任务激活（核心新增）

`service/impl/task/TaskActivationCommand.java`（`@Component`）：

```
void activate(ProcessInstance instance, ProcessModel model, TransitionResult result)
```
- 若 `result.isUserTask()`：
  - 对 `result.getNodes()` 逐节点建任务；
  - 节点覆盖：`AssigneeOverrides.parse(instance.getAssigneeOverridesJson())` 命中且非空 → 每个账号一条任务；否则按 `node.assignee` / 会签 `node.assignees` 建任务；
  - 每条任务应用一次代理改派（D-4），写 `delegate_from`；
  - `result.getCcNodes()` 逐节点写 `process_cc`（覆盖优先，去重，`DuplicateKeyException` 视为幂等）。
- `CC_TASK` 永不建任务。

`service/impl/task/TaskCommandSupport.java`：删除 `createTasks/createTask`，保留 `requirePendingTask/requireRunningInstance/cancelPendingTasks`。

`service/impl/ProcessInstanceServiceImpl.java`：
- `start`：解析 `assigneeOverrides` → `AssigneeOverrides.validate` → 写入实例 → 建实例 → `activationCommand.activate(instance, model, result)` → `ccService.createStartCc(instance.getId(), ccUserIds)`。
- 删除本地 `createTasks/createTask/cancelPendingTasks`，改用 `taskCommandSupport` / `activationCommand`。
- 新增 `startByCode(InternalProcessStartRequest)`（按 `definitionCode` 找定义，逻辑同 `start`）。
- `page` 增加 `initiator` 等值过滤。
- 新增 `List<InstanceStatusVO> batchStatus(List<Long> ids)`（上限 100）。

`service/impl/task/ApproveCommand.java`：
- `applyResult` 改为 `instance` 更新 + `activationCommand.activate(instance, model, result)`；其余分支不变。

### 4.5 抄送

| 文件 | 内容 |
|---|---|
| `dto/ProcessCcQueryRequest.java` | 继承 `PageQuery`：`instanceId`、`recipient`、`readFlag` |
| `vo/ProcessCcVO.java` | `id/instanceId/nodeKey/nodeName/recipient/readFlag/readTime/createTime` |
| `service/ProcessCcService.java` | `PageResult<ProcessCcVO> page(...)`；`void markRead(Long id)`；`void createStartCc(Long instanceId, List<Long> ccUserIds)`；`void createNodeCc(Long instanceId, String nodeKey, String nodeName, List<String> recipients)` |
| `service/impl/ProcessCcServiceImpl.java` | 实现；`markRead` 幂等（已读直接返回）；越权由门面入参保证；`createNodeCc` 捕获 `DuplicateKeyException` |
| `controller/WorkflowInternalCcController.java` | `GET /workflow/internal/cc/page`；`POST /workflow/internal/cc/{id}/read` |
| `converter/WorkflowConverter.java` | 新增 `ProcessCcVO toCcVo(ProcessCc entity)` |

### 4.6 代理规则

| 文件 | 内容 |
|---|---|
| `dto/DelegateRuleSaveRequest.java` | `definitionCode`(@NotBlank,@Size(max=64))、`fromAccount`(@NotBlank,@Size(max=64))、`toAccount`(@NotBlank,@Size(max=64))、`startTime`(@NotNull)、`endTime`(@NotNull)、`enabled`、`remark`(@Size(max=255)) |
| `vo/DelegateRuleVO.java` | 同实体展示字段 |
| `service/ProcessDelegateService.java` | `page(fromAccount, current, size)`；`create`；`update`；`delete`；`Optional<String> resolveActive(String definitionCode, String account, LocalDateTime now)` |
| `service/impl/ProcessDelegateServiceImpl.java` | 校验 `start < end`、`from != to`；按 D-4 取规则；`resolveActive` 只读启用且在窗口内的规则 |
| `controller/WorkflowInternalDelegateController.java` | `GET /page`、`POST`、`PUT /{id}`、`DELETE /{id}` |
| `converter/WorkflowConverter.java` | 新增 `DelegateRuleVO toDelegateVo(ProcessDelegate entity)` |

### 4.7 催办

| 文件 | 内容 |
|---|---|
| `dto/UrgeRequest.java` | `taskId`、`urger`(@NotBlank)、`targetAccount`、`comment`(@Size(max=255)) |
| `vo/ProcessUrgeVO.java` | `id/instanceId/taskId/urger/targetAccount/comment/createTime` |
| `service/ProcessUrgeService.java` | `void urge(Long instanceId, UrgeRequest request)`；`PageResult<ProcessUrgeVO> page(Long instanceId, long current, long size)` |
| `service/impl/ProcessUrgeServiceImpl.java` | 实例存在校验；频控（D-5）；`taskId` 非空时校验任务属于该实例；落库 |
| `controller/WorkflowInternalTaskController.java` | 新增 `POST /workflow/internal/task/{instanceId}/urge`（与既有 `{id}/complete` 路径不冲突） |
| `converter/WorkflowConverter.java` | 新增 `ProcessUrgeVO toUrgeVo(ProcessUrge entity)` |

### 4.8 查询过滤

| 文件 | 变更 |
|---|---|
| `dto/ProcessInstanceQueryRequest.java` | 新增 `String initiator` |
| `dto/ProcessTaskQueryRequest.java` | 新增 `Long operatorId`、`Boolean handled`、`String actorAccount`、`Long actorId` |
| `service/impl/ProcessInstanceServiceImpl.java` | `page` 加 `initiator` 等值 |
| `service/impl/task/TaskQueryCommand.java` | `handled=true` → `status in (1,2)`；`actorAccount/actorId` 存在时按 `(assignee = actorAccount OR operator_id = actorId)` 分组过滤；`operatorId` 单独等值 |

说明：“待我审批” = `assignee=我 & status=0`；“我已审批” = `handled=true & actorAccount=我 & actorId=我ID`。

### 4.9 发起请求

| 文件 | 变更 |
|---|---|
| `dto/ProcessInstanceStartRequest.java` | 新增 `Map<String, List<String>> assigneeOverrides`、`List<Long> ccUserIds`（`ccUserIds` ≤ 100） |
| `dto/InternalProcessStartRequest.java` | 新增：`definitionCode`(@NotBlank)、`title`(@NotBlank,@Size(max=128))、`businessKey`(@Size(max=64))、`initiator`(@Size(max=64))、`variables`、`assigneeOverrides`、`ccUserIds` |
| `controller/WorkflowInternalController.java` | 新增 `POST /workflow/internal/instance/start`（body `InternalProcessStartRequest`），返回 `ProcessInstanceVO` |

### 4.10 内网门面其余端点

| 文件 | 端点 |
|---|---|
| `controller/WorkflowInternalController.java` | `GET /page?initiator=&status=&definitionCode=`；`GET /{id}`；`POST /{id}/cancel`；`GET /status?instanceIds=1,2,3` |
| `controller/WorkflowInternalTaskController.java` | `GET /page`；`POST /{id}/approve`；`POST /{id}/reject`；`PUT /{id}/transfer`；`PUT /{id}/delegate`；`PUT /{id}/resolve`；`POST /{id}/rollback`；`POST /{id}/add-sign` |
| `controller/WorkflowInternalDefinitionController.java` | `POST /workflow/internal/definition/ensure` |
| `dto/DefinitionEnsureRequest.java` | `code`(@NotBlank,@Size(max=64))、`name`(@NotBlank,@Size(max=128))、`definitionJson`(@NotBlank)、`remark`(@Size(max=255)) |
| `service/ProcessDefinitionService.java` + `Impl` | 新增 `ProcessDefinitionVO ensure(DefinitionEnsureRequest request)`：`ProcessModelValidator.validate` 不通过 → `PARAM_ERROR(首条问题)`；按 code 已存在直接返回；不存在则走 `create` |
| `vo/InstanceStatusVO.java` | `id/businessKey/status/currentNodeKey` |

内网动作端点复用 `ProcessTaskService` 现有方法；`approve` 的 actor 由 `WebHeaders.username` 解析（无则用请求头缺省 `null`），`operatorId` 由 `WebHeaders.userId` 解析后写入任务，保证“我已审批”过滤可用。

### 4.11 公开查询过滤（无新增权限）
`ProcessTaskController#page`、`ProcessInstanceController#page` 绑定对象新增字段即可，权限码不变。

## 5. 验收标准

| 编号 | 场景 | 期望 |
|---|---|---|
| AC-1 | 模型含 `CC_TASK`，发起带 `ccUserIds` | `process_task` 无抄送任务；`process_cc` 有 `START` 行与 CC 节点行 |
| AC-2 | `assigneeOverrides={dept_manager:[lisi,lisi2]}` | 该节点生成 2 条待办；实例 `assignee_overrides_json` 有快照；后续推进仍生效 |
| AC-3 | 覆盖未知节点 / 空列表 / 会签节点 | 400，文案指明节点 |
| AC-4 | 代理窗口内发起 | 任务 `assignee=toAccount`、`delegate_from=fromAccount`；目标人自身代理不再叠加 |
| AC-5 | 同一实例同一催办人 1 小时内二次催办 | 第二次 400；`process_urge` 仅 1 行 |
| AC-6 | `initiator=zhangsan` 查实例 | 仅张三发起的实例；`handled=true&actorAccount=zhangsan` 仅已同意/已驳回 |
| AC-7 | 重复调用 `definition/ensure` | 定义仅 1 条，返回同一 id |
| AC-8 | 全量回归 | `whale-workflow` 编译通过、测试全绿、Checkstyle 0 违规 |

## 6. 非目标（明确不做）

- 自动催办定时任务、Message 消息下发（M1A-4）。
- OA 侧审批人解析与快照（M0-3 已实现，OA 负责账号有效性）。
- 抄送节点的表单绑定、抄送未读红点前端（M1A-6）。
- 公开 Workflow 前端页面与权限码（M0-7/M1A-5）。
- BPMN 导入的 `CC_TASK` 映射（本次仅支持规范化 JSON；BPMN 支持列入 M1A 待评估）。

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| `CC_TASK` 改变状态机推进语义，回归面大 | 新增单测覆盖 `trace` 路径；`CC_TASK` 不进入 `nodes`，`isUserTask()` 语义不变 |
| 历史实例无 `assignee_overrides_json` | 解析空值返回空 Map，行为与现状一致 |
| 代理规则重叠 | 明确取 `start_time` 最大、`id` 最大单条；只应用一次 |
| `process_cc` 唯一键冲突 | 插入捕获 `DuplicateKeyException` 视为幂等 |
| 内网端点被外部访问 | 依赖网关 `internal-deny` 对 `/workflow/internal/**` 统一 403（既有规则） |
| 无权限码导致内网端点不可用 | 内网端点不加 `@RequirePermission`，与现有 `WorkflowInternalController` 一致 |

## 8. 验证命令

```powershell
# 编译
.\mvnw.cmd -pl :whale-workflow -am -DskipTests compile

# 测试（含 Checkstyle）
.\mvnw.cmd -pl :whale-workflow test
```

预期：BUILD SUCCESS；`Tests run` 失败数 0；`You have 0 Checkstyle violations`。

## 9. 需要用户确认的决策

1. **D-5 催办范围**：M0-4 仅留痕 + 频控，真实消息发送推迟到 M1A-4。是否接受？
2. **D-6 全部走内网**：代理规则维护、抄送查询、催办均只暴露在 `/workflow/internal/**`，由 OA 代理给前端；不新增 Workflow 权限码。是否接受？
3. **D-2 收件人字符串**：`ccUserIds` 以文本存入 `recipient`（与账号共用一列）。是否接受，或要求拆成 `user_id`/`account` 两列？

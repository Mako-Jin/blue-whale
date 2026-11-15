# Whale Workflow：驳回改为「退回到指定处理人」

## Context

当前 `reject` 是**终态驳回**：实例置「已驳回」（status=2）、取消全部待办、不写 `finished_at`，不会回到任何人手里；「退回上一节点」目前只能走另一个 `rollback` 接口。业务侧确认要按方案 2 调整：**驳回 = 退回继续处理**，且要覆盖发起人/指定节点/上一节点三种目标，并按会签策略决定退回时机与目标。

本方案只改运行期行为，不改流程定义结构（不需要设计器/BPMN/DB 结构变更）。

## 目标语义

1. **驳回即退回**：实例保持「运行中」，驳回任务保留「已驳回」痕迹，其余待办取消，目标处理人收到新待办。
2. **三种目标**（`RejectTarget`）：
   - `INITIATOR`：退回发起人。在流程入口节点创建一条虚拟待办（`nodeKey=__resubmit__`，名称「重新提交」，处理人=实例 `initiator`）；发起人同意后从入口继续推进到首个审批节点。
   - `PREVIOUS`：退回上一审批节点。按流转图反向 BFS，取最近的上一层审批节点；若上一层不唯一（并行分支），该选项不可用，必须用 `SPECIFIED`。
   - `SPECIFIED`：退回指定审批节点。只允许选择当前节点的**上游审批节点**，禁止当前/下游/非审批节点。
3. **首节点永不设置驳回**：
   - 入口节点/「重新提交」待办不提供驳回（调用直接报错）。
   - 首个审批节点没有上游审批节点，只能 `INITIATOR`，不能 `PREVIOUS`/`SPECIFIED`。
4. **会签节点由策略决定，忽略请求里的目标**：
   - `ALL`（缺省）：一人驳回 → 节点失败，立即退回「发起会签的人」。落地为「最近的上一层审批节点」；无上一层（会签是首个审批节点）或多层不唯一时，退回发起人（虚拟待办）。
   - `ANY`：一人驳回不失败。若已有同意（`approved>0`）或仍有待办，继续等待；当全部有效任务都被驳回且无人同意 → 节点失败，按上一节点/发起人退回。
   - `RATIO:n`：驳回票计入分母。若 `approved + pending` 已无法达到阈值 → 立即失败退回；否则继续等待。
   - 当前仅 `ALL / ANY / RATIO:n` 三种策略，无其他策略；未知策略按 `ALL` 处理。
5. **终态驳回保留在内部链路**：`CompleteCommand` 的 `REJECT` 与内网 `/workflow/internal/task/{id}/reject` 仍走原 `RejectCommand`（实例终止），避免破坏 OA/签章回调契约。公开 `/workflow/v1/task/{id}/reject` 改为退回。

## 实现步骤

### 1. 请求与接口
- 新增 `request/TaskRejectRequest`：`comment`（≤255）、`target`（非空枚举）、`targetNodeKey`（`SPECIFIED` 必填，≤64）。
- 新增枚举 `support/RejectTarget`：`INITIATOR / PREVIOUS / SPECIFIED`。
- `ProcessTaskController`：
  - `POST /{id}/reject` 改为 `@Valid TaskRejectRequest` → `processTaskService.returnTask(...)`，权限沿用 `sys:workflow:task:approve`。
  - 新增 `GET /{id}/reject-options` → 返回当前任务可用的退回选项（`rejectable/reason/initiator/previous/candidates/policy`），供前端渲染驳回弹窗；权限同上。
- `WorkflowInternalTaskController.reject` 与 `ProcessTaskService.reject` 保持「终止」语义不变，仅供内网/回调使用。
- 服务接口新增 `returnTask(Long id, TaskRejectRequest request)` 与 `rejectOptions(Long id)`。

### 2. 退回命令（核心）
新增 `service/impl/task/ReturnCommand`：
1. `requirePendingTask` + `requireRunningInstance`。
2. 解析模型；若 `task.nodeKey == __resubmit__` → 抛「发起节点不支持驳回」。
3. 当前节点为 `COUNTER_SIGN`：调用 `CounterSignCommand` 新增的 `shouldReturnOnReject(instanceId, nodeKey, node)` 判定；`WAIT` 则只落驳回痕迹并返回。
4. 非会签节点：按 `target` 校验并解析目标（`INITIATOR` / 唯一 `PREVIOUS` / 上游 `SPECIFIED`）。
5. 统一执行退回：
   - 驳回任务 CAS 置 `TASK_REJECTED`（复用 `TaskCommandSupport.transitionFromPending`）；
   - `cancelPendingTasks(instanceId)`；
   - 实例置 `RUNNING`、`activeNodeKeys=null`、`currentIndex/currentNodeKey` 指向目标；
   - `INITIATOR`：插入一条 `__resubmit__` 待办（`assignee/ownerId=initiator`，无 `dueAt`）；
   - 节点目标：复用 `TaskActivationCommand.activate(..., TransitionResult.at(List.of(target)))`，保持覆盖/代理/会签策略快照逻辑一致。

新增 `support/ProcessPaths`（或 `ProcessModel` 私有方法）：
- `ancestorApprovalNodes(model, currentKey)`：反向 BFS，跳过网关/抄送/开始节点，返回全部上游审批节点（去重排序）。
- `nearestPreviousApprovalNodes(...)`：取 BFS 最近一层的审批节点，用于 `PREVIOUS` 与「发起会签的人」。

### 3. 会签判定
在 `CounterSignCommand` 新增：
- `public boolean shouldReturnOnReject(Long instanceId, String nodeKey, ProcessModelNode node)`，实现上述 `ALL/ANY/RATIO` 规则；
- 复用现有任务统计与 `isEffective`（已取消任务不进分母）。

### 4. 发起人重新提交
- `ApproveCommand` 增加分支：`__resubmit__` 任务同意时，不查节点、不走会签，直接
  `new StateMachine(model, merged).advance(model.entryKey())` 后 `applyResult(...)`；
  归一化模型从 `START_EVENT` 推进，legacy 模型入口即首个审批节点，两种都能落到正确待办。
- 常量 `WorkflowConstants.RESUBMIT_NODE_KEY = "__resubmit__"`、`RESUBMIT_NODE_NAME = "重新提交"`。

### 5. 指标与文档
- `WorkflowMetrics` 增加 `TASK_RETURN = "RETURN"`，`returnTask` 记录动作；退回原因（INITIATOR/PREVIOUS/SPECIFIED/COUNTER_SIGN）只进日志，不建高基数 tag。
- 同步 `docs/devlog/2026-11-14.md`：驳回语义变更、内部终止链路保留、会签退回规则、首节点限制。

## 不改动的部分

- 不加数据库列/迁移，不改 `init.sql`（退回目标不落库，待办本身即痕迹）。
- 不改流程定义 JSON、设计器、BPMN 导入导出。
- 不改 `rollback` 接口（仍为显式回退，权限 `sys:workflow:task:rollback`），但共享目标校验/退回执行逻辑。
- 归档口径不变：仍只有审批通过写 `finished_at`；退回中的实例保持运行中。

## 测试与验收

新增/调整单测（纯 Mockito，必要时用 `TableInfoHelper` 初始化 Wrapper）：
1. `ReturnCommandTest`
   - `INITIATOR`：取消原待办、实例停在入口、生成 `__resubmit__` 待办且处理人为发起人。
   - `PREVIOUS`：唯一上游节点 → 重建该节点待办；多上游 → 报错要求指定节点。
   - `SPECIFIED`：上游审批节点成功；当前/下游/非审批/不存在 → `BizException`。
   - 首个审批节点：`INITIATOR` 可用；`PREVIOUS/SPECIFIED` 报错；`__resubmit__` 任务驳回报错。
   - 会签 `ALL` 驳回 → 退回上一节点；无上一层 → 发起人。
   - 会签 `ANY`：仍有待办/已有人同意 → 不退回；全部驳回 → 退回。
   - 会签 `RATIO`：阈值已不可能 → 退回；仍可能 → 等待。
2. `ApproveCommandTest`：`__resubmit__` 同意后推进到首个审批节点并生成待办。
3. `ProcessTaskController` 契约测试（如已有 WebMvc 测试）：`target` 缺失 400、`SPECIFIED` 缺 `targetNodeKey` 400。

验证命令：
```powershell
mvn -o -pl whale-services/whale-service-workflow test
```
要求：全部通过、Checkstyle 0 违规；完成后按既定约定提交并推送 `origin/main`。

## 待确认的产品口径（未确认则按上述默认实现）

1. **首个审批节点是否允许驳回到发起人**：默认允许（只有入口节点不能驳回）。若“首节点永不设置驳回”指首个审批节点也不能驳回，则需把该节点 `rejectable` 直接置 false。
2. **会签「发起会签的人」**：当前未记录“是谁把流程推进到会签节点”的操作人，默认按“上一层审批节点”落地；若上一层不唯一则退回发起人。若要精确到人，需要额外记录进入会签节点的操作人快照（新增字段/迁移）。
3. **是否保留“终止/不通过”**：公开驳回改为退回后，审批人不再有“直接终止流程”的公开入口（内网回调仍可终止）。若业务需要，建议另加 `POST /{id}/terminate`，本次不做。

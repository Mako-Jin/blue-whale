# Wave 5.31 规格（whale-workflow 引擎升级 · 状态机内核 + BPMN2.0 + JSON 定义模型）

> 用户指令（2026-10-20）：「现在开始搭建工作流服务吧，包含以下三部分：简单的状态机，BPMN2.0，json 格式的工作流数据。」
> 经确认口径（冻结）：
> 1. **扩展现有 `whale-workflow`**（不新建模块、不推倒重来）；
> 2. **BPMN 轻量自研双向转换**（不引入 Flowable / Camunda）；
> 3. **通用状态机内核 + 审批跑其上**；
> 4. **本轮仅后端**（前端设计器 / BPMN 可视化不在范围）。
>
> 关联台账：[roadmap.md](../roadmap.md) §2.5、[task-breakdown.md](../task-breakdown.md)。
> 前置交付：Wave 5.24（流程定义 CRUD）、Wave 5.25（顺序审批闭环）。

---

## 一、目标与范围

以**规范化 JSON（`nodes[]` + `flows[]`）为唯一事实源**，把三部分串成一条主线：

| 部分 | 交付 | 落点 |
|---|---|---|
| ① 简单状态机 | 纯 Java 通用状态机内核（状态/事件/迁移，`Guard` 为唯一扩展点）；审批流转改由内核驱动 | `support/machine/`、`service/impl/*` 重构 |
| ② BPMN 2.0 | BPMN 2.0 XML ⇄ 内部 JSON 双向转换 + 结构校验（XXE 加固）；导入建定义 / 导出下载 | `support/bpmn/`、新增控制器 |
| ③ JSON 工作流数据 | 规范化模型 `{type,nodes[],flows[]}`（4 种节点类型）+ 校验/归一化；**向后兼容**旧 `{"nodes":[…]}` | `support/*`、`/workflow/definition/validate` |

**非目标（明确不做，见 §六）**：BPMN 运行时执行、并行/包容/事件网关、service/script 任务、子流程、边界事件、定时器、泳道、条件表达式求值、`variables_json`、会签/转办/回退、前端设计器与 BPMN 可视化、定义发布版本快照、实例迁移。

---

## 二、现状分析（已核验源码）

- [ProcessNodeParser.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/support/ProcessNodeParser.java)：唯一解析器，只认 `{"nodes":[{"key","name","assignee"}]}`，下标即顺序，`assignee` 必填，非法统一抛 `PARAM_ERROR`「流程定义缺少有效审批节点」。
- [ProcessInstanceServiceImpl.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/service/impl/ProcessInstanceServiceImpl.java#L60-L78)：`start` 载定义 → 解析节点 → 建实例（`currentIndex=0`）→ 首节点建待办。
- [ProcessTaskServiceImpl.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/service/impl/ProcessTaskServiceImpl.java#L78-L99)：`approve` **硬编码 `currentIndex+1`**，越界即实例 `APPROVED`；`reject` 置实例 `REJECTED` + 取消其余待办。
- 定义契约 **无图模型**（无 `flows`）、**无状态机抽象**、**无 BPMN**。
- JSON 解析沿用 Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`（传递依赖 nacos-client）；HTTP 层为 Boot 默认 Jackson 3。

---

## 三、冻结契约（实现前不得更改）

### 3.1 规范化 JSON（事实源）

```json
{ "type": "stateMachine",
  "nodes": [ { "key": "start", "type": "START_EVENT", "name": "开始" },
             { "key": "n1",    "type": "USER_TASK",   "name": "一级审批", "assignee": "admin" },
             { "key": "gw",    "type": "EXCLUSIVE_GATEWAY", "name": "金额判断" },
             { "key": "end",   "type": "END_EVENT",   "name": "结束" } ],
  "flows": [ { "key": "f1", "from": "start", "to": "n1" },
             { "key": "f2", "from": "n1",    "to": "gw" },
             { "key": "f3", "from": "gw",    "to": "end", "isDefault": true } ] }
```

- 节点类型仅 4 种：`START_EVENT` / `END_EVENT` / `USER_TASK` / `EXCLUSIVE_GATEWAY`。
- **模式判定 `ProcessModelParser`**：
  - `type=="stateMachine"` → **规范化**；
  - `type` 缺失**且无 `flows`** → **legacy**（原样委托 `ProcessNodeParser`，零改动）；
  - `type` 缺失但存在 `flows` → 规范化（宽容）；
  - 其它非空 `type` → `PARAM_ERROR`。
- **legacy 归一化**：`nodes[i]` → `USER_TASK`（`key` 缺省合成 `n{i+1}`），自动补 `i→i+1` 顺序流，入口 = `nodes[0]`，末节点无出流；**与 Wave 5.25 语义逐字节一致**。
- 上限：`MAX_NODES=200`、`MAX_FLOWS=400`。
- `condition` 本轮**解析/保留/往返**，但运行期**不参与选路**（仅产出 `CONDITION_IGNORED` 警告）。

### 3.2 状态机内核语义（唯一权威）

- 状态 = 节点 `key`；事件 = `START / APPROVE / REJECT / CANCEL / COMPLETE`；迁移 = `flows`。
- **统一选路 `pickFlow(node)`**：出流 0 → 终止；1 → 该流；≥2 → 取 `isDefault=true` 的流（校验器保证多分支必有一个 `isDefault`，否则报错）。
- **发起**：从入口起，`START_EVENT` / `EXCLUSIVE_GATEWAY` / `END_EVENT` 为透传（`START`→`COMPLETE`），命中首个 `USER_TASK` 建待办；legacy 定义首节点序 0 → `currentIndex=0`、首任务 = `nodes[0]`（与今日完全一致）。
- **同意**：当前节点 = `currentNodeKey`，为空则回落 `model.nodeAt(currentIndex)`（迁移前旧行）；0 出流 → 实例 `APPROVED`（不建任务）；目标 `USER_TASK` → 建任务；目标 `END_EVENT` → 实例 `APPROVED`；目标网关 → 透传循环，超 `MAX_GATEWAY_HOPS=16` 抛 `PARAM_ERROR`「网关流转超出上限」。
- **驳回 / 撤销**：逻辑与文案不变（驳回 → 实例 `REJECTED` + 其余待办 `CANCELED`；撤销 → 实例 `CANCELED` + 待办 `CANCELED`）。
- **错误文案逐字保留**：解析失败/缺失/空 `nodes`/空 `assignee` → `PARAM_ERROR`「流程定义缺少有效审批节点」；已处理 → 「任务已处理」；非运行中 → 「实例非运行中」；不存在 → `NOT_FOUND`。

### 3.3 REST + 权限种子（服务自持 `/workflow` 前缀，网关不剥离；种子当前最大 id = 57）

| 方法 | 路径 | 权限码（id） | 响应 |
|---|---|---|---|
| POST | `/workflow/definition/validate` | `sys:workflow:definition:validate`（**58**） | `Result<ProcessDefinitionValidateVO>`：`{valid,mode,errors[],warnings[],normalizedJson,nodeCount,flowCount}`，**内容非法不抛异常** |
| POST | `/workflow/definition/bpmn` | `sys:workflow:definition:bpmnImport`（**59**） | body `{code,name,bpmnXml,...}`（`consumes=application/json`，XML 以字符串字段传入）→ `Result<ProcessDefinitionVO>` |
| GET | `/workflow/definition/{id}/bpmn` | 复用 `sys:workflow:list`（43） | `ResponseEntity<byte[]>` `application/xml;charset=UTF-8`（先例：whale-form `FormDataController.export`） |

与既有映射无冲突（`POST /definition`、`PUT /{id}`、`GET /{id}`、`GET /page`）。

### 3.4 BPMN 2.0 映射

- 根：`definitions` 下恰好 1 个 `process`（亦接受无 `definitions` 的裸 `process`）；0 或 >1 → 错。
- `startEvent→START_EVENT`、`endEvent→END_EVENT`、`userTask→USER_TASK`、`exclusiveGateway→EXCLUSIVE_GATEWAY`；`key=id`、`name=name`；`assignee` 读**任意前缀**的 `assignee` 属性（plain / `flowable:` / `camunda:`），导出统一写 plain `assignee`（文档说明偏差点）。
- `sequenceFlow`：`key=id`、`from=sourceRef`、`to=targetRef`、`condition=conditionExpression` 文本；网关 `default="fId"` ⇒ `isDefault=true`（导出对称输出）。
- **忽略（非错误）**：BPMN-DI 命名空间元素（`BPMNDiagram`/`BPMNPlane`/`BPMNShape`/`BPMNEdge`）、`documentation`、`extensionElements`。
- **报错**（`BPMN_UNSUPPORTED_ELEMENT`，消息点名元素）：`serviceTask` / `scriptTask` / `manualTask` / `parallelGateway` / `inclusiveGateway` / `eventBasedGateway` / `subProcess` / `callActivity` / `boundaryEvent` / `intermediateCatchEvent` / `intermediateThrowEvent` / `timerEventDefinition` / `laneSet` / `lane`。
- 体积上限 `MAX_BPMN_XML_LENGTH=262144`；落库 JSON 仍受既有 65536 约束（复用 `ProcessDefinitionServiceImpl` 的长度校验）。

### 3.5 校验码（`ValidationCode` 枚举 + `ValidationIssue{code,message,level,refKey}`）

- **错误**：`MISSING_NODES`、`UNKNOWN_DEFINITION_TYPE`、`NODE_KEY_MISSING`、`NODE_KEY_INVALID`、`NODE_KEY_DUPLICATE`、`UNKNOWN_NODE_TYPE`、`USER_TASK_ASSIGNEE_REQUIRED`、`START_EVENT_COUNT`、`END_EVENT_REQUIRED`、`FLOW_KEY_DUPLICATE`、`FLOW_REFERENCE_MISSING`、`FLOW_SELF_LOOP`、`DEAD_END`、`UNREACHABLE_NODE`、`CYCLE_DETECTED`、`GATEWAY_NO_OUTGOING`、`GATEWAY_MULTI_BRANCH_NO_DEFAULT`、`TOO_MANY_NODES`、`TOO_MANY_FLOWS`；XML 侧：`XML_NOT_WELL_FORMED` / `XML_DOCTYPE_FORBIDDEN` / `XML_TOO_LARGE` / `BPMN_ROOT_INVALID` / `BPMN_PROCESS_COUNT` / `BPMN_UNSUPPORTED_ELEMENT`。
- **警告**：`CONDITION_IGNORED`（每条带 `condition` 的流一处）。

### 3.6 校验请求/响应契约

```
POST /workflow/definition/validate
body: { "definitionJson": "<JSON 文本>", "bpmnXml": "<可选，BPMN XML 文本>" }
Response: Result<ProcessDefinitionValidateVO>  HTTP 200（内容非法也 200，错误进 errors[]）
  { valid, mode(LEGACY|NORMALIZED|BPMN), errors[], warnings[], normalizedJson, nodeCount, flowCount }
```

> 规则：`definitionJson` / `bpmnXml` 二选一（都有则优先 `definitionJson`；都无 → `PARAM_ERROR`）。

---

## 四、改动清单（文件级）

**新增（`backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/`）**
- `support/`：`ProcessNodeType`、`ProcessModelNode`、`ProcessModelFlow`、`ProcessModel`（含 `entryKey/node(key)/nodeAt/outgoing`）、`ProcessModelParser`、`ValidationCode`、`ValidationIssue`、`ValidationReport`、`ProcessModelValidator`
- `support/machine/`：`StateMachineEvent`、`Guard`、`GuardContext`、`AlwaysGuard`、`DefaultFlowGuard`、`Transition`、`TransitionResult`、`StateMachine`
- `support/bpmn/`：`BpmnXmlSupport`（加固工厂）、`BpmnXmlParser`、`BpmnXmlWriter`
- `dto/`：`DefinitionValidateRequest`、`BpmnImportRequest`；`vo/`：`ProcessDefinitionValidateVO`、`ValidationIssueVO`
- `service/ProcessModelService` + `service/impl/ProcessModelServiceImpl`；`controller/ProcessModelController`
- `src/main/resources/db/migration-5.31.sql`
- 测试：`support/ProcessModelParserTest`、`support/ProcessModelValidatorTest`、`support/machine/StateMachineTest`、`support/bpmn/{BpmnXmlSupportTest,BpmnXmlParserTest,BpmnXmlWriterTest}`、`service/impl/ProcessModelServiceImplTest`

**修改**
- `support/ProcessNodeParser.java`：**保持字节不变**（成为新解析器的 legacy 分支）
- `service/impl/ProcessInstanceServiceImpl.java`、`service/impl/ProcessTaskServiceImpl.java`：改由内核驱动（语义/文案/DB 调用次数不变）
- `entity/ProcessInstance.java`、`vo/ProcessInstanceVO.java`：+ `currentNodeKey`（可加性）
- `src/main/resources/db/init.sql`：`process_instance` 加 `current_node_key VARCHAR(64) NULL ... AFTER current_index`（权威源同步）
- `backend/whale-services/whale-auth/src/main/resources/db/init.sql`：追加种子 **58**、**59**
- 既有测试 `ProcessInstanceServiceImplTest` / `ProcessTaskServiceImplTest` 扩展（legacy 用例保持全绿）

---

## 五、验证方式

1. **静态**：`mvn -o -pl :whale-workflow -am test checkstyle:check` 全绿；`mvn -o clean install -T 1C` SUCCESS。
2. **旧行为零回归**：Wave 5.25 的用例不改即过（钉住 `currentIndex`、首任务 assignee/`nodeIndex`、末节点→APPROVED、驳回/撤销、错误文案）。
3. **库证据**：`bluewhale_workflow.process_instance.current_node_key` 存在；`bluewhale_auth.sys_permission` 58/59 可读。（清 `whale:cache:auth:user:perm:{userId}`、重登，避免 403）
4. **legacy 冒烟**：`POST /workflow/definition`（2 节点旧 JSON）→ `POST /workflow/instance`（`status=0,currentIndex=0,currentNodeKey="n1"`）→ 待办含首审批人 → 同意 → 新任务 `nodeIndex=1,assignee=user` → 同意 → 实例 `status=1`；另以 SQL 插入 `current_node_key=NULL` 实例，同意成功（回落证明）。
5. **校验冒烟**：`/validate`（legacy → `valid=true,mode=LEGACY`；含网关 default+一条条件流 → `valid=true` + 一条 `CONDITION_IGNORED`；双 `startEvent`/悬空 `to`/成环/XML 含 `serviceTask` → `valid=false` 且码正确；`<!DOCTYPE…>` → `XML_DOCTYPE_FORBIDDEN`）。
6. **BPMN 往返**：`POST /workflow/definition/bpmn`（startEvent→userTask→exclusiveGateway+default→endEvent）→ `code=0` 存规范化 JSON → `GET /{id}/bpmn` 返回 `200 application/xml` → 再送 `/validate` → `valid=true`。
7. **负向/信封**：导出不存在 id → `404`；无 token → `403`；定义非法仍可发起 → `400`「流程定义非法：<code>」。

---

## 六、明确削减（避免过度设计）

1. **条件求值 / `variables_json` / 启动变量 → 本轮不做**：留 `Guard` SPI，后续增量（`variables_json` 可空列 + `ComparisonGuard`），零破坏。`condition` 仅保留往返 + 警告。
2. **`process_task.node_key` 列 → 不加**：`current_node_key` + `node_index`/`node_name` 已足够。
3. 并行/包容/事件网关、service/script 任务、子流程、定时器、边界事件、泳道 → 不做（导入即报错）。
4. BPMN-DI 渲染保真、`extensionElements`/`documentation` 往返 → 不做（导入忽略、导出省略）。
5. 单独 `normalize` 端点 → 不做（`/validate` 已回 `normalizedJson`）。
6. 导出另立权限码 → 不做（复用 43，先例 whale-form 5.30）。
7. 定义发布/版本快照、实例迁移、前端设计器/BPMN 查看器 → 不做。

---

## 七、决策记录（冻结）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 模块策略 | 扩展现有 `whale-workflow`，不新建模块 |
| 2 | BPMN 方案 | 轻量自研双向转换，不引入 Flowable/Camunda |
| 3 | 事实源 | 规范化 JSON `{type,nodes[],flows[]}`；legacy 自动归一化 |
| 4 | 兼容策略 | `type` 缺失且无 `flows` → 委托 `ProcessNodeParser`（字节不变） |
| 5 | 状态机扩展点 | `Guard` SPI（v1 仅 `AlwaysGuard`/`DefaultFlowGuard`） |
| 6 | 网关选路 | 多分支取 `isDefault`；condition 不参与选路（保 `CONDITION_IGNORED` 警告） |
| 7 | XML 安全 | JDK DOM + XXE 全加固 + 体积卡口，不新增依赖 |
| 8 | 导出信封 | 二进制 XML 直出（`ResponseEntity<byte[]>`），复用 `sys:workflow:list` |
| 9 | 迁移兼容 | `current_node_key` 可空，旧行 `NULL` 时回落 `currentIndex` |
| 10 | 本轮边界 | 仅后端；不做前端设计器/BPMN 可视化/条件求值 |

---

## 八、风险与对策

| # | 风险 | 缓解 |
|---|---|---|
| R1 | Jackson 2 是传递依赖，项目默认 JSON 为 Jackson 3 | 沿用既有 `support/*` 内静态 `ObjectMapper`（Jackson 2）写法 |
| R2 | 重构内核可能悄悄改动 Wave 5.25 行为 | `ProcessNodeParser` 不动；文案逐字保留；序映射保持 `currentIndex` 一致；`currentNodeKey=null` 回落；重跑旧用例 + legacy 运行时冒烟 |
| R3 | BPMN 导入 XXE / 实体膨胀 DoS | §3.4 + S3 加固 + 体积卡口 + 负向单测 |
| R4 | 请求路径 XML 反序列化 XXE | 导入仅以 **JSON 字符串字段** 收 XML，不声明 `application/xml` |
| R5 | 新种子未入角色集 / Redis 权限缓存陈旧 → 403 | 插 58/59 + 清缓存 + 重登后核验 |
| R6 | v1 拒绝成环 / 多分支无 default → 部分真实 BPMN 导不进 | 明确阻断码与修复提示；如需宽容，改一行降级为警告 |
| R7 | 二进制 XML 导出绕过 `Result<T>` 信封 | 记录先例（whale-form 5.30 导出）并在本 spec 说明理由 |

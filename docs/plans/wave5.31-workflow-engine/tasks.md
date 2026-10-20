# Wave 5.31 任务分解（whale-workflow 引擎升级）

> 规格：[spec.md](spec.md)。执行原则：**先落文档 → 冻结契约 → 编码 → 模块自检 → 全量回归 → 检查报告 → 台账勾选 → commit & push origin(Gitee)**。
> 并行策略：S1/S2/S3 均为 `support/` 下新增包，无文件交集，可并行；S4（库表/种子）与 S1~S3 无交集，可并行。S5 依赖 S1~S4；S6 依赖 S5。

---

## Wave 5.31-a：契约冻结与并行实施

### 线 A：S1 模型层（`support/`，纯 Java）

- [x] **A1** `ProcessNodeType` 枚举（4 种节点类型 + `from(String)` 解析）
- [x] **A2** `ProcessModelNode` / `ProcessModelFlow` / `ProcessModel`（`entryKey/node(key)/nodeAt/outgoing`）
- [x] **A3** `ProcessModelParser`（模式判定：stateMachine / legacy / 无 type 有 flows；legacy 归一化委托 `ProcessNodeParser`；上限 `MAX_NODES=200`/`MAX_FLOWS=400`）
- [x] **A4** `ValidationCode` 枚举 + `ValidationIssue`（`code,message,level,refKey`）+ `ValidationReport`（`errors[]/warnings[]`）
- [x] **A5** `ProcessModelValidator`（§3.5 全部错误码 + `CONDITION_IGNORED` 警告）

### 线 B：S2 状态机内核（`support/machine/`，纯 Java）

- [x] **B1** `StateMachineEvent` 枚举（START/APPROVE/REJECT/CANCEL/COMPLETE）
- [x] **B2** `Guard` / `GuardContext` / `AlwaysGuard` / `DefaultFlowGuard`
- [x] **B3** `Transition` / `TransitionResult` / `StateMachine`（`pickFlow` 统一选路 + 网关透传 + `MAX_GATEWAY_HOPS=16`）

### 线 C：S3 BPMN 双向转换（`support/bpmn/`，纯 JDK）

- [x] **C1** `BpmnXmlSupport`：加固 `DocumentBuilderFactory`（禁 DOCTYPE/外部实体/XInclude/外部 DTD·schema、`FEATURE_SECURE_PROCESSING`、空 `EntityResolver`、预解析长度卡口 `MAX_BPMN_XML_LENGTH=262144`）
- [x] **C2** `BpmnXmlParser`：XML → `ProcessModel`（映射 §3.4；忽略 DI/documentation/extensionElements；不支持元素报 `BPMN_UNSUPPORTED_ELEMENT`）
- [x] **C3** `BpmnXmlWriter`：`ProcessModel` → XML（对称输出 `assignee` / 网关 `default`）

### 线 D：S4 库表/实体/种子

- [x] **D1** `whale-workflow/src/main/resources/db/init.sql`：`process_instance` + `current_node_key VARCHAR(64) NULL AFTER current_index`
- [x] **D2** 新增 `whale-workflow/src/main/resources/db/migration-5.31.sql`（`ALTER TABLE process_instance ADD COLUMN current_node_key ...`）
- [x] **D3** `entity/ProcessInstance.java` + `vo/ProcessInstanceVO.java`：+ `currentNodeKey`
- [x] **D4** `whale-auth/src/main/resources/db/init.sql`：追加种子 **58** `sys:workflow:definition:validate`、**59** `sys:workflow:definition:bpmnImport`
- [x] **D5** 实机执行 DDL + 种子（`bluewhale_workflow` / `bluewhale_auth`）—— `current_node_key varchar(64) NULL` 已存在；`sys_permission` id 58 `sys:workflow:definition:validate` / 59 `sys:workflow:definition:bpmnImport` 已就位

---

## Wave 5.31-b：S5 服务接线

- [x] **E1** `service/ProcessModelService` + `service/impl/ProcessModelServiceImpl`（`validate` / `importBpmn` 委托 `ProcessDefinitionService.create` / `exportBpmn`）
- [x] **E2** `controller/ProcessModelController`（3 端点，权限 58/59/43）
- [x] **E3** `dto/{DefinitionValidateRequest,BpmnImportRequest}`、`vo/{ProcessDefinitionValidateVO,ValidationIssueVO}`
- [x] **E4** `ProcessInstanceServiceImpl#start` 改由内核驱动（legacy 语义零回归）
- [x] **E5** `ProcessTaskServiceImpl#approve` 改由内核驱动（`currentNodeKey` + `currentIndex` 回落；驳回/撤销不变）

---

## Wave 5.31-c：S6 测试与静态门禁

- [x] **F1** `support/ProcessModelParserTest`（模式判定 / legacy 归一化 / 上限）
- [x] **F2** `support/ProcessModelValidatorTest`（各错误码 + `CONDITION_IGNORED`）
- [x] **F3** `support/machine/StateMachineTest`（选路 / default / 网关透传 / 跳数上限）
- [x] **F4** `support/bpmn/*Test`（加固负向 XXE/DOCTYPE/超大；映射往返；不支持元素报错）
- [x] **F5** `service/impl/ProcessModelServiceImplTest`（validate/import/export 委托）
- [x] **F6** 既有 `ProcessInstanceServiceImplTest` / `ProcessTaskServiceImplTest` 扩展（legacy 用例全绿）
- [x] **F7** `mvn -o -pl :whale-workflow -am test checkstyle:check`（0 违规，102 用例全绿）

---

## Wave 5.31-d：全量回归与运行时冒烟

- [x] **G1** 停运行中服务 → `mvn -o clean install -T 1C` BUILD SUCCESS → 重启并核验健康 —— **28/28 模块 BUILD SUCCESS**（whale-workflow 102 用例全绿）；重启 whale-auth 8081 / whale-workflow 8083 / whale-gateway 8080 并核验监听
- [x] **G2** legacy 冒烟（定义→发起→同意→同意，`currentNodeKey` 断言）—— PASS（`n1` → `n2` → 实例 status=1）
- [x] **G3** 校验冒烟（LEGACY / CONDITION_IGNORED / 各负向码）—— PASS 18/18（LEGACY + NORMALIZED+`CONDITION_IGNORED`；16 个错误码：`UNKNOWN_DEFINITION_TYPE`/`MISSING_NODES`/`NODE_KEY_DUPLICATE`/`UNKNOWN_NODE_TYPE`/`USER_TASK_ASSIGNEE_REQUIRED`/`FLOW_REFERENCE_MISSING`/`FLOW_SELF_LOOP`/`DEAD_END`/`END_EVENT_REQUIRED`/`START_EVENT_COUNT`/`UNREACHABLE_NODE`/`CYCLE_DETECTED`/`GATEWAY_NO_OUTGOING`/`GATEWAY_MULTI_BRANCH_NO_DEFAULT`/`XML_DOCTYPE_FORBIDDEN`/`BPMN_UNSUPPORTED_ELEMENT`）
- [x] **G4** BPMN 往返冒烟（导入 → 导出 → 再校验）—— PASS（导入建定义 → 导出 657B XML → 再校验 `mode=BPMN valid=true nodes=4 flows=3`，规范化 JSON 与源同构）
- [x] **G5** 回落冒烟（`current_node_key=NULL` 实例同意成功）+ 负向信封（404/403/400）—— PASS（`current_node_key` 置 NULL 后同意成功推进至 `n2`；信封 `404`/`400`/`401`（网关无 token）/`403`（服务端无权限头），业务码均在 `Result.code` 内）

---

## Wave 5.31-e：收尾

- [x] **H1** 检查报告：`docs/reviews/2026-10-20-wave5.31-workflow-engine.md`
- [x] **H2** devlog：`docs/devlog/2026-10-20.md` 追加 Wave 5.31 段落
- [x] **H3** 台账勾选：`roadmap.md` §2.5、`task-breakdown.md`
- [x] **H4** commit & push `origin`（Gitee `https://gitee.com/StrongToStop/blue-whale.git`）

---

## 验收标准

| 项 | 标准 |
|---|---|
| ① 状态机 | `support/machine` 纯 Java 内核可独立单测；审批流转由其驱动 |
| ② BPMN | XML ⇄ JSON 双向；加固生效（DOCTYPE/超大/不支持元素负向均报对应码） |
| ③ JSON | `/validate` 回 `normalizedJson`；legacy 定义零改动可发起并通过 |
| 兼容 | Wave 5.25 旧用例不改即过；`current_node_key=NULL` 旧实例可继续推进 |
| 收尾 | 检查报告 + devlog + 台账勾选 + origin(Gitee) 推送成功 |

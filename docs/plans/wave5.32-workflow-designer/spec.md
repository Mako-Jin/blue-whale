# Wave 5.32 规格（whale-workflow-designer 工作流前端 · 管理 + 绘制 + 表单绑定）

> 用户指令（2026-10-20）：「后端已经完成了吗？现在开始前端？管理和绘制，表单可以选择表单管理器的表单。」
> 经 AskUserQuestion 冻结口径：
> 1. **新建独立前端应用** `whale-frontend/platform-apps/whale-workflow-designer`（端口 **8003**），与 whale-form-designer 并列；
> 2. **自研自由画布流程图**（SVG 自绘 + 复用 `@dnd-kit`），**不引入 React Flow**；
> 3. 本轮覆盖四域：**流程定义管理 / 流程发起+填表单 / 我的待办+审批 / 流程实例监控**；
> 4. **允许后端最小可加性补强**：节点绑定表单（`formId`/`formCode`）、节点坐标（`x`/`y`）、待办携带表单信息。
>
> 关联台账：[roadmap.md](../roadmap.md) §2.5、[task-breakdown.md](../task-breakdown.md)。
> 前置交付：Wave 5.31（后端引擎：JSON 事实源 + 状态机内核 + BPMN 双向）、Wave 5.29/5.30（whale-form-designer，表单来源）。

---

## 一、目标与范围

| 层 | 内容 | 落点 |
|---|---|---|
| 后端 whale-workflow | 可加性补强：节点 `formId`/`formCode`/`x`/`y` 与 JSON 往返；BPMN `formKey` 往返；`process_task` 增 `node_key`/`form_id`/`form_code`；待办 VO 透出 | `support/*`、`support/bpmn/*`、`entity/*`、`vo/*`、`db/*` |
| 前端（新应用 8003） | 应用骨架 + 自由画布设计器 + 定义管理 + 流程发起（填表单）+ 待办审批（回显表单）+ 实例监控 | `whale-frontend/platform-apps/whale-workflow-designer` |
| 共享组件 | 抽取 `FormRenderer` 至 `elemental/whale-ui`，两个前端应用共用 | `whale-frontend/elemental/whale-ui`、`whale-form-designer`（改 re-export） |
| 闭环 | `绘制定义 → 保存 → 校验 → 发起（填绑定表单）→ 待办审批（回显表单）→ 实例监控` | — |

**非目标（见 §六）**：审批时表单可编辑写回、条件表达式求值、会签/转办/回退、BPMN-DI 坐标回写、定义版本快照、实例迁移。

---

## 二、现状分析（已核验源码）

| # | 事实 | 影响 |
|---|---|---|
| 1 | [ProcessModelNode.java](file:///d:/BlueWhale/BlueWhale/whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/support/ProcessModelNode.java#L11-L32) 仅 `key/type/name/assignee`；无表单绑定、无坐标 | 需加 4 个可空字段 |
| 2 | [ProcessModelParser.java](file:///d:/BlueWhale/BlueWhale/whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/support/ProcessModelParser.java#L163-L187) `readNode` **逐字段**读取 `JsonNode` | 未知字段被静默丢弃 → 坐标无法"夹带"在 JSON 里，必须显式加字段 |
| 3 | [ProcessModelServiceImpl.java](file:///d:/BlueWhale/BlueWhale/whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/service/impl/ProcessModelServiceImpl.java#L184-L212) `toJson` **逐字段**写出 | 新增字段必须同步补进 `toJson`，否则保存即丢失 |
| 4 | [ProcessTaskVO.java](file:///d:/BlueWhale/BlueWhale/whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/vo/ProcessTaskVO.java#L14-L36) 仅 `nodeIndex/nodeName/assignee`，无 `nodeKey`、无表单 | 待办页拿不到绑定表单 → 需 DDL + 实体 + VO |
| 5 | [ProcessInstanceVO.java](file:///d:/BlueWhale/BlueWhale/whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/vo/ProcessInstanceVO.java#L20-L56) 已含 `businessKey`、`currentNodeKey`、`tasks` | 实例监控页与"表单数据定位"可直接复用 |
| 6 | [ProcessDefinitionVO.java](file:///d:/BlueWhale/BlueWhale/whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/vo/ProcessDefinitionVO.java#L19-L43) 已含 `definitionJson` | 设计器可回读画布模型 |
| 7 | [application.yml](file:///d:/BlueWhale/BlueWhale/whale-backend/whale-services/whale-gateway/src/main/resources/application.yml#L45-L52) 已有 `/workflow/**` 与 `/form/**` 两条 lb 路由 | 前端可跨服务取表单，无需改网关 |
| 8 | [FormRenderer.tsx](file:///d:/BlueWhale/BlueWhale/whale-frontend/platform-apps/whale-form-designer/src/render/FormRenderer.tsx) 是 **应用内组件**（非共享包），受控受全量值、`disabled` 可整体只读 | 工作流发起/审批页复用需先抽取到共享包 |
| 9 | 前端工程为 pnpm workspace（`shared-packages/*` + `elemental/*` + `platform-apps/*`），共享组件库为 `elemental/whale-ui` | 新应用按既有约定加入 workspace 即可 |

---

## 三、冻结契约

### 3.1 后端补强（whale-workflow，纯加字段，向后兼容）

**节点字段（规范化 JSON）**

```
{ "key":"n1", "type":"USER_TASK", "name":"一级审批", "assignee":"admin",
  "formId":"2112355156699869186", "formCode":"bw_d29_form",   // 可空：绑定表单
  "x":320, "y":180 }                                          // 可空：画布坐标
```

- `ProcessModelNode` + `String formId`、`String formCode`、`Integer x`、`Integer y`（全部可空）。
- `ProcessModelParser.readNode` 读这 4 个字段；legacy 归一化**不带**（保持 `null`）。
- `ProcessModelServiceImpl.toJson` 同步写回（`formId`/`formCode` 走 `putIfPresent`；`x`/`y` 仅在非空时写整型）。
- `formId` 以 **String** 承载（后端 Long 序列化为字符串，避免前端精度丢失，与 whale-form 契约一致）。

**校验码（`ValidationCode` 追加 2 个，均为边界输入校验）**

| 码 | 触发 |
|---|---|
| `USER_TASK_FORM_CODE_INVALID` | USER_TASK 的 `formCode` 非空且不匹配 `^[A-Za-z0-9_-]{1,64}$` |
| `USER_TASK_FORM_INCOMPLETE` | USER_TASK 的 `formId` 与 `formCode` 仅有一个非空（必须成对） |

> 不做跨服务存在性校验（whale-workflow 不依赖 whale-form）；非 USER_TASK 节点携带表单字段时不报错，仅被忽略。

**BPMN 映射（对称往返）**

- 导入：`userTask` 读**任意前缀**的 `formKey` → `formCode`、`formId` → `formId`（无则 `null`）；坐标不从 BPMN 读。
- 导出：`userTask` 在 `formCode` 非空时写 plain `formKey`、`formId` 非空时写 plain `formId`；**不输出 `x`/`y`**（BPMN-DI 不在范围）。
- 既有 `IGNORED`/`UNSUPPORTED` 集合与报错文案不变。

**库表（`process_task` 增 3 列，均可空）**

| 列 | 类型 | 说明 |
|---|---|---|
| `node_key` | `VARCHAR(64) NULL` | 建任务时的节点键（规范化模型用 key；legacy 用合成 key `n{i+1}`） |
| `form_id` | `VARCHAR(64) NULL` | 节点绑定的表单定义 ID（字符串承载，可空） |
| `form_code` | `VARCHAR(64) NULL` | 节点绑定的表单编码（可空） |

- 落点：`entity/ProcessTask` + 3 字段；`db/migration-5.32.sql`（幂等）+ `db/init.sql`（权威源同步）。
- 建任务处（实例发起 / 同意推进）从当前节点模型回填三列。
- `ProcessTaskVO` + `nodeKey` / `formId` / `formCode`。

### 3.2 前端应用骨架（8003）

- vite proxy：`'^/(auth|workflow|form)(/|$)'` → `http://localhost:8080`（复用表单设计器同款正则，避免"前缀即完整路径"落 vite 404）。
- token/http/auth **复用 admin 与 form-designer 的既有实现约定**，storage 前缀 `whale_wf_`；`Id = string`（禁止 `Number(id)`）。
- 静态菜单：流程定义 `/definitions`、流程发起 `/start`、我的待办 `/tasks`、流程实例 `/instances`。
- 依赖：`@bluewhale/whale-ui`、`whale-common-theme`、`whale-common-tools`、`antd`、`react-router-dom`、`@dnd-kit/*`、`dayjs`。

### 3.3 设计器序列化契约

- 画布 ↔ 规范化 JSON `{ type:"stateMachine", nodes[], flows[] }`（节点含 `x`/`y`）。
- 载入：`GET /workflow/definition/{id}` 的 `definitionJson` → 解析；缺失 `x`/`y` 的节点（legacy/BPMN 导入）→ 前端自动纵向布局并在保存时回填。
- 保存：`POST /workflow/definition`（新建）/ `PUT /workflow/definition/{id}`（更新），字段 `{code,name,definitionJson}`。
- 校验：`POST /workflow/definition/validate` → `Result{valid,mode,errors[],warnings[]}`；`errors[].refKey` 映射到画布节点/连线并高亮，`warnings`（`CONDITION_IGNORED`）以提示条展示。
- BPMN 导入：`POST /workflow/definition/bpmn`（`{code,name,bpmnXml}`）；导出：`GET /workflow/definition/{id}/bpmn` → `downloadBlob`。
- 连线交互（v1 最小实现）：选中源节点 → 点击目标节点建流；连线可选中 → 删除/设为该网关的 `isDefault`。

### 3.4 表单绑定与数据闭环

- **选择表单**：属性面板下拉 → `GET /form/definition/page?current=1&size=200` → 回填 `formId` + `formCode`。
- **发起**：入口 `USER_TASK` 节点的 `formId` → `GET /form/definition/{id}` → `<FormRenderer>` 填报 → `POST /form/data`（`{definitionId,dataJson}`）取数据 ID → `POST /workflow/instance`（`{definitionId,title,businessKey=<数据ID>}`）；未绑定表单 → 仅填 `title` 直接发起。
- **待办审批**：任务 VO 的 `formId` → `GET /form/definition/{formId}`；`GET /workflow/instance/{instanceId}` 的 `businessKey` → `GET /form/data/{businessKey}` → `<FormRenderer disabled>` **只读回显** + 审批意见 → `POST /workflow/task/{id}/approve|reject`。
- **降级**：whale-form 不可达或数据缺失 → 表单区域显示「无表单数据」占位，审批仍可继续（不阻塞流转）。

### 3.5 权限

全部复用既有权限码，**不新增种子**：`sys:workflow:list`（43）、`sys:workflow:definition:validate`（58）、`sys:workflow:definition:bpmnImport`（59）、定义 CRUD / 实例 / 任务既有码，表单侧复用 `sys:form:*`。

---

## 四、改动清单（文件级）

**后端（`whale-backend/whale-services/whale-workflow`）**
- 改：`support/ProcessModelNode.java`、`support/ProcessModelParser.java`、`support/ProcessModelValidator.java`、`support/ValidationCode.java`、`support/bpmn/BpmnXmlParser.java`、`support/bpmn/BpmnXmlWriter.java`、`service/impl/ProcessModelServiceImpl.java`（`toJson`）、`service/impl/ProcessInstanceServiceImpl.java`、`service/impl/ProcessTaskServiceImpl.java`（建任务回填三列）、`entity/ProcessTask.java`、`vo/ProcessTaskVO.java`
- 新增：`src/main/resources/db/migration-5.32.sql`
- 改：`src/main/resources/db/init.sql`（`process_task` 建表同步 3 列）
- 测试：扩展 `ProcessModelParserTest`、`ProcessModelValidatorTest`、`BpmnXmlParserTest`、`BpmnXmlWriterTest`、`ProcessModelServiceImplTest`、`ProcessTaskServiceImplTest`（新增表单/坐标往返与三列落库断言）

**共享组件（`whale-frontend/elemental/whale-ui`）**
- 新增 `src/components/FormRenderer/index.tsx`（自 form-designer 迁入，props 签名不变：`fields/value/onChange/disabled`）+ 结构化字段类型 `WhaleFormFieldConfig`
- 改：`whale-form-designer/src/render/FormRenderer.tsx` → `export { FormRenderer as default } from '@bluewhale/whale-ui'`（**引用路径与调用点零改动**）

**前端新应用（`whale-frontend/platform-apps/whale-workflow-designer`）**
- 骨架：`package.json` / `index.html` / `tsconfig.json` / `tsconfig.node.json` / `vite.config.ts` / `src/main.tsx` / `src/App.tsx` / `src/vite-env.d.ts`
- 契约层：`src/api/{token,http,auth,workflow,form}.ts`
- 框架：`src/router/guards.tsx`、`src/layouts/AppLayout.tsx`、`src/pages/Login.tsx`
- 设计器：`src/pages/designer/{WorkflowDesigner,Palette,Canvas,NodeShape,EdgePath,PropertyPanel,FormPicker}.tsx` + `src/pages/designer/model.ts`（画布↔JSON 序列化与自动布局）
- 定义管理：`src/pages/definition/DefinitionList.tsx`
- 流程发起：`src/pages/start/ProcessStart.tsx`
- 待办审批：`src/pages/task/TaskCenter.tsx`
- 实例监控：`src/pages/instance/InstanceList.tsx`

**文档**
- 本 `spec.md` / `tasks.md`；报告 `docs/reviews/2026-10-2X-wave5.32-workflow-designer.md`；`docs/devlog/2026-10-2X.md`；`docs/plans/{roadmap,task-breakdown}.md` 台账。

---

## 五、验证方式

1. **后端静态**：`mvn -o -pl :whale-workflow -am test checkstyle:check` 全绿；`mvn -o -f whale-backend/pom.xml clean install -T 1C` **BUILD SUCCESS（28/28）**；**Wave 5.31 既有 102 用例零回归**。
2. **DDL 证据**：`bluewhale_workflow.process_task` 存在 `node_key`/`form_id`/`form_code` 三列。
3. **后端运行时冒烟**（经网关 8080）：
   - 保存含 `formId/formCode/x/y` 的定义 → `GET /workflow/definition/{id}` 的 `definitionJson` 字段**往返保留**；
   - `/validate` 非法 `formCode` → `USER_TASK_FORM_CODE_INVALID`；仅 `formId` → `USER_TASK_FORM_INCOMPLETE`；
   - 发起 → `GET /workflow/task/page` 的待办 VO 含 `nodeKey`/`formId`/`formCode`；
   - BPMN 含 `formKey` → 导入后节点 `formCode` 一致 → 导出 XML 再含 `formKey`（往返）。
4. **前端构建**：`pnpm --filter whale-workflow-designer build`（`tsc -b && vite build`）通过；`pnpm -C frontend build`（全量）通过；`whale-form-designer` 构建回归通过。
5. **浏览器 E2E**（`browser_use`，8003 → 网关 8080，admin/123456）：
   - 登录；四菜单可达；
   - **绘制**：拖入 4 类节点 + 连线 → 保存 → 重载后坐标与表单绑定保留；
   - **校验**：造错（双 `START_EVENT` / 成环）→ `errors[]` 提示与画布高亮；
   - **BPMN**：导出 XML 下载 → 重新导入建定义；
   - **发起**：选定义 → 填绑定表单 → 提交 → 实例生成且待办出现；
   - **待办**：抽屉回显绑定表单 + 意见 → 同意推进至下一节点；
   - **实例监控**：列表 + 详情（当前节点 / 任务轨迹 / 状态）。
6. **表单设计器无回归**：`whale-form-designer` `/render/:id` 浏览器冒烟（共享渲染器抽取后）。

---

## 六、明确削减（避免过度设计）

1. **审批时表单可编辑写回 → 不做**：whale-form 无 `PUT /form/data/{id}`；v1 审批页**只读回显**，写回留后续增量（仅需 whale-form 加一个更新端点）。
2. **条件表达式求值 / 分支条件编辑 → 不做**：仅保留 `condition` 文本编辑与 `CONDITION_IGNORED` 警告展示（与 5.31 内核口径一致）。
3. **会签 / 转办 / 回退 / 加签 → 不做**（内核无对应事件）。
4. **网关多分支真实选路模拟 → 不做**（v1 取 `isDefault`）。
5. **BPMN-DI 坐标回写 / BPMN 图形保真 → 不做**（画布坐标存 JSON，不进 XML）。
6. **画布高级能力**（对齐线/吸附/分组/泳道/撤销重做/缩放平移）→ 不做，v1 仅拖拽 + 连线 + 选中删除。
7. **定义版本快照 / 发布 / 实例迁移 → 不做**。
8. **移动端与响应式适配 → 不做**（桌面端）。
9. **不新增权限码、不动 `schema_json`、不改 legacy 语义**（`ProcessNodeParser` 保持字节不变）。

---

## 七、决策记录（冻结）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 应用落点 | 新建独立应用 `whale-workflow-designer`（8003），不与 whale-form-designer 合并 |
| 2 | 设计器形态 | 自研 SVG 自由画布 + `@dnd-kit` 拖入，不引入 React Flow |
| 3 | 节点坐标 | 进规范化 JSON（`x`/`y` 可空），后端加字段并加入 `toJson` 往返 |
| 4 | 表单绑定 | 节点 `formId`(String)+`formCode`；落 `process_task` 三列以便待办/已办直接展示 |
| 5 | 表单渲染器复用 | 抽取 `FormRenderer` 至 `whale-ui`；form-designer 改为 re-export，**引用路径与 props 不变** |
| 6 | 审批表单 v1 | 只读回显（`disabled`）；可编辑写回列入后续增量 |
| 7 | 表单数据存储 | 数据落 whale-form（`POST /form/data`），实例 `businessKey` = 表单数据 ID |
| 8 | 后端边界 | 只补强 whale-workflow；**不触碰 whale-form** |
| 9 | 权限 | 全部复用既有权限码，不新增种子 |
| 10 | 本轮边界 | 四域前端 + 最小后端补强；不做 BPMN-DI / 条件求值 / 会签 |

---

## 八、风险与对策

| # | 风险 | 缓解 |
|---|---|---|
| R1 | 抽取 `FormRenderer` 破坏运行中的 whale-form-designer | 保持引用路径与 props 签名不变（re-export）+ `whale-form-designer` 构建与 `/render/:id` 浏览器回归 |
| R2 | 新增节点字段 / 校验码导致 5.31 的 102 用例回归 | 纯加字段、legacy 分支不带新字段；重跑全部既有用例 |
| R3 | `process_task` DDL 改动影响既有数据 | 3 列均可空 + 幂等 migration（存在性守卫）+ `init.sql` 权威同步 |
| R4 | `businessKey` 复用为表单数据 ID 的语义约定 | 在 spec 明确约定；前端取不到数据时降级为「无表单数据」占位，不阻塞审批 |
| R5 | 画布连线交互复杂、自动化难验证 | v1 采用「选中源 → 点目标」最小交互；E2E 以"保存后 JSON 结构正确"为主证据，手势部分标注需人工复核 |
| R6 | whale-form 服务未启动 | 发起/审批页表单加载失败仅降级提示；工作流流转不受影响 |
| R7 | 前端 `Long` 精度丢失重演（Wave 5.29 P0） | 契约层统一 `Id = string`，全链路禁 `Number(id)` |

# Wave 5.42 工作流服务收尾：版本管理 UI + 画布分组/泳道

## 1. 背景与目标

用户要求「完成工作流服务剩余任务，这一轮尽量完成所有代办项，进行收尾」。W1–W6 已完成并推送，剩余两项：

1. **P1 定义版本管理前端 UI**：后端已有 `GET /definition/{id}/versions`、`POST /definition/{id}/publish`、`POST /definition/{id}/migrate`，但前端契约层与设计器 UI 均缺失（Wave 5.38 devlog §十 留待后续）。
2. **P2 backlog 5b 画布分组 / 泳道容器**：需要模型、解析、校验、BPMN 双向映射、前端渲染与交互的跨端最小实现。

本轮同时完成构建、单测、前端构建、浏览器验证、devlog/roadmap/backlog 更新与提交推送。

## 2. 范围

### In scope

- 前端版本管理抽屉：版本列表（草稿/已发布）、发布、运行中实例迁移。
- 规范化 JSON 新增可选分组元数据：`groups:[{key,name}]` + 节点 `groupKey`（不持久化容器坐标，前端按成员节点包围盒派生）。
- 后端解析 / 校验 / 归一化输出 / BPMN 导出与导入对分组的支持。
- 前端画布分组容器渲染、节点归属编辑、分组的增删改。

### Out of scope

- 真正的 BPMN 多池/多流程（multi-pool）、跨 lane 自动布局、分组嵌套。
- 分组的历史版本独立管理、权限细分。
- 运行中实例的字段级迁移策略调整（沿用 Wave 5.38 已有实现）。

## 3. 关键现状（已核对源码）

- `ProcessModel` 仅 `definitionType/legacy/nodes/flows/nodeIndex`；`ProcessModelNode` 无 `groupKey`。
- `ProcessModelParser.normalized()` 只读 `nodes/flows`；`readNode` 逐字段读取（不读的字段会丢）。
- `ProcessModelValidator` 分层：模式判定 → 节点 → 流转 → 图级；`validateModel(model,mode)` 供 BPMN 导入复用。
- `ProcessModelServiceImpl.toJson()` 逐字段写 nodes/flows，**新增字段必须显式写入**，否则保存后丢失。
- `BpmnXmlParser.UNSUPPORTED` 当前包含 `laneSet`、`lane`，实现泳道时必须移除；`IGNORED` 需补充 `flowNodeRef`。
- `BpmnXmlWriter` 在 `<process>` 内先写节点后写流转，`appendDiagram` 只写节点 Shape/Bounds。
- 前端 `NormalizedDefinition` / `ProcessModelNode` / `CanvasModel` 无 groups/groupKey；`EMPTY_MODEL` 与 `parseDefinition` 返回值需同步补 `groups: []`。
- 版本端点后端契约：
  - `GET /definition/{id}/versions` → `ProcessDefinitionVersionVO[]`（`publishStatus` 0 草稿 / 1 已发布）
  - `POST /definition/{id}/publish` → body `{version}` → `ProcessDefinitionVO`
  - `POST /definition/{id}/migrate` → body `{targetVersion, instanceIds?}` → `DefinitionMigrationVO{migrated,skipped,failed,failures[]}`

## 4. 设计决策

### 4.1 分组/泳道数据模型（JSON 元数据）

规范化 JSON：

```json
{
  "type": "stateMachine",
  "groups": [{ "key": "g1", "name": "财务审批" }],
  "nodes": [{ "key": "n1", "type": "USER_TASK", "groupKey": "g1", "...": "..." }],
  "flows": []
}
```

- `groups` 可选；缺失等价于空数组（向后兼容）。
- 分组 key 复用节点 key 规则 `^[A-Za-z0-9_-]{1,64}$`；上限 50。
- 节点 `groupKey` 为空/缺失表示未分组；引用不存在分组报 `NODE_GROUP_UNKNOWN`。
- `groups` **不写容器坐标**：容器矩形由成员节点 `x/y` + 节点尺寸派生（padding 24，顶部标签区 24）。因此无坐标节点不会产生容器，分组仅在画布层呈现。
- BPMN 映射：
  - `bpmn:laneSet id="LaneSet_{processId}"` 内每个 `bpmn:lane id="{groupKey}" name="{groupName}"`，成员 `bpmn:flowNodeRef` 写节点 key。
  - BPMN-DI 为每个有成员的分组追加 `BPMNShape bpmnElement="{groupKey}"` + `dc:Bounds`（成员节点包围盒）。
  - 导入时 lane id → group key，lane name → group name，flowNodeRef → node.groupKey；lane Bounds 忽略（画布重新派生）。同一节点出现在多个 lane 时以首个为准。

### 4.2 新增校验码

| 码 | 级别 | 触发 |
| --- | --- | --- |
| `GROUP_KEY_INVALID` | ERROR | 分组 key 缺失/不合法 |
| `GROUP_KEY_DUPLICATE` | ERROR | 分组 key 重复 |
| `NODE_GROUP_UNKNOWN` | ERROR | 节点 groupKey 引用不存在的分组 |
| `TOO_MANY_GROUPS` | ERROR | 分组数 > 50 |

legacy 模式不执行分组校验（其 JSON 无 groups）。BPMN 导入的模型在 `validateModel` 中执行 `NODE_GROUP_UNKNOWN` 与 key 校验（解析器已保证 key 唯一）。

### 4.3 版本管理 UI

- 入口：设计器顶栏「版本」按钮（仅 `definitionId` 存在时可用）。
- 抽屉内容：版本快照表（版本号 / 状态 Tag / 创建时间 / 操作），操作列「发布」；底部「迁移运行中实例」入口。
- 发布：确认后 `POST .../publish {version}`，成功后刷新版本列表并重新加载定义内容（发布切换定义表内容）。
- 迁移：Modal 输入目标版本（默认当前选中版本）+ 可选实例 ID 列表（逗号/换行分隔，字符串承载雪花 ID，禁止 Number()）；提交后展示 `migrated/skipped/failed` 摘要与 failures 明细。
- 契约层类型严格对齐后端字段名，禁止改名。

### 4.4 前端分组交互

- `CanvasGroup { key, name }`；`CanvasNode.groupKey: string | null`；`CanvasModel.groups`。
- `nextKey` 前缀扩展为 `'n' | 'f' | 'g'`。
- Canvas 在连线/节点之下渲染分组容器（浅色填充 + 虚线边框 + 左上标题，`pointerEvents="none"`，不参与命中）。
- 节点属性面板新增「所属分组」Select（可清空）。
- 设计器新增「分组」按钮 → Modal：新建（名称 → 自动生成 g 前缀 key）、重命名、删除（删除后成员节点自动置为未分组）。

## 5. 涉及文件

### 后端（whale-workflow）

| 文件 | 变更 |
| --- | --- |
| `support/ProcessModelGroup.java` | 新建：`@Data`，字段 `key`/`name`，含类与字段 Javadoc |
| `support/ProcessModel.java` | 新增 `groups` 列表 + `group(String key)` 查询；类 Javadoc 补充分组 |
| `support/ProcessModelNode.java` | 新增 `groupKey` 字段（可空，Javadoc） |
| `support/ProcessModelParser.java` | 新增 `FIELD_GROUPS`/`MAX_GROUPS`；`normalized()` 读 groups；`readNode` 读 groupKey；分组 key 缺失/非法抛 `GROUP_KEY_INVALID` |
| `support/ValidationCode.java` | 新增 4 个分组校验码（带 Javadoc） |
| `support/ProcessModelValidator.java` | 新增 `checkGroups`（条数/key/重复/节点引用），`validate` 仅在 normalized 分支调用；`validateModel` 校验模型内分组引用 |
| `service/impl/ProcessModelServiceImpl.java` | `FIELD_GROUPS` 常量；`toJson` 非空时写 `groups` 数组；节点 `putIfPresent("groupKey")` |
| `support/bpmn/BpmnXmlWriter.java` | `<process>` 内写 `laneSet/lane/flowNodeRef`；`appendDiagram` 追加分组 Shape/Bounds（成员包围盒） |
| `support/bpmn/BpmnXmlParser.java` | `UNSUPPORTED` 移除 `laneSet`/`lane`，`IGNORED` 补 `flowNodeRef`；新增 `readLanes()` 回读分组与节点归属 |

### 前端（whale-workflow-designer）

| 文件 | 变更 |
| --- | --- |
| `src/api/workflow.ts` | 新增 `ProcessModelGroup`、`ProcessDefinitionVersion`、`DefinitionPublishRequest`、`DefinitionMigrationRequest`、`DefinitionMigrationResult`；`NormalizedDefinition.groups?`、`ProcessModelNode.groupKey?`；`workflowApi.definitionVersions/definitionPublish/definitionMigrate` |
| `src/pages/designer/model.ts` | `CanvasGroup`、`CanvasNode.groupKey`、`CanvasModel.groups`；`nextKey` 支持 g；`createGroup`；parse/serialize 支持 groups/groupKey 并剔除未知分组引用 |
| `src/pages/designer/Canvas.tsx` | 分组容器矩形 + 标题渲染（置于连线层之下） |
| `src/pages/designer/PropertyPanel.tsx` | 节点「所属分组」Select |
| `src/pages/designer/WorkflowDesigner.tsx` | `EMPTY_MODEL` 补 groups；分组管理 Modal；版本抽屉（列表/发布/迁移） |

### 文档

- `docs/devlog/2026-10-21.md`：追加 §十四（版本管理 UI）、§十五（分组/泳道）。
- `docs/plans/roadmap.md`：新增并勾选 Wave 5.42 / 5.43。
- `docs/plans/whale-workflow-backlog.md`：5b 置为已完成。

## 6. 验收标准

1. 后端 `mvn -o -f backend/pom.xml -pl :whale-workflow -am test checkstyle:check` 通过（JDK 21、禁用沙箱）。
2. 新增/更新单测覆盖：分组 JSON 解析、校验码、BPMN 导出→导入分组往返。
3. 前端 `pnpm --filter whale-workflow-designer build` 通过。
4. 浏览器实机验证：设计器「版本」抽屉可打开、版本列表展示与发布/迁移入口存在；画布分组容器渲染，新建分组后成员节点显示容器，取消归属后容器消失。
5. devlog/roadmap/backlog 已更新，提交并 `git push origin main`。

## 7. 风险与兼容

- `groups` 为可选字段，旧定义解析、校验、BPMN 导入导出行为不变（无分组时不输出 laneSet，不改变现有输出结构）。
- 现有 BPMN 单测若断言 `laneSet` 属于不支持元素，需同步改为「支持并回读」。
- BPMN lane id 直接使用 group key，避免额外映射；group key 字符集本身满足 XML ID 要求。
- 前端旧 `CanvasModel` 构造点需补 `groups: []`，TypeScript 编译会强制暴露遗漏。

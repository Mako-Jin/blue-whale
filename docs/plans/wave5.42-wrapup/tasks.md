# Wave 5.42 收尾任务拆分

> 执行顺序：T1 → T2 → T3 → T4 → T5 → T6（T2 与 T3 可并行，但同一工作区顺序落地更稳）。

## T1 前端契约层：版本 / 迁移 / 分组类型与 API

- 文件：`whale-frontend/platform-apps/whale-workflow-designer/src/api/workflow.ts`
- 变更：
  1. 新增 `ProcessModelGroup { key: string; name?: string | null }`。
  2. `NormalizedDefinition` 增 `groups?: ProcessModelGroup[] | null`；`ProcessModelNode` 增 `groupKey?: string | null`。
  3. 新增 `ProcessDefinitionVersion`（id/definitionId/version/definitionJson/publishStatus/createTime/updateTime，Long 一律 `Id`）、`DefinitionPublishRequest { version: number }`、`DefinitionMigrationRequest { targetVersion: number; instanceIds?: Id[] }`、`DefinitionMigrationResult`。
  4. `workflowApi` 增 `definitionVersions(id)`、`definitionPublish(id, data)`、`definitionMigrate(id, data)`。
- 验证：`pnpm --filter whale-workflow-designer build`（后续统一执行）。

## T2 后端分组模型 / 解析 / 校验 / 输出

- 新建 `whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/support/ProcessModelGroup.java`。
- `ProcessModel`：`groups` + `group(key)`。
- `ProcessModelNode`：`groupKey`。
- `ProcessModelParser`：读 `groups` 与节点 `groupKey`，分组 key 非法抛 `GROUP_KEY_INVALID`，上限 50。
- `ValidationCode`：新增 `GROUP_KEY_INVALID`、`GROUP_KEY_DUPLICATE`、`NODE_GROUP_UNKNOWN`、`TOO_MANY_GROUPS`。
- `ProcessModelValidator`：`checkGroups`（normalized 分支）与 `validateModel` 内分组引用校验。
- `ProcessModelServiceImpl.toJson`：写 `groups`（非空）与节点 `groupKey`。
- 验证：`mvn -o -f whale-backend/pom.xml -pl :whale-workflow -am test checkstyle:check`。

## T3 后端 BPMN 分组双向映射

- `BpmnXmlWriter`：
  - `write` 在节点输出后写 `<laneSet id="LaneSet_{processId}">`，每个分组一个 `<lane id="{key}" name="{name}">` + `<flowNodeRef>` 列表（只含有成员节点的分组）。
  - `appendDiagram` 为分组写 `BPMNShape bpmnElement="{groupKey}"` + `dc:Bounds`（成员节点包围盒，padding 24）。
- `BpmnXmlParser`：
  - `UNSUPPORTED` 移除 `laneSet`/`lane`；`IGNORED` 增 `flowNodeRef`。
  - 新增 `readLanes(process, model)`：lane id/name → group，flowNodeRef → node.groupKey（首个 lane 优先）。
- 单测：新增或扩展 BPMN 往返测试，断言分组与 groupKey 导出后可回读；同步修正原「laneSet 不支持」断言。
- 验证：同上 Maven 命令。

## T4 前端画布模型与分组交互

- `model.ts`：`CanvasGroup`、`CanvasNode.groupKey`、`CanvasModel.groups`；`nextKey('g')`；`createGroup`；`parseDefinition` 读取 groups/groupKey 并剔除未知分组；`toNormalized` 输出 groups/groupKey。
- `Canvas.tsx`：分组容器（矩形 + 标题，`pointerEvents="none"`，位于连线层之下）。
- `PropertyPanel.tsx`：节点「所属分组」Select（allowClear）。
- `WorkflowDesigner.tsx`：`EMPTY_MODEL` 补 groups；「分组」Modal（新建/重命名/删除，删除后成员置未分组）。
- 验证：前端构建 + 浏览器实机。

## T5 设计器版本管理抽屉

- `WorkflowDesigner.tsx`：
  - 顶栏「版本」按钮（`definitionId` 为空时禁用）。
  - Drawer：打开时 `definitionVersions`；Table 列版本号/状态/创建时间/操作（发布、迁移）。
  - 发布：确认 → `definitionPublish` → 刷新版本 + 重新加载定义。
  - 迁移 Modal：目标版本 InputNumber（默认选中版本）+ 实例 ID 文本域（逗号/换行分隔，按字符串提交）→ `definitionMigrate` → 结果 Alert（migrated/skipped/failed + failures）。
- 验证：浏览器实机打开抽屉、发布与迁移交互（无数据时提示空态）。

## T6 构建 / 验证 / 文档 / 提交

1. 停 8083 上运行中的 workflow 服务（避免 jar 占用），执行后端 `mvn -o -f whale-backend/pom.xml -pl :whale-workflow -am test checkstyle:check`（`dangerouslyDisableSandbox: true`）。
2. 前端 `pnpm --filter whale-workflow-designer build`。
3. 浏览器验证版本抽屉与分组渲染（8003 dev server）。
4. 更新 `docs/devlog/2026-10-21.md`（§十四/§十五）、`docs/plans/roadmap.md`（Wave 5.42/5.43）、`docs/plans/whale-workflow-backlog.md`（5b ✅）。
5. `git add` 指定文件 → commit → `git push origin main`。

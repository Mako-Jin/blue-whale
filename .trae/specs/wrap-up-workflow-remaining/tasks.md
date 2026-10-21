# Tasks

- [x] Task 1: 前端契约层：版本 / 迁移 / 分组类型与 API
  - [x] `src/api/workflow.ts` 新增 `ProcessModelGroup`、`groupKey`、`groups`、`ProcessDefinitionVersion`、`DefinitionPublishRequest`、`DefinitionMigrationRequest`、`DefinitionMigrationResult`
  - [x] `workflowApi.definitionVersions / definitionPublish / definitionMigrate`
- [x] Task 2: 后端分组模型 / 解析 / 校验 / 输出
  - [x] 新建 `ProcessModelGroup`，`ProcessModel.groups` + `group(key)`，`ProcessModelNode.groupKey`
  - [x] `ProcessModelParser` 读 groups/groupKey，上限 50
  - [x] `ValidationCode` 新增 4 个分组校验码
  - [x] `ProcessModelValidator` 分组校验；`ProcessModelServiceImpl.toJson` 输出 groups/groupKey
- [x] Task 3: 后端 BPMN 分组双向映射 + 单测
  - [x] `BpmnXmlWriter` 写 laneSet/lane/flowNodeRef 与分组 Bounds
  - [x] `BpmnXmlParser` 回读 laneSet/lane/flowNodeRef
  - [x] `ProcessModelGroupTest` 5 用例
- [x] Task 4: 前端画布模型与分组交互
  - [x] `model.ts`、`Canvas.tsx`、`PropertyPanel.tsx`
  - [x] `WorkflowDesigner.tsx` 分组状态 / 顶栏按钮 / 分组管理 Modal
- [x] Task 5: 设计器版本管理抽屉
  - [x] 版本 state / 加载 / 发布 / 迁移
  - [x] 顶栏「版本」按钮、Drawer、迁移 Modal 与结果展示
- [x] Task 6: 构建验证 / 浏览器复核 / 文档 / 提交推送
  - [x] 后端 `mvn -o -f backend/pom.xml -pl :whale-workflow -am test checkstyle:check`（147 测试 / 0 违规）
  - [x] 前端 `pnpm --filter whale-workflow-designer build`
  - [x] 浏览器验证 8003 版本抽屉与分组渲染
  - [x] 更新 `docs/devlog/2026-10-21.md`、`docs/plans/roadmap.md`、`docs/plans/whale-workflow-backlog.md`
  - [ ] commit + `git push origin main`

# Task Dependencies
- Task 5 依赖 Task 4 中的 `WorkflowDesigner.tsx` 改动（同文件顺序落地）
- Task 6 依赖 Task 4、Task 5 完成

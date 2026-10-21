# 工作流服务剩余任务收尾 Spec

## Why
工作流服务 W1–W6 已完成并推送，但仍有两项待办：定义版本管理前端 UI 缺失、backlog 5b 画布分组/泳道未实现。本轮尽量清空待办并完成构建、验证与提交收尾。

## What Changes
- 前端新增版本管理抽屉：版本列表（草稿/已发布）、发布、运行中实例迁移。
- 规范化流程 JSON 新增可选分组元数据 `groups:[{key,name}]` 与节点 `groupKey`（不持久化容器坐标，由成员节点包围盒派生）。
- 后端解析 / 校验 / 归一化输出 / BPMN 导出与导入支持分组（laneSet/lane/flowNodeRef 双向映射）。
- 前端画布渲染分组容器、节点归属编辑、分组增删改。
- 更新 devlog / roadmap / backlog 并提交推送 Gitee。

## Impact
- Affected specs: whale-workflow 定义模型、BPMN 转换、设计器画布
- Affected code:
  - 后端：`support/ProcessModel*.java`、`support/ValidationCode.java`、`support/ProcessModelValidator.java`、`service/impl/ProcessModelServiceImpl.java`、`support/bpmn/BpmnXml{Writer,Parser}.java`
  - 前端：`src/api/workflow.ts`、`src/pages/designer/{model.ts,Canvas.tsx,PropertyPanel.tsx,WorkflowDesigner.tsx}`

## ADDED Requirements
### Requirement: 分组/泳道元数据
系统 SHALL 支持可选 `groups` 数组与节点 `groupKey`，缺失时行为与旧定义完全一致。

#### Scenario: 旧定义零影响
- **WHEN** 定义 JSON 无 `groups`
- **THEN** 解析、校验、保存、BPMN 导出输出与改动前一致

#### Scenario: 无效分组被拦截
- **WHEN** 分组 key 缺失/非法、重复、超过 50 个，或节点引用不存在的分组
- **THEN** 返回 `GROUP_KEY_INVALID` / `GROUP_KEY_DUPLICATE` / `TOO_MANY_GROUPS` / `NODE_GROUP_UNKNOWN`

#### Scenario: BPMN 往返
- **WHEN** 含分组的定义导出 BPMN 后再导入
- **THEN** 分组 key/name 与节点归属保持一致

### Requirement: 定义版本管理 UI
系统 SHALL 在设计器提供「版本」抽屉，展示版本快照并支持发布与实例迁移。

#### Scenario: 发布版本
- **WHEN** 用户在版本列表点击发布并确认
- **THEN** 调用发布接口，刷新版本列表并重新加载定义内容

#### Scenario: 迁移运行中实例
- **WHEN** 用户输入目标版本与可选实例 ID 后提交
- **THEN** 展示 migrated/skipped/failed 摘要与失败明细

### Requirement: 画布分组容器
系统 SHALL 在画布中按成员节点包围盒渲染分组容器，并提供分组增删改与节点归属设置。

#### Scenario: 新建分组并归属节点
- **WHEN** 用户新建分组并在节点属性面板选择该分组
- **THEN** 画布出现覆盖该节点的虚线分组容器并显示分组名

#### Scenario: 删除分组
- **WHEN** 用户删除分组
- **THEN** 该分组所有成员的 `groupKey` 置空，容器消失

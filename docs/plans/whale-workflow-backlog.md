# whale-workflow 剩余任务清单（Wave 5.32 §六 削减项续作）

> 来源：[wave5.32-workflow-designer/spec.md](wave5.32-workflow-designer/spec.md) §六「明确削减」+ Wave 5.35/5.36 后的实际缺口。
> 执行顺序按「改动面小、依赖少 → 大改」排列，逐项闭环：文档 → 编码 → 单测 → 构建 → 冒烟 → devlog → 提交推送。

| # | 任务 | 状态 | 说明 |
|---|---|---|---|
| 1 | 加签（ADD_SIGN） | ✅ 已结案（Wave 5.36） | `POST /workflow/task/{id}/add-sign`；同节点追加待办全部完成后才推进；ANY/RATIO 会签拒绝；权限种子 78；见 [devlog 2026-10-21 §八](../devlog/2026-10-21.md) |
| 2 | 设计器多分支真实选路模拟 | ✅ 已结案（Wave 5.37） | `POST /workflow/definition/simulate`；`StateMachine.pickTransition` + `ConditionGuard` 真实求值，返回逐步路径（入边 key/condition/isDefault），不建实例；前端「模拟」弹窗按路径定位节点；见 [devlog 2026-10-21 §九](../devlog/2026-10-21.md) |
| 3 | 定义版本快照 / 发布 / 实例迁移 | ✅ 已结案（Wave 5.38） | 新表 `process_definition_version`（快照 + 发布态）；定义 JSON 变更版本自增写草稿，`publish` 切换新实例版本，`migrate` 按节点键迁移运行中实例；发起优先已发布快照，审批/回退按实例锁定版本解析；见 [devlog 2026-10-21 §十](../devlog/2026-10-21.md) |
| 4 | BPMN-DI 坐标回写 / 图形保真 | ✅ 已结案（Wave 5.39） | 导出节点 `x/y` 非空时追加 `bpmndi:BPMNDiagram` + `BPMNShape/dc:Bounds`；导入按 `bpmnElement` 回读 `x/y`（缺失/未知/非数字静默跳过）；坐标经 JSON 全链路往返；见 [devlog 2026-10-21 §十一](../devlog/2026-10-21.md) |
| 5 | 画布高级能力：撤销重做 / 缩放平移 / 对齐线 + 吸附 | ✅ 已结案（Wave 5.40） | `useReducer` 历史栈（`past/future`，上限 50）+ `commit()` 统一入口 + 顶栏撤销/重做 + `Ctrl+Z` / `Ctrl+Y` / `Ctrl+Shift+Z`（输入框内不劫持）；缩放 0.4x~2x（工具栏 / `Ctrl+滚轮`，指针定位）与空格/中键平移；6px 阈值十向对齐 + 虚线参考线，命中轴对齐、未命中轴 10px 网格吸附；见 [devlog 2026-10-21 §十二](../devlog/2026-10-21.md) |
| 5b | 画布分组 / 泳道容器 | ✅ 已结案（Wave 5.43） | 规范化 JSON 可选 `groups:[{key,name}]` + 节点 `groupKey`（容器坐标不持久化，按成员包围盒派生）；后端解析/4 校验码/`toJson` 输出 + BPMN `laneSet/lane/flowNodeRef` 与 DI Bounds 双向；前端画布虚线容器、属性面板「所属分组」、分组新建/重命名/删除；workflow **147/147** 单测 + Checkstyle 0 违规 + 浏览器容器渲染/删除验证；见 [devlog 2026-10-21 §十五](../devlog/2026-10-21.md) |
| 6 | 人工复核清单收口 | ✅ 已结案（Wave 5.41） | 转办弹窗：空表单必填拦截、原生 input 事件提交 `PUT /task/{id}/transfer` 200 + DB `assignee/owner_id` 核对通过（复核后 SQL 还原）；dnd-kit 真实指针拖拽 6 条人工清单归档在 [devlog 2026-10-21 §十三](../devlog/2026-10-21.md)，无代码变更 |

## 约束

- 保持 `whale-workflow` 单模块扩展，不新建服务；legacy `{"nodes":[…]}` 语义零回归。
- 前端 `Id = string` 全链路禁 `Number(id)`；`api/workflow.ts` 为冻结契约层，改动需同步契约与调用方。
- 每项完成后在 `docs/devlog/YYYY-MM-DD.md` 追加记录，并在 [roadmap.md](roadmap.md) 勾选。

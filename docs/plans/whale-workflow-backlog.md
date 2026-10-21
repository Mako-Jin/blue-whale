# whale-workflow 剩余任务清单（Wave 5.32 §六 削减项续作）

> 来源：[wave5.32-workflow-designer/spec.md](wave5.32-workflow-designer/spec.md) §六「明确削减」+ Wave 5.35/5.36 后的实际缺口。
> 执行顺序按「改动面小、依赖少 → 大改」排列，逐项闭环：文档 → 编码 → 单测 → 构建 → 冒烟 → devlog → 提交推送。

| # | 任务 | 状态 | 说明 |
|---|---|---|---|
| 1 | 加签（ADD_SIGN） | ✅ 已结案（Wave 5.36） | `POST /workflow/task/{id}/add-sign`；同节点追加待办全部完成后才推进；ANY/RATIO 会签拒绝；权限种子 78；见 [devlog 2026-10-21 §八](../devlog/2026-10-21.md) |
| 2 | 设计器多分支真实选路模拟 | ⏳ 待开始 | 画布/校验侧按 `condition` 做静态选路预演（复用 `ConditionGuard` 语法校验，不启动实例） |
| 3 | 定义版本快照 / 发布 / 实例迁移 | ⏳ 待开始 | `process_definition` 版本列 + 发布态 + 实例锁定版本；DDL 变更 |
| 4 | BPMN-DI 坐标回写 / 图形保真 | ⏳ 待开始 | 导出 BPMN 时写 `bpmndi:BPMNDiagram`，导入时读 `x/y`；XML 双向往返 |
| 5 | 画布高级能力 | ⏳ 待开始 | 撤销重做 / 缩放平移 / 对齐线 / 吸附 / 分组 / 泳道（纯前端大改） |
| 6 | 人工复核清单收口 | ⏳ 待开始 | 转办弹窗交互、dnd-kit 真实指针拖拽手势（自动化不可复现） |

## 约束

- 保持 `whale-workflow` 单模块扩展，不新建服务；legacy `{"nodes":[…]}` 语义零回归。
- 前端 `Id = string` 全链路禁 `Number(id)`；`api/workflow.ts` 为冻结契约层，改动需同步契约与调用方。
- 每项完成后在 `docs/devlog/YYYY-MM-DD.md` 追加记录，并在 [roadmap.md](roadmap.md) 勾选。

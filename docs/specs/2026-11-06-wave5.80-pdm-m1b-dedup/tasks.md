# Wave 5.80 PDM M1B-4 任务清单

> 状态：全部完成（2026-11-06）
> 规格：[spec.md](spec.md)

| 编号 | 任务 | 交付物 | 状态 |
|---|---|---|---|
| M1B-4-1 | 合并字段迁移 | `V13__part_merge.sql`：`pdm_product.merged_to` + 索引 | 完成 |
| M1B-4-2 | 去重候选服务 | `PdmPartDedupService`：归一化分组、组数/扫描上限 | 完成 |
| M1B-4-3 | 人工合并服务 | 校验、草稿行改写、冲突用量累加、跳过已发布、停用源件 | 完成 |
| M1B-4-4 | 接口与审计 | `GET /pdm/part/dedup/candidates`、`POST /pdm/part/merge` | 完成 |
| M1B-4-5 | 权限与菜单 | `migration-5.80-pdm-part-merge.sql`：941/942、菜单 187 | 完成 |
| M1B-4-6 | 后端单测 | 分组、改写、跳过、累加、拒绝、迁移校验（8 例） | 完成 |
| M1B-4-7 | 前端审阅页 | `pages/part/dedup.tsx`、API、路由与菜单 | 完成 |
| M1B-4-8 | 文档同步 | 全景计划 M1B-4/M1B-6、评审、devlog | 完成 |

## 未完成（明确留待）

- 自动合并策略、相似度匹配：仅人工确认。
- 已发布 BOM 引用改写：按决策保持不可变，历史以 `merged_to` 追溯。
- ERP 只读物料/库存：M1B-5 依赖 ERP 契约，本轮不涉及。

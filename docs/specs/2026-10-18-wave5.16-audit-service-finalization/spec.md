# Wave 5.16 · 审计服务收尾（originals 归档 + final 整合版）

> 状态：⏳ 进行中 ｜ 对应 [tasks.md](./tasks.md) / [checklist.md](./checklist.md)

## 1. 需求项

| 编号 | 需求 | 来源 | 验收口径 |
|---|---|---|---|
| F1 | 将**审计服务相关原始文档**以副本形式归置于 `docs/whale-audit/originals/`（**原位置文件不删不动**） | 用户指令（对照 `docs/whale-auth/` 收尾模式） | 副本齐备、路径可溯源、原位文件不变 |
| F2 | 输出**审计服务整合最终版** `docs/whale-audit/final/README.md`（以原位最新文件为准） | 同上 | 能力矩阵/表结构/接口清单/配置/闭环与残余/原文索引/版本记录七节齐备 |
| F3 | 留痕：检查报告 + devlog 四节 + 台账销账（register/next-steps/design 指向最终版） | REQ-DOC-002 / REQ-QA-002 | 四类信息齐备 |
| F4 | 自动 commit + push（Gitee origin/main） | 用户长期约定 | 工作区干净且 `main == origin/main` |

## 2. 归档范围（originals 副本清单）

| 分类 | 源位置 | 副本目标 | 文件 |
|---|---|---|---|
| design | `docs/design/audit-log.md` | `originals/design/audit-log.md` | 审计设计总纲 |
| 方案原文 | `.trae/documents/审计日志服务多通道完善方案.md` | `originals/scheme/审计日志服务多通道完善方案.md` | 多通道方案原文 |
| specs | `docs/specs/2026-10-17-wave5.1b-audit-log/` | `originals/specs/...` | spec/tasks/checklist |
| specs | `docs/specs/2026-10-18-wave5.10-multichannel-log/` | `originals/specs/...` | spec/tasks/checklist |
| specs | `docs/specs/2026-10-18-wave5.12-audit-backend-polish/` | `originals/specs/...` | spec/tasks/checklist |
| specs | `docs/specs/2026-10-18-wave5.14-whale-log-sdk/` | `originals/specs/...` | spec/tasks/checklist（通道③④） |
| specs | `docs/specs/2026-10-18-wave5.15-audit-channel-query/` | `originals/specs/...` | spec/tasks/checklist（通道①②） |
| reviews | `docs/reviews/2026-10-17-wave5.1b-audit-log.md` 等 6 份 | `originals/reviews/...` | 见暂存清单 |
| devlog | `docs/devlog/2026-10-16.md`、`2026-10-17.md`、`2026-10-18.md` | `originals/devlog/...` | 全量 | 

> 说明：`docs/design/audit-log.md`、`docs/specs/**`、`docs/reviews/**`、`docs/devlog/**` 的**原位文件不删不动**，副本仅供归档与溯源。

## 3. 契约（final 整合版章节）

`docs/whale-audit/final/README.md` 章节：① 需求与范围；② 能力矩阵；③ 接口与权限点清单；④ 关键配置项；⑤ 表结构；⑥ 已闭环项与残余/阻塞项；⑦ 原文索引；⑧ 版本与变更记录。

## 4. 落点

- 新增 `docs/whale-audit/originals/**`（副本，不改内容）
- 新增 `docs/whale-audit/final/README.md`（整合最终版）
- 新增 `docs/specs/2026-10-18-wave5.16-audit-service-finalization/**`
- 新增 `docs/reviews/2026-10-18-wave5.16-audit-service-finalization.md`
- 修改 `docs/devlog/2026-10-18.md`、`docs/requirements/register.md`、`docs/plans/next-steps.md`

## 5. 范围外（不做）

- 不改任何 `whale-backend/**` 源码（AUTH-08/09/10 仍受 whale-file/whale-job/RabbitMQ 未就绪阻塞）
- 不删改原位置任何文件
- 不重构既有设计文档内容（`docs/design/audit-log.md` 仅保留原位）

## 6. 偏差与残余

- 运行时端到端验证仍受阻（本机无 Docker，中间件未就绪）
- AUTH-08 导出 / AUTH-09 归档 / AUTH-10 MQ 汇聚仍为阻塞项（external dependency）

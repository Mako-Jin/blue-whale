# Wave 5.80 PDM M4：任务清单

> 状态：全部完成（2026-11-05）
> 规格：[spec.md](spec.md)

| 编号 | 任务 | 状态 | 交付 |
|---|---|---|---|
| M4-T1 | DDL `V11__collab_preview.sql`（3 表 + 唯一键 + 索引） | 完成 | 迁移脚本 |
| M4-T2 | 交付物实体/Mapper/状态常量 | 完成 | `collab` 包 |
| M4-T3 | 交付物服务：已发布版本校验、唯一性、分页、解除 | 完成 | `PdmClbDeliverableService(Impl)` |
| M4-T4 | 评审实体/Mapper/常量 | 完成 | `collab` 包 |
| M4-T5 | 评审服务：草稿、关闭、结论校验、附件校验 | 完成 | `PdmClbReviewService(Impl)` |
| M4-T6 | 预览任务实体/Mapper/格式判定规则 | 完成 | `PdmDocPreviewRules` |
| M4-T7 | 预览服务：幂等创建、重试、内容读取（复用 ACL） | 完成 | `PdmDocPreviewService(Impl)` |
| M4-T8 | 三个控制器 + 审计 + 权限注解 | 完成 | `collab/controller` |
| M4-T9 | 检索增强：关键字覆盖描述与设计师 | 完成 | `PdmDocDocumentServiceImpl` |
| M4-T10 | 权限 980–989 / 菜单 198–200 / 角色 21107 幂等迁移 | 完成 | `migration-5.80-pdm-collab.sql` |
| M4-T11 | 前端 API 与三个页面（交付物/评审/预览）+ 路由菜单 | 完成 | `api/collab.ts`、`pages/collab/*` |
| M4-T12 | 单元测试（交付物 5、评审 4、规则 6、预览 5） | 完成 | 181 tests / 0 failures |
| M4-T13 | 计划、规格、检查报告、服务清单、登记册、devlog | 完成 | 文档 |

## 未完成（有意留待）

- Office→PDF 的 LibreOffice 容器与 STEP/IGES→glTF 的 FreeCAD/OCCT 转换器（需部署外部服务）。
- 3D 渲染引擎查看器（不依赖冻结的 `whale-common-three`，留待专用包）。
- MySQL ngram 全文索引与图纸标题栏 OCR（按 D-PDM-3 决策另行启用）。

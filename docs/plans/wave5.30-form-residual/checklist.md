# Wave 5.30 验收清单（表单剩余任务 · 问题修复 · 收尾）

> 对应规格：[spec.md](spec.md)；任务：[tasks.md](tasks.md)。
> 勾选规则：仅当「代码可读证 + 门禁通过 + 实机可复现」三者齐备时才勾选。

## R1 契约与文档

- [x] 冻结契约（`describeColumns` / ALTER 生成方法 / 发布语义 / 列合并语义 / 导出接口）在 spec.md §三逐条落地
- [x] 决策记录（含「编辑/删除只改元数据，发布时才 diff + ALTER」）在 spec.md §六可追溯

## R2 列变更在线迁移

- [x] 编辑/删除列仅改 `form_column_meta` 元数据，**不**触发任何 DDL
- [x] 「发布 / 确认完成」时对比列元数据与 `information_schema` 现有列，产出 ADD / MODIFY / DROP
- [x] 物理表公共列（id/tenant_id/definition_id/definition_version/biz_key/submitter/status/create_time/update_time/deleted）永不参与 ADD/MODIFY/DROP
- [x] 类型/长度变更（如 `number → text`）发布后物理列类型随之变更
- [x] 待删列若被本表定义下表单视图引用 → 前置拦截并返回 `PARAM_ERROR`（带表单名）
- [x] **P1 修复**：表定义编辑后既有列主键保持不变（`mergeColumns` 就地合并），既有表单视图仍可保存/编译

## R3 表单数据导出

- [x] `GET /form/data/export?definitionCode=<code>&format=csv` 返回可打开的 CSV（UTF-8 BOM）
- [x] `GET /form/data/export?definitionCode=<code>&format=xlsx` 返回可打开的最小 XLSX（OpenXML）
- [x] 表头 = 公共列（ID/表单编码/版本/业务键/提交人/提交时间/状态）+ `schema_json.fields[]` 的字段标签
- [x] 行数据取自 `form_data` 主表并解析 `dataJson`，单次查询无 N+1，上限 5000 行
- [x] 缺少 `definitionCode` → `PARAM_ERROR`
- [x] **不新增 Maven 依赖**（纯 JDK 实现；本地仓无 EasyExcel）

## R4 设计器撤销 / 重做

- [x] 历史栈覆盖拖入、排序、删除、属性补丁四类变更
- [x] 撤销/重做按钮状态随栈变化正确禁用/启用
- [x] `Ctrl+Z` / `Ctrl+Y` / `Ctrl+Shift+Z` 生效
- [x] 在输入框（input/textarea/contenteditable）内不劫持快捷键
- [x] 切换表定义（清空画布）亦入历史

## D 并行门禁

- [x] 后端全量构建通过：`mvn -o -f whale-backend/pom.xml clean install`（Checkstyle `severity=error` + 单测）
- [x] 前端构建通过：`pnpm -C frontend --filter whale-form-designer build`
- [x] 新增/调整单测全绿：ALTER 语句生成、重复发布=ALTER、DROP 护栏、列编辑保留主键

## E 实机 E2E

- [x] E1 编辑表定义（增列 / 删列）后 `SHOW COLUMNS` 证明物理表未变
- [x] E2 点「发布」后物理表新增列
- [x] E3 改类型后发布，物理列类型随之变更
- [x] E4 导出 CSV 与 XLSX 各一次，均可打开且表头/行数正确
- [x] E5 设计器撤销/重做（拖入 2 字段 → `Ctrl+Z`×2 清空 → `Ctrl+Y`×2 复原）
- [x] ~~E6 真实指针拖拽入画布复验（截图留档）~~ ✅ 2026-10-28（`form-designer-drag.spec.ts` 真实鼠标 `dragTo` 入画布 + API 核验 + 截图人工复核；2026-11-11 扫描复核确认，见 [扫描](../reviews/2026-11-11-workspace-scan.md)）
- [x] E7 负向：导出缺 `definitionCode` → `PARAM_ERROR`；DROP 被引用列 → 护栏 `PARAM_ERROR`

## G 收尾

- [x] 检查报告：`docs/reviews/2026-10-20-wave5.30-form-residual.md`
- [x] devlog：`docs/devlog/2026-10-20.md` 追加 Wave 5.30 段落
- [x] 台账勾选：`roadmap.md` §2.5 追加 Wave 5.30 行；`task-breakdown.md` 追加本分组
- [x] 已 commit 并 push `origin`（Gitee `https://gitee.com/StrongToStop/blue-whale.git`）

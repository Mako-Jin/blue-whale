# Wave 5.30 任务分解（表单剩余任务 · 问题修复 · 收尾）

> 规格：[spec.md](spec.md)。执行原则：**先落文档 → 冻结契约 → 编码 → 模块自检 → 全量回归 → 检查报告 → 台账勾选 → commit & push origin(Gitee)**。
> 并行策略：R2（后端迁移）与 R4/R5（前端）无文件交集，可并行；R3（后端导出）只碰 `FormDataService/Controller`，与 R2 文件不重叠，可并行。

---

## Wave 5.30-a：契约冻结与并行实施（3 条并行线）

### 线 A：后端 · 列变更在线迁移（R2）

- [x] **A1** `FormDataEngine` + `JdbcFormDataEngine`：新增 `describeColumns(table)`（`information_schema.COLUMNS`，表名走白名单正则）
- [x] **A2** `FormTableDdlGenerator`：新增 `columnDef / alterAdd / alterModify / alterDrop / normalizeType / reservedColumns`
- [x] **A3** `FormPublishServiceImpl`：抽 `ensurePublished(table)`（无登记 → CREATE；有登记 → diff + ALTER），`publishTableDefinition` 与 `publishMetadata` 均改走该方法
- [x] **A4** DROP 前护栏——被本表定义下表单视图启用字段引用的列禁止删除（`PARAM_ERROR` 带表单名）；实现落在 `FormTableDefinitionServiceImpl.assertColumnNotReferenced`（元数据合并阶段前置拦截，先于发布期 DROP）
- [x] **A5'** 验证：`FormTableDefinitionServiceImpl.update()` 走 `mergeColumns`（按 `columnCode` 就地更新，保留列主键；缺失列逻辑删除）
- [x] **A6** 单测：`FormTableDdlGeneratorTest`（ALTER 语句）、`FormPublishServiceImplTest`（元数据模型重复发布=ALTER / DROP 护栏）、`FormTableDefinitionServiceImplTest`（保留主键）
  - 追加修复：**停用列（`status=0`）仍在元数据中时不下发 DROP**（`applyAlter` 增 `metadataCodes` 判定），避免「停用列」被误删造成物理列数据丢失；`FormPublishServiceImplTest#shouldNotDropDisabledColumn` 覆盖

### 线 B：后端 · 数据导出（R3）

- [x] **B1** `support/FormDataExportSupport`（新）：纯 JDK `csv(headers, rows)` / `xlsx(sheet, headers, rows)`
- [x] **B2** `vo/FormDataExportVO`（新）：`content / filename / contentType`
- [x] **B3** `FormDataService` + `FormDataServiceImpl`：`export(request, tenantId, format)`（解析 `definitionCode` → 表单定义 `fields[]` → 主表分页 ≤5000 → 动态列取 `dataJson`）
- [x] **B4** `FormDataController`：`GET /form/data/export` → `ResponseEntity<byte[]>` + `Content-Disposition`

### 线 C：前端（R4/R5）

- [x] **C1** `api/http.ts` `downloadBlob`（原始字节流 + `Content-Disposition` 文件名解析 + 401 续期重放一次）+ `api/form.ts` `ExportFormat`、`formDataApi.export`
- [x] **C2** `FormDataList.tsx`：+「导出」Dropdown.Button（CSV / Excel）+ blob 下载 + 失败提示 + 无 `definitionCode` 时禁用并提示
- [x] **C3** `TableDefinitionList.tsx`：列编辑弹窗 + 迁移语义 `Alert`；发布成功文案改为「发布成功，物理表结构已同步」
- [x] **C4** `FormDesigner.tsx`：+历史栈（`past/future` ref + `historyVersion`）、`commit()` 统一入口、撤销/重做按钮、`Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z`

### 线 D：并行门禁（可与其他线同时启动）

- [x] **D1** 后端全量回归：`mvn -o -f whale-backend/pom.xml clean install`（28/28 模块 BUILD SUCCESS）
- [x] **D2** 前端构建：`pnpm -C frontend --filter whale-form-designer build`（1461 modules，通过）

---

## Wave 5.30-b：实机 E2E

- [x] **E1** 编辑表定义（增 `remark2` / 删 `remark`）→ 物理表 **未变**（`SHOW COLUMNS`）
- [x] **E2** 点「发布」→ 物理表出现 `remark2`（`remark` 被引用则触发护栏，属预期负向）
- [x] **E3** 改类型（`number → text`）→ 发布 → `SHOW COLUMNS` 变 `varchar(255)`
- [x] **E4** 导出 CSV / XLSX 各一次，校验可打开、表头与行数正确
- [x] **E5** 设计器撤销/重做：拖入 2 字段 → `Ctrl+Z`×2 清空 → `Ctrl+Y`×2 复原
- [ ] **E6** 拖拽复验：真实指针拖入画布（`browser_use` 子代理 + 截图）（自动化不可复现 → 代码复核通过，需人工复核；详见 reports）
- [x] **E7** 负向：导出缺 `definitionCode` → `PARAM_ERROR`；DROP 被引用列 → 护栏 `PARAM_ERROR`

---

## Wave 5.30-c：收尾（R7）

- [x] **G1** 检查报告：`docs/reviews/2026-10-20-wave5.30-form-residual.md`
- [x] **G2** devlog：`docs/devlog/2026-10-20.md` 追加 Wave 5.30 段落
- [x] **G3** 台账勾选：`roadmap.md` §2.5 追加 Wave 5.30 行；`task-breakdown.md` 追加本文件分组
- [x] **G4** commit & push `origin`（Gitee `https://gitee.com/StrongToStop/blue-whale.git`）

---

## 验收标准

| 项 | 标准 |
|---|---|
| R2 | 编辑列不动物理表；发布执行 ADD/MODIFY/DROP；被引用列 DROP 被拦截；列主键在编辑后保持不变（既有表单视图仍可保存） |
| R3 | `GET /form/data/export?definitionCode=<code>&format=csv|xlsx` 返回可打开文件，表头 = 固定列 + 数据字段 |
| R4 | 撤销/重做覆盖拖入/排序/删除/属性补丁；快捷键在输入框内不劫持 |
| 收尾 | 检查报告 + devlog + 台账勾选 + origin(Gitee) 推送成功 |

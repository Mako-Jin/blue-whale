# Wave 5.30 规格（表单剩余任务 · 问题修复 · 收尾）

> 用户指令（2026-10-20）：「完成表单的剩余任务，修复现有问题，然后收尾」。
> 经 AskUserQuestion 冻结本轮范围（3 项，已选定）：
> 1. **设计器撤销/重做 + 拖拽手势复验**（前端 `@dnd-kit` 设计器加历史栈；用真实指针复核拖拽入画布）
> 2. **列删除/改类型在线迁移**
> 3. **表单数据导出（CSV / Excel）**
> 未选：表单↔工作流联动。Wave 5.29 spec §七 决策 10 中「不做」的三项，本轮补齐。
>
> **用户对「列变更」的关键澄清（必须遵守，原话）**：「针对列删除，之前不是让你把列的信息表存到数据表里面么，是发布或者确认完成之后才实际修改真实表结构，进行 alter 的。」
> → **编辑/删除列只改元数据（`form_column_meta`），不动物理表；只有「发布」时才对比 diff 并对物理表执行 `ALTER`。禁止做成「编辑即迁移」。**
>
> 关联台账：[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)。
> 前置规格：[wave5.29-form-designer/spec.md](../wave5.29-form-designer/spec.md)（已交付并推送，commit `5d8487e`）。

---

## 一、目标与范围

补齐 Wave 5.29 遗留的三项能力，并修复实现过程中暴露的两个真实缺陷。

**本轮做**：

- **R2 后端 · 列变更在线迁移**：表定义发布改为「存在即 diff + ALTER」语义（ADD / MODIFY / DROP COLUMN），取代原「同版本重复发布报错」。
- **R2 后端 · 修复 P1**：`FormTableDefinitionServiceImpl.update` 原「整体逻辑删除 + 重建全部列」会生成新的列主键，导致既有表单视图 `form_field_config.column_meta_id` 全部悬空（`FormMetadataCompiler.compile` 会抛「字段配置关联的列不存在」）。改为**按 `columnCode` 就地合并**，保留列主键。
- **R3 后端 + 前端 · 数据导出**：新增 `GET /form/data/export`（`format=csv|xlsx`），前端数据列表页加「导出」下拉。
- **R4 前端 · 设计器撤销/重做**：历史栈 + 工具栏按钮 + `Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z` 快捷键。
- **R6 拖拽手势复验**：用真实指针事件（Playwright `Input.dispatchMouseEvent`）复核调色板 → 画布拖拽。
- **R7 收尾**：检查报告 + devlog + 台账勾选 + commit & push origin(Gitee)。

**本轮不做**：表单↔工作流联动；物理表数据回填（ALTER ADD 新列一律 `DEFAULT NULL`）；列重命名（`columnCode` 视作物理列名，改名 == DROP + ADD，本轮不提供）。

---

## 二、现状分析（已核验源码）

### 2.1 后端 whale-form

| 文件 | 现状 | 问题 |
|---|---|---|
| [FormTableDdlGenerator.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/support/FormTableDdlGenerator.java) | 只有 `generate` / `generateFromColumns`（`CREATE TABLE IF NOT EXISTS`） | 缺 `ALTER` 语句生成 |
| [FormPublishServiceImpl.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/service/impl/FormPublishServiceImpl.java#L199-L210) | `assertTableVersionNotPublished()` 命中同版本登记即抛错 | 无法重复发布 → 无法迁移 |
| [FormTableDefinitionServiceImpl.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/service/impl/FormTableDefinitionServiceImpl.java#L147-L153) | `replaceColumns()` = `delete(wrapper)`（`@TableLogic` 逻辑删）+ 全量 `insert` → **列主键全变** | 既有表单视图引用悬空（P1） |
| [FormDataEngine.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/support/FormDataEngine.java) | `executeDdl / insert / selectFields / logicDelete` | 缺「描述物理表现有列」能力，无法做 diff |
| [FormDataServiceImpl.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/service/impl/FormDataServiceImpl.java#L120-L133) | `page()` 只读主表 `form_data`（`fields` 为 `null`） | 缺导出 |

**关键事实**：

- `BaseEntity` 含 `@TableLogic Integer deleted` → `formColumnMetaMapper.delete(wrapper)` 是**逻辑删除**；重新 `insert` 必然产生新雪花 ID。
- `form_data` 主表已双写 `dataJson`（提交时的键值 JSON 快照），导出可**直接解析 `dataJson`**，无需 N+1 查物理表。
- `whale-form` 未引入 EasyExcel，本地仓 `D:\repository\maven\com\alibaba\easyexcel` 不存在 → 离线构建会失败，**不新增 Maven 依赖**。
- 物理表公共列（`id/tenant_id/definition_id/definition_version/biz_key/submitter/status/create_time/update_time/deleted`）与 `FormSchemaParser.RESERVED_KEYS` 一致，**永不允许 DROP/MODIFY**。

### 2.2 前端 whale-form-designer

- [FormDesigner.tsx](file:///d:/BlueWhale/BlueWhale/frontend/platform-apps/whale-form-designer/src/pages/designer/FormDesigner.tsx)：`fields` 为唯一画布状态，共 5 处 `setFields`（加载 / 拖入 / 排序 / 删除 / 属性补丁 / 切表清理）→ 无历史栈。
- [FormDataList.tsx](file:///d:/BlueWhale/BlueWhale/frontend/platform-apps/whale-form-designer/src/pages/data/FormDataList.tsx)：无导出入口。
- [http.ts](file:///d:/BlueWhale/BlueWhale/frontend/platform-apps/whale-form-designer/src/api/http.ts)：仅解析 JSON 响应，**无 blob 下载能力**。

---

## 三、冻结契约（本轮新增/变更）

### 3.1 后端：`FormDataEngine` 新增方法

```java
/** 描述物理表现有列：列名 → 归一化 MySQL 列类型（小写无空格）；表不存在返回空 Map */
Map<String, String> describeColumns(String table);
```

- 实现：`SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?`。
- 表名仍走 `assertTable()` 白名单正则（`^form_data_[a-z0-9_]+_v[0-9]+$`）。

### 3.2 后端：`FormTableDdlGenerator` 新增方法

```java
public static String columnDef(FormColumnMeta column);                    // "`code` TYPE DEFAULT NULL COMMENT '名称'"
public static String alterAdd(String table, FormColumnMeta column);       // ALTER TABLE `t` ADD COLUMN `c` ...
public static String alterModify(String table, FormColumnMeta column);    // ALTER TABLE `t` MODIFY COLUMN `c` ...
public static String alterDrop(String table, String columnCode);          // ALTER TABLE `t` DROP COLUMN `c`
public static String normalizeType(String mysqlType);                     // 小写、去空格、去反引号
public static Set<String> reservedColumns();                              // 公共列集合
```

### 3.3 后端：发布语义（冻结）

`FormPublishServiceImpl.publishTableDefinition(id)`：

1. `assertPublishableCode(tableCode)`；`columns = listPublishColumns(id, version)`（**非空**，否则 `PARAM_ERROR`）。
2. `table = form_data_{code}_v{version}`；`existing = describeColumns(table)`。
3. `existing` 为空 → `executeDdl(generateFromColumns(...))`（原逻辑）。
4. `existing` 非空 → **diff + ALTER**：
   - 目标列 ∉ existing → `alterAdd`
   - 目标列 ∈ existing 且 `normalizeType(mysqlType) != existing.get(code)` → `alterModify`
   - existing 中的动态列 ∉ 目标集合 → **DROP 前护栏**：若该 `columnCode` 被本表定义下任一表单视图的启用字段引用（`form_field_config.column_code → form_column_meta` 反查，按 `form_id ∈ 本表定义的表单`），抛 `PARAM_ERROR`：`列 X 正被表单视图「Y」引用，请先在设计器中移除该字段后再发布`；否则 `alterDrop`。
   - 公共列永不参与 diff。
5. 登记：`existing` 为空时 `insert`，否则 `updateById` 刷新 `fieldCount`。

`publishMetadata(definition)`（表单视图发布）改为调用同一私有方法 `ensurePublished(table)`，**同样执行 diff + ALTER**（用户口径「发布或者确认完成」两条入口都生效）。

### 3.4 后端：列元数据合并语义（冻结）

`FormTableDefinitionServiceImpl.replaceColumns` → 重命名 `mergeColumns`：

- 载入本表定义全部启用列（`tableDefinitionId = id`，`orderByAsc(sortNo)`），建 `Map<columnCode, FormColumnMeta>`。
- 逐 DTO：命中同 `columnCode` → `updateById`（**保留主键**）并回写 `tableVersion`；未命中 → `insert`。
- 载入但未出现在 DTO 中的列 → 逻辑删除（`deleteById`）。
- 校验与落库顺序不变（`assertColumnCode` / 类型白名单 / `mysqlType` 预检 / 编码去重）。

**语义**：编辑/删除只改元数据；物理表在发布时才变。

### 3.5 后端：导出接口契约

```
GET /form/data/export?definitionCode=<必填>&bizKey=&submitter=&format=csv|xlsx
Response: 200, Content-Type: text/csv;charset=UTF-8 | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
          Content-Disposition: attachment; filename="form-data-{definitionCode}-{yyyyMMddHHmmss}.csv|xlsx"
错误：definitionCode 缺失或查不到表单定义 → PARAM_ERROR（包成 Result JSON，HTTP 200）
```

- 列顺序 = `ID / 表单编码 / 版本 / 业务键 / 提交人 / 提交时间 / 状态` + 表单视图数据字段（`fields[]` 中 `columnCode != null`，按 `sortNo`），表头用 `fieldLabel`（空则 `columnCode`）。
- 行数据：`form_data` 主表分页取全（上限 `EXPORT_MAX_ROWS = 5000`，`orderByDesc(createTime)`），动态列值取 `JSON.parse(dataJson).get(columnCode)`，缺失输出空串。
- 权限：复用 `sys:form:data:list`（admin = `super_admin` 网关放行，不新增权限种子）。
- 纯 JDK 实现（UTF-8 BOM CSV；最小 XLSX：`[Content_Types].xml` + `_rels/.rels` + `xl/workbook.xml` + `xl/_rels/workbook.xml.rels` + `xl/worksheets/sheet1.xml`，单元格用 `t="inlineStr"`）。**不新增 Maven 依赖**。

### 3.6 前端契约

```ts
// api/form.ts
export type ExportFormat = 'csv' | 'xlsx';
export const formDataApi = {
  ...,
  export: (query: PageParams, format: ExportFormat) => downloadBlob('/form/data/export', { ...query, format }),
};
```

`downloadBlob` 放在 `api/form.ts` 内部（不在 `http.ts` 加通用能力）：`fetch` + `Authorization: Bearer`，`!res.ok` 时读 JSON 文案抛 `ApiError`，成功返回 `Blob`。

### 3.7 设计器历史栈契约

- `past: DesignerField[][]` / `future: DesignerField[][]`（`useRef`），上限 50；`historyVersion` state 驱动按钮禁用态。
- 入栈时机：拖入、画布内排序、删除、属性补丁、切换表定义清理。**加载/回填不入栈并清空两栈**。
- 属性补丁按 `patch:{uid}` 合并（同一字段连续修改只占一步）。
- 快捷键：`Ctrl+Z` 撤销、`Ctrl+Y` 或 `Ctrl+Shift+Z` 重做；`event.target` 为 `INPUT/TEXTAREA/SELECT/[contenteditable]` 时不拦截。

---

## 四、改动清单（文件级）

### 4.1 后端（whale-form）

| # | 文件 | 改动 |
|---|---|---|
| B1 | `support/FormDataEngine.java` | +`describeColumns(String)` |
| B2 | `support/impl/JdbcFormDataEngine.java` | 实现 `describeColumns`（`information_schema`） |
| B3 | `support/FormTableDdlGenerator.java` | +`columnDef/alterAdd/alterModify/alterDrop/normalizeType/reservedColumns` |
| B4 | `service/impl/FormPublishServiceImpl.java` | `ensurePublished(table)` 统一 CREATE/ALTER；DROP 前护栏；+`FormFieldConfigMapper` |
| B5 | `service/impl/FormTableDefinitionServiceImpl.java` | `replaceColumns` → `mergeColumns`（保主键） |
| B6 | `support/FormDataExportSupport.java`（新） | `csv(headers, rows)` / `xlsx(sheet, headers, rows)` → `byte[]` |
| B7 | `vo/FormDataExportVO.java`（新） | `content / filename / contentType` |
| B8 | `service/FormDataService.java` + `impl/FormDataServiceImpl.java` | +`FormDataExportVO export(FormDataQueryRequest, Long tenantId, String format)` |
| B9 | `controller/FormDataController.java` | +`GET /export` → `ResponseEntity<byte[]>` |
| B10 | `test/.../support/FormTableDdlGeneratorTest.java` | +ALTER 语句生成单测 |
| B11 | `test/.../service/impl/FormPublishServiceImplTest.java` | 调整「重复发布报错」用例 → 「重复发布 = ALTER」；+DROP 护栏用例 |
| B12 | `test/.../service/impl/FormTableDefinitionServiceImplTest.java` | +「编辑保留列主键」用例 |

**不做**：`FormMetadataCompiler` / `FormSchemaParser` / `schema_json` 结构 / 物理表公共列 / `init.sql`（本轮无 DDL 变更）。

### 4.2 前端（whale-form-designer）

| # | 文件 | 改动 |
|---|---|---|
| F1 | `src/api/form.ts` | +`ExportFormat`、`downloadBlob`、`formDataApi.export` |
| F2 | `src/pages/data/FormDataList.tsx` | +「导出」Dropdown（CSV / Excel）+ blob 下载 + 错误提示 |
| F3 | `src/pages/table/TableDefinitionList.tsx` | 列编辑弹窗 +`Alert`：「列增删改只更新元数据，点『发布』后才对物理表执行 ALTER」；发布成功文案含「已同步物理表结构」 |
| F4 | `src/pages/designer/FormDesigner.tsx` | +历史栈 + 撤销/重做按钮 + 快捷键；5 处 `setFields` 改走 `commit()` |

---

## 五、验证方式

1. **后端模块**：`mvn -o -f backend/pom.xml -pl whale-services/whale-form -am clean install`（Checkstyle `severity=error` + 单测）。
2. **后端全量**：`mvn -o -f backend/pom.xml clean install`。
3. **前端**：`pnpm -C frontend --filter whale-form-designer build`（tsc + vite）。
4. **实机 E2E**（8002 应用，凭据 admin/123456，实机服务已在跑）：
   - E1 编辑表定义：新增一列 `remark2` 并删除 `remark` → 保存 → **物理表未变**（`SHOW COLUMNS` 无 `remark2`）。
   - E2 点「发布」→ 物理表出现 `remark2`、`remark` 仍在（若被表单视图引用则被护栏拦截，属预期负向）。
   - E3 改类型 `amount number → text` → 发布 → `SHOW COLUMNS` 显示 `varchar(255)`。
   - E4 数据导出：CSV 与 XLSX 各下载一次，校验文件可打开 / 表头与行数正确。
   - E5 设计器撤销/重做：拖入 2 个字段 → `Ctrl+Z` 两次 → 画布清空 → `Ctrl+Y` 两次 → 复原。
   - E6 拖拽复验：真实指针 `mouse down/move/up` 从调色板拖入画布（`browser_use` 子代理 + 截图）。
5. **负向**：导出缺 `definitionCode` → `PARAM_ERROR`；发布时 DROP 被引用列 → 护栏 `PARAM_ERROR`。

---

## 六、决策记录（冻结）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 列变更时机 | **编辑/删除只改元数据；发布时才 diff + ALTER**（用户原话口径） |
| 2 | 发布幂等性 | 同版本再发布 = 结构同步（ALTER），不再报错 |
| 3 | 列主键稳定性 | 按 `columnCode` 就地合并，保留 `form_column_meta.id` |
| 4 | DROP 安全 | 被表单视图启用字段引用的列禁止 DROP，发布时护栏报错 |
| 5 | 导出依赖 | 不引入 EasyExcel / POI，纯 JDK 生成 CSV 与最小 XLSX |
| 6 | 导出数据源 | `form_data` 主表（含 `dataJson`），单次分页查询，无 N+1 |
| 7 | 导出权限 | 复用 `sys:form:data:list`，不新增权限种子 |
| 8 | 撤销/重做粒度 | 拖入/排序/删除/属性补丁/切表清理；属性补丁按字段合并 |
| 9 | 公共列 | 永不出现在 diff 的 ADD/MODIFY/DROP 目标中 |
| 10 | 不做 | 表单↔工作流联动、列重命名、历史数据回填、`schema_json` 结构变更 |

---

## 七、风险与对策

| 风险 | 对策 |
|---|---|
| ALTER MODIFY 遇存量数据不兼容 → MySQL 报错 | 事务内执行，DDL 失败整笔回滚（`@Transactional`，MySQL DDL 隐式提交，故改为**先校验后执行**：类型/长度已在 `FormDataType.mysqlType` 预检）；失败时原样透出 MySQL 文案 |
| `describeColumns` 表不存在返回空 Map | 走 `CREATE TABLE IF NOT EXISTS` 分支，天然兜底 |
| 极简 XLSX 在 Excel 打不开 | E4 实机下载核验；失败则降级为只提供 CSV |
| `replaceColumns` 行为变更致旧单测失败 | B12 同步更新断言 |
| 设计器历史栈与 `@dnd-kit` 拖拽叠加 | 拖拽结束才 `commit`（一次拖拽 = 一步） |

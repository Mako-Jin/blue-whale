# Wave 5.29 规格（拖拽式表单设计器 · 前后端贯通 · 冻结契约）

> 用户指令（2026-10-20）：「今天打算搭建拖拽式表单搭建，前端后端一起搭建，并贯通。拆分任务，然后逐步搭建。」
> 经 AskUserQuestion 冻结口径（详见 §七 决策记录）：**(a) 范围 = 设计器 + 渲染填报 + 数据列表完整闭环**｜**(b) 拖拽用 `@dnd-kit`**｜**(c) 独立新应用 `frontend/platform-apps/whale-form-designer`（非 qiankun 子应用，端口 8002，真实认证直连网关 8080）**｜**(d) 设计器可新建表定义也可选已有表定义**｜**(e) 画布布局粒度 = 栅格 12/24（后端加 `col_span`）**｜**(f) 支持纯布局字段（`columnMetaId=null` 的 title/divider/text-note）**。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 编码 → 模块自检（REQ-QA-002）→ 全量回归 → 检查报告 → 台账勾选 → 提交推送**。
> 前置：Wave 5.28 已交付三层元数据模型（表定义 / 表单视图 / 选项），后端 API 契约已就绪；本轮后端仅「最小必要补强」。
> 关联台账：[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)。
> 唯一权威计划：[.trae/documents/wave5.29-form-designer.md](../../../.trae/documents/wave5.29-form-designer.md)。

---

## 一、目标与范围

**端到端闭环**：`建表定义 → 发布物理表 → 拖拽设计表单视图 → 保存 → 发布 → 动态渲染填报 → 数据列表查看/删除`。

本轮**做**：

1. 后端 whale-form 最小补强：`form_field_config.col_span`（栅格宽度）+ 字段配置 VO 回填关联列信息（`columnCode/dataType/required/maxLength`），使设计器「一次请求回填画布」。
2. 新建前端独立应用 `whale-form-designer`：真实认证登录、表定义管理、表单视图列表、`@dnd-kit` 三栏拖拽设计器、动态渲染填报、数据列表。
3. 端到端贯通验证（实机 E2E 7 步 + 2 条负向）。

本轮**不做**（§七 决策 10）：表单↔工作流联动、列删除/改类型的在线物理表迁移、表单数据导出、设计器撤销/重做。

---

## 二、现状分析（Current State Analysis）

### 2.1 后端：契约已就绪（Wave 5.28 三层模型）

| 能力 | 接口 | 关键点 |
|---|---|---|
| 表定义 CRUD | `POST/PUT/DELETE/GET /form/table-definition[/{id}]`、`GET /form/table-definition/page` | body 内嵌 `columns[]`；`tableCode` 需匹配 `^[a-z][a-z0-9_]{0,39}$` |
| 发布表定义 | `POST /form/table-definition/{id}/publish` | 按列元数据建物理表 `form_data_{tableCode}_v{version}` + 登记 registry；**同版本重复发布报错** |
| 表单视图 CRUD | `POST /form/definition`、`PUT /form/definition/{id}`、`GET /form/definition/{id}`、`GET /form/definition/page`、`DELETE /form/definition/{id}` | 保存时由 `FormMetadataCompiler` 编译 `schema_json`；**至少一个数据字段**，否则 `PARAM_ERROR` |
| 发布表单视图 | `POST /form/definition/{id}/publish` | 元数据模型下**幂等**：确保所属表定义已发布（未发布则代建） |
| 提交数据 | `POST /form/data` | body `{definitionId, dataJson, bizKey}`；`dataJson` 为 JSON 文本且**键必须是 schema 内字段**，未知键 400；租户取 `X-Tenant-Id`（缺省 1） |
| 数据分页/详情/删除 | `GET /form/data/page`、`GET /form/data/{id}`、`DELETE /form/data/{id}` | 详情返回 `fields` Map；列表 `fields` 为 `null` |
| 权限 | `sys:form:*` / `sys:form:table:*` / `sys:form:data:*` | admin 用户角色 `super_admin`，网关注入后直接放行，**前端无需权限码适配** |

**关键机制**（已核验源码）：

- 提交数据经 `FormDataServiceImpl.submit` → `requireRegistry(tableCode, table.getVersion())` → **必须先发布表定义**，否则报 `表单未发布或版本已变更，请先发布`。
- `schema_json` 是发布快照，**纯布局字段（`columnMetaId == null`）被跳过**，不进快照、不建物理列；`FormSchemaParser` 用 `JsonNode.path()` 取值，**容忍多余键**。
- `FormFieldConfigVO` 目前**不含 `columnCode` / `dataType`**，`toFieldVo` 用 `BeanUtils.copyProperties`，设计器回填需二次请求 `GET /form/table-definition/{id}` 做 join → 本轮回填列信息解决。

### 2.2 前端：缺口清单

- `platform-apps/*` 现有应用均为 qiankun 子应用，挂在 **mock 认证**基座 `whale-platform`（8000）下 —— 无法满足「真实认证 + 调 `/form`」，故本轮做**独立 Vite 应用**。
- admin `vite.config.ts` 代理只覆盖 `auth|user|dept|role|menu|permission|audit`，**缺 `/form/**`**。
- 可直接复用：`admin/src/api/{http,token,auth}.ts`、`admin/src/router/guards.tsx`、`admin/src/pages/Login.tsx`、`admin/src/layouts/AdminLayout.tsx`、`admin/package.json`、`admin/tsconfig.json`。
- `frontend/pnpm-workspace.yaml` 已含 `platform-apps/*`，**新应用自动纳入 workspace，无需改工作区配置**。

---

## 三、冻结契约

### 3.1 后端 DDL 契约

- `form_field_config` 新增 `col_span INT NOT NULL DEFAULT 24 COMMENT '栅格宽度：12 半宽 / 24 整宽'`（`sort_no` 之后）。
- `init.sql` 与增量脚本 `db/migration-5.29.sql` 同步（后者供存量库一次性升级）。

### 3.2 后端 VO 契约（`FormFieldConfigVO` 新增字段）

| 字段 | 类型 | 含义 |
|---|---|---|
| `colSpan` | Integer | 栅格宽度：12 半宽 / 24 整宽 |
| `columnCode` | String | 关联列编码（`columnMetaId != null` 时回填） |
| `dataType` | String | 关联列存储类型（回填） |
| `required` | Integer | 关联列是否必填（回填） |
| `maxLength` | Integer | 关联列长度（回填） |

### 3.3 前端 API 契约（`src/api/form.ts`）

类型：`DataType`、`WidgetType`、`ColumnMeta`、`TableDefinition`、`FieldOption`、`FieldConfig`、`FormDefinition`、`FormDataItem`。字段名与后端 DTO/VO 严格一致（完整定义见 §四）。

请求方法：
- `tableDefinitionApi.{page,detail,create,update,remove,publish}`
- `formApi.{page,detail,create,update,remove,publish,tables}`
- `formDataApi.{page,detail,create,remove}`

### 3.4 渲染与序列化契约

- 渲染数据源 = `GET /form/definition/{id}` 的 `fields[]`（天然含纯布局字段），**不是 `schema_json`**。
- 提交 `dataJson` 键必须为 `columnCode` 且只含数据字段；空值字段**不下发键**。
- 类型序列化：`date→YYYY-MM-DD`、`datetime→YYYY-MM-DD HH:mm:ss`、`checkbox→数组 join(',')`、`switch→1|0`、`number→number`、`text/textarea/select/radio→string`。

---

## 四、前端契约类型（`src/api/form.ts`）

```ts
export type DataType = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'bool';
export type WidgetType = 'text' | 'textarea' | 'number' | 'select' | 'radio' | 'checkbox'
  | 'date' | 'datetime' | 'switch' | 'title' | 'divider' | 'text-note';

export interface ColumnMeta { id?: number; columnCode: string; columnName: string; dataType: DataType;
  length?: number; precision?: number; scale?: number; nullable?: number; required?: number;
  defaultValue?: string; remark?: string; sortNo?: number; status?: number; }
export interface TableDefinition { id?: number; tableCode: string; tableName: string; version?: number;
  remark?: string; status?: number; columns?: ColumnMeta[]; }

export interface FieldOption { optionLabel: string; optionValue: string; sortNo?: number; }
export interface FieldConfig { id?: number; columnMetaId?: number | null; fieldLabel: string;
  widgetType?: WidgetType; showInList?: number; showInForm?: number; editable?: number; sortNo?: number;
  colSpan?: number; placeholder?: string; defaultValue?: string; validateJson?: string;
  remark?: string; status?: number; options?: FieldOption[];
  columnCode?: string; dataType?: DataType; required?: number; maxLength?: number; }
export interface FormDefinition { id?: number; code: string; name: string; tableDefinitionId?: number;
  schemaJson?: string; version?: number; status?: number; remark?: string; fields?: FieldConfig[]; }
export interface FormDataItem { id: number; definitionId: number; definitionCode: string;
  definitionVersion: number; bizKey?: string; dataJson: string; fields?: Record<string, unknown> | null;
  submitter?: string; status: number; createTime: string; }
```

---

## 五、后端改动清单（whale-form，最小必要）

> 目标：让设计器「一次请求回填画布」并支持半宽/整宽栅格。**不改 `schema_json` 结构与 `FormMetadataCompiler`**，避免触碰 `FormSchemaParser` 的字段白名单校验。

- **B1** DDL：`init.sql` 在 `form_field_config.sort_no` 后插 `col_span`；新建 `db/migration-5.29.sql`。
- **B2** 实体/DTO/VO：`FormFieldConfig`（+`colSpan`）、`FormFieldConfigDTO`（+`colSpan`，缺省 24）、`FormFieldConfigVO`（+`colSpan/columnCode/dataType/required/maxLength`）。
- **B3** 服务：`toConfig` 写 `colSpan`（缺省 24 常量）；`getDetail/create/update` 走新增私有方法 `enrichColumnInfo`（`formColumnMetaMapper` 按 `tableDefinitionId` 一次查列建 Map 回填，**无 N+1**）；`page()` 不 enrich。

**不做**：`FormMetadataCompiler` / `FormSchemaParser` / `FormPublishService` 改动。

---

## 六、前端改动清单（新应用 `whale-form-designer`）

应用骨架、`api/form.ts`、表定义管理页、表单视图列表页、`@dnd-kit` 三栏设计器、动态渲染器 + 填报页、数据列表页 —— **文件级设计见权威计划 [wave5.29-form-designer.md](../../../.trae/documents/wave5.29-form-designer.md) §4**。

关键约束：

- 顶栏「保存」提交 `fields[]`（含 `colSpan`、`options[]`）；**前置校验**：至少 1 个数据字段、数据字段 `columnCode` 不重复。
- 「发布」顺序：先 `POST /form/table-definition/{tableDefinitionId}/publish`（幂等）→ 再 `POST /form/definition/{id}/publish`。
- 数据字段**禁用复制**（同一表单视图不得重复引用同一列，否则后端编译报错）。

---

## 七、决策记录（冻结）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 本轮范围 | 设计器 + 渲染填报 + 数据列表（完整闭环） |
| 2 | 拖拽实现 | `@dnd-kit`（core + sortable + utilities） |
| 3 | 落点 | 独立新应用 `frontend/platform-apps/whale-form-designer`（非 qiankun 子应用） |
| 4 | 设计器与表定义关系 | 可新建表定义，也可选已有表定义 |
| 5 | 画布布局粒度 | 栅格 12/24（半宽/整宽）→ 后端加 `form_field_config.col_span` |
| 6 | 纯布局字段 | 支持（标题/分割线/说明，`columnMetaId=null`，不参与填报提交） |
| 7 | 认证 | 自带登录、直连网关 8080、**不注册进 qiankun 基座** |
| 8 | 端口 | 8002（8000 基座 / 8001 admin / 8080 网关 / 8101–8103 子应用 / 8084 whale-form 已占） |
| 9 | 权限 | admin = `super_admin`，网关放行，本轮不加权限种子、不做按钮级权限 |
| 10 | 不做 | 表单↔工作流联动、列删除/改类型在线迁移、数据导出、设计器撤销/重做 |

---

## 八、假设与风险

**假设**：数据源为 `GET /form/definition/{id}` 的 `fields[]`；纯布局字段 `widgetType ∈ {title, divider, text-note}`；数据字段复制禁用；布局粒度仅 12/24 两档；checkbox 以逗号拼接字符串提交；`whale-ui` 本轮不扩展。

| 风险 | 对策 |
|---|---|
| 编辑表单后改列导致 schema 与物理表不一致 | 设计器只允许新增列；版本号由用户显式修改后再发布 |
| 同一表定义被多张表单视图共享（1:N） | 「+ 新增列」仅追加列，不修改既有列 |
| `pnpm install` 拉取 `@dnd-kit` 触及沙箱外路径 | 先沙箱内执行；报错再 `dangerouslyDisableSandbox` 重试 |
| `enrich` 增加 `getDetail` 查询次数 | 单次 `selectList(eq tableDefinitionId)` 一次查全列建 Map，无 N+1 |
| 设计与渲染字段类型映射错位 | 冻结 `form.ts` 类型 + 序列化规则；F7 与 F6 预览共用同一 `FormRenderer` |

---

## 九、验证方式

- 后端：`mvn -f backend/pom.xml -pl whale-services/whale-form -am clean install`（Checkstyle `severity=error` + 单测）→ 全量 `mvn -f backend/pom.xml clean install`。
- DDL 落库：对 `bluewhale_form` 执行 `db/migration-5.29.sql`。
- 前端：`pnpm -C frontend install` → `pnpm -C frontend --filter whale-form-designer build`。
- 实机 E2E：7 步正向 + 2 条负向，`browser_use` 子代理在 8002 截图核验。

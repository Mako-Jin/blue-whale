# Wave 5.29：拖拽式表单设计器（前后端贯通）

## 1. 概要（Summary）

为 BlueWhale 搭建**拖拽式表单设计器**并端到端贯通：`建表定义 → 发布物理表 → 拖拽设计表单视图 → 保存 → 发布 → 动态渲染填报 → 数据列表查看/删除`。

- 后端 whale-form（Wave 5.28）已具备除「字段栅格宽度」外的**全部** API，本轮后端仅做**最小必要补强**（`col_span` + 字段配置回填列信息）。
- 前端为**本轮主要工作量**：新建独立应用 `frontend/platform-apps/whale-form-designer`（真实认证，直连网关 8080），采用 `@dnd-kit` 实现拖拽。

**已冻结决策（用户已确认，不再变更）**

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 本轮范围 | 设计器 + 渲染填报 + 数据列表（完整闭环） |
| 2 | 拖拽实现 | `@dnd-kit`（`@dnd-kit/core` + `@dnd-kit/sortable` + `@dnd-kit/utilities`） |
| 3 | 落点 | 独立新应用 `frontend/platform-apps/whale-form-designer`（**非** qiankun 子应用） |
| 4 | 设计器与表定义关系 | 两者都支持：可**新建表定义**，也可**选已有表定义** |
| 5 | 画布布局粒度 | **栅格 12/24**（半宽/整宽）→ 后端加 `form_field_config.col_span` |
| 6 | 纯布局字段 | **支持**（标题/分割线/说明，`columnMetaId = null`，不参与填报提交） |

---

## 2. 现状分析（Current State Analysis）

### 2.1 后端：契约已就绪（Wave 5.28 三层模型）

| 能力 | 接口 | 关键点 |
|---|---|---|
| 表定义 CRUD | `POST/PUT/DELETE/GET /form/table-definition[/{id}]`、`GET /form/table-definition/page` | body 内嵌 `columns[]`；`tableCode` 需匹配 `^[a-z][a-z0-9_]{0,39}$` |
| 发布表定义 | `POST /form/table-definition/{id}/publish` | 按列元数据建物理表 `form_data_{tableCode}_v{version}` + 登记 registry；**同版本重复发布报错** |
| 表单视图 CRUD | `POST /form/definition`、`PUT /form/definition/{id}`、`GET /form/definition/{id}`、`GET /form/definition/page`、`DELETE /form/definition/{id}` | 保存时由 [FormMetadataCompiler.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/support/FormMetadataCompiler.java) 编译 `schema_json`；**至少一个数据字段**，否则 `PARAM_ERROR` |
| 发布表单视图 | `POST /form/definition/{id}/publish` | 元数据模型下**幂等**：确保所属表定义已发布（未发布则代建） |
| 提交数据 | `POST /form/data` | body `{definitionId, dataJson, bizKey}`；`dataJson` 为 JSON 文本且**键必须是 schema 内字段**，未知键 400；租户取 `X-Tenant-Id`（缺省 1） |
| 数据分页/详情/删除 | `GET /form/data/page`、`GET /form/data/{id}`、`DELETE /form/data/{id}` | 详情返回 `fields` Map；列表 `fields` 为 `null` |
| 权限 | `sys:form:*` / `sys:form:table:*` / `sys:form:data:*` | 种子 id 36–39、49–52、53–57；**admin 用户角色为 `super_admin`，网关注入后经 `RequirePermissionInterceptor` 直接放行**，前端无需权限码适配 |

**关键机制**（已核验源码）：
- 提交数据经 `FormDataServiceImpl.submit` → `requireRegistry(tableCode, table.getVersion())` → **必须先发布表定义**，否则报 `表单未发布或版本已变更，请先发布`。
- `schema_json` 是发布快照，**纯布局字段（`columnMetaId == null`）被跳过**，不进快照、不建物理列；`FormSchemaParser` 用 `JsonNode.path()` 取值，**容忍多余键**（不影响后续扩展）。
- `FormFieldConfigVO` 目前**不含 `columnCode` / `dataType`**，`toFieldVo` 用 `BeanUtils.copyProperties`，设计器回填需二次请求 `GET /form/table-definition/{id}` 做 join。

### 2.2 前端：缺口清单

- `platform-apps/*` 现有应用均为 qiankun 子应用，挂在 **mock 认证**基座 `whale-platform`（8000）下 —— 无法满足设计器「真实认证 + 调 `/form`」需求，故本轮做**独立 Vite 应用**。
- [admin/vite.config.ts](file:///d:/BlueWhale/BlueWhale/frontend/admin/vite.config.ts) 的代理只覆盖 `auth|user|dept|role|menu|permission|audit`，**缺 `/form/**`**。
- 可直接复用的既有资产：[admin/src/api/http.ts](file:///d:/BlueWhale/BlueWhale/frontend/admin/src/api/http.ts)（`Result`/`PageResult`/`ApiError` + 401 单飞续期）、[admin/src/api/token.ts](file:///d:/BlueWhale/BlueWhale/frontend/admin/src/api/token.ts)、[admin/src/api/auth.ts](file:///d:/BlueWhale/BlueWhale/frontend/admin/src/api/auth.ts)、[admin/src/router/guards.tsx](file:///d:/BlueWhale/BlueWhale/frontend/admin/src/router/guards.tsx)、[admin/src/pages/Login.tsx](file:///d:/BlueWhale/BlueWhale/frontend/admin/src/pages/Login.tsx)、[admin/src/layouts/AdminLayout.tsx](file:///d:/BlueWhale/BlueWhale/frontend/admin/src/layouts/AdminLayout.tsx)、[admin/package.json](file:///d:/BlueWhale/BlueWhale/frontend/admin/package.json)、[admin/tsconfig.json](file:///d:/BlueWhale/BlueWhale/frontend/admin/tsconfig.json)。
- `frontend/pnpm-workspace.yaml` 已含 `platform-apps/*`，**新应用自动纳入 workspace / turbo，无需改工作区配置**。

---

## 3. 后端改动（whale-form，最小必要）

> 目标：让设计器「一次请求回填画布」并支持半宽/整宽栅格。**不改 `schema_json` 结构与 `FormMetadataCompiler`**，避免触碰 `FormSchemaParser` 的字段白名单校验。

### B1. DDL：`form_field_config` 增加 `col_span`

- 文件 [db/init.sql](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/resources/db/init.sql)：在 `form_field_config` 的 `sort_no` 之后插入
   `col_span INT NOT NULL DEFAULT 24 COMMENT '栅格宽度：12 半宽 / 24 整宽',`
- 新建 [db/migration-5.29.sql](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/resources/db/migration-5.29.sql)（一次性增量脚本，头注释对齐 `migration-5.28.sql` 风格）：

```sql
-- BlueWhale whale-form 增量迁移脚本（Wave 5.29：表单字段配置栅格宽度）
ALTER TABLE form_field_config
    ADD COLUMN col_span INT NOT NULL DEFAULT 24 COMMENT '栅格宽度：12 半宽 / 24 整宽' AFTER sort_no;
```

### B2. 实体 / DTO / VO

- [entity/FormFieldConfig.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/entity/FormFieldConfig.java)：新增 `private Integer colSpan;`（Javadoc：栅格宽度：12 半宽 / 24 整宽）
- [dto/FormFieldConfigDTO.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/dto/FormFieldConfigDTO.java)：新增 `private Integer colSpan;`（缺省 24）
- [vo/FormFieldConfigVO.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/vo/FormFieldConfigVO.java)：新增
  `colSpan`、`columnCode`、`dataType`、`required`、`maxLength`（后 4 项为**关联列信息冗余**，供设计器一次回填）

### B3. 服务：持久化 `colSpan` + 回填列信息

[service/impl/FormDefinitionServiceImpl.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-form/src/main/java/com/bluewhale/form/service/impl/FormDefinitionServiceImpl.java)

- `toConfig(dto, formId)`：写入 `config.setColSpan(dto.getColSpan() == null ? DEFAULT_COL_SPAN : dto.getColSpan())`（新增常量 `DEFAULT_COL_SPAN = 24`）。
- `getDetail(id)`：在 `toVo(entity, listFields(id))` 之后，若 `entity.getTableDefinitionId() != null`，调用新增私有方法
  `enrichColumnInfo(vo.getFields(), entity.getTableDefinitionId())`：用 `formColumnMetaMapper` 按 `tableDefinitionId` 查询列，建立 `Map<Long, FormColumnMeta>`，对 `columnMetaId != null` 的字段补 `columnCode / dataType / required / maxLength`。
- `create/update` 返回同理走 enrich（复用 `getDetail` 的私有方法与 `toVo`）。
- `page()` 不 enrich（列表不需要，保持字段为 `null`）。

**不做**：`FormMetadataCompiler` / `FormSchemaParser` / `FormPublishService` 改动（`schema_json` 结构与发布语义保持不变）。

---

## 4. 前端改动（新应用 `whale-form-designer`）

### 4.1 应用骨架（对齐 admin 约定）

```
frontend/platform-apps/whale-form-designer/
├─ package.json          # name=whale-form-designer, private, type=module
├─ index.html
├─ tsconfig.json         # extends ../tsconfig.base.json（同 admin）
├─ vite.config.ts        # port 8002；proxy '^/(auth|form)(/|$)' → http://localhost:8080
└─ src/
   ├─ main.tsx           # ConfigProvider(antdThemeConfig) + AntdApp + BrowserRouter
   ├─ App.tsx            # useRoutes：/login、/（AppLayout 下：/tables、/forms、/design/:id?、/render/:id、/data）
   ├─ index.css
   ├─ api/
   │  ├─ token.ts        # 复制 admin 版，storage key 前缀改 whale_fd_
   │  ├─ http.ts         # 复制 admin 版（Result/PageResult/ApiError/401 单飞续期）
   │  ├─ auth.ts         # 复制 admin 版（POST /auth/login、GET /auth/me、POST /auth/logout）
   │  └─ form.ts         # 本轮新增：表定义/表单定义/数据的类型 + 请求封装
   ├─ router/guards.tsx  # 复制 admin 版（RequireAuth / GuestOnly）
   ├─ layouts/AppLayout.tsx
   └─ pages/
      ├─ Login.tsx
      ├─ table/TableDefinitionList.tsx      # F4
      ├─ form/FormList.tsx                  # F5
      ├─ designer/FormDesigner.tsx          # F6（@dnd-kit 三栏）
      ├─ designer/Palette.tsx / Canvas.tsx / PropertyPanel.tsx / FieldPreview.tsx
      ├─ render/FormRenderer.tsx            # F7（设计器预览 + 填报共用）
      ├─ render/FormRender.tsx              # F7（填报页）
      └─ data/FormDataList.tsx              # F8
```

`package.json` 依赖：`react@^19`、`react-dom@^19`、`antd@^5.21`、`react-router-dom@^6.27`、`@dnd-kit/core`、`@dnd-kit/sortable`、`@dnd-kit/utilities`、`@bluewhale/whale-ui`、`@bluewhale/whale-common-theme`、`@bluewhale/whale-common-tools`（后三者 `workspace:*`）；devDeps 同 admin（`@vitejs/plugin-react`、`typescript@^5.6`、`vite@^6`、`rimraf`、`@types/react*`）。

### 4.2 `src/api/form.ts`（契约层，F1 冻结）

类型（字段名与后端 DTO/VO 严格一致）：

```ts
export type DataType = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'bool';
export type WidgetType = 'text' | 'textarea' | 'number' | 'select' | 'radio' | 'checkbox'
  | 'date' | 'datetime' | 'switch' | 'title' | 'divider' | 'text-note'; // 后 3 个为纯布局控件

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
  // 回填冗余（来自后端 enrich）
  columnCode?: string; dataType?: DataType; required?: number; maxLength?: number; }
export interface FormDefinition { id?: number; code: string; name: string; tableDefinitionId?: number;
  schemaJson?: string; version?: number; status?: number; remark?: string; fields?: FieldConfig[]; }
export interface FormDataItem { id: number; definitionId: number; definitionCode: string;
  definitionVersion: number; bizKey?: string; dataJson: string; fields?: Record<string, unknown> | null;
  submitter?: string; status: number; createTime: string; }
```

请求方法（均为薄封装）：
`tableDefinitionApi.{page,detail,create,update,remove,publish}`、`formApi.{page,detail,create,update,remove,publish,tables}`、`formDataApi.{page,detail,create,remove}`。

### 4.3 表定义管理页（F4）

- 列表：`GET /form/table-definition/page`；列：`tableCode`、`tableName`、`version`、`status`、`createTime`、操作（编辑 / 发布 / 列管理 / 删除 / **新建表单视图**→跳 `/design?tableDefinitionId={id}`）。
- 新建/编辑弹窗：`tableCode`（正则提示 `^[a-z][a-z0-9_]{0,39}$`）、`tableName`、`version`（默认 1）、`status`、`remark` + **列编辑器**（可增删行）：`columnCode`、`columnName`、`dataType`(下拉 7 种)、`length`(仅 text)、`precision`/`scale`(仅 number)、`nullable`、`required`、`defaultValue`、`sortNo`、`status`、`remark`。
- 发布：`POST /form/table-definition/{id}/publish`；失败时把后端 message（如「该表版本已发布，如需变更请修改版本号后重新发布」）用 `message.error` 原样展示。

### 4.4 表单视图列表页（F5）

- 列表：`GET /form/definition/page`；列：`code`、`name`、`tableDefinitionId`、`version`、`status`、`createTime`。
- 操作：**设计**（`/design/{id}`）、**发布**（`POST /form/definition/{id}/publish`）、**填报**（`/render/{id}`）、**数据**（`/data?definitionCode={code}`）、**删除**。
- 新建：表单弹 `code`（必填）、`name`、`所属表定义`（下拉，来自 `GET /form/table-definition/page`）、`version`(1)、`remark` → `POST /form/definition`（`fields` 暂为空，随后进设计器补齐）。

### 4.5 设计器（F6，核心，@dnd-kit）

路由 `/design/:id?`（可带 `?tableDefinitionId=`）。三栏：

- **左 Palette**
  - 「数据字段」区：由当前表定义的 `columns[]` 驱动，每列一项显示 `columnName (columnCode)`，`dataType` 决定默认 `widgetType`（`bool→switch`，其余回落同名；`select→select`）。拖入画布生成 `{columnMetaId: col.id, fieldLabel: col.columnName, widgetType: 默认, colSpan: 24, sortNo: 末尾, showInList:1, showInForm:1, editable:1}`。
  - 「布局字段」区：**标题 / 分割线 / 说明** 三个固定条目，拖入生成 `{columnMetaId: null, widgetType: 'title'|'divider'|'text-note', fieldLabel: 默认文案, colSpan: 24, sortNo: 末尾}`。
  - 「+ 新增列」按钮：就地弹列编辑弹窗 → `PUT /form/table-definition/{id}`（整体替换 columns，**保留原 sortNo**）→ 刷新列列表。
- **中 Canvas**
  - `DndContext` + `SortableContext`（`@dnd-kit/sortable` + `verticalListSortingStrategy`）；antd `Row gutter=16` + `Col span={colSpan}`（12 半宽 / 24 整宽，`Row` 自动换行 → 半宽字段自然并排）。
  - 从 Palette 拖入 → 追加到末尾；画布内拖动 → 重排并重算 `sortNo`（`arrayMove` + 索引即 sortNo）。
  - 每个卡片：显示 `fieldLabel` + 控件类型标签 + 宽度标签；选中态高亮（点击 → 右侧属性面板）；卡片右上角操作：**删除**、**复制**（布局字段复制为新行；数据字段复制会重复引用同一列 → **禁用复制**，避免后端「同一表单视图重复引用列」报错）。
  - 空画布占位提示「从左侧拖入字段」。
- **右 PropertyPanel**（选中项属性）
  - 数据字段：`fieldLabel`、`widgetType`（候选按 `dataType` 过滤：`text→text/textarea`、`textarea→textarea`、`number→number`、`select→select/radio/checkbox`、`date→date`、`datetime→datetime`、`bool→switch`）、`colSpan`（整宽 24 / 半宽 12 `Radio.Group`）、`showInList`、`showInForm`、`editable`、`placeholder`、`defaultValue`；`columnCode`/`dataType`/`required` 以只读展示（由列元数据决定）。
  - 选项编辑（`widgetType ∈ {select, radio, checkbox}`）：行内表格增删改 `optionLabel` / `optionValue` / `sortNo`。
  - 布局字段：`fieldLabel`（标题/说明文本）、`colSpan`；`divider` 仅 `colSpan`。
- **顶栏**：表单 `name`/`code`、`所属表定义`（只读展示，来自 `tableDefinitionId`）、按钮：**保存**、**发布**、**预览**（`Drawer` 内嵌 `<FormRenderer>` 实时渲染）、**填报**、**返回**。
- **保存**：`PUT /form/definition/{id}`；若新建且无 id 则 `POST /form/definition`（成功后 `navigate('/design/{newId}')`）。提交 `fields[]`（含 `colSpan`、`options[]`）；**前置校验**：至少 1 个数据字段、数据字段的 `columnCode` 不重复（否则后端 400，前端先拦）。
- **发布**：先 `POST /form/table-definition/{tableDefinitionId}/publish`（幂等，已发布则直接返回登记）→ 再 `POST /form/definition/{id}/publish`。
- **回填**：`GET /form/definition/{id}` → `fields[]`（含 `colSpan` + `columnCode/dataType`）与 `GET /form/table-definition/{tableDefinitionId}`（列清单）→ 还原画布与 Palette。

### 4.6 动态渲染器 + 填报页（F7）

`FormRenderer`（受控组件，`value`/`onChange` + `fields` 入参，设计器预览与填报页共用）：
- 过滤 `showInForm !== 0`；`Row/Col span={colSpan ?? 24}` 栅格布局。
- 布局字段：`title`→`Typography.Title level=5`；`divider`→`Divider`；`text-note`→`Typography.Text type="secondary"`。
- 数据字段按 `widgetType`：
  - `text`→`Input maxLength={maxLength ?? 255}`、`textarea`→`Input.TextArea`、`number`→`InputNumber`
  - `select`→`Select`、`radio`→`Radio.Group`、`checkbox`→`Checkbox.Group`
  - `date`→`DatePicker format="YYYY-MM-DD"`、`datetime`→`DatePicker showTime format="YYYY-MM-DD HH:mm:ss"`、`switch`→`Switch`（值 `1/0`）
  - 选项 `options` 映射为 `{ label: optionLabel, value: optionValue }`。
- 必填：`required === 1` → `rules:[{required:true, message:'请输入{fieldLabel}'}]`。

`FormRender`（路由 `/render/:id`）：
- 加载 `GET /form/definition/{id}`（拿 `fields[]` 与 `code/name`）→ `<FormRenderer>` → 提交。
- **提交前序列化**（`dataJson` 键必须为 `columnCode` 且只含数据字段）：
  - `date` → `dayjs.format('YYYY-MM-DD')`；`datetime` → `dayjs.format('YYYY-MM-DD HH:mm:ss')`
  - `checkbox` → 数组 `join(',')`（后端 `select` 列为 `VARCHAR(255)`）
  - `switch` → `1|0`；`number` → `number`；`text/textarea/select/radio` → `string`
  - 空值字段**不下发键**（后端 `validateFields` 对未知键 400、对必填空值 400）
- `POST /form/data {definitionId, dataJson: JSON.stringify(values), bizKey}` → 成功 `message.success('提交成功')` 并重置表单。
- 未发布时后端返回「表单未发布或版本已变更，请先发布」→ 原样提示。

### 4.7 数据列表页（F8）

路由 `/data?definitionCode=`。
- `GET /form/data/page`；列：`id`、`definitionCode`、`definitionVersion`、`bizKey`、`submitter`、`createTime`、`status`。
- 详情 `Drawer`：`GET /form/data/{id}` 拿 `fields` Map，结合 `GET /form/definition/{id}` 的 `fields[]`（`fieldLabel` ↔ `columnCode`）做「标签: 值」有序展示。
- 删除：`DELETE /form/data/{id}`（后端同时逻辑删除主表与物理表行）。

---

## 5. 任务拆分（可并行）

> 原则：先冻结契约（F1），再按「后端 / 前端骨架」两条独立轨并行，随后 4 个页面级任务并行，最后设计器与门禁收口。

| 阶段 | 任务 | 依赖 | 交付物 |
|---|---|---|---|
| **G1** | **F1 文档与契约冻结** | — | `docs/plans/wave5.29-form-designer/spec.md` + `tasks.md`；roadmap §2.5 追加 Wave 5.29 条目；`task-breakdown.md` 追加 Wave 5.29-a/b/c 分组；**冻结 `src/api/form.ts` 类型与接口契约**（§4.2） |
| **G2（并行）** | **F2 后端补强** | F1 | B1 DDL(`init.sql` + `migration-5.29.sql`) + B2 实体/DTO/VO + B3 Service enrich；whale-form 单测补充（`colSpan` 持久化 + enrich 回填） |
| | **F3 前端骨架** | F1 | 新应用骨架（§4.1）+ `api/{token,http,auth,form}.ts` + guards + AppLayout + Login；`pnpm install` 装 `@dnd-kit` |
| **G3（并行）** | **F4 表定义管理页** | F3 | `pages/table/TableDefinitionList.tsx`（含列编辑器 + 发布） |
| | **F5 表单视图列表页** | F3 | `pages/form/FormList.tsx` |
| | **F7 渲染器 + 填报页** | F3 | `render/FormRenderer.tsx` + `render/FormRender.tsx` |
| | **F8 数据列表页** | F3 | `pages/data/FormDataList.tsx` |
| **G4** | **F6 @dnd-kit 设计器** | F3、F5（复用 Palette 列来源与保存语义） | `designer/*`（Palette/Canvas/PropertyPanel + 保存/发布/预览/回填） |
| **G5** | **F9 门禁与端到端贯通** | F2、F4、F5、F6、F7、F8 | 后端 checkstyle + 单测 + 全量构建；前端 `tsc -b && vite build`；实机 E2E（见 §6）；检查报告 `docs/reviews/2026-10-20-wave5.29-form-designer.md`；`docs/devlog/2026-10-20.md`；台账勾选；commit & push origin(Gitee) |

---

## 6. 验证方式（Verification）

### 6.1 后端

```powershell
$env:JAVA_HOME='D:\tools\installer\jdk\java21'
mvn -f backend/pom.xml -pl whale-services/whale-form -am clean install   # Checkstyle(severity=error) + 单测
mvn -f backend/pom.xml clean install                                     # 全量 29 模块
```
（Maven 本地仓 `D:\repository\maven` 在沙箱外 → 需 `dangerouslyDisableSandbox: true`）

DDL 落库：对 `bluewhale_form` 执行 `db/migration-5.29.sql`（MySQL 需运行）。

### 6.2 前端

```powershell
pnpm -C frontend install
pnpm -C frontend --filter whale-form-designer build   # tsc -b && vite build
```

### 6.3 实机端到端（E2E）

前置：MySQL / Redis / Nacos / whale-auth / whale-gateway / **whale-form(8084)** 已启动；设计器 dev 服务 `pnpm -C frontend --filter whale-form-designer dev`（8002）。

1. 打开 `http://localhost:8002/login`，`admin / 123456` 登录（真实 `/auth/login`）。
2. `/tables` 新建表定义 `bw_demo_guest`（列：`guest_name` text + `age` number + `join_date` date + `level` select + `remark` textarea）→ 保存 → **发布**（生成 `form_data_bw_demo_guest_v1`）。
3. `/forms` 新建表单视图（选该表定义）→ 进 `/design/{id}`：拖入 4 个数据字段 + 1 个标题，2 个字段改半宽 → **保存** → **发布**。
4. `/render/{id}` 填报：必填留空应报错；正确填写提交 → 提示成功。
5. `/data?definitionCode=bw_demo_guest` 列表可见新行 → 详情展示 `标签: 值` → 删除 → 列表消失、详情 404。
6. 负向：未发布表单直接填报 → 后端 `表单未发布或版本已变更，请先发布`；同版本重复发布 → `该表版本已发布，如需变更请修改版本号后重新发布`。
7. 用 `browser_use` 子代理在 8002 上截图核验：登录页、表定义列表、三栏设计器（拖拽后可保存并回填）、填报页、数据列表。

---

## 7. 假设与决策（Assumptions & Decisions）

1. **独立真实认证应用**：`whale-form-designer` 自带登录并**直连网关 8080**（vite proxy `/auth`、`/form`），**不注册进 qiankun 基座**（基座为 mock 认证，本轮不打通）。
2. **端口 8002**（8000 基座 / 8001 admin / 8080 网关 / 8101–8103 子应用 / 8084 whale-form 均已占用）。
3. **权限**：admin = `super_admin`，网关注入 `X-Permission-Codes: super_admin` → `RequirePermissionInterceptor` 全放行，**本轮不加权限种子、不做按钮级权限**。
4. **`schema_json` 结构不变**：`span` 只落 `form_field_config.col_span` 并经 VO 回填；**不写入快照**。渲染数据源 = `GET /form/definition/{id}` 的 `fields[]`（天然含纯布局字段），不是 `schema_json`。
5. **纯布局字段语义**：`columnMetaId = null`，`widgetType ∈ {title, divider, text-note}`；后端编译快照时自动跳过（不建列、不参与提交校验）；前端填报**不提交**这些键。
6. **数据字段复制禁用**（同一表单视图不得重复引用同一列 —— 后端 `FormMetadataCompiler` 会报错）。
7. **布局粒度只有 12 / 24** 两档（不做 8/6 等更细栅格）。
8. **checkbox 多选值以逗号拼接字符串提交**（对应后端 `select` 存储列 `VARCHAR(255)`）。
9. `whale-ui` 组件本轮**不扩展**（设计器用 antd 原生组件），仅复用 `whale-common-theme` 主题与既有工具包。
10. 本轮**不做**：表单↔工作流联动、列删除/改类型的在线物理表迁移、表单数据导出、设计器撤销/重做（`@dnd-kit` 仅做拖拽本身）。

---

## 8. 关键风险与对策

| 风险 | 对策 |
|---|---|
| 编辑表单后改列会导致 schema 与物理表不一致 | 本轮设计器**只允许新增列、不允许删除已发布列**；版本号由用户显式修改后再发布 |
| 同一表定义被多张表单视图共享（1:N），改列影响他表 | 设计器「+ 新增列」仅追加列，不修改既有列；列编辑仅在表定义页做并给出二次确认提示 |
| `pnpm install` 拉取 `@dnd-kit` 可能触及沙箱外路径 | 先在沙箱内执行；若报权限/路径错误再 `dangerouslyDisableSandbox: true` 重试 |
| 后端 `enrich` 增加 `getDetail` 的查询次数 | 单次 `formColumnMetaMapper.selectList(eq tableDefinitionId)`，一次查全列建 Map，无 N+1 |
| 设计与渲染字段类型映射错位 | F1 冻结 `form.ts` 类型 + §4.6 序列化规则；F7 与 F6 预览共用同一 `FormRenderer`，天然一致 |

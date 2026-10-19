# Wave 5.28 规格（whale-form 动态表单元数据模型 · 三层分离 · 冻结契约）

> 用户指令（2026-10-19）：「有一个动态列的详情表，里面记录表的列、什么类型、多少长度、必填与否、能不能为空、备注等等；还有一个表单的属性详情表，记录表单列表、显示不显示列、排序、属性类型（下拉框/单选/文本/数字）、允不允许编辑、关联哪个字段等等；然后表单和表有一个关联。是不是这样的，你优化一下。」
> 经 AskUserQuestion 冻结口径（详见 §七 决策记录）：**(a) 表:表单 = 1:N**（一张表被多张表单视图共享）｜**(b) 选项独立表** `form_field_option`｜**(c) `schema_json` 保留为发布快照**｜**(d) 本轮仅后端元数据表 + 迁移**（前端设计器/动态渲染后置）。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 编码 → 模块自检（REQ-QA-002）→ 全量回归 → 检查报告 → 台账勾选 → 提交推送**。
> 前置：Wave 5.27 已交付动态表引擎（`form_table_registry` + `form_data_{code}_v{version}` 物理表 + `tenant_id` 行级隔离，36 单测全绿）。
> 关联台账：[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)。

---

## 一、设计优化：为什么是「三层」而不是「两张表」

用户提出的是**两张表**（列详情 + 表单属性详情）+ 一个关联。方向正确，但**两处必须拆开**，否则模型会退化：

1. **存储类型 ≠ 控件类型**。Wave 5.27 中 `FormField.type` 一身二职（既是 DDL 列类型又是前端控件），导致一个 `VARCHAR` 列无法既渲染「下拉」又渲染「单选」。本波拆为：
   - `data_type`（**存储/DDL**）：`text / textarea / number / date / datetime / select / bool`；
   - `widget_type`（**前端控件**）：`text / textarea / number / select / radio / checkbox / date / datetime / switch`。
   - 同一 `data_type=VARCHAR` 可按不同表单视图渲染成 text / select / radio / checkbox。
2. **选项必须独立成表**。下拉/单选/多选的候选值若塞进属性行的 JSON，将无法按值检索、无法复用、无法约束。独立 `form_field_option`（label + value + sort）。

优化后的**三层模型**：

```
① 表定义层（Table Definition Layer）
   form_table_definition  1 ──< N  form_column_meta
        （业务表 + 版本）              （列：编码/名称/存储类型/长度/精度/是否可空/是否必填/默认值/备注/排序）

② 表单视图层（Form View Layer）          表:表单 = 1:N（一张表被多张表单视图共享）
   form_table_definition  1 ──< N  form_definition（+ table_definition_id）
   form_definition        1 ──< N  form_field_config
                                        （字段属性：显示名/控件类型/列表显示/表单显示/可编辑/排序/占位/默认值/校验/关联列）
   form_field_config      N ──> 1  form_column_meta（可为 NULL = 纯布局字段：标题/分割线/说明）

③ 选项层（Option Layer）
   form_field_config      1 ──< N  form_field_option
                                        （option_label / option_value / sort）
```

**发布与运行语义（三层各自的职责）**

| 动作 | 输入 | 输出 |
|---|---|---|
| 发布**表定义** | `form_column_meta`（status=1，table_version=version） | 物理表 `form_data_{table_code}_v{version}`（真实列）+ `form_table_registry` 登记 |
| 发布**表单视图** | `form_field_config` + `form_field_option` | 编译快照写回 `form_definition.schema_json`，并确保其表定义物理表已发布（幂等） |
| 提交**数据** | 该表单视图 `field_config`（`show_in_form=1`）选出的列子集 | 按子集校验（未知字段/必填/长度）后写物理表（物理表以表定义为准），主表记录 `form_definition_id` |
| 查询**详情** | `form_data.table_code` + `table_version` | 路由到对应物理表读结构化字段 |

**迁移**：历史 `form_definition.schema_json` 无元数据，迁移时反推为「表定义（`table_code = form.code`、`version = form.version`）+ 列元数据 + 字段配置 + 选项」，并回填 `form_definition.table_definition_id`。
因 `table_code == form.code` 且 `table_version == form.version`，**迁移后物理表名 `form_data_{table_code}_v{version}` 与 Wave 5.27 的 `form_data_{form.code}_v{version}` 完全一致，存量物理表与数据零改动**。

---

## 二、范围与非目标

| 轨 | 范围 | 落点 |
|---|---|---|
| **A 表结构** | 4 张新表 + `form_definition`/`form_table_registry`/`form_data` 兼容列 | `whale-form` db |
| **B 类型与编译** | `data_type`/`widget_type` 白名单与映射、元数据→`schema_json` 编译、列元数据→DDL | `whale-form` `support` |
| **C 元数据服务** | 表定义（含列）聚合 CRUD、字段配置（含选项）聚合 CRUD | `whale-form` |
| **D 迁移** | `schema_json.fields[]` → 表定义 + 列 + 字段配置 + 选项（幂等） | `whale-form` |
| **E 发布/提交改造** | 发布走元数据（表定义建物理表、表单视图编译快照）；提交按字段视图校验 | `whale-form` |
| **F 门禁** | 单测 + checkstyle + 全量构建 + 网关冒烟 + 检查报告 | 全仓 |

**非目标（明确不做）**
- 前端表单设计器与动态渲染页（本波纯后端元数据层）。
- 字段级索引自动优化、列删除/改类型的在线数据迁移（按要求「升版本建新物理表」）。
- 跨表格/跨版本聚合查询、外键约束、字段级唯一约束。
- 选项的运行时字典表（如从数据字典服务拉取）——本轮选项仍随字段配置内联维护。

---

## 三、冻结契约（实现前不得更改）

### 3.1 `form_table_definition`（表定义层）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| table_code | VARCHAR(64) NOT NULL | 表编码（业务唯一，物理表名来源；`key` 规则 `^[a-z][a-z0-9_]{0,39}$`） |
| table_name | VARCHAR(128) NOT NULL | 表名称 |
| version | INT NOT NULL DEFAULT 1 | 版本号 |
| remark | VARCHAR(255) NULL | 备注 |
| status | TINYINT NOT NULL DEFAULT 1 | 1 正常 / 0 禁用 |
| create_time / update_time / deleted | | 公共字段 |
| KEY `idx_table_code` (`table_code`) | | 唯一性由服务层前置校验保证 |

### 3.2 `form_column_meta`（表定义层 · 列）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| table_definition_id | BIGINT NOT NULL | 表定义 ID |
| table_version | INT NOT NULL DEFAULT 1 | 该列所属表版本（发布 `version=N` 取 `table_version=N` 且 status=1 的列） |
| column_code | VARCHAR(64) NOT NULL | 列编码（= 物理列名；规则同 `key`，不得命中保留列） |
| column_name | VARCHAR(128) NOT NULL | 列名称（中文名） |
| data_type | VARCHAR(32) NOT NULL | **存储类型**：`text/textarea/number/date/datetime/select/bool` |
| length | INT NULL | 长度（`text` 生效；缺省 255，范围 1..2000） |
| precision | INT NULL | 精度（`number` 生效；缺省 18） |
| scale | INT NULL | 小数位（`number` 生效；缺省 4） |
| nullable | TINYINT NOT NULL DEFAULT 1 | 能否为空：1 可空 / 0 非空 |
| required | TINYINT NOT NULL DEFAULT 0 | 是否必填：1 必填 / 0 非必填（服务层校验，不建 DB NOT NULL） |
| default_value | VARCHAR(255) NULL | 默认值 |
| remark | VARCHAR(255) NULL | 备注 |
| sort_no | INT NOT NULL DEFAULT 0 | 排序号 |
| status | TINYINT NOT NULL DEFAULT 1 | 1 正常 / 0 禁用 |
| create_time / update_time / deleted | | 公共字段 |
| KEY `idx_table_definition_id` (`table_definition_id`) | | |

### 3.3 `form_definition` 改造（表单视图层）

- **新增** `table_definition_id BIGINT NULL COMMENT '所属表定义ID（迁移后回填；1:N 共享）'` + `KEY idx_table_definition_id`。
- **保留** `schema_json`（由字段配置编译的**发布快照**，非作者输入源）。

### 3.4 `form_field_config`（表单视图层 · 字段属性）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| form_id | BIGINT NOT NULL | 表单定义 ID |
| column_meta_id | BIGINT NULL | 关联列元数据 ID；**NULL = 纯布局字段**（标题/分割线/说明） |
| field_label | VARCHAR(128) NOT NULL | 字段显示名 |
| widget_type | VARCHAR(32) NULL | **控件类型**（见 §3.6） |
| show_in_list | TINYINT NOT NULL DEFAULT 1 | 列表是否显示 |
| show_in_form | TINYINT NOT NULL DEFAULT 1 | 表单是否显示 |
| editable | TINYINT NOT NULL DEFAULT 1 | 是否允许编辑 |
| sort_no | INT NOT NULL DEFAULT 0 | 排序号 |
| placeholder | VARCHAR(255) NULL | 占位提示 |
| default_value | VARCHAR(255) NULL | 默认值（视图级覆盖） |
| validate_json | VARCHAR(1024) NULL | 校验规则 JSON（如 `{"min":0,"max":150}`） |
| remark | VARCHAR(255) NULL | 备注 |
| status | TINYINT NOT NULL DEFAULT 1 | 1 正常 / 0 禁用 |
| create_time / update_time / deleted | | 公共字段 |
| KEY `idx_form_id` (`form_id`)、KEY `idx_column_meta_id` (`column_meta_id`) | | |

### 3.5 `form_field_option`（选项层）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| field_config_id | BIGINT NOT NULL | 字段配置 ID |
| option_label | VARCHAR(128) NOT NULL | 选项显示名 |
| option_value | VARCHAR(128) NOT NULL | 选项值 |
| sort_no | INT NOT NULL DEFAULT 0 | 排序号 |
| status | TINYINT NOT NULL DEFAULT 1 | 1 正常 / 0 禁用 |
| create_time / update_time / deleted | | 公共字段 |
| KEY `idx_field_config_id` (`field_config_id`) | | |

### 3.6 `data_type` ↔ MySQL 列类型 ↔ `widget_type`（冻结）

**存储类型 `data_type` → MySQL 列类型**

| data_type | MySQL 列类型 | 参数 |
|---|---|---|
| text | `VARCHAR(n)` | `n = length`（缺省 255，1..2000） |
| textarea | `TEXT` | |
| number | `DECIMAL(p,s)` | `p = precision`（缺省 18）、`s = scale`（缺省 4） |
| date | `DATE` | |
| datetime | `DATETIME` | |
| select | `VARCHAR(255)` | 存选项值 |
| bool | `TINYINT` | 0/1 |

**控件类型 `widget_type` 白名单**：`text` / `textarea` / `number` / `select` / `radio` / `checkbox` / `date` / `datetime` / `switch`。
**兼容规则**：`widget_type` 与 `data_type` 相互独立校验；`radio/checkbox/select` 建议其列 `data_type=select`（`VARCHAR(255)`）；`switch` 建议其列 `data_type=bool`。缺失 `widget_type` 时，按 `data_type` 默认映射（text→text、select→select、bool→switch、number→number、date→date、datetime→datetime、textarea→textarea）。

### 3.7 `form_table_registry` / `form_data` 兼容改造（冻结）

- `form_table_registry` **新增** `table_definition_id BIGINT NULL`、`table_code VARCHAR(64) NULL`、`table_version INT NULL`；**将 `definition_id/definition_code/definition_version` 改为可空**（历史行保留原值）；新增 `KEY idx_table_code_version (table_code, table_version)`。
- `form_data` **新增** `table_definition_id BIGINT NULL`、`table_code VARCHAR(64) NULL`、`table_version INT NULL`、`form_definition_id BIGINT NULL`；新增 `KEY idx_table_code_version (table_code, table_version)`、`KEY idx_form_definition_id (form_definition_id)`。
- **物理表公共列保持 Wave 5.27 原样**（`id/tenant_id/definition_id/definition_version/biz_key/submitter/status/create_time/update_time/deleted`）；其中 `definition_id` 承载**所属表定义 ID**、`definition_version` 承载**表版本**（迁移后 `table_code == form.code`，故与历史语义同值，存量物理表零改动）。

### 3.8 接口（服务前缀 `/form`；网关 `Path=/form/**` 只转发不剥离）

**新增：表定义（含列）聚合**

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| POST | `/form/table-definition` | `sys:form:table:add` | 新增表定义 + 列（body 内嵌 `columns[]`） |
| PUT | `/form/table-definition/{id}` | `sys:form:table:edit` | 更新表定义，整体替换列集合 |
| DELETE | `/form/table-definition/{id}` | `sys:form:table:delete` | 逻辑删除表定义 + 其列 |
| GET | `/form/table-definition/{id}` | `sys:form:table:list` | 表定义详情（含列） |
| GET | `/form/table-definition/page` | `sys:form:table:list` | 表定义分页 |
| POST | `/form/table-definition/{id}/publish` | `sys:form:table:publish` | 发布表定义：由列元数据建物理表 + 登记 registry |
| GET | `/form/table-definition/{id}/tables` | `sys:form:table:list` | 该表定义已发布版本列表 |

**改造：表单视图（含字段配置与选项）**

| 方法 | 路径 | 权限码 | 变更 |
|---|---|---|---|
| POST | `/form/definition` | `sys:form:add` | body 可带 `tableDefinitionId` + `fields[]`（内嵌 `options[]`）；持久化字段配置并**编译** `schema_json` 快照 |
| PUT | `/form/definition/{id}` | `sys:form:edit` | 同上，整体替换字段配置与选项 |
| GET | `/form/definition/{id}` | `sys:form:list` | 返回 `tableDefinitionId` + `fields[]`（含 `options[]`） |
| POST | `/form/definition/{id}/publish` | `sys:form:publish` | 编译快照写回 `schema_json` + **确保**其表定义物理表已发布（幂等，未发布则建） |
| GET | `/form/definition/{id}/tables` | `sys:form:list` | 兼容保留（按表定义维度返回） |
| POST | `/form/data` / GET `/form/data/{id}` / GET `/form/data/page` / DELETE `/form/data/{id}` | 既有 | 提交按**表单视图 `field_config`（`show_in_form=1`）**选列校验；主表新增 `table_code/table_version/form_definition_id` 路由 |

**兼容规则（冻结）**：`form_definition` 无 `tableDefinitionId`（未迁移）时，`/form/definition` 保存与 `publish` 走 **Wave 5.27 原路径**（`schema_json` 直接作为来源、物理表名 `form_data_{form.code}_v{version}`）。

### 3.9 权限种子（whale-auth `db/init.sql` 追加）

| id | perm_code | perm_name | method | path |
|---|---|---|---|---|
| 53 | `sys:form:table:add` | 新增表定义 | POST | `/form/table-definition` |
| 54 | `sys:form:table:edit` | 修改表定义 | PUT | `/form/table-definition/**` |
| 55 | `sys:form:table:delete` | 删除表定义 | DELETE | `/form/table-definition/{id}` |
| 56 | `sys:form:table:list` | 查询表定义 | GET | `/form/table-definition/**` |
| 57 | `sys:form:table:publish` | 发布表定义 | POST | `/form/table-definition/{id}/publish` |

### 3.10 SQL 注入防护与校验（沿用 Wave 5.27，冻结）

- 表名：`form_data_{table_code}_v{version}`，执行前二次正则 `^form_data_[a-z0-9_]+_v[0-9]+$`。
- 列名：来源为 `form_column_meta.column_code`（服务端校验 `^[a-z][a-z0-9_]{0,63}$` + 保留列排除），一律反引号。
- 值：全部 JDBC `?` 占位参数化。
- 字段名校验：提交字段必须属于该表单视图 `field_config`（`show_in_form=1`）且关联到列；未知字段 / 必填缺失 / 超长 → `PARAM_ERROR`。

### 3.11 迁移与幂等（冻结）

- 迁移入口：`FormMetadataMigrationService#migrateLegacy()`（幂等）——对 `table_definition_id IS NULL` 且 `schema_json` 非空的 `form_definition`：
  1. 建 `form_table_definition`（`table_code = form.code`、`version = form.version`）；
  2. 由 `schema_json.fields[]` 建 `form_column_meta`（`column_code=key`、`column_name=label`、`data_type=type`、`length=maxLength`、`required`）；
  3. 建 `form_field_config`（`column_meta_id` 对应列、`field_label=label`、`widget_type` 按默认映射）；
  4. `select` 字段的 `options[]` 拆入 `form_field_option`（label=value=选项文本）；
  5. 回填 `form_definition.table_definition_id`，并回填 `form_table_registry`/`form_data` 的 `table_definition_id/table_code/table_version`。
- 迁移不改动物理表与历史数据（表名同构）。

---

## 四、新增/修改文件清单（预期）

**新增**
- `entity/FormTableDefinition.java`、`entity/FormColumnMeta.java`、`entity/FormFieldConfig.java`、`entity/FormFieldOption.java`
- `mapper/FormTableDefinitionMapper.java`、`mapper/FormColumnMetaMapper.java`、`mapper/FormFieldConfigMapper.java`、`mapper/FormFieldOptionMapper.java`
- `support/FormDataType.java`、`support/FormWidgetType.java`、`support/FormMetadataCompiler.java`
- `service/FormTableDefinitionService.java` + `service/impl/FormTableDefinitionServiceImpl.java`
- `service/FormMetadataMigrationService.java` + `service/impl/FormMetadataMigrationServiceImpl.java`
- `controller/FormTableDefinitionController.java`
- `dto/FormTableDefinitionSaveRequest.java`、`dto/FormColumnMetaDTO.java`、`dto/FormFieldConfigDTO.java`、`dto/FormFieldOptionDTO.java`
- `vo/FormTableDefinitionVO.java`、`vo/FormColumnMetaVO.java`、`vo/FormFieldConfigVO.java`、`vo/FormFieldOptionVO.java`
- 测试：`support/FormMetadataCompilerTest`、`service/impl/FormTableDefinitionServiceImplTest`、`service/impl/FormMetadataMigrationServiceImplTest`

**修改**
- `entity/FormDefinition.java`（+`tableDefinitionId`）、`entity/FormTableRegistry.java`（+table 维度列）、`entity/FormData.java`（+table/form 维度列）
- `dto/FormDefinitionSaveRequest.java`（+`tableDefinitionId`、+`fields[]`）、`vo/FormDefinitionVO.java`（+`tableDefinitionId`、+`fields[]`）
- `service/FormDefinitionService(+Impl)`（持久化字段配置 + 编译快照）、`service/FormPublishService(+Impl)`（表定义发布 + 视图快照）、`service/FormDataService(+Impl)`（按视图字段校验 + 表维度路由）
- `controller/FormController.java`（详情/保存带字段）、`mapper/FormTableRegistryMapper`/`mapper/FormDataMapper`（不动）
- `resources/db/init.sql`（4 新表 + 3 表兼容列）
- `whale-auth/src/main/resources/db/init.sql`（种子 53–57）

---

## 五、完成定义（DoD）

- `whale-form` 模块 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时段证据（经网关 8080）：
  1. 新建**表定义**（3 列）+ 发布 → `bluewhale_form` 生成 `form_data_{table_code}_v{n}`，`SHOW CREATE TABLE` 列与列元数据一致；
  2. 新建 **2 张表单视图**（同表定义，字段可见性/可编辑不同）→ 详情各返回自身 `fields[]`；
  3. 表单视图 A 提交 → 主表 `form_definition_id`/`table_code`/`table_version` 落库 + 物理表真实列写入；详情 `fields` 回显；
  4. 负向：未迁移/未发布提交 → `400`；未知字段 → `400`；必填缺失 → `400`；跨租户 → `404`。
- `docs/reviews/2026-10-19-wave5.28-form-metadata-model.md` + devlog 追加 + roadmap/task-breakdown 勾选 + commit & push origin（Gitee）。

---

## 六、三层 vs 两张表：对照小结

| 用户原设想 | 优化后 | 理由 |
|---|---|---|
| 列详情表（类型/长度/必填/可空/备注） | `form_column_meta`（+ 表定义 `form_table_definition`） | 列需归属到「表」才有复用与发布边界；`data_type` 只管存储 |
| 表单属性详情表（显示/排序/属性类型/是否可编辑/关联字段） | `form_field_config`（+ `widget_type`） | 属性类型=**控件类型**，与存储类型解耦；「关联哪个字段」= `column_meta_id` |
| 下拉/单选/文本/数字（选项） | `form_field_option` | 选项独立成表，可检索/复用，不塞 JSON |
| 表单 ↔ 表 一个关联 | `form_definition.table_definition_id`（N:1） | 一张表被多张表单视图共享（申请表/审批表） |
| （无） | `schema_json` 作为**发布快照** | 兼容 Wave 5.27 引擎与存量物理表，支持版本回溯 |

---

## 七、决策记录（2026-10-19，AskUserQuestion 冻结）

| # | 议题 | 结论 |
|---|---|---|
| a | 表单:表 基数 | **1:N**（一张表被多张表单视图共享，字段可见性/可编辑性不同） |
| b | options 存储 | **独立 `form_field_option` 表**（非 JSON） |
| c | `form_definition.schema_json` | **保留为发布快照**（元数据表为唯一真源，快照用于版本回溯/回退） |
| d | 本轮范围 | **仅后端元数据表 + 迁移**（前端设计器/动态渲染后置） |

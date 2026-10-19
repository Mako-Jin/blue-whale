# Wave 5.27 规格（whale-form 动态表引擎 · 主从分离 · 冻结契约）

> 用户指令（2026-10-19）：「我想的动态表单，是用户在页面可以自由新建表单，对应后端新建对应的表，字段等。然后客户端可以动态绘制渲染出表单，你现在这样的实现，业务多了，是不是这张表会炸。」
> 经 AskUserQuestion 逐轮冻结口径（详见 §六 决策记录）：**业务用户自助动态表单 + 多租户行级 `tenant_id` + 纯后端引擎 + 保留 `data_json` 过渡 + 版本化建新表 + `tenant_id` 取自 `X-Tenant-Id` 头（缺省回落默认租户 1）**。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归（REQ-QA-002）**。
> 前置：Wave 5.26 已交付 `form_data`（库 `bluewhale_form`、`/form/data` 4 接口、种子 `sys:form:data:submit/list/delete` id 49–51）。
> 关联台账：[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)、[service-catalog.md](../../service-catalog.md)。

---

## 一、范围与非目标

| 轨 | 范围 | 落点 |
|---|---|---|
| **A 表与登记** | `form_data` 加 `tenant_id`（保留 `data_json` 过渡）；新增 `form_table_registry`（版本↔物理表登记，含 schema 快照） | `whale-form` db |
| **B 引擎内核** | `schema_json` 解析器（字段/类型白名单校验）+ DDL 生成器 + `JdbcTemplate` 参数化动态数据引擎（建表/插入/查询/逻辑删除） | `whale-form` `support` |
| **C 发布与接线** | `POST /form/definition/{id}/publish`（解析 schema → 建物理表 → 登记 registry）+ `GET /form/definition/{id}/tables`；权限种子 `sys:form:publish`（id 52） | `whale-form`、`whale-auth` db |
| **D 数据改造** | `form_data` 提交/详情/删除改为主表 + 物理表双写/双删；详情返回结构化 `fields`；`tenant_id` 行级过滤 | `whale-form` |
| **E 门禁** | 单测 + checkstyle + 全量构建 + 网关 `/form` 冒烟 + 检查报告 | 全仓 |

**非目标（明确不做）**
- 前端表单设计器与动态渲染页（本波纯后端引擎）。
- 跨版本聚合查询（如「按某字段查所有版本提交」需 UNION 多版本表）——本文档 §三.3.9 说明语义，实现留待后续波次。
- 字段级唯一约束、字段级 DB `NOT NULL`（必填仅在服务层校验）、外键、字段索引自定义。
- 现有 `form_data.data_json` 的历史数据迁移（保留过渡列，物理表仅承载新提交）。
- 表单与工作流联动、草稿暂存、附件。

---

## 二、现状事实（研究结论，勿重复调研）

- **Wave 5.26 已就绪**：`form_data`（`id/definition_id/definition_code/definition_version/biz_key/data_json/submitter/status` + 公共字段、`idx_definition_id`/`idx_biz_key`）；`FormData`/`FormDataMapper`；`FormDataService(+Impl)`（`submit` 仅校验定义存在 + `dataJson` ≤64KB 后整串落库）；`FormDataController`（`/form/data` 4 接口）；`FormDataVO`；测试 8 用例。
- **模板**：whale-form 现行 `Xxx`(继承 `BaseEntity`) / `XxxMapper`(`BaseMapper`，由 `@MapperScan("com.bluewhale.**.mapper")` 统一扫描) / `XxxService(+Impl)` / `XxxController`(`@RequirePermission`)；`BizException(ErrorCode.PARAM_ERROR/NOT_FOUND)`；`PageQuery`/`PageResult`。
- **依赖**：`mybatis-plus-spring-boot4-starter` 传递 `spring-jdbc`，Spring Boot 4 `JdbcTemplateAutoConfiguration` 自动装配 `JdbcTemplate`（单一 `DataSource` 前提下），本波不新增依赖；Jackson 由 `spring-boot-starter-web` 提供。
- **租户现状**：平台**无租户模型**——仅 `audit_log.tenant_id` 为「多租户扩展预留」列；网关 `WebHeaders` 现注入 `X-Trace-Id`/`X-User-Id`/`X-Username`/`X-User-Roles`/`X-Permission-Codes`，**不注入** `X-Tenant-Id`。
- **权限种子**：当前至 **51**（5.26 补至 51）。

---

## 三、冻结契约（实现前不得更改）

### 3.1 `schema_json` 格式（表单结构定义，冻结）

```json
{
  "fields": [
    { "key": "user_name", "label": "姓名", "type": "text", "required": true, "maxLength": 64 },
    { "key": "age",       "label": "年龄", "type": "number" },
    { "key": "join_date", "label": "入职日期", "type": "date" },
    { "key": "level",     "label": "级别", "type": "select", "options": ["P5", "P6", "P7"] }
  ]
}
```

- 顶层仅 `fields`（数组，至少 1 个元素）；缺失/空数组 → `PARAM_ERROR`。
- 字段属性：`key`（必填）、`type`（必填）、`label`（选填，缺省取 `key`）、`required`（选填，默认 `false`）、`maxLength`（选填，仅 `text` 生效）、`options`（选填，仅 `select` 建议提供）。
- **`key` 规则（冻结）**：匹配 `^[a-z][a-z0-9_]{0,63}$`（小写字母开头，仅小写字母/数字/下划线）；同 schema 内不得重复；不得命中**保留列**：`id, tenant_id, definition_id, definition_version, biz_key, submitter, status, create_time, update_time, deleted`。违规 → `PARAM_ERROR`。
- **`type` 白名单（冻结）**：`text` / `textarea` / `number` / `date` / `datetime` / `select` / `bool`。违规 → `PARAM_ERROR`。

### 3.2 字段类型 → MySQL 列类型映射（冻结）

| type | MySQL 列类型 | 说明 |
|---|---|---|
| text | `VARCHAR(n)` | `n` = `maxLength`（缺省 255，范围 1..2000） |
| textarea | `TEXT` | 长文本 |
| number | `DECIMAL(18,4)` | 整数与小数通用 |
| date | `DATE` | |
| datetime | `DATETIME` | |
| select | `VARCHAR(255)` | 选项值 |
| bool | `TINYINT` | 0/1 |

- 所有动态字段列均为 `DEFAULT NULL`（必填由服务层校验，不做 DB `NOT NULL`，避免部分数据写入失败）。

### 3.3 物理表命名与 DDL（冻结）

- 表名：`` form_data_{code}_v{version} ``（`code` 小写；最终标识符长度 ≤64）。
- **发布前置校验**：`code` 必须匹配 `^[a-z][a-z0-9_]{0,39}$`（总长 ≤40，保证 `form_data_`+`code`+`_v`+版本 ≤64）；否则 `PARAM_ERROR`「表单编码仅允许小写字母、数字、下划线且以字母开头，长度不超过 40」。
- 生成 DDL（`{table}` 与列名一律加反引号；列名序列仅来自 3.1 白名单校验后的 schema）：

```sql
CREATE TABLE IF NOT EXISTS `{table}` (
  `id`                 BIGINT       NOT NULL                   COMMENT '主键（= form_data 主表 id）',
  `tenant_id`          BIGINT       NOT NULL DEFAULT 1         COMMENT '租户ID（行级隔离）',
  `definition_id`      BIGINT       NOT NULL                   COMMENT '表单定义ID',
  `definition_version` INT          NOT NULL                   COMMENT '表单定义版本',
  `biz_key`            VARCHAR(64)  DEFAULT NULL               COMMENT '业务键（可选）',
  `submitter`          VARCHAR(64)  DEFAULT NULL               COMMENT '提交人账号',
  `status`             TINYINT      NOT NULL DEFAULT 1         COMMENT '状态：1 有效 / 0 作废',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            TINYINT      NOT NULL DEFAULT 0         COMMENT '逻辑删除：0 未删除 / 1 已删除',
  {动态字段列……}                                              COMMENT '{label}',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_biz_key` (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单数据物理表 {code} v{version}';
```

### 3.4 `form_table_registry`（物理表登记表，冻结）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| definition_id | BIGINT NOT NULL | 表单定义 ID |
| definition_code | VARCHAR(64) NOT NULL | 表单定义编码 |
| definition_version | INT NOT NULL | 表单定义版本 |
| physical_table | VARCHAR(64) NOT NULL | 物理表名 |
| field_count | INT NOT NULL DEFAULT 0 | 字段数 |
| schema_json | MEDIUMTEXT NULL | **发布时的 schema 快照**（读旧版本数据的列来源） |
| status | TINYINT NOT NULL DEFAULT 1 | 1 生效 / 0 废弃 |
| create_time / update_time / deleted | | 公共字段 |
| KEY `idx_code_version` (`definition_code`, `definition_version`)、KEY `idx_definition_id` (`definition_id`) | | |

> 唯一性：因逻辑删除不建唯一索引，`(definition_code, definition_version)` 唯一性由服务层发布前校验保证（命中已存在 → `PARAM_ERROR`）。

### 3.5 `form_data` 变更（冻结）

- **新增列** `tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（行级隔离）'` + `KEY idx_tenant_id (tenant_id)`。
- **保留** `data_json`（过渡期双写，客户端契约不变；后续波次再评估移除）。

### 3.6 接口（服务前缀 `/form`；网关 `Path=/form/**` 只转发不剥离）

**新增（本文档）**

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| POST | `/form/definition/{id}/publish` | `sys:form:publish` | 发布：解析当前 `schema_json` → 建物理表 → 登记 registry（同 `code+version` 已发布 → `PARAM_ERROR`） |
| GET | `/form/definition/{id}/tables` | `sys:form:list` | 该定义已发布的版本登记列表（按 `definition_version desc`） |

**改造（Wave 5.26 已有接口）**

| 方法 | 路径 | 权限码 | 变更 |
|---|---|---|---|
| POST | `/form/data` | `sys:form:data:submit` | body 不变（`definitionId`/`dataJson`/`bizKey`）。新增：校验当前版本**已发布**（registry 命中）→ 解析 `dataJson` 为对象 → 按 schema 校验（未知字段/必填缺失 → `PARAM_ERROR`）→ 写主表（含 `tenant_id` + `data_json` 过渡）→ 用主表 id 写物理表真实列 |
| GET | `/form/data/page` | `sys:form:data:list` | 主表分页（`tenant_id` 行级过滤 + `definitionCode`/`bizKey`/`submitter` 等值），按 `create_time desc`，单页上限 200；列表项不含结构化 `fields`（降 N+1） |
| GET | `/form/data/{id}` | `sys:form:data:list` | 主表 + 按记录的 `definition_version` 从对应物理表读字段 → `FormDataVO.fields` |
| DELETE | `/form/data/{id}` | `sys:form:data:delete` | 主表逻辑删除 + 物理表 `deleted=1`（同事务） |

- 统一响应体 `Result<T>` / `PageResult<T>`。
- 业务校验：定义不存在/数据不存在 → `NOT_FOUND`；**当前版本未发布** → `PARAM_ERROR`「表单未发布或版本已变更，请先发布」；`dataJson` 非法 JSON → `PARAM_ERROR`；未知字段/必填缺失 → `PARAM_ERROR`；`dataJson` 超 64KB → `PARAM_ERROR`；跨租户访问数据 → `NOT_FOUND`。

### 3.7 权限种子（whale-auth `db/init.sql` 追加，落 `bluewhale_auth.sys_permission`）

| id | perm_code | perm_name | method | path |
|---|---|---|---|---|
| 52 | `sys:form:publish` | 发布表单定义 | POST | `/form/definition/{id}/publish` |

### 3.8 `tenant_id` 取值（冻结）

- 优先读请求头 `X-Tenant-Id`（网关不注入，客户端可选传递）；缺失/空 → 默认租户 `1`；非法（非数字）→ `PARAM_ERROR`。
- 常量 `X-Tenant-Id` 归属 `WebHeaders`（平台共享请求头常量），新增常量 + `tenantId(request)` 取值方法。

### 3.9 SQL 注入防护与 DDL 权限控制（冻结）

- **DDL 仅由** `/form/definition/{id}/publish`（`sys:form:publish`）触发；DDL 文本由服务端依据 3.1 白名单校验后的字段生成。
- **表名**：来源为服务端生成的 `form_data_{code}_v{version}`，执行前二次正则校验 `^form_data_[a-z0-9_]+_v[0-9]+$`，不匹配 → `PARAM_ERROR`。
- **列名**：来源为 schema 解析器白名单（3.1 `key` 规则）校验后的字段，一律加反引号。
- **值**：全部经 JDBC `?` 占位参数化，禁止拼接任何用户输入。
- **版本化语义**：每条数据由主表 `definition_version` 绑定其提交时的物理表；旧版本数据原地保留、不同步；查详情按其 `definition_version` 路由到对应物理表（`schema_json` 快照取自 registry）。跨版本聚合查询需 UNION 多版本表，本波不支持。

### 3.10 状态与流程语义（冻结）

- **发布**：`definition.version` 为发布版本唯一来源；同 `(code, version)` 重复发布 → `PARAM_ERROR`。发布新版本流程 = 先 `PUT /form/definition/{id}` 递增 `version` 并更新 `schema_json` → 再 `POST .../publish`。
- **DDL 隐式提交**：MySQL DDL 自动提交，发布不依赖事务回滚；用 `CREATE TABLE IF NOT EXISTS` 容忍重试（建表后登记失败可能残留空表）。
- **数据状态**：主表/物理表 `status` 默认 1（有效）；删除为逻辑删除（`deleted=1`），双表一致。

---

## 四、新增/修改文件清单（预期）

**新增**
- `entity/FormTableRegistry.java`、`mapper/FormTableRegistryMapper.java`
- `support/FormField.java`（字段定义值对象）、`support/FormSchemaParser.java`（解析 + 白名单校验）
- `support/FormTableDdlGenerator.java`（表名 + 类型映射 + DDL 生成）
- `support/FormDataEngine.java`（接口）+ `support/impl/JdbcFormDataEngine.java`（JdbcTemplate 实现）
- `support/TenantResolver.java`（`X-Tenant-Id` → tenant_id，缺省 1）
- `service/FormPublishService.java` + `service/impl/FormPublishServiceImpl.java`
- `vo/FormTableRegistryVO.java`
- 测试：`support/FormSchemaParserTest`、`support/FormTableDdlGeneratorTest`、`service/impl/FormPublishServiceImplTest`、改造 `service/impl/FormDataServiceImplTest`

**修改**
- `entity/FormData.java`（+`tenantId`）
- `vo/FormDataVO.java`（+`tenantId`、+`fields: Map<String,Object>`）
- `service/FormDataService.java`（接口方法 +`tenantId` 参数）、`service/impl/FormDataServiceImpl.java`（双写改造）
- `controller/FormDataController.java`（解析并传 `tenantId`）、`controller/FormController.java`（+publish/tables）
- `resources/db/init.sql`（`form_data` +`tenant_id`；新增 `form_table_registry`）
- `whale-auth/src/main/resources/db/init.sql`（种子 52）
- `whale-spring-boot-starter` `com/bluewhale/common/web/WebHeaders.java`（+`TENANT_ID` 常量 + `tenantId` 方法）

---

## 五、完成定义（DoD）

- `whale-form` 模块 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时段证据（经网关 8080）：
  1. `POST /form/definition/{id}/publish` → `code=0`，`bluewhale_form` 生成 `form_data_{code}_v{n}` 物理表（`SHOW CREATE TABLE` 列与 schema 一致）+ registry 落库；
  2. `POST /form/data`（`dataJson` 含真实字段值）→ `code=0`，主表 + 物理表均有数据，物理表动态列为**真实列值**；
  3. `GET /form/data/{id}` → `fields` 返回结构化字段；
  4. `GET /form/data/page` → 列表可见；
  5. `DELETE /form/data/{id}` → 主表/物理表 `deleted=1`，详情 `404`；
  6. 负向：未发布版本提交 → `400`；`schema_json` 字段 `key` 非法 → `400`；跨租户访问 → `404`。
- `docs/reviews/2026-10-19-wave5.27-form-dynamic-table-engine.md` + devlog 追加 + roadmap/task-breakdown 勾选 + commit & push origin（Gitee）。

---

## 六、决策记录（2026-10-19，AskUserQuestion 冻结）

| # | 议题 | 结论 |
|---|---|---|
| 1 | 产品形态 | **业务用户自助动态表单**（用户在页面自由设计字段 → 后端建真实列 → 客户端动态渲染） |
| 2 | 多租户 | **是**，物理表/主表**行级 `tenant_id`** |
| 3 | 交付范围 | **纯后端引擎**（前端设计器/渲染后续单独排期） |
| 4 | 现有 `data_json` | **保留过渡**（双写，不迁移历史） |
| 5 | schema 演进 | **版本化建新表** `form_data_{code}_v{version}`（旧数据不同步、原地保留，按版本路由查询） |
| 6 | `tenant_id` 来源 | **`X-Tenant-Id` 请求头 + 缺省回落默认租户 1** |

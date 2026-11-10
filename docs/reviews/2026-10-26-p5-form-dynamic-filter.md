# 检查报告：whale-form 动态字段条件检索（查询侧）

> 日期：2026-10-26 ｜ 服务：`whale-form`（8084）｜ 前端：`whale-form-designer`（8002）｜ 结论：**PASS（单测 + 构建 + 经网关实机验证）**
> 关联：[字段级索引自动优化报告](2026-10-26-p5-form-field-index-auto.md) 第四章遗留项、[todo-inventory 2026-10-23](../plans/archive/todo-inventory-2026-10-23.md) 第 5 项、优先级执行台账序号 27

---

## 一、问题与范围

[字段级索引自动优化](2026-10-26-p5-form-field-index-auto.md) 只补齐了索引地基，列表查询仍只支持 `definitionCode` / `bizKey` / `submitter` 三个公共列等值过滤，业务字段（金额、日期、选项等）无法作为查询条件。本轮补「查询侧」：

- 后端：`GET /form/data/page`、`GET /form/data/export` 接受 `filters` JSON 数组，按表单 schema 动态字段过滤；
- 前端：`whale-form-designer` 数据列表页新增筛选构建区（字段 + 操作符 + 值），条件随分页/导出请求下发。

不涉及：物理表结构变更、索引规则调整、字段级排序（`orderBy`）、聚合统计。

## 二、契约与规则

### 2.1 请求契约

`filters` 为 JSON 数组文本（查询参数，需 URL 编码），仅在指定 `definitionCode` 时生效：

```json
[
  { "field": "amount", "op": "gte", "value": 100 },
  { "field": "stage", "op": "eq", "value": "new" }
]
```

| 字段 | 说明 |
|---|---|
| `field` | 列编码；字符白名单 `^[a-z][a-z0-9_]{0,63}$`，且必须命中该表单 `schema_json` 字段 |
| `op` | 操作符；缺省 `eq`，大小写不敏感（`ISNULL` / `isnull` 均归一为 `isNull`） |
| `value` | 条件值；`isNull` / `notNull` 不需要；`in` / `between` 为数组 |

限制：条件个数 ≤ **10**、JSON 文本 ≤ **4096** 字符、`in` 值数量 1..**50**、`between` 必须恰好 2 个值。

### 2.2 类型-操作符矩阵

| 存储类型 | 允许操作符（除下列外的 `isNull` / `notNull` 对所有可过滤类型开放） |
|---|---|
| `number` / `date` / `datetime` | `eq` `ne` `gt` `gte` `lt` `lte` `in` `between` |
| `text` / `textarea` / `select` | `eq` `ne` `like` `in` |
| `bool` | `eq` `ne` `in` |
| `attachment` / `subtable` | 不可过滤（值为 JSON 文本，`FormDataType.isFilterable` 拒绝） |

值转换：`number` → `BigDecimal`；`bool` → `0/1`（接受 `true/false`、`1/0`）；`date` → `yyyy-MM-dd`；`datetime` → `yyyy-MM-dd HH:mm[:ss]`，格式不符即参数错误。

## 三、实现与变更点

| 文件 | 变更 |
|---|---|
| `dto/FormDataFilter.java`（新增） | 过滤条件契约（`field` / `op` / `value`） |
| `dto/FormDataQueryRequest.java` | 新增 `filters` |
| `support/FormDataType.java` | 新增 `FILTERABLE` 白名单与 `isFilterable` |
| `support/FormDataEngine.java` | 新增 `countByFilters` / `selectIdsByFilters` |
| `support/impl/JdbcFormDataEngine.java` | 动态 WHERE 构造、操作符归一化与类型-操作符校验、值转换、LIKE 转义、全参数化绑定 |
| `service/impl/FormDataServiceImpl.java` | `page` / `export` 接入 `filters`；多版本物理表合并、主表回捞排序、条件解析与字段校验 |
| 前端 `api/form.ts` | 新增 `FilterOp` / `DataFilter` 类型 |
| 前端 `pages/data/FormDataList.tsx` | 筛选构建区、可筛选字段加载、条件序列化、分页/导出透传 |

### 3.1 查询链路

1. `parseFilters`：JSON 解析 → 长度/个数/字段名校验；
2. 非空时要求 `definitionCode`，`requireDefinitionByCode` 取该编码最大版本定义；
3. `assertFilterFieldsDefined`：字段必须存在于当前 `schema_json` 且类型可过滤；
4. `listFilterTables`：按表定义（元数据模型）或表单编码（旧模型）取 `status=1` 的物理表登记；
5. 逐表 `fieldsOf` 取该版本 schema 快照，**缺任一过滤字段的版本直接跳过**；`countByFilters` 累加命中总数，`selectIdsByFilters` 取前 `min(offset+limit, 5000)` 条 ID；
6. 合并 ID → 主表 `selectBatchIds` 装载 → 按 `create_time desc, id desc` 排序 → 取当页窗口。

### 3.2 安全设计

- 物理表名二次正则 `^form_data_[a-z0-9_]+_v[0-9]+$`；
- 列名只取自 schema 解析结果的 key（服务层 + 引擎层双重白名单），拼接前用反引号包裹；
- 操作符枚举白名单，SQL 比较符由枚举映射，不接受原始字符串拼接；
- 条件值一律 `?` 占位绑定；`like` 对 `\`、`%`、`_` 转义后再包 `%`；
- 非法字段、非法操作符、类型不匹配、值格式非法均返回 `PARAM_ERROR`（400），不泄露 SQL。

### 3.3 前端交互

- 选择表单编码后按最新定义加载可筛选字段（`dataType` 命中 `FILTERABLE_TYPES`）；
- 操作符按字段类型联动；`number` 用 `InputNumber`、`bool` 用下拉、其余用文本输入；
- 条件以 Tag 展示可逐个移除；变更表单编码或重置时清空动态条件；
- 分页与导出（CSV/xlsx）均携带 `filters`。

## 四、验证

### 4.1 单元测试与构建

- `mvn -o -f whale-backend/pom.xml -pl :whale-form test`：**113 用例、0 失败**（较上轮 103 新增 10 个）。
  - `JdbcFormDataEngineFilterTest`（新增 6）：参数化 SQL 与参数顺序、`selectIdsByFilters` limit、操作符大小写归一、未知字段防注入、类型-操作符不匹配、数值非法。
  - `FormDataServiceImplTest`（新增 4）：动态条件分页排序、字段不存在、未指定编码、非法 JSON。
- `mvn -o -f whale-backend/pom.xml -pl :whale-form checkstyle:check`：**0 违规**。
- `mvn -o -f whale-backend/pom.xml -pl :whale-form package`：停掉占用 jar 的 8084 进程后 **BUILD SUCCESS**。
- 前端：`pnpm --filter whale-form-designer build` 与 `lint` 均通过。

### 4.2 实机验证（重启 8084 新 jar，经网关 8080）

环境：MySQL 8.0.26 / Redis / Nacos；探针定义 `idx_filter_probe`（`amount` number、`stage` select、`memo` text(200)、`due_date` date），写入 4 条数据。

| 用例 | 结果 |
|---|---|
| `amount gte 100` | 命中 3 条 |
| `stage eq new` | 命中 2 条 |
| `amount gte 100 AND stage eq new` | 命中 1 条 |
| `memo like bet` | 命中 1 条（含通配符转义路径） |
| `amount between 60..210` | 命中 3 条 |
| `memo isNull` | 命中 1 条 |
| `memo notNull` | 命中 3 条 |
| 分页 `size=1&current=2` | 返回 1 条，为 `amount=200`（时间倒序第 2 条） |
| 导出 CSV（`amount gte 100`） | 表头 + 3 行（150/200/100，按时间倒序） |

错误提示（均为 400 信封，无 SQL 泄漏）：

| 场景 | 提示 |
|---|---|
| 未知字段 `secret` | 过滤字段不在当前表单结构定义中：secret |
| `text` 字段用 `gt` | 字段 备注 不支持操作符 gt |
| `number` 字段值 `abc` | 字段 金额 的数值格式非法：abc |
| 未指定 `definitionCode` | 动态字段条件检索需先指定表单编码 |

> 实机曾暴露 `isNull` / `notNull` 因 `toLowerCase()` 归一为 `isnull` 而报「不支持的条件操作符」，已改为遍历白名单 `equalsIgnoreCase` 返回规范写法，并补单测锁定。

## 五、遗留与残余

- **`in` / `between` 前端 UI 已补齐**（同日增量）：操作符矩阵按类型暴露，`in` 支持布尔/选项多选与文本标签输入，`between` 支持数值区间与日期文本区间，Playwright 端到端通过，见 [UI 暴露报告](2026-10-26-p5-form-dynamic-filter-ui.md)。
- **深分页上限 5000**：多版本物理表合并时各表最多回捞 5000 条并在内存排序，超过即报错；数据量增长后需改为按时间窗口/游标分页。
- **多版本合并排序在内存完成**：当前以 `offset+limit` 截断后合并，版本数量多时命中集可能被单表上限截断（极端场景下当页可能少于预期）。
- **仅支持 AND 组合**：无 OR / 分组 / 嵌套条件。
- **索引依赖发布**：过滤字段若未命中 `idx_f_` 索引（长文本、bool 等）仍走全表扫描，索引规则见上轮报告。
- **探针数据未清理**：`idx_probe`（表定义 + 物理表）、`idx_filter_probe`（定义 + 4 条数据 + 物理表）保留在开发库 `bluewhale_form`；删除物理表属破坏性 DDL，未执行。

# 检查报告：whale-form 字段级索引自动优化

> 日期：2026-10-26 ｜ 服务：`whale-form`（8084）｜ 结论：**PASS（单测 + 建表/存量对齐双路径实机验证）**
> 关联：[todo-inventory 2026-10-23](../plans/archive/todo-inventory-2026-10-23.md) 第 5 项残余（Wave 5.27 起遗留）、优先级执行台账序号 25

---

## 一、问题与范围

动态表单的物理表 `form_data_{code}_v{version}` 此前只有 `PRIMARY KEY(id)`、`idx_tenant_id`、`idx_biz_key` 三个索引，业务字段（金额、日期、编码、选项等）全部无索引。元数据模型（Wave 5.28）虽然支持动态列，但发布时只做列结构对齐（ADD / MODIFY / DROP COLUMN），从不管理索引；按动态字段过滤时会退化为全表扫描。

本轮补「字段级索引自动优化」：建表时按列类型自动生成索引，发布到**已存在**物理表时幂等对齐（补缺 + 回收）。不做查询条件改造、不做复合索引、不改 `schema_json` / `FormMetadataCompiler`。

## 二、规则与实现

### 2.1 索引规则

| 存储类型 | 是否建索引 | 说明 |
|---|---|---|
| `number` / `date` / `datetime` / `select` | 是 | 高选择性、常用于过滤/排序 |
| `text` | 长度 ≤ **191** 时建 | utf8mb4 下 191×4=764 字节，兼容 767 字节索引前缀限制 |
| `text`（>191）/ `textarea` / `attachment` / `subtable` | 否 | 长文本、JSON 文本不适合全列索引 |
| `bool` | 否 | 低区分度，索引收益低于写放大 |

- 索引名固定 `idx_f_<列编码>`，列编码过长导致索引名超 64 字符时**跳过该列**（不截断，避免歧义）。
- 单表自动索引上限 **16** 个（按列顺序截断），控制写放大与元数据开销。
- 只管理 `idx_f_` 前缀的索引，公共索引（`PRIMARY` / `idx_tenant_id` / `idx_biz_key`）与人工索引不动。

### 2.2 变更点

| 文件 | 变更 |
|---|---|
| `support/FormTableDdlGenerator.java` | 新增 `AUTO_INDEX_PREFIX` / `MAX_AUTO_INDEXES` / `MAX_INDEXED_TEXT_LENGTH` 常量与 `isIndexable`、`autoIndexName`、`autoIndexDef`、`autoIndexes`、`autoIndexDefsForFields`、`alterAddIndex`、`alterDropIndex`；`generate`（旧模型）与 `generateFromColumns`（元数据模型）在建表 DDL 末尾追加自动索引定义 |
| `support/FormDataEngine.java` | 新增 `Set<String> describeIndexes(String table)`，供发布时对齐索引 |
| `support/impl/JdbcFormDataEngine.java` | 实现 `describeIndexes`：查 `information_schema.STATISTICS`，表名仍走二次白名单正则 |
| `service/impl/FormPublishServiceImpl.java` | `ensurePublished` 在**已存在物理表**分支（列结构对齐之后）调用 `syncIndexes`：按目标列元数据补齐缺失索引、回收不再合格的 `idx_f_` 索引；新建表路径索引已随 DDL 生成，不重复对齐 |

设计取舍：

- **对齐只发生在发布路径**，与既有「列变更只在发布时落物理表」口径一致；不在运行时或每次写入时校验索引。
- **回收仅限 `idx_f_` 前缀**，且仅在列已被删除或类型不再满足索引条件时触发；列被 DROP 时 MySQL 会自动删索引，回收逻辑作为兜底。
- **不做后台定时任务 / 不做异步 DDL**：索引变更随发布同步执行，避免引入新的调度与一致性面。

## 三、验证

### 3.1 单元测试

- `mvn -o -f whale-backend/pom.xml -pl :whale-form test`：**103 用例、0 失败**。
  - `FormTableDdlGeneratorTest` 14 用例（新增 4：建表 DDL 自动索引、上限 16 截断、类型过滤、ADD/DROP INDEX 语句与非法前缀拒绝）。
  - `FormPublishServiceImplTest` 16 用例（新增 3：存量表缺索引 → ADD INDEX、类型降级 → 回收索引、索引已对齐 → 不下发 DDL）。
- `mvn -o -f whale-backend/pom.xml -pl :whale-form checkstyle:check`：**0 违规**。
- `mvn -o -f whale-backend/pom.xml -pl :whale-form package -DskipTests`：**BUILD SUCCESS**。

### 3.2 实机验证（重启 8084 新 jar，经网关 8080）

环境：MySQL 8.0.26 / Redis / Nacos；`whale-form` 8084 以新 jar 重启，`/actuator/health` `UP`。

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | `POST /form/table-definition` 建探针表定义（`idx_probe`：`amount` number、`code` text(64)、`memo` text(400)、`enabled` bool、`note` textarea） | `code=0` |
| 2 | `POST /form/table-definition/{id}/publish` 建物理表 `form_data_idx_probe_v1` | `code=0` |
| 3 | 查 `information_schema.statistics` | 生成 `idx_f_amount`、`idx_f_code`；`memo`（400）、`enabled`（bool）、`note`（textarea）**未建** |
| 4 | `PUT` 表定义：`code` 由 text(64) 改 text(400)，新增 `stage`（select），再 publish | `code=0` |
| 5 | 复查索引 | `idx_f_code` **已回收**；`idx_f_stage` **已补建**；`idx_f_amount` 保留 |

即：建表路径（DDL 内联索引）、存量对齐路径（ADD INDEX）、回收路径（DROP INDEX）三条链路均实机生效。

## 四、遗留与残余

- **无索引字段级查询改造**：分页查询仍只按 `definitionCode` / `bizKey` / `submitter` 过滤，动态字段条件检索未实现；本轮只把索引地基铺好，查询侧改造需与前端列表筛选一起排期。
- **不建复合索引 / 唯一索引 / 前缀索引**：复合条件检索出现后再评估，避免提前设计。
- **`bool` 不建索引**、**text >191 不建索引**为明确取舍，若后续出现高频等值查询再调整阈值。
- 探针表 `form_data_idx_probe_v1`（表定义 `idx_probe`）为本次实机验证产物，保留在开发库 `bluewhale_form`，未删除（删除物理表属破坏性 DDL）。
- 存量历史物理表只有在**再次发布**时才会补齐索引；如需批量补齐，可通过逐个重新发布表定义触发，不新增后台任务。

# agent 服务数据库脚本目录说明（REQ-SEC-022）

## 目录约定

| 路径 | 用途 | 执行方式 |
|---|---|---|
| `db/migration/` | **Flyway 版本化迁移**（`V1__baseline.sql`，10 张表） | agent 服务启动时由 Flyway 自动执行，记录于 `flyway_schema_history` |
| `db/init.sql` | 全新建库的手工初始化脚本（与 `db/migration/V1` 同源，含 `DROP TABLE`） | 仅用于本地/一次性空库手工执行；**不要**在存量库重放 |
| `db/migration-5.47.sql` / `migration-5.53.sql` / `migration-5.54-tenant-business.sql` / `migration-5.75-agent-doc.sql` | 历史 Wave 增量脚本（索引元数据、租户字段、知识文档表等） | 手工按序执行，仅供追溯，**不参与 Flyway 扫描** |
| `db/data-query-ro.sql` | 数据查询只读账号授权脚本（**非表结构**，需人工执行） | 由 DBA 在目标实例手工执行，不纳入 Flyway |

Flyway 只扫描 `classpath:db/migration`（见 `application.yml` 的 `spring.flyway.locations`），
因此 `db/` 根目录下的历史脚本与授权脚本不会被自动执行。

## 存量库与空库的行为

1. **空库**：Flyway 执行 `V1`，一次建齐 `agent_fact`、`agent_conversation`、`agent_message`、
   `agent_feedback`、`agent_operation`、`agent_index_meta`、`audit_log`、`agent_knowledge_file`、
   `agent_knowledge_doc`、`api_log`。
2. **存量库**（已有事实/知识库表但无 `flyway_schema_history`）：`baseline-on-migrate=true` +
   `baseline-version=1` 先写入 version=1 基线记录，**不重放 V1**，既有知识库与索引数据不受影响；
   后续增量从 V2 开始。
3. **已接入 Flyway 的库**：按版本增量执行，`validate-on-migrate=true` 校验脚本 checksum；
   修改已执行脚本会启动失败（需新增版本，不得改历史版本）。

## 后续增量约定

- 向量/全文索引的物理存储由应用层与外部索引组件管理，不在本迁移脚本内建表。
- 若后续新增事实表/知识库表/列/索引，一律新增 `V2__*.sql` 及以上版本，并同步 `db/init.sql`，
  保持 `V1` 与 `init.sql` 表集合一致（单测门禁校验）。

## 常用校验

```sql
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name;
SELECT index_status, COUNT(*) FROM agent_knowledge_file GROUP BY index_status;
SELECT fact_type, COUNT(*) FROM agent_fact GROUP BY fact_type;
```

## 运维开关

- `WHALE_FLYWAY_ENABLED=false`：应急临时关闭自动迁移（须先确认 schema 与脚本一致）。
- `WHALE_FLYWAY_BASELINE_ON_MIGRATE=false`：仅用于已确认基线已手工登记的场景。
- `clean` 已禁用（`spring.flyway.clean-disabled=true`），不允许通过 Flyway 清库。

# workflow 服务数据库脚本目录说明（REQ-SEC-022）

## 目录约定

| 路径 | 用途 | 执行方式 |
|---|---|---|
| `db/migration/` | **Flyway 版本化迁移**（`V1__baseline.sql` 5 张表 + `V2__task_operator.sql` 任务操作来源列） | workflow 服务启动时由 Flyway 自动执行，记录于 `flyway_schema_history` |
| `db/init.sql` | 全新建库的手工初始化脚本（与 `V1` + `V2` 后的最终结构一致，含 `DROP TABLE`） | 仅用于本地/一次性空库手工执行；**不要**在存量库重放 |
| `db/migration-5.31.sql` ~ `migration-5.37.sql` / `migration-5.54-tenant-business.sql` / `migration-5.63-workflow-task-operator.sql` | 历史 Wave 增量脚本 | 手工按序执行，仅供追溯，**不参与 Flyway 扫描** |

Flyway 只扫描 `classpath:db/migration`（见 `application.yml` 的 `spring.flyway.locations`），
因此 `db/` 根目录下的历史脚本不会被自动执行。

## 存量库与空库的行为

1. **空库**：Flyway 依次执行 `V1`、`V2`，建齐 `process_definition`、`process_definition_version`、
   `process_instance`、`process_task`、`api_log`，并在 `process_task` 上补齐 `operator_id`、
   `operator_type`、`operator_source`。
2. **存量库**（已有流程表但无 `flyway_schema_history`）：`baseline-on-migrate=true` +
   `baseline-version=1` 先写入 version=1 基线记录，**不重放 V1**，既有流程数据不受影响；
   随后执行 `V2`（`information_schema` 守卫，列已存在时为空操作）。
3. **已接入 Flyway 的库**：按版本增量执行，`validate-on-migrate=true` 校验脚本 checksum；
   修改已执行脚本会启动失败（需新增版本，不得改历史版本）。

## 历史脚本折叠关系

- `migration-5.31` ~ `migration-5.54-tenant-business` 的表结构变更已折叠进 `init.sql` 与 `V1`。
- `migration-5.63-workflow-task-operator.sql` 收敛为 `V2__task_operator.sql`；
  `init.sql` 已同步补列，保证「手工初始化」与「Flyway 迁移」最终结构一致（单测门禁校验）。
- 若后续新增表/列/索引，一律新增 `V3__*.sql` 及以上版本，并同步 `db/init.sql`。

## 常用校验

```sql
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name;
SELECT column_name FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'process_task' AND column_name LIKE 'operator%';
```

## 运维开关

- `WHALE_FLYWAY_ENABLED=false`：应急临时关闭自动迁移（须先确认 schema 与脚本一致）。
- `WHALE_FLYWAY_BASELINE_ON_MIGRATE=false`：仅用于已确认基线已手工登记的场景。
- `clean` 已禁用（`spring.flyway.clean-disabled=true`），不允许通过 Flyway 清库。

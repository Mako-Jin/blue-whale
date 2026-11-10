# config 服务数据库脚本目录说明（REQ-SEC-022）

## 目录约定

| 路径 | 用途 | 执行方式 |
|---|---|---|
| `db/migration/` | **Flyway 版本化迁移**（`V1__baseline.sql`，4 张表） | config 服务启动时由 Flyway 自动执行，记录于 `flyway_schema_history` |
| `db/init.sql` | 全新建库的手工初始化脚本（与 `db/migration/V1` 同源） | 仅用于本地/一次性空库手工执行；**不要**在存量库重放 |

本服务 `db/` 下没有历史 `migration-*.sql`，`init.sql` 本身已使用 `CREATE TABLE IF NOT EXISTS`。
Flyway 只扫描 `classpath:db/migration`（见 `application.yml` 的 `spring.flyway.locations`）。

## 存量库与空库的行为

1. **空库**：Flyway 执行 `V1`，一次建齐 `config_item`、`config_item_history`、`audit_log`、`api_log`。
2. **存量库**（无 `flyway_schema_history`）：`baseline-on-migrate=true` + `baseline-version=1`
   写入 version=1 基线记录，**不重放 V1**、不改动既有配置数据；后续增量从 V2 开始。
3. **已接入 Flyway 的库**：按版本增量执行，`validate-on-migrate=true` 校验 checksum；
   修改已执行脚本会启动失败（需新增版本）。

## 后续增量约定

- `config_item_history` 为只追加表，任何变更必须新增 `V2__*.sql` 及以上版本，**不得**改写 `V1`。
- 新增表/列时同步更新 `db/init.sql`，并保持 `V1` 与 `init.sql` 表集合一致（单测门禁）。

## 常用校验

```sql
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name;
SELECT COUNT(*) FROM config_item;
```

## 运维开关

- `WHALE_FLYWAY_ENABLED=false`：应急临时关闭自动迁移（须先确认 schema 与脚本一致）。
- `WHALE_FLYWAY_BASELINE_ON_MIGRATE=false`：仅用于已确认基线已手工登记的场景。
- `clean` 已禁用（`spring.flyway.clean-disabled=true`），不允许通过 Flyway 清库。

# audit 服务数据库脚本目录说明（REQ-SEC-022）

## 目录约定

| 路径 | 用途 | 执行方式 |
|---|---|---|
| `db/migration/` | **Flyway 版本化迁移**（`V1__baseline.sql`，7 张 append-only 日志/审计表） | audit 服务启动时由 Flyway 自动执行，记录于 `flyway_schema_history` |
| `db/init.sql` | 全新建库的手工初始化脚本（与 `db/migration/V1` 同源，含 `DROP TABLE`） | 仅用于本地/一次性空库手工执行；**不要**在存量库重放 |
| `db/migration-5.48.sql` / `migration-5.54-tenant-logs.sql` / `migration-auth10.sql` | 历史 Wave 增量脚本（归档水位表、日志表 `tenant_id`、MQ 去重表）；三者内容已折叠进 `init.sql` | 手工按序执行，仅供追溯，**不参与 Flyway 扫描** |

Flyway 只扫描 `classpath:db/migration`（见 `application.yml` 的 `spring.flyway.locations`），
因此 `db/` 根目录下的历史脚本不会被自动执行。

## 存量库与空库的行为

1. **空库**：Flyway 执行 `V1`，一次建齐 `audit_log`、`audit_archive_watermark`、`api_log`、
   `outbound_log`、`frontend_log`、`behavior_log`、`audit_mq_message` 共 7 张表。
2. **存量库**（已有日志表但无 `flyway_schema_history`）：
   `baseline-on-migrate=true` + `baseline-version=1` 先写入 version=1 基线记录，
   **不重放 V1**，不触碰任何既有日志数据；后续增量从 V2 开始。
3. **已接入 Flyway 的库**：按版本增量执行，`validate-on-migrate=true` 校验脚本 checksum；
   修改已执行脚本会启动失败（需新增版本，不得改历史版本）。

> 注意：`audit_log` 等表为 append-only，本库**不允许**任何 `DROP`/`TRUNCATE`/`DELETE`
> 类迁移；数据清理统一走 `whale.audit.retention` 保留期/归档任务（默认关闭）。

## 后续折叠（阶段 4 待办）

- ~~`db/migration-5.54-tenant-logs.sql` 声明「`bluewhale_audit` / `bluewhale_auth` 两库同构」~~
  **已失效（2026-11-13）**：auth 侧日志表已由 auth 的 `V25__drop_local_log_tables.sql` 全部删除，
  审计/观测日志统一汇聚本库（`whale.audit.forward` + whale-audit 多实例兜底），
  两库不再同构；本库的 7 张表是唯一的日志落库点。
- 若后续新增日志表/索引，一律新增 `V2__*.sql` 及以上版本，并把 `init.sql` 同步为新空库入口。

## 常用校验

```sql
SELECT installed_rank, version, description, success, execution_time
FROM flyway_schema_history ORDER BY installed_rank;

SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() ORDER BY table_name;

SHOW COLUMNS FROM api_log LIKE 'tenant_id';
```

## 运维开关

- `WHALE_FLYWAY_ENABLED=false`：应急临时关闭自动迁移（须先确认 schema 与脚本一致）。
- `WHALE_FLYWAY_BASELINE_ON_MIGRATE=false`：仅用于已确认基线已手工登记的场景。
- `clean` 已禁用（`spring.flyway.clean-disabled=true`），不允许通过 Flyway 清库。

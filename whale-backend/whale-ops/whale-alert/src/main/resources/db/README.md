# alert 服务数据库脚本目录说明（REQ-SEC-022）

## 目录约定

| 路径 | 用途 | 执行方式 |
|---|---|---|
| `db/migration/` | **Flyway 版本化迁移**（`V1__baseline.sql`，3 张表 + 2 条种子规则） | alert 服务启动时由 Flyway 自动执行，记录于 `flyway_schema_history` |
| `db/init.sql` | 全新建库的手工初始化脚本（与 `db/migration/V1` 同源，含 `DROP TABLE`） | 仅用于本地/一次性空库手工执行；**不要**在存量库重放 |
| `db/migration-5.54-tenant-business.sql` / `migration-5.68-alert-record-fingerprint.sql` | 历史增量（租户列/唯一键、告警指纹列与索引），内容已折叠进 `init.sql` | 手工按序执行，仅供追溯，**不参与 Flyway 扫描** |

Flyway 只扫描 `classpath:db/migration`（见 `application.yml` 的 `spring.flyway.locations`），
因此 `db/` 根目录下的历史脚本不会被自动执行。

## 存量库与空库的行为

1. **空库**：Flyway 执行 `V1`，建齐 `alert_rule`、`alert_record`、`api_log`，并写入 2 条内置规则种子
   （`INSERT IGNORE`，重复执行不产生重复行）。
2. **存量库**（无 `flyway_schema_history`）：`baseline-on-migrate=true` + `baseline-version=1`
   写入 version=1 基线记录，**不重放 V1**，既有规则/记录数据无损；后续增量从 V2 开始。
3. **已接入 Flyway 的库**：按版本增量执行，`validate-on-migrate=true` 校验 checksum；
   修改已执行脚本会启动失败（需新增版本）。

> 历史脚本 `migration-5.54-tenant-business.sql` 含 `DROP PROCEDURE` / `DROP INDEX` 等过程式语句，
> 这也是它们**不进入** `db/migration` 的原因之一（Flyway 迁移禁止破坏性语句，由单测门禁拦截）。

## 后续增量约定

- 新增告警级别、通知通道等枚举值属于代码变更，不需要迁移；
  新增列/索引/表一律新增 `V2__*.sql` 及以上版本。
- 新增表/列时同步更新 `db/init.sql`，并保持 `V1` 与 `init.sql` 表集合一致（单测门禁）。

## 常用校验

```sql
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, rule_code, status FROM alert_rule ORDER BY id;
SHOW COLUMNS FROM alert_record LIKE 'fingerprint';
```

## 运维开关

- `WHALE_FLYWAY_ENABLED=false`：应急临时关闭自动迁移（须先确认 schema 与脚本一致）。
- `WHALE_FLYWAY_BASELINE_ON_MIGRATE=false`：仅用于已确认基线已手工登记的场景。
- `clean` 已禁用（`spring.flyway.clean-disabled=true`），不允许通过 Flyway 清库。

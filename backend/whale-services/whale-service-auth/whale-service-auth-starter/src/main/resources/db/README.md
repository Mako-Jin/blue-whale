# auth 服务数据库脚本目录说明（REQ-SEC-022）

## 目录约定

| 路径 | 用途 | 执行方式 |
|---|---|---|
| `db/migration/` | **Flyway 版本化迁移**（`V1__baseline.sql`、`V2__password_lifecycle.sql`、`V3__tenant_password_policy.sql`） | auth 服务启动时由 Flyway 自动执行，记录于 `flyway_schema_history` |
| `db/init.sql` | 全新建库的手工初始化脚本（与 `db/migration` 基线同源，含 `DROP TABLE`） | 仅用于本地/一次性空库手工执行；**不要**在存量库重放 |
| `db/migration-*.sql` | 历史 Wave 增量脚本（5.34–5.76 等） | 手工按序执行，仅供追溯，**不参与 Flyway 扫描** |

Flyway 只扫描 `classpath:db/migration`（见 `application.yml` 的 `spring.flyway.locations`），
因此 `db/` 根目录下的历史脚本不会被自动执行。

## 存量库与空库的行为

1. **空库**：Flyway 依次执行 `V1 → V2 → V3`，得到完整核心表结构与种子（平台租户、admin、
   super_admin、系统菜单与权限 1–280/172/173）。
2. **存量库**（已有 `sys_user` 等表但无 `flyway_schema_history`）：
   `baseline-on-migrate=true` + `baseline-version=1` 会先写入一条 version=1 的基线记录，
   **不重放 V1**，随后仅执行 `V2`、`V3`；两者均为幂等/守卫式脚本，不覆盖既有数据。
3. **已接入 Flyway 的库**：按版本增量执行，`validate-on-migrate=true` 校验脚本 checksum；
   修改已执行脚本会启动失败（需新增版本，不得改历史版本）。

## 后续折叠（阶段 4 待办）

历史迁移中的**其他 Wave 模块权限/菜单种子**（音乐 281–303 / 菜单 39–44、文档、OA、上诉等）
尚未折叠进 `V1__baseline.sql`。影响面：

- 空库由 Flyway 建库后，登录与本系统 RBAC（菜单 1–17、36 + 权限 1–280/172/173）可正常工作；
- 但音乐/文档等模块的权限与菜单需要按各自 `migration-*.sql` 手工补齐，否则前端菜单不出现。

阶段 4 铺开时按服务把各模块种子折叠为 `R__<module>_seed.sql`（可重复执行）或并入对应
`V1` 基线，并在 CI 增加「`init.sql` vs `db/migration` 表/种子一致性」校验。

## 常用校验

```sql
SELECT installed_rank, version, description, success, execution_time
FROM flyway_schema_history ORDER BY installed_rank;

SELECT id, perm_code FROM sys_permission WHERE id IN (172, 173);
SELECT id, tenant_id, path, permission_code FROM sys_menu WHERE path = '/system/password-policy';
SHOW COLUMNS FROM sys_user LIKE 'password_%';
```

## 运维开关

- `WHALE_FLYWAY_ENABLED=false`：应急临时关闭自动迁移（须先确认 schema 与脚本一致）。
- `WHALE_FLYWAY_BASELINE_ON_MIGRATE=false`：仅用于已确认基线已手工登记的场景。
- `clean` 已禁用（`spring.flyway.clean-disabled=true`），不允许通过 Flyway 清库。

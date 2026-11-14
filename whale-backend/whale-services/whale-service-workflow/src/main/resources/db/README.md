# workflow 服务数据库脚本目录说明（REQ-SEC-022）

## 目录约定

| 路径 | 用途 | 执行方式 |
|---|---|---|
| `db/migration/` | **Flyway 版本化迁移**（`V1` 基线 5 张表 + `V2`~`V11` 增量，最终 9 张表） | workflow 服务启动时由 Flyway 自动执行，记录于 `flyway_schema_history` |
| `db/init.sql` | 全新建库的手工初始化脚本（与 `V1`~`V11` 迁移后的**最终结构一致**，含 `DROP TABLE`） | 仅用于本地/一次性空库手工执行；**不要**在存量库重放 |
| `db/migration-5.31.sql` ~ `migration-5.37.sql` / `migration-5.54-tenant-business.sql` / `migration-5.63-workflow-task-operator.sql` | 历史 Wave 增量脚本 | 手工按序执行，仅供追溯，**不参与 Flyway 扫描** |

Flyway 只扫描 `classpath:db/migration`（见 `application.yml` 的 `spring.flyway.locations`），
因此 `db/` 根目录下的历史脚本不会被自动执行。

## 存量库与空库的行为

1. **空库**：Flyway 依次执行 `V1`~`V11`，建齐 9 张表：
   `process_definition`、`process_definition_version`、`process_instance`、`process_task`、`api_log`、
   `process_cc`、`process_delegate`、`process_urge`、`audit_log`，并补齐
   `operator_*`、`assignee_overrides_json`、`window_bucket`、`active_code`、`finished_at` 等增量结构。
2. **存量库**（已有流程表但无 `flyway_schema_history`）：`baseline-on-migrate=true` +
   `baseline-version=1` 先写入 version=1 基线记录，**不重放 V1**。
   注意：该模式下 V1 的建表/补列会被整体跳过，若存量库缺少 `tenant_id` 等基础列（例如从 5.31~5.54
   之前的版本升级而来），后续 `V3`/`V4` 会因缺列失败。接入 Flyway 前必须先按 `migration-*.sql`
   手工补齐基础结构，或改用 `WHALE_FLYWAY_BASELINE_ON_MIGRATE=false` 并在确认结构后手工登记基线。
3. **已接入 Flyway 的库**：按版本增量执行，`validate-on-migrate=true` 校验脚本 checksum；
   修改已执行脚本会启动失败（需新增版本，不得改历史版本）。

## 历史脚本折叠关系

- `migration-5.31` ~ `migration-5.54-tenant-business` 的表结构变更已折叠进 `init.sql` 与 `V1`。
- `migration-5.63-workflow-task-operator.sql` 收敛为 `V2__task_operator.sql`；
  `init.sql` 已同步全部增量（`V2`~`V11`），保证「手工初始化」与「Flyway 迁移」最终结构一致（单测门禁校验）。
- 若后续新增表/列/索引，一律新增 `V12__*.sql` 及以上版本，并同步 `db/init.sql`。

## 种子数据约定

- `V4`~`V6` 种子的 `OA_LEAVE` / `OA_OVERTIME` / `OA_TRIP` 节点 `assignee` 为占位账号
  （`dept_manager` / `hr`）。发起 OA 流程时**必须**通过 `assigneeOverrides` 覆盖为真实账号，
  否则待办会落到不存在的账号上导致流程卡死；`ProcessModelParser` 要求 `USER_TASK` 必须有
  `assignee`，因此不能留空。
- 种子主键固定在 `9000000000000001xx` 段，低于当前雪花算法的时间戳段（约 `1.7e18`），
  不会与业务新增 ID 冲突；如需扩充种子请沿用该段并保持不重复。
- 发起实例要求定义存在「已发布」版本快照（`process_definition_version.publish_status=1`）；
  无已发布快照默认拒绝发起，存量定义确需继续运行时可设置
  `bluewhale.workflow.allow-unpublished-start=true`（会按定义当前内容发起并打 `warn`）。

## 历史脚本注释口径

已执行的迁移脚本不可修改（`validate-on-migrate` 校验 checksum），下列历史注释以最新版本为准：

- `V1__baseline.sql` 的 `process_definition.code` 注释仍写「逻辑删除冲突由服务层前置校验」；
  最终唯一性由 `V9` 的 `active_code` 生成列 + `uk_tenant_active_code` 唯一键兜底。
- `V3__cc_delegate_urge.sql` 的 `process_cc.recipient` 注释为「账号或用户ID文本」；
  实际口径由 `V11` 统一为**账号**（发起级与节点级一致）。
- `V8__urge_frequency_window.sql` 的 `window_bucket` 允许 `NULL`：该值为存量去重残留，
  服务端写入恒非空，唯一键 `uk_urge_window` 对新增记录生效。

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

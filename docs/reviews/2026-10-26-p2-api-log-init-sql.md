# P2 api_log 建表纳入各服务 init.sql（2026-10-26）

> 来源：[2026-10-25 Wave 5.55 部署验证](2026-10-25-wave5.55-deployment-verification.md) 遗留第 3 条：「`api_log` 建表未纳入 `whale-im` 等服务的 `init.sql`，本轮手工补建，建议统一收敛」。

## 一、问题

`ApiLogFilter`（whale-spring-boot-starter，`whale.log.api.enabled` 默认 true）向**本服务库**落 `api_log`，但此前仅 whale-auth / whale-audit 的 `init.sql` 含该表；其余服务新环境初始化后首次请求即报表不存在错误，5.54 租户回填脚本 `whale_backfill_log_tenant('api_log')` 也会因缺表失败。

## 二、变更

- 以 [whale-auth init.sql 的 api_log DDL](../../whale-backend/whale-services/whale-auth/src/main/resources/db/init.sql)（含 `tenant_id` + `idx_tenant_create`，即 5.54 迁移后的最终形态）为标准块，追加到 **11 个**缺失的 `init.sql`：
  - whale-services：workflow / pay / job / im / form / file / archive / agent
  - whale-ops：alert
  - whale-apps：pm / oa
- 结构对齐校验：5.54 各服务迁移的 `whale_backfill_log_tenant('api_log')` 依赖该表存在，init → migration 顺序现在自洽。
- `whale-i18n-sdk/db/init.sql` 为 SDK 示例库（无运行服务写日志），不纳入。

## 三、验证

1. **存量库补齐**：9 个运行库（job/file/alert/pay/form/workflow/agent/archive/pm）执行同构 `CREATE TABLE IF NOT EXISTS api_log`；`information_schema` 复核 11 个 bluewhale_* 库全部有表（auth/audit 原有）。`bluewhale_im` 库当前不存在（10-26 重建仅 4 库），随下次 init 覆盖。
2. **实机落库**：whale-pm（9005 运行中）经网关请求 `GET /pm/project/page`，3 秒后 `bluewhale_pm.api_log` 落 1 行：`module=whale-pm`、`biz_status=1`、`tenant_id=1`、uri 正确。

## 四、结论

api_log 建表已收敛进全部业务服务 init.sql，新环境一次 init 即可用；存量 9 库已在线补齐并实机验证落库。5.55 遗留第 3 条闭环。

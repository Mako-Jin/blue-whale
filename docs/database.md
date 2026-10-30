# 数据库设计规范

## 总则

- **命名**：库名 `bluewhale_<域>`（基础设施同前缀：`bluewhale_nacos`）；表名 `<域>_<实体>` 或领域内小写下划线（`pay_order`、`msg_template`）；字段小写下划线（[naming-conventions.md](naming-conventions.md)）。
- **一服务一库**：跨服务禁止直连对方库，集成走内网 REST；库名登记见 [service-catalog.md](service-catalog.md) §一。
- **字符集**：`utf8mb4`，连接串统一 `connectionCollation=utf8mb4_general_ci`、`serverTimezone=Asia/Shanghai`。

## 惯例（各服务一致）

| 项 | 约定 |
|---|---|
| 主键 | 雪花 ID（`whale-tools` 分布式 ID） |
| 逻辑删除 | `deleted`（0 否 / 1 是），MyBatis-Plus 全局配置 |
| 审计字段 | `create_time` / `update_time` 等；操作留痕另落 `audit_log` |
| 接口日志 | 对外依赖调用落 `api_log`（pay/contract/message/config 等） |
| 租户隔离 | 业务表带 `tenant_id`，租户插件自动过滤；平台表（如菜单/权限种子）不隔离 |
| DDL 位置 | 各服务 `src/main/resources/db/init.sql` + 增量迁移 `migration-*.sql`（幂等可重跑） |

## 连接池基线（T-13）

所有服务 `spring.datasource.hikari` 显式配置：`maximum-pool-size=${DB_POOL_MAX_SIZE:5}`、`minimum-idle=${DB_POOL_MIN_IDLE:2}`、连接超时 30s、空闲 10min、最长生命周期 30min、keepalive 2min、泄漏检测阈值 60s（超限告警日志）。容量按「实例数 × 5」估算，20 服务默认合计 ≈100 连接，扩容时同步调大 `DB_POOL_MAX_SIZE` 并核对 MySQL `max_connections`。

## 演进与国产化

多数据库（去 MySQL 专有语法）按 [design/multi-database.md](design/multi-database.md) T1–T6 分批实施（需求 REQ-ARCH-002，尚未实施）；新增 SQL 避免使用 MySQL 专有函数与语法。

# 数据库可切换适配设计（REQ-ARCH-002）

> 日期：2026-10-25 ｜ 状态：**T1+T2+T3 已实施（2026-10-30）**，T4–T6 后置（待国产库需求/环境明确）；当前统一使用 MySQL 8 ｜ 关联：[需求登记册](../requirements/register.md) REQ-ARCH-002、[架构说明](../architecture.md)、[分布式设计](distributed.md)
> 目标：按客户要求切换数据库，覆盖国产化选型（达梦 DM、人大金仓 KingBase、openGauss、OceanBase）；切换不改业务代码、不改 SQL 语义。

---

## 一、现状基线

| 维度 | 事实 | 证据 |
|---|---|---|
| 连接与驱动 | 各服务 `application.yml` 硬编码 `com.mysql.cj.jdbc.Driver` 与 `jdbc:mysql://`，URL 固定 `characterEncoding=utf8`、`connectionCollation=utf8mb4_general_ci`、`serverTimezone=Asia/Shanghai`、`useSSL=false`、`allowPublicKeyRetrieval=true`；仅主机/端口/账号走环境变量 | `backend/whale-services/whale-workflow/src/main/resources/application.yml:11-16` |
| 分页方言 | `PaginationInnerInterceptor(new DbType.MYSQL)` 写死 | `backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/mybatis/config/MybatisPlusConfig.java:53` |
| 租户插件 | `TenantLineInnerInterceptor` + `tenant_id` 已就绪，与方言无关 | 同上 `:47-55`、`:86-118` |
| DDL 方言 | 全仓 SQL 中 `ENGINE=InnoDB` 31 处 / 10 文件；`ON UPDATE CURRENT_TIMESTAMP` 29 处 / 10 文件；`INSERT IGNORE`/`ON DUPLICATE KEY` 21 处 / 9 文件；普遍使用 `TINYINT`、`BIGINT`、`DATETIME`、`utf8mb4_general_ci`、反引号 | `backend/**/src/main/resources/db/*.sql`（含 `whale-auth`、`whale-form`、`whale-agent`、`whale-workflow`、`whale-audit`、`whale-alert`、`whale-pay`、`whale-i18n-sdk`） |
| Mapper SQL | XML 中未检索到 `DATE_FORMAT`/`IFNULL`/`GROUP_CONCAT`/`FIND_IN_SET`/`JSON_EXTRACT`/`LIMIT n` 等 MySQL 专有函数；动态表名与分页由插件生成 | `backend/**/src/main/resources/**/*.xml` 全量检索无匹配 |
| 初始化方式 | **未引入 Flyway/Liquibase**；DDL 靠手工/容器 init 脚本执行（全新建库用各服务 `db/init.sql`，存量库用 `db/migration-*.sql`） | `backend/whale-ops/whale-audit/src/main/resources/db/init.sql:14-17`、`infrastructure/docker/mysql/init/01-init-databases.sql` |
| 多库现状 | 单实例多 schema：`bluewhale_auth/job/workflow/form/file/monitor/audit/pay/alert/agent/nacos/erp/mes/pdm/oa` 等，统一 `utf8mb4` + `utf8mb4_unicode_ci`（建库脚本） | `infrastructure/docker/mysql/init/01-init-databases.sql:2-82` |
| 已有方言目录先例 | 国际化 SDK 已按数据库分目录：`db/i18n/mysql/sys_i18n_message.sql` | `backend/whale-sdk/whale-i18n-sdk/src/main/resources/db/i18n/mysql/` |
| 动态数据源 | 评估稿提到的 `TenantDataSourceRouter` **不存在**；当前「一服务一库」，无多数据源路由 | 全仓检索无该类 |

**结论**：SQL 语句本身对 MySQL 专有函数依赖很低（Mapper 无匹配），主要改造面集中在 **连接/驱动、分页方言、DDL 语法、跨库初始化机制** 四处。

---

## 二、目标架构

### 2.1 方言选择

1. 新增统一配置 `whale.db.type`（默认 `mysql`），取值：`mysql` / `dm` / `kingbase` / `opengauss` / `oceanbase`。
2. `MybatisPlusConfig` 由配置解析 `DbType`（映射到 MP 3.5.17 枚举：`MYSQL`、`DM`、`KINGBASE_ES`、`OPENGAUSS`（若枚举缺失则回落 `POSTGRE_SQL`/`ORACLE` 并记录告警）、`ORACLE` 用于 OceanBase Oracle 模式；OceanBase MySQL 模式直接用 `MYSQL`）。
3. JDBC 驱动与 URL 按 vendor 由环境变量注入（`WHALE_DB_TYPE` + `MYSQL_*` 改名通用 `DB_HOST/DB_PORT/...`），不接受在业务代码里拼 URL。

### 2.2 DDL 版本化与分发

- **目录约定**：`db/{vendor}/` 放方言 DDL；MySQL 维持现状 `db/init.sql`（兼容期），新增 `db/mysql/` 承接新脚本。为避免一次性搬迁风险，先支持「同目录多方言文件」：`init.mysql.sql` / `init.dm.sql`。
- **执行方式（推荐分两步）**：
  - 第一步（低风险）：保留手工/容器执行，增加「启动自检」——按 `whale.db.type` 校验必需表/列存在，缺失即失败并给出目标脚本路径。
  - 第二步（评估后）：引入 Flyway，按 vendor 分 `locations`（`classpath:db/migration/mysql`、`.../dm`）。注意：Flyway Community 对 DM/KingBase/openGauss 的原生支持有限，需企业版或自研扩展；在国产库需求明确前不强行引入。
- **禁止**：同一脚本内混用多方言 DDL；禁止 `DROP`/`ALTER` 破坏性语句混入 `init.sql`。

### 2.3 去 MySQL 专有写法（映射表）

| MySQL 写法 | 目标写法 | 处理方式 |
|---|---|---|
| `ENGINE=InnoDB`、`DEFAULT CHARSET`、`COLLATE` | 删除（DM/KingBase/openGauss 无此概念） | DDL 生成模板按 vendor 输出 |
| `TINYINT` | `SMALLINT` / `NUMBER(3)` | 类型映射；布尔语义统一 0/1 |
| `BIGINT AUTO_INCREMENT` | `BIGINT IDENTITY` / `BIGSERIAL` / 序列 | 统一使用雪花 ID（现状已如此），主键自增仅测试脚本使用 |
| `DATETIME` / `ON UPDATE CURRENT_TIMESTAMP` | `TIMESTAMP` / `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | **更新时间由 `MetaObjectHandler.updateFill` 写入**（已存在），DDL 去掉 `ON UPDATE` |
| `INSERT IGNORE` | `INSERT ... WHERE NOT EXISTS` / `MERGE` | 迁移脚本改为「先查后插」或服务层幂等 |
| `ON DUPLICATE KEY UPDATE` | `MERGE INTO`（DM/KingBase）/ `INSERT ... ON CONFLICT`（openGauss） | 按 vendor 生成；业务层优先「查询 + 更新」 |
| `JSON` 类型 | `CLOB` / `TEXT` / `JSONB`（openGauss） | 统一按字符串列 + 应用层序列化（现状 `tool_trace_json` 即 TEXT） |
| `UNSIGNED` | 删除，改用 CHECK 约束 | 现状未使用，避免新增 |
| 反引号 | 双引号或裸标识符 | 统一去掉反引号，标识符不与保留字冲突 |
| `LIMIT` | `LIMIT`（DM/OceanBase 兼容）/ `FETCH FIRST`（Oracle 模式） | 由 MP 分页插件生成，不手写 |
| `VARCHAR(n)` 长度语义 | DM 默认按字节，需显式 `VARCHAR(n CHAR)` | 中文列长度按字符声明，或统一放大 4 倍字节数 |

### 2.4 兼容层与边界

- **多 schema**：继续「一服务一库」，国产库侧用 schema/用户映射；不引入跨库事务。
- **租户隔离**：`TenantLineInnerInterceptor` 依赖标准 SQL，换库不需要改；但需验证各 vendor 的分页 + 租户插件叠加生成的 SQL。
- **对象存储/缓存**：与数据库选型无关，不在本设计范围。

---

## 三、实施清单（建议顺序）

| # | 任务 | 落点 | 验收 |
|---|---|---|---|
| T1 | `whale.db.type` 配置 + `DbType` 解析 + 分页方言可切换 | `whale-spring-boot-starter`（`MybatisPlusConfig`、新增 `DatabaseProperties`） | 单测覆盖 5 种取值；MySQL 默认行为不变 |
| T2 | 驱动依赖按 vendor 可选（`mysql`/`dm`/`kingbase`/`opengauss`/`oceanbase` profile 或 optional 依赖） | 各服务 `pom.xml`、父 POM `dependencyManagement` | 默认构建只带 MySQL 驱动；切换 profile 可打包对应驱动 |
| T3 | DDL 方言化：先补 `db/mysql/` 与新脚本模板，统计的 31/29/21 处逐条替换 | `backend/**/src/main/resources/db/*` | MySQL 脚本语义等价；新增 DM/KingBase/openGauss 版本可执行 |
| T4 | 初始化机制：启动自检（表/列缺失即失败）→ 评估 Flyway | 公共 starter `DatabaseSchemaVerifier` | 缺失表启动失败并输出脚本路径；Flyway 决策记录留档 |
| T5 | SQL 兼容审计：动态表名、`LIKE`、批量插入、`last("limit 1")` 等 MP 生成 SQL | `whale-form`（动态表）、`whale-auth`（`limit 1`）、`whale-im` | 各 vendor 冒烟：登录、分页、表单动态表、IM 会话列表 |
| T6 | 验证矩阵与文档 | 本文件 + `docs/design/cicd.md` | MySQL 8 回归全绿；国产库至少 1 种（建议 DM 或 KingBase）跑通登录 + 分页 + 租户隔离 |

---

## 四、风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| 国产库驱动/连接池兼容 | 达梦/金仓 JDBC 驱动与 Hikari、MyBatis-Plus 版本组合需实测；openGauss 需 PostgreSQL 协议驱动 | 先做单服务 POC（`whale-auth`），再铺开 |
| Flyway 支持面 | Community 版对 DM/KingBase/openGauss 支持有限 | 第一步不引入；用启动自检 + 方言脚本，待需求明确再评估企业版/自研 runner |
| DDL 语义差异 | 字符长度（字节 vs 字符）、大小写敏感、`NULL` 排序、索引长度上限 | 统一 `VARCHAR(n CHAR)`；排序显式 `NULLS LAST`；索引列长度按最小 vendor 约束设计 |
| 保留字冲突 | `status`、`type`、`level` 等在部分库为保留字 | 统一加前缀/改名，或按 vendor 加引号（禁止依赖反引号） |
| JSON/大文本 | 各库 JSON 能力不同 | 统一 TEXT/CLOB + 应用层 JSON 序列化 |
| 时区与精度 | `DATETIME` 无时区，`TIMESTAMP` 有时区 | 全链路 `Asia/Shanghai`，Java 侧 `LocalDateTime`，禁止依赖库默认时区 |
| 数据迁移 | 存量 MySQL → 国产库需结构 + 数据同步 | 另立迁移方案（工具导出 + 校验行数/校验和），不在本设计内展开 |

---

## 五、附：现状统计口径

- `ENGINE=InnoDB`：31 处 / 10 文件（`whale-agent`、`whale-form`、`whale-workflow`、`whale-alert`、`whale-audit` 等）。
- `ON UPDATE CURRENT_TIMESTAMP`：29 处 / 10 文件。
- `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE`：21 处 / 9 文件（集中在 `whale-auth` 迁移脚本）。
- Mapper XML 中 MySQL 专有函数：0 处。

> 统计命令示例：`rg -c "ENGINE\s*=\s*InnoDB" backend --glob "*.sql"`。

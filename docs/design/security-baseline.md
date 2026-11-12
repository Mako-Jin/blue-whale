# 安全基线设计（Jasypt 配置加密 / 安全哈希 / Flyway / 口令生命周期）

> 日期：2026-11-01 ｜ 状态：Jasypt、口令策略与定期改密已落地；Flyway 已接入 22 个服务库（auth + config/audit/alert/workflow/form/agent/doc/file + 12 个业务库 + video）并完成空库/存量库实机验证｜ 关联：[需求登记册](../requirements/register.md) `REQ-SEC-020~024`、[实施计划](../plans/2026-10-31-security-baseline-config-flyway-password-rotation.md)、[多数据库适配](multi-database.md)

本文档记录四项安全基线的落地口径、配置项、代码落点与验证证据，并明确本期范围外事项。

---

## 一、范围与结论

| 项 | 需求 | 结论 | 主要落点 |
|---|---|---|---|
| 配置敏感值加密 | `REQ-SEC-020` | **已实现**（Jasypt 1.9.3 + 自研 `EnvironmentPostProcessor`） | `whale-common/whale-config-crypto`、`WHALE_CONFIG_ENC_KEY`、`scripts/security/encrypt-secret.ps1` |
| 安全哈希 | `REQ-SEC-021` | **已实现**（BCrypt `strength=12` + 登录自动升级 + 统一强度策略 + 历史口令） | `SecurityConfig`、`PasswordHasherAdapter`、`PasswordPolicy`、`PasswordPolicyService` |
| 数据库版本化 | `REQ-SEC-022` | **已实现（22 个服务库）**：auth 试点 + 阶段 4 铺开 config/audit/alert/workflow/form/agent/doc/file 与 12 个业务库；`whale-video` 自 M0 起使用 Flyway | 各服务 `pom.xml`、`application.yml`、`db/migration/*`、`db/README.md` |
| 口令定期修改 | `REQ-SEC-023` / `REQ-SEC-024` | **已实现**（到期强制改密、自助改密、管理员重置一次性临时口令、租户可配置有效期、到期提醒） | `SessionAppService`、`PasswordAppService`、`TenantPasswordPolicyService`、`PasswordExpiryNoticeJob`、网关 `JwtAuthGlobalFilter` |
| 中间件账号 | 范围说明 | **本期不纳入自动定期改密**（MySQL/Redis/RabbitMQ/Nacos 仅提供人工轮换手册） | 见 §六 |

---

## 二、配置文件加密（REQ-SEC-020）

### 2.1 机制

1. 依赖：`org.jasypt:jasypt:1.9.3` 核心库（不引入第三方 starter，规避 Boot 4 自动装配风险）。
2. 解密链路：`ConfigEncryptionEnvironmentPostProcessor`（`META-INF/spring.factories` 注册）在环境准备阶段包装属性源，把 `ENC(...)` 解出明文；优先级保持 Spring 原始顺序。
3. 算法：`PBEWithHMACSHA512AndAES_256`，随机 IV/Salt。
4. 密文格式：`ENC(密文)`（前缀/后缀可配置）。
5. **完整性哨兵**：Jasypt PBE 无认证性，错密钥解密可能不抛填充异常而返回乱码。`ConfigValueCipher` 在明文中前置 `whale-enc:` 标记，解密后校验标记缺失即报「完整性校验失败」，保证错密钥稳定失败。

### 2.2 配置项

| 配置键 | 含义 | 默认 |
|---|---|---|
| `whale.config.encryption.enabled` | 是否启用密文解密 | `true`（缺省开启） |
| `whale.config.encryption.password` | 备用密钥配置项（优先级低于环境变量） | 空 |
| `WHALE_CONFIG_ENC_KEY` | **推荐密钥注入方式**（环境变量/K8s Secret/挂载文件） | 空 |
| `whale.config.encryption.algorithm` | PBE 算法 | `PBEWithHMACSHA512AndAES_256` |
| `whale.config.encryption.prefix` / `suffix` | 密文前后缀 | `ENC(` / `)` |
| `whale.config.encryption.min-key-length` | 密钥最小长度 | `16` |

### 2.3 启动守卫

- 存在 `ENC(...)` 但未注入密钥 → 启动失败，提示通过 `WHALE_CONFIG_ENC_KEY` 注入。
- 密钥长度不足 → 启动失败并提示最小长度（不输出密钥内容）。
- 密钥为占位值（`password`、`todo`、`change_me` 等）→ 启动失败。
- 加密/重加密工具：`scripts/security/encrypt-secret.ps1`（`encrypt` / `reencrypt`），仅输出密文。

> 数据源口令等仍保留 `SPRING_DATASOURCE_PASSWORD` / `MYSQL_PASSWORD` 环境变量覆盖能力，环境变量优先于密文。

---

## 三、安全哈希与口令强度（REQ-SEC-021）

| 能力 | 口径 | 落点 |
|---|---|---|
| 哈希算法 | BCrypt，`strength=12` | `SecurityConfig#passwordEncoder` |
| 登录自动升级 | 命中旧 cost 的密文时按新 cost 重哈希并回写 | `PasswordHasherAdapter`（`matches`/`upgradeEncoding`） |
| 强度策略 | 最小 12 / 最大 64 位；必须含大写、小写、数字、特殊字符；不得包含用户名；拒绝常见弱口令 | `PasswordPolicy` + `PasswordPolicyProperties` |
| 统一入口 | 创建、更新、管理员重置、自助改密四条入口共用 `PasswordPolicyService` | `UserAppService`、`PasswordAppService` |
| 历史口令 | 最近 `history-size`（默认 5）次不可复用；仅存 BCrypt 密文 | `sys_password_history` |
| 管理端表单 | 新建/编辑用户密码最少 12 位并提示复杂度 | `whale-frontend/admin/src/pages/user/UserList.tsx` |

配置项（`whale.auth.password.*`，对应环境变量）：

| 配置键 | 含义 | 默认 / 环境变量 |
|---|---|---|
| `max-age-days` | 口令有效期（`<=0` 应急关闭到期强制） | `90` / `WHALE_AUTH_PASSWORD_MAX_AGE_DAYS` |
| `warn-days` | 到期前提醒天数 | `7` / `WHALE_AUTH_PASSWORD_WARN_DAYS` |
| `history-size` | 历史口令保留条数 | `5` / `WHALE_AUTH_PASSWORD_HISTORY_SIZE` |
| `min-length` / `max-length` | 口令长度范围 | `12` / `64` |
| `require-upper` / `lower` / `digit` / `special` | 字符类别要求 | `true` |
| `force-change-on-create` | 新建/更新/重置后强制首登改密 | `true` / `WHALE_AUTH_PASSWORD_FORCE_CHANGE` |
| `notice-cron` | 到期提醒任务 cron | `0 0 2 * * ?` / `WHALE_AUTH_PASSWORD_NOTICE_CRON` |

---

## 四、口令生命周期与定期改密（REQ-SEC-023 / REQ-SEC-024）

### 4.1 数据模型

| 变更 | 说明 |
|---|---|
| `sys_user.password_updated_time` | 最近改密时间；存量账号回填 `COALESCE(create_time, NOW())`，避免升级即全员到期 |
| `sys_user.password_notice_time` | 最近一次到期提醒时间，同一口令周期只提醒一次 |
| `sys_password_history` | 最近 N 次口令（BCrypt 密文），用于复用校验 |
| `sys_tenant_password_policy` | 租户口令策略（`tenant_id` 唯一：`max_age_days` / `warn_days`） |

迁移脚本（幂等，可重复执行）：

- `whale-service-auth-starter/src/main/resources/db/migration-2026-11-01-password-lifecycle.sql`
- `whale-service-auth-starter/src/main/resources/db/migration-2026-11-01-tenant-password-policy.sql`（含权限点 172/173、平台菜单模板 36、已有业务租户菜单复制）
- `db/init.sql` 同步上述表结构、权限与菜单（全新建库无需执行增量脚本）

### 4.2 租户口令策略配置（REQ-SEC-024）

- 接口：`GET/PUT /tenant/v1/password-policy`（权限码 `sys:tenant:pwdpolicy:list` / `sys:tenant:pwdpolicy:edit`，下发给租户管理员）。
- 可配置范围：有效期 **1–365 天**，提醒天数 **0–有效期**；不允许租户关闭到期强制（应急关闭只保留全局 `max-age-days<=0`）。
- 未配置时回退全局默认，响应 `customized=false` 并返回 `defaultMaxAgeDays` / `defaultWarnDays`，供前端展示「沿用全局默认」。
- 登录/刷新按**账号所属租户**的生效策略判定到期，而非全局一刀切。
- 到期提醒任务：先取全部启用租户中的最短提醒窗口做一次跨租户扫描，再按候选账号所属租户策略二次筛选，避免多租户重复扫描且不误提醒。

### 4.3 管理员重置：一次性临时口令

1. 管理端确认重置后，后端调用 `TemporaryPasswordGenerator` 生成 16 位随机口令（`SecureRandom`，必含四类字符，剔除易混淆字符）。
2. 口令经 `PasswordPolicyService` 加密后落库（仅 BCrypt 密文），写入历史表，置 `must_change_password=1`，并发布 `PASSWORD_CHANGED` 事件撤销存量令牌。
3. 接口 `PUT /user/{id}/password` 返回 `ResetPasswordVO`：`userId`、`username`、`temporaryPassword`、`mustChangePassword`。
4. **明文只在本次响应出现一次**：不落库、不写审计参数、不提供再次查询接口；管理端以结果弹窗展示并提供复制，关闭后不可再查看。
5. 目标账号使用临时口令登录即触发强制改密；旧令牌已撤销。

管理端落点：`whale-frontend/admin/src/api/user.ts`（`ResetPasswordVO`）、`whale-frontend/admin/src/pages/user/UserList.tsx`（确认弹窗 + 一次性结果弹窗）。

### 4.4 自助改密与强制阻断

- 自助改密：`PUT /auth/password`，请求 `{oldPassword,newPassword}`；成功后清强制改密标记、更新改密时间、写历史、发布 `PASSWORD_CHANGED`。审计注解 `recordParams=false`，不记录口令。
- 登录/刷新响应透出 `mustChangePassword`（强制改密与已到期均折算为 `true`）、`passwordExpired`、`passwordExpiresInDays`。
- JWT 声明 `pwdChangeRequired=true` 时：
  - 网关 `JwtAuthGlobalFilter` 对非白名单路由返回 403 `PASSWORD_CHANGE_REQUIRED`；
  - auth 侧 `JwtAuthenticationFilter` 做同等拦截，防止直连服务端口绕过。
  - 白名单：`/auth/password`、`/auth/logout`、`/auth/refresh`、`/auth/me`。
- 共享前端弹窗 `MustChangePasswordDialog` 改为调用 `PUT /auth/password`（原密码 + 新密码 + 确认），失败提示与强度校验前后端一致。

---

## 五、Flyway 数据库版本化（REQ-SEC-022）

### 5.1 当前状态（2026-11-01 更新）

- **auth 试点已在运行时生效**：`whale-service-auth-starter` 引入 `spring-boot-starter-flyway`（Boot 4.0.8 BOM 管理 Flyway 11.14.1）与 `flyway-mysql`，`application.yml` 已配置 `spring.flyway.*`，脚本位于 `classpath:db/migration`，含 `V1__baseline.sql`（17 张表 + 27 条种子）、`V2__password_lifecycle.sql`、`V3__tenant_password_policy.sql`。
- **阶段 4 已完成服务库（8 个）与业务库（12 个）铺开**（依赖/配置与 auth 同口径，每库新增 `V1__baseline.sql` 与 `db/README.md`）：

  | 服务 | 基线表数 | 备注 |
  |---|---|---|
  | `whale-config` | 4 | `config_item` / `config_item_history` / `audit_log` / `api_log` |
  | `whale-audit` | 7 | 全部 append-only 日志表，禁止 `DROP`/`TRUNCATE`/`DELETE` 迁移 |
  | `whale-alert` | 3 | 含 `INSERT IGNORE` 的 2 条规则种子 |
  | `whale-workflow` | 5 | 另有 `V2__task_operator.sql` 补齐 `operator_id/operator_type/operator_source` |
  | `whale-form` | 9 | 动态表单「元数据表」；业务动态表由应用运行时建表，不在迁移范围 |
  | `whale-agent` | 10 | 事实表 / 会话 / 知识库采集表 |
  | `whale-doc` | 18 | 16 张文档业务表 + `audit_log` / `api_log` |
  | `whale-file` | 2 | `file_info` / `api_log`（脚本位于 `whale-service-file-web`） |

  | 业务库 | 基线表数 | 历史脚本折叠 |
  |---|---|---|
  | `whale-contract` | 11 | 另有 `V2__esign_callback.sql` 补齐回调幂等表与 `workflow_task_id/sign_url` |
  | `whale-erp` | 4 | `migration-5.71-erp-log-tables.sql` 已折叠 |
  | `whale-mes` / `whale-oa` / `whale-pdm` | 2 / 2 / 2 | 无历史增量 |
  | `whale-pm` | 13 | `migration-5.55-m2-workitem.sql` / `m3-board.sql` 已折叠 |
  | `whale-archive` | 9 | `migration-5.54-tenant-business.sql` 已折叠 |
  | `whale-im` | 7 | `migration-5.52` / `5.54` 已折叠 |
  | `whale-job` | 5 | `migration-p3-job.sql` / `5.54` 已折叠 |
  | `whale-message` | 5 | `migration-5.70` / `5.71` 已折叠 |
  | `whale-music` | 14 | `migration-5.75-m5-appeal.sql` 已折叠 |
  | `whale-pay` | 4 | `migration-5.71-pay-flow.sql` / `5.54` 已折叠 |

- 另有 `whale-video`（Wave 5.77 新服务）自 M0 起即直接以 Flyway 建库（`V1` 16 表 + `V2__series` + `V3__movie_collection`），无 `init.sql`，本次一并完成空库实机验证（`APPLIED=3`，20 张表含历史表）。
- 全部服务库合计 **22 个**（auth + config/audit/alert/workflow/form/agent/doc/file + 12 个业务库 + video）。
- `db/init.sql` 保留为「手工空库初始化」入口（含 `DROP TABLE`，**不在 Flyway 扫描路径**）；历史 `migration-*.sql` **保留在原位**（文档/冒烟脚本仍引用），同样不在 `classpath:db/migration` 内，口径见各服务 `db/README.md`。
- `.env.example` 中的 `WHALE_FLYWAY_ENABLED` / `WHALE_FLYWAY_BASELINE_ON_MIGRATE` 已实际生效（应急关闭 / 存量库 baseline）。

### 5.2 接入口径（试点 `whale-service-auth`）

| 配置 | 值 |
|---|---|
| `spring.flyway.locations` | `classpath:db/migration` |
| `spring.flyway.baseline-on-migrate` | `true`（存量库自动 baseline，空库照常执行 V1） |
| `spring.flyway.baseline-version` | `1` |
| `spring.flyway.validate-on-migrate` | `true` |
| `spring.flyway.clean-disabled` | `true` |
| `spring.flyway.placeholder-replacement` | `false` |
| `spring.flyway.connect-retries` | `10` |

迁移脚本约定：

- `V1__baseline.sql`：由 `init.sql` 转换导出（17 张表），**去掉全部 `DROP TABLE`/`TRUNCATE`**，`CREATE TABLE` 改用 `IF NOT EXISTS`，`INSERT` 改 `INSERT IGNORE`，表尾 `) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci`。
- `V2`：口令生命周期（`password_updated_time` / `password_notice_time` / `sys_password_history`），`ALTER TABLE` 用 `information_schema` 守卫保证幂等。
- `V3`：租户口令策略（表 + 权限 172/173 + 菜单 36）。
- 历史 `migration-*.sql` **保留在 `db/` 根目录且不在扫描路径**（计划允许的「保留但确保不参与迁移」方案），新增 `db/README.md` 说明三类脚本边界与后续「种子折叠」待办。

> 国产库（DM/KingBase/openGauss）本期不纳入 Flyway，按 [多数据库适配](multi-database.md) 另行决策。

### 5.3 实施结果（2026-11-01）

| 验证场景 | 结果 |
|---|---|
| 空库 | 依次应用 V1→V2→V3，18 张表；种子 1 用户 / 18 菜单 / 116 权限（含 172/173、菜单 36），登录可用 |
| 存量库（先导 `init.sql`，17 表） | `BASELINE` 写入 version=1 后仅执行 V2/V3；数据无损，`password_updated_time` 回填为 `create_time`；重复执行 `APPLIED=0` |
| checksum 校验 | 改动 V2 副本后 `FlywayValidateException: Migration checksum mismatch for migration version 2`（exit=1），`validate-on-migrate=true` 生效 |
| 并发启动 | 两进程同时 migrate：历史表 1/2/3 无重复、无重复执行，**但两实例可能分摊版本**（实测一实例执行 V1/V3、另一实例执行 V2）；结论为「单版本只执行一次」而非「单实例串行执行全部」 |
| 真启动（auth jar + 空库） | 日志 `Successfully applied 3 migrations ... now at version v3`、`Tomcat started on port 8081`；19 张表（含运行时建表的 `sys_i18n_message`）；health 503 仅因 RabbitMQ 未启动，与 Flyway 无关 |
| 单测门禁（auth） | `FlywayMigrationScriptTest` 4 用例（命名/版本递增、无破坏性语句、V1 表集合 == `init.sql`、V2/V3 关键对象），auth 全量 475 用例全绿 + Checkstyle 通过 |
| 阶段 4 · 空库（8 库） | config 5 表 / audit 8 表 / alert 4 表 / workflow 6 表（V1+V2）/ form 10 表 / agent 11 表 / doc 19 表 / file 3 表（均含 `flyway_schema_history`），`APPLIED` 与脚本版本数一致 |
| 阶段 4 · 存量库（8 库） | 先导 `init.sql` + 插入 1 条业务数据 → 写入 `BASELINE` version=1；config `config_item=1`、audit `audit_log=1`、alert `alert_record=1`、workflow `process_definition=1`（另执行 V2，列已存在时为空操作）、form `form_definition=1`、agent `agent_fact=1`、doc `doc_space=1`、file `file_info=1` 均保留，表数不变 |
| 阶段 4 · workflow `V2` | 空库执行后 `process_task` 存在 `operator_id/operator_type/operator_source`；存量库（`init.sql` 已补列）执行 V2 为守卫空操作，无重复列错误 |
| 阶段 4 · 单测门禁 | `mvn -pl :whale-workflow,:whale-form,:whale-agent,:whale-doc,:whale-service-file-web -am test`：workflow 155、form 118、agent 136、doc 18、file-web 2（新增 `FileFlywayMigrationScriptTest`）用例全绿，Checkstyle 0 违规 |
| 阶段 4 · 业务库空库（12 库） | contract 12 表（V1+V2）/ erp 5 / mes 3 / oa 3 / pdm 3 / pm 14 / archive 10 / im 8 / job 6 / message 6 / music 15 / pay 5（均含历史表），`APPLIED` 与脚本版本数一致 |
| 阶段 4 · 业务库存量库（12 库） | 先导 `init.sql` + `api_log` 插入 1 条 → `BASELINE` version=1；`api_log=1` 全部保留、表数不变；contract 另执行 V2（回调表与两列已由 `init.sql` 折叠，空操作） |
| 阶段 4 · 业务库单测门禁 | `mvn -pl :whale-contract,:whale-erp,:whale-mes,:whale-oa,:whale-pdm,:whale-pm,:whale-archive,:whale-im,:whale-job,:whale-message,:whale-music,:whale-pay -am test`：contract 49 / erp 6 / mes 2 / oa 8 / pdm 2 / pm 129 / archive 63 / im 146 / job 83 / message 25 / music 119 / pay 24 全绿（每库新增 2 个迁移脚本用例），Checkstyle 0 违规 |
| 阶段 4 · whale-video 空库 | `APPLIED=3`（V1+V2+V3），20 张表（含 `flyway_schema_history`），`vd_series/vd_movie/vd_video` 建表成功 |

已知差异：空库执行 V2/V3 的 `CREATE TABLE IF NOT EXISTS` 会触发 MySQL 1050 note，Flyway 以 WARN 记录，不影响结果；其他 Wave 模块（音乐 281–303、菜单 39–44、文档/OA/上诉）的权限与菜单种子尚未折叠进 V1，已记入 `db/README.md` 与计划「已知差距」，不影响空库登录与 RBAC。

---

## 六、范围外：中间件账号

本期**不将中间件/基础设施账号纳入自动定期改密**，包括：

| 账号 | 原因 | 当前处置 |
|---|---|---|
| MySQL（服务库账号、运维账号） | 轮换涉及连接池热更新与多服务同步，需维护窗口 | 人工轮换 + 变更单；口令仍通过环境变量/密钥管理系统注入 |
| Redis（含 Redisson 口令） | 轮换需同步全部实例与客户端配置 | 人工轮换手册 |
| RabbitMQ（`guest` 及业务账号） | 重建用户/权限需停机窗口 | 人工轮换手册 |
| Nacos（配置中心账号） | 依赖配置中心自身的权限模型 | 人工轮换手册 |

口径说明：

- 到期扫描与提醒任务只覆盖 `sys_user` 中的人工账号，不扫描中间件账号；
- 中间件口令仍必须遵循「禁止明文入库/入仓、密钥外置注入」；
- 后续如需自动化，另立运维任务（含轮换窗口、客户端热加载、回滚密钥）。

---

## 七、验证证据

| 项 | 方式 | 结果 |
|---|---|---|
| Jasypt 加密/解密、错密钥完整性、EPP 注册 | `whale-config-crypto` 单测（`ConfigValueCipherTest`、`ConfigEncryptionEnvironmentPostProcessorTest`、`ConfigEncryptionRegistrationTest`） | 通过（9 个用例） |
| 口令强度/历史/临时口令/租户策略 | auth starter 单测（`PasswordPolicyTest`、`PasswordPolicyServiceTest`、`TenantPasswordPolicyServiceTest`、`TemporaryPasswordGeneratorTest`、`PasswordAppServiceTest`） | 通过 |
| 到期提醒多租户口径 | `PasswordExpiryNoticeJobTest` | 通过 |
| 登录到期标记、自助改密、管理员重置响应 | `AuthControllerTest`、`UserControllerTest`、`UserAppServiceTest` | 通过 |
| 网关强制改密拦截 | `JwtAuthGlobalFilterTest` | 通过 |
| auth 全量单测 | `mvn -pl whale-service-auth-starter -am test` | `Tests run: 475, Failures: 0, Errors: 0`（含新增 `FlywayMigrationScriptTest` 4 用例）+ Checkstyle 通过 |
| 网关全量单测 | `mvn -pl whale-gateway -am test` | `Tests run: 122, Failures: 0, Errors: 0` |
| Flyway 空库/存量库/checksum/并发/真启动实机验证（auth） | 本机 MySQL 8.0.45 + Flyway API/应用启动 | **已通过**，明细见 §5.3 |
| Flyway 阶段 4 铺开单测（核心库） | `mvn -pl :whale-workflow,:whale-form,:whale-agent,:whale-doc,:whale-service-file-web -am test` | workflow 155 / form 118 / agent 136 / doc 18 / file-web 2 全绿 + Checkstyle 0 违规 |
| Flyway 阶段 4 铺开单测（业务库） | `mvn -pl :whale-contract,:whale-erp,:whale-mes,:whale-oa,:whale-pdm,:whale-pm,:whale-archive,:whale-im,:whale-job,:whale-message,:whale-music,:whale-pay -am test` | 12 模块 656 用例全绿（含新增 24 个迁移脚本用例）+ Checkstyle 0 违规 |
| Flyway 阶段 4 空库/存量库实机验证（20 库） | 本机 MySQL 8.0.45 + Flyway API（各服务 `db/migration` 文件系统路径） | **已通过**，明细见 §5.3；临时库验证后已删除 |
| 前端 lint/build | `pnpm --filter @bluewhale/whale-auth lint` / `pnpm --filter whale-admin lint` / `build` | **已通过**（口令策略页 `applyPolicy` 改为 `useCallback` 后无警告） |

---

## 八、待办

1. ~~阶段 2（Flyway 试点）~~：已完成（依赖/配置/V1–V3/单测/五种实机验证）。
2. auth 库实机执行两份增量脚本并回读校验：由 Flyway V2/V3 覆盖并已实机验证。
3. ~~前端 lint/build 与共享包校验（`whale-auth`、`admin`）~~：已完成。
4. **阶段 4**：~~服务库与业务应用库铺开~~已完成（22 个服务库：依赖/配置/基线/README/单测/实机验证）；余下为其他 Wave 种子折叠为 `R__<module>_seed.sql` 或并入 V1、CI「`init.sql` vs `db/migration` 一致性」与 `flyway:migrate`/`validate` 空库/存量库 job，以及 i18n 种子决策。
5. 实机 IT 补测：到期登录强制改密、改密后旧 access/refresh 令牌 401、租户策略保存/回读；浏览器联调口令策略页与两个弹窗。

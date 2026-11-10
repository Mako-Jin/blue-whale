# 安全基线四项核查与实施计划（Jasypt / 安全哈希 / Flyway / 定期改密）

> 核查日期：2026-10-31 ｜ 核查基线：`9b13133` ｜ 实施启动：2026-11-01（用户确认按本方案默认决策实施）
> 需求编号：`REQ-SEC-020`（Jasypt）/ `REQ-SEC-021`（哈希加固）/ `REQ-SEC-022`（Flyway）/ `REQ-SEC-023`（定期改密）
> 说明：原计划草稿写作 `REQ-SEC-018~021`，与 2026-10-29/2026-10-31 已登记的文档安全（018）、音乐安全（019）冲突，实施时顺延为 `020~023`。
> 追加需求（2026-11-01 用户指令）：`REQ-SEC-024`——租户口令策略自助配置、管理员重置一次性临时口令、中间件账号不纳入自动定期改密。
> **实施进度（2026-11-01）**：阶段 0 ✅、阶段 1 ✅（代码/单测）、阶段 3 ✅（代码/单测 + 前端页面 + lint/build）、阶段 2 ✅（auth 试点已接入，空库/存量库/checksum/并发/启动实机验证通过）、阶段 4 🔄（Flyway 已铺开到全部 22 个服务库（8 个核心服务库 + 12 个业务库 + video），20 个库完成空库/存量库实机验证，文档已回填；CI、种子折叠与浏览器联调待补）。详见 [安全基线设计](../design/security-baseline.md) 与 [开发日志 2026-11-01](../devlog/2026-11-01.md)。
> 范围：① 配置文件敏感值是否用 Jasypt 加密；② 账号口令是否采用安全哈希；③ 数据库脚本是否由 Flyway 版本化管理；④ 账号口令是否支持定期修改/到期强制修改。
> 结论口径：以当前仓库代码、配置、SQL 与既有设计文档为准；未在运行环境实测的项目明确标注「代码存在、运行未取证」。

## 一、结论速览

| 项 | 当前结论 | 证据 | 主要缺口 | 风险等级 |
|---|---|---|---|---|
| ① 配置文件密码 Jasypt | **未实现** | 全仓检索 `jasypt` 0 命中；各服务 `application.yml` 使用 `${MYSQL_PASSWORD:}` 等环境变量占位 | 无 `ENC(...)` 解密链路；无加密密钥注入/启动守卫；部分配置仍有弱默认值 | P1 |
| ② 安全哈希 | **已实现（BCrypt）** | `SecurityConfig#passwordEncoder` 返回 `BCryptPasswordEncoder`；`PasswordHasherAdapter` 编码；`User#changePassword` 拒绝非 BCrypt 密文；登录走 `AuthenticationManager` 比对 | 默认 `strength=10`，无登录时自动升级；后端口令强度仅 6–64 位，弱于前端 12 位+复杂度；无口令历史/重用校验 | P2（可作为加固项） |
| ③ 数据库脚本 Flyway | **未实现（且既有文档明确决定暂不引入）** | 全仓检索 `flyway` 仅出现在文档；`docs/design/multi-database.md:17` 明确「未引入 Flyway/Liquibase」；DDL 靠手工/容器 init 脚本执行 | 无版本表、无校验和、无自动迁移；`init.sql` 含 `DROP TABLE`，不可直接作为迁移脚本；历史 `migration-*.sql` 命名不统一且需人工按序执行 | P1（交付/环境一致性） |
| ④ 定期修改账号密码 | **部分实现（仅出厂管理员一次性轮换）** | `AdminPasswordInitializer` 启动检测种子密文并轮换；`sys_user.must_change_password`；前端 `MustChangePasswordDialog` | 无 `password_updated_time`、无到期判定、无定时任务、无自助改密接口、无历史口令校验；管理员重置后反而清除强制改密标记 | P1 |

**一句话结论**：四项中只有「安全哈希」已落地；Jasypt、Flyway、定期改密均需新增实现，其中定期改密属于「有骨架但未闭环」。

---

## 二、现状核查明细

### 2.1 配置文件密码与 Jasypt

**已具备的能力**

- 敏感值已从 yml 外置为环境变量，例如数据源：
  - [`whale-service-auth-starter/application.yml:19-21`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/application.yml#L19-L21)
    `username: ${MYSQL_USER:root}`、`password: ${MYSQL_PASSWORD:}`
- 空口令启动守卫：
  - [`DataSourcePasswordGuard.java`](file:///f:/blue-whale/whale-backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/security/DataSourcePasswordGuard.java#L47-L59)
  - [`DataSourcePasswordGuardAutoConfiguration.java`](file:///f:/blue-whale/whale-backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/security/DataSourcePasswordGuardAutoConfiguration.java#L17-L31)
- 非 dev 环境 JWT 占位密钥/Cookie Secure 启动拒绝：
  - [`SecretPlaceholderChecker.java`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-infrastructure/src/main/java/com/bluewhale/auth/config/SecretPlaceholderChecker.java#L37-L50)
- 已有 AES-GCM 工具类（但**未接入配置解析**）：
  - [`AesCrypto.java`](file:///f:/blue-whale/whale-backend/whale-common/whale-security/src/main/java/com/bluewhale/security/crypto/AesCrypto.java#L73-L94)

**未实现/风险**

1. 全仓无 `jasypt` 依赖、无 `ENC(...)` 解析、无加密密钥配置项，因此「配置文件里的密码用 Jasypt 加密」**不成立**。
2. 当前模式是「环境变量/`.env` 明文」：
   - [`infrastructure/docker/.env.example:19-22`](file:///f:/blue-whale/infrastructure/docker/.env.example#L19-L22) 直接给出 `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` 占位；
   - [`infrastructure/docker/.env.example:40`](file:///f:/blue-whale/infrastructure/docker/.env.example#L40) `REDIS_PASSWORD`；
   - [`infrastructure/docker/.env.example:55`](file:///f:/blue-whale/infrastructure/docker/.env.example#L55) `JWT_SECRET`；
   - [`infrastructure/docker/.env.example:63-64`](file:///f:/blue-whale/infrastructure/docker/.env.example#L63-L64) RabbitMQ 账号口令。
   这些值会以明文存在于部署机 `.env`、容器环境变量、进程环境与部分诊断输出中。
3. 仍有弱默认值，虽然非 dev 守卫会拦一部分，但不同服务口径不一致：
   - auth RabbitMQ `guest/guest`：[`application.yml:42-43`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/application.yml#L42-L43)
   - JWT 默认占位：[`application.yml:63`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/application.yml#L63)、网关 [`application.yml:174`](file:///f:/blue-whale/whale-backend/whale-services/whale-gateway/src/main/resources/application.yml#L174)
   - 告警 Webhook 默认令牌：[`whale-alert/application.yml:52`](file:///f:/blue-whale/whale-backend/whale-ops/whale-alert/src/main/resources/application.yml#L52)
   - 合同回调默认密钥：[`whale-contract/application.yml:82`](file:///f:/blue-whale/whale-backend/whale-apps/whale-contract/src/main/resources/application.yml#L82)

**结论**：不满足「Jasypt 加密配置」要求；但「敏感值不硬编码、外置环境变量」已部分满足，可作为迁移基础。

### 2.2 安全哈希

**已实现**

- BCrypt 编码器 Bean：[`SecurityConfig.java:70-73`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-infrastructure/src/main/java/com/bluewhale/auth/config/SecurityConfig.java#L70-L73)
- 应用层编码端口适配：[`PasswordHasherAdapter.java:23-29`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-infrastructure/src/main/java/com/bluewhale/auth/infrastructure/security/PasswordHasherAdapter.java#L23-L29)
- 领域层只接受 BCrypt 密文：[`User.java:265-271`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-domain/src/main/java/com/bluewhale/auth/domain/user/aggregate/User.java#L265-L271)
- 登录比对由 Spring Security `AuthenticationManager` + `UserDetailsService` 完成：[`SpringSecurityCredentialAuthenticator.java:36-37`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-infrastructure/src/main/java/com/bluewhale/auth/infrastructure/adapter/auth/SpringSecurityCredentialAuthenticator.java#L36-L37)
- 数据库字段注释明确 BCrypt：[`init.sql:42`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/init.sql#L42)
- 检查清单已把 BCrypt 作为基线：[`code-review-checklist.md:50`](file:///f:/blue-whale/docs/checklists/code-review-checklist.md#L50)

**不足**

1. `new BCryptPasswordEncoder()` 默认 `strength=10`，属可用但非推荐上限；没有 `upgradeEncoding` 登录自动重哈希。
2. 后端口令强度与前端不一致：
   - 前端强制 12 位且含大小写+数字：[`MustChangePasswordDialog.tsx:16-29`](file:///f:/blue-whale/whale-frontend/shared-packages/whale-auth/src/MustChangePasswordDialog.tsx#L16-L29)
   - 后端仍允许 6 位：[`ChangePasswordRequest.java:15-17`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/dto/ChangePasswordRequest.java#L15-L17)、[`UserCreateRequest.java:20-21`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/dto/UserCreateRequest.java#L20-L21)、[`UserUpdateRequest.java:14-15`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/dto/UserUpdateRequest.java#L14-L15)、[`TenantCreateRequest.java:40`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/dto/TenantCreateRequest.java#L40)
3. 无口令历史/重用限制、无常见弱口令字典校验、无「不得与用户名相同」校验。

**结论**：「采用安全哈希」✅ 已满足（BCrypt）；但建议按本计划补齐强度策略与自动升级，不改变「BCrypt + 可平滑迁移」的主路线。

### 2.3 数据库脚本与 Flyway

**现状**

- 全仓无 `flyway-core` / `flyway-mysql` 依赖，无 `flyway_schema_history` 表，无自动迁移配置。
- 既有设计明确暂不引入：
  - [`docs/design/multi-database.md:17`](file:///f:/blue-whale/docs/design/multi-database.md#L17)：DDL 靠手工/容器 init 脚本执行；
  - [`docs/design/multi-database.md:37-39`](file:///f:/blue-whale/docs/design/multi-database.md#L37-L39)：第一步保留手工执行 + 启动自检，第二步评估 Flyway；
  - [`docs/design/multi-database.md:84`](file:///f:/blue-whale/docs/design/multi-database.md#L84)：Flyway Community 对 DM/KingBase/openGauss 支持有限；
  - [`docs/devlog/2026-10-17.md:119`](file:///f:/blue-whale/docs/devlog/2026-10-17.md#L119)：项目未引入 Flyway/Liquibase。
- 脚本现状（按服务库分目录，`src/main/resources/db/`）：

| 服务库 | 脚本数 | 备注 |
|---|---:|---|
| `bluewhale_auth` | 37 | `init.sql` + 36 个历史迁移，命名从 `migration-5.34` 到 `migration-5.76`，另有 `migration-2026-10-30-*` |
| `bluewhale_workflow` | 7 | 多份增量脚本 |
| `bluewhale_agent` | 6 | 多份增量脚本 |
| `bluewhale_form` | 5 | 多份增量脚本 |
| `bluewhale_file` / `bluewhale_doc` / `bluewhale_audit` / `bluewhale_job` / `bluewhale_contract` | 2–4 | 混合 init 与迁移 |
| 其余业务库 | 1–3 | 以 `init.sql` 为主 |
| `whale-i18n-sdk` | 2 | 运行时 `auto-init` 自建表，需单独决策是否纳入 Flyway |
| `whale-gateway` / `whale-monitor` | 0 | 无业务数据源，Flyway 不启用 |

- `init.sql` 结构：含 `DROP TABLE IF EXISTS` + `CREATE TABLE`（auth 侧 15 张表），**不能在存量库直接重放**，否则会清空数据。
- 历史迁移大量依赖人工执行顺序，且 `init.sql` 与迁移是否完全同构缺少门禁；这会导致「新建库」与「存量库」schema 漂移。

**结论**：不满足「数据库脚本有 Flyway」要求；当前属于手工脚本阶段。

### 2.4 定期修改账号密码

**已实现（仅覆盖出厂管理员）**

- 启动时检测种子 admin 密文，命中则用 `SecureRandom` 生成 16 位随机口令、BCrypt 落库、置强制改密、启动日志打印一次：
  - [`AdminPasswordInitializer.java:92-112`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/java/com/bluewhale/auth/bootstrap/AdminPasswordInitializer.java#L92-L112)
  - [`AdminPasswordInitializer.java:163-179`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/java/com/bluewhale/auth/bootstrap/AdminPasswordInitializer.java#L163-L179)
- `sys_user.must_change_password` 列及幂等迁移：
  - [`init.sql:43`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/init.sql#L43)
  - [`migration-2026-10-30-admin-random-password.sql:15-27`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-2026-10-30-admin-random-password.sql#L15-L27)
- 登录/刷新响应透出 `mustChangePassword`；前端不可关闭弹窗：
  - [`LoginResponse.java:30`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-application/src/main/java/com/bluewhale/auth/vo/LoginResponse.java#L30)
  - [`MustChangePasswordDialog.tsx`](file:///f:/blue-whale/whale-frontend/shared-packages/whale-auth/src/MustChangePasswordDialog.tsx)

**未实现/关键缺口**

1. **无「最后改密时间」字段**：`sys_user` 仅有 `last_login_time`，无法计算口令年龄：
   - [`init.sql:38-57`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/init.sql#L38-L57)
2. **无到期判定**：登录流程只做凭据校验、记录登录时间、签发令牌，不检查口令是否到期：
   - [`SessionAppService.java:81-91`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-application/src/main/java/com/bluewhale/auth/application/auth/SessionAppService.java#L81-L91)
3. **无定时任务**：auth 仅有权限缓存重对齐任务，无口令到期扫描/提醒。
4. **无自助改密接口**：当前唯一改密接口是管理端重置：
   - [`UserController.java:139-146`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/UserController.java#L139-L146)
   - 前端首登弹窗也调用该接口，但普通用户没有 `sys:user:resetpwd`，存在「被要求改密却无权改密」的权限悖论。
5. **管理员重置反而清除强制改密标记**：
   - [`UserAppService.java:189-196`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-application/src/main/java/com/bluewhale/auth/application/user/UserAppService.java#L189-L196)（`clearMustChangePassword`）
   - [`UserAppService.java:140-145`](file:///f:/blue-whale/whale-backend/whale-services/whale-service-auth/whale-service-auth-application/src/main/java/com/bluewhale/auth/application/user/UserAppService.java#L140-L145)（更新用户时同样清除）
   这会导致管理员重置后，用户仍可用管理员设定的临时口令长期使用。
6. **无口令历史/重用限制**，无法满足「定期修改且不得复用近期口令」的常见合规口径。
7. **无服务端强制阻断**：`mustChangePassword` 仅用于前端提示，用户可绕过前端直接调用其他接口。

**结论**：只实现了「出厂默认管理员口令的一次性轮换 + 首登提示」，未实现「所有账号定期改密/到期强制改密」。

---

## 三、实施目标与关键决策

### 3.1 目标

1. **配置敏感值**：仓库/配置中心的敏感值可写为 `ENC(...)` 密文，密钥只从环境变量或密钥文件注入；非 dev 缺密钥/错密钥 fail-fast。
2. **安全哈希**：保持 BCrypt，提升 cost 并支持登录自动升级；所有口令入口统一服务端强度策略。
3. **数据库版本化**：每个有数据源的服务由 Flyway 管理 schema，空库可一键建库，存量库可 baseline 后增量升级，校验和不匹配即失败。
4. **定期改密**：所有人工账号支持 90 天（可配置）到期强制改密、提前提醒、自助改密、历史口令限制；管理员重置后目标账号下次登录必须改密。

### 3.2 关键决策（建议先拍板）

| 决策 | 结论（2026-11-01 已确认） | 理由/落地方式 |
|---|---|---|
| Jasypt 版本与 Boot 4 兼容 | **采用 `org.jasypt:jasypt:1.9.3` 核心加密库 + 自研 `EnvironmentPostProcessor`，不引入第三方 starter** | 第三方 `jasypt-spring-boot-starter:3.0.5` 的自动配置面向 Boot 2/3，Boot 4.0.8 下存在装配风险；自研 EPP 仅依赖 `StandardPBEStringEncryptor`，语法保持 `ENC(...)` |
| 密钥来源 | `WHALE_CONFIG_ENC_KEY` 环境变量 / K8s Secret / 挂载文件；禁止写入 yml、Nacos 明文、日志 | 与现有「环境变量注入」路线一致 |
| 哈希算法 | 继续 BCrypt，`strength=12`，登录时自动升级旧 hash | 存量 `$2a$` 密文可平滑匹配 |
| Flyway 范围 | 先 `whale-service-auth` 试点，本期只保证 MySQL 8.x | 国产库按 `docs/design/multi-database.md` 的 vendor 策略另立任务 |
| Flyway baseline | `V1__baseline.sql`（无 DROP/TRUNCATE）+ 存量库 `baseline-on-migrate=true`；后续只增 V2+ | 存量库不重建、不丢数据 |
| 口令有效期 | 全局默认 90 天、提前 7 天提醒、历史 5 次；**口令有效期与提醒天数允许租户管理员自助配置（1–365 天，REQ-SEC-024）**，未配置回退全局默认，不允许租户关闭到期强制 | 常见合规基线 + 多租户差异化诉求；应急关闭仅保留全局 `max-age-days<=0` |
| 管理员重置行为 | 系统生成一次性随机临时口令，响应仅展示一次，并置 `must_change_password=1` | 修复「重置后不再强制改密」缺口，同时避免管理员自定义弱口令 |
| 强制改密阻断层 | auth 服务内拦截 + 网关按 JWT 声明拦截 | 与现有网关/内网边界模型一致 |
| 中间件账号 | 不纳入自动定期改密；仅提供运维轮换手册 | 中间件口令轮换涉及停机窗口，另立运维任务 |

---

## 四、实施计划

### 阶段 0：登记与可行性验证（0.5–1 人日）

| 任务 | 内容 | 产出/验收 |
|---|---|---|
| SEC-00-1 | 在 `docs/requirements/register.md` 登记 `REQ-SEC-020`（Jasypt）、`REQ-SEC-021`（哈希加固）、`REQ-SEC-022`（Flyway）、`REQ-SEC-023`（定期改密） | 四个需求编号、优先级、验收口径可追溯（已完成 2026-11-01） |
| SEC-00-2 | 依赖可用性验证：`org.jasypt:jasypt:1.9.3`、Boot 4 管理的 `flyway-core` + `flyway-mysql` 能解析并启动 | Maven 镜像可达性已验证（2026-11-01：jasypt 1.9.3 / flyway 元数据均 200）；编译与启动验证随阶段 1/2 完成 |
| SEC-00-3 | 明确密钥托管方式、口令策略参数、Flyway 试点范围 | 决策已写入本文档「3.2」（2026-11-01 确认） |

### 阶段 1：Jasypt 配置加密（2–3 人日）——✅ 已完成（2026-11-01）

| 任务 | 涉及文件 | 实施要点 | 验收 |
|---|---|---|---|
| SEC-01-1 | `whale-backend/whale-parent/pom.xml`、`whale-spring-boot-starter/pom.xml`、`whale-gateway/pom.xml` | 引入 Jasypt starter（gateway 因不依赖公共 starter，需单独引入）；版本统一管理 | 依赖树无冲突，`mvn validate` 通过 |
| SEC-01-2 | 各服务 `application.yml` | 增加 `jasypt.encryptor.*`：`password=${WHALE_CONFIG_ENC_KEY:}`、`algorithm=PBEWITHHMACSHA512ANDAES_256`、随机 IV/Salt、`property.prefix=ENC(` / `suffix=)` | dev 可启动；非 dev 缺密钥时给出明确中文报错 |
| SEC-01-3 | `whale-spring-boot-starter` 新增 `ConfigEncryptionGuard` | 非 dev 且 `whale.config.encryption.enabled=true` 时校验密钥非空、非占位、长度达标；禁止把密钥打印到日志 | 单测覆盖缺密钥/占位/短密钥/正常四类 |
| SEC-01-4 | 敏感配置迁移 | 先迁移第三方类密钥（合同 app-secret、告警 webhook token、SMS token、MinIO access key、TSA/P12 口令等）；数据源口令保留 `SPRING_DATASOURCE_PASSWORD` 环境变量覆盖能力 | 仓库内无新增明文；`ENC(...)` 能被正确解密 |
| SEC-01-5 | 新增加密工具 | `scripts/security/encrypt-secret.ps1` + 一个 `main` 工具类（读取明文/旧密钥/新密钥，输出 `ENC(...)`）；支持批量重加密 | 运维可按文档生成密文；不把明文写入历史文件 |
| SEC-01-6 | 兼容性兜底 | 若 Jasypt 3.0.5 在 Boot 4 下无法解密/启动，改用 `EnvironmentPostProcessor` + 现有 [`AesCrypto`](file:///f:/blue-whale/whale-backend/whale-common/whale-security/src/main/java/com/bluewhale/security/crypto/AesCrypto.java) 包装 PropertySource，保持 `ENC(...)` 语法 | POC 报告记录「采用 Jasypt / 采用自研兜底」结论 |

**关键验证**

1. 用 `ENC(...)` 覆盖一个测试配置（如 `spring.datasource.password`），以 `WHALE_CONFIG_ENC_KEY` 启动 auth，能正常连库、登录。
2. 错误密钥启动 → 失败且不泄露密钥/密文；缺失密钥非 dev → 失败。
3. 环境变量覆盖优先于密文（`SPRING_DATASOURCE_PASSWORD`）行为符合预期。
4. 密钥轮换演练：旧密文用旧密钥可启动 → 工具重加密 → 新密钥启动成功。

### 阶段 2：Flyway 试点与基线（5–8 人日）——✅ auth 试点已完成（2026-11-01）

#### 2.1 试点选择

先选 `whale-service-auth`（`bluewhale_auth`）：
- 它拥有最多脚本（37 个）和权限种子，最能暴露基线问题；
- 后续「定期改密」也要在 auth 库加表，可直接接在 Flyway V2 之后。

#### 2.2 基线生成步骤

1. 在一次性 MySQL 库中执行当前 `init.sql`。
2. 按版本顺序执行该库全部 `migration-*.sql`（auth 从 5.34 到 5.76，含 2026-10-30 脚本）。
3. 导出最终 schema（表、列、索引、注释）与种子数据（`sys_permission`/`sys_menu`/角色授权等）。
4. 生成 `src/main/resources/db/migration/V1__baseline.sql`：
   - 删除所有 `DROP TABLE IF EXISTS`；
   - `CREATE TABLE` 改为 `CREATE TABLE IF NOT EXISTS`；
   - 种子统一 `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE`，保证幂等；
   - 不包含任何 `DELETE`、`TRUNCATE`、`DROP`。
5. 审计 36 个历史迁移，分类：
   - **纯 DDL**：折叠进 V1；
   - **权限/菜单/角色种子**：折叠进 V1 或独立 `R__auth_permission_seed.sql`（重复运行幂等）；
   - **一次性数据回填**：折叠进 V1，或改写为带 `WHERE NOT EXISTS` 的幂等 V2；
   - **纯注释/校验脚本**：移入 `db/legacy/` 归档，不参与 Flyway。
6. 历史脚本移动到 `src/main/resources/db/legacy/`（或保留但确保不在 `spring.flyway.locations` 扫描路径内），并加 `db/legacy/README.md` 说明「仅供追溯，不再执行」。

#### 2.3 配置

| 配置 | 建议值 |
|---|---|
| `spring.flyway.enabled` | `true`（仅试点服务；gateway/monitor 无数据源不启用） |
| `spring.flyway.locations` | `classpath:db/migration` |
| `spring.flyway.baseline-on-migrate` | `true`（存量库自动 baseline；空库仍执行 V1） |
| `spring.flyway.baseline-version` | `1` |
| `spring.flyway.validate-on-migrate` | `true` |
| `spring.flyway.clean-disabled` | `true` |
| `spring.flyway.placeholder-replacement` | `false`（权限 path 中可能含 `${}` 类片段） |
| `spring.flyway.table` | `flyway_schema_history`（默认） |
| `spring.flyway.connect-retries` | `10`（容器启动竞态） |

#### 2.3.1 实施结果（2026-11-01）

- **依赖**：auth starter 增加 `spring-boot-starter-flyway` + `flyway-mysql`（版本由 Spring Boot 4.0.8 BOM 管理）。
- **配置**：按上表落地到 `whale-service-auth-starter/src/main/resources/application.yml`，`enabled` 支持 `WHALE_FLYWAY_ENABLED` 应急开关。
- **脚本**：
  - `db/migration/V1__baseline.sql`：由 `db/init.sql` 转换（去 `DROP TABLE`、`CREATE TABLE IF NOT EXISTS`、种子 `INSERT IGNORE`、显式 `InnoDB`/`utf8mb4`）；
  - `db/migration/V2__password_lifecycle.sql`：改密/提醒时间列 + 回填 + 口令历史表（守卫式幂等）；
  - `db/migration/V3__tenant_password_policy.sql`：租户口令策略表 + 权限 172/173 + `tenant_admin` 授权 + 菜单 36（`NOT EXISTS` 幂等）。
- **历史脚本**：**未物理移动到 `db/legacy/`**（避免打断大量文档与冒烟脚本对 `migration-*.sql` 路径的引用），保留在 `db/` 根目录且不在 `spring.flyway.locations` 扫描路径内；新增 `db/README.md` 说明「仅供追溯、不参与自动迁移」，并登记「其他 Wave 模块种子尚未折叠进 V1」的后续任务。
- **单测门禁**：`FlywayMigrationScriptTest`（4 例）校验脚本命名/版本递增、无 `DROP`/`TRUNCATE`/`DELETE`、V1 与 `init.sql` 表集合一致、口令脚本关键对象齐全。
- **已知差异**：空库首次执行 V2/V3 时，`CREATE TABLE IF NOT EXISTS` 会命中 MySQL 1050 note，Flyway 以 WARN 记录（不失败）；存量库与重复执行不受影响。

#### 2.4 验收

1. **空库** ✅：仅建库 → 启动 auth → Flyway 自动建 17 表 + 历史表 + i18n 表并写入种子（1 用户 / 18 菜单 / 116 权限，含 172/173 与菜单 36）。
2. **存量库** ✅：先导入 `init.sql` 再启动 → `flyway_schema_history` 写入 version=1 BASELINE，仅执行 V2/V3，存量账号与授权保留，`password_updated_time` 回填为 `create_time`。
3. **校验和** ✅：在副本目录追加一行注释后重跑 → `FlywayValidateException: Migration checksum mismatch for migration version 2`。
4. **并发** ✅（口径修正）：两实例同时启动不会重复执行同一版本、历史表无重复记录；Flyway 按单个迁移加锁，两个实例可能**分摊**版本（实测 V1/V3 与 V2 分别由两个进程执行），最终版本一致。原「只有一个执行迁移」表述按此修正。
5. **安全** ✅：`clean-disabled=true`；单测门禁禁止 `DROP`/`TRUNCATE`/`DELETE`；`db/` 根目录历史脚本不在扫描路径。
6. **CI** ⏳：`flyway:migrate`/`validate` 的空库/存量库 job 与 Testcontainers 待补（阶段 4）。

#### 2.5 铺开顺序（试点通过后）

1. 小库：`whale-config`、`whale-audit`、`whale-alert`。
2. 核心：`whale-workflow`、`whale-file`、`whale-form`、`whale-agent`、`whale-doc`。
3. 业务应用：`whale-erp`、`whale-mes`、`whale-pdm`、`whale-oa`、`whale-pm`、`whale-contract`、`whale-pay`、`whale-message`、`whale-im`、`whale-music`、`whale-archive`、`whale-job`。
4. `whale-i18n-sdk` 的 `sys_i18n_message` 单独决策：要么纳入各库 V1，要么关闭运行时 `auto-init`；二者必须选一，避免双写。

每个库重复「基线生成 → 空库验证 → 存量库 baseline 验证 → 冒烟」四步。

### 阶段 3：口令策略与定期改密（5–7 人日）——✅ 已完成（2026-11-01，含租户策略与一次性临时口令）

#### 3.1 数据模型（Flyway V2）

在 `bluewhale_auth` 增加：

| 变更 | 说明 |
|---|---|
| `sys_user.password_updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` | 最近改密时间；存量用户回填 `COALESCE(update_time, create_time, NOW())` |
| `sys_user.password_notice_time DATETIME NULL` | 最近一次到期提醒时间，避免重复骚扰 |
| `sys_password_history` | `id/tenant_id/user_id/password/create_time`，保留最近 N 条；只存 BCrypt 密文 |
| `sys_tenant_password_policy` | 租户口令策略（`tenant_id` 唯一：`max_age_days` / `warn_days`），未配置回退全局默认（REQ-SEC-024） |

要求：幂等迁移、存量回填、无 `DROP`、不改变既有 `must_change_password` 语义。

#### 3.2 领域与应用层

| 任务 | 内容 |
|---|---|
| 口令策略值对象 | `PasswordPolicy`（最小长度 12、大小写/数字/特殊字符、不得含用户名、不得为常见弱口令） |
| 策略配置 | `whale.auth.password.*`：`max-age-days=90`、`warn-days=7`、`history-size=5`、`min-length=12`、`require-upper/lower/digit/special=true`、`force-change-on-create=true` |
| 哈希端口扩展 | `PasswordHasher#matches(raw, encoded)`、`upgradeEncoding(encoded)`；Adapter 委托 `PasswordEncoder.matches/upgradeEncoding` |
| 聚合行为 | `User` 增加 `passwordUpdatedTime`；`changePassword` 同步更新时间；新增 `isPasswordExpired(now, maxAgeDays)`；`reconstitute` 增加字段映射 |
| 策略服务 | `PasswordPolicyService` 统一校验创建/更新/重置/租户开通管理员口令；保存历史并清理超出 N 条 |
| 自助改密 | 新增 `PUT /auth/password`，请求 `{oldPassword,newPassword}`；校验旧口令 → 策略校验 → 历史校验 → 改密 → 撤销令牌 → 审计 |
| 管理员重置 | `PUT /user/{id}/password`：系统生成一次性随机临时口令（16 位、必含四类字符），BCrypt 落库 + 写历史 + `markMustChangePassword()`，返回 `ResetPasswordVO`；明文仅本次响应展示、不落库、不写审计参数 |
| 创建/更新用户 | 初始口令默认置强制改密；更新用户口令同样置强制改密 |

#### 3.3 登录与阻断

1. 登录成功后加载 `User`，计算：
   - `mustChange = user.isMustChangePassword() || user.isPasswordExpired(now, maxAgeDays)`；
   - 响应 `mustChangePassword=true`，并增加 `passwordExpiresInDays`/`passwordExpired` 可选字段（保持向后兼容）。
2. JWT 增加声明 `pwd_change_required=true`：
   - 网关 `JwtAuthGlobalFilter` 对该声明只放行 `/auth/password`、`/auth/logout`、`/auth/refresh`、`/auth/me`，其余路由返回 403 `PASSWORD_CHANGE_REQUIRED`；
   - auth 服务自身加同等拦截，防止直连绕过。
3. 改密成功后：
   - 清除 `must_change_password`；
   - 写历史、更新 `password_updated_time`；
   - 触发既有 `UserDomainEvent(PASSWORD_CHANGED)`，撤销所有租户存量 access/refresh token（已有能力，见 `REQ-SEC-012/014`）。
4. 管理员重置/创建用户后，目标用户下次登录即被强制改密。

#### 3.4 到期提醒

- `@Scheduled(cron="${whale.auth.password.notice-cron:0 0 2 * * ?}")` 每日扫描：
  - 条件：`status=1`、`password_updated_time <= now - (租户 maxAgeDays - warnDays)`、`password_notice_time` 为空或早于最近改密时间；
  - **多租户口径**：先取全部启用租户的最短提醒窗口做一次跨租户扫描，再按候选账号所属租户策略二次筛选；
  - 当前实现输出 WARN 日志（含租户/用户/剩余或超期天数），供日志采集侧配置通知通道；失败只记日志，不阻断调度；
  - 成功后更新 `password_notice_time`，同一口令周期只提醒一次。
- 范围外：中间件账号（MySQL/Redis/RabbitMQ/Nacos）不进入扫描与提醒。

#### 3.5 前端

- 将 [`MustChangePasswordDialog.tsx`](file:///f:/blue-whale/whale-frontend/shared-packages/whale-auth/src/MustChangePasswordDialog.tsx) 改为调用 `PUT /auth/password`，增加「当前密码」输入；保留 12 位+复杂度提示。
- 登录响应新增字段后，各登录页无需改动（继续消费 `mustChangePassword`）；到期账号同样弹出不可关闭弹窗。
- 管理端用户页重置改为「确认生成一次性临时口令」+ 结果弹窗（可复制、关闭后不可再查看）；新建/编辑用户密码规则提升为 12 位。
- 管理端新增「口令策略」页（`whale-frontend/admin/src/pages/system/password-policy/index.tsx`）与 `api/tenant.ts` 策略接口；`dynamicRoutes` 注册契约键 `password-policy`，对应菜单 36（`/system/password-policy`）。

### 阶段 4：验证、文档与 CI（2–3 人日）

| 任务 | 内容 |
|---|---|
| SEC-04-1 | auth 单测：策略校验、到期计算、历史校验、自助改密、管理员重置置标记、登录标志、网关声明拦截 | ✅ 已完成（auth 471 / gateway 122 全绿） |
| SEC-04-2 | 实机 IT（MySQL）：Flyway V1/V2、存量 baseline、空库建表、到期登录、改密后旧令牌 401 | ✅ Flyway 部分已完成（2026-11-01 空库/存量/checksum/并发/真启动均实测；**阶段 4 铺开 20 个库空库/存量库实机验证**）；到期登录与改密后旧令牌 401 实机 IT 待补 |
| SEC-04-3 | 前端单测/E2E：首登弹窗新接口、旧密码必填、改密后重新登录 | ⏳ `whale-admin` / `whale-auth` lint+build 已通过；单测/E2E 与浏览器联调待补 |
| SEC-04-4 | 文档更新：`docs/requirements/register.md`、`docs/design/security-baseline.md`（新建）、`docs/design/multi-database.md`（Flyway 决策回填）、`docs/service-catalog.md`（新增环境变量）、`docs/devlog/2026-11-01.md`、检查报告 | ✅ 已完成（2026-11-01，含阶段 4 铺开口径回填） |
| SEC-04-5 | `.env.example` 增补 `WHALE_CONFIG_ENC_KEY`、`WHALE_FLYWAY_*`、`WHALE_AUTH_PASSWORD_*`，并标注「生产必须由密钥管理系统注入」 | ✅ 已完成 |
| SEC-04-6 | CI：`mvn validate` + 单测 + Flyway 空库/存量库校验 + 前端 lint/build；密钥仅用 CI Secret，日志掩码 | ⏳ auth 单测 + 前端 lint/build 已具备；Flyway 空库/存量库 CI job 待补 |

#### 阶段 4 铺开结果（2026-11-01）

| 范围 | 模块 | 结果 |
|---|---|---|
| 核心服务库（5） | `whale-workflow` / `whale-form` / `whale-agent` / `whale-doc` / `whale-service-file-web` | 各新增 `V1__baseline.sql`（5/9/10/18/2 表）+ `db/README.md` + 迁移单测；workflow 另有 `V2__task_operator.sql` 补齐 `operator_*` 三列（收敛自 `migration-5.63`） |
| 业务应用库（12） | `whale-contract` / `whale-erp` / `whale-mes` / `whale-oa` / `whale-pdm` / `whale-pm` / `whale-archive` / `whale-im` / `whale-job` / `whale-message` / `whale-music` / `whale-pay` | 各新增 `V1__baseline.sql`（11/4/2/2/2/13/9/7/5/5/14/4 表）+ `db/README.md` + 迁移单测；`whale-contract` 另有 `V2__esign_callback.sql` 补齐 `ct_esign_callback` 与 `workflow_task_id/sign_url`（收敛自 `migration-5.63` / `migration-5.67`） |
| video（M0 已用 Flyway） | `whale-video` | 空库 `APPLIED=3`（V1 16 表 + V2 + V3），20 张表含历史表，`vd_series/vd_movie/vd_video` 就绪 |

- 统一接入口径与 auth 试点一致：`spring-boot-starter-flyway` + `flyway-mysql`、`locations=classpath:db/migration`、`baseline-on-migrate`/`baseline-version=1`/`validate-on-migrate=true`/`clean-disabled=true`、`WHALE_FLYWAY_ENABLED` 应急开关。
- 每库单测两用例：脚本命名与版本唯一递增、无 `DROP/TRUNCATE/DELETE`；V1 建表集合与 `db/init.sql` 一致（另断言关键表/列存在）。
- 实机验证：20 个库（8 个核心库 = auth 试点 + 5 个铺开 + config/audit/alert；12 个业务库）空库 `APPLIED` 与版本数一致，存量库先导 `init.sql` 后 baseline 数据无损、表数不变；验证后临时库全部 DROP。
- 单测：核心库 5 模块 `BUILD SUCCESS`（workflow 155 / form 118 / agent 136 / doc 18 / file-web 2）；业务库 12 模块 `BUILD SUCCESS`（合计 656，每库新增 2 例）；Checkstyle 均 0。

---

## 五、验收矩阵（汇总）

| 编号 | 验收项 | 判定方式 |
|---|---|---|
| A-01 | 仓库配置无明文敏感值 | 全仓检索 `password:`/`secret:`/`app-secret:` 无真实明文；仅 `ENC(...)`/环境变量占位 |
| A-02 | Jasypt 解密链路可用 | 加密数据源口令启动成功；错密钥/缺密钥失败 |
| A-03 | 口令哈希安全 | 数据库仅存 BCrypt；登录比对成功；新 hash `strength=12`；旧 hash 登录后自动升级 |
| A-04 | 服务端口令策略生效 | 6 位弱口令、含用户名、复用近 5 次口令均被拒绝（HTTP 400） |
| A-05 | 空库 Flyway 一键建库 | 新库启动后表/种子齐全，登录冒烟通过 |
| A-06 | 存量库 baseline 无损 | 存量数据保留；`flyway_schema_history` 有 baseline；无 DROP/TRUNCATE |
| A-07 | 校验和门禁 | 篡改已执行脚本后启动失败 |
| A-08 | 90 天到期强制改密 | 模拟 `password_updated_time` 超期 → 登录返回 `mustChangePassword=true`；其他接口 403，改密后恢复 |
| A-09 | 管理员重置闭环 | 重置后目标用户下次登录必须改密；改密后旧令牌失效 |
| A-10 | 到期提醒 | 定时任务按租户策略输出即将到期/已过期告警日志；重复扫描不重复提醒 |
| A-11 | 审计与指标 | 改密/重置/策略配置均有审计（不含口令参数）；到期登录/提醒有日志留痕 |
| A-12 | 文档与需求可追溯 | 五个 REQ 编号（020–024）、设计文档、devlog、检查报告齐备 |

---

## 六、风险、回滚与注意事项

| 风险 | 影响 | 缓解/回滚 |
|---|---|---|
| Jasypt 3.0.5 与 Spring Boot 4.0.8 不兼容 | 服务无法启动 | 阶段 0 POC；失败即切自研 `EnvironmentPostProcessor` + `AesCrypto`，语法仍为 `ENC(...)` |
| Flyway baseline 与真实 schema 漂移 | 新库缺列/存量库重复 DDL | 基线由「init.sql + 全量迁移」生成；增加 CI schema diff；试点 auth 先验收 |
| `init.sql` 的 `DROP TABLE` 被误纳入迁移 | 清空生产数据 | 基线脚本强制移除 DROP/TRUNCATE；`clean-disabled=true`；代码评审专项检查 |
| 多实例同时迁移 | 锁冲突/重复执行 | Flyway 数据库锁 + `connect-retries`；启动前健康检查 |
| 国产库支持不足 | 后续 DM/KingBase 无法直接迁移 | 本期只覆盖 MySQL；保留 `db/{vendor}` 规划，国产库另立任务 |
| 口令到期导致大面积锁定 | 用户无法登录 | 存量用户回填 `password_updated_time`；先提醒后强制；提供管理员重置；`max-age-days` 可配置/可临时关闭 |
| 管理员重置临时口令泄露 | 账号被冒用 | 临时口令一次性展示、强制首登改密、审计留痕 |
| 通知服务不可用 | 提醒丢失 | 失败仅告警；管理端列表兜底；不阻断登录 |
| 密钥轮换窗口 | 旧密文无法解密 | 提供批量重加密工具与双人复核；维护窗口切换新密钥；保留回滚密钥 |
| 直接连业务端口绕过网关 | 强制改密可被绕过 | auth 服务内同样拦截；直连端口按既有网络边界收口 |

---

## 七、工作量与建议排期

| 阶段 | 内容 | 预估 |
|---|---|---|
| 0 | 需求登记、依赖 POC、决策 | 0.5–1 人日 |
| 1 | Jasypt 接入、守卫、工具、敏感配置迁移 | 2–3 人日 |
| 2 | Flyway auth 试点 + 基线 | 5–8 人日 |
| 3 | 口令策略、定期改密、前端、提醒 | 5–7 人日 |
| 4 | 全服务 Flyway 铺开 + 验证/文档/CI | 5–10 人日（与阶段 2/3 可并行部分） |
| **合计** | | **约 18–29 人日**（不含真实环境运维窗口） |

建议顺序：**阶段 0 → 1 → 3（先做 auth 代码，迁移脚本暂以手工版验证）→ 2（Flyway 试点后把阶段 3 的 DDL 收为 V2）→ 4**。若必须严格 Flyway 先行，则阶段 2 完成后立即接阶段 3。

---

## 八、待确认问题

1. Jasypt 是否必须使用第三方 starter，还是允许仓库自研 `ENC(...)` 解密（推荐先 POC，失败即兜底）？ → ✅ 已定：`jasypt:1.9.3` 核心库 + 自研 `EnvironmentPostProcessor`。
2. 口令有效期是否统一 90 天、提前 7 天提醒、历史 5 次？是否需要按租户/角色差异化？ → ✅ 已定：全局默认 90/7/5，租户管理员可覆盖（1–365 天）。
3. 管理员重置口令时，是由管理员自定义临时口令，还是系统生成一次性随机口令并在响应中只展示一次？ → ✅ 已定：系统生成一次性随机口令，仅响应展示一次。
4. Flyway 本期是否只要求 MySQL？国产库（DM/KingBase/openGauss）是否另立任务？ → ✅ 已定：本期只覆盖 MySQL，国产库另立任务。
5. 配置密文的密钥托管方式：部署机环境变量、Docker Secret、K8s Secret，还是挂载文件？ → ✅ 已定：`WHALE_CONFIG_ENC_KEY` 环境变量 / K8s Secret / 挂载文件，禁止写入 yml 与配置中心明文。
6. 服务账号/中间件账号（MySQL、Redis、Nacos、RabbitMQ）是否需要纳入「定期改密」运维流程？本计划默认覆盖人工账号，中间件口令只提供运维轮换手册。 → ✅ 已定：**不纳入自动定期改密**，仅提供人工轮换手册（见安全基线设计 §六）。

---

## 九、附：本计划涉及的主要文件

**后端**

- `whale-backend/whale-parent/pom.xml`
- `whale-backend/whale-common/whale-spring-boot-starter/pom.xml`
- `whale-backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/security/*`
- `whale-backend/whale-common/whale-security/src/main/java/com/bluewhale/security/crypto/AesCrypto.java`（兜底方案复用）
- `whale-backend/whale-services/whale-gateway/pom.xml` + `config/*`（Jasypt 与强制改密拦截）
- `whale-backend/whale-services/whale-service-auth/**`（策略、聚合、接口、定时任务、Flyway）
- 各服务 `src/main/resources/application.yml` 与 `src/main/resources/db/**`

**前端**

- `whale-frontend/shared-packages/whale-auth/src/MustChangePasswordDialog.tsx`
- 各登录页（消费 `mustChangePassword` 不变）
- `whale-frontend/admin` 用户管理页（可选：到期账号/最近改密时间）

**文档与运维**

- `docs/requirements/register.md`
- `docs/design/multi-database.md`
- `docs/design/security-baseline.md`（新建）
- `docs/service-catalog.md`
- `docs/devlog/2026-10-31.md`
- `infrastructure/docker/.env.example`
- `scripts/security/encrypt-secret.ps1`（新建）
- `scripts/ci/*`（Flyway 校验）

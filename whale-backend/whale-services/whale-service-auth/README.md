# whale-service-auth 权限中心

> 认证（Authentication）与授权（Authorization）中心：登录 / 刷新 / 登出、令牌治理、用户 / 部门 / 角色 /
> 菜单 / 权限、多租户与口令生命周期。基于 DDD 四层（接口层 / 应用层 / 领域层 / 基础设施层）多模块工程。

| 项 | 值 |
|---|---|
| 服务名 | `whale-service-auth`（Nacos 注册名同名） |
| 启动类 | [`WhaleAuthApplication`](whale-service-auth-starter/src/main/java/com/bluewhale/auth/WhaleAuthApplication.java) |
| 默认端口 | `8081`（`SERVER_PORT` 可覆盖） |
| 数据库 | MySQL `bluewhale_auth` |
| 缓存 / 锁 | Redis + Redisson（令牌黑名单、刷新轮换、权限缓存广播） |
| 网关前缀 | `/auth/**`、`/user/**`、`/dept/**`、`/role/**`、`/menu/**`、`/permission/**`、`/tenant/**` |
| 契约文档 | `/v3/api-docs`、`/swagger-ui.html`（仅服务直连端口，网关未放行） |

技术选型与版本以 [whale-parent/pom.xml](../../whale-parent/pom.xml) 为唯一事实源，本文不硬编码版本号。

## 一、模块与依赖顺序

### 1.1 模块清单

| 顺序 | 模块 | 职责 | 主要依赖 |
|---|---|---|---|
| 1 | [`whale-service-auth-domain`](whale-service-auth-domain) | 聚合根、实体、值对象、领域事件、仓储端口 | `whale-common-core`、`whale-common-domain` |
| 2 | [`whale-service-auth-application`](whale-service-auth-application) | 用例编排（AppService）、命令 / 查询、视图对象、出站端口 | `-domain`、`whale-common-core`、`whale-spring-boot-starter`、`whale-security` |
| 3 | [`whale-service-auth-infrastructure`](whale-service-auth-infrastructure) | 仓储实现、持久化装配、JWT / 安全、Redis、MQ、领域事件监听 | `-domain`、`-application`、`whale-security`、`whale-auth-sdk`、Web/Security/Redis/Redisson/MyBatis-Plus/AMQP |
| 4 | [`whale-service-auth-interfaces`](whale-service-auth-interfaces) | REST 控制器、请求 DTO、Cookie 管理 | `-application`、`whale-spring-boot-starter`、`whale-auth-sdk`、Web/Validation/Security/SpringDoc |
| 5 | [`whale-service-auth-starter`](whale-service-auth-starter) | 启动类、`application.yml`、Flyway 脚本、i18n 文案、可执行 jar | 上述四层 + Nacos/Flyway/MySQL/AMQP/Actuator/Prometheus/i18n |
| 6 | [`whale-service-auth-architecture-test`](whale-service-auth-architecture-test) | ArchUnit 架构守护（无生产代码） | 上述四层（`test` 作用域）+ ArchUnit |

> Maven Reactor 构建顺序与上表一致；`-starter` 依赖全部四层，`-architecture-test` 仅以 `test` 作用域聚合四层。

### 1.2 依赖方向（核心约束）

```
        ┌──────────────── interfaces ────────────────┐
        │        （controller / dto / web）           │
        └───────────────────┬────────────────────────┘
                            │ 单向依赖
                    ┌───────▼────────┐
                    │  application   │  AppService + Command/Query + Port
                    └───────┬────────┘
                            │ 单向依赖
                    ┌───────▼────────┐
                    │     domain     │  聚合 / 值对象 / 领域事件 / Repository 接口
                    └───────▲────────┘
                            │ 实现端口（依赖倒置）
        ┌───────────────────┴────────────────────────┐
        │              infrastructure                 │
        │  RepositoryImpl / Adapter / Security / MQ   │
        └─────────────────────────────────────────────┘

        starter  ──聚合──▶  domain + application + infrastructure + interfaces
```

三句话记住方向：

1. **interfaces → application → domain**：外层只能向内依赖，领域层不感知任何外层。
2. **infrastructure → application / domain**：基础设施实现应用层端口与领域仓储接口（依赖倒置），不反向被依赖。
3. **starter 只做装配**：启动类与配置不承载业务规则；`architecture-test` 只验证，不参与运行。

### 1.3 架构守护规则

[`AuthArchitectureTest`](whale-service-auth-architecture-test/src/test/java/com/bluewhale/auth/architecture/AuthArchitectureTest.java) 以 ArchUnit 固化以下规则（仅扫描生产类）：

| 规则 | 约束 |
|---|---|
| `domain_must_not_depend_on_frameworks_or_outer_layers` | `domain` 不得依赖 `infrastructure` / `application` / `controller` / `dto` / `vo`，也不得依赖 Spring、MyBatis-Plus、Jakarta |
| `application_must_not_depend_on_infrastructure_or_within_interfaces` | `application` 不得依赖 `infrastructure` / `controller` / `dto`，不得直连 MyBatis、Spring Data Redis、Spring Security |
| `interfaces_must_not_depend_on_infrastructure_or_domain` | 控制器与 DTO 不得依赖 `infrastructure` 与 `domain`，只能经应用层用例访问领域 |
| `distributed_lock_fail_fast_false_only_on_cache_mutex` | `@DistributedLock` 仅允许 `PermissionCacheMutex` 显式关闭 `failFast` |

修改分层边界后必须运行该模块测试，新增放行点需评审并同步白名单。

## 二、目录结构

```
whale-service-auth/
├── pom.xml                                    # 聚合 POM
├── whale-service-auth-domain/                 # 领域层
│   └── src/main/java/com/bluewhale/auth/domain/
│       ├── user/       aggregate/User + entity/UserRoleAssignment + repository/
│       ├── role/       aggregate/Role + entity/RolePermissionAssignment + repository/
│       ├── menu/       aggregate/Menu + repository/
│       ├── permission/ aggregate/Permission、UserPermissionAttribute + repository/
│       ├── dept/       aggregate/Dept + repository/
│       ├── tenant/     aggregate/Tenant、TenantExpiryClock + repository/
│       ├── model/vo/   Username、EncryptedPassword、RoleCode、PermCode
│       └── event/      AuthDomainEvent（sealed）+ 各聚合事件
├── whale-service-auth-application/            # 应用层
│   └── src/main/java/com/bluewhale/auth/application/
│       ├── auth|user|role|menu|permission|dept|tenant|userpermission
│       └── vo/          登录、当前用户、租户、角色、菜单、权限等返回对象
├── whale-service-auth-infrastructure/         # 基础设施层
│   └── src/main/java/com/bluewhale/auth/infrastructure/
│       ├── persistence/{entity,mapper,assembler,repository}
│       │                 MyBatis-Plus 持久化对象（Sys*）、Mapper、装配器、仓储实现
│       ├── adapter|event|cache|mail|password|tenant
│       ├── security/     JWT 过滤器、权限拦截器、Redis 存储、UserDetails
│       ├── jwt/          JwtTokenProvider
│       └── config/       SecurityConfig、JwtProperties、Cookie/缓存配置
├── whale-service-auth-interfaces/             # 接口层
│   └── src/main/java/com/bluewhale/auth/interfaces/
│       ├── controller/   10 个 REST 控制器
│       ├── dto/          请求对象
│       └── web/          AuthCookieManager
├── whale-service-auth-starter/                # 启动与资源
│   └── src/main/
│       ├── java/com/bluewhale/auth/          WhaleAuthApplication
│       ├── java/com/bluewhale/auth/infrastructure/bootstrap/  管理员口令初始化
│       └── resources/                        application.yml、db/、i18n/auth/
└── whale-service-auth-architecture-test/      # ArchUnit 守护
```

## 三、领域模型

| 聚合 / 实体 | 关键不变量 | 仓储端口 |
|---|---|---|
| `User`（`sys_user`） | 用户名格式合法且租户内唯一；口令必须为 BCrypt 密文；状态仅 0/1；必须归属租户 | `UserRepository`、`UserTenantRepository`、`PasswordHistoryRepository` |
| `Role`（`sys_role`） | `roleCode` 合法且全局唯一；角色名非空；状态仅 0/1 | `RoleRepository` |
| `Menu`（`sys_menu`） | 菜单层级与可见性规则由聚合维护 | `MenuRepository` |
| `Permission`（`sys_permission`） | 权限码格式合法（`PermCode`）；类型与层级匹配 | `PermissionRepository`、`UserPermissionRepository` |
| `Dept`（`sys_dept`） | 部门树与负责人关系 | `DeptRepository` |
| `Tenant`（`sys_tenant`） | 编码 2–64 位；类型仅 `PLATFORM`/`TENANT`；仅启用且未过期租户可签发令牌 | `TenantRepository` |
| `UserRoleAssignment` / `RolePermissionAssignment` | 聚合内实体，维护授权关系 | 随所属聚合仓储持久化 |

值对象：`Username`、`EncryptedPassword`、`RoleCode`、`PermCode` —— 把格式与合法性校验收敛在领域内。

领域事件：`AuthDomainEvent` 为 `sealed interface`，派生出 `UserDomainEvent`、`RoleDomainEvent`、`MenuDomainEvent`、
`PermissionDomainEvent`、`DeptDomainEvent`、`UserPermissionDomainEvent`，变更类型由 `ChangeType` 枚举表达，
用于权限缓存失效与审计。

## 四、应用层

### 4.1 应用服务

| 分组 | 应用服务 | 用例 |
|---|---|---|
| 认证 | `SessionAppService` | 登录、刷新、当前用户、租户切换、登出 |
| 口令 | `PasswordAppService`、`PasswordPolicyService`、`TenantPasswordPolicyService`、`TemporaryPasswordGenerator` | 自助改密、口令策略、历史与到期、临时口令 |
| 用户 | `UserAppService`、`UserDirectoryAppService`、`UserLifecycleAppService` | 用户 CRUD、通讯录、入职/转正/调岗/离职/返聘 |
| 角色 / 菜单 / 权限 / 部门 | `RoleAppService`、`MenuAppService`、`PermissionAppService`、`DeptAppService` | 授权与基础数据管理 |
| 租户 | `TenantAppService`、`TenantMembershipAppService`、`TenantProvisioningAppService` | 租户生命周期、成员与角色、开通编排 |
| 用户属性 | `UserPermissionAppService` | ABAC 属性维护 |

### 4.2 出站端口（由 infrastructure 实现）

| 端口 | 实现 |
|---|---|
| `CredentialAuthenticator` | `SpringSecurityCredentialAuthenticator` |
| `TokenIssuer` | `JwtTokenIssuerAdapter` / `JwtTokenProvider` |
| `TokenBlacklist` | `RedisTokenBlacklist` |
| `RefreshTokenRotationStore` | `RedisRefreshTokenRotationStore` |
| `CurrentUserProvider` | `SecurityContextCurrentUserProvider` |
| `UserSessionQuery` | `UserDetailsSessionQuery` |
| `UserAccessRevocationPort` | `RedisUserAccessRevocationStore` |
| `PasswordHasher` | `PasswordHasherAdapter` |
| `TenantStatusSyncPort` | `TenantStatusCache` |
| `PasswordExpiryQuery` / `TenantPasswordPolicyRepository` | `PasswordExpiryQueryImpl` / `TenantPasswordPolicyRepositoryImpl` |

## 五、接口层

| 控制器 | 基础路径 | 主要端点 |
|---|---|---|
| [`AuthController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/AuthController.java) | `/auth` | `POST /login`、`POST /refresh`、`GET /me`、`PUT /password`、`GET /tenants`、`POST /tenant/switch`、`POST /logout` |
| [`UserController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/UserController.java) | `/user` | 分页 / 详情 / 更新 / 删除 / 重置口令 / 状态 / 角色分配 / 属性 / 租户绑定 |
| [`UserInternalController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/UserInternalController.java) | `/user/internal` | `POST /exists`、`GET /directory`、`GET /by-role` |
| [`UserLifecycleInternalController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/UserLifecycleInternalController.java) | `/user/internal/lifecycle` | `POST` 人员生命周期动作 |
| [`RoleController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/RoleController.java) | `/role` | 分页 / 详情 / 更新 / 删除 / 权限分配与查询 |
| [`MenuController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/MenuController.java) | `/menu` | 树 / 详情 / 更新 / 删除 |
| [`PermissionController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/PermissionController.java) | `/permission` | 分页 / 详情 / 更新 / 删除 |
| [`DeptController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/DeptController.java) | `/dept` | 树 / 详情 / 更新 / 删除 |
| [`DeptInternalController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/DeptInternalController.java) | `/dept/internal` | `GET /tree`、`GET /{id}/leader` |
| [`TenantController`](whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/controller/TenantController.java) | `/tenant` | 分页 / 详情 / 更新 / 状态 / 续期 / 成员与角色 / 口令策略 / 内部启用租户列表 |

> `/user/internal/**`、`/dept/internal/**`、`/tenant/internal/**` 属内网接口，已列入网关 `internal-deny`，
> 外部经网关访问返回 403；调用方须经 Nacos 服务发现直连。

## 六、认证与令牌

- **登录**：`POST /auth/login` 校验「租户 + 用户名 + 口令」，签发 accessToken（默认 120 分钟）与
  refreshToken（默认 1440 分钟）。响应体只返回 accessToken；refreshToken 与 CSRF 令牌通过 Cookie 下发：
  `whale_refresh_token`（HttpOnly）、`whale_csrf`（前端可读）。
- **刷新**：`POST /auth/refresh` 要求 `X-CSRF-Token` 与 `whale_csrf` 双提交一致，否则 403；校验签名、类型、
  租户成员关系、登出黑名单、用户级撤销时刻后换发新令牌并轮换 Cookie。
- **轮换与复用检测**：`whale.auth.refresh.rotation.enabled`（默认 `false`）开启后，同一 refreshToken 二次使用
  判定为复用，整族（familyId）吊销并告警。
- **登出**：accessToken 与 refreshToken 双写 Redis 黑名单，TTL 取剩余有效期，并清除认证 Cookie。
- **用户级撤销**：停用、删除、改密后写入撤销时刻，撤销前签发的 accessToken / refreshToken 均失效。
- **降级口径**：`whale.security.degradation.fail-closed`（默认 `false`）控制 Redis 不可用时放行或拒绝，
  需与网关保持一致。
- **口令生命周期**：默认 90 天到期、提前 7 天提醒、最近 5 次不可复用、最小 12 位且含四类字符；
  新建或管理员重置后强制首登改密。租户可通过 `PUT /tenant/password-policy` 覆盖有效期。

## 七、权限模型与缓存

权限层级：**系统 > 菜单 > 接口 > 按钮 > 数据 > 属性**。

- RBAC：用户 → 角色 → 权限点；`GET /auth/me` 返回角色、权限码与可见菜单树，供前端动态路由与按钮级渲染。
- ABAC：`sys_user_permission` 与 `UserPermissionAttribute` 承载用户级属性，用于数据行与字段级控制。
- 超管：`super_admin` 直接获得全量启用权限码与可见菜单。
- 权限缓存：默认 TTL 5 分钟，本地批量删除 + Redisson `RTopic` 跨实例广播；Redis 断连期间待失效 Key
  进入待处理集合并可持久化到 `pending-file`，由定时任务在恢复后重对齐。

## 八、数据与迁移

- 库：`bluewhale_auth`。核心表：`sys_user`、`sys_role`、`sys_menu`、`sys_permission`、`sys_dept`、
  `sys_tenant`、`sys_user_role`、`sys_role_permission`、`sys_user_permission`、`sys_user_tenant`、
  `sys_password_history`、`sys_tenant_password_policy`；日志表：`audit_log`、`api_log`、`outbound_log`、
  `frontend_log`、`behavior_log`。i18n starter 启动时幂等创建 `sys_i18n_message`。
- Flyway 仅扫描 `classpath:db/migration`：`V1__baseline.sql` → `V2__password_lifecycle.sql` →
  `V3__tenant_password_policy.sql`。
- 存量库由 `baseline-on-migrate=true`、`baseline-version=1` 自动登记基线，不重放 V1；`clean` 已禁用。
- `db/init.sql` 与 `db/migration-*.sql` 为手工 / 追溯脚本，不在 Flyway 扫描路径内。
- 详细边界与校验 SQL 见 [`db/README.md`](whale-service-auth-starter/src/main/resources/db/README.md)。

## 九、配置项

路径：`whale-service-auth-starter/src/main/resources/application.yml`。

| 配置 / 环境变量 | 说明 | 默认 |
|---|---|---|
| `SERVER_PORT` | 服务端口 | `8081` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库账号；**未设置口令时启动守卫直接报错** | `root` / 空 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `127.0.0.1:6379` |
| `NACOS_SERVER`（兼容 `NACOS_ADDR`） | Nacos 地址 | `127.0.0.1:8848` |
| `RABBITMQ_*` | 审计 MQ 连接 | 本机 `guest/guest` |
| `JWT_SECRET` | HS256 签名密钥，须与网关一致；生产必须覆盖 | 开发占位值 |
| `WHALE_AUTH_COOKIE_SECURE` | Cookie `Secure` 标记，HTTPS 必开 | `false` |
| `WHALE_AUTH_PASSWORD_MAX_AGE_DAYS` / `WARN_DAYS` | 口令有效期 / 提前提醒 | `90` / `7` |
| `WHALE_AUTH_PASSWORD_HISTORY_SIZE` / `MIN_LENGTH` | 历史口令数 / 最小长度 | `5` / `12` |
| `WHALE_AUTH_PASSWORD_FORCE_CHANGE` | 新建或重置后强制改密 | `true` |
| `PERMISSION_CACHE_ENABLED` / `TTL` | 权限缓存开关 / TTL | `true` / `5m` |
| `PERMISSION_CACHE_BROADCAST_ENABLED` | 跨实例失效广播 | `true` |
| `WHALE_FLYWAY_ENABLED` / `WHALE_FLYWAY_BASELINE_ON_MIGRATE` | Flyway 开关 / 存量库基线 | `true` / `true` |
| `WHALE_AUDIT_FORWARD_ENABLED` / `URL` | 审计转发到 `whale-audit` | `true` / `http://127.0.0.1:9101` |
| `WHALE_AUDIT_MQ_PUBLISH_ENABLED` | 审计优先投递 RabbitMQ | `false` |
| `WHALE_AUTH_MAIL_PROVISION_ENABLED` | 建用户后自动开邮箱 | `false` |
| `SECURITY_DEGRADATION_FAIL_CLOSED` | Redis 不可用时安全优先拒绝 | `false` |

## 十、构建与运行

前置：JDK 21、Maven Wrapper、MySQL（`bluewhale_auth`）、Redis、Nacos；审计转发与 MQ 按需启用。

```bash
# 在仓库根目录执行
./mvnw -f whale-backend/pom.xml -pl whale-services/whale-service-auth/whale-service-auth-starter -am spring-boot:run

# 或先安装再运行
./mvnw -f whale-backend/pom.xml -pl whale-services/whale-service-auth -am clean install -DskipTests
java -jar whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/target/whale-service-auth.jar
```

Windows 使用 `mvnw.cmd`。启动前至少设置 `MYSQL_PASSWORD`；生产环境同时覆盖 `JWT_SECRET`、
`WHALE_AUTH_COOKIE_SECURE=true`，并按需注入 `WHALE_CONFIG_ENC_KEY`。

## 十一、测试

```bash
# 单元测试 + 集成测试（含 ArchUnit 守护）
./mvnw -f whale-backend/pom.xml -pl whale-services/whale-service-auth -am test

# 仅架构守护
./mvnw -f whale-backend/pom.xml -pl whale-services/whale-service-auth/whale-service-auth-architecture-test test
```

- 领域规则：`domain/**/aggregate/*Test`、`ValueObjectsTest`、`AuthDomainEventTest`
- 应用用例：`application/**/*AppServiceTest`
- 控制器：`controller/*ControllerTest`
- 基础设施：`infrastructure/**`、`security/*Test`、`jwt/JwtTokenProviderTest`
- 实库联调：`live/*IT`、`migration/FlywayMigrationScriptTest`（需可用的 MySQL，按环境启用）

## 十二、可观测性

- `GET /actuator/health`、`GET /actuator/info`
- `GET /actuator/prometheus`：已开启 `http.server.requests` 直方图，供登录 P95 等 SLO 查询。
- 审计：优先转发 `whale-audit`，转发失败回退本地 `audit_log`；开启 MQ 后优先投递 RabbitMQ。

## 十三、相关文档

- 服务与端口总表：[docs/service-catalog.md](../../../docs/service-catalog.md)
- 数据库脚本约定：[db/README.md](whale-service-auth-starter/src/main/resources/db/README.md)
- 架构总览：[docs/architecture.md](../../../docs/architecture.md)

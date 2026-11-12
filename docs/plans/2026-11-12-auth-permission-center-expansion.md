# 权限中心扩展计划：SQL 能力补齐 + OAuth2.0 + 三方登录 + 用户导入导出

| 项 | 内容 |
|---|---|
| 目标服务 | `whale-backend/whale-services/whale-service-auth`（DDD 四层：domain / application / infrastructure / interfaces / starter） |
| 需求输入 | 1) 按 `auth_tbl_*` 建表脚本丰富权限中心；2) 支持 OAuth2.0；3) 支持三方登录认证；4) 用户导入导出 |
| 输入脚本 | `c:\Users\84628\Desktop\auth_tbl_brand_config.sql`（源库 `yc-sts-db-auth`，39 张表） |
| 现状基线 | [README.md](../../whale-backend/whale-services/whale-service-auth/README.md)、[whale-auth-backlog.md](whale-auth-backlog.md)、Flyway `V1~V10` |
| 建模方法 | `domain-architecture-workflow` → `domain-modeling` → `domain-architecture-guidance` 扫描结果，见 §二、§三 |
| 日期 | 2026-11-12 |

---

## 〇、结论摘要

1. **不照搬 `auth_tbl_*`**。输入脚本中的 `tenant / role / user / password_history / i18n` 与本服务已有 `sys_*` 主数据重复，直接建表会造成双份主数据与双写风险。只补齐**能力缺口**，新表统一 `sys_` 前缀 + 现有审计列约定。
2. **分 5 个里程碑**，每个里程碑可独立发布与回滚：M0 品牌与客户端基座 → M1 OAuth2.0 → M2 三方登录 → M3 身份档案/用户组/策略/设备会话 → M4 用户导入导出。
3. **三个必须先定的决策**：
   - OAuth2 授权服务器选型（Spring Authorization Server 与 Boot 4.0.8 的兼容性未验证，需 M1 前置 spike）；
   - 三方登录绑定表在输入 SQL 中**不存在**（`auth_tbl_user_credential` 是「证件」而非第三方凭证），需新增 `sys_user_social_binding`；
   - 输入 SQL 的 `auth_tbl_resource` 与现有 `sys_menu + sys_permission` 语义重叠，**不新建**资源表。
4. **不实现 `password` grant**（OAuth 2.1 已移除），仅支持 `authorization_code + PKCE`、`client_credentials`、`refresh_token`。

---

## 一、现状与差距

### 1.1 现有能力（已落地）

| 维度 | 现状 | 关键位置 |
|---|---|---|
| 分层 | 四层模块 + ArchUnit 守护（domain 禁框架、interfaces 禁 infrastructure/domain） | [AuthArchitectureTest.java](../../whale-backend/whale-services/whale-service-auth/whale-service-auth-architecture-test/src/test/java/com/bluewhale/auth/architecture/AuthArchitectureTest.java) |
| 认证 | 租户+用户名+口令登录、refresh Cookie + CSRF 双提交、登出黑名单、refresh 轮换与复用检测、二次认证 `@RequireReauth` | `SessionAppService`、`ReauthAppService`、`infrastructure/security`、`infrastructure/jwt` |
| 口令 | 90 天到期、历史 5 次、最小 12 位、首登改密、租户策略覆盖 | `PasswordAppService`、`TenantPasswordPolicyService`、`com.bluewhale.auth.domain.model.vo.PasswordPolicy` |
| 授权 | RBAC（用户-角色-权限-菜单-部门）+ ABAC 属性、权限码缓存与跨实例广播、超管直通 | `RoleAppService`、`PermissionAppService`、`UserPermissionAppService` |
| 租户 | 租户生命周期、成员与角色、开通编排、租户配置 KV | `TenantAppService`、`TenantMembershipAppService`、`TenantProvisioningAppService`、`TenantConfigAppService` |
| 用户 | CRUD、通讯录、生命周期（入职/转正/调岗/离职/返聘） | `UserAppService`、`UserDirectoryAppService`、`UserLifecycleAppService` |
| 审计 | `@AuditLog` 切面、脱敏、MQ/HTTP/本地三级链路、导出 CSV 到 whale-file | `whale-common` audit + `AuditLogArchiver` |
| REST 契约 | `interfaces/api/*Api` 声明契约，`starter/controller` 实现，路径 `/资源/v1/*`，统一 `Result<T>` | `AuthApi`、`UserApi`、`TenantApi` 等 14 个 Api |

### 1.2 输入 SQL 39 张表的处置矩阵

处置口径：**复用**（已有等价能力，只补字段）、**新建**（能力缺口）、**不建**（与现有模型重叠或非本域）。

| SQL 表 | 现有对应 | 处置 | 里程碑 |
|---|---|---|---|
| `auth_tbl_tenant` / `auth_tbl_tenant_info` / `auth_tbl_tenant_config` | `sys_tenant` / `sys_tenant_config` / `sys_tenant_password_policy` | 复用；按需补 `allow_register/allow_add/parent_id/tenant_code_path` 与登录策略字段 | M0 |
| `auth_tbl_role` / `auth_tbl_role_info` | `sys_role` | 复用 + 扩展 `parent_id/role_level/inherit_strategy/category/filters` | M3 |
| `auth_tbl_role_constraint` | 无 | **新建** `sys_role_constraint`（互斥/基数约束） | M3 |
| `auth_tbl_permission_strategy` | 无（现有为角色-权限直连 + 用户属性） | **新建** `sys_permission_strategy` | M3 |
| `auth_tbl_resource` / `auth_tbl_resource_contact` | `sys_menu` + `sys_permission` | **不建**（见 §七-6）；仅当需要“资源责任人”时在 `sys_menu` 扩展联系人字段 | — |
| `auth_tbl_user_info` | `sys_user` | 复用 + 补 `is_locked/lock_time/unlock_time/mfa_bound/mfa_type/register_source/registered_at/online` | M0/M2 |
| `auth_tbl_user_profile` | 无 | **新建** `sys_user_profile` | M3 |
| `auth_tbl_user_contact` | `sys_user.phone/email` 单值 | **新建** `sys_user_contact`（多联系方式 + 已验证标记） | M3 |
| `auth_tbl_user_credential` | 无 | **新建** `sys_user_credential`（证件，AES 加密） | M3 |
| `auth_tbl_user_education` | 无 | **新建** `sys_user_education` | M3 |
| `auth_tbl_user_employment` | 无（生命周期动作不存履历） | **新建** `sys_user_employment` | M3 |
| `auth_tbl_user_emergency_contact` | 无 | **新建** `sys_user_emergency_contact` | M3 |
| `auth_tbl_user_secret_question` | 无 | **新建** `sys_user_secret_question`（答案 AES） | M3 |
| `auth_tbl_user_group` + `rel_user_group_member` / `rel_user_group_user` | 无 | **新建** `sys_user_group` + `sys_user_group_member` | M3 |
| `auth_tbl_org` / `auth_tbl_org_info` | `sys_dept`（部门树） | 暂**不建**；若“公司/组织”需独立于部门，另立 M5（见 §九-4） | 待定 |
| `auth_tbl_company_info` | 无 | 同上，暂不建 | 待定 |
| `auth_tbl_rel_org_user` / `rel_org_user_group` | `sys_user_tenant`、`sys_user_role` | 复用，不另建关系表 | — |
| `auth_tbl_rel_role_user` / `rel_role_member` / `rel_role_resource` / `rel_role_resource_instance` | `sys_user_role` / `sys_role_permission` | 复用；`member_type`（用户/组/组织）在 M3 扩展 `sys_user_role.subject_type` | M3 |
| `auth_tbl_rel_tenant_users` | `sys_user_tenant` | 复用 | — |
| `auth_tbl_client_info` | 无 | **新建** `sys_oauth_client`（OAuth2 客户端） | M0/M1 |
| `auth_tbl_brand_config` | 无 | **新建** `sys_brand_config`（登录页品牌/主题） | M0 |
| `auth_tbl_device_info` | 无 | **新建** `sys_device` | M3 |
| `auth_tbl_login_history` | `audit_log` 部分覆盖，无结构化查询 | **新建** `sys_login_history`（append-only） | M3 |
| `auth_tbl_refresh_token` | Redis 轮换存储（无落库） | **新建** `sys_refresh_token`（会话可见性/管理端吊销；Redis 仍是实时校验源） | M3 |
| `auth_tbl_remember_me_token` | 无 | **新建** `sys_remember_me_token`（series 轮换，仅当开启 remember-me） | M3 |
| `auth_tbl_password_history` | `sys_password_history` | 复用 + 补 `is_active/expires_at/change_source` | M0 |
| `aux_tbl_i18n_resources` | `sys_i18n_message`（i18n starter） | 复用，不建 | — |
| `aux_tbl_script_history` | — | 不属权限域，排除 | — |

### 1.3 能力缺口（按需求归类）

- **R1 功能丰富**：品牌配置、OAuth 客户端、用户档案（7 张子表）、用户组、权限策略、角色约束与继承、设备与登录历史、refresh/remember-me 落库。
- **R2 OAuth2.0**：当前仅有自研 JWT 登录，**无标准 OAuth2 端点、无客户端注册表、无 scope 体系**。
- **R3 三方登录**：当前无社交登录代码、无绑定表、无 provider 适配器。
- **R4 导入导出**：当前仅审计日志导出 CSV；`easyexcel 4.0.3` 已在 [whale-parent/pom.xml](../../whale-backend/whale-parent/pom.xml) 管理但 auth 未使用。

---

## 二、领域建模（domain-modeling 扫描结果）

决策范围：`increment`；深度：`tactical`（不新增独立服务、不拆库，仍为单服务多模块）。

### 2.1 上下文与模块划分（逻辑分包，物理同服务）

```
认证上下文 (authentication)      授权上下文 (authorization)
  Session / Token / Password       Role / Permission / Policy / Constraint
        │                                  │
        └────────── 共享内核 ──────────────┘
             UserId / TenantId / 审计列约定
        ┌──────────────────────────────────┐
  身份档案上下文 (identity-profile)   组织目录上下文 (directory)
    Profile / Contact / Credential      Tenant / Dept / UserGroup
    Education / Employment / Emergency
        ┌──────────────────────────────────┐
  客户端集成上下文 (client-integration)  外观与风控 (presentation & risk)
    OAuthClient / SocialBinding           BrandConfig / Device / LoginHistory
```

上下文关系：同一进程内以包边界隔离，跨上下文只通过 `UserId/TenantId` 与领域事件协作，**不跨上下文直接读写对方聚合**（ArchUnit 补充规则见 §3.3）。

### 2.2 聚合与不变量

| 聚合根 | 不变量 | 子实体 / 值对象 | 仓储 | 领域事件 |
|---|---|---|---|---|
| `BrandConfig`（`sys_brand_config`） | `target_type` ∈ {PLATFORM, TENANT, COMPANY}；同 target 下 `priority` 唯一生效顺序；`primary_color` 必须合法色值 | `BrandTheme`(logo/loginTitle/institution/copyright/primaryColor/backgroundUrl) | `BrandConfigRepository` | `BrandConfigChanged` |
| `OAuthClient`（`sys_oauth_client`） | `clientId` 全局唯一；`client_secret` 仅存 BCrypt；`redirect_uris` 必须命中白名单且禁止通配；`grant_types` 与 `scope` 非空；禁用客户端不得换票 | `ClientId`、`ClientSecret`、`RedirectUriSet`、`ScopeSet`、`GrantType` | `OAuthClientRepository` | `OAuthClientRegistered/Disabled` |
| `SocialBinding`（`sys_user_social_binding`） | (provider, provider_user_id) 全局唯一；一个用户在同一 provider 下至多一条主绑定；解绑后不得作为登录入口 | `SocialProvider`、`ProviderUserId`、`SocialProfile` | `SocialBindingRepository` | `SocialAccountBound/Unbound` |
| `UserProfile`（`sys_user_profile` + 6 张子表） | `user_id` 唯一；证件号与密保答案必须密文存储；主联系方式/主证件同类型唯一；教育/工作/紧急联系人支持多条且有序 | `Contact`、`Credential`、`Education`、`Employment`、`EmergencyContact`、`SecretQuestion` | `UserProfileRepository`（聚合内级联） | `UserProfileUpdated`、`ContactVerified` |
| `UserGroup`（`sys_user_group` + member） | 编码租户内唯一；树不得成环；`category=STATIC` 成员显式维护，`DYNAMIC` 由 `filters` 计算且成员只读 | `GroupFilters`、`GroupMember` | `UserGroupRepository` | `UserGroupMembershipChanged` |
| `PermissionStrategy`（`sys_permission_strategy`） | `strategy_code` 租户内唯一；`role_ids/resource_ids` 必须存在；同租户仅一条 `is_default`；`priority` 数字小者优先 | `StrategyCondition`（JSON 条件） | `PermissionStrategyRepository` | `PermissionStrategyPublished` |
| `RoleConstraint`（`sys_role_constraint`） | `MUTEX` 需 ≥2 个角色且两两互斥；`CARDINALITY.max_assign` > 0；分配角色时必须校验全部启用约束 | `ConstraintType` | `RoleConstraintRepository` | `RoleConstraintViolated`（领域异常） |
| `UserSession`（`sys_refresh_token` + `sys_remember_me_token` + `sys_device` + `sys_login_history`） | 登录历史 append-only；refresh 记录只能“新增/撤销”，不可修改历史；设备指纹 + 用户维度唯一；单设备登录策略由租户配置裁决 | `DeviceFingerprint`、`RevocationReason` | `RefreshTokenRepository`、`RememberMeTokenRepository`、`DeviceRepository`、`LoginHistoryRepository` | `SessionRevoked`、`DeviceTrusted` |
| `Role`（扩展） | 继承不得成环；`role_level` 只能由父角色 +1；`inherit_strategy` ∈ {FULL, INCREMENT, OVERRIDE, NONE} | 复用 `RolePermissionAssignment` | 复用 `RoleRepository` | `RoleHierarchyChanged` |

### 2.3 命令 / 事件（应用层用例输入）

- 品牌：`SaveBrandConfigCommand`、`ResolveBrandQuery`（登录页免登录解析）。
- 客户端：`RegisterOAuthClientCommand`、`RotateClientSecretCommand`、`DisableOAuthClientCommand`。
- OAuth2：`AuthorizeCommand`、`ExchangeTokenCommand`、`RevokeTokenCommand`、`IntrospectTokenQuery`。
- 三方登录：`StartSocialAuthorizationQuery`、`CompleteSocialLoginCommand`（绑定/建号/登录三态）。
- 档案：`SaveUserProfileCommand`、`ReplaceUserContactsCommand`、`SaveUserCredentialCommand`（加密）、`SaveSecretQuestionsCommand`。
- 用户组：`SaveUserGroupCommand`、`ReplaceGroupMembersCommand`。
- 策略：`SavePermissionStrategyCommand`、`SaveRoleConstraintCommand`、`ReassignUserRolesCommand`（含约束校验）。
- 导入导出：`PrecheckUserImportCommand`、`CommitUserImportCommand`、`ExportUsersQuery`。

### 2.4 建模约束（避免反模式）

- 聚合内“子表”只允许通过聚合根整体替换，不提供单条子表 CRUD 绕过不变量。
- 登录历史、审计、导入批次均为 append-only，不承载业务状态机。
- 不为 `auth_tbl_resource` 建表：它把菜单、接口、按钮、数据权限混在一张宽表，直接落地会与现有 `sys_menu/sys_permission` 形成双份授权源，违反单一事实源。

---

## 三、架构落位（domain-architecture-guidance 结果）

### 3.1 新增包（严格四层）

```
-domain/src/main/java/com/bluewhale/auth/domain/
  brand/{aggregate,repository}
  client/{aggregate,repository}
  social/{aggregate,repository,model}
  profile/{aggregate,entity,repository}
  group/{aggregate,entity,repository}
  policy/{aggregate,repository}
  session/{aggregate,entity,repository}
  role/…（扩展 RoleAggregate）
-application/…/service/{BrandAppService,OAuthClientAppService,OAuth2AppService,
  SocialLoginAppService,UserProfileAppService,UserGroupAppService,
  PermissionStrategyAppService,UserImportAppService,UserExportAppService,
  SessionAdminAppService}
-application/…/port/{SocialIdentityProvider,AuthorizationCodeStore,
  ClientSecretVerifier,UserSpreadsheetReader,UserSpreadsheetWriter,ImportReportWriter}
-infrastructure/…/
  persistence/{entity,mapper,assembler,repository}/*（每表一套）
  security/oauth2/{RedisAuthorizationCodeStore,OAuthClientAuthenticator,ScopePermissionMapper}
  social/{WechatSocialAdapter,DingtalkSocialAdapter,FeishuSocialAdapter,OidcSocialAdapter}
  excel/{EasyExcelUserReader,EasyExcelUserWriter}
-interfaces/api/{BrandApi,OAuthClientApi,OAuth2Api,SocialAuthApi,UserProfileApi,
  UserGroupApi,PermissionStrategyApi,SessionAdminApi,UserImportExportApi}
-starter/controller/*+ Flyway V11+ + i18n
```

### 3.2 端口与适配器

| 应用端口（application） | 适配器（infrastructure） | 说明 |
|---|---|---|
| `SocialIdentityProvider` | `Wechat/Dingtalk/Feishu/OidcSocialAdapter` | `authorizeUrl(state,codeChallenge)`、`exchangeCode`、`fetchProfile`；应用层不感知 HTTP |
| `AuthorizationCodeStore` | `RedisAuthorizationCodeStore` | 一次性消费（`SET NX` + TTL 5min），绑定 `clientId/redirectUri/scope/PKCE` |
| `ClientSecretVerifier` | `BcryptClientSecretVerifier` | OAuth 客户端密钥只存哈希 |
| `UserSpreadsheetReader/Writer` | `EasyExcel*` | 应用层不依赖 EasyExcel；解析结果转 `ImportRow` |
| `TokenIssuer`（已有） | `JwtTokenIssuerAdapter` | OAuth2 与登录共用一套签发，不引入第二套令牌体系 |

### 3.3 架构守护补充（ArchUnit）

1. `domain` 新增包仍禁止 Spring/MyBatis/Jackson 注解（沿用现有规则）。
2. 新增 `oauth2/social/spreadsheet` 允许清单：`interfaces` 不得直接依赖 EasyExcel 或社交 SDK 类型。
3. `interfaces` 不得引用 `infrastructure.security.oauth2` 的领域外类型（DTO 单独定义）。
4. 新增包必须在 `package-info.java` 写明依赖规则。

### 3.4 事务边界

- 本地事务仅覆盖“加载聚合 → 领域行为 → 保存”；三方换取用户信息、EasyExcel 解析、whale-file 上传均在事务外完成。
- 导入分批提交（每批 ≤ 500 行，`REQUIRES_NEW`），失败批次不污染已成功批次；批次结果单独落 `sys_user_import_batch`（可选，M4 视需要）。

---

## 四、接口设计

### 4.1 业务 REST（统一 `/资源/v1`，鉴权沿用 `@RequirePermission` + `@AuditLog`）

| 模块 | 方法与路径 | 权限码 | 说明 |
|---|---|---|---|
| 品牌 | `GET /brand/v1/resolve` | 免登录 | 按 `targetType/targetId/domain` 解析品牌，供登录页 |
| | `GET /brand/v1/page`、`POST /brand/v1`、`PUT /brand/v1/{id}`、`DELETE /brand/v1/{id}` | `sys:brand:list/add/edit/delete` | 管理端 |
| 客户端 | `GET /oauth/client/v1/page`、`POST /oauth/client/v1`、`PUT /oauth/client/v1/{id}`、`POST /oauth/client/v1/{id}/secret`、`DELETE /oauth/client/v1/{id}` | `sys:oauth:client:*` | 密钥仅创建/轮换时明文返回一次 |
| OAuth2 | 见 §4.2 | 协议端点 | 独立白名单 |
| 三方登录 | `GET /auth/v1/social/{provider}/authorize`、`GET /auth/v1/social/{provider}/callback`、`POST /auth/v1/social/bind`、`DELETE /auth/v1/social/bind/{provider}` | 前两个免登录 | 回调后 302 到前端并带一次性 code |
| 档案 | `GET/PUT /user/v1/{id}/profile`、`PUT /user/v1/{id}/contacts`、`PUT /user/v1/{id}/credentials`、`PUT /user/v1/{id}/educations`、`PUT /user/v1/{id}/employments`、`PUT /user/v1/{id}/emergency-contacts`、`PUT /user/v1/{id}/secret-questions` | `sys:user:profile:view/edit` | 聚合整体替换；证件/答案脱敏返回 |
| 用户组 | `GET /user-group/v1/tree`、`GET/POST/PUT/DELETE /user-group/v1…`、`PUT /user-group/v1/{id}/members` | `sys:usergroup:*` | 动态组成员只读 |
| 策略 | `GET/POST/PUT/DELETE /permission-strategy/v1…`、`GET/POST/PUT/DELETE /role-constraint/v1…` | `sys:policy:*` | 发布后清权限缓存 |
| 会话 | `GET /session/v1/page`（登录历史）、`GET /device/v1/page`、`DELETE /device/v1/{id}`、`DELETE /session/v1/{tokenId}` | `sys:session:list/revoke` | 吊销联动 Redis 黑名单 |
| 导入导出 | `GET /user/v1/import/template`、`POST /user/v1/import/precheck`、`POST /user/v1/import`、`GET /user/v1/import/{batchId}`、`POST /user/v1/export` | `sys:user:import/export` | 见 §六 M4 |

### 4.2 OAuth2 端点（`/oauth2/v1/**`，服务直连 + 网关放行）

| 端点 | 说明 |
|---|---|
| `GET /oauth2/v1/authorize` | `response_type=code`、`client_id`、`redirect_uri`、`scope`、`state`、`code_challenge(S256)`；已登录则直接出码，未登录 302 到前端登录页并回跳 |
| `POST /oauth2/v1/token` | `authorization_code` / `refresh_token` / `client_credentials`；`client_secret_basic` 或 `client_secret_post` |
| `POST /oauth2/v1/revoke` | RFC 7009，撤销 access/refresh |
| `POST /oauth2/v1/introspect` | RFC 7662，供网关/下游校验 |
| `GET /oauth2/v1/jwks` | 若改为非对称签名；当前 HS256 → 仅暴露 `issuer` 元数据，不暴露密钥 |
| `GET /.well-known/oauth-authorization-server` | 元数据发现 |

**Grant 支持矩阵**：`authorization_code`（含 PKCE，Web/移动/SPA）、`refresh_token`、`client_credentials`（服务间）；**不支持** `password`、`implicit`。

**Scope ↔ 权限**：`scope` 值直接映射 `sys_permission.perm_code` 前缀（如 `sys:user:list`），换票时按用户权限求交集，禁止越权放大。

### 4.3 网关与安全配置变更

- 网关 `whale-gateway` 路由新增 `/oauth2/**`、`/brand/**`、`/user-group/**`、`/permission-strategy/**`、`/session/**`、`/device/**`。
- `SecurityConfig` 白名单：`/brand/v1/resolve`、`/auth/v1/social/*/authorize`、`/auth/v1/social/*/callback`、`/oauth2/v1/token`、`/oauth2/v1/revoke`、`/oauth2/v1/introspect`、`/oauth2/v1/jwks`、`/.well-known/**`。
- `internal-deny` 不新增放行；管理端接口全部保持鉴权。

---

## 五、数据方案

### 5.1 命名与字段映射原则

1. 新表统一 `sys_` 前缀，字段 snake_case；主键沿用 `varchar(32)` 雪花 ID，与现有 `sys_*` 一致。
2. 统一审计列：`create_user_id/create_user_name/create_time/update_user_id/update_user_name/update_time`；可软删的加 `is_deleted`。
3. 敏感字段：证件号、密保答案、OAuth client secret **只存密文/哈希**；手机号/邮箱查询列保留明文但禁止进日志（`@Sensitive`）。
4. MySQL 方言：现有 Flyway 仅 MySQL，`json` 列在 DM/Kingbase/OpenGauss 下不可移植；带 `json` 的字段（`filters`、`conditions`、`extra_attributes`）统一用 `TEXT` + 应用层 JSON 序列化，避免后续多库适配返工。

### 5.2 Flyway 版本规划（`db/migration`）

| 版本 | 内容 |
|---|---|
| `V11__brand_config.sql` | `sys_brand_config` + 平台默认品牌种子 + `sys:brand:*` 权限点与菜单 |
| `V12__oauth_client.sql` | `sys_oauth_client` + 内置 Web 客户端种子（secret 哈希）+ `sys:oauth:client:*` 权限点 |
| `V13__user_social_binding.sql` | `sys_user_social_binding` + `sys_user` 补 `register_source/registered_at` |
| `V14__user_profile.sql` | `sys_user_profile` + `sys_user_contact` + `sys_user_credential` + `sys_user_education` + `sys_user_employment` + `sys_user_emergency_contact` + `sys_user_secret_question` |
| `V15__user_group.sql` | `sys_user_group` + `sys_user_group_member` |
| `V16__permission_policy.sql` | `sys_permission_strategy` + `sys_role_constraint` + `sys_role` 扩展（parent/level/inherit/category）+ `sys_user_role.subject_type` |
| `V17__session_device.sql` | `sys_device` + `sys_login_history` + `sys_refresh_token` + `sys_remember_me_token` |
| `V18__password_history_enhance.sql` | `sys_password_history` 补 `is_active/expires_at/change_source`（含存量回填） |
| `V19__user_import_export.sql`（可选） | `sys_user_import_batch` / `sys_user_import_row_error`（仅异步/审计留痕时启用） |

> 每个脚本必须“可空/带默认值”，存量库 `baseline-on-migrate=true` 下顺序执行；新增列一律 `NULL DEFAULT`，禁止 `NOT NULL` 无默认。

### 5.3 索引与容量

- 唯一键：`sys_oauth_client(client_id)`、`sys_user_social_binding(provider, provider_user_id)`、`sys_user_group(tenant_id, user_group_code)`、`sys_permission_strategy(tenant_id, strategy_code)`、`sys_user_profile(user_id)`、`sys_user_credential(user_id, credential_type)`。
- 查询索引：`sys_login_history(user_id, login_time)`、`sys_device(tenant_id, user_id)`、`sys_refresh_token(jti)`、`sys_refresh_token(expires_at)`。
- 归档：`sys_login_history` 与 `sys_refresh_token` 超过保留期由已有 `LogRetentionScheduler` 模式扩展清理；先只做归档导出，不物理删除。

---

## 六、分阶段实施计划

> 排序原则：先建“被依赖的基座”（品牌/客户端/组织），再做协议（OAuth2），最后做批量与档案。每个里程碑结束都必须：`clean install` 通过、ArchUnit 通过、新增单测覆盖核心不变量、更新 README 与 backlog 台账。

### M0 基座：品牌配置 + OAuth 客户端 + 表结构约定（R1 部分）

- 任务：`sys_brand_config` / `sys_oauth_client` 迁移；`BrandAppService`、`OAuthClientAppService`；`BrandApi`、`OAuthClientApi`；品牌解析（免登录），客户端密钥 BCrypt 与一次性明文返回；权限点种子。
- 验收：登录页可按租户解析品牌（无登录）；客户端可增删改查，禁用后不可用于换票；密钥只落哈希。
- 测试：品牌优先级解析、client 校验（redirect_uri 精确匹配）、领域不变量单测；`BrandControllerTest`。

### M1 OAuth2.0（R2）

- 前置 spike（必须先做）：验证 Spring Authorization Server 与 Spring Boot 4.0.8 / Spring Security 7 的兼容性。
  - 兼容 → 引入 `spring-boot-starter-oauth2-authorization-server`，客户端信息接 `OAuthClientRepository`。
  - 不兼容 → 按 RFC 6749/7636/7009/7662 **自研授权端点**，复用 `TokenIssuer`；范围限定在 5 个端点，不引入第二套令牌。
- 任务：授权码存储（Redis 一次性）、PKCE 校验、token/refresh/revoke/introspect、scope→权限交集、`/.well-known` 元数据；网关放行与限流；审计埋点（签发/撤销）。
- 验收：Postman/curl 完成 `authorize → code → token → 调用受保护接口 → refresh → revoke` 全链路；PKCE 错误、redirect 不匹配、client 禁用均被拒；scope 超集被裁剪。
- 测试：`OAuth2AuthorizationAppServiceTest`、`RedisAuthorizationCodeStoreTest`（并发消费仅一次）、控制器契约测试；网关端到端冒烟脚本置于 `scripts/smoke`。

### M2 三方登录（R3）

- 任务：`sys_user_social_binding`；`SocialLoginAppService`；`SocialIdentityProvider` 端口 + 首批适配器（**渠道优先级待确认，见 §九-2**）；state/nonce/PKCE 存 Redis；三种落地：已绑定→直接登录、同邮箱未绑定→要求先登录再绑定、无记录→按租户 `self_register_enabled` 自动建号（`register_source=SOCIAL`）。
- 验收：微信/钉钉/飞书（或确认的渠道）完成扫码登录；重复回调幂等；解绑后不能登录；自动建号受租户开关约束；全链路产生 `sys_login_history` 与审计。
- 测试：provider 适配器用 WireMock 桩；绑定冲突、state 过期、code 重放用例；`SysUser` 唯一约束回归。

### M3 身份档案 / 用户组 / 策略 / 会话设备（R1 主体）

- 任务：`UserProfile` 聚合与 7 张子表、`UserGroup`、`PermissionStrategy`、`RoleConstraint`、`Role` 继承增强、`sys_device/sys_login_history/sys_refresh_token/sys_remember_me_token`；登录/刷新/登出/切换租户时写入登录历史、设备、refresh 记录；角色分配接入约束校验；密码历史补 `is_active/expires_at`。
- 验收：
  - 档案聚合整体保存，证件号与密保答案密文落库、接口脱敏返回；
  - 互斥角色分配被拒且给出具体约束名；角色继承成环被拒；
  - 登录历史可按用户/时间/结果查询；管理端可强制下线某会话并即时生效（Redis 黑名单）；
  - 动态用户组成员由 `filters` 计算且不可手工改。
- 测试：各聚合不变量单测、`SessionAppService` 回归（登录/刷新/登出链路不得因落库变慢而破坏）、策略优先级与互斥约束用例、Flyway 存量库迁移测试。

### M4 用户导入导出（R4）

- 任务：
  - 模板：`GET /user/v1/import/template`（EasyExcel 表头 + 示例行 + 注释必填项）。
  - 预检：上传文件 → 解析 → 逐行校验（用户名格式、租户内唯一、手机/邮箱格式、部门/角色是否存在、必填列）→ 返回 `batchId` + 行级错误清单（不落库）。
  - 提交：仅对预检通过的行分批写入（每批 ≤500），密码列**不导入**，统一生成随机临时口令并强制首登改密；写入 `register_source=IMPORT`。
  - 导出：按当前筛选条件导出 xlsx；≤5 万行同步流式导出，超过走异步并复用 whale-file 返回 `objectKey`（沿用审计导出模式）。
  - 权限与审计：`sys:user:import/export`；`@AuditLog(type=IMPORT/EXPORT, recordParams=false)`，导入结果记录成功/失败行数。
- 验收：1 万行导入在预检阶段全量报错定位到行/列；重复导入幂等（同名用户跳过并计入“已存在”）；导出文件表头与模板一致；无权限 403。
- 测试：Excel 解析/写出适配器单测、行校验器参数化用例、分批事务回滚用例、导出流式内存上限用例。

---

## 七、关键决策（需形成 ADR）

| # | 决策 | 结论与理由 |
|---|---|---|
| 1 | 是否照搬 `auth_tbl_*` | **不照搬**。`tenant/user/role/password_history/i18n` 已存在，双份主数据会造成授权与登录不一致；新表 `sys_` 前缀，字段按需映射。 |
| 2 | OAuth2 实现方式 | 先 spike SAS 兼容性；不兼容则自研 5 个 RFC 端点并复用现有 `TokenIssuer`。**禁止**同时维护两套令牌。 |
| 3 | 是否支持 password grant | **不支持**（OAuth 2.1 已移除）；已有 `/auth/v1/login` 承担口令登录，避免两套语义。 |
| 4 | 三方登录账号策略 | 已绑定直登；同邮箱未绑定→登录后绑定（防账号劫持）；无记录→受租户自助注册开关约束自动建号；所有分支均记审计。 |
| 5 | 导入导出技术 | EasyExcel（父 POM 已管理）+ 应用层 `SpreadsheetReader/Writer` 端口；同步小文件、异步大文件复用 whale-file。 |
| 6 | 是否建 `sys_resource` | **不建**。资源语义已由 `sys_menu`（菜单/页面）+ `sys_permission`（接口/按钮/数据）承载；再建一张宽表会产生第二授权事实源。若确需“资源责任人/文档地址”，在 `sys_menu` 上扩展 3 个字段即可。 |
| 7 | 登录历史/refresh 落库与 Redis 关系 | Redis 仍是**实时校验源**（黑名单/轮换）；MySQL 作为**可查询与可审计的账本**，写入失败不得阻断登录（降级仅告警）。 |

---

## 八、测试与验收

| 层次 | 要求 |
|---|---|
| 领域 | 每个新聚合至少 1 个不变量测试类；值对象（ClientId/RedirectUri/Scope/SocialProvider）构造校验用例 |
| 应用 | 每个 AppService 的主成功路径 + 至少 2 个失败路径；导入分批与幂等 |
| 接口 | 新增 Api 的控制器契约测试（路径/版本/权限注解/审计注解反射断言） |
| 架构 | ArchUnit 全绿；新增包 `package-info.java` 齐备 |
| 迁移 | `FlywayMigrationScriptTest` 覆盖 V11~V19；存量库（V1 基线）升级后启动成功 |
| 端到端 | `scripts/smoke` 增补：OAuth2 全链路、三方登录桩回调、导入导出；经网关验证 `code=0` |
| 回归 | 登录/刷新/登出/切换租户/权限缓存/审计链路全量回归 |

---

## 九、风险与未决问题（需确认后定稿）

1. **OAuth2 授权服务器兼容性**：Boot 4.0.8 + Security 7 下 SAS 版本可用性未知，是 M1 的唯一技术风险；已用 spike 前置隔离，不阻塞 M0。
2. **三方渠道优先级**：首批适配器做哪些？候选：企业微信/微信开放平台、钉钉、飞书、GitHub/Google（OIDC）。默认建议：钉钉 + 飞书 + 企业微信（国内办公场景）+ 通用 OIDC。
3. **品牌配置识别维度**：输入 SQL 用 `target_type + target_id + priority`；是否还需要按**登录域名**解析（多品牌 SaaS）？建议保留 `target` 并增加可选 `domain` 字段。
4. **公司/组织是否本期**：`auth_tbl_company_info`、`auth_tbl_org_info`、`auth_tbl_org` 与现有 `sys_tenant + sys_dept` 存在边界重叠。建议先不做（M5 单独立项），避免组织模型双轨。
5. **是否需要管理端前端**：`whale-frontend/admin` 目前未对接权限中心；本计划只交付后端契约，前端页面另立任务。
6. **多数据库**：父 POM 管理 DM/Kingbase/OpenGauss/Oracle 驱动，但现有迁移脚本为 MySQL 方言；本期继续 MySQL，JSON 字段统一用 `TEXT` 降低后续移植成本。

---

## 十、依赖关系（执行顺序）

```
M0 品牌/客户端 ──▶ M1 OAuth2 ──▶ M2 三方登录
      │                               │
      └──────────────┬────────────────┘
                     ▼
              M3 档案/组/策略/会话
                     ▼
              M4 用户导入导出（依赖 M3 的档案与租户角色数据）
```

- M3 与 M1/M2 无强依赖，可在 M1 spike 期间并行开发。
- M4 必须在 M3 的租户/部门/角色查询能力稳定后启动，否则导入校验逻辑会反复返工。

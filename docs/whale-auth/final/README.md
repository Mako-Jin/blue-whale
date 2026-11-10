# 权限中心最终设计文档（whale-auth）

> 关联需求：REQ-FUNC-002（权限中心 RBAC+ABAC 六层级）、REQ-ARCH-001（DDD 改造）、REQ-ARCH-003（网关路由前缀）、REQ-ARCH-004（命名规范）、REQ-SEC-001~005（审计/黑名单/密钥校验）、REQ-SEC-006（多通道日志）、REQ-DIST-001~003（分布式 ID/锁/其他）、REQ-QA-002（必检留痕）、REQ-CICD-001、REQ-PROC-001
> 落点模块：`whale-backend/whale-services/whale-auth`（端口 8081，Nacos 服务名 `whale-auth`，库 `bluewhale_auth`）
> 生成日期：2026-10-18 ｜ 作者：BlueWhale ｜ 依据：Wave 5.11（残余收口）
> 口径说明：本文为**整合最终版**，以 `docs/{design,plans,requirements,specs,architecture.md,naming-conventions.md}` 原位最新文件为准；历史副本归置于 `docs/whale-auth/originals/`（原文件保留原位，见第 6 节）。

---

## 1. 能力矩阵

| 能力 | 状态 | 依据（类名 / 文档路径） |
|---|---|---|
| RBAC + ABAC 六层级（用户/角色/菜单/权限点/部门/ABAC 属性） | 已闭环 | `controller/`（6）· `service/`（7 接口 + 6 实现）· `mapper/`（8）· `db/init.sql`（8 表）；`docs/plans/whale-auth-backlog.md` §1.1 |
| JWT 认证（签发 access/refresh，带 `jti`/`tokenType`） | 已闭环 | `jwt/JwtTokenProvider.java`（jjwt 0.12.5）· `security/JwtAuthenticationFilter.java` · `config/SecurityConfig.java`（STATELESS） |
| 令牌黑名单（auth + 网关双层，REQ-SEC-002/003/004） | 已闭环 | `auth`：`security/RedisTokenBlacklist.java`；网关：`security/TokenBlacklistChecker.java`（响应式降级放行）；键下沉 `whale-security` `token/TokenBlacklistKeys.java` |
| 权限拦截（横切 `@RequirePermission`） | 已闭环 | `security/PermissionInterceptor.java` · `SecurityConfig#permissionInterceptorConfigurer`；whale-audit 侧改用 `PermissionHeaderGlobalFilter` + `RequirePermissionInterceptor`（AUTH-06） |
| 审计埋点（注解 + 切面 + 脱敏 + 查询） | 已闭环 | `whale-spring-boot-starter` `common/audit/*`（`AuditLog`/`AuditLogAspect`/`SensitiveDataMasker`/`AuditLogRecord`）；查询 `whale-audit` `AuditLogController`；`docs/design/audit-log.md` |
| DDD 骨架（四层 + 值对象） | 部分（骨架已建，业务仍走经典三层） | `domain`/`application`/`interfaces`/`infrastructure` 四层 + 4 值对象（`Username`/`EncryptedPassword`/`RoleCode`/`PermCode`）；`docs/design/ddd-whale-auth.md` §7/§10 |
| 幂等（`@Idempotent`） | 已闭环 | `whale-spring-boot-starter` `common/idempotent/*`（`IdempotentAspect`，Redisson `RBucket.trySet` SETNX + 结果缓存）；`UserController#create` 接入（AUTH-18） |
| 缓存一致性（Cache-Aside + 延迟双删 + 空哨兵 + TTL 抖动） | 已闭环 | `common/cache/CacheConsistencyTemplate.java` · `auth/security/PermissionCache.java` · `config/PermissionCacheProperties.java`（AUTH-20，Wave 5.8） |
| 分布式锁（`@DistributedLock` 场景接入） | 已闭环 | `common/lock/*`（`DistributedLockAspect`）· `security/PermissionCacheMutex.java`（防击穿）· `SysRoleServiceImpl#assignPermissions`（`lock:auth:role:{roleId}`）；实时 IT `LivePermissionLockIT`（AUTH-19） |
| 网关路由（服务自持一级前缀，REQ-ARCH-003） | 已闭环 | `whale-gateway/src/main/resources/application.yml`（`Path=/auth/**,/user/**,/dept/**,/role/**,/menu/**,/permission/**`，无 `StripPrefix`）；`docs/architecture.md` §网关路由与前缀约定 |
| 限流（网关入口，Redis 计数器兜底） | 部分（Sentinel 未接入，转 backlog） | `gateway/filter/RateLimitGlobalFilter.java` · `config/RateLimitProperties.java`（固定窗口，超阈值 429 + `GatewayResponse`）；AUTH-30 |
| 密钥校验（非 dev 占位密钥拒绝启动，REQ-SEC-005） | 已闭环 | `auth`：`config/SecretPlaceholderChecker.java` + `config/JwtProperties.java`；网关：`config/GatewaySecretPlaceholderChecker.java` + `config/WhaleGatewayProperties.java`（AUTH-01） |
| 降级告警（黑名单降级可观测，AUTH-05） | 已闭环 | `whale-security` `degradation/SecurityDegradationAlerter.java`（ERROR + 固定标记 `SECURITY_DEGRADATION` + 进程内计数）；接入 `RedisTokenBlacklist` 与 `TokenBlacklistChecker` |
| 元审计（读审计留痕，AUTH-07） | 已闭环 | `whale-audit` `controller/AuditLogController.java` 3 个查询方法 `@AuditLog(type=QUERY, targetType="audit_log")`；`docs/design/audit-log.md` §9/§13 |
| 返回体落库（`recordResult()`，AUTH-13） | 已闭环 | `audit_log.result_body` 列 + `common/audit/entity/AuditLogRecord.java#resultBody` + `AuditLogAspect#buildRecord`（`maskJson` 脱敏 + 常量上限截断） |
| 多通道日志（REQ-SEC-006，Wave 5.10） | 部分（后端 Wave A 已落地，前端 SDK 未开工） | `common/log/*`（`ApiLogFilter`/`OutboundLogInterceptor`/`LogPersistSupport`）· `whale-audit` `LogIngestController`；4 张 append-only 表 |

---

## 2. 接口与权限点清单

**统计口径**：6 控制器 / **37 个 REST 接口**；`@RequirePermission` **34 处**（除 3 个白名单接口外全部鉴权）；`@AuditLog` 源码逐处核对 **22 处**（`whale-auth-backlog.md` §1.1 文本记为 20 处，**口径差异待确认**，以源码为准）。

### 2.1 接口清单

| 控制器（一级前缀） | 方法与路径 | `@RequirePermission` 权限码 | `@AuditLog` 埋点类型 |
|---|---|---|---|
| `AuthController`（`/auth`） | POST `/auth/login` | 无（白名单 `permitAll`） | `@AuditLog(operation="用户登录", type=LOGIN, targetType=sys_user, recordParams=false)` |
| | POST `/auth/refresh` | 无（白名单） | 无 |
| | POST `/auth/logout` | 无（白名单） | `@AuditLog(operation="用户登出", type=LOGOUT, targetType=sys_user, recordParams=false)` |
| | GET `/auth/me` | `@RequirePermission`（无权限码，仅登录校验） | 无 |
| `UserController`（`/user`） | GET `/user/page` ｜ GET `/user/{id}` | `sys:user:list` | 无 |
| | POST `/user`（创建） | `sys:user:add` | `@AuditLog(CREATE, targetType=sys_user)` |
| | PUT `/user/{id}`（更新） | `sys:user:edit` | `@AuditLog(UPDATE, targetId=#id)` |
| | PUT `/user/{id}/attrs`（ABAC 写入） | `sys:user:edit` | `@AuditLog(UPDATE, targetId=#id)` |
| | PUT `/user/{id}/password`（重置密码） | `sys:user:resetPwd` | `@AuditLog(UPDATE, targetId=#id, recordParams=false)` |
| | PUT `/user/{id}/status`（启停） | `sys:user:status` | `@AuditLog(UPDATE, targetId=#id)` |
| | DELETE `/user/{id}` | `sys:user:delete` | `@AuditLog(DELETE, targetId=#id)` |
| | POST `/user/{id}/roles`（分配角色） | `sys:user:assign` | `@AuditLog(GRANT, targetType=sys_user, targetId=#id)` |
| | GET `/user/{id}/roles` ｜ GET `/user/{id}/attrs` | `sys:user:list` | 无 |
| `RoleController`（`/role`） | GET `/role/page` ｜ GET `/role/{id}` | `sys:role:list` | 无 |
| | POST `/role`（创建） | `sys:role:add` | `@AuditLog(CREATE, targetType=sys_role)` |
| | PUT `/role/{id}` | `sys:role:edit` | `@AuditLog(UPDATE, targetId=#id)` |
| | DELETE `/role/{id}` | `sys:role:delete` | `@AuditLog(DELETE, targetId=#id)` |
| | POST `/role/{id}/permissions`（分配权限） | `sys:role:assign` | `@AuditLog(GRANT, targetType=sys_role, targetId=#id)` |
| | GET `/role/{id}/permissions` | `sys:role:list` | 无 |
| `MenuController`（`/menu`） | GET `/menu/tree` ｜ GET `/menu/{id}` | `sys:menu:list` | 无 |
| | POST `/menu` | `sys:menu:add` | `@AuditLog(CREATE, targetType=sys_menu)` |
| | PUT `/menu/{id}` | `sys:menu:edit` | `@AuditLog(UPDATE, targetId=#id)` |
| | DELETE `/menu/{id}` | `sys:menu:delete` | `@AuditLog(DELETE, targetId=#id)` |
| `PermissionController`（`/permission`） | GET `/permission/page` ｜ GET `/permission/{id}` | `sys:permission:list` | 无 |
| | POST `/permission` | `sys:permission:add` | `@AuditLog(CREATE, targetType=sys_permission)` |
| | PUT `/permission/{id}` | `sys:permission:edit` | `@AuditLog(UPDATE, targetId=#id)` |
| | DELETE `/permission/{id}` | `sys:permission:delete` | `@AuditLog(DELETE, targetId=#id)` |
| `DeptController`（`/dept`） | GET `/dept/tree` ｜ GET `/dept/{id}` | `sys:dept:list` | 无 |
| | POST `/dept` | `sys:dept:add` | `@AuditLog(CREATE, targetType=sys_dept)` |
| | PUT `/dept/{id}` | `sys:dept:edit` | `@AuditLog(UPDATE, targetId=#id)` |
| | DELETE `/dept/{id}` | `sys:dept:delete` | `@AuditLog(DELETE, targetId=#id)` |

> 每控制器接口数：Auth 4 ＋ User 11 ＋ Role 7 ＋ Menu 5 ＋ Permission 5 ＋ Dept 5 = **37**（其中白名单 3：`/auth/login`、`/auth/refresh`、`/auth/logout`）。
> 相关 `whale-audit` 侧接口（归属 `AuditLogController`，前缀 `/audit/log`，非 whale-auth 的 37 接口）：`GET /audit/log/page`、`GET /audit/log/{id}`、`GET /audit/log/trace/{traceId}` 均带 `@RequirePermission("sys:audit:log:list")` + `@AuditLog(type=QUERY, targetType="audit_log")`；`POST /audit/log/export` 未落地（AUTH-08 阻塞）。

### 2.2 权限点种子清单（`whale-auth` `src/main/resources/db/init.sql`，共 25 条 `sys:*`）

| id | perm_code | perm_name | 类型 | 路径 / 方法 |
|---|---|---|---|---|
| 1 | `sys:user:list` | 查询用户 | API | `/user/**` · GET |
| 2 | `sys:user:add` | 新增用户 | API | `/user` · POST |
| 3 | `sys:user:edit` | 修改用户 | API | `/user/**` · PUT |
| 4 | `sys:user:delete` | 删除用户 | API | `/user/{id}` · DELETE |
| 5 | `sys:user:assign` | 分配用户角色 | API | `/user/{id}/roles` · POST |
| 6 | `sys:role:list` | 查询角色 | API | `/role/**` · GET |
| 7 | `sys:role:add` | 新增角色 | API | `/role` · POST |
| 8 | `sys:role:edit` | 修改角色 | API | `/role/**` · PUT |
| 9 | `sys:role:delete` | 删除角色 | API | `/role/{id}` · DELETE |
| 10 | `sys:role:assign` | 分配角色权限 | API | `/role/{id}/permissions` · POST |
| 11 | `sys:menu:list` | 查询菜单 | API | `/menu/**` · GET |
| 12 | `sys:menu:add` | 新增菜单 | API | `/menu` · POST |
| 13 | `sys:menu:edit` | 修改菜单 | API | `/menu/**` · PUT |
| 14 | `sys:menu:delete` | 删除菜单 | API | `/menu/{id}` · DELETE |
| 15 | `sys:permission:list` | 查询权限点 | API | `/permission/**` · GET |
| 16 | `sys:permission:add` | 新增权限点 | API | `/permission` · POST |
| 17 | `sys:permission:edit` | 修改权限点 | API | `/permission/**` · PUT |
| 18 | `sys:permission:delete` | 删除权限点 | API | `/permission/{id}` · DELETE |
| 19 | `sys:dept:list` | 查询部门 | API | `/dept/**` · GET |
| 20 | `sys:dept:add` | 新增部门 | API | `/dept` · POST |
| 21 | `sys:dept:edit` | 修改部门 | API | `/dept/**` · PUT |
| 22 | `sys:dept:delete` | 删除部门 | API | `/dept/{id}` · DELETE |
| 23 | `sys:user:resetPwd` | 重置用户密码 | API | `/user/{id}/password` · PUT |
| 24 | `sys:user:status` | 启停用户 | API | `/user/{id}/status` · PUT |
| 25 | `sys:audit:log:list` | 审计日志查询 | API | `/audit/log/**` · GET（AUTH-06，Wave 5.10） |

> 菜单种子（`sys_menu`，共 6 行）：1 个目录「系统管理」（`/system`）+ 5 个菜单项「用户管理 / 角色管理 / 菜单管理 / 部门管理 / 权限管理」。`whale-auth-backlog.md` 文本记为「8 个菜单项」，与 `init.sql` 实际 6 行**口径差异待确认**。

---

## 3. 关键配置项

| 配置键 | 含义 / 默认值 | 来源 |
|---|---|---|
| `whale.gateway.jwt.secret` | 网关 HS256 签名密钥，默认占位 `bluewhale-jwt-secret-2026-please-change-in-production`，须与 auth 签发侧一致；非 dev + 占位 → 拒绝启动 | `whale-gateway/application.yml`、`WhaleGatewayProperties.Jwt` |
| `WHALE_GATEWAY_JWT_SECRET` | 网关密钥环境变量（覆盖 `whale.gateway.jwt.secret`） | `naming-conventions.md` §五 |
| `whale.auth.jwt.secret` | auth 侧签名密钥，默认同占位；环境变量 `JWT_SECRET` 覆盖；非 dev + 占位 → 拒绝启动（`SecretPlaceholderChecker`） | `whale-auth/application.yml`、`JwtProperties` |
| `whale.auth.jwt.expire-minutes` / `refresh-expire-minutes` | access 有效期 120 分钟 / refresh 有效期 1440 分钟 | `whale-auth/application.yml` |
| `whale.gateway.jwt.white-list` | 网关免 JWT 白名单：`/auth/login`、`/auth/refresh`、`/actuator/**`、`/v3/api-docs/**`、`/doc.html`、`/webjars/**`、`/favicon.ico`、`/audit/log/frontend/**`、`/audit/log/behavior/**` | `whale-gateway/application.yml` |
| `whale.gateway.permission-header.enabled` / `codes-header` / `cacheKeyPrefix` / `null-sentinel` | 权限码头注入开关（默认 `true`）／头名 `X-Permission-Codes`／缓存键前缀 `whale:cache:auth:user:perm`／哨兵 `__NULL__` | `PermissionHeaderProperties` |
| `whale.gateway.rate-limit.enabled` / `limit` / `window-seconds` | 网关入口限流开关（默认 `true`）／单窗口上限 `200`／固定窗口 `1` 秒；超阈值 429 | `RateLimitProperties`、`application.yml` |
| `whale.auth.permission-cache.ttl` | 权限码缓存正常值 TTL，默认 5 分钟（叠加 `±10%` 抖动） | `PermissionCacheProperties` |
| `whale.cache.key-prefix` / `null-sentinel` / `ttl-jitter-percent` / `null-ttl` | 缓存键前缀 `whale:cache`／空哨兵 `__NULL__`／TTL 抖动 `10`%／空值 TTL `60s` | `CacheProperties` |
| `whale.audit.sensitive.keys` | 审计脱敏附加敏感键名（与内置规则并集，忽略大小写、去 `_`/`-`，AUTH-11） | `SensitiveKeysProperties`、`docs/design/audit-log.md` §7 |
| `whale.audit.sink` | 审计落库方式 `local`（本地异步直写）/ `mq`（已废弃，自动回落 local 并告警；MQ 汇聚用 `whale.audit.mq.publish-enabled`，HTTP 转发用 `whale.audit.forward.enabled`） | `LogSinkProperties`、`docs/design/audit-log.md` §6 |
| `whale.audit.retention.days` | 审计保留期（天），默认 90，到期整批删除（归档任务 AUTH-09 阻塞于 whale-job） | `docs/design/audit-log.md` §10 |
| `whale.auth.ddd.{context}.enabled` | DDD 新旧实现并行开关，默认 `false`（规划；随 AUTH-16 落地） | `docs/design/ddd-whale-auth.md` §7 |
| `NACOS_ADDR` / `NACOS_NAMESPACE` / `REDIS_HOST` / `REDIS_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` / `SPRING_PROFILES_ACTIVE` | 基础设施通用环境变量（Nacos `127.0.0.1:8848`/`public`；Redis `6379`；MySQL `root/root`；profile `dev`） | `application.yml`、`naming-conventions.md` §五 |

**Redis 键约定**：

| 用途 | 键 | 说明 |
|---|---|---|
| 令牌黑名单 | `auth:token:blacklist:<sha256(token)>` | TTL = 令牌剩余有效期；规范要求 `whale:auth:token:blacklist:<摘要>`（**偏差 N-02**，待整改）；常量 `TokenBlacklistKeys.KEY_PREFIX` |
| 权限码缓存 | `whale:cache:auth:user:perm:{userId}` | Cache-Aside，值为权限码逗号串；网关注入权限码头时读取同键 |
| 角色权限变更锁 | `lock:auth:role:{roleId}` | `@DistributedLock(failFast=true)` |
| 缓存防击穿锁 | `lock:cache:auth:user:perm:{userId}` | `PermissionCacheMutex`，同 Key 并发仅一次回源 |
| 网关限流计数 | `whale:gateway:rate-limit:<窗口>:<客户端>` | 固定窗口计数，超阈值 429 |

**数据源 URL 约定**：`jdbc:mysql://127.0.0.1:3306/bluewhale_auth?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`（AUTH-36：`characterEncoding=utf8` 而非 `utf8mb4`）。

---

## 4. 表结构

`whale-auth` `db/init.sql` 共 8 张 RBAC 表 + `audit_log`（另有 Wave 5.10 的 4 张多通道日志表）。公共字段：`id BIGINT`（雪花，无自增）、`create_time`、`update_time`、`deleted`（逻辑删除 0/1）；表间为逻辑外键（不建物理 FK）。

| 表 | 字段要点 | 关键约束 / 索引 |
|---|---|---|
| `sys_user` | username/password（BCrypt 密文）/nickname/phone/email/avatar/dept_id/status(1 启用/0 停用)/last_login_time | PK(id)；`uk_username` |
| `sys_role` | role_code（如 `super_admin`）/role_name/description/sort_no/status | PK(id)；`uk_role_code` |
| `sys_permission` | parent_id/perm_code（如 `sys:user:list`）/perm_name/perm_type（SYSTEM/MENU/API/BUTTON/DATA/ATTR）/path/method/sort_no/status | PK(id)；`uk_perm_code`、`idx_parent_id` |
| `sys_menu` | parent_id/name/path/component/icon/sort/menu_type（DIR/MENU）/permission_code/visible/status | PK(id)；`idx_parent_id` |
| `sys_dept` | parent_id/dept_name/sort/leader | PK(id)；`idx_parent_id` |
| `sys_user_role` | user_id/role_id | PK(id)；`uk_user_role(user_id, role_id)`、`idx_role_id` |
| `sys_role_permission` | role_id/permission_id | PK(id)；`uk_role_permission(role_id, permission_id)`、`idx_permission_id` |
| `sys_user_permission`（即 ABAC 属性表，任务书中别名 `sys_user_attr`） | user_id/permission_id/attr_key（如 `deptId`/`dataScope`）/attr_value | PK(id)；`uk_user_perm_attr(user_id, permission_id, attr_key)`、`idx_permission_id` |
| `audit_log` | trace_id/user_id/tenant_id/username/module/operation/operate_type(L...QUERY...)/method(全限定类名#方法)/request_uri/http_method/target_type/target_id/request_params/**result_body**/response_code/biz_status(1 成功/0 失败)/error_msg/ip/user_agent/cost_ms/create_time | PK(id)；append-only（**无 update_time / deleted**）；索引 `idx_trace_id`/`idx_user_id`/`idx_module`/`idx_operate_type`/`idx_biz_status`/`idx_tenant_time(tenant_id, create_time)`/`idx_create_time` |

> **本轮新增列（AUTH-13）**：`audit_log.result_body TEXT NULL COMMENT '返回体（JSON，脱敏 + 截断；recordResult=true 时写入）'`；`whale-auth` 与 `whale-audit` 两份 `db/init.sql` 保持一致。语义：仅 `@AuditLog(recordResult=true)` 时经 `SensitiveDataMasker#maskJson` 脱敏并按切面常量上限（`AuditLogAspect#MAX_RESULT_LENGTH`，2000 字符）截断后落库；序列化失败仅告警、该字段留空。
> 表名约定：`sys_` 为平台内建 RBAC，`audit_log`/`*_log` 为 append-only 观测表（`naming-conventions.md` §2.2）。

---

## 5. 已闭环项与残余/阻塞项

### 5.1 已闭环 AUTH 项

| 编号 | 结论 |
|---|---|
| AUTH-01 | 网关新增 `GatewaySecretPlaceholderChecker`（`InitializingBean`），非 dev + 占位密钥 `IllegalStateException` 拒绝启动；占位值常量化到 `WhaleGatewayProperties.Jwt` |
| AUTH-02 | jjwt 版本统一由 `whale-parent` 管理 `0.12.5`，`dependency:tree` 实测 api/impl/jackson 一致 |
| AUTH-03 | refreshToken 轮换策略经 [ADR-001](adr/2026-10-17-refresh-token-rotation.md) 定稿（一换一 + 旧 token 黑名单 + 复用检测），实施另立 AUTH-39 |
| AUTH-04 | 接口权限注解 3 → 34 处（全部非白名单接口鉴权），补 22 条权限点种子 |
| AUTH-05 | `SecurityDegradationAlerter`（ERROR + `SECURITY_DEGRADATION` + 计数）接入 auth/网关两处降级分支，降级不阻断鉴权 |
| AUTH-06 | 审计查询鉴权：网关注入权限码头 + starter 通用拦截器，`sys:audit:log:list` 权限点种入库，无权限 403 |
| AUTH-07 | 读审计元审计：3 个查询方法 `@AuditLog(type=QUERY, targetType=audit_log)`，元审计落同库可反查 |
| AUTH-11 | 脱敏规则配置化 `whale.audit.sensitive.keys` + `SensitiveDataMasker#maskJson` |
| AUTH-12 | 审计埋点 5 → 全量敏感写操作（改密 `recordParams=false`），覆盖 §2 事件分类 |
| AUTH-13 | `recordResult()` 落库：补 `result_body` 列 + `AuditLogRecord#resultBody` + 切面脱敏截断落库 |
| AUTH-14 | DDD 四层骨架 + 5 `package-info` 依赖规则 + 4 值对象 + 契约冻结清单（§10） |
| AUTH-18 | `@Idempotent`（Redisson SETNX + 结果缓存），`UserController#create` 接入，唯一索引 `uk_username` 兜底 |
| AUTH-19 | `@DistributedLock` 场景接入（角色权限变更互斥 + 缓存防击穿），实时 IT `LivePermissionLockIT` 2/0/0 |
| AUTH-20 | `PermissionCache` Cache-Aside 接入权限判定读路径 + 7 处变更点 `afterCommit` 失效 |
| AUTH-21 | `GET /auth/me`（`AuthMeResponse`：roles/permissions/menus），`SysMenuService#treeByPermissionCodes` 补齐祖先目录 |
| AUTH-22 | 新增 `PUT /user/{id}/password`、`PUT /user/{id}/status`（含权限码/审计/值对象/单测） |
| AUTH-23 | 补齐 4 个零覆盖 `ServiceImpl` 等单测（44 用例），`whale-auth` 55 → 99 → 120 全绿 |
| AUTH-24 | JaCoCo 0.8.12 接入 + `coverage-gate` profile（行 ≥40% / 分支 ≥30%） |
| AUTH-25 | Checkstyle 默认 `severity` `warning` → `error`，全 reactor 0 违规即阻断 |
| AUTH-26 | 清理 3 处未使用 import，`checkstyle:check` 0 WARN |
| AUTH-28 | Gitee Go 流水线映射（`.workflow/backend-ci.yml`、`frontend-ci.yml`）即代码；Gitee 侧触发待验证 |
| AUTH-29 | 网关路由前缀冲突修复：定夺「服务自持一级前缀、网关不 `StripPrefix`」（REQ-ARCH-003） |
| AUTH-30 | 网关限流 `RateLimitGlobalFilter`（Redis 计数器兜底 + 429 + TraceId 透传）；Sentinel 接入转 backlog |
| AUTH-31 | 端到端验证：直连 + 经网关全链路（login `code=0` → logout → 重放 401 → refresh 业务码 401 → `audit_log` 落库） |
| AUTH-35 / AUTH-36 / AUTH-37 | Redisson 升 4.8.0 实测兼容 Boot 4.0.8；数据源字符集参数修正；种子 admin 密文与口令 `123456` 对齐 |
| AUTH-38 | auth 管理前缀网关路由补齐（`/user`、`/dept`、`/role`、`/menu`、`/permission`），经网关冒烟通过 |

### 5.2 残余 / 阻塞项

| 项 | 状态 / 处置 |
|---|---|
| AUTH-08 审计导出 | **阻塞**：`POST /audit/log/export` 依赖 whale-file（8085）未启动，EXPORT 元审计一并待办 |
| AUTH-09 保留与归档任务 | **阻塞**：依赖 whale-job 未启动；热/温/冷策略与 `whale.audit.retention.days` 已设计 |
| AUTH-10 阶段二 MQ 汇聚 | **已闭环（2026-10-23）**：`AuditLogMqPublisher`（MQ→HTTP→本地三级降级）+ whale-audit 消费落 `bluewhale_audit`，`audit_mq_message` 幂等 + DLQ；whale-auth 已接 `WHALE_AUDIT_MQ_PUBLISH_ENABLED` 试点并实机验证 |
| AUTH-15 / AUTH-16 / AUTH-17 DDD 深水区 | **残余**：User 聚合与仓储（15）、Role/Menu/Permission/Dept 聚合 + 灰度开关（16）、AppService/领域事件/清理旧实现（17）均未启动 |
| AUTH-32 / AUTH-33 / AUTH-34 前端对接 | **残余**：admin 登录/登出对接（32）、`whale-ui` 组件封装（33）、管理页 + 按钮级权限渲染（34）；前置 `/auth/me`、网关路由均已解除 |
| AUTH-39 refresh 轮换实施 | **残余**：ADR-001 已采纳但未实现（旧 token 黑名单 + 复用检测 family 吊销 + 前端 token 持久化），待排期 |
| AUTH-27 全量回归 | **残余**：`mvn clean install -T 1C` + `pnpm build` 全量回归报告待出 |
| AUTH-30 Sentinel 端到端 | **残余**：Sentinel 未接入（当前为 Redis 计数器兜底），端到端 429 冒烟未做 |
| Micrometer 指标未接入 | **已登记偏差**：黑名单降级告警、限流等可观测性暂以「ERROR 日志标记 + 进程内计数」替代，待监控栈就绪后回填 Prometheus/Micrometer 指标 |
| 名称存量偏差 | N-02（黑名单 key 缺 `whale` 命名空间）、N-03（`whale-frontend/admin` 目录名）、N-04（`whale-i18n-sdk` 包名）待整改（`naming-conventions.md` §九） |

---

## 6. 原文索引

权限中心全部相关历史文档已以**副本**形式归置于 `docs/whale-auth/originals/`（共 **49 份**），**原位置文件（`docs/design`、`docs/plans`、`docs/requirements`、`docs/specs`、`docs/reviews`、`docs/devlog` 等）内容与路径均不变**，本文以原位最新文件为准。

| 分类 | 副本位置 | 文件（清单） |
|---|---|---|
| design | `originals/design/` | `ddd-whale-auth.md`、`audit-log.md`、`distributed.md`、`cicd.md`、`adr/2026-10-17-refresh-token-rotation.md` |
| plans | `originals/plans/` | `whale-auth-backlog.md`、`next-steps.md`、`roadmap.md`、`task-breakdown.md` |
| requirements | `originals/requirements/` | `register.md` |
| specs | `originals/specs/` | `2026-10-17-wave5.1b-audit-log/`、`2026-10-18-wave5.6-crosscutting-infra/`、`2026-10-18-wave5.7-auth-serviceimpl-ut/`、`2026-10-18-wave5.8-auth-permission-cache/`、`2026-10-18-wave5.9-auth-distributed-lock/`、`2026-10-18-wave5.10-multichannel-log/`（各含 spec/tasks/checklist 等） |
| reviews | `originals/reviews/` | `2026-10-17-wave5.1-security-closure.md`、`...-wave5-static-groundwork.md`、`...-whale-auth-token-blacklist.md`、`...-wave5.1b-audit-log.md` ~ `...-wave5.1g-naming-and-nacos-db.md`、`2026-10-18-auth23-serviceimpl-unit-tests.md`、`2026-10-18-wave5.6-crosscutting-infra.md`、`...-wave5.8-auth-permission-cache.md`、`...-wave5.9-auth-distributed-lock.md`、`...-wave5.10-multichannel-log.md` |
| devlog | `originals/devlog/` | `2026-10-16.md`、`2026-10-17.md`、`2026-10-18.md` |
| 平台根文档 | `originals/`（根） | `architecture.md`、`naming-conventions.md` |

---

## 7. 版本与变更记录

| 版本 | 日期 | 作者 | 变更说明 |
|---|---|---|---|
| v1.0 | 2026-10-18 | BlueWhale | 初始整合最终版。依据 **Wave 5.11**（权限中心残余收口：AUTH-01 网关占位密钥拒绝启动、AUTH-05 黑名单降级告警 `SecurityDegradationAlerter`、AUTH-07 审计读取元审计 `@AuditLog(type=QUERY)`、AUTH-13 `recordResult()` 返回体落库 `result_body`）。整合来源：`docs/plans/whale-auth-backlog.md`、`docs/design/ddd-whale-auth.md`、`docs/design/audit-log.md`、`docs/design/distributed.md`、`docs/architecture.md`、`docs/naming-conventions.md`、`docs/requirements/register.md`、`docs/specs/2026-10-18-wave5.11-auth-residual/spec.md` 及 `whale-auth`/`whale-gateway`/`whale-audit` 源码与 `db/init.sql`。 |

> 待确认事项：① `@AuditLog` 埋点处数（源码 22 vs 台账 20）；② 菜单种子行数（`init.sql` 6 vs 台账「8 个菜单项」）。

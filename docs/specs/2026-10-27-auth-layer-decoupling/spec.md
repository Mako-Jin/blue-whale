# 认证用例下沉应用层 + 领域层依赖最小化

- 状态：待批准（Spec Mode，批准后执行）
- 日期：2026-10-27
- 关联：REQ-ARCH-001、ADR-AUTH-004、[whale-service-auth 拆分 spec](../2026-10-26-whale-service-auth-split/spec.md)
- 影响模块：`backend/whale-common`（新增 `whale-common-core`）、`backend/whale-services/whale-service-auth`（5 个模块）
- 不改：Java 包根 `com.bluewhale.auth`、REST 契约、错误码/文案、Redis 键、数据库。

## 1. 背景与问题（审查结论）

对 `whale-service-auth` 按 DDD / 六边形架构审查后确认两处硬性违规：

| # | 问题 | 证据 | 后果 |
| --- | --- | --- | --- |
| P1 | 认证用例 `AuthService` 位于 **interfaces（主适配器层）**，且 interfaces pom 依赖 infrastructure | `whale-service-auth-interfaces/src/main/java/com/bluewhale/auth/service/AuthService.java`；`whale-service-auth-interfaces/pom.xml` L30-35 | 入站适配器直接依赖出站适配器与 Spring Security/JWT/MyBatis 具体实现，依赖倒置失效；应用逻辑无法脱离 Web 容器测试 |
| P2 | **domain** 依赖全栈 starter | `whale-service-auth-domain/pom.xml` L20-24 依赖 `whale-spring-boot-starter` | 领域层被传递引入 spring-web / mybatis-plus / redis / amqp / redisson，领域纯度仅靠约定维持 |

`AuthService` 实际只使用了 `whale-spring-boot-starter` 中的 `BizException` / `ErrorCode` 两个类（以及 `PageResult` 等其他 AppService 用途，不在本次范围）。因此 P2 只需抽出最小 `whale-common-core`。

## 2. 目标与范围

### 2.1 本次做

1. 在 `whale-common` 下新增 `whale-common-core`，仅承载 `BizException`、`ErrorCode`；domain 改依赖它。
2. 在 application 层新增 `com.bluewhale.auth.application.auth` 包，迁入登录/刷新/登出/切租户/me 用例 `SessionAppService`，通过端口（接口）反向依赖基础设施。
3. infrastructure 新增 4 个适配器实现这些端口；三个安全存储接口迁入 application 端口包。
4. interfaces 删除 `service/AuthService.java`，`AuthController` 只依赖 `SessionAppService`，并从 pom 移除 infrastructure 依赖。

### 2.2 本次不做（审查中的其余建议）

- `TenantStatusCache` 从 application 下沉到 infrastructure（application 暂时保留 `spring-boot-starter-data-redis`）。
- 引入 ArchUnit 固化分层依赖。
- `User` ↔ `UserRoleAssignment` 关联纳入聚合、异常包归位、`UserPermissionAppService` 等其它包整理。
- 不修改任何 REST 路径 / DTO / VO / 错误码 / 对外文案 / 日志内容 / Redis 键。

## 3. 目标依赖图

```
whale-common-core            （仅 lombok）
      ▲                     
      │
   domain ──> (无 Spring / MyBatis / Redis)
      ▲
application ──> domain, whale-common-core, whale-security, spring-security-crypto, data-redis, starter
      ▲
infrastructure ──> application, domain, whale-common-core, starter, whale-security, web, security, redis, redisson, mybatis, amqp
      ▲
interfaces ──> application, domain, whale-auth-sdk, starter, web, validation, security, springdoc   ← 删除 infrastructure 依赖
```

`starter` 继续聚合四层，运行时依赖清单不变。

## 4. 新增 `whale-common-core`

| 动作 | 路径 |
| --- | --- |
| 新建 pom | `backend/whale-common/whale-common-core/pom.xml`：parent `whale-parent`（`../whale-parent/pom.xml`），`artifactId=whale-common-core`，仅 `org.projectlombok:lombok`（optional） |
| 迁移 | `whale-spring-boot-starter/src/main/java/com/bluewhale/common/core/exception/BizException.java` → `whale-common-core/src/main/java/com/bluewhale/common/core/exception/BizException.java`（包名不变） |
| 迁移 | 同目录 `ErrorCode.java` 同上 |
| 修改 | `backend/whale-common/pom.xml`：`<modules>` 首位新增 `whale-common-core` |
| 修改 | `whale-spring-boot-starter/pom.xml`：新增 `com.bluewhale:whale-common-core:${project.version}` 依赖（`GlobalExceptionHandler` 等继续可用，并向下游传递） |
| 修改 | `whale-service-auth-domain/pom.xml`：`whale-spring-boot-starter` → `whale-common-core`；保留 lombok |

`whale-security`、`whale-tools` 均不使用 `common.core.*`（已 grep 验证），无需改动。

## 5. application 层（`com.bluewhale.auth.application.auth`）

### 5.1 值对象 / 常量 / 配置

```java
// AuthenticatedUser.java —— 应用层当前用户快照（不含口令）
public record AuthenticatedUser(Long tenantId, Long userId, String username, String nickname,
                                List<String> roles, boolean enabled) {
    public AuthenticatedUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}

// TokenPayload.java —— refreshToken 解析结果（由适配器从 Claims 转换，应用层不感知 jjwt）
public record TokenPayload(String subject, Long tenantId, Long userId, String tokenType,
                           String familyId, String jti, long issuedAtMillis) {
    public static final String TYPE_REFRESH = "refresh";
}

// AuthRoleCodes.java
public final class AuthRoleCodes {
    public static final String SUPER_ADMIN = "super_admin";
    private AuthRoleCodes() { }
}

// RefreshRotationProperties.java —— 从 infrastructure/config 迁入，注解与 prefix 原样保留
@Data @Component
@ConfigurationProperties(prefix = "whale.auth.refresh.rotation")
public class RefreshRotationProperties { private boolean enabled = false; }
```

> `@ConfigurationPropertiesScan` 在启动类扫描 `com.bluewhale.auth` 全包，迁移包后仍生效。
> `PermissionInterceptor.SUPER_ADMIN` 改为 `= AuthRoleCodes.SUPER_ADMIN`，保留 public 常量以免破坏既有引用/测试。

### 5.2 端口（`application/auth/port`）

| 端口 | 方法 |
| --- | --- |
| `CredentialAuthenticator` | `AuthenticatedUser authenticate(Long tenantId, String username, String rawPassword)`；失败抛 `BizException(401)`，文案由实现翻译为「用户名或密码错误 / 账号已停用 / 认证失败」 |
| `TokenIssuer` | `String issueAccessToken(Long tenantId, Long userId, String username, List<String> roles)`；`String issueRefreshToken(Long tenantId, Long userId, String username, String familyId)`；`long accessExpireSeconds()`；`long refreshExpireSeconds()`；`long remainingValiditySeconds(String token)`；`TokenPayload parse(String token)`（签名/过期非法时返回 `null`） |
| `TokenBlacklist` | 迁入：`void add(String token)`、`boolean contains(String token)` |
| `RefreshTokenRotationStore` | 迁入：`markUsed(String jti, long ttlSeconds)`、`isFamilyRevoked(String familyId)`、`revokeFamily(String familyId, long ttlSeconds)` |
| `UserAccessRevocationPort` | 由 `UserAccessRevocationStore` 迁入并更名：`void revoke(Long userId)`、`boolean isRevoked(Long userId, Date issuedAt)`（用例只用查询，`AuthCacheEvictionListener` 用写入） |
| `CurrentUserProvider` | `AuthenticatedUser current()`；未认证抛 `BizException(ErrorCode.UNAUTHORIZED)` |
| `UserSessionQuery` | `Optional<AuthenticatedUser> loadUserById(Long userId, Long tenantId)`；用户不存在返回 `Optional.empty()`（文案由用例决定，保证与现状逐字一致） |

> 旧接口 `com.bluewhale.auth.security.{TokenBlacklist,RefreshTokenRotationStore,UserAccessRevocationStore}` 删除；其 Redis 实现保留在 infrastructure，仅改 import 并实现新端口。

### 5.3 `SessionAppService`（`@Service`，方法语义化参数，不接触 dto）

迁移 `AuthService` 全部行为，方法签名：

```java
LoginResponse login(String tenantCode, String username, String rawPassword);
LoginResponse refresh(String refreshToken);
AuthMeResponse me();
List<MyTenantVO> listTenants();
LoginResponse switchTenant(String authorization, Long targetTenantId, String refreshToken);
void logout(String authorization, String refreshToken);
```

依赖（构造注入）：`CredentialAuthenticator`、`TokenIssuer`、`TokenBlacklist`、`RefreshTokenRotationStore`、`RefreshRotationProperties`、`UserAccessRevocationPort`、`CurrentUserProvider`、`UserSessionQuery`、`UserRepository`、`TenantAppService`、`TenantMembershipAppService`、`RoleAppService`、`MenuAppService`、`PermissionAppService`。

行为要点（必须与 `AuthService` 逐行等价）：

1. `login`：`tenantAppService.resolveEnabledTenantId(tenantCode)` → `credentialAuthenticator.authenticate(...)` → 记录最后登录时间 → 以新 `UUID.randomUUID()` familyId 签发。
   最后登录改为聚合行为：`userRepository.findByIdAcrossTenants(userId)` → `user.recordLogin(LocalDateTime.now())` → `userRepository.save(user)`；找不到用户时跳过（不影响登录结果）。
2. `refresh`：`tokenIssuer.parse` 为 `null` 或 `tokenType` 非 `TokenPayload.TYPE_REFRESH` → 401「refreshToken 无效或已过期」；family/租户/用户校验、黑名单、用户级撤销、`markUsed` 复用检测与整族吊销、`SecurityDegradationAlerter.alarm("auth:refresh:reuse", ...)` 全部沿用现有顺序、日志与文案。
3. `switchTenant`：`currentUserProvider.current()` → `requireSwitchable` → `userSessionQuery.loadUserById(current.userId(), targetTenantId)` 空则 401「账号不存在或已停用」→ 停用则 401「账号已停用」→ 旧 access/refresh 失效（轮换开启且有 familyId 时整族吊销）→ 日志 → 新 familyId 签发。
4. `logout`：Bearer 解析、双写黑名单、缺失时 debug 日志，全部照搬。
5. `me` / `buildLoginResponse` / `resolveBearerToken` / `resolveRotatingFamilyId` / `resolveTokenTenantId` / `resolveTokenUserId` / `requireRefreshNotRevoked` / `consumeRotatingToken` / `invalidateSwitchTokens` 按原逻辑迁移，仅把 `Claims` 读取换成 `TokenPayload` 访问器，`PermissionInterceptor.SUPER_ADMIN` 换成 `AuthRoleCodes.SUPER_ADMIN`。

### 5.4 领域行为新增

`User` 新增：

```java
/**
 * 行为：记录最近登录时间
 * @param loginTime 登录时刻
 */
public void recordLogin(LocalDateTime loginTime) {
    this.lastLoginTime = loginTime;
    this.updateTime = LocalDateTime.now();
}
```

`UserRepository.save` 走 `updateById`，与 `findByIdAcrossTenants` 配合可跨租户更新该用户行。

## 6. infrastructure 适配器（`com.bluewhale.auth.infrastructure.adapter.auth`）

| 类 | 实现 | 关键点 |
| --- | --- | --- |
| `SpringSecurityCredentialAuthenticator` | `CredentialAuthenticator` | `LoginTenantContext.set(tenantId)` → `AuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, rawPassword))` → `LoginUser` 转 `AuthenticatedUser`；`BadCredentialsException/DisabledException/AuthenticationException` 翻译为原三种 401 文案，`finally` 清上下文 |
| `JwtTokenIssuerAdapter` | `TokenIssuer` | 包装 `JwtTokenProvider`；`parse` 把 `Claims` 转 `TokenPayload`（`issuedAt` 用 `TokenTimeSupport.issuedAtMillis`，租户/用户 ID 用 `String.valueOf` 容错转换，非法返回 `null`） |
| `SecurityContextCurrentUserProvider` | `CurrentUserProvider` | 从 `SecurityContextHolder` 取 `LoginUser`，非 `LoginUser` 抛 `BizException(ErrorCode.UNAUTHORIZED)` |
| `UserDetailsSessionQuery` | `UserSessionQuery` | 复用 `UserDetailsServiceImpl.loadUserById`，`UsernameNotFoundException` → `Optional.empty()` |
| `SessionUserMapper` | 包内工具 | `static AuthenticatedUser toAuthenticatedUser(LoginUser user)`，供 1/3/4 复用 |

需改 import / 类型的既有文件：

- `security/RedisTokenBlacklist.java` → 实现 `application.auth.port.TokenBlacklist`
- `security/RedisRefreshTokenRotationStore.java` → 实现 `application.auth.port.RefreshTokenRotationStore`
- `security/RedisUserAccessRevocationStore.java` → 实现 `application.auth.port.UserAccessRevocationPort`
- `security/JwtAuthenticationFilter.java` → 注入 `TokenBlacklist`、`UserAccessRevocationPort`（import 更新）
- `infrastructure/event/AuthCacheEvictionListener.java` → 注入 `UserAccessRevocationPort`
- `security/PermissionInterceptor.java` → `SUPER_ADMIN = AuthRoleCodes.SUPER_ADMIN`
- `config/RefreshRotationProperties.java` 删除（已迁 application）

## 7. interfaces 层

| 文件 | 变更 |
| --- | --- |
| `service/AuthService.java` | **删除** |
| `controller/AuthController.java` | 注入 `SessionAppService`；dto → 语义参数映射留在 controller：`login(request.getTenantCode(), request.getUsername(), request.getPassword())`、`refresh(request.getRefreshToken())`、`switchTenant(authorization, request.getTenantId(), request.getRefreshToken())`、`logout(authorization, request == null ? null : request.getRefreshToken())`；`me()` / `listTenants()` 直通 |
| `pom.xml` | 删除 `whale-service-auth-infrastructure` 依赖与注释；其余不变 |

## 8. 测试改动（starter 模块 `src/test`）

1. `service/AuthServiceTest.java` → 迁移为 `application/auth/SessionAppServiceTest.java`（`@Mock` 端口 + 2 个 AppService + `UserRepository` + `CurrentUserProvider`；`TokenPayload` 构造替代 `Claims` 桩）。**保留全部 18 个用例与断言语义**：黑名单拒绝、access 冒充 refresh、正常换发、撤销前签发拒绝/撤销后放行、轮换消费、复用检测（含 `alarmCount==1`）、缺 familyId、family 已吊销、logout 三例、me 超管/普通/匿名、缺 userId/tenantId、listTenants、switchTenant 成功/非成员。
2. `controller/AuthControllerTest.java`：mock 类型改为 `SessionAppService`；`new AuthController(sessionAppService)`；`verify` 改为语义参数调用。
3. `security/JwtAuthenticationFilterTest.java`、`infrastructure/event/AuthCacheEvictionListenerTest.java`、`security/PermissionCacheTest.java`：mock 字段类型/import 改为 `UserAccessRevocationPort`（仅类型与 import，不改用例逻辑）。
4. 其余测试不动。

## 9. 验证（执行门禁）

在 `backend/` 下执行（PowerShell）：

```powershell
mvn -q -pl whale-common/whale-common-core -am -DskipTests install
mvn -q -pl whale-services/whale-service-auth -am test
mvn -q -pl whale-services/whale-service-auth/whale-service-auth-domain -am dependency:tree
```

验收标准：

- [ ] 三个命令 BUILD SUCCESS；原有测试全绿（含迁移后的 18 个用例）。
- [ ] `dependency:tree` 中 `whale-service-auth-domain` 不含 `spring-boot-starter-web`、`mybatis-plus`、`spring-boot-starter-data-redis`、`redisson`、`amqp`、`spring-security`。
- [ ] `interfaces` 源码与 pom 不再引用 `com.bluewhale.auth.{infrastructure,security,jwt,config,entity,mapper}`。
- [ ] `application` 源码不引用 `org.springframework.security.*`、`io.jsonwebtoken.*`、`com.bluewhale.auth.(jwt|security|config|entity|mapper)`。
- [ ] REST 路径、出入参、错误码与文案、日志内容零变更。

## 10. 风险与回滚

| 风险 | 缓解 |
| --- | --- |
| `whale-common-core` 未先安装导致下游 `-am` 编译失败 | 先执行 `whale-common-core` 的 `install`（门禁第 1 条） |
| 接口迁移漏改实现类 import | `mvn test` 编译期即失败；按 §6 清单核对 |
| 最后登录时间由 `SysUserMapper` 改为聚合 `save` 后字段覆盖 | `save` 使用 `updateById` 全字段更新，`findByIdAcrossTenants` 取到同一行完整快照；保留原表结构 |
| 错误文案漂移 | 测试保留原断言；§5.3 明确文案逐字保留 |

回滚：本次不删除任何业务表/资源，`git checkout -- .` + 删除新增目录（`whale-common-core`、`application/auth`、`infrastructure/adapter/auth`）即可完全恢复。

## 11. 需用户确认

1. 同意按 §4 新增 `whale-common-core`（而不是把 `BizException/ErrorCode` 复制一份到 domain）。
2. 同意本次只处理 P1、P2，其余审查项（`TenantStatusCache` 下沉、ArchUnit、包归位）留待后续。
3. 同意 `UserAccessRevocationStore` 迁入 application 时更名为 `UserAccessRevocationPort`（仅本服务内部，无跨服务影响）。

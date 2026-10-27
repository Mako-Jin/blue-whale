# tasks：认证用例下沉应用层 + 领域层依赖最小化

> 顺序执行；T2 完成后先编译 `whale-common-core`，再做应用层迁移。

## T1 建立 `whale-common-core`

- [x] T1.1 新建 `backend/whale-common/whale-common-core/pom.xml`（parent `whale-parent`，artifactId `whale-common-core`，仅 lombok optional）
- [x] T1.2 `git mv` `BizException.java`、`ErrorCode.java` 到 `whale-common-core/src/main/java/com/bluewhale/common/core/exception/`（包名不变）
- [x] T1.3 `backend/whale-common/pom.xml` modules 首位加入 `whale-common-core`
- [x] T1.4 `whale-spring-boot-starter/pom.xml` 增加 `whale-common-core` 依赖
- [x] T1.5 `whale-service-auth-domain/pom.xml`：`whale-spring-boot-starter` → `whale-common-core`
- [x] T1.6 验证：`mvn -q -pl whale-common/whale-common-core -am -DskipTests install`

## T2 application 层新增 auth 包

- [x] T2.1 新增 `AuthenticatedUser`、`TokenPayload`、`AuthRoleCodes`
- [x] T2.2 新增端口 `port/CredentialAuthenticator`、`port/TokenIssuer`、`port/CurrentUserProvider`、`port/UserSessionQuery`
- [x] T2.3 `git mv` `security/TokenBlacklist.java`、`security/RefreshTokenRotationStore.java` → `application/auth/port/`（改 package/import）
- [x] T2.4 `git mv` `security/UserAccessRevocationStore.java` → `application/auth/port/UserAccessRevocationPort.java`（改类名/package；保留 `revoke` 与 `isRevoked`）
- [x] T2.5 `git mv` `config/RefreshRotationProperties.java` → `application/auth/RefreshRotationProperties.java`（package 改）
- [x] T2.6 `User.java` 增加 `recordLogin(LocalDateTime)`
- [x] T2.7 新增 `SessionAppService`，按 spec §5.3 迁移 `AuthService` 全部逻辑与文案
- [x] T2.8 `application/pom.xml` 增加 `whale-common-core` 依赖
- [x] T2.9 `PermissionInterceptor.SUPER_ADMIN` 改为引用 `AuthRoleCodes.SUPER_ADMIN`

## T3 infrastructure 适配器

- [x] T3.1 新增 `infrastructure/adapter/auth/SessionUserMapper`
- [x] T3.2 新增 `SpringSecurityCredentialAuthenticator`
- [x] T3.3 新增 `JwtTokenIssuerAdapter`
- [x] T3.4 新增 `SecurityContextCurrentUserProvider`
- [x] T3.5 新增 `UserDetailsSessionQuery`
- [x] T3.6 改 import/实现类型：`RedisTokenBlacklist`、`RedisRefreshTokenRotationStore`、`RedisUserAccessRevocationStore`、`JwtAuthenticationFilter`、`AuthCacheEvictionListener`

## T4 interfaces 层

- [x] T4.1 删除 `service/AuthService.java`
- [x] T4.2 `AuthController` 改注入 `SessionAppService` 并做 dto → 参数映射
- [x] T4.3 `interfaces/pom.xml` 删除 `whale-service-auth-infrastructure` 依赖及注释

## T5 测试

- [x] T5.1 `git mv` `service/AuthServiceTest.java` → `application/auth/SessionAppServiceTest.java`，改 mock 端口与 `TokenPayload`，保留 18 个用例
- [x] T5.2 `AuthControllerTest` 改 mock `SessionAppService`
- [x] T5.3 `JwtAuthenticationFilterTest`、`AuthCacheEvictionListenerTest`、`PermissionCacheTest` 改 `UserAccessRevocationPort` 类型/import

## T6 验证

- [x] T6.1 `mvn -q -pl whale-services/whale-service-auth -am test`
- [x] T6.2 `mvn -q -pl whale-services/whale-service-auth/whale-service-auth-domain -am dependency:tree`，确认无 web/mybatis/redis/redisson/amqp/security
- [x] T6.3 grep 门禁：interfaces 无 `auth.(infrastructure|security|jwt|config|entity|mapper)`；application 无 `org.springframework.security`、`io.jsonwebtoken`
- [x] T6.4 `git status` 仅含预期改动（不动 `whale-agent/application.yml` 与 `skills/`）

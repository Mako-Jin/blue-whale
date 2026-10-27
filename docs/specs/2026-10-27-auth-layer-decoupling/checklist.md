# checklist：认证用例下沉应用层 + 领域层依赖最小化

## 模块与依赖
- [x] `whale-common-core` 模块存在，仅承载 `BizException`、`ErrorCode`，且无框架传递依赖
- [x] `whale-common/pom.xml` 已纳入 `whale-common-core`，`whale-spring-boot-starter` 已依赖它
- [x] `whale-service-auth-domain` 仅依赖 `whale-common-core` 与 lombok（dependency:tree 实测）
- [x] `whale-service-auth-interfaces/pom.xml` 已删除 `whale-service-auth-infrastructure` 依赖

## 应用层
- [x] `application/auth` 包存在 `AuthenticatedUser`、`TokenPayload`、`AuthRoleCodes`、`RefreshRotationProperties`、`SessionAppService`
- [x] 端口 `CredentialAuthenticator`、`TokenIssuer`、`CurrentUserProvider`、`UserSessionQuery`、`TokenBlacklist`、`RefreshTokenRotationStore`、`UserAccessRevocationPort` 定义在应用层
- [x] `SessionAppService` 覆盖 login / refresh / me / listTenants / switchTenant / logout，错误码、文案、日志与告警逐字保留
- [x] `application/auth` 源码不出现 `org.springframework.security`、`io.jsonwebtoken` 或 infrastructure/security/jwt 引用

## 基础设施层
- [x] `infrastructure/adapter/auth` 下存在 4 个适配器与 `SessionUserMapper`
- [x] `RedisTokenBlacklist`、`RedisRefreshTokenRotationStore`、`RedisUserAccessRevocationStore` 实现应用层端口
- [x] `JwtAuthenticationFilter`、`AuthCacheEvictionListener` 依赖端口类型而非旧 `security` 具体接口

## 接口层
- [x] `interfaces/service/AuthService.java` 已删除
- [x] `AuthController` 注入 `SessionAppService`，DTO → 参数映射正确，注解与方法签名未变
- [x] interfaces 源码不出现 `com.bluewhale.auth.(infrastructure|security|jwt|config|entity|mapper|service)`

## 测试与验证
- [x] `SessionAppServiceTest` 20 个用例全部通过（原 20 个用例语义保留）
- [x] `AuthControllerTest`、`JwtAuthenticationFilterTest`、`AuthCacheEvictionListenerTest`、`PermissionCacheTest` 全部通过
- [x] `mvn -pl whale-services/whale-service-auth -am test` BUILD SUCCESS（含 checkstyle 门禁）
- [x] 全仓无 `UserAccessRevocationStore` / `AuthService` 旧类型残留引用
- [x] 工作区未改动 `whale-agent/application.yml` 内容与 `skills/`（均为会话前既有状态）

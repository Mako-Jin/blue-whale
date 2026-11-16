# whale-gateway 代码评审响应（2026-11-16）

| 项 | 内容 |
|---|---|
| 评审对象 | `whale-backend/whale-services/whale-service-gateway`（Spring Cloud Gateway / WebFlux 栈） |
| 评审来源 | 外部代码评审意见（P0 3 条 / P1 6 条 / P2 14 条 / 测试缺口） |
| 处理原则 | P0/P1 全部修复；P2 除 P2-13（下游 stream 票据属业务口径）外全部闭环；集成测试待联调 |
| 验证 | 网关模块 208 项单测零失败，`BUILD SUCCESS`；`spring-boot-starter-web` 传递依赖检查为空 |

> 说明：评审时提供的是主代码文件，未包含测试目录；实际仓库网关模块已有 25 个测试类、
> 208 项用例（第一轮补充 6 项 P0 回归用例，第二批补充 9 项、第三批补充 8 项 P2 收尾用例）。

---

## 一、需求项（评审建议 → 处置结论）

| # | 优先级 | 建议 | 结论 |
|---|---|---|---|
| P0-1 | P0 | `GatewayAccessListProvider#refreshList` 防重入标记泄漏 | **采纳并修复**（`RouteOverrideProvider` 同类问题一并加固） |
| P0-2 | P0 | 熔断超时把权限码头注入耗时算进下游超时 | **采纳并修复**：`PermissionHeaderGlobalFilter` order `-50 → -80` |
| P0-3 | P0 | JWT `iat` 在未来导致撤销校验永久失效 | **采纳并修复**：验签后增加 5 分钟时钟漂移上限 |
| P1-1 | P1 | 每请求 4~5 次串行 Redis 往返 | **采纳并修复**：四段校验改 `Mono.zip` 并行，判定优先级不变 |
| P1-2 | P1 | 每请求重建 `SecretKey` | **采纳并修复**：构造期解析一次并复用 |
| P1-3 | P1 | 限流 Lua 不补 TTL | **采纳并修复**：`TTL < 0` 时补设过期 |
| P1-4 | P1 | 登录接口缺网关层防爆破 | **采纳**：新增 login/refresh/social-login/oauth2-token 四条 IP 级规则 |
| P1-5 | P1 | Nacos 路由读取在事件循环线程同步阻塞 | **采纳并修复**：`subscribeOn(boundedElastic)` |
| P1-6 | P1 | 熔断告警令牌来源与其它两处不一致 | **采纳并修复**：统一走 `whale.gateway.circuit-breaker.internal-token` |
| P2-2 | P2 | `X-User-Id` 字面量重复定义 | **采纳并修复**：新增 `GatewayHeaders` 统一常量 |
| P2-3 | P2 | 限流早于 TraceId，429 无 traceId | **采纳并修复**：order 调至 `-190`（TraceId 之后） |
| P2-5 | P2 | 空名单每 60s 一条 WARN | **采纳并修复**：仅「非空→空」迁移时 WARN |
| P2-9 | P2 | 熔断器 Map 只增不减 | **采纳**：补运维约定（移除熔断路由需重启），不改运行逻辑 |
| P2-11 | P2 | 指标命名混用下划线/点号 | **采纳并修复**：`gateway.rate_limit.rejected → gateway.rate.limit.rejected` |
| P2-12 | P2 | 传递依赖引入 Servlet 栈 / ENC 解密时机 | **已验证无需改动**：`dependency:tree` 无 `spring-boot-starter-web`；ENC 由 `EnvironmentPostProcessor` 在属性绑定前完成 |
| P2-1 | P2 | 6~7 处响应写出重复 | **采纳并修复**：新增 `GatewayResponseWriter`，7 个类 10 处写出统一（第二批） |
| P2-4 | P2 | `trust-forwarded-for` 默认 true 可被伪造 | 不改默认值：网关部署在 Nginx 之后，置 false 会把全部用户折叠成同一 IP 反而造成全局限流；保留配置开关并在 yml 固化上线检查项 |
| P2-6 | P2 | WS 路径硬编码三处同步 | **采纳并修复**：新增 `whale.gateway.ws-ticket.paths`，过滤器改读配置（第二批） |
| P2-7 | P2 | 本地 white-list 30+ 条 | **采纳并修复**：auth `V34` 补全远程名单 9 条；本地精简为 5 条登录闭环/外部回调兜底（第三批） |
| P2-8 | P2 | Cluster 迁移时键槽位一致性 | **采纳并修复**：`TokenBlacklistKeys` / `UserAccessRevocationKeys` 补槽位约定与改键迁移前提（不改键格式） |
| P2-10 | P2 | 下游 404 与路由不存在 404 语义混同 | **采纳并修复**：响应体文案区分「路由不存在 / 下游资源不存在」，HTTP 码不变 |
| P2-13 | P2 | 音视频流白名单鉴权机制 | 待做：审计下游 stream 一次性票据校验（属业务服务口径） |
| P2-14 | P2 | `/actuator/**` 免 JWT | **采纳并修复**：新增 `ActuatorInternalOnlyWebFilter` 内网来源限制（含 XFF 首段校验），不再依赖 Nginx 单点 |
| 测试 | — | 缺少 P0 回归保护 | **已补齐 6 项**：iat 未来、租户白名单旁路、同步异常复位（名单/覆盖）、告警令牌、限流 order |

---

## 二、完成项（代码改动）

### P0

1. `GatewayAccessListProvider#refreshList`
   - 改用 `Mono.defer(source)` 延迟创建请求链，并对 `source.get()` 的同步异常做 `try/catch` 兜底复位 `AtomicBoolean`；
   - `AuthGatewayAccessListClient#fetch` / `AuthGatewayRouteOverrideClient#fetch` 在地址为空白时直接返回
     `Mono.error`，从源头消除 `WebClient.uri(null/空)` 的同步抛出路径。
2. `RouteOverrideProvider#refresh`：同样改为 `Mono.defer(client::fetch)` + 兜底复位。
3. `PermissionHeaderGlobalFilter`：`ORDER` 由 `-50` 调整为 `-80`，保证权限码 Redis 读取与 `/auth/me`
   800ms 回落发生在熔断过滤器（`-75`）的 `timeout` 计时之外。
4. `JwtAuthGlobalFilter`：验签后、撤销校验前新增 `isIssuedInFuture`，`iat` 超前当前时间超过
   `ALLOWED_CLOCK_SKEW_MILLIS`（5 分钟）即 401 `invalid_token`。

### P1

5. `JwtAuthGlobalFilter`：拆出 `verifyAccessAndForward` / `decideAccess` / `forward`，
   登出黑名单、租户状态、成员撤销、用户撤销四段用 `Mono.zip` 并行执行，
   再按「黑名单 > 租户级白名单 > 租户停用 > 成员解绑 > 用户撤销 > 强制改密」短路判定。
6. `JwtAuthGlobalFilter`：`SecretKey` 改为构造期解析一次的 `final` 字段。
7. `RateLimitGlobalFilter`：Lua 脚本增加 `redis.call('TTL', KEYS[1]) < 0` 兜底补 TTL。
8. `application.yml`：`rate-limit.path-rules` 新增 `/auth/v1/login`(10/60s)、`/auth/v1/refresh`(20/60s)、
   `/social/v1/login`(10/60s)、`/oauth2/token`(60/60s)，阈值均可用环境变量覆盖。
9. `NacosRouteDefinitionRepository#getRouteDefinitions`：`Flux.defer(...).subscribeOn(Schedulers.boundedElastic())`。
10. `CircuitBreakerProperties` 新增 `internalToken`；`CircuitAlertNotifier` 改从配置项读取，
    `application.yml` 绑定 `${WHALE_INTERNAL_TOKEN:}`，与访问名单/路由覆盖三处一致。

### P2

11. 新增 `com.bluewhale.gateway.common.GatewayHeaders`，`JwtAuthGlobalFilter` 与
    `PermissionHeaderGlobalFilter` 共用身份头常量。
12. `RateLimitGlobalFilter`：order 调至 `-190`（TraceId `-200` 之后、内网屏蔽 `-110` 之前），
    429 响应可携带 `X-Trace-Id`；指标更名 `gateway.rate.limit.rejected`。
13. `GatewayAccessListProvider#replaceSnapshot`：空名单仅在「非空→空」时 WARN，其余 INFO。
14. `CircuitBreakerGlobalFilter`：补充 `apply-to-routes` 热更新移除路由需重启的运维注释。

### P2 第二批（2026-11-16 收尾）

15. 新增 `com.bluewhale.gateway.common.GatewayResponseWriter`：统一 401/403/429/503 JSON 写出、
    `application/json;charset=UTF-8` 与 `X-Trace-Id` 透传；响应已提交时跳过，避免二次写出。
    替换 `JwtAuthGlobalFilter`（3 处）、`RateLimitGlobalFilter`、`CircuitBreakerGlobalFilter`、
    `InternalApiDenyGlobalFilter`、`ActuatorGovernanceAuthWebFilter`、`ImWsTicketGuardGlobalFilter`、
    `GatewayExceptionHandler` 共 7 个类、10 处写出。
16. `WhaleGatewayProperties` 新增 `wsTicket.paths`（默认 `/im/ws`、`/im/world`、`/doc/ws`）；
    `ImWsTicketGuardGlobalFilter` 改读 `whale.gateway.ws-ticket.paths`（支持
    `WHALE_GATEWAY_WS_TICKET_PATHS` 覆盖），`application.yml` 注释声明与远程白名单、兜底路由的同步要求。
17. `TokenBlacklistKeys` / `UserAccessRevocationKeys`：补充 Redis Cluster 槽位约定——当前均为单键
    GET/SET，无跨槽原子操作；未来多键 Lua/MULTI 需统一 hash tag，且键格式变更须 auth/gateway 同批切换。
18. `GatewayExceptionHandler`：新增 `resolveMessage`，`NotFoundException` → 「路由不存在」，
    下游 `ResponseStatusException` 404 → 「下游资源不存在」，502/503/504 → 「服务暂时不可用」，
    其余 → 「系统异常」；HTTP 状态码映射不变。同步修正两处历史注释。

### P2 第三批（2026-11-16 收口 P2-7 / P2-14）

19. 新增 auth 迁移 `V34__gateway_whitelist_remote_complete.sql`：把此前只存在于网关本地的
    三方登录 3 条、OAuth2/发现文档 5 条、`/im/world` 1 条共 9 条平台级白名单幂等补入
    `sys_gateway_whitelist`；远程名单成为权威源。
20. 网关 `white-list` 由 24 条精简为 5 条兜底：`/auth/v1/login`、`/auth/v1/refresh`、
    `/brand/v1/resolve`、`/social/v1/*/callback`、`/contract/sign/callback/**`；
    其余放行项只在远程名单维护，管理端增删 ≤60s 生效。
21. 新增 `ActuatorInternalOnlyWebFilter`（order = `HIGHEST_PRECEDENCE`）：`/actuator/**`
    仅允许内网/回环来源，且 `X-Forwarded-For` 首段也必须是内网（阻断公网经 Nginx 转发）；
    配置 `whale.gateway.actuator.internal-only`（默认 true，`WHALE_GATEWAY_ACTUATOR_INTERNAL_ONLY`
    可关闭）。治理端点 `X-Governance-Token` 校验保持不变。


---

## 三、已修正问题项（本轮实际闭环的缺陷）

| 问题 | 修复前表现 | 修复后 |
|---|---|---|
| 名单刷新标记泄漏 | URI 配置为空等同步异常后，防重入标记永久 `true`，白/黑名单静默停更 | `defer + try/catch` 保证标记必复位，客户端提前返回错误信号 |
| 熔断误开 | auth 抖动时权限码头 800ms 回落 + 下游处理撞 3000ms 超时，打开下游熔断 | 权限码头在熔断计时之外完成，只统计真实下游耗时 |
| 撤销失效 | `iat` 在未来的令牌对停用/删除/改密/解绑/租户停用全部免疫 | 超出 5 分钟时钟漂移直接 401 |
| 鉴权 RTT 偏高 | 每请求 4 段串行 Redis + 权限码头 = 5 次往返 | 4 段并行约 1 次 RTT，整体降为 2 次 |
| 密钥重复解析 | 每请求 `getBytes` + `hmacShaKeyFor` | 启动期一次，请求仅复用 |
| 限流键永久 429 | 无 TTL 脏键导致脚本永不补过期 | `TTL < 0` 兜底补设，自动自愈 |
| 登录可撞库 | 仅全局 200/s，对单 IP 等于不限 | login/refresh/social/oauth2 token 独立 IP 阈值 |
| 事件循环阻塞 | 刷新若落在 Netty 线程，Nacos 同步读最长阻塞 3s | 切换到 `boundedElastic` |
| 告警静默丢失 | 仅读环境变量，Nacos 配了令牌也发不出 | 统一配置项，三处一致 |
| 429 无 traceId | 限流早于 TraceId 过滤器 | order `-190`，429 也带 `X-Trace-Id` |
| 响应写出散落 7 处 | 漏改一处即出现「某状态码无 traceId」或响应体不一致 | 统一 `GatewayResponseWriter`，traceId/Content-Type/已提交保护一次收口 |
| WS 路径硬编码 | 新增 WS 端点漏改过滤器 → 能连上但下游永远拒绝 | 过滤器读 `ws-ticket.paths`，新增端点只改配置（远程白名单/路由仍需同步，已在配置注释标注） |
| 404 无法区分责任方 | 路由缺失与下游 404 都返回「服务不可用」 | 分别返回「路由不存在」「下游资源不存在」 |
| 白名单管理端失效 | 本地 white-list 与远程名单重复，管理端删除条目后本地仍放行 | 本地精简为 5 条兜底，其余只由远程名单决定 |
| actuator 公网可读 | `health/info/prometheus` 匿名可达，暴露 JVM/线程/指标 | 仅内网来源可访问，公网经 Nginx 转发也被 XFF 首段校验拦截 |

---

## 四、待做项

| 项 | 说明 |
|---|---|
| Lua 原子性集成测试 | 需真实 Redis（Testcontainers/本地实例），单测仅覆盖脚本契约与降级路径 |
| 网关集成测试 | `@SpringBootTest + WebTestClient`，依赖 Nacos/Redis 环境 |

---

## 五、验证记录

- 命令：`.\mvnw.cmd -f whale-backend\whale-services\whale-service-gateway\pom.xml -s D:\maven\conf\settings.xml test -DskipITs`
- 结果：`Tests run: 208, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，checkstyle 0 违规
- 第一批新增/调整用例：
  - `JwtAuthGlobalFilterTest`：`iat` 未来 401、租户级白名单旁路治理校验；四段校验改为并行后补充默认桩；
  - `GatewayAccessListProviderTest` / `RouteOverrideProviderTest`：同步异常后标记复位、后续刷新仍执行；
  - `CircuitAlertNotifierTest`：内网令牌改由配置项提供，新增配置令牌时发送请求头断言；
  - `PermissionHeaderGlobalFilterTest` / `RateLimitGlobalFilterTest`：order 断言（-80 / -190）。
- 第二批新增用例：
  - `GatewayResponseWriterTest`（4 项）：状态码/JSON/Content-Type、traceId 透传且缺失不生成、按需生成、已提交跳过；
  - `GatewayExceptionHandlerTest`（4 项）：路由 404「路由不存在」、下游 404「下游资源不存在」、503 保留且补 traceId、500「系统异常」；
  - `ImWsTicketGuardGlobalFilterTest`（+1 项）：自定义 `ws-ticket.paths` 后新路径拦截、原默认路径放行。
- 第三批新增用例：
  - `ActuatorInternalOnlyWebFilterTest`（8 项）：回环/内网放行、公网直连 403、内网代理 + 公网 XFF 403、
    非 actuator 路径放行、XFF 非 IP 字面量拒绝、开关关闭放行、order 早于治理令牌过滤器；
  - auth `FlywayMigrationScriptTest`（7 项）：V34 命名、版本递增、无破坏性语句校验全部通过（`BUILD SUCCESS`）。
- 依赖检查：`maven-dependency-plugin:tree -Dincludes=org.springframework.boot:spring-boot-starter-web` 无匹配，
  WebFlux 栈未被 Servlet 栈污染。

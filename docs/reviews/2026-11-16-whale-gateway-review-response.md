# whale-gateway 代码评审响应（2026-11-16）

| 项 | 内容 |
|---|---|
| 评审对象 | `whale-backend/whale-services/whale-service-gateway`（Spring Cloud Gateway / WebFlux 栈） |
| 评审来源 | 外部代码评审意见（P0 3 条 / P1 6 条 / P2 14 条 / 测试缺口） |
| 处理原则 | P0 全部本轮修复；P1 全部本轮修复（P1-4 以配置规则落地）；P2 优先修复维护风险与排障效率项，业务口径/运维归属类登记待做 |
| 验证 | 网关模块 177 项单测零失败，`BUILD SUCCESS`；`spring-boot-starter-web` 传递依赖检查为空 |

> 说明：评审时提供的是主代码文件，未包含测试目录；实际仓库网关模块已有 18 个测试类、
> 177 项用例（本轮在此基础上补充 6 项 P0 回归用例）。

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
| P2-1 | P2 | 6~7 处响应写出重复 | 待做：抽 `GatewayResponseWriter`（本轮不扩大改动面） |
| P2-4 | P2 | `trust-forwarded-for` 默认 true 可被伪造 | 不改默认值：网关部署在 Nginx 之后，置 false 会把全部用户折叠成同一 IP 反而造成全局限流；保留配置开关并在 yml 固化上线检查项 |
| P2-6 | P2 | WS 路径硬编码三处同步 | 待做：抽 `whale.gateway.ws-ticket.paths` |
| P2-7 | P2 | 本地 white-list 30+ 条 | 待做：需与权限中心远程名单同步迁移，单独排期 |
| P2-8 | P2 | Cluster 迁移时键槽位一致性 | 待做：在 `whale-security` Keys 类补 cluster-safe 注释 |
| P2-10 | P2 | 下游 404 与路由不存在 404 语义混同 | 待做：响应体 message 区分（当前 HTTP 码已正确） |
| P2-13 | P2 | 音视频流白名单鉴权机制 | 待做：审计下游 stream 一次性票据校验（属业务服务口径） |
| P2-14 | P2 | `/actuator/**` 免 JWT | 待做：Nginx 层限制 `/actuator` 仅内网（运维项） |
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

---

## 四、待做项

| 项 | 说明 |
|---|---|
| `GatewayResponseWriter` 抽取（P2-1） | 统一 401/403/429/503 响应写出与 traceId 透传，减少改漏 |
| WS 路径配置化（P2-6） | `whale.gateway.ws-ticket.paths`，避免 yml / fallback 路由 / 过滤器三处同步 |
| 本地 white-list 精简（P2-7） | 需与权限中心远程名单同批迁移，避免 auth 不可用时业务链路中断 |
| Cluster 槽位注释（P2-8） | `TokenBlacklistKeys` / `UserAccessRevocationKeys` 明确是否 hash tag |
| 下游 404 语义区分（P2-10） | 响应体 message 区分「路由不存在」与「下游 404」 |
| `/actuator` 内网限制（P2-14） | Nginx 层收口，或扩展 `ActuatorGovernanceAuthWebFilter` 覆盖写端点 |
| Lua 原子性集成测试 | 需真实 Redis（Testcontainers/本地实例），单测仅覆盖脚本契约与降级路径 |
| 网关集成测试 | `@SpringBootTest + WebTestClient`，依赖 Nacos/Redis 环境 |

---

## 五、验证记录

- 命令：`.\mvnw.cmd -f whale-backend\whale-services\whale-service-gateway\pom.xml -s D:\maven\conf\settings.xml test -DskipITs`
- 结果：`Tests run: 177, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`
- 新增/调整用例：
  - `JwtAuthGlobalFilterTest`：`iat` 未来 401、租户级白名单旁路治理校验；四段校验改为并行后补充默认桩；
  - `GatewayAccessListProviderTest` / `RouteOverrideProviderTest`：同步异常后标记复位、后续刷新仍执行；
  - `CircuitAlertNotifierTest`：内网令牌改由配置项提供，新增配置令牌时发送请求头断言；
  - `PermissionHeaderGlobalFilterTest` / `RateLimitGlobalFilterTest`：order 断言（-80 / -190）。
- 依赖检查：`maven-dependency-plugin:tree -Dincludes=org.springframework.boot:spring-boot-starter-web` 无匹配，
  WebFlux 栈未被 Servlet 栈污染。

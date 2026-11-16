# whale-gateway 平台化扩展响应（2026-11-16）

| 项 | 内容 |
|---|---|
| 评审来源 | 外部建议：分布式追踪 / JWT 密钥轮换 / 契约测试 / 本地缓存 / 灰度 / 自检等 |
| 本轮范围 | 用户确认「推荐三件事全做」：OTel 分布式追踪、JWT 密钥轮换（JWKS + kid）、契约测试 |
| 关联评审 | `docs/reviews/2026-11-16-whale-gateway-review-response.md`（P0/P1/P2 已修复） |
| 验证 | 网关 + auth + whale-security 模块单测 |

> 关键前提修正：auth 的 `/oauth2/jwks` 当前由 `ImmutableSecret` 提供，发布的是 **HS256 对称密钥**
> （`kty=oct`），且该端点位于网关白名单内免 JWT。任何可访问该端点的调用者都能下载签名密钥并伪造
> 任意用户/租户令牌。该问题优先于「无法轮换」，本轮按用户确认同步将 auth 签发迁移到 RS256。

---

## 一、需求项

| # | 需求 | 处置 |
|---|---|---|
| E-1 | 网关产生 span，与 `X-Trace-Id` 并存；网关到 auth / 告警的四类 WebClient 注入 W3C `traceparent` | 本轮落地 |
| E-2 | 网关消费 auth JWKS，按 `kid` 选 key，本地缓存 5~10 分钟，未知 `kid` 强制刷新一次；无 `kid` 回落 HS256 | 本轮落地 |
| E-3 | auth 签发迁移 RS256：配置私钥 + `kid`，JWKS 只发布公钥；存量 HS256 令牌过渡期仍可验签 | 本轮落地 |
| E-4 | 契约测试：白名单、黑名单、route-overrides、`/auth/me` permissions、JWT claims | 本轮落地 |
| E-5 | 修复 `/oauth2/jwks` 对称密钥外泄：HS256 模式下关闭该 HTTP 端点 | 本轮落地 |
| E-6 | 修复 route-overrides `data.items` 与 auth 实际 `data[]` 的形状漂移 | 本轮落地（契约测试首个战果） |
| E-7 | 本地二级缓存（权限码/租户状态）、灰度发布、路由级熔断参数、IP 封禁、mTLS、WAF 等 | 待做（不在本轮） |

---

## 二、完成项

| # | 完成内容 | 落点 |
|---|---|---|
| 1 | OTel 依赖与 OTLP 导出配置；采样率、collector 地址均可用环境变量覆盖 | `whale-service-gateway/pom.xml`、`application.yml`（`management.tracing` / `management.otlp.tracing`） |
| 2 | `TraceIdGlobalFilter` 融合业务追踪号与 OTel traceId：入站 `X-Trace-Id` 优先，其次当前 span traceId，最后本地生成；存在 span 时向下游写 `traceparent` | `filter/TraceIdGlobalFilter.java` |
| 3 | 出站 WebClient 统一装配 ObservationRegistry + `traceparent` 注入；覆盖权限码头回落、白名单/黑名单、路由覆盖 | `config/GatewayWebClientConfig.java`、`trace/TraceparentExchangeFilter.java` |
| 4 | 熔断告警在触发线程捕获 traceparent，异步上报时携带 | `support/CircuitAlertNotifier.java` |
| 5 | auth 支持 RS256：`private-key` + `key-id` + `previous-public-keys`（kid→公钥）；JWT 头写 kid，HS256 仍可验签过渡期旧令牌 | `JwtProperties`、`JwtKeyMaterial`、`JwtTokenProvider` |
| 6 | JWKS 只发布公钥；HS256 模式 `/oauth2/jwks` 返回 404，修复对称密钥可被公开下载的 P0 | `JwksExposureGuardFilter`、`OAuth2AuthorizationServerConfig#jwkSource` |
| 7 | 网关 JWKS 验签：按 kid 选 key、Caffeine 10 分钟缓存、未知 kid 强制刷新一次、30 秒失败负缓存、只接受公钥、HS256 回落共享密钥 | `security/JwksKeyStore.java`、`security/TokenHeader.java`、`JwtAuthGlobalFilter` |
| 8 | 启动校验允许 JWKS 模式下共享密钥为空（`jwks-enabled` 显式开启时才放行，避免削弱 AUTH-01） | `GatewaySecretPlaceholderChecker` |
| 9 | 跨服务契约常量统一（信封、名单、路由覆盖、`/auth/me`、JWT claims、请求头） | `whale-security` 的 `GatewayContract`；网关 `GatewayHeaders` 引用之 |
| 10 | 修复 route-overrides 形状漂移：auth 返回 `data[]`，网关原按 `data.items[]` 解析导致覆盖永远为空；现兼容两种形状，以 `data[]` 为准 | `route/AuthGatewayRouteOverrideClient.java` |
| 11 | 契约测试：白名单/黑名单响应、route-overrides（含 `data[]` 与 `data.items[]`）、`/auth/me` permissions、JWT claims、RS256 kid、旧 kid 轮换、JWKS 拒收对称密钥、traceparent 格式、JWKS 端点守卫 | 两侧新增/扩展测试类（见下） |

### 新增/调整测试

| 模块 | 测试 |
|---|---|
| gateway | `JwksKeyStoreTest`、`AuthGatewayRouteOverrideClientTest`、`AuthGatewayAccessListClientTest`、`TraceparentExchangeFilterTest`、`PermissionHeaderGlobalFilterTest#shouldParsePermissionsContract` |
| auth | `JwtKeyMaterialTest`（RS256/kid、HS256 过渡、旧 kid 轮换）、`JwksExposureGuardFilterTest`；`JwtTokenProviderTest` / `LiveTokenBlacklistIT` 适配新构造器 |

### 上线顺序（RS256 切换）

1. 生成 RSA 密钥对，配置 auth：`whale.auth.jwt.private-key`、`key-id`，重启 auth（新令牌 RS256，旧 HS256 仍可验签）。
2. 网关配置 `whale.gateway.jwt.jwks-enabled=true`，确认能拉取 `/oauth2/jwks`；无 kid 的存量 HS256 令牌继续走共享密钥。
3. **警告：必须等存量 accessToken 全部过期才能移除 `JWT_SECRET`。** 最短等待时间为
   `accessToken 有效期 + 时钟漂移余量`（当前 120 分钟 + 5 分钟）。提前移除会让尚未过期的
   HS256 令牌验签失败，用户被强制重登。
4. 轮换：auth 换新私钥 + 新 `key-id`，把旧公钥按旧 kid 放入 `previous-public-keys`，保留一个 accessToken 有效期后移除。

### 部署与运维注意

| 事项 | 说明 |
|---|---|
| JWKS 404 守卫应独立先上 | 该守卫不依赖 RS256 切换，可直接随下次发布上线；上线后 HS256 模式下 `/oauth2/jwks` 返回 404 属预期，`bw-auth-oauth2-m1-smoke.ps1` 已按 404=SKIP 处理 |
| 指标更名 | `gateway.rate_limit.rejected` → `gateway.rate.limit.rejected` 为破坏性变更；仓库内无其他引用，但需同步检查 Prometheus 告警规则/Grafana 看板；如外部已引用，过渡期可双发一个迭代 |
| trust-forwarded-for | 默认 `true` 的前提是网关仅经可信代理访问；非 dev 启动时已打印 WARN，部署侧应显式设置 `WHALE_GATEWAY_RATE_LIMIT_TRUST_XFF`，不要依赖默认值 |
| 契约形状 | route-overrides 的 `data` 为数组或 `{items:[]}`；其他形状显式抛错并保留上一份快照，不再静默清空 |
| JWKS 并发 | 未知 kid 并发刷新已合并为一次拉取；拉取失败后 30 秒负缓存，避免伪造 kid 放大请求 |

---

## 三、待做项

| # | 待做 | 原因 |
|---|---|---|
| 1 | 本地二级缓存：权限码 30~60s、租户状态 10~30s；撤销类保持每请求校验 | 需 auth 侧 Pub/Sub 失效广播，跨服务改动，单独迭代 |
| 2 | 灰度/金丝雀：Weight 断言 + 实例元数据 version + `RouteOverrideRule` 增加 weight | 需 Nacos 路由配置与运维页面配合 |
| 3 | 路由级超时/熔断参数（per-route override） | 依赖 DB 加列与治理页面 |
| 4 | 请求体大小 / Content-Type 前置拦截、IP/CIDR 封禁 | 安全加固，排期处理 |
| 5 | 优雅停机 + 启动依赖自检（Redis/Nacos/令牌/CORS） | 运维体验优化 |
| 6 | 结构化 JSON 日志 + MDC（traceId/userId/tenantId/routeId） | 需 logback encoder 与日志平台约定 |
| 7 | mTLS/per-service token、WAF、gRPC/WebSocket/SSE 治理、HTTP/2、Redis Cluster 注释 | 长期方向 |
| 8 | `GatewayFilterOrder` 常量类 + 链路图 | 结构性整理，随下一次过滤器改动落地 |

---

## 四、已修正问题项

| # | 问题 | 处置 |
|---|---|---|
| 1 | `/oauth2/jwks` 在 HS256 模式下发布对称密钥（P0：任何调用者可下载密钥伪造令牌） | HS256 模式该端点返回 404；RS256 模式仅发布公钥；启动日志明确提示 |
| 2 | route-overrides 契约漂移：auth `data[]` vs 网关 `data.items[]`，导致路由覆盖静默失效 | 网关解析以 `data[]` 为准并兼容 `data.items[]`；契约测试锁定形状 |
| 3 | 网关每请求用 `Keys.hmacShaKeyFor` 重建密钥 | 密钥材料启动期解析一次；HS256 回落密钥在 `JwksKeyStore` 构造期构建 |
| 4 | 熔断告警令牌来源与名单/覆盖拉取不一致 | 统一走 `CircuitBreakerProperties.internalToken` |
| 5 | 未知 kid 强制刷新复用「在途」旧快照，轮换后新 kid 永远找不到 | 引入拉取序号：只有「晚于当前快照发起」的拉取才会被 join，产出当前快照的旧拉取不再被误用 |
| 6 | `Mono<Void>` 后接 `switchIfEmpty` 会重复写 401 并触发响应头只读 | 改为 `Optional` 包装保证只 emit 一次 |
| 7 | route-overrides 的 `data` 形状非法时静默返回空覆盖 | 非数组且无 `items` 对象时显式抛异常，保留上一份快照 |
| 8 | 未知 kid 并发强制刷新可能惊群 | 用拉取序号合并并发刷新：只有「晚于当前快照发起」的拉取会被 join，其余复用同一 Mono |
| 9 | `/oauth2/jwks/` 尾斜杠可绕过 HS256 守卫 | 守卫按前缀匹配（`/oauth2/jwks` 及其子路径），并补充单测 |
| 10 | 四段 Redis 校验并行化的取舍未留痕 | 在 `JwtAuthGlobalFilter` 注释中记录：以异常令牌多 3 次 O(1) GET 换取常态 1 次 RTT，且各 checker 已各自降级 |


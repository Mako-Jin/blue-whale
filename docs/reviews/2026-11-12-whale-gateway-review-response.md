# whale-gateway 代码评审响应（2026-11-12）

| 项 | 内容 |
|---|---|
| 评审对象 | `whale-backend/whale-services/whale-gateway`（Spring Cloud Gateway / WebFlux 栈） |
| 评审来源 | 外部代码评审意见（正确性 P0 / 安全 P1 / 健壮性 P2 / 风格 P3 共 30 条） |
| 处理原则 | 影响契约、可被利用或可导致误判的问题本轮修复；依赖运行环境的测试类建议登记为待做；已知取舍以注释固化 |
| 验证 | `mvn -pl whale-services/whale-gateway test`：**133 个单测全部通过**（17 个测试类），BUILD SUCCESS |

---

## 一、需求项（评审建议 → 处置结论）

| # | 优先级 | 建议 | 结论 |
|---|---|---|---|
| 1 | P0 | 熔断过滤器把所有下游异常统一降级为 503，掩盖 404 等真实状态 | **采纳并修复** |
| 2 | P0 | 熔断器 Map 无上限、Gauge 重复注册 | **部分采纳**：键仅来自静态 `apply-to-routes`，无动态增长；补注释明确生命周期，不做回调清理 |
| 3 | P0 | 回落 `/auth/me` 无超时 | **采纳并修复** |
| 4 | P0 | 限流固定窗口边界可放大到 2×limit | **不修复**：固定窗口固有取舍，注释固化；滑动窗口留待后续 |
| 5 | P0 | 首次 `EXPIRE` 失败导致计数键永不过期 | **采纳并修复**（Lua 原子 INCR+EXPIRE） |
| 6 | P0 | `rollback()` 后 `previousRoutes` 语义易误读 | **采纳**：补 Javadoc 与 `isRolledBack()` |
| 7 | P0 | `stripServicePrefix` 单段路径与注释略有出入 | **不修复**：已论证无害，注释与实现一致 |
| 8 | P1 | `GatewayResponse.toJson()` 手写 JSON 无转义 | **采纳并修复** |
| 9 | P1 | JWT 密钥长度不足在运行期才暴露 | **采纳并修复**：启动期校验 ≥32 字节 |
| 10 | P1 | 白名单清身份头但保留 `Authorization` | **部分采纳**：`/auth/v1/refresh` 需要该头，保留行为并补注释说明 |
| 11 | P1 | `MessageDigest.isEqual` 长度不同提前返回 | **采纳**：先 SHA-256 摘要再比较 |
| 12 | P1 | 内网屏蔽 pattern 未覆盖更深前缀 | **采纳**：Javadoc 固化命名约定与补充规则要求 |
| 13 | P1 | 权限码逗号拼接 header | **不修复**：权限码形态固定无逗号；下游按逗号解析属既有契约 |
| 14 | P2 | 路由签名受 Map 迭代顺序影响 | **采纳并修复**：参数按 key 排序后生成签名 |
| 15 | P2 | 熔断告警异步任务无并发限制 | **不修复**：依赖 whale-alert 按 fingerprint 去重 |
| 16 | P2 | 半开只放行一次探测易抖动 | **采纳**：注释说明取舍 |
| 17 | P2 | 信任 XFF 首段可被伪造 | **采纳**：新增 `trust-forwarded-for` 开关并补配置注释 |
| 18 | P2 | traceId 未校验格式 | **采纳并修复**：长度 ≤64、字符集 `[A-Za-z0-9_-]` |
| 19 | P2 | 503 的 `ResponseStatusException` 被回成 HTTP 404 | **采纳并修复**：404/502/503/504 原样映射 |
| 20 | P2 | 网关指标不完整 | **采纳**：新增限流/认证/内网拒绝三类计数指标 |
| 21 | P2 | 权限码头测试用 raw type 且未验证 URI/头 | **采纳**：捕获并断言 `lb://whale-service-auth/auth/v1/me` 与 `Authorization`/`X-Tenant-Id` 透传 |
| 22 | P2 | 缺少 `@SpringBootTest` 集成测试 | **待做**：需 Nacos/Redis 环境，登记后续 |
| 23 | P2 | 半开测试依赖 `Thread.sleep` 有抖动风险 | **待做**：resilience4j 不支持注入 Clock，后续引入 Awaitility |
| 24 | P3 | 类注释仍提 `CircuitBreakerOperator` | **采纳并修复**：注释与 pom 同步改为手动状态机描述 |
| 25 | P3 | Jackson 2/3 栈是否统一 | **已确认**：权限码头用 Jackson 3；告警通知仅发字符串体，不涉及树模型 |
| 26 | P3 | `loadRouteDefinitions` 赋值非原子 | **采纳并修复**：快照交换段与 `rollback()` 共用监视器 |
| 27 | P3 | `windowSeconds=0` 除零 | **采纳并修复**：下界收敛为 1 并 WARN |
| 28 | P3 | 熔断参数无下界校验 | **采纳并修复**：窗口 ≥1、失败率 1~100、超时 ≥1ms |
| 29 | P3 | 生产 CORS 仍可能是 localhost 通配 | **采纳**：非 dev 启动期 WARN（不阻断） |
| 30 | P3 | 网关 debug 日志对所有 profile 生效 | **采纳并修复**：迁至 `application-dev.yml`，基线改 info |

---

## 二、完成项（代码改动）

### 正确性（P0）

1. `CircuitBreakerGlobalFilter`
   - 新增 `isUnavailable(Throwable)`：`Exceptions.unwrap` 后仅 `TimeoutException` / `IOException`（连接拒绝、重置、响应提前关闭）降级 **503**；
     其余异常记录日志后 `Mono.error` 继续抛出，交由 `GatewayExceptionHandler` 按真实语义映射。
   - `buildConfig()` 收敛参数：窗口 ≥1、失败率 1~100、等待期 ≥1ms；`timeout` 取 `max(1, timeoutMillis)`。
   - 补充 `breakers` Map 生命周期、半开单探测取舍、手动状态机（非 `CircuitBreakerOperator`）说明。
2. `PermissionHeaderProperties` + `PermissionHeaderGlobalFilter`
   - 新增 `fallback-timeout-millis`（默认 800），回落 `/auth/me` 强制 `.timeout(...)`，超时走 fail-closed 空权限码头。
3. `RateLimitGlobalFilter`
   - `INCR` 与首计数 `EXPIRE` 改为 Lua 脚本单次原子执行，消除「已计数但无 TTL」竞态；
   - `windowSeconds < 1` 回落 1 秒并 WARN，避免窗口编号除零；
   - 新增 `trust-forwarded-for`（默认 `true`，配合 Nginx 覆写）与 `trust-forwarded-for=false` 时的 `remoteAddress` 回落；
   - 新增指标 `gateway.rate_limit.rejected{rule}`。

### 安全（P1）

4. `GatewayResponse`
   - `toJson()` 对 `message` 做防御性转义（`"`、`\`、`\n`、`\r`、`\t`、`< 0x20` 控制字符转 `\u00XX`）。
5. `GatewaySecretPlaceholderChecker`
   - 非 dev 增加「密钥 ≥32 字节」启动期校验，错误前移，避免运行期 `WeakKeyException` 表现为 401；
   - 非 dev 检测到 CORS 来源仍为 `http://localhost*` 时输出 WARN。
6. `ActuatorGovernanceAuthWebFilter`
   - 治理令牌比较改为「SHA-256 摘要 + `MessageDigest.isEqual`」，长度差异不再泄露时序。
7. `JwtAuthGlobalFilter`
   - 明确注释白名单保留 `Authorization` 的原因（refresh/logout 依赖该头）；
   - 新增指标 `gateway.auth.rejected{reason}`，原因覆盖缺失、验签失败、非 accessToken、缺 claim、黑名单、租户停用、成员解绑、用户撤销、强制改密。
8. `InternalApiDenyProperties` / `InternalApiDenyGlobalFilter`
   - Javadoc 固化内网路径命名约定（`/<service>/internal/**`、`/<service>/<version>/internal/**`）与更深前缀必须补规则的约束；
   - 新增指标 `gateway.internal_deny.rejected`。

### 健壮性 / 可运维性（P2/P3）

9. `GatewayExceptionHandler`：路由不存在 → 404；下游不可用 → 保留 502/503/504；其余 → 500，业务码与 HTTP 码一致。
10. `TraceIdGlobalFilter`：入站 traceId 仅放行 `[A-Za-z0-9_-]{1,64}`，非法重新生成。
11. `NacosRouteDefinitionRepository`
    - 签名参数按 key 排序（`TreeMap`），避免键序变化触发无意义刷新；
    - `loadRouteDefinitions()` 快照交换段与 `rollback()` 共用监视器，消除「上一版指向新当前」的中间态；
    - `previousRoutes` 回滚后语义补充说明，并新增 `isRolledBack()` 供治理端点区分。
12. `application.yml` / `application-dev.yml`：基线日志 `info`，dev 覆盖 `debug`；补充权限码头超时、XFF 信任开关说明。
13. `pom.xml`：熔断依赖注释与实现同步（手动状态机）。

---

## 三、已修正问题项（本轮实际闭环的缺陷）

| 问题 | 修复前表现 | 修复后 |
|---|---|---|
| 非可用性异常误降级 | 下游 404 / 业务异常经异常通道抛出时被改写为 HTTP 503 | 仅连接失败/超时降级 503，其余透传给全局异常处理器 |
| `/auth/me` 无超时 | auth 抖动时请求线程持续挂起，放大故障 | 800ms 快速失败，fail-closed 注入空权限码 |
| 限流键无 TTL | `EXPIRE` 失败后计数键永久驻留，客户端被永久 429 | Lua 原子计数+过期，键必带 TTL |
| 503 被回成 404 | `ResponseStatusException(503)` 对外 HTTP 404，误导重试 | 502/503/504 原样返回 |
| 弱密钥延迟暴露 | 短密钥在首个请求抛 `WeakKeyException`，表现为 401 | 非 dev 启动期直接拒绝 |
| traceId 注入 | 超长/含控制字符的 traceId 进入日志与响应头 | 格式校验，非法即重新生成 |
| 窗口秒数为 0 | 窗口编号除零抛 `ArithmeticException` | 回落 1 秒窗口并 WARN |
| 配置键序误判变更 | 仅调整 YAML 键序即触发 `RefreshRoutesEvent` | 排序签名后不触发 |

---

## 四、待做项

| 项 | 说明 |
|---|---|
| 网关集成测试 | `@SpringBootTest + WebTestClient`：OPTIONS 预检不被 JWT 拦截、`/actuator/routeGovernance` 无令牌 403、`/file/internal/x` 403；需 Nacos/Redis 环境 |
| 半开测试去抖动 | 引入 Awaitility 或可注入时钟，替换 `Thread.sleep` |
| 限流滑动窗口 | 若业务反馈边界双倍流量不可接受，改 Redis ZSET 或双窗口加权 |
| 熔断路由清理 | 若后续 `apply-to-routes` 改为动态来源，需在路由删除时回调清理 `breakers` 与 Gauge |

---

## 五、验证记录

- 命令：`.\mvnw.cmd -f whale-backend\pom.xml -pl whale-services/whale-gateway -s D:\maven\conf\settings.xml -DskipITs test`
- 结果：`Tests run: 133, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`
- 新增/调整用例：
  - `CircuitBreakerGlobalFilterTest`：新增「非可用性异常继续抛出」；下游失败改用 `ConnectException` 验证 503 降级；
  - `RateLimitGlobalFilterTest`：改用 Lua 脚本桩，新增「窗口 0 不除零」；
  - `GatewaySecretPlaceholderCheckerTest`：新增「短密钥非 dev 拒绝」；
  - `PermissionHeaderGlobalFilterTest`：新增回落 URI 与 `Authorization`/`X-Tenant-Id` 透传断言。

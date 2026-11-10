# whale-gateway 网关服务 · 剩余任务台账（Backlog）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-ARCH-003（服务自持一级前缀、网关不剥离）、REQ-DIST-004（限流熔断）、REQ-SEC-003（网关黑名单校验）、REQ-SEC-005（网关占位密钥校验）、REQ-FUNC-006（内网接口收敛） |
| 生成日期 | 2026-10-19 ｜ 作者：BlueWhale |
| 数据来源 | [roadmap.md](roadmap.md) §2.2、[whale-auth-backlog.md](whale-auth-backlog.md) ⑦ 网关与联调、[wave5.21-four-tracks/spec.md](wave5.21-four-tracks/spec.md)、`whale-backend/whale-services/whale-gateway/` 源码与 `application.yml` |
| 落点模块 | `whale-backend/whale-services/whale-gateway`（端口 `8080`，Nacos 服务名 `whale-gateway`）；Spring Cloud Gateway 5.x（`spring-cloud-starter-gateway-server-webflux`，WebFlux 栈） |
| 快照口径 | **Wave 5.6**：限流（`RateLimitGlobalFilter` Redis 固定窗口 + 429 `Result` 体）；**Wave 5.21**：熔断（自研轻量熔断 `CircuitBreakerGlobalFilter`）+ Nacos 动态路由（`NacosRouteDefinitionRepository`）+ 内网接口屏蔽（`InternalApiDenyGlobalFilter`） |

> 说明：本文档**只新增**，不改动既有计划文件。网关相关历史项 `AUTH-01/29/30/31/38` 仍以 [whale-auth-backlog.md](whale-auth-backlog.md) ⑦ 为准，本文仅承接 Wave 5.21 及后续新增项。

---

## 一、已具备能力（Wave 5.21 后）

| 能力 | 状态 | 依据 |
|---|---|---|
| Nacos 服务发现 + 静态路由（8 条） | 已闭环 | `application.yml` `spring.cloud.gateway.routes` |
| **Nacos 动态路由**（配置中心热刷新） | 已闭环（Wave 5.21） | `spring.config.import=optional:nacos:whale-gateway-routes.yaml?group=DEFAULT_GROUP&refreshEnabled=true` + `route/NacosRouteDefinitionRepository`（snakeyaml 解析、失败返回空列表、监听 `EnvironmentChangeEvent` → `RefreshRoutesEvent`） |
| JWT 全局鉴权 `JwtAuthGlobalFilter` + 黑名单校验 | 已闭环 | `filter/JwtAuthGlobalFilter`、`TokenBlacklistChecker`（响应式，降级放行 + 告警） |
| **限流**（Redis 固定窗口） | 已闭环（Wave 5.6；429 端到端 Wave 5.19） | `filter/RateLimitGlobalFilter` + `RateLimitProperties`（`pathRules` 按路径分级） |
| **熔断降级**（resilience4j） | 已闭环（Wave 5.21 自研 → **Wave 5.35 替换**） | `filter/CircuitBreakerGlobalFilter`（order=-75）+ `config/CircuitBreakerProperties`（`apply-to-routes`、`timeout-millis`、`failure-rate-threshold`、`sliding-window-size`、`wait-duration-in-open-state-millis`）；resilience4j 2.3.0 手动状态机；503 + `Result` 体降级；指标名不变 |
| **动态路由配置治理** | 已闭环（Wave 5.35） | `route/RouteDefinitionValidator` + `route/NacosRouteDefinitionRepository`（双快照 / 整体拒绝 / 回滚固定标记）+ `actuator/RouteGovernanceEndpoint` |
| **内网接口屏蔽** | 已闭环（Wave 5.21） | `filter/InternalApiDenyGlobalFilter`（order=-110，`/file/internal/**` → 403） |
| 请求日志 / TraceId 链路追踪 | 已闭环 | `TraceIdGlobalFilter` |
| 网关占位密钥校验 | 已闭环（Wave 5.11，AUTH-01） | `config/GatewaySecretPlaceholderChecker` |

---

## 二、剩余任务清单

| 任务 | 内容 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|
| ~~**GW-01**~~ ✅ **已结案（Wave 5.35）** ↔ AUTH-30 | Sentinel 接入评估：以 Sentinel 替换/增强现有「限流 + 熔断」自研实现（当前为 Redis 计数器 + 自研熔断） | P3 | 容器/Dashboard 就绪后可灰度对比；不改变 429/503 契约 | **结论：不引入**——本地仓库无 `com.alibaba.csp` 制品、Boot 4.0.8 兼容未验证、无 Dashboard/规则源；现有 Redis 限流 + resilience4j 已满足契约。报告：[reviews/2026-10-21-wave5.35-sentinel-evaluation.md](../reviews/2026-10-21-wave5.35-sentinel-evaluation.md) |
| ~~**GW-02**~~ ✅ **已闭环（Wave 5.35）** | resilience4j 接入：以 `spring-cloud-starter-circuitbreaker-reactor-resilience4j` 替换自研熔断 | P3 | 依赖可解析 + 运行时装配可验证（Boot 4.0.8）；契约 `applyToRoutes` 等价 | **已完成**：`CircuitBreakerGlobalFilter` 改 resilience4j 2.3.0 手动状态机（`tryAcquirePermission`/`onSuccess`/`onError`），保留 `apply-to-routes`、503 `Result` 体、`gateway.circuit.*` 指标；`waitDurationInOpenState` 最小 1ms；单测全绿 |
| ~~**GW-03**~~ ✅ **已闭环（Wave 5.35）** | 动态路由配置治理：Nacos `whale-gateway-routes.yaml` 的 schema 校验 / 灰度发布 / 回滚提示 | P3 | 非法配置不阻断启动且可观测；变更可回滚 | **已完成**：`RouteDefinitionValidator` 白名单校验 + 非法配置**整体拒绝** + current/previous 双快照 + `gateway.routes.rejected{dataId}` + `GET/POST /actuator/routeGovernance` 回滚；**回滚固定标记**避免被 Nacos 刷新覆盖；运行时 `GW-GOVERNANCE-PASS`。**P1-13 追加（2026-10-27）**：治理端点原可匿名访问（actuator 不经过 `GlobalFilter`），现由 `ActuatorGovernanceAuthWebFilter` 强制 `X-Governance-Token`，令牌未配置时 fail-closed 403；运维调用需设置 `WHALE_GATEWAY_GOVERNANCE_TOKEN` 并带头，health/prometheus 不受影响（[报告](../reviews/2026-10-27-p1-13-actuator-governance-auth.md)） |
| ~~**GW-04**~~ ✅ **已闭环（Wave 5.22）** | 熔断指标可观测：熔断打开/半开/闭合状态与失败率目前仅日志，未暴露 Micrometer 指标 | P2 | 暴露 `gateway.circuit.*` 指标供监控告警 | **已完成**：`CircuitBreakerGlobalFilter` 注入 `ObjectProvider<MeterRegistry>`（缺省静默不注册）；注册 Counter `gateway.circuit.calls{route,result}`、Counter `gateway.circuit.short_circuits{route}`、Gauge `gateway.circuit.state{route}`（0=CLOSED/1=OPEN/2=HALF_OPEN，随熔断器创建登记）；503/429 契约不变 |
| ~~**GW-05**~~ ✅ **已闭环（Wave 5.22）** | 多路由熔断验证：`apply-to-routes` 现仅纳入 `whale-auth`，其余服务（job/file/audit）待按需纳入并验证 | P2 | 逐路由纳入后经网关验证 503 降级 | **已完成**：`apply-to-routes` 覆盖 `whale-auth, whale-file, whale-job, whale-audit`（yml 与 `CircuitBreakerProperties` 默认值一致）；运行时停 job/file 后经网关各 6 次 → 全 **503** `{"code":503,...}` + 日志「熔断已打开，短路请求并降级」 |
| ~~**GW-06**~~ ✅ **已闭环（Wave 5.22）** | 内网接口屏蔽覆盖评估：现仅 `/file/internal/**`，其余服务内网端点（如后续 job/audit 内网接口）纳入同一拦截 | P2 | 内网端点经网关一律 403 | **已完成**：`patterns` 配置化语义确认 + 多 pattern / Ant 通配 `**` / 前缀零层级 / 非命中放行单测；类 Javadoc 记录「其余服务当前无内网端点，规则就绪可配置追加」 |
| ~~**GW-07**~~ ✅ **已闭环（Wave 5.22）** | 网关层单元/集成测试基线维持：现有 48 用例（含熔断/限流/内网/路由）随新过滤器扩充 | P2 | 新增过滤器均有单测；checkstyle 0 违规 | **已完成**：用例 48 → **58**（新增指标与内网规则用例），`checkstyle:check` **0 违规** |

---

## 三、残余风险（未被验证 / 证据不足）

| # | 项 | 现状与风险 |
|---|---|---|
| 1 | 熔断仅运行时以「1ms 超时 + 4 窗口」构造故障验证过一次（半开探测路径未在运行时单独取证） | 半开探测由单测覆盖（`CircuitBreakerGlobalFilterTest`），运行时未单独复现 |
| 2 | ~~动态路由仅在「配置为空时返回空列表」与「环境变更触发刷新」两种路径有单测~~ → **已消除（Wave 5.35）** | 已在真实 Nacos（`tenant=public`）发布合法/非法/第二版配置并观察热刷新、整体拒绝与回滚；回滚固定标记单测 2 例 |
| 3 | 熔断状态为进程内（单实例） | 多实例部署时各实例独立计数，无全局一致性（可接受，符合本地熔断语义） |
| 4 | 动态路由 current/previous 快照为进程内（Wave 5.35） | 回滚只在当前实例生效，不回写 Nacos；多实例需重新发布上一版配置实现全局回滚 |

---

## 四、版本与变更记录

| 版本 | 日期 | 作者 | 变更说明 |
|---|---|---|---|
| v1.2 | 2026-10-21 | BlueWhale | **Wave 5.35 网关治理收口**：GW-03 动态路由配置治理闭环（校验器 + 整体拒绝 + 双快照 + `gateway.routes.rejected` + `/actuator/routeGovernance` 回滚 + **回滚固定标记**）；GW-02 resilience4j 2.3.0 替换自研熔断闭环（契约与指标名不变）；GW-01 Sentinel **评估结案（不引入）**。网关单测 **76/76**，运行时 `GW-GOVERNANCE-PASS`；残余风险 #2 消除，新增 #4（快照为进程内）。报告：[reviews/2026-10-21-wave5.35-workflow-gateway.md](../reviews/2026-10-21-wave5.35-workflow-gateway.md)、[Sentinel 评估](../reviews/2026-10-21-wave5.35-sentinel-evaluation.md) |
| v1.1 | 2026-10-21 | BlueWhale | Wave 5.34 收尾：GW-04~07 维持闭环；**GW-01/02/03 明确保持 P3 backlog**（本轮不实现）。另修复潜伏缺陷：`whale-apps` erp/mes/oa/pdm 与 `whale-config` 共 5 处 `${server.port:…}` → `${SERVER_PORT:…}`，消除带 actuator 服务启动时的 `Circular placeholder reference`（与网关无直接耦合，但属同批配置收尾）。 |
| v1.0 | 2026-10-19 | BlueWhale | 初始版。依据 **Wave 5.21**（网关熔断 + Nacos 动态路由 + 内网接口屏蔽）建立网关台账。 |

# whale-gateway 评审整改第二批（P2 收尾）实施方案

| 项 | 内容 |
|---|---|
| 关联评审 | [2026-11-16 whale-gateway 代码评审响应](../reviews/2026-11-16-whale-gateway-review-response.md) |
| 生成日期 | 2026-11-16 ｜ 作者：BlueWhale |
| 落点模块 | `whale-backend/whale-services/whale-service-gateway`、`whale-backend/whale-common/whale-security` |
| 范围 | 仅代码可闭环项：P2-1、P2-6、P2-8、P2-10 |

## 一、范围

| 项 | 目标 | 不做 |
|---|---|---|
| P2-1 | 抽取 `GatewayResponseWriter`，统一 401/403/429/503 响应写出与 `X-Trace-Id` 透传 | 不改变响应体结构、状态码与既有指标 |
| P2-6 | WS 握手 ticket 路径改为 `whale.gateway.ws-ticket.paths` 配置 | 不改路由定义（Nacos/兜底 YAML 仍是路由权威源），不合并 white-list |
| P2-8 | `TokenBlacklistKeys` / `UserAccessRevocationKeys` 补 Redis Cluster 槽位说明 | 不改现有键格式（改格式需 auth/gateway 同批切换，单独排期） |
| P2-10 | 区分「网关未匹配到路由」与「下游 404」的响应体 message | 不改 HTTP 状态码映射（已正确） |

排除项（沿用评审响应文档）：P2-7 本地 white-list 精简、P2-14 `/actuator` 内网收口、Lua 原子性集成测试、网关集成测试（联调/运维/协同项）。

## 二、设计

### 2.1 P2-1 GatewayResponseWriter

新增 `com.bluewhale.gateway.common.GatewayResponseWriter`，静态工具类（无状态、无 Spring 依赖）：

- `write(exchange, status, code, message)`：设置状态码、`application/json;charset=UTF-8`、透传入站 `X-Trace-Id`，写 `GatewayResponse` JSON；
- 重载 `write(..., boolean generateTraceIdIfMissing)`：异常兜底处理器需要「入站无 traceId 时生成」，保持原行为；
- 响应已提交时记 WARN 并返回 `Mono.empty()`，避免二次写出抛 `IllegalStateException`（原仅熔断降级有此保护）。

替换点（7 处）：

1. `JwtAuthGlobalFilter`：401 未认证、403 路径封禁、403 强制改密；
2. `RateLimitGlobalFilter`：429；
3. `CircuitBreakerGlobalFilter`：503；
4. `InternalApiDenyGlobalFilter`：403；
5. `ActuatorGovernanceAuthWebFilter`：403；
6. `ImWsTicketGuardGlobalFilter`：401；
7. `GatewayExceptionHandler`：404/500/502/503/504（生成 traceId）。

各调用点保留自己的指标计数与日志，仅替换响应写出。

### 2.2 P2-6 WS 路径配置化

- `WhaleGatewayProperties` 新增 `wsTicket.paths`，默认 `/im/ws`、`/im/world`、`/doc/ws`（与当前硬编码一致）；
- `ImWsTicketGuardGlobalFilter` 改为构造注入 `WhaleGatewayProperties`，按配置判断是否校验 ticket；
- `application.yml` 新增 `whale.gateway.ws-ticket.paths: ${WHALE_GATEWAY_WS_TICKET_PATHS:...}`，注释声明与 white-list、兜底路由 predicates 的同步要求。

### 2.3 P2-8 Cluster-safe 注释

- `TokenBlacklistKeys`：说明三类键均为单键 GET/SET/SETNX，不涉及跨槽多键操作；若未来引入 Lua/MULTI 批量键，需在动态段加 `{hashTag}` 并保证 auth/gateway 同批切换；
- `UserAccessRevocationKeys`：同上，明确当前单键语义；批量撤销场景的迁移前提。

### 2.4 P2-10 404 语义区分

`GatewayExceptionHandler#resolveMessage`：

| 异常/状态 | message |
|---|---|
| `NotFoundException`（网关未匹配路由） | `路由不存在` |
| `ResponseStatusException` 404（下游返回） | `下游资源不存在` |
| 502/503/504 | `服务暂时不可用` |
| 500 | `系统异常` |

## 三、测试计划

| 用例 | 断言 |
|---|---|
| `GatewayResponseWriterTest` | 状态码/Content-Type/响应体；入站 traceId 透传；缺失时按需生成；已提交不重复写 |
| `GatewayExceptionHandlerTest` | 路由不存在 404「路由不存在」；下游 404「下游资源不存在」；503「服务暂时不可用」且补 traceId；其他异常 500 |
| `ImWsTicketGuardGlobalFilterTest` | 默认路径行为不变；自定义 `paths` 生效（新路径拦截、旧路径放行） |
| 既有 177 项 | 响应体断言（429/503/403）不变，全量零失败 |

## 四、验证与收尾

1. 网关模块 `mvn -o test` 全量通过（基线 177，不回归）；
2. 回填本文「完成情况/验证结果」；
3. 更新评审响应文档（结论、完成项、待做项、验证记录）；
4. 追加 `docs/devlog/2026-11-16.md`；
5. 仅暂存相关路径，提交 `fix(gateway): ...` 并推送 `origin/main`。

## 五、完成情况（2026-11-16）

| 项 | 状态 | 落点 |
|---|---|---|
| P2-1 | 已完成 | 新增 `common/GatewayResponseWriter`；替换 JWT（3 处）、限流、熔断、内网屏蔽、actuator 治理、WS ticket、全局异常兜底共 7 个类、10 处写出；已提交响应不再二次写出 |
| P2-6 | 已完成 | `WhaleGatewayProperties.WsTicket.paths`（默认 `/im/ws`、`/im/world`、`/doc/ws`）+ `whale.gateway.ws-ticket.paths` 配置项 + `ImWsTicketGuardGlobalFilter` 改读配置 |
| P2-8 | 已完成 | `TokenBlacklistKeys`、`UserAccessRevocationKeys` 补 Redis Cluster 槽位约定与改键迁移前提 |
| P2-10 | 已完成 | `GatewayExceptionHandler#resolveMessage`：路由不存在 / 下游资源不存在 / 服务暂时不可用 / 系统异常 四类文案 |

附带修正：`gateway-routes-fallback.yaml` 与 `RouteDefinitionValidator` 中两处「404 服务不可用」的历史注释同步为「路由不存在」。

## 六、验证结果

- 命令：`.\mvnw.cmd -f whale-backend\whale-services\whale-service-gateway\pom.xml -s D:\maven\conf\settings.xml test -DskipITs`
- 结果：`Tests run: 200, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，checkstyle 0 违规。
- 新增用例：
  - `GatewayResponseWriterTest` 4 项：状态码/JSON/Content-Type、traceId 透传与不生成、按需生成、已提交跳过；
  - `GatewayExceptionHandlerTest` 4 项：路由 404、下游 404、503 保留 + 补 traceId、500；
  - `ImWsTicketGuardGlobalFilterTest` +1 项：自定义 `ws-ticket.paths` 后新路径拦截、原默认路径放行。
- 既有响应体断言（401/403/429/503）未改动，全量通过。
- `whale-security` 仅注释变更，无代码行为变化。


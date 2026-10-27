# P1 #13 检查报告：路由治理端点匿名可达（可回滚网关路由）

- 日期：2026-10-27
- 范围：`whale-gateway` actuator 端点暴露与鉴权
- 关联：Wave 5.35 GW-03 动态路由治理、REQ-SEC-016

## 1. 问题

`management.endpoints.web.exposure.include` 暴露了 `health,info,prometheus,routeGovernance`，且 `application.yml` 把 `/actuator/**` 放进 JWT 白名单。

两个事实叠加导致治理端点对任意网络调用者开放：

1. actuator 端点由网关自身的 `WebFluxEndpointHandlerMapping` 直接处理，**不经过 `RoutePredicateHandlerMapping`**，因此 `JwtAuthGlobalFilter`（`GlobalFilter`）根本不会执行，白名单与否都不影响 actuator；
2. `RouteGovernanceEndpoint` 声明了 `@WriteOperation` `rollback()`，`POST /actuator/routeGovernance` 可把当前路由整体回滚到上一版快照，并触发网关重载。

结果：任何能访问网关端口的人都能：

- `GET /actuator/routeGovernance` 读取全部路由 id / uri / 断言（内部拓扑泄露）；
- `POST /actuator/routeGovernance` 匿名回滚路由，造成部分业务 404/503（可用性与配置完整性风险）；
- `POST` 请求还会被反复触发，形成路由震荡。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `gateway/.../config/WhaleGatewayProperties.java` | 新增 `Governance` 配置（`whale.gateway.governance.token`，默认空） |
| `gateway/.../filter/ActuatorGovernanceAuthWebFilter.java`（新建） | WebFlux 全局 `WebFilter`，`Ordered.HIGHEST_PRECEDENCE + 10`，在 actuator handler 之前拦截 `/actuator/routeGovernance` 及其子路径；要求 `X-Governance-Token` 与配置令牌**常量时间比较**（`MessageDigest.isEqual`），未配置令牌或头缺失/不匹配一律 403 + `GatewayResponse` JSON + TraceId 透传 |
| `gateway/src/main/resources/application.yml` | 新增 `whale.gateway.governance.token: ${WHALE_GATEWAY_GOVERNANCE_TOKEN:}`，令牌未配置时端点 fail-closed |

`health` / `info` / `prometheus` 等只读观测端点不受影响，监控抓取保持匿名可用。使用 `WebFilter` 而非 `GlobalFilter` 是因为后者只作用于路由请求，覆盖不到 actuator。

## 3. 行为矩阵

| 请求 | 令牌配置 | `X-Governance-Token` | 结果 |
|---|---|---|---|
| `GET /actuator/routeGovernance` | 未配置 | 任意 | 403 |
| `GET /actuator/routeGovernance` | 已配置 | 缺失/错误 | 403 |
| `GET /actuator/routeGovernance` | 已配置 | 正确 | 200（返回摘要） |
| `POST /actuator/routeGovernance`（回滚） | 未配置 | 任意 | 403，不回滚 |
| `POST /actuator/routeGovernance`（回滚） | 已配置 | 正确 | 200（执行回滚） |
| `GET /actuator/health` | 任意 | 任意 | 200，匿名可用 |
| `GET /actuator/prometheus` | 任意 | 任意 | 200，匿名可用 |
| `GET /actuator/routeGovernanceX` | 任意 | 任意 | 不拦截（非治理前缀） |

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-gateway -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- gateway `112` 用例，`0` 失败、`0` 错误，BUILD SUCCESS；
- Checkstyle `0` 违规（parent / whale-security / gateway 三个模块）；
- 新增 `ActuatorGovernanceAuthWebFilterTest` 8 用例：令牌未配置 fail-closed、缺头 403、错头 403、GET/POST 正确令牌放行、`/actuator/health` 不拦截、相似前缀不误拦截、403 响应体含 `"code":403` 且透传 TraceId；
- 既有 104 用例全绿，无回归。

**运行方式变更**：Wave 5.35 的治理端点运行时验证（`GW-GOVERNANCE-PASS`）原先直接 `curl`；启用后需先设置令牌并带 `X-Governance-Token` 头。

## 5. 边界

- **令牌为空即禁用**：默认 fail-closed，运维需显式设置 `WHALE_GATEWAY_GOVERNANCE_TOKEN` 才能使用治理端点；不在非 dev 环境启动失败，因为端点属可选的运维能力；
- **令牌强度**：由部署方负责使用高熵随机值；常量时间比较避免逐字节时序泄露；
- **仍匿名可观测端点**：`health` / `info` / `prometheus` 保持开放，符合监控抓取需求；如需进一步收紧可改由内网 Nginx 限制来源；
- **非治理 actuator 端点**：`exposure.include` 未包含 `env`/`beans`/`shutdown` 等，无需额外处理；
- **多实例**：每个网关实例各自校验同一环境变量令牌，回滚仍只作用于当前实例（Wave 5.35 既定边界）。

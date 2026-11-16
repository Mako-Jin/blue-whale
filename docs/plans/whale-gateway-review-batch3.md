# whale-gateway 评审整改第三批（P2-7 / P2-14）实施方案

| 项 | 内容 |
|---|---|
| 关联评审 | [2026-11-16 whale-gateway 代码评审响应](../reviews/2026-11-16-whale-gateway-review-response.md) |
| 生成日期 | 2026-11-16 ｜ 作者：BlueWhale |
| 落点模块 | `whale-service-gateway`、`whale-service-auth`（迁移脚本） |
| 范围 | P2-7 本地 white-list 精简、P2-14 `/actuator/**` 访问收口 |

## 一、P2-7 本地 white-list 精简

### 现状
- 网关 `application.yml` 的 `whale.gateway.white-list` 已增长到 24 条，其中 15 条与
  auth 库 `sys_gateway_whitelist`（V13 种子）重复；
- 远程名单由 `GatewayAccessListProvider` 每 60s 拉取并与本地取并集，**本地重复项会让
  管理端停用/删除条目失效**（本地仍放行），是真正的治理风险；
- 冷启动时 auth 不可用则远程快照为空，只能依赖本地兜底。

### 方案
1. 新增 auth 迁移 `V34__gateway_whitelist_remote_complete.sql`：把 V13 之后新增、
   目前仅存在于网关本地的 9 条逐字补入 `sys_gateway_whitelist`（平台级，幂等）：
   - `/social/v1/*/authorize`、`/social/v1/*/callback`、`/social/v1/login`；
   - `/oauth2/token`、`/oauth2/revoke`、`/oauth2/introspect`、`/oauth2/jwks`；
   - `/.well-known/**`；
   - `/im/world`。
2. 网关本地 `white-list` 精简为 **登录闭环 + 外部不可重试回调** 5 条：
   - `/auth/v1/login`、`/auth/v1/refresh`、`/brand/v1/resolve`（登录页必须可用）；
   - `/social/v1/*/callback`（外部 IdP 跳转，不可重试）；
   - `/contract/sign/callback/**`（外部签章平台回调，不可重试）。
3. 其余业务放行项（文档、actuator、审计上报、WS、音视频流、OAuth2 端点等）只保留在
   远程名单，管理端增删 ≤60s 生效；auth 不可用时沿用上一份快照（`GatewayAccessListProvider`
   失败保留语义），冷启动极端场景仅影响非登录链路，需在运维手册注明。

## 二、P2-14 `/actuator/**` 访问收口

### 背景
`/actuator/**` 在 JWT 白名单内，`health/info/prometheus/routeGovernance` 匿名可达；
仓库内无 Nginx 配置，无法按运维建议直接改 Nginx，因此在网关代码侧增加等价收口。

### 方案
新增 `ActuatorInternalOnlyWebFilter`（order = `HIGHEST_PRECEDENCE`，早于治理令牌过滤器）：
- 仅拦截 `/actuator` 与 `/actuator/**`；
- **双重来源校验**：直连远端地址必须为内网/回环，且若存在 `X-Forwarded-For`，其首段客户端地址
  也必须为内网——前者阻断公网直连，后者阻断 Nginx 代理进来的公网请求；
- 内网判定：回环、站点本地（10/8、172.16/12、192.168/16）、链路本地、IPv6 ULA `fc00::/7`；
- 非内网返回 403 `{"code":403,"message":"管理端点仅限内网访问"}`，不透传下游；
- 配置 `whale.gateway.actuator.internal-only`（默认 `true`，可用
  `WHALE_GATEWAY_ACTUATOR_INTERNAL_ONLY` 关闭），关闭时不做来源限制。

治理端点 `routeGovernance` 的 `X-Governance-Token` 校验保持不变：内网来源 + 令牌双重要求。

## 三、测试计划

| 用例 | 断言 |
|---|---|
| 回环/内网直连 | 放行至后续链 |
| 公网直连 | 403，不进入链 |
| 内网代理 + 公网 XFF | 403（Nginx 代理场景） |
| 内网代理 + 内网 XFF | 放行 |
| 非 actuator 路径 + 公网来源 | 放行（不影响业务流量） |
| 关闭 `internal-only` | 公网来源放行 |
| auth `FlywayMigrationScriptTest` | V34 命名/版本递增/无破坏性语句 |

## 四、收尾

1. 网关模块全量单测 + auth `FlywayMigrationScriptTest` 通过；
2. 回填本文、评审响应文档与 `docs/devlog/2026-11-16.md`；
3. 按用户要求一并提交 `assets/主人公/原始素材/hero.glb`（约 79.7 MB），推送 `origin/main`。

## 五、完成情况（2026-11-16）

| 项 | 状态 | 落点 |
|---|---|---|
| P2-7 | 已完成 | auth `V34__gateway_whitelist_remote_complete.sql`（9 条平台级、幂等）；网关 `white-list` 24 → 5 条；`docs/design/gateway-access-list.md` 同步 |
| P2-14 | 已完成 | 新增 `ActuatorInternalOnlyWebFilter` + `whale.gateway.actuator.internal-only`（默认 true） |

## 六、验证结果

- 网关：`Tests run: 208, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，checkstyle 0 违规；
  新增 `ActuatorInternalOnlyWebFilterTest` 8 项。
- auth 迁移：`FlywayMigrationScriptTest` 7 项全部通过（V34 命名、版本递增、无破坏性语句校验），`BUILD SUCCESS`。
- 说明：本地未安装 auth-application 快照时该测试会因 `TenantProvisioningAppService` 缺类失败，
  属环境依赖问题；本轮以 `-am` 构建依赖模块后执行，7/7 通过。


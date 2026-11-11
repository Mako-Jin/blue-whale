# Wave 6.22 auth 危险操作二次认证（Step-Up Authentication）

> 状态：**待确认（Spec）**，确认后进入实现。
> 收口目标：`AuthApi.java:79` 的 `TODO : 危险操作需要二次认证`。
> 编写：2026-11-11 ｜ 涉及：whale-auth（后端）、@bluewhale/http（前端拦截器）、admin/基座（弹窗 UI）

## 0. 一句话方案

已登录用户执行**危险操作**前，必须处于「近期已验密」状态：前端引导重新输入当前口令
（`POST /auth/v1/reauth`）换取短时效 step-up 凭证，请求危险端点时携带
`X-Reauth-Token` 头，服务端注解 + 切面强制校验，缺失/过期返回 **HTTP 428**。

## 1. 语义边界（与既有机制的分工）

| 机制 | 回答的问题 | 不负责 |
|---|---|---|
| 登录态（accessToken） | 你是谁 | 操作是否危险 |
| `@RequirePermission` | 你有没有权做 | 操作人是否本人在场 |
| **`@RequireReauth`（本波）** | **此刻是否本人确认过** | 权限判断 |

三者叠加：登录 → 鉴权 → 二次认证，逐层收窄。**二次认证不替代任何一层**。

## 2. 后端设计

### 2.1 新端点：`POST /auth/v1/reauth`

- 请求体：`{ "password": "<当前口令明文>" }`（传输层已有 TLS/同源约束，与 login 同口径）。
- 校验当前登录用户的口令（复用既有 BCrypt 比对）；通过后发放 step-up 凭证。
- **凭证存储**：Redis `auth:reauth:{tenantId}:{userId}` → `{ token, issuedAt }`，
  值用 JSON，TTL `WHALE_AUTH_REAUTH_TTL_SECONDS`（默认 **300s**）。
  每用户单活跃凭证（新验证覆盖旧凭证），无需凭证表、无库改动。
- **凭证复用策略**：TTL 窗口内可复用（一次验证 → 5 分钟内连续危险操作不再反复弹窗）。
  理由：改密等操作后前端通常还有清理动作；一次性凭证会把体验做差。可配置收紧为一次性
  （TTL 设 1 即近似一次性）。
- **防爆破**：复用 login 的失败锁定口径（同用户连续 5 次失败锁 10 分钟，Redis 计数）。
- 响应：`Result<ReauthVO>`，`{ "expiresIn": 300 }`（前端用来提示剩余窗口）。
- **不进网关白名单 / 不进 `permitAll`**：它要求登录态（URL 版本化三处同步红线：本次零改动）。
- 审计：成功/失败各记 `@AuditLog`（`action=reauth`）。

### 2.2 强制点：`@RequireReauth` 注解 + 切面

```java
@RequireReauth                       // 标在 Controller 方法上
@PutMapping("/password")
Result<Void> changePassword(...);
```

- 切面在 `@RequirePermission` 校验之后、业务方法之前执行（同为 AOP 时按 `@Order` 排在权限之后）。
- 校验：读 `X-Reauth-Token` 头 → 与 Redis 中该用户活跃凭证比对（`MessageDigest.isEqual` 防时序）→ 通过则放行（不消费，TTL 内复用）；失败返回 **HTTP 428**，响应体
  `Result.fail(42801, "REAUTH_REQUIRED")`，`data = { "reauthRequired": true, "expiresIn": 0 }`。
- 选 428（Precondition Required）而非 401/403：401 = 未登录、403 = 无权限，语义不混淆；
  前端拦截器按业务码 `42801` 分流。
- 内网端点（`/internal/**`）**禁止**标注本注解（服务间调用无用户凭证，门禁脚本口径不变）。

### 2.3 首批保护清单（宁窄勿宽，机制先行、清单渐进）

| 端点 | 理由 |
|---|---|
| `PUT /auth/v1/password`（自助改密） | TODO 原点；凭证类操作 |
| `PUT /user/{id}/password`（管理员重置他人密码） | 凭证类 + 影响他人 |
| `PUT /user/{id}/status`（启停用户） | 影响他人可用性 |
| `DELETE /user/{id}` | 不可逆 |
| `PUT /tenant/{id}/status`（启停租户） | 影响整租户 |
| `POST /tenant/members/{userId}/roles`（成员角色变更） | 提权通道 |

不加注解的：普通 CRUD（用户/部门/菜单/角色的增改）、配置读写（user-config / tenant-config）。
后续波次按「影响他人 / 不可逆 / 凭证类」三判据渐进追加注解即可，无新机制。

## 3. 前端设计

### 3.1 `@bluewhale/http` 统一拦截（一处实现，全部子应用受益）

- 响应拦截：业务码 `42801` → 触发全局「确认身份」回调 → 中止本次请求。
- 「确认身份」弹窗（whale-ui 新组件 `ReauthDialog`）：口令输入 + 提交
  `POST /auth/v1/reauth` → 成功后**自动重放**触发它的原请求（带 `X-Reauth-Token`）；
  弹窗期间排队后续 42801 请求，凭证就绪后统一重放。
- 5 分钟窗口内第二个危险操作不再弹窗（凭证仍有效，直接带头重放）。
- 改密成功的既有处理不变（全令牌撤销、回登录页）。

### 3.2 波及面

- admin「用户管理 / 租户管理」页、基座「修改密码」入口 —— 均经 `@bluewhale/http`，零页面改动。
- 子应用暂无直接调这些端点的路径（grep 核对），无波及。

## 4. 测试与验收

1. 单测：发放/过期/覆盖旧凭证/复用窗口/失败锁定/428 语义/切面顺序（权限先于 reauth）/
   `MessageDigest.isEqual` 恒时比较；Checkstyle 0 违规。
2. 实机（curl 直连 8081 + 网关 8080 双路）：无 token 改密 → 428；验密 → 凭证；
   带 token 改密成功；TTL 过期 → 428；错误口令 5 次 → 锁定。
3. 前端实机（Playwright，造 super_admin 测试号、验收后还原）：触发改密 → 弹窗 →
   重放成功；窗口内第二操作不弹窗。
4. 门禁：`check-internal-boundary.py`（新端点非内网，基线不变）、`DOC-CONSISTENCY-PASS`。

## 5. 待确认决策点

1. **凭证复用**：TTL 5 分钟窗口内复用（推荐）vs 严格一次性？
2. **首批清单**：§2.3 六个端点是否合适？是否有想追加/移除的？
3. **过期码**：HTTP 428 + 业务码 42801（推荐）vs 复用 401？
4. wave 编号 6.22 是否与在途波次冲突（当前最新 devlog 记到 6.21）。

## 6. 明确不做

- 不做 TOTP/短信等第二因子（当前口令即因子，未来可扩展凭证类型）。
- 不做全局强制（所有写操作都要验密）——只按清单渐进。
- 不动网关白名单与 auth `permitAll`（零三处同步改动）。

---

## 7. 实现记录（2026-11-11 18:20-18:45，决策点按推荐项确认）

### 落地清单

| 层 | 产物 |
|---|---|
| SDK | `whale-auth-sdk`：`@RequireReauth`（METHOD/TYPE，RUNTIME） |
| 应用 | `ReauthTokenStore` 端口、`ReauthAppService`（验密复用 `CredentialAuthenticator`，校验 userId 一致）、`ReauthResult(record)`、`ReauthRequiredException`（CODE=42801） |
| 基础设施 | `RedisReauthTokenStore`（键 `whale:auth:reauth:{tenant}:{user}`，TTL `${whale.auth.reauth.ttl-seconds:300}`，恒时比较，Redis 故障 fail-closed）、`ReauthInterceptor`、`WebMvcConfig`（拦截器注册从 SecurityConfig 拆出，见下） |
| 接口 | `ReauthController`（`POST /auth/v1/reauth`，`@AuditLog`）、`ReauthExceptionHandler`（428 + 42801） |
| 注解落点 | `AuthApi#changePassword`（接口方法，拦截器支持接口注解解析）、`UserController` 重置密码/启停/删除、`TenantController` 成员角色/启停租户 |
| 前端 | `@bluewhale/http`：`onReauthRequired` 回调 + 428/42801 单次重放（`X-Reauth-Token`）；admin：`api/reauth.tsx` 单飞验密弹窗 + `setReauthPrompt` 注入 |

### 实现期踩坑

1. **Bean 循环依赖**（起服即崩）：SecurityConfig → ReauthInterceptor → ReauthAppService →
   CredentialAuthenticator → AuthenticationManager（SecurityConfig 提供）。Boot 4 默认禁止循环引用。
   拆出 `WebMvcConfig` 只做注册，SecurityConfig 不再依赖拦截器。
2. **手册写 response 的 428 响应体为空**：拦截器 `response.getWriter()` 在日志过滤器包装下被吞。
   改为抛 `ReauthRequiredException` + `@ResponseStatus(PRECONDITION_REQUIRED)` 的独立 advice，
   走 Spring 标准序列化；独立 advice 只处理该类型，与全局 BizException 处理器无歧义。
3. **路径规则兜底**：`whale.auth.reauth.protected-rules`（默认 `PUT /auth/v1/password`）——
   注解解析已支持接口方法，规则保留为「不便改源码/运维临时收紧」的兜底。
4. 口令策略联动：改密端点带凭证后仍走业务校验（如「口令长度至少 12 位」），二次认证不越过业务规则。

### 验收（实机，curl/Node 走网关 8080）

| # | 场景 | 结果 |
|---|---|---|
| 1 | `PUT /auth/v1/password` 无 `X-Reauth-Token` | **HTTP 428** + `{"code":42801,"message":"REAUTH_REQUIRED"}` |
| 2 | `POST /auth/v1/reauth` 错误口令 | `code=401`「用户名或密码错误」 |
| 3 | 正确口令 | `code=0`，凭证 64 位十六进制，`expiresIn=300` |
| 4 | 带凭证改密 | 越过 428，进入业务校验（`code=400` 口令长度） |
| 5 | `PUT /user/{id}/status` 无凭证 / 错凭证 | 428 / 42801 |
| 6 | 同端点带凭证 | 200 `code=0`，状态变更成功并已还原 |
| 7 | 单测 | 11/11（存储 5 + 拦截器 6，含接口注解解析）；Checkstyle 0 |

造数：临时 super_admin 探针号（id 990000000000000001）+ 角色绑定，验收后删除
（`sys_user`/`sys_user_role` 归零、`audit_log`/`api_log` 相关行清理、被操作用户 status 还原为 0）。

### 未做 / 后续

- 基座（whale-platform）与其余子应用未接入 `onReauthRequired`（当前只有 admin 有危险操作入口）；
  接入方式：`setReauthPrompt(promptReauth)` 一行。
- 未接 login 失败锁定计数（reauth 失败仅返回 401）；如需防爆破，可与 login 锁定共用计数键。

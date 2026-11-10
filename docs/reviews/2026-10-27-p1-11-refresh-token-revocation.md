# P1 #11 检查报告：改密/停用后 refreshToken 仍可换发令牌

- 日期：2026-10-27
- 范围：`whale-service-auth` 刷新链路、用户级撤销存储
- 关联：P1 #9 用户级访问撤销（REQ-SEC-012）、P1 #10 租户到期、REQ-SEC-014

## 1. 问题

P1 #9 让停用、删除、改密后签发的旧 accessToken 在网关与 auth 过滤器立即失效，但**没有覆盖 refreshToken**：

- `POST /auth/refresh` 不携带 accessToken，只校验 refreshToken 自身的签名/类型/黑名单、租户可用性、成员关系与用户启用状态；
- 用户「改密」不会改变启用状态，数据库校验全部通过，撤销前的 refreshToken 可继续换发**撤销之后签发**的新 accessToken/refreshToken；
- 新 accessToken 的 `iat` 晚于撤销时刻，网关与 auth 的用户级撤销校验自然放行，等于绕过 P1 #9 的整条撤销链路。

停用/删除由 `loadEnabledUser` 拦截、成员解绑由 `requireEnabledMembership` 拦截、租户到期由 `ensureEnabled` 拦截，**改密是唯一完全无拦截的路径**；即使账号已停用，若撤销键 TTL 短于 refreshToken 有效期，也存在撤销记录先过期、旧 refreshToken 再次可用的窗口。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `auth-interfaces/.../service/AuthService.java` | 新增 `UserAccessRevocationStore` 依赖；`refresh` 在 `loadEnabledUser` 之后、消费轮换令牌之前调用 `requireRefreshNotRevoked(userId, tenantId, claims)`，`iat <= 撤销时刻` 直接 401（`refreshToken 已失效，请重新登录`），不换发、不占用轮换标记 |
| `auth-infrastructure/.../security/RedisUserAccessRevocationStore.java` | 撤销键 TTL 由「accessToken 有效期 + 60s」改为「`max(accessToken, refreshToken)` 有效期 + 60s」，保证撤销记录覆盖旧 refreshToken 的剩余生命周期 |
| `AuthServiceTest.java` | 新增 `@Mock UserAccessRevocationStore`；新增「撤销前签发的 refreshToken → 401 且不换发/不消费」与「撤销后签发的 refreshToken → 正常换发」两用例 |
| `RedisUserAccessRevocationStoreTest.java` | TTL 断言改为覆盖 refreshToken 有效期（新增 `getRefreshExpireSeconds` 桩） |

## 3. 行为矩阵

| 管理动作 | accessToken（存量） | refreshToken（存量） | 重新登录 |
|---|---|---|---|
| 改密 | 网关/auth 401 | refresh 401，无法换发 | 可登录，新令牌正常 |
| 停用 | 网关/auth 401 | refresh 401（原先由用户状态拦截） | 拒绝（账号已停用） |
| 删除 | 网关/auth 401 | refresh 401（原先由用户不存在拦截） | 拒绝 |
| 启用（停用后） | 仍失效 | 仍失效 | 可登录，新令牌正常 |
| 成员解绑 | 目标租户 401 | refresh 目标租户 401（成员关系校验） | 目标租户不可登录 |
| 租户到期 | 网关 401 | refresh 401（租户可用性校验） | 拒绝 |

撤销键语义保持 `iat <= 撤销时刻`：撤销前签发（含 refreshToken）一律失效，撤销后重新登录签发的新令牌放行；TTL 覆盖两类令牌的最长有效期后由 Redis 自动清理。

## 4. 验证

```powershell
mvn -B -pl whale-backend/whale-services/whale-service-auth/whale-service-auth-starter -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- auth starter `402` 用例，`0` 失败、`0` 错误，`7` 跳过（Live IT），BUILD SUCCESS；
- Checkstyle `0` 违规（全模块）；
- `AuthServiceTest` 20 用例全绿，其中新增：
  - `refreshShouldRejectRefreshTokenIssuedBeforeUserRevocation`：撤销前签发 → 401，`generateAccessToken` 与 `markUsed` 均未调用；
  - `refreshShouldIssueTokensWhenRefreshTokenIssuedAfterUserRevocation`：撤销后签发 → 正常换发，且按 `(userId, issuedAt)` 查询撤销记录；
- `RedisUserAccessRevocationStoreTest` 8 用例全绿，TTL 断言为 `refreshToken 有效期 + 60s`；
- `Sm4CryptoTest` 为本机 JDK/JCE 既有失败，已隔离，与本次无关。

## 5. 边界

- **同秒重新登录**：JWT `iat` 为秒级精度，撤销后同一秒内重新登录的新令牌会被判为已撤销；实际登录交互跨秒，窗口可忽略（与 P1 #8/#9 一致）；
- **Redis 降级**：撤销键不可读时按「未撤销」放行，旧 refreshToken 仍可换发；此时权限缓存已失效、租户/成员/状态校验仍在，且输出 `SECURITY_DEGRADATION` 告警；
- **改密前已泄漏的 refreshToken**：本次在撤销记录有效期内阻断；超过 `refreshToken 有效期 + 60s` 后撤销记录过期，但泄漏令牌本身也已过期；
- **登出/切换租户**：仍由既有 token 黑名单与轮换 family 吊销负责，不依赖用户级撤销；
- **令牌轮换开启时**：撤销校验在 `markUsed` 之前，被撤销令牌不会消耗轮换标记，也不会误触发整族吊销告警。

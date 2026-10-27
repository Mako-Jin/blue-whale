# P1 #9 检查报告：用户停用/删除/改密后存量令牌与权限缓存失效

- 日期：2026-10-27
- 范围：`whale-service-auth` 用户管理链路、`whale-gateway` JWT 鉴权链路
- 关联：P1 #8 租户解绑存量令牌撤销、REQ-SEC-011

## 1. 问题

P1 #8 只覆盖「成员解绑」。用户级生命周期变更仍存在缺口：

### R1：停用/删除/改密不撤销存量令牌

网关只校验签名、登出黑名单、租户状态、成员关系，不校验用户状态。用户被停用或删除后，其 accessToken 在过期前仍可通过网关访问**未声明权限注解**的业务接口；改密后旧令牌同样继续有效。

### R2：`update` 路径漏发状态/改密事件

管理端编辑用户走 `PUT /user/{id}`。原实现无论是否修改状态或口令，都只发布 `UPDATED` 事件，而 `AuthCacheEvictionListener` 对 `UPDATED` 不做缓存失效：

- 通过编辑表单把用户改为停用：权限缓存不失效，最长 5 分钟内仍可能保留旧权限码；
- 通过编辑表单重置口令：不触发任何令牌撤销。

专用 `changeStatus` 路径虽发布 `STATUS_CHANGED`（会失效缓存），但不撤销令牌。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `whale-security/.../token/UserAccessRevocationKeys.java` | 新增跨租户用户级撤销键 `whale:auth:user:revoked:{userId}`，网关与 auth 共用 |
| `auth-infrastructure/.../security/UserAccessRevocationStore.java` | 新增用户级撤销端点：`revoke(userId)` / `isRevoked(userId, issuedAt)` |
| `auth-infrastructure/.../security/RedisUserAccessRevocationStore.java` | Redis 实现：值为撤销时刻 Unix 秒，TTL = accessToken 有效期 + 60s；Redis 不可用时写入仅告警、查询降级为未撤销并上报安全降级告警 |
| `auth-domain/.../event/AuthDomainEvent.java` | `ChangeType` 新增 `USER_DISABLED` |
| `auth-application/.../user/UserAppService.java` | `update` 对比变更前状态、识别口令变更，按「停用 > 改密 > 启用 > 普通更新」发布事件；`changeStatus` 停用发布 `USER_DISABLED`、启用发布 `STATUS_CHANGED` |
| `auth-infrastructure/.../event/AuthCacheEvictionListener.java` | `USER_DISABLED` 失效权限缓存；`USER_DISABLED`/`DELETED`/`PASSWORD_CHANGED` 写入用户级撤销时间戳 |
| `auth-infrastructure/.../security/JwtAuthenticationFilter.java` | 成员撤销校验后追加用户级撤销校验，直连 auth 服务同样拒绝存量令牌 |
| `gateway/.../security/UserAccessRevocationChecker.java` | 网关侧响应式校验：`iat <= 撤销时间` 即拒绝；Redis 缺失/异常降级放行并告警 |
| `gateway/.../filter/JwtAuthGlobalFilter.java` | 令牌必须含 `userId`；撤销校验抽为 `verifyRevocationsAndForward`，成员撤销 → 用户级撤销 → 注入身份头转发 |

## 3. 行为矩阵

| 管理动作 | 事件 | 权限缓存 | 存量令牌 |
|---|---|---|---|
| 编辑用户改为停用 | `USER_DISABLED` | 失效 | 全部租户即时失效 |
| 停用按钮 | `USER_DISABLED` | 失效 | 全部租户即时失效 |
| 启用用户 | `STATUS_CHANGED` | 失效 | 停用前令牌保持失效，重新登录后的新令牌可用 |
| 编辑/重置口令 | `PASSWORD_CHANGED` | 不变（权限未变） | 全部租户即时失效 |
| 删除用户 | `DELETED` | 失效 | 全部租户即时失效 |
| 仅改昵称/手机/部门 | `UPDATED` | 不变 | 不变 |
| 成员解绑 | `TENANT_UNBOUND` | 目标租户失效 | 目标租户即时失效 |

撤销键 `iat <= 撤销时刻` 的语义保证：撤销前签发的令牌失效，撤销后重新登录签发的新令牌放行；撤销键 TTL 覆盖 accessToken 剩余生命周期。

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-service-auth/whale-service-auth-starter -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -B -pl backend/whale-services/whale-gateway -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- auth starter `393` 用例，`0` 失败、`0` 错误，`7` 跳过（Live IT）；Checkstyle 通过；
- gateway `96` 用例，`0` 失败、`0` 错误；Checkstyle 通过；
- 新增/更新用例：
  - `RedisUserAccessRevocationStoreTest` 8：跨租户键、TTL/Unix 秒、签发先后、无记录、Redis 降级与告警计数；
  - `UserAccessRevocationCheckerTest` 5：键与 auth 侧一致、撤销/新令牌/无键、Redis 降级、非法 claim 不访问 Redis；
  - `JwtAuthGlobalFilterTest` 新增「用户级撤销 → 401 且不转发」；
  - `JwtAuthenticationFilterTest` 新增「用户级撤销前签发 → 不写入认证」；
  - `AuthCacheEvictionListenerTest` 新增停用/删除/改密撤销、改密不失效缓存、启停不撤销；
  - `UserAppServiceTest` 新增 `update` 停用/改密/启用/仅资料变更与 `changeStatus` 停用/启用的事件断言；
- `Sm4CryptoTest` 为本机 JDK/JCE 既有失败，已隔离，与本次无关。

## 5. 边界

- **同秒重新登录**：JWT `iat` 为秒级精度，撤销时刻取当前秒；撤销后同一秒内重新登录的新令牌会被判为已撤销。实际登录交互跨秒，窗口可忽略；
- **Redis 降级**：撤销键不可读写时，权限缓存仍已失效、权限码为空，无权限注解的接口在令牌过期前可能可访问；与登出黑名单、租户状态校验的可用性取舍一致，并输出 ERROR 级告警；
- **绕过网关直连业务服务**：撤销校验落在网关与 auth 服务；直连其他业务端口属于部署安全边界（生产仅暴露网关）；
- **会话级登出**：单设备登出仍由既有 token 黑名单负责；本次的用户级撤销用于账号状态变化，不替代单令牌黑名单。

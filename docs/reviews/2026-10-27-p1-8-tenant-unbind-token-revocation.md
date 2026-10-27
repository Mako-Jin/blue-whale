# P1 #8 检查报告：租户解绑后存量令牌撤销与租户角色清理

- 日期：2026-10-27
- 范围：`whale-service-auth` 成员解绑链路、`whale-gateway` JWT 鉴权链路
- 关联：P1 #7 权限缓存提交后失效与成员跨租户失效、Wave 5.54 Phase 5.3 成员关系（MT-09）

## 1. 问题

P1 #7 修复了成员绑定/解绑的权限缓存失效，但仍留有两个安全边界：

### R1：解绑后已签发的 accessToken 在过期前仍可用

网关 `JwtAuthGlobalFilter` 只校验签名/过期、令牌类型、登出黑名单与租户状态，不校验 `(userId, tenantId)` 成员关系。平台解绑后，用户持有的目标租户 accessToken（默认有效期 2h）仍可通过网关，仅在没有权限码的接口上也会被放行；刷新与切换虽会 401，但存量令牌不受影响。

### R2：解绑不删除租户内角色关联

`unbindTenant` 只软停用 `sys_user_tenant`，`sys_user_role` 中该租户的角色关联保留。重新绑定后原角色立即恢复，超出「离开租户即失去授权」的安全预期，也放大了 R1 的影响（旧令牌 + 保留角色）。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `whale-security/.../token/TenantMembershipRevocationKeys.java` | 新增共享键约定 `whale:{tenantId}:cache:auth:member:revoked:{userId}`，授权侧写入与网关侧读取指向同一键 |
| `auth-infrastructure/.../security/TenantMembershipRevocationStore.java` | 新增撤销存储端口：`revoke(tenantId,userId)` / `isRevoked(tenantId,userId,issuedAt)` |
| `auth-infrastructure/.../security/RedisTenantMembershipRevocationStore.java` | Redis 实现：值为撤销时刻 Unix 秒，TTL = accessToken 有效期 + 60s；Redis 不可用时写入仅告警、查询按未撤销降级并上报安全降级告警 |
| `auth-domain/.../event/AuthDomainEvent.java` | `ChangeType` 新增 `TENANT_UNBOUND` |
| `auth-domain/.../user/repository/UserRepository.java` + 实现 | 新增 `removeRolesInTenant(userId, tenantId)`：物理删除目标租户角色的关联行，不影响其他租户角色 |
| `auth-infrastructure/.../mapper/SysUserRoleMapper.java` | 新增 `@InterceptorIgnore(tenantLine) @Delete`：显式按 `sys_role.tenant_id` 删除关联，避免平台上下文给子查询追加 `tenant_id=1` 导致删不到目标租户角色 |
| `auth-application/.../tenant/TenantMembershipAppService.java` | `unbindTenant` 停用关系后清理本租户角色，发布 `TENANT_UNBOUND`（替换原 `ROLES_ASSIGNED`） |
| `auth-infrastructure/.../event/AuthCacheEvictionListener.java` | `TENANT_UNBOUND` 失效目标租户权限缓存，并写入撤销时间戳；缺少租户标识时告警跳过 |
| `auth-infrastructure/.../security/JwtAuthenticationFilter.java` | 解析 `(tenantId,userId,iat)` 后查询撤销记录，解绑前签发的令牌保持未认证（直连 auth 服务同样生效） |
| `gateway/.../security/MembershipRevocationChecker.java` | 网关侧响应式校验：`iat <= 撤销时间` 即拒绝；Redis 缺失/异常按未撤销降级并告警 |
| `gateway/.../filter/JwtAuthGlobalFilter.java` | 校验顺序追加「租户成员撤销」，命中返回 401 且不转发下游 |

## 3. 行为

1. 平台解绑 → 事务内软停用 `sys_user_tenant`、删除该租户角色关联；
2. 事务提交 → 监听器失效目标租户权限缓存并写入撤销时间戳；
3. 存量 accessToken 携带的 `iat` 不晚于撤销时刻 → 网关与 auth 过滤器均返回 401，无需等待令牌过期；
4. 重新绑定后需重新分配角色并重新登录；重新登录签发的新令牌 `iat` 晚于撤销时刻，正常放行；
5. 撤销键 TTL 覆盖 accessToken 剩余生命周期，过期后自动清理，不产生长期垃圾键。

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-service-auth/whale-service-auth-starter -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -B -pl backend/whale-services/whale-gateway -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- auth starter `378` 用例，`0` 失败、`0` 错误，`7` 跳过（Live IT）；Checkstyle 通过；
- gateway `90` 用例，`0` 失败、`0` 错误；Checkstyle 通过；
- 新增用例：
  - `RedisTenantMembershipRevocationStoreTest` 8：TTL/Unix 秒、真实/null 参数、签发时间先后、无记录、Redis 降级与告警计数；
  - `MembershipRevocationCheckerTest` 5：键与 auth 侧一致、撤销/新令牌/无键、Redis 降级、非法 claim 不访问 Redis；
  - `JwtAuthGlobalFilterTest` 新增「成员已解绑 → 401 且不转发」；
  - `JwtAuthenticationFilterTest` 新增「解绑前签发 → 不写入认证」；
  - `AuthCacheEvictionListenerTest` 新增解绑失效 + 撤销、缺租户跳过、角色分配不撤销；
  - `TenantMembershipAppServiceTest` 断言解绑清理角色并发布 `TENANT_UNBOUND`；`UserRepositoryImplTest` 覆盖角色关联删除；
- `Sm4CryptoTest` 为本机 JDK/JCE 既有失败，已隔离，与本次无关。

## 5. 边界

- **同秒重新登录**：JWT `iat` 为秒级精度，撤销时间取当前秒；若解绑后在同一秒内完成重新绑定并登录，新令牌会被判为已撤销。实际管理流程跨秒，窗口可忽略；
- **直连非网关服务**：撤销校验落在网关与 auth 服务；绕过网关直连其他业务端口属于部署安全边界（生产仅暴露网关），不在本次范围；
- **Redis 降级**：Redis 不可用时撤销记录无法写入/读取，存量令牌仍受角色清理影响（权限码为空），但无权限码的接口在令牌过期前可能仍可访问；该降级与登出黑名单、租户状态校验取舍一致，并输出 ERROR 级告警；
- **用户停用/删除令牌**：已由 [P1 #9](2026-10-27-p1-9-user-access-revocation.md) 补齐用户级撤销键，停用/删除/改密会撤销该用户全部租户的存量令牌；

# P1 #7 检查报告：权限缓存提交后失效与成员跨租户失效

- 日期：2026-10-27
- 范围：`whale-service-auth` 权限缓存失效链路
- 关联：P1 #3 跨实例广播、P1 #5 失效残余、Wave 5.54 Phase 5.3 成员关系（MT-09）

## 1. 问题

### R1：AFTER_COMMIT 监听器内二次延迟，缓存实际未失效

`AuthCacheEvictionListener` 使用 `@TransactionalEventListener(AFTER_COMMIT)`，监听器触发时事务同步仍处于激活状态。`PermissionCache` 之前再次调用 `TransactionSynchronizationManager.registerSynchronization(...afterCommit)`：

1. Spring 的 `triggerAfterCommit` 遍历的是事务同步的**快照**；
2. 监听器内新注册的同步不会被本轮 afterCommit 执行；
3. 事务清理时同步被清空，失效动作永久丢失。

单元测试在无事务同步时直接调用，`isSynchronizationActive=false` 走立即执行分支，因此未暴露该缺陷。真实运行中角色/用户/权限变更后的缓存删除可能完全不发生，只能等 TTL（默认 5m）自然过期。

### R2：成员绑定/解绑仍按平台租户失效

`TenantMembershipAppService#bindTenant/unbindTenant` 在平台租户上下文执行，原事件 `UserDomainEvent(ROLES_ASSIGNED)` 未携带目标租户。权限缓存 Key 为 `whale:{tenantId}:cache:auth:user:perm:{userId}`，监听器按平台租户删除，目标租户缓存未失效（P1 #5 已记录残余）。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `infrastructure/security/PermissionCache.java` | 移除 eviction 方法内的二次 `afterCommit` 注册；监听器触发即执行删除。新增 `evictUserInTenant(tenantId,userId)`，按显式租户坐标失效 |
| `domain/event/UserDomainEvent.java` | 新增可选 `tenantId` 分量，保留 2 参构造兼容既有事件（`tenantId=null` 表示按当前租户） |
| `infrastructure/event/AuthCacheEvictionListener.java` | 用户事件携带租户时调用 `evictUserInTenant`，否则维持 `evictUser` |
| `application/tenant/TenantMembershipAppService.java` | 角色分配、平台绑定、平台解绑统一发布带目标租户的 `UserDomainEvent` |
| 测试 | `PermissionCacheTest` 新增显式租户/空参/监听器提交后立即失效；`AuthCacheEvictionListenerTest` 新增带租户分支；`TenantMembershipAppServiceTest` 新增分配/解绑事件断言并更新绑定断言 |

## 3. 行为

1. 事务提交 → Spring 调用监听器 → `PermissionCache` 直接委托 `AuthPermissionCacheInvalidator` 批量 `DEL` 并广播；
2. 不再在 afterCommit 内注册新的同步回调，避免「注册成功但永不执行」；
3. 成员角色分配在目标租户上下文发布目标租户事件；
4. 平台绑定/解绑发布目标租户事件，监听器按 `(targetTenantId,userId)` 清理缓存，覆盖用户已打开的目标租户会话；
5. 无租户分量的事件仍按当前 `TenantContext` 失效，兼容既有用户启停/删除/平台角色变更路径。

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-service-auth/whale-service-auth-starter -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- auth starter `364` 用例，`0` 失败、`0` 错误，`7` 跳过（Live IT）；Checkstyle `0`；
- 新增回归用例显式模拟「事务同步仍激活时监听器触发」，修复前该用例不会调用失效器；
- `Sm4CryptoTest` 为本机 JDK/JCE 既有失败，已隔离，与本次无关。

## 5. 边界

- **活跃令牌撤销**：本次只修缓存失效。平台解绑后，用户已签发的目标租户访问令牌在过期前仍可能被下游接受（切换/刷新会校验关系并 401）；若要即时吊销，需要令牌黑名单或按 `(userId,tenantId)` 的版本号机制；
- **角色关联保留**：解绑只停用 `sys_user_tenant` 关系，不删除 `sys_user_role`；重新绑定后原角色仍生效，是否符合产品预期需另立需求确认；
- **多实例真机验证**：Redis 双实例广播/断连恢复仍需联调环境（与 P1 #3 边界一致）；
- 缓存失效仍为「精确到受影响用户」，不做全量 pattern 清理。

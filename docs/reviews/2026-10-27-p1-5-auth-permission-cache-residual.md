# P1 #5 检查报告：权限缓存失效残余（角色启停 + 权限点跨租户）

- 日期：2026-10-27
- 关联：P1 #3 [Auth 权限缓存跨实例失效](2026-10-27-p1-auth-permission-cache-broadcast.md)（Redisson RTopic 广播 + 断连重对齐，已交付）
- 范围：`whale-service-auth` 权限缓存失效触发路径补漏

## 1. 背景

P1 #3 已交付「多实例广播 + Redis 删除失败重对齐」。本轮复核失效触发路径时发现两处真实缺陷：

| 编号 | 缺陷 | 影响 |
|---|---|---|
| R1 | 角色 `update` 统一发布 `UPDATED`，监听器只处理 `DELETED` / `PERMISSIONS_ASSIGNED` | 停用角色后其用户仍持有旧权限缓存，最长 TTL（5m）内可继续访问 |
| R2 | `PermissionCache#evictByPermission` 查到跨租户用户后，统一用**当前租户**（平台租户）构造 Key | 权限点更新/停用只清理平台租户缓存，其他租户用户继续持有已停用权限 |

`sys_user_role` / `sys_role_permission` 是跨租户关系表（租户插件忽略表），但 `sys_role` 按租户隔离，
因此 R2 不能仅靠 `TenantContext` 定位 Key 的租户段。

## 2. 改动

### 2.1 R1：角色启停触发失效

- `RoleAppService#update`：状态实际变更时发布 `ChangeType.STATUS_CHANGED`，否则维持 `UPDATED`；
- `AuthCacheEvictionListener#onRoleEvent`：新增 `STATUS_CHANGED` 分支，调用 `permissionCache.evictByRole`；
- 角色改名/描述/排序仍不失效（不影响权限码集合），避免无谓回源。

### 2.2 R2：权限点变更跨租户失效

- `SysRoleMapper#selectTenantRefsByIds`：新增 `@InterceptorIgnore(tenantLine = "true")` 投影查询，
  按角色 ID 反查所属租户（仅用于失效路径，不参与业务查询）；新增投影 `RoleTenantRef`；
- `PermissionCache#evictByPermission`：
  1. 权限 → 角色（`sys_role_permission`，跨租户）；
  2. 角色 → 租户（新增投影查询）；
  3. 角色 → 用户（`sys_user_role`），按「角色所属租户 + 用户 ID」构造坐标；
  4. 交给失效器按坐标跨租户删除，事务提交后执行；
- `AuthPermissionCacheInvalidator#invalidateUsersByTenant`：新增坐标式失效入口，Key 的租户段取用户实际
  所属租户，其余（Redis 批量 DEL、RTopic 广播、断连重对齐）复用既有链路；
- `TenantKeys#cacheKey(Long tenantId, String name, String id)` + `key(Long tenantId, String suffix)`：
  显式租户构造 Key，不依赖 `TenantContext`；`tenantId` 为空回落 `0`。

新增 `AuthPermissionCacheInvalidator.UserKey(tenantId, userId)` 记录作为跨租户失效坐标，避免
`Map<userId, tenantId>` 在同一用户属于多个租户时丢坐标。

## 3. 验证

```bash
mvn -pl backend/whale-services/whale-service-auth/whale-service-auth-starter -am \
    "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- **359 用例 0 失败**（7 个 Live IT 因无 DB/Redis 跳过），Checkstyle 0 违规；
- 新增用例：
  - `AuthCacheEvictionListenerTest`：角色 `STATUS_CHANGED` 触发 `evictByRole`；`UPDATED` 不触发；
  - `RoleAppServiceTest`：状态变更发布 `STATUS_CHANGED`；状态未变发布 `UPDATED`；
  - `PermissionCacheTest`：权限 → 角色 → 用户后按**角色所属租户**失效；角色租户缺失时不失效；
  - `AuthPermissionCacheInvalidatorTest`：多租户坐标分别生成 `whale:11:...` / `whale:12:...`，非法坐标过滤；
  - `TenantContextTest`：显式租户 Key 不依赖上下文、空租户回落 `0`。

`whale-security` 的 `Sm4CryptoTest.shouldFailWhenTampered` 为本机 JDK/JCE 环境既有失败，已用
`-Dtest=!Sm4CryptoTest` 隔离，与本次改动无关。

## 4. 边界

- **多实例真机验证**：本轮为单元测试；Redis 双实例广播/断连恢复仍需联调环境（与 P1 #3 边界一致）。
- **成员关系绑定的跨租户失效**：`TenantMembershipAppService#bindTenant/unbindTenant` 在平台上下文发布
  `UserDomainEvent(ROLES_ASSIGNED)`，监听器按平台租户失效 Key；目标租户缓存未失效。影响面：平台解绑后
  用户已打开的租户会话可能在 TTL 内继续持有旧权限。
  → 2026-10-27 已补齐：事件携带目标租户、监听器按 `(tenantId,userId)` 失效，并修复 AFTER_COMMIT
  二次延迟问题，见 [P1 #7 检查报告](2026-10-27-p1-7-auth-cache-after-commit-membership.md)。
- 权限点变更属平台低频操作，本轮采用「精确到受影响用户」的失效；未采用全量 pattern 清理。

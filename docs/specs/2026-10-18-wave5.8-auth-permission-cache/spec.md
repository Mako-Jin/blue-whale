# Wave 5.8 · whale-auth 权限判定缓存场景接入规格（AUTH-20）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-DIST-003（缓存一致性）、REQ-FUNC-002（权限中心 RBAC+ABAC）、REQ-QA-002（代码检查留痕） |
| 任务映射 | AUTH-20 ↔ T5.5 工具的场景接入（Wave 5.6 已交付 `CacheConsistencyTemplate`，无业务使用方） |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [distributed.md](../../design/distributed.md) §3.2、§2.4；[next-steps.md](../../plans/next-steps.md) T5.5；[whale-auth-backlog.md](../../plans/whale-auth-backlog.md)；[devlog/2026-10-18.md](../../devlog/2026-10-18.md) §四 |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 按 [code-review-checklist.md](../../checklists/code-review-checklist.md) 自检留报告（REQ-QA-002）→ 构建验证 → 提交推送 |

---

## 一、需求项（本波次范围）

`PermissionInterceptor#preHandle` 是权限中心**每次受保护请求**的必经路径，当前对 `@RequirePermission` 兜底校验**每次都直查 DB**（`sys_user_role → sys_role_permission → sys_permission` 三段查询），在高频调用下形成稳定 DB 压力；Wave 5.6 已交付 `CacheConsistencyTemplate`（Cache-Aside + 空哨兵防穿透 + TTL 抖动防雪崩 + 延迟双删）但**尚无业务使用方**。

本波次把权限判定改为 Cache-Aside，并在权限/角色/用户变更处失效缓存，闭合 REQ-DIST-003「缓存一致性」的场景落地。

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-DIST-003 | 权限判定缓存（读路径）+ 变更失效（写路径） | 命中缓存时不再查 DB；变更后缓存失效；Redis 异常降级直查 |
| REQ-FUNC-002 | 权限中心受保护接口判定语义不变 | 既有 99 用例不回归；放行/拒绝结论与直查一致 |
| REQ-QA-002 | 单测 + 检查报告 | 报告逐项对应清单编号 |

---

## 二、现状与落点

| 项 | 现状 | 落点 |
|---|---|---|
| 读路径 | [PermissionInterceptor.java](../../../whale-backend/whale-services/whale-auth/src/main/java/com/bluewhale/auth/security/PermissionInterceptor.java) L72-75：`roleService.listPermissionCodesByUserId(userId).contains(required)` 每次直查 | 改为 `PermissionCache#getPermissionCodes(...)` |
| 缓存工具 | `com.bluewhale.common.cache.CacheConsistencyTemplate`（`getOrLoad(name,id,ttl,loader)` / `evict` / `evictWithDelay`，Key 约定 `{prefix}:{name}:{id}`） | 直接复用，不修改 starter |
| 写路径（失效点） | 角色分配权限 / 角色删除；用户分配角色 / 用户删除 / 用户启停；权限点更新 / 删除 | 上述 6 类操作后失效受影响用户的缓存 |

**缓存 Key 约定**（REQ-ARCH-004 `whale:<域>:<用途>:<标识>`）：`whale:cache:auth:user:perm:{userId}`。

---

## 三、设计

### 3.1 新增组件

| 组件 | 包 | 职责 |
|---|---|---|
| `PermissionCache` | `com.bluewhale.auth.security` | 权限码缓存读写的唯一入口：`getPermissionCodes` / `evictUser` / `evictUsers` / `evictByRole` / `evictByPermission` |
| `PermissionCacheProperties` | `com.bluewhale.auth.config` | `whale.auth.permission-cache.enabled`（默认 true）、`whale.auth.permission-cache.ttl`（默认 5 分钟） |

`PermissionCache` 依赖：`CacheConsistencyTemplate`、`PermissionCacheProperties`、`SysUserRoleMapper`、`SysRolePermissionMapper`。

> **依赖方向（避免循环依赖）**：`PermissionCache` **不注入** `SysRoleService`（否则 `SysRoleServiceImpl → PermissionCache → SysRoleService` 形成环，Boot 默认禁止循环引用导致启动失败）。因此：
> - 读路径的 DB 查询由**调用方以 `Supplier` 传入**（`PermissionInterceptor` 持有 `SysRoleService`，作为 loader 传入）；
> - 失效路径的「受影响用户」反查由 `PermissionCache` 直接用 Mapper 完成，**不复制**权限码查询逻辑（避免 C6 重复代码）。

### 3.2 读路径（Cache-Aside）

```
Set<String> getPermissionCodes(Long userId, Supplier<List<String>> loader)
  enabled == false            → 直接 loader（不读不写缓存）
  命中（含空结果哨兵）        → 直接返回
  未命中                      → loader 回源 → join(",") 写缓存（空列表写空串）→ 返回
```

- 序列化：权限码以 `,` 连接（权限码字符集不含 `,`）；空列表写空串，读回空串即空集合。
- Redis 任何异常由 `CacheConsistencyTemplate` 内部降级（读失败回源、写失败仅告警），不阻断主流程。
- 缓存未命中时的**击穿防护**（同 Key 并发仅一次回源）按 [distributed.md](../../design/distributed.md) §2.4 由 `@DistributedLock` 场景接入（AUTH-19）承担，**不在本波次范围**（登记残余项）。

### 3.3 写路径（先更库后删缓存 + 提交后失效）

变更点统一在**事务提交后**失效，避免「事务未提交即删缓存 → 并发请求回源读到旧值并写回」的脏缓存窗口（符合 [distributed.md](../../design/distributed.md) §3.2「先更库后删缓存」）。下表共 **7 处变更点**（角色 2 / 用户 3 / 权限点 2）：

| 变更点 | 失效动作 | 影响范围 |
|---|---|---|
| `SysRoleServiceImpl#assignPermissions` | `evictByRole(roleId)` | 该角色下全部用户 |
| `SysRoleServiceImpl#delete` | `evictByRole(roleId)` | 同上（关联权限行已删，权限码集合变化） |
| `SysUserServiceImpl#assignRoles` | `evictUser(userId)` | 该用户 |
| `SysUserServiceImpl#delete` | `evictUser(userId)` | 该用户 |
| `SysUserServiceImpl#changeStatus` | `evictUser(userId)` | 该用户（启停后不再沿用旧判定） |
| `SysPermissionServiceImpl#update` | `evictByPermission(id)` | 持有该权限点的角色 → 其用户 |
| `SysPermissionServiceImpl#delete` | `evictByPermission(id)` | 同上 |

实现方式：`PermissionCache` 提供 `afterCommit(Runnable)`（`TransactionSynchronizationManager` 同步激活时注册 `afterCommit` 回调，否则立即执行），变更点调用 `evictXxx` 时内部走该包装。

> **不失效的变更（有意为之）**：`SysRoleServiceImpl#update` 仅改 `roleName/description/sortNo/status`，不改变任何用户的权限码集合（`listPermissionCodesByUserId` 只按 `sys_permission.status` 过滤，不按角色状态过滤），故不失效；若后续判定逻辑引入角色状态过滤，需同步补齐（登记在任务残余项）。

### 3.4 配置与降级

| 配置 | 默认 | 说明 |
|---|---|---|
| `whale.auth.permission-cache.enabled` | `true` | 置 `false` 即回到直查 DB（秒级回滚开关） |
| `whale.auth.permission-cache.ttl` | `5m` | 正常值 TTL，写入时由 `CacheProperties.ttlJitterPercent`（默认 ±10%）叠加抖动 |

降级链：`enabled=false` → 直查；Redis 不可用 → 模板内部降级直查（读）/仅告警（写、删），权限判定结论始终由 DB 兜底，**不因缓存故障而误放行**。

---

## 四、验收

| 项 | 验证方式 | 通过标准 |
|---|---|---|
| 读路径 | `PermissionCacheTest` | 命中/未命中回源/空结果/`enabled=false`/Redis 降级 5 类路径断言成立 |
| 失效路径 | `PermissionCacheTest` + 3 个 `ServiceImplTest` | `evictByRole`/`evictByPermission` 反查得到正确 userId 集合；6 个变更点均触发失效；事务同步激活时走 `afterCommit` |
| 判定语义不回归 | `PermissionInterceptorTest`（改造）+ 全量单测 | 放行/拒绝结论不变；`whale-auth` 既有 99 用例不回归 |
| 覆盖率门禁 | `mvn -pl :whale-auth -am -Pcoverage-gate clean verify` | BUILD SUCCESS（LINE ≥ 0.40、BRANCH ≥ 0.30） |
| 代码规范 | `mvn checkstyle:check` | 全 reactor 0 违规（含 E16 Javadoc 门禁） |

---

## 五、风险与残余项

| 风险 / 残余 | 影响 | 处置 |
|---|---|---|
| 缓存一致性依赖失效点完备性 | 漏失效会导致**权限变更延迟生效**（最长 TTL=5 分钟，仅影响「新授予/新收回」的可见时间，不会误放行超管） | 已枚举 6 类变更点；TTL 兜底；后续新增变更点需同步补失效（写入设计说明） |
| 击穿防护（同 Key 并发） | 缓存失效瞬间多线程并发回源，DB 瞬时压力 | 本波次不做，登记残余 → AUTH-19（`@DistributedLock`，Redis 本机已可用） |
| 应用内多实例失效不广播 | 多实例部署时其他节点缓存靠 TTL 过期 | 记录为残余，规模化后按分布式缓存/消息广播演进 |
| 端到端实测 | 本机 Redis 可用但服务起机与经网关验证未纳入本波 | 单测 + 覆盖率门禁为准；端到端待中间件联调窗口 |

---

## 六、执行顺序

```
1) 文档先行：本 spec + tasks + checklist
2) PermissionCacheProperties + PermissionCache（含 afterCommit 包装）
3) PermissionInterceptor 改为经 PermissionCache 读
4) 6 个变更点接入失效
5) 新增 PermissionCacheTest；改造 PermissionInterceptorTest 与 3 个 ServiceImplTest 构造
6) mvn -pl :whale-auth -am -Pcoverage-gate clean verify（全绿 + 覆盖率达标）
7) mvn checkstyle:check（0 违规）
8) 检查报告 + devlog + 台账销账
9) commit + push（Gitee origin/main）
```

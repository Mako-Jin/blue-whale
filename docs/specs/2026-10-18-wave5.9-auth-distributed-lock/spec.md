# Wave 5.9 · whale-auth `@DistributedLock` 场景接入规格（AUTH-19）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-DIST-002（分布式锁）、REQ-DIST-003（缓存一致性 → 防击穿）、REQ-FUNC-002（权限中心）、REQ-QA-002（代码检查留痕） |
| 任务映射 | AUTH-19 ↔ T-LOCK-4（场景接入）/ T5.2 工具的场景化落地 |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [distributed.md](../../design/distributed.md) §2.2–§2.5（RLock 规范 / 锁工具 / 典型场景）；[whale-auth-backlog.md](../../plans/whale-auth-backlog.md) AUTH-19；[specs/2026-10-18-wave5.8-auth-permission-cache/spec.md](../2026-10-18-wave5.8-auth-permission-cache/spec.md)（本波前置，防击穿登记为残余）；[devlog/2026-10-18.md](../../devlog/2026-10-18.md) §四 |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 按 [code-review-checklist.md](../../checklists/code-review-checklist.md) 自检留报告（REQ-QA-002）→ 构建验证 → 提交推送 |

---

## 一、需求项（本波次范围）

Wave 5.0 交付了 `@DistributedLock` 注解 + `DistributedLockAspect`（Redisson `RLock`，SpEL Key，`failFast` 语义），但**零业务场景接入、无并发实证**（backlog 风险 #9）。Wave 5.8 又把权限判定缓存接入后，明确登记「缓存**击穿防护**（同 Key 并发仅一次回源）」为 AUTH-19 的残余项。

本波次把 `@DistributedLock` 接入权限中心两个场景，并在真实 Redis 上做并发实证。

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-DIST-002 | ① 角色权限变更互斥（`lock:auth:role:{roleId}`）；② 缓存重建防击穿（`lock:cache:auth:user:perm:{userId}`） | 并发调用互斥；获取失败按注解语义处置；同 Key 并发仅一次回源 |
| REQ-DIST-003 | 击穿防护补齐 Wave 5.8 残余 | 缓存未命中并发场景下 DB 回源次数 = 1 |
| REQ-FUNC-002 | 权限判定语义不变 | 既有 120 用例不回归；放行/拒绝结论与直查一致 |
| REQ-QA-002 | 单测 + 实时 IT + 检查报告 | 报告逐项对应清单编号 |

---

## 二、现状与落点

| 项 | 现状 | 落点 |
|---|---|---|
| 锁工具 | [DistributedLock.java](../../../backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/lock/annotation/DistributedLock.java) + [DistributedLockAspect.java](../../../backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/lock/aop/DistributedLockAspect.java) + `DistributedLockAutoConfiguration`（`@ConditionalOnClass({RedissonClient, Aspect})`）；whale-auth 已引 Redisson → 切面已生效 | 直接复用（starter 不改锁语义） |
| 场景 ①：角色权限变更 | [SysRoleServiceImpl.java](../../../backend/whale-services/whale-auth/src/main/java/com/bluewhale/auth/service/impl/SysRoleServiceImpl.java) `#assignPermissions`：先校验 → 删关联 → 逐条 `insert` → `evictByRole`；并发下会出现「删/插 交错」 | 方法上加 `@DistributedLock`，Key `lock:auth:role:{roleId}` |
| 场景 ②：缓存防击穿 | [PermissionCache.java](../../../backend/whale-services/whale-auth/src/main/java/com/bluewhale/auth/security/PermissionCache.java) `#getPermissionCodes` 经 `CacheConsistencyTemplate#getOrLoad`：未命中即回源，**无互斥**（同 Key 并发 N 次回源） | 未命中后经锁 + **双重检查**再回源 |
| 缓存工具 | `CacheConsistencyTemplate`（starter）：`getOrLoad` / `evict` / `evictWithDelay`；`buildKey`/`readQuietly` 为 private | 新增**只读**公开方法 `getIfPresent(name, id)`（不回源、不写哨兵），供调用方自实现「先读 → 加锁 → 复查 → 回源」 |

**锁 Key 约定**（REQ-ARCH-004 / [distributed.md](../../design/distributed.md) §2.4）：

| 场景 | Key | 说明 |
|---|---|---|
| 角色权限变更 | `lock:auth:role:{roleId}` | 同角色并发分配权限串行化，避免关联表交错写 |
| 缓存重建防击穿 | `lock:cache:auth:user:perm:{userId}` | 与设计模板 `lock:cache:{cacheKey}` 对齐（`cacheKey` = 缓存 Key 去前缀） |

---

## 三、设计

### 3.1 组件与依赖

| 组件 | 包 | 职责 |
|---|---|---|
| `PermissionCacheMutex`（新增） | `com.bluewhale.auth.security` | 缓存重建互斥门面：唯一方法 `load(Long userId, Supplier<String> loader)` 带 `@DistributedLock` |

`PermissionCache` 新增依赖 `PermissionCacheMutex`（**独立 Bean**，非 `PermissionCache` 自身方法），使 `@DistributedLock` 切面经由**跨 Bean 调用**生效（规避同类自调用不走 AOP 的陷阱，[code-review-checklist.md](../../checklists/code-review-checklist.md) E11 / B12）。

### 3.2 场景 ②：缓存重建防击穿（双重检查 + 互斥回源）

```
getPermissionCodes(userId, loader)
  enabled == false                      → 直查 loader（不读不写缓存）
  getOrLoad(name, id, ttl, 回源函数)
      命中（含空串）                    → 直接返回，不取锁
      未命中 → 回源函数 = loadUnderLock(userId, loader)
                 PermissionCacheMutex#load(userId, () -> 复查缓存 → 命中则直接返回；未命中才 loader.get())
```

- `@DistributedLock` 参数：`prefix="lock"`、`biz="cache:auth:user:perm"`、`key="#userId"`、`waitTime=1`、`leaseTime=10`、`failFast=false`。
- 并发语义：线程 A 持锁回源并写缓存，线程 B 等待（≤1s）后取锁，**复查缓存命中 → 不回源** ⇒ 同 Key 并发回源次数 = 1。
- 降级：`waitTime` 超时未取锁（`failFast=false`）→ 告警后**放行**，仍走复查回源（安全，无死锁），符合 [distributed.md](../../design/distributed.md) §2.2「不允许静默死锁」。
- Redis 不可用：`tryLock` 抛异常 → `DistributedLockAspect#acquire` 抛 `BizException(LOCK_ACQUIRE_FAILED)`；`failFast=false` 仅对**获取返回 false** 生效，**异常路径**仍会抛出 → 因此 `PermissionCache#getPermissionCodes` 对回源函数整体包一层 `try/catch`，锁异常时降级为直查，保证缓存故障不阻断权限判定（与 Wave 5.8 降级链一致）。

> **降级链**：`enabled=false` → 直查；锁异常 → 直查；锁竞争超时 → 复查回源。任一路径均以 DB 为准，**不因缓存/锁故障误放行**。

### 3.3 场景 ①：角色权限变更互斥

- `@DistributedLock(prefix="lock", biz="auth:role", key="#id", waitTime=1, leaseTime=30, failFast=true)` 加在 `SysRoleServiceImpl#assignPermissions`。
- 切面 order `HIGHEST_PRECEDENCE + 100` **高于** `@Transactional`，锁覆盖整个事务，避免「锁已释放但事务未提交」（[distributed.md](../../design/distributed.md) §2.2）。
- Key `lock:auth:role:{roleId}`：最小资源粒度，不阻塞其他角色的权限分配。
- `failFast=true`：并发同一角色分配权限时后到者抛 `BizException(LOCK_ACQUIRE_FAILED)`，前端可重试；不放行「交错写」。

### 3.4 「注册幂等」场景说明（不重复加锁）

[distributed.md](../../design/distributed.md) §2.4 列有「用户注册幂等 `lock:auth:user:register:{phone}`」。该语义**已于 AUTH-18（Wave 5.6）由 `@Idempotent` 覆盖**——`IdempotentAspect` 用 Redisson `RBucket.trySet`（即 `SETNX`）做原子抢占，效果等价于一把一次性锁。**本波次不再叠加 `@DistributedLock`**，避免同一写路径两次 Redis 往返；此判断在 tasks/report 中留痕。

---

## 四、验收

| 项 | 验证方式 | 通过标准 |
|---|---|---|
| 只读 API | `CacheConsistencyTemplateTest`（新增用例） | `getIfPresent` 命中返回原始值、未命中/异常返回 `null`、不回源不写哨兵 |
| 防击穿读路径 | `PermissionCacheTest`（新增用例） | 命中不取锁；未命中经 `PermissionCacheMutex` 复查后**仅一次** loader 调用；`enabled=false` 不取锁；锁异常降级直查 |
| 互斥门面 | `PermissionCacheMutexTest` | 透传 loader 结果；异常原样抛出 |
| 注解接线 | `SysRoleServiceImplTest`（新增用例） | 反射断言 `assignPermissions` 携带 `@DistributedLock`，`biz="auth:role"`、`key="#id"`、`failFast=true` |
| 并发实证 | `LivePermissionLockIT`（`-Dwhale.live.it=true`，真实 Redis） | ① 同角色并发：持锁期间第二调用 `LOCK_ACQUIRE_FAILED`，Redis 中 `lock:auth:role:{id}` 确实被持有；② 缓存防击穿：N 线程并发未命中，DB 回源计数 = 1 |
| 判定语义不回归 | 全量单测（既有 120 用例） | 全绿 |
| 覆盖率门禁 | `mvn -f backend/pom.xml -pl :whale-auth -am -Pcoverage-gate clean verify` | BUILD SUCCESS（LINE ≥ 0.40、BRANCH ≥ 0.30） |
| 代码规范 | `mvn -f backend/pom.xml checkstyle:check` | 全 reactor 0 违规（含 E16 Javadoc 门禁） |

---

## 五、风险与残余项

| 风险 / 残余 | 影响 | 处置 |
|---|---|---|
| 锁等待时长与业务耗时 | `assignPermissions` `leaseTime=30`，权限条数极大时事务可能超时 → 锁提前释放 | 权限分配条数受前端分页约束；如后续支持全量批配需复核 `leaseTime` |
| 防击穿仅单实例内有效 | 多实例部署时各节点用同一 Redis 锁 → **跨实例仍互斥**（Redisson 分布式锁天然跨实例），无残余 | —— |
| `failFast=false` 降级回源 | 锁竞争超时后仍回源，DB 瞬时压力略增（无死锁） | 已用「复查缓存」降低概率；记入说明 |
| 用户注册锁未叠加 | 由 `@Idempotent` 等价覆盖（见 §3.4） | 在 spec/report 留痕，不重复实现 |
| 端到端实测 | 本机 Redis 可用，实时 IT 可跑；经网关全链路并发未纳入 | 实时 IT 为准；经网关并发待联调窗口 |

---

## 六、执行顺序

```
1) 文档先行：本 spec + tasks + checklist
2) CacheConsistencyTemplate 新增 getIfPresent（只读）
3) 新增 PermissionCacheMutex（@DistributedLock 门面）
4) PermissionCache 读路径接入「复查 + 互斥回源」
5) SysRoleServiceImpl#assignPermissions 加 @DistributedLock
6) 单测：CacheConsistencyTemplateTest / PermissionCacheMutexTest / PermissionCacheTest / SysRoleServiceImplTest
7) 实时 IT：LivePermissionLockIT（真实 Redis 并发实证，-Dwhale.live.it=true）
8) mvn -pl :whale-auth -am -Pcoverage-gate clean verify（全绿 + 覆盖率达标）
9) mvn checkstyle:check（0 违规）
10) 检查报告 + devlog + 台账销账
11) commit + push（Gitee origin/main）
```

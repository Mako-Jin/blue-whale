# Tasks：Wave 5.9 whale-auth `@DistributedLock` 场景接入（AUTH-19）

> 关联 spec：[spec.md](./spec.md) ｜ 关联需求：REQ-DIST-002、REQ-DIST-003、REQ-FUNC-002、REQ-QA-002
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [ ] S1 | 文档先行：spec/tasks/checklist 落地 | 无 | 高 | 文档一致可追溯 | ✅ |
| [ ] S2 | `CacheConsistencyTemplate` 新增只读方法 `getIfPresent(name, id)`（不回源、不写哨兵、异常降级 null）+ 单测 | S1 | 高 | 新增用例全绿；既有 `getOrLoad` 行为不变 | ✅ |
| [ ] S3 | 新增 `PermissionCacheMutex`（`@DistributedLock(prefix="lock", biz="cache:auth:user:perm", key="#userId", waitTime=1, leaseTime=10, failFast=false)` 的透传门面） | S2 | 高 | 独立 Bean（跨 Bean 调用使切面生效）；单测透传 + 异常上抛 | ✅ |
| [ ] S4 | `PermissionCache#getPermissionCodes` 接入「复查 + 互斥回源」防击穿；锁异常降级直查 | S2、S3 | 高 | 命中不取锁；未命中并发仅一次 loader 调用；锁异常不阻断 | ✅ |
| [ ] S5 | `SysRoleServiceImpl#assignPermissions` 加 `@DistributedLock(prefix="lock", biz="auth:role", key="#id", waitTime=1, leaseTime=30, failFast=true)` | S1 | 高 | Key 为 `lock:auth:role:{roleId}`；锁覆盖事务（切面 order 高于 `@Transactional`） | ✅ |
| [ ] S6 | 单测：`PermissionCacheTest` 扩充 + `PermissionCacheMutexTest` 新增 + `SysRoleServiceImplTest` 注解接线断言 | S2–S5 | 高 | 新增用例全绿；既有 `PermissionCacheTest` 用例同步构造 | ✅ |
| [ ] S7 | 实时 IT：`LivePermissionLockIT`（真实 Redis @6379，`-Dwhale.live.it=true`）① 同角色并发互斥 ② 缓存防击穿回源计数=1 | S3–S5 | 高 | 实测通过（Redis 本机已可用） | ✅ |
| [ ] S8 | 构建验证：`mvn -f whale-backend/pom.xml -pl :whale-auth -am -Pcoverage-gate clean verify` | S6、S7 | 高 | 全绿 + LINE ≥ 0.40、BRANCH ≥ 0.30 | ✅ |
| [ ] S9 | 规范门禁：`mvn -f whale-backend/pom.xml checkstyle:check` | S8 | 高 | 全 reactor 0 违规 | ✅ |
| [ ] S10 | 留痕收口：检查报告 `docs/reviews/2026-10-18-wave5.9-auth-distributed-lock.md` + devlog §29 + §三新增问题项 | S8、S9 | 中 | 四类信息齐备 | ✅ |
| [ ] S11 | 台账销账：`whale-auth-backlog.md` AUTH-19（+ 风险 #9）、`next-steps.md` | S10 | 中 | 删除线 + ✅ | ✅ |
| [ ] S12 | 自动 commit + push（Gitee origin/main） | S11 | 中 | 工作区干净且 `main == origin/main` | ✅ |

## 实施注意（给实现者的事实约束）

- **切面生效条件**：`@DistributedLock` 由 AOP 代理生效，**同类自调用不走切面**。故防击穿必须经独立 Bean `PermissionCacheMutex` 调用，不可写成 `PermissionCache` 自身方法。
- `DistributedLockAutoConfiguration` 以 `@ConditionalOnClass({RedissonClient.class, Aspect.class})` 生效；whale-auth 已引 `redisson-spring-boot-starter`（4.8.0）→ 切面已装配。
- `@Transactional` 默认 `Ordered.LOWEST_PRECEDENCE`，`DistributedLockAspect` 为 `HIGHEST_PRECEDENCE + 100` → 锁在事务外层。
- 锁获取失败有两种形态，处置不同：① `tryLock` 返回 `false` → 按 `failFast` 决定抛 `BizException(LOCK_ACQUIRE_FAILED)` 或放行；② `tryLock` 抛异常（Redis 不可用）→ 直接上抛。防击穿路径取 `failFast=false` 且**对异常做降级直查**。
- `CacheConsistencyTemplate#getIfPresent` 仅只读，**不得**改动 `getOrLoad` 的回源/哨兵/抖动语义（Wave 5.6 既有 8 用例须保持全绿）。
- 权限缓存对「空集合」写**空串**（非 `null` 哨兵），故 `getIfPresent` 返回 `null` 即「未命中」，返回 `""` 即「已知无权限」。
- 测试构造：`PermissionCache` 新增 `PermissionCacheMutex` 依赖后，`PermissionCacheTest` 的 `new` 语句必须同步补参数（Mockito mock 门面，`when(mutex.load(any(), any())).thenAnswer(...)` 透传）。
- 实时 IT 参照 `whale-spring-boot-starter` 的 `LiveDistributedLockIT`（`AspectJProxyFactory` + `Redisson.create`）与 `LiveTokenBlacklistIT`（`@EnabledIfSystemProperty(named="whale.live.it", matches="true")`）。
- 锁 Key 常量与 REQ-ARCH-004 对齐：`lock:auth:role:{roleId}`、`lock:cache:auth:user:perm:{userId}`。

## 待做项（本次明确不做，登记备查）
- 用户注册场景的 `@DistributedLock`：语义已由 AUTH-18 的 `@Idempotent`（`RBucket.trySet` ＝ SETNX）等价覆盖，**不重复加锁**（见 spec §3.4）。
- 定时任务防重复执行（`lock:job:{jobCode}`，依赖 whale-job）、库存扣减（`lock:stock:{skuId}`，依赖业务域）：本波仅覆盖权限中心域。
- T-LOCK-5「Redis 不可用降级策略开关」：现有降级为「锁异常 → 直查」，如需配置化开关另立任务。
- 多实例缓存失效广播 / Canal 订阅 binlog（规模化后演进）。
- 经网关全链路并发实测、控制器层单测。

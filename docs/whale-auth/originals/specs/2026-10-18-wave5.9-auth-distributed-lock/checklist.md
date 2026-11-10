# Checklist：Wave 5.9 whale-auth `@DistributedLock` 场景接入（AUTH-19）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含两个场景、Key 约定、降级链、残余项
- [x] 1.2 锁 Key 与 REQ-ARCH-004（`whale:<域>:<用途>:<标识>`）/ distributed.md §2.4 一致：`lock:auth:role:{roleId}`、`lock:cache:auth:user:perm:{userId}`

## 二、代码

- [x] 2.1 `CacheConsistencyTemplate#getIfPresent` 为**只读**方法（不回源、不写哨兵、异常降级 `null`），`getOrLoad` 语义未变
- [x] 2.2 `PermissionCacheMutex` 为**独立 Bean**，注解参数为 `biz="cache:auth:user:perm"`/`key="#userId"`/`waitTime=1`/`leaseTime=10`/`failFast=false`
- [x] 2.3 `PermissionCache` 读路径：命中不取锁；未命中经门面**复查缓存**后回源 ⇒ 同 Key 并发仅一次回源
- [x] 2.4 锁异常（Redis 不可用）时 `PermissionCache` 降级直查，**不阻断**权限判定，且不误放行
- [x] 2.5 `SysRoleServiceImpl#assignPermissions` 带 `@DistributedLock(prefix="lock", biz="auth:role", key="#id", waitTime=1, leaseTime=30, failFast=true)`，Key = `lock:auth:role:{roleId}`
- [x] 2.6 锁覆盖事务（切面 order `HIGHEST_PRECEDENCE + 100` > `@Transactional`），无「锁已释放事务未提交」窗口
- [x] 2.7 无同类自调用走切面、无死锁（`tryLock` + 超时 + `finally` 条件解锁）
- [x] 2.8 全部生产代码含中文 Javadoc（类型 + 方法，E16 门禁），无魔法值（Key/前缀/TTL 提为常量或注解属性）
- [x] 2.9 「注册幂等」不重复加锁的理由已在 spec §3.4 与 tasks 留痕

## 三、测试与门禁

- [x] 3.1 `CacheConsistencyTemplateTest` 新增 `getIfPresent` 用例（命中 / 未命中 / 异常），既有 8 用例不回归
- [x] 3.2 `PermissionCacheMutexTest`：透传 loader 结果、异常上抛
- [x] 3.3 `PermissionCacheTest`：命中不取锁 / 未命中复查后仅一次 loader / `enabled=false` 不取锁 / 锁异常降级直查
- [x] 3.4 `SysRoleServiceImplTest`：反射断言 `assignPermissions` 的 `@DistributedLock` 接线（`biz`/`key`/`failFast`）
- [x] 3.5 `LivePermissionLockIT`（真实 Redis）：同角色并发互斥 + 缓存防击穿回源计数=1，实测通过
- [x] 3.6 `mvn -f whale-backend/pom.xml -pl :whale-auth -am -Pcoverage-gate clean verify`：全绿 + LINE ≥ 0.40、BRANCH ≥ 0.30
- [x] 3.7 `mvn -f whale-backend/pom.xml checkstyle:check`：全 reactor 0 违规

## 四、留痕与销账

- [x] 4.1 检查报告 `docs/reviews/2026-10-18-wave5.9-auth-distributed-lock.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号，含未通过项与修复记录
- [x] 4.2 `docs/devlog/2026-10-18.md` 追加本波次条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 4.3 台账销账：`whale-auth-backlog.md` AUTH-19（含风险 #9）、`next-steps.md` 对应条目为「删除线 + ✅」
- [x] 4.4 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 五、残余项登记

- [x] 5.1 注册锁（`@Idempotent` 已等价覆盖）、定时任务/库存锁、T-LOCK-5 降级开关、多实例失效广播、经网关并发实测已登记

## 六、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：**通过**
- 依据：
  - 构建/覆盖率：`mvn -f whale-backend/pom.xml -pl :whale-auth -am -Pcoverage-gate clean verify` → **BUILD SUCCESS**；`whale-auth` **126 run / 0 fail / 0 error / 0 skipped**；`All coverage checks have been met`；JaCoCo **行 563/779 = 0.7227、分支 209/288 = 0.7257**（阈值 LINE ≥ 0.40、BRANCH ≥ 0.30）。
  - 规范门禁：`mvn -f whale-backend/pom.xml checkstyle:check` → **28 模块 0 违规 / BUILD SUCCESS**（含 E16 Javadoc 门禁）。
  - 实时 IT：`mvn -f whale-backend/pom.xml -pl :whale-auth -am test "-Dwhale.live.it=true" "-Dtest=LivePermissionLockIT" "-Dsurefire.failIfNoSpecifiedTests=false"` → **2/0/0**（真实 Redis @127.0.0.1:6379，`redis-cli ping` = PONG）：① 同角色并发第二调用抛 `BizException(LOCK_ACQUIRE_FAILED)` 且 `lock:auth:role:71` 被持有；② 8 线程并发未命中 DB 回源计数 == 1。
  - 检查报告：[reviews/2026-10-18-wave5.9-auth-distributed-lock.md](../../reviews/2026-10-18-wave5.9-auth-distributed-lock.md)（逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号，含未通过项与修复记录）。
- **规格偏离（如实记录）**：场景②「写缓存」时机对 spec §3.2 参考实现**有意偏离**——由「解锁后写」改为「锁内完成回源 + 写缓存」，以消除「A 解锁 → B 取锁复查（缓存尚未写）」竞态，满足「同 Key 并发仅一次回源」验收（spec 参考实现实测回源 2 次）。语义未变（值/哨兵/TTL 抖动仍由 starter `getOrLoad` 承担）。详见报告「规格偏离」与 devlog §三 #64。
- 残余项见报告「待做项 / 遗留」。

# P1 #3 检查报告：Auth 权限缓存跨实例失效

- 日期：2026-10-27
- 范围：`whale-backend/whale-services/whale-service-auth`（多模块 DDD）
- 关联：台账序号 24 残余、REQ-DIST-003、AUTH-20、Wave 5.8/5.9 权限缓存与防击穿

## 1. 问题

权限码缓存为共享 Redis（Cache-Aside），`PermissionCache` 变更后只删除当前租户 Key：

1. 无跨实例失效广播，多个 auth 实例之间缺少显式失效信号，无法观测与兜底；
2. Redis 断连时 `CacheConsistencyTemplate.evict` 静默失败，旧值残留至 TTL（默认 5 分钟），只能依赖下一次权限变更顺带清理；
3. 失效动作隐式依赖 `TenantContext`，异步重试线程无租户上下文会落到错误租户段。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `whale-service-auth-infrastructure/.../infrastructure/cache/AuthPermissionCacheInvalidator.java` | 新增。按当前租户计算完整 Key，`tryEvictKeys` 批量删除；Redisson RTopic `auth:permission-cache:invalidate` 广播 `v1\|KEYS\|instanceId\|key,...`，过滤回声与非法 Key；删除失败登记完整 Key；`@PreDestroy` 退订 |
| `whale-service-auth-infrastructure/.../infrastructure/cache/AuthPermissionCacheReconcileJob.java` | 新增。`@Scheduled` 默认 30s 重试待处理 Key，异常仅记日志 |
| `whale-service-auth-infrastructure/.../security/PermissionCache.java` | 失效路径统一委托失效器；`evictUser(null)` 直接返回 |
| `whale-service-auth-infrastructure/.../config/PermissionCacheProperties.java` | 新增 `broadcastEnabled/reconcileEnabled/reconcileIntervalMs/reconcileInitialDelayMs/reconcileBatchSize/pendingLimit` |
| `whale-common/.../cache/CacheConsistencyTemplate.java` | 新增 `tryEvictKey(String)`、`tryEvictKeys(Collection)`，返回删除是否成功；`tryEvict(name,id)` 保留 |
| `whale-service-auth-starter/.../WhaleAuthApplication.java` | 新增 `@EnableScheduling` |
| `whale-service-auth-starter/src/main/resources/application.yml` | 新增 `whale.auth.permission-cache.*`，全部支持环境变量覆盖 |
| 测试 | `AuthPermissionCacheInvalidatorTest`（7 用例）、`AuthPermissionCacheReconcileJobTest`（4 用例）；`PermissionCacheTest`、`LivePermissionLockIT` 适配新构造参数 |

## 3. 行为

1. 事务提交后 → `PermissionCache.evictUsers` → 失效器按 `whale:{tenantId}:cache:auth:user:perm:{userId}` 计算 Key；
2. 批量 `DEL`；成功即完成（共享 Redis，网关与其他实例读取立即失效）；
3. 失败 → Key 进入本实例待处理集合（超 `pendingLimit` 丢弃并告警）；
4. 广播 Key 给其他实例，其他实例同样执行批量删除（回声过滤）；
5. 重对齐任务每 30s 重试待处理 Key，Redis 恢复后自动清理，不依赖租户上下文与下一次变更；
6. Redisson 不可用 → 跳过广播，删除与重对齐仍然工作。

## 4. 验证

```powershell
mvn -pl whale-backend/whale-services/whale-service-auth/whale-service-auth-starter -am test -q
```

- 结果：`346` 用例，`0` 失败（新增 11 个）；Checkstyle 通过（`-am` 覆盖 common/auth 各模块）。
- 新增用例覆盖：跨用户去重与完整 Key 集合、删除失败登记与恢复后清空、远端广播触发删除、回声忽略、广播关闭、非法消息、待处理上限、停机退订、任务开关与异常隔离。

## 5. 边界

- ~~真实 Redis 双实例广播与断连恢复的实机联调未执行（需 2 个 auth 实例 + 可控断网）~~；
  → 2026-10-27 已在双实例 + 停机/重启场景实机取证，见
  [T4 检查报告](2026-10-27-p1-auth-permission-cache-real-machine.md)；
- 待处理集合为进程内状态，进程重启会丢失；最坏回退为 TTL（默认 5 分钟）；
- 仅清理当前租户的 Key（失效事件发生在该租户请求内）；跨租户批量清理不在本项范围；
  → 2026-10-27 后续已补齐权限点变更的跨租户失效与角色启停失效，见
  [权限缓存失效残余检查报告](2026-10-27-p1-5-auth-permission-cache-residual.md)；
- `CacheConsistencyTemplate.tryEvictKeys` 为单次批量 `DEL`，Redis 连接级失败时整批保留重试。

## 6. 附带复核：`whale-auth` → `whale-service-auth`

- 5 子模块齐全，`whale-backend/whale-services/pom.xml` 仅含 `whale-service-auth`；
- 网关两条路由均 `lb://whale-service-auth`；
- CI/脚本/prometheus/Dockerfile 旧名残留计数为 0；
- 旧目录 `whale-backend/whale-services/whale-auth` 已无跟踪文件，本机构建产物已删除；
- 迁移判定：已完成，可移除旧模块（见 [拆分 checklist](../specs/2026-10-26-whale-service-auth-split/checklist.md) 复核章节）。

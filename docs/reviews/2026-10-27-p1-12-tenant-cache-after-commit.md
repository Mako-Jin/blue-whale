# P1 #12 检查报告：租户状态缓存写入延迟到事务提交后

- 日期：2026-10-27
- 范围：`whale-service-auth` 租户开通/启停/续期缓存写入
- 关联：P1 #10 租户到期网关生效（REQ-SEC-013）、P1 #7 权限缓存提交后失效、REQ-SEC-015

## 1. 问题

`TenantAppService#changeStatus`、`#renew` 与 `TenantProvisioningAppService#provision` 均标注 `@Transactional`，但 `TenantStatusCache#sync` 在事务**内部**直接写 Redis。网关以缓存为唯一依据判定租户停用/到期，因此：

- 停用租户后事务回滚（如后续审计/事件处理失败）：数据库仍为启用，缓存已是 `0`，该租户**全部用户立即被网关 401**，直到下次启停、续期或应用重启预热才恢复；
- 开通租户后事务回滚：缓存留下不存在的租户状态，若事务内后续步骤失败，可能造成脏状态；
- 续期同理：回滚后缓存中的新到期时间会让已到期租户被放行。

数据库是权威来源，缓存不应领先于提交结果。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `auth-application/.../tenant/TenantStatusCache.java` | `sync` 在调用时先冻结「租户 ID / 状态值 / 到期 Unix 秒」，若 `TransactionSynchronizationManager.isSynchronizationActive()` 为真则注册 `afterCommit` 回调，提交后写入；无事务上下文（启动预热、事务外调用）立即写入 |
| 同上 | 抽出 `toExpireAtSeconds` 与 `write`：`write` 统一处理状态键写入、到期键写入或删除（长期有效），Redis 异常仍只告警不外抛 |
| `TenantStatusCacheTest.java` | 新增「事务内延迟到提交后写入，提交前不触碰缓存」用例：`initSynchronization` 后断言无写入，手动触发 `afterCommit` 后断言状态键写入与到期键删除；`@AfterEach` 清理同步上下文 |

调用方（`TenantAppService#changeStatus`/`#renew`、`TenantProvisioningAppService#provision`）无需改动，语义自动变为「提交后写缓存」。冻结值而非延迟读取聚合，避免提交后回调读到被后续代码修改的对象。

## 3. 行为矩阵

| 场景 | 事务结果 | 缓存 | 网关 |
|---|---|---|---|
| 停用租户 | 提交 | 提交后写 `0` | 该租户 401 |
| 停用租户 | 回滚 | 不写，保持原值 | 维持原判定，不误停 |
| 续期租户 | 提交 | 提交后写新到期秒数 | 按新到期时间放行 |
| 续期租户 | 回滚 | 不写 | 维持旧到期时间 |
| 开通租户 | 提交 | 提交后写 `1` + 到期键 | 可正常访问 |
| 开通租户 | 回滚 | 不写 | 不产生幽灵租户状态 |
| 启动预热 | 无事务 | 立即写入 | 与数据库一致 |
| 事务外调用 | 无事务 | 立即写入 | 与既有行为一致 |
| 提交后 Redis 不可用 | 提交 | 写入失败仅告警 | 网关按可用降级放行并告警 |

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-service-auth/whale-service-auth-starter -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- auth starter `403` 用例，`0` 失败、`0` 错误，`7` 跳过（Live IT），BUILD SUCCESS；
- Checkstyle `0` 违规；
- `TenantStatusCacheTest` 6 用例全绿：原有 5 例（状态+到期、停用+删除到期键、Redis 异常、模板缺失、非法租户）验证事务外路径不变；新增 1 例验证事务内延迟写入；
- `Sm4CryptoTest` 为本机 JDK/JCE 既有失败，已隔离，与本次无关。

## 5. 边界

- **提交后写入失败**：Redis 不可用时无法回滚数据库，缓存保持旧值并输出告警；网关按可用降级放行，与既有降级口径一致；
- **同一事务多次 sync**：每次注册独立回调，按调用顺序在提交后依次写入，最终值与最后冻结的租户状态一致；
- **无事务的调用方**：`ApplicationRunner` 预热与事务外调用仍即时写入，行为不变；
- **分布式锁与提交顺序**：`provision` 的 `@DistributedLock` 释放与事务提交顺序由切面决定；缓存写入在提交后执行，不再依赖锁内时序；
- **回滚不清理旧缓存**：回滚时缓存维持原值，若原值本身已过期（如长时间未同步），仍需靠启动预热或下一次成功变更对齐。

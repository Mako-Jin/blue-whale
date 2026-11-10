# P2 ImBotService#openDefault 并发首建失败修复（2026-10-26）

> 来源：[2026-10-25 Wave 5.55 部署验证](2026-10-25-wave5.55-deployment-verification.md) 第四节 P2-1 / B16
> 现象：并发首建机器人会话偶发 `Duplicate entry '1-B:2' for key 'im_conversation.uk_tenant_pair_key'`，接收人通知丢失 1 条。

## 一、根因

`openDefault` 处于 `@Transactional`（MySQL 默认 **REPEATABLE READ**）中：

1. 线程 B 首次 `findExisting` 建立一致性读快照（返回 null）；
2. 线程 A 抢先插入并提交；
3. 线程 B `insert` 撞 `uk_tenant_pair_key` 唯一索引，进入 `catch (DuplicateKeyException)`；
4. **同一事务内**再次 `findExisting` 仍读旧快照，看不到 A 已提交的行，返回 null；
5. 原异常被重新抛出 → 该接收人会话初始化失败、通知丢失。

即：catch 块里的"回查兜底"在 REPEATABLE READ 快照下形同虚设。

## 二、修复方案

恢复读必须跳出外层事务快照。采用 `REQUIRES_NEW + READ_COMMITTED` 独立事务包裹整个恢复读（回查 + 补齐成员 + 补齐绑定）：

- `REQUIRES_NEW`：挂起外层事务，新连接/新快照；
- `READ_COMMITTED`：每次语句级快照，可见并发事务已提交的行；
- 恢复读整体入新事务（而非仅 `findExisting`）：防 `ensureMember`/`ensureBinding` 二次撞唯一索引时再次落入快照陷阱；
- 恢复读仍为 null（极端时序：A 尚未提交）→ 原样 rethrow `DuplicateKeyException`，不吞错。

### 变更点

| 文件 | 变更 |
|---|---|
| [ImBotService.java](../../whale-backend/whale-services/whale-im/src/main/java/com/bluewhale/im/service/ImBotService.java) | 构造器新增 `PlatformTransactionManager`；装配 `recoveryReadTemplate`（`PROPAGATION_REQUIRES_NEW` + `ISOLATION_READ_COMMITTED`）；`openDefault` catch 块改为 `recoveryReadTemplate.execute(...)` 包裹 findExisting + ensureMember + ensureBinding |
| [ImBotServiceTest.java](../../whale-backend/whale-services/whale-im/src/test/java/com/bluewhale/im/service/ImBotServiceTest.java) | 构造注入 txManager mock（`getTransaction` 放行）；既有并发恢复用例补 `ArgumentCaptor<TransactionDefinition>` 断言事务属性；**新增双线程并发首建用例** |

未选方案及理由：

- 整个 `openDefault` 改 `READ_COMMITTED`：影响面大（正常路径全部语句降级），只治 catch 块即可；
- `REQUIRES_NEW` 但不降隔离：新事务仍继承不了「读已提交」语义外的保证，RC 足够且开销最小；
- Redis 分布式锁：引入新依赖与超时语义，唯一索引 + 恢复读已能闭环。

## 三、验证

### 单测（`mvn -o -pl whale-backend/whale-services/whale-im -am package`，JAVA_HOME=java21）

- **BUILD SUCCESS**，whale-im **139 用例全绿**，Checkstyle 0 违规；
- `ImBotServiceTest` 12/12（原 11 + 新增 1）：
  - `shouldRecoverFromDuplicateKeyOnConcurrentCreate`：恢复读命中已落库会话，且断言 `txManager.getTransaction` 捕获的事务定义为 `REQUIRES_NEW + READ_COMMITTED`；
  - `shouldSucceedForBothThreadsOnConcurrentFirstCreate`（新增）：`AtomicBoolean` 模拟「先 insert 成功者提交、后至者撞唯一索引」，双线程并发 `openDefault` 均返回同一会话 VO（id=100）、无异常；insert answer 模拟 MyBatis-Plus 主键回填。

### 行为对照

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 并发首建，败者恢复读 | 同事务快照重读 → null → rethrow，通知丢失 | 新事务 RC 重读 → 命中先落库会话 → 补齐成员/绑定 → 正常返回 |
| 败者恢复读仍为 null（胜者未提交） | rethrow | rethrow（行为不变） |
| 正常路径（无并发冲突） | 不变 | 不变（不进入恢复分支） |

## 四、残留与后续

- 真机双进程并发复现属时序偶发，单测已以确定性并发模型覆盖；后续 IM 压测时观察 `im_conversation` 插入告警即可。
- P2 队列下一项：PM 审批开启态联调（需 whale-workflow + 流程定义 ID）。

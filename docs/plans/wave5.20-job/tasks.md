# Wave 5.20 — whale-job「继续完成」任务分解（tasks）

- 对应 spec：`docs/plans/wave5.20-job/spec.md`
- 执行顺序：T1 → T2 → T3 → T4 → T5 → T6 → T7（T4 可与 T1~T3 并行；T5 依赖 T1~T4 完成）
- 约定：main 源码每个类型/方法（含 private/构造器）必须写标准 Javadoc（checkstyle error 级）；测试源码免 Javadoc
- **状态说明（2026-10-21 收尾审计）**：本文件为任务分解原稿，全部任务已随 Wave 5.20 落地，勾选已回填；权威结论以 [roadmap](../roadmap.md) §2.5 与 [devlog 2026-10-19](../devlog/2026-10-19.md) 为准。

## T1 调度健壮性·基础设施（R1）

- [x] T1.1 新增 `config/JobProperties.java`（`@ConfigurationProperties("whale.job")`，`logRetentionDays` 默认 30）
- [x] T1.2 新增 `scheduler/JobExecutionGuard.java`（`@Component`；`tryEnter`/`exit`/`markPendingOnce`/`consumePendingOnce`/`isRunning`）
- [x] T1.3 新增 `scheduler/JobHandlerRegistry.java`（`@Component`；`resolve`/`exists`，抛 `BizException`）

验收：三个类可独立编译；`mvn -pl ...whale-job checkstyle:check` 无 error。

## T2 调度健壮性·JobScheduler 重构（R1）

- [x] T2.1 注入 `JobHandlerRegistry`、`JobExecutionGuard`、`ObjectProvider<RedissonClient>`
- [x] T2.2 `execute(JobInfo)` 改为包可见：`guard.tryEnter` 失败 → `handleOverlap`；成功 → `runWithLock` + `finally exit` → `drainPendingOnce`
- [x] T2.3 新增 `acquireLock(Long)` / `releaseLock(RLock)` / `handleOverlap(JobInfo)` / `drainPendingOnce(JobInfo)`
- [x] T2.4 原执行体重命名为包可见 `doExecute(JobInfo)`（落日志 + 调 handler + 回写）
- [x] T2.5 `runOnce(Long)` 保留同步存在性校验后改 `taskScheduler.execute(() -> execute(job))` 异步
- [x] T2.6 删除 `resolveHandler`，改用 `JobHandlerRegistry.resolve`

验收：包可见方法可供同包测试调用；锁键 `lock:job:execute:{jobId}` 生效；无 Redisson 时降级不抛。

## T3 可观测与校验（R2 + R1 校验）

- [x] T3.1 `JobInfoVO` 增 `nextFireTime`（`LocalDateTime`）
- [x] T3.2 `JobInfoServiceImpl` 注入 `JobHandlerRegistry`，`create`/`update` 增 `validateHandler`（不存在抛 `PARAM_ERROR`）
- [x] T3.3 `JobInfoServiceImpl.toVo` 计算 `nextFireTime`（合法 cron → `CronExpression.parse(cron).next(now)`，否则 null）

验收：创建任务返回含 `nextFireTime`；handlerBean 不存在时返回 `code=400`。

## T4 归档清理 + 依赖配置（R3 + R1）

- [x] T4.1 新增 `scheduler/handler/JobLogCleanupHandler.java`（Bean 名 `jobLogCleanupHandler`，逻辑删除过期 `job_log`）
- [x] T4.2 `WhaleJobApplication` 增 `@ConfigurationPropertiesScan`
- [x] T4.3 `pom.xml` 增 `spring-boot-starter-data-redis`、`redisson-spring-boot-starter`
- [x] T4.4 `application.yml` 增 `spring.data.redis.host/port`、`whale.job.log-retention-days`
- [x] T4.5 `db/init.sql` 增清理任务种子行（id=1，cron `0 0 3 * * ?`，status=1）

验收：yml 无硬编码密码；种子行字段与 DDL 列一致。

## T5 单测基线（R4）

- [x] T5.1 `JobExecutionGuardTest`：进入/退出、重复进入失败、补跑标记消费（约 6 用例）
- [x] T5.2 `JobHandlerRegistryTest`：resolve 正常/空名/缺 Bean/类型不符；exists（约 6 用例）
- [x] T5.3 `JobSchedulerTest`：`doExecute` 成功/失败落库；`runOnce` NOT_FOUND；`execute` 重叠按策略补跑（约 8 用例）
- [x] T5.4 `JobInfoServiceImplTest`：create/update/delete/changeStatus/page/getDetail/runOnce + cron 非法 + handler 不存在（约 12 用例）
- [x] T5.5 `JobLogCleanupHandlerTest`：按保留期调用 delete 并返回摘要（约 3 用例）
- [x] T5.6 `DemoJobHandlerTest`：回显 param 与时间戳（约 2 用例）

验收：`mvn -pl ...whale-job test` 全绿，累计约 35+ 用例。

## T6 验证

- [x] T6.1 `mvn -pl backend/whale-services/whale-job test`（禁沙箱，JDK21）
- [x] T6.2 `mvn clean install -T 1C` 全量构建 SUCCESS
- [x] T6.3 8082 启动冒烟：种子任务注册、`/job/info/page` 含 `nextFireTime`、分布式锁键生成

## T7 文档与提交

- [x] T7.1 `docs/plans/task-breakdown.md` 追加 Wave 5.20
- [x] T7.2 `docs/plans/roadmap.md` 勾选 §2.5 whale-job 增强项
- [x] T7.3 `docs/devlog/2026-10-19.md` 追加 Wave 5.20 记录（范围/变更点/验证结果）
- [x] T7.4 `docs/reviews/2026-10-19-wave5.20-job-completion.md` 审查报告
- [x] T7.5 提交并 `git push origin HEAD`（Gitee）

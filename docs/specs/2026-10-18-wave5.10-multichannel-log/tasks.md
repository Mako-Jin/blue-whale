# Tasks：Wave 5.10 审计日志多通道完善（后端 Wave A，REQ-SEC-006）

> 关联 spec：[spec.md](./spec.md) ｜ 关联需求：REQ-SEC-006、REQ-SEC-001、REQ-SEC-002/003、REQ-FUNC-002、REQ-QA-002
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [ ] A1 | 文档先行：spec/tasks/checklist + `register.md` REQ-SEC-006 + `naming-conventions.md` 4 表与 SDK 包名登记 | 无 | 高 | 文档一致可追溯；4 表与 `@bluewhale/whale-log-sdk` 已登记 | ✅ |
| [ ] A2 | 4 表 DDL（`whale-audit/db/init.sql` + `whale-auth/db/init.sql`）+ starter 实体/Mapper | A1 | 高 | 字段/索引与 spec §3.1 一致；append-only 无 `update_time`/`deleted` | ⏳ |
| [ ] A3 | `LogPersistSupport` + `whaleLogExecutor`（core=2/max=4/queue=2048/`CallerRunsPolicy`/前缀 `whale-log-`）+ `LogSink`/`LocalLogSink` + `LogSinkProperties`/`ApiLogProperties` | A2 | 高 | 事务活跃 `afterCommit`、否则投递线程池；异常仅 warn；`sink=mq` 回落 local | ⏳ |
| [ ] A4 | `ApiLogFilter` + 自研 `LimitedCaching{Request,Response}Wrapper`（后端接口日志） | A3 | 高 | finally `copyBodyToResponse()`；Content-Type 白名单；`shouldNotFilterAsyncDispatch()=true`；`exclude-paths` 含上报接口防回环 | ⏳ |
| [ ] A5 | `OutboundLogInterceptor` + `OutboundLogRestTemplateCustomizer` + `SensitiveDataMasker#maskJson`（三方调用日志 + AUTH-11 脱敏配置化） | A3 | 高 | 经 `RestTemplateBuilder` 自动挂载；traceId 透传；超时记 `biz_status=0` 并原样抛出 | ⏳ |
| [ ] A6 | whale-audit `LogIngestController` 上报/查询接口 + `dto`/`vo`/`service`（含 AUTH-06 权限点种子） | A2、A3 | 高 | `items` 上限 50；上报 `permitAll` + `X-User-Id` 回填；查询 `@RequirePermission("sys:audit:log:list")` | ⏳ |
| [ ] A7 | `WhaleLogAutoConfiguration` 注册 + `AutoConfiguration.imports` 追加 + 网关白名单/限流配置 | A4、A5、A6 | 高 | `ObjectProvider` 延迟解析（**禁止** `@ConditionalOnBean(Mapper)`）；网关上报路径放行 | ⏳ |
| [ ] A8 | 单测：`ApiLogFilterTest` / `LimitedCachingWrapperTest` / `OutboundLogInterceptorTest` / `SensitiveDataMaskerTest(maskJson)` / `LogIngestControllerTest` / 两个 `*ServiceImplTest` | A2–A7 | 高 | 新增用例全绿；既有审计/脱敏用例不回归 | ⏳ |
| [ ] A9 | 实时 IT：`LiveApiLogIT` / `LiveFrontendLogIT`（`-Dwhale.live.it=true`，MySQL 3306） | A8 | 高 | `api_log` ≥1 行且无明文凭据；`frontend_log`/`behavior_log` 查询可见 | ⏳ |
| [ ] A10 | 构建验证：`mvn -f backend/pom.xml clean verify -T 1C` + `mvn -f backend/pom.xml checkstyle:check` | A8、A9 | 高 | BUILD SUCCESS；全 reactor 0 违规 | ⏳ |
| [ ] A11 | 留痕收口：检查报告 `docs/reviews/2026-10-18-wave5.10-multichannel-log.md` + devlog §30 + §三新增问题项 | A10 | 中 | 四类信息齐备 | ⏳ |
| [ ] A12 | 台账销账：`register.md`（REQ-SEC-006 + AUTH-06/11）、`whale-auth-backlog.md`、`next-steps.md` | A11 | 中 | 删除线 + ✅ | ⏳ |
| [ ] A13 | 自动 commit + push（Gitee origin/main） | A12 | 中 | 工作区干净且 `main == origin/main` | ⏳ |

## 实施注意（给实现者的事实约束）

- **自动配置时序**：`WhaleLogAutoConfiguration` 内**禁止** `@ConditionalOnBean(XxxMapper.class)`——Wave 5.1e 已证 `@ConditionalOnBean` 在配置类解析期早于 `@MapperScan`，条件恒不成立致切面永不装配；须在 Bean 方法内用 `ObjectProvider<XxxMapper>` **实例化期**延迟解析，解析不到则返回 `null` 不装配。
- **响应体回写**：`ApiLogFilter` 的 `finally` **必须**调用 `LimitedCachingResponseWrapper#copyBodyToResponse()`，否则客户端收空响应（[spec 风险表](./spec.md)）。文件/大 body 走 Content-Type 白名单跳过，避免 OOM。
- **采样与排除**：`exclude-paths` 默认含 `/audit/log/frontend/**`、`/audit/log/behavior/**` 防上报回环（上报请求本身不打接口日志）。
- **线程池隔离**：新建 `whaleLogExecutor`，**不复用** `auditLogExecutor`（core=1 会被高频接口日志打满并阻塞审计）。
- **脱敏复用**：`SensitiveDataMasker#maskJson` 只新增方法，**不改** 既有 `maskArguments`/`mask` 语义（Wave 5.1b 既有用例须保持全绿）；`maskJson` 解析失败仅按 `MAX_PARAM_LENGTH` 截断，不抛异常。
- **鉴权边界**：上报接口 `permitAll`（未登录期报错也需上报），但 token 存在时以网关 `X-User-Id` 回填 `user_id`（上报体 `userId` 不可信）。
- **路由约定**：`/audit/log` 服务自持一级前缀，网关**不剥离**（REQ-ARCH-003），白名单路径按同一对外路径书写。
- 锁/缓存无关；本波不触碰 whale-auth 权限判定链路。

## 待做项（本波明确不做，登记备查）

- **前端 `whale-log-sdk`（Wave B）**：console 劫持 + 行为埋点 + 批量/采样/去重/退避 + 隐私掩码；基座 `whale-platform` 统一 `initLogSdk`。待 A 批接口冻结后另立 spec。
- **MQ 汇聚（阶段二）**：`whale.audit.sink=mq` 仅预留开关；RabbitMQ 未就绪，本波只实现 `local` 分支。
- **保留期策略**：`whale.audit.retention.days` 扩多表（当前仅有 `audit_log` 语义）。
- **异步派发采集**：`shouldNotFilterAsyncDispatch()=true` 登记为已知丢采集。

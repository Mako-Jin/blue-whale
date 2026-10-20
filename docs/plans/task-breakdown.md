# 任务拆分与并行执行方案

> 基于 [roadmap.md](roadmap.md) 拆分为可并行的小任务。执行原则：**先定契约，再波次并行**；每个子任务由独立执行单元完成并自行编译验证。

## 并行性分析

| 依赖关系 | 结论 |
|---|---|
| whale-common 是 auth/gateway/i18n 的依赖 | **T1.1 必须先行**（Wave 1），核心服务在 Wave 2 并行 |
| DDL 脚本、前端基座、Docker 检查 与 common 无依赖 | 可与 T1.1 同时并行 |
| whale-auth / whale-gateway / whale-i18n-sdk 三者互不依赖 | Wave 2 三路并行 |
| 集成验证需所有产物 | Wave 3 串行收尾 |

环境限制：本机 **未安装 Docker**，容器启动类任务（Phase 1.2/1.3）推迟，改为产出物就绪 + 验证脚本。

## Wave 0：契约与准备（串行）

- [x] ~~T0.1 定义 common 契约（见附录 A），写入本文档供 Wave 1/2 共同遵守~~
- [x] ~~T0.2 环境检查：Docker 未安装 → Phase 1 容器启动推迟；DDL 改为脚本产出 + SQL 审查~~
- [x] ~~T0.3 拆分文档提交推送（`8eddae1`）~~

## Wave 1：基础层（4 路并行）

- [x] ~~T1.1 **whale-common 填充**~~
  - `common/core`：Result / PageResult / PageQuery / ErrorCode / GlobalException / GlobalExceptionHandler
  - `common/mybatis`：MyBatis-Plus 分页插件 + 字段自动填充 MetaObjectHandler 配置类
  - whale-tools：补充 AssertX / IdUtils 等实用工具
  - 验证：`mvn -pl whale-common/... compile` 通过
- [x] ~~T1.2 **全服务 DDL 脚本**（`backend/<module>/src/main/resources/db/init.sql`）~~
  - whale-auth：sys_user / sys_role / sys_permission / sys_menu / sys_dept / sys_user_role / sys_role_permission / sys_user_permission(ABAC属性)
  - whale-i18n-sdk：sys_i18n_message
  - whale-audit：audit_log；whale-file：file_info；whale-job：job_info/job_log
  - 验证：语句审查 + 与实体字段一致
- [x] ~~T1.3 **前端基座登录与布局**（whale-platform）~~
  - 登录页（mock 登录 API）+ 路由守卫 + MainLayout(Header/Sidebar/多标签) + 主题接入
  - 验证：`pnpm --filter @bluewhale/whale-platform build` 通过
- [x] ~~T1.4 **运维脚本补全**：`infrastructure/scripts/` 增加 dev 启动校验脚本（检查 MySQL/Redis/Nacos 可达性），替代 Docker 缺位（check-env.ps1 / check-env.sh）~~

## Wave 2：核心服务（3 路并行，依赖 T1.1）

- [x] ~~T2.1 **whale-auth 权限中心**（最大任务）~~
  - JWT：登录/刷新/注销，jjwt 0.12 API
  - Spring Security 过滤器链 + UserDetailsService
  - RBAC 六层级实体/Mapper/Service/Controller：用户、角色、菜单、权限点（接口/按钮）、部门、ABAC 属性
  - 内置数据：admin 用户 + 初始化角色
  - 验证：`mvn -pl whale-services/whale-auth compile` + 启动类加载测试
- [x] ~~T2.2 **whale-gateway 网关**~~
  - Nacos 动态路由 + 白名单（auth 相关路径放行）
  - JWT 全局过滤器（解析 token 传递用户信息 Header）
  - 全局异常处理 + TraceId 响应头
  - 验证：`mvn -pl whale-services/whale-gateway compile`
- [x] ~~T2.3 **whale-i18n-sdk 国际化**~~
  - 三级优先级：数据库 > 属性文件 > 默认值（MessageSource 扩展）
  - Redis 缓存 + `I18nDatabaseProvider` SPI 实现
  - 验证：`mvn -pl whale-sdk/whale-i18n-sdk compile` + 单测三级回退逻辑

## Wave 3：集成验证与收尾（串行）

- [x] ~~T3.1 `mvn clean install` 全量构建，修复跨模块问题（25 模块 exit 0）~~
- [x] ~~T3.2 `pnpm build` 前端全量构建（11/11 successful）~~
- [x] ~~T3.3 更新 roadmap 勾选 + devlog 记录 + commit + push origin~~

## Wave 4：工程规范与分布式基础（2026-10-17，串并混合）

> 本轮新增需求见 [requirements/register.md](../requirements/register.md) 第二节（REQ-DOC/QA/CICD/SEC/DIST/ARCH 共 12 条）。
> 本轮执行原则：**先落文档 → 编码 → 按清单自检留报告 → 构建验证**。

- [x] ~~T4.0 **工程规范体系**（REQ-DOC-001/002、REQ-QA-001/002）~~
  - `docs/requirements/register.md`：REQ 编号体系（8 域）+ 变更控制七步流程 + 四条门禁
  - `docs/checklists/code-review-checklist.md`：5 大类 57 项 + 严重级别 + 报告模板
  - `docs/reviews/`：每次变更一份检查报告（首份已归档）
  - 验证：清单可逐条对照；`docs/README.md` 约定章节同步更新
- [x] ~~T4.0b **四份专项设计文档定稿**（REQ-SEC-001 / REQ-DIST-001~003 / REQ-ARCH-001 / REQ-CICD-001）~~
  - `docs/design/audit-log.md` / `distributed.md` / `ddd-whale-auth.md` / `cicd.md`
  - 验证：与真实代码与 DDL 核对一致（audit_log 字段已比对 init.sql）
- [x] ~~T4.0c **CI/CD 代码质量门禁**（REQ-CICD-001）~~
  - `config/checkstyle/whale-checkstyle.xml`（C/D/E 类可自动化项）、`whale-parent/pom.xml` 插件管理、`.github/workflows/code-quality.yml`
  - 修正 `.github/workflows/frontend-ci.yml`：缺省工作目录 `frontend`
  - 验证：`mvn -pl backend/whale-common/whale-tools checkstyle:check`、`.../whale-auth checkstyle:check` 均 0 违规
  - 遗留：Gitee 托管需映射 Gitee Go；门禁当前为报告模式
- [x] ~~T4.1 **分布式令牌黑名单**（REQ-SEC-002 / REQ-DIST-002）~~
  - 新增 `TokenBlacklist` / `RedisTokenBlacklist`；改造 `JwtAuthenticationFilter`、`AuthService#logout`、`AuthController#logout`、`JwtTokenProvider`、`whale-auth/pom.xml`
  - 验证：`mvn -pl backend/whale-services/whale-auth -am compile` → BUILD SUCCESS（6 模块 / 67 源文件）
  - 遗留：REQ-SEC-003（网关侧校验）、REQ-SEC-004（refreshToken 失效）→ 已于 Wave 5.1（T5.6/T5.7）闭环
- [x] ~~T4.2 审计日志落地（`@AuditLog` + AOP + 异步写库 + 脱敏 + 7 个 ALTER 字段）~~
  - 已由 Wave 5.1b（T5.10/T5.11/T5.12）落地到 `whale-spring-boot-starter` `com.bluewhale.common.audit.*` + `whale-audit` 查询接口
  - 验证：全量编译 SUCCESS / 40 单测全绿 / Checkstyle 0 违规；遗留：运行时端到端（无 Docker）
- [x] ~~T4.3 分布式锁工具（`@DistributedLock` + SpEL key + Redisson AOP）~~
  - 已由 Wave 5.0 T5.2 落地
- [x] ~~T4.4 auth DDD 迁移第 1 步（四层目录骨架 + 认证上下文试点，API 不中断）~~
  - 已由 Wave 5.2 T5.13 / AUTH-14 落地（四层骨架 + 4 值对象 + 契约冻结清单）；后续迁移见 T5.14/T5.15
- [ ] T4.5 全量回归（`mvn clean install -T 1C` + `pnpm build`）+ 提交推送

## Wave 5.1b：审计日志落地（2026-10-17）

> 承接 Wave 4 T4.2，落地 REQ-SEC-001（T5.10/T5.11/T5.12）。执行原则同 Wave 4：先落文档 → 编码 → 按清单自检留报告 → 构建验证。

- [x] ~~①审计核心下沉 starter：`whale-audit` 经 `repackage` 打包后类位于 `BOOT-INF/classes`、无法被 auth 依赖，故 `@AuditLog` / 切面 / 脱敏 / 实体 / mapper / 自动配置统一置于 `whale-spring-boot-starter` `com.bluewhale.common.audit.*`（原 `com.bluewhale.audit.annotation` 路径废弃）~~
- [x] ~~②`@AuditLog` 注解 + `AuditLogAspect` 切面 + `afterCommit`/直提双路径异步落库（线程池 core=1 / max=2 / queue=256 / `CallerRunsPolicy`）~~
- [x] ~~③`SensitiveDataMasker` 脱敏器（递归深度 8 + 参数 4096 字符截断 + key 归一化规则匹配）~~
- [x] ~~④whale-audit 查询接口 `AuditLogController`（`/audit/log` 的 page / 详情 / 按 traceId，`size<=200`）~~
- [x] ~~⑤auth 埋点（登录/登出/创建用户/分配角色/分配权限）+ auth 侧 `db/init.sql` 追加 `audit_log` 建表~~
- [x] ~~⑥单测与验证：新增 40 用例全绿（starter 15 / audit 6 / auth 19 不回归）；全量编译 SUCCESS；Checkstyle 0 违规~~
- [x] ~~⑦检查报告归档：`docs/reviews/2026-10-17-wave5.1b-audit-log.md`~~
- [ ] 待做：运行时端到端验证（无 Docker）、`POST /audit/log/export`、`@RequirePermission("sys:audit:log:list")`、MQ 汇聚（阶段二）、归档任务、网关 `/audit/**` + StripPrefix 路由前缀冲突确认

## Wave 5.18：whale-ui 二次封装 + admin 三管理页（2026-10-19）

> 承接 [next-steps.md](next-steps.md) 组 F：T5.22 / T5.23。因 T5.23 依赖 T5.22（组件不存在则无从消费），本波合并推进：先落组件，再搭页面，避免二次重构。

- [x] ~~T5.22-a：whale-ui 组件封装~~（`ProTable` / `FormModal` / `SearchForm`）
- [x] ~~T5.22-b：按钮级权限组件~~（`PermissionProvider` / `usePermission` / `AuthButton`）
- [x] ~~T5.22-c：whale-ui 独立构建（tsup + dts）+ 示例页~~
- [x] ~~T5.23-a：admin API 层~~（`http.ts` / `token.ts` + user/role/menu/dept/permission 五模块）
- [x] ~~T5.23-b：admin 骨架~~（`PermissionGate` / `AdminLayout` / 路由拆分）
- [x] ~~T5.23-c：用户管理页（CRUD + 启停 + 重置密码 + 分配角色）~~
- [x] ~~T5.23-d：角色管理页（CRUD + 分配权限）~~
- [x] ~~T5.23-e：菜单管理页（树表 CRUD）~~
- [x] ~~T5.23-f：构建验证 + 浏览器端到端（三页 CRUD + 按钮权限隐藏 + 守卫）~~
- [x] ~~缺陷：admin vite 代理正则未覆盖集合型接口，致 `POST /user|/role|/menu` 落 vite 返回 404~~
- [ ] 待做：admin 动态路由（`menus[]` 生成导航）、角色/菜单删除与角色分配的 UI 脚本化回归、AUTH-32 refresh 前端运行时验证、`infrastructure/.env.example`

> Wave 5 详细任务清单（含依赖、优先级、验收口径）见 [next-steps.md](next-steps.md)。

## Wave 5.19：whale-job 骨架填充 + 权限中心 DDD 第 2 步 + 审计残余收尾（2026-10-19）

> 用户指令（2026-10-19）：① 继续权限中心；② 继续审计日志；③ 决定下一个服务 → 经决策选定 **whale-job**。
> 决策依据：whale-job 的表（`job_info`/`job_log`）与网关路由 `/job/**` 均已就绪，且建好后可解锁审计 **AUTH-09**（归档定时任务），与审计诉求直接衔接。
> 执行原则同前：**先落文档 → 编码 → 自检留报告 → 构建验证**；四轨并行，末尾统一全量回归。

**轨道 A：whale-job 服务填充（T5.24，REQ-FUNC-006）**

- [x] T5.24-a 依赖与配置：`whale-spring-boot-starter`（Result/异常/MyBatis 自动填充）+ datasource（`bluewhale_job`）+ `whale.permission.header.enabled` ✅
- [x] T5.24-b 实体/Mapper：`JobInfo` / `JobLog`（对齐 `db/init.sql`）+ 分页/逻辑删除 ✅
- [x] T5.24-c 调度引擎：`JobHandler` SPI + `JobScheduler`（`ThreadPoolTaskScheduler` + `CronTrigger`，无额外依赖）+ 启动装载/增删改后重排 ✅
- [x] T5.24-d REST：`/job/info` CRUD + `/{id}/status` + `/{id}/run` + `/job/log/page`（`@RequirePermission("sys:job:*")` + `@AuditLog`）✅
- [x] T5.24-e 权限点种子（whale-auth `db/init.sql` id 26–31）+ 建库 + 构建 + 启动冒烟（8082，Nacos 注册，经网关 `/job/**` 可达；`POST /job/info`+`/{id}/run`+`GET /job/log/page` 均 `code=0`，调度器 1 cron + 1 手动共 2 条日志）✅

**轨道 B：权限中心 DDD 迁移第 2 步（AUTH-15 ↔ T5.14，REQ-ARCH-001）**

- [x] T5.14-a `User` 聚合根 + 内部实体 `UserRoleAssignment` + 不变量（domain，无框架注解；工厂 `create`/`reconstitute` + 行为 `enable`/`disable`）✅
- [x] T5.14-b `UserRepository` 仓储接口（domain）：save/findById/findByUsername/existsByUsername/replaceRoles ✅
- [x] T5.14-c `infrastructure/persistence`：PO 复用既有 `SysUser` + `UserRepositoryImpl` + **手写** `UserPersistenceAssembler`（PO↔领域对象字段比对单测）✅
- [x] T5.14-d 登录链路 `UserDetailsServiceImpl` 改走新仓储（REST 契约不变）+ 单测回归（whale-auth 143 → **164 用例全绿**）✅

**轨道 C：权限中心收尾（AUTH-30 + 台账文书）**

- [x] AUTH-30：网关 **429 端到端冒烟**（Redis 计数器限流实机触发；60 并发打 `/audit/log/frontend/batch` → **30×200 + 30×429**，429 体 `{"code":429,"message":"请求过于频繁，请稍后重试"}`）✅；Sentinel 接入结论：暂缓（现有 Redis 固定窗口满足需求，Sentinel 待后续按需引入）
- [x] 台账：AUTH-22 / AUTH-32 标记闭合（实际已于 Wave 5.3 / 5.17 完成）✅

**轨道 D：审计日志残余收尾（whale-audit）**

- [x] D-a 四通道查询端点补 `@AuditLog(type=QUERY, targetType=<通道表>)` 元审计 + 单测（`LogIngestControllerMetaAuditTest` 反射断言）✅
- [x] D-b `@Sensitive` 注解兜底（字段级脱敏），补齐 `SensitiveDataMasker` 注解路径（`maskJson(Object,String)` 重载 + `maskBean` 反射收集 `@Sensitive` 字段）✅
- [x] D-c 四通道「写—查」运行时端到端验证（经网关 8080：`/audit/log/{frontend,behavior}/batch` 写 → 四通道 `page` 均 `code=0`；`audit_log` 落 QUERY 元审计 4 类）✅
- [x] D-d（新增，实机发现）**建库 `bluewhale_audit` + 应用 `whale-audit/src/main/resources/db/init.sql`（5 表）** —— 此前库缺失导致审计落库/查询全部报 `Unknown database 'bluewhale_audit'` ✅
- [x] D-e（新增，实机发现并修复）**网关白名单过宽缺陷**：`/audit/log/frontend/**` 同时放行了需鉴权的查询端点 `/audit/log/frontend/page`，致网关跳过 JWT、漏注入 `X-Permission-Codes` → 下游 403；改为仅放行 `/audit/log/{frontend,behavior}/batch`（限流路径同步收窄）✅

**收尾（串行）**

- [x] AUTH-27 ↔ T5.20：全量回归 `mvn clean install -T 1C`（**28 模块 BUILD SUCCESS**）+ `pnpm build`（**12/12 成功**）+ 报告归档 ✅
- [x] 更新 roadmap / devlog / whale-auth-backlog / register + 提交推送 Gitee ✅

## Wave 5.20：whale-job「继续完成」——调度健壮性 + 可观测 + 日志清理 + 单测基线（2026-10-19）

> 用户指令（2026-10-19）：继续完成定时任务服务。经决策（AskUserQuestion 四项全选）确定范围；
> 规格与分解：[spec.md](wave5.20-job/spec.md) / [tasks.md](wave5.20-job/tasks.md)。
> 非目标：`job_log` **归档导出至对象存储**（依赖 whale-file）继续阻塞，本轮只做本地保留策略清理。

**T1 基础设施**

- [x] T1.1 `config/JobProperties`（`@ConfigurationProperties("whale.job")`，`logRetentionDays` 默认 30）✅
- [x] T1.2 `scheduler/JobExecutionGuard`（实例内互斥 `running` + 待补跑 `pendingOnce`；`tryEnter`/`exit`/`markPendingOnce`/`consumePendingOnce`/`isRunning`）✅
- [x] T1.3 `scheduler/JobHandlerRegistry`（`resolve`/`exists`，缺 Bean / 类型不符抛 `PARAM_ERROR`）✅

**T2 调度健壮性（`JobScheduler` 重构）**

- [x] T2.1 注入 `JobHandlerRegistry` / `JobExecutionGuard` / `ObjectProvider<RedissonClient>`（Redisson 缺失自动降级）✅
- [x] T2.2 `execute` 包可见：`guard.tryEnter` 失败 → `handleOverlap`；成功 → `runWithDistributedLock` + `finally exit` → `drainPendingOnce` ✅
- [x] T2.3 分布式锁 `lock:job:execute:{jobId}`（`tryLock(0, TimeUnit.SECONDS)`，看门狗续期；未获锁跳过且不补偿；Redis 异常降级放行）+ `unlock` 仅当当前线程持有 ✅
- [x] T2.4 `misfire_policy` 落地（由死字段转生效）：`DO_NOTHING` 跳过；`FIRE_ONCE`/`SMART` 登记一次性补跑，补跑不再补偿（`drainPendingOnce` 循环消费，栈深有界）✅
- [x] T2.5 原执行体改为包可见 `doExecute`；`resolveHandler` 下沉至 `JobHandlerRegistry` ✅
- [x] T2.6 `runOnce` 改 `taskScheduler.execute(...)` 异步（保留同步存在性校验抛 `NOT_FOUND`）✅
- [x] T2.7 补 `@PreDestroy destroy()` 真正关闭线程池（原 `setWaitForTasksToCompleteOnShutdown` 因非 Spring Bean 管理而未生效）✅

**T3 可观测与校验**

- [x] T3.1 `JobInfoVO` 增 `nextFireTime`✅
- [x] T3.2 `JobInfoServiceImpl` 创建/更新增 handler Bean 存在性校验（`PARAM_ERROR`）✅
- [x] T3.3 `toVo` 推算 `nextFireTime`（合法 cron → `CronExpression.parse(cron).next(now)`，否则 null）✅

**T4 归档清理与依赖配置**

- [x] T4.1 `scheduler/handler/JobLogCleanupHandler`（Bean 名 `jobLogCleanupHandler`；逻辑删除超过保留期的 `job_log`）✅
- [x] T4.2 `WhaleJobApplication` 增 `@ConfigurationPropertiesScan`✅
- [x] T4.3 `pom.xml` 增 `spring-boot-starter-data-redis` + `redisson-spring-boot-starter`（版本由 parent 管理）✅
- [x] T4.4 `application.yml` 增 `spring.data.redis`（host/port/password/database，密码走 `SPRING_DATA_REDIS_PASSWORD`）+ `whale.job.log-retention-days`✅
- [x] T4.5 `db/init.sql` 增内置清理任务种子（id=1，`0 0 3 * * ?`，`jobLogCleanupHandler`，status=1）✅

**T5 单测基线**

- [x] T5.1–T5.6 新增 6 个测试类（`JobExecutionGuardTest` 6 / `JobHandlerRegistryTest` 6 / `JobSchedulerTest` 10 / `JobInfoServiceImplTest` 13 / `JobLogCleanupHandlerTest` 4 / `DemoJobHandlerTest` 3）**共 42 用例全绿** ✅

**T6/T7 验证与收尾**

- [x] T6.1 `mvn -pl ...whale-job test` 42/42 通过；`checkstyle:check` 0 违规 ✅
- [x] T6.2 全量回归 `mvn clean install -T 1C` **29 模块 BUILD SUCCESS** ✅
- [x] T6.3 8082 启动冒烟：Redisson 4.8.0 连接成功；种子任务注册（`jobId=1`）；`GET /job/info/page` 返回 `nextFireTime=2026-10-20T03:00:00`；`POST /job/info/1/run` 落 `job_log`（status=1，清理摘要）；Redis `lock:job:*` 执行后已释放 ✅
- [x] T7 更新 roadmap / devlog / 审查报告 + 提交推送 Gitee ✅

## Wave 5.21：四轨并行（whale-file 最小可用 + 归档链路解冻 + 网关熔断/动态路由 + 权限 DDD 第 3 步）（2026-10-19）

> 用户指令（2026-10-19）：盘点权限/审计/网关/定时任务四服务未完成项后，经决策（AskUserQuestion 四项全选）确定本轮范围。
> 规格与分解：[spec.md](wave5.21-four-tracks/spec.md) / [tasks.md](wave5.21-four-tracks/tasks.md)。
> 冻结契约（spec §三）先落地，各轨仅改自己模块；`whale-gateway/application.yml` 单点所有权归 T4/T5/T6 同一执行单元。

**Wave 5.21-a（并行：T1 / T4 / T5 / T6 / T7 / T8）**

- [x] **T1 whale-file 最小可用**（`whale-services/whale-file`）：`FileStorage` SPI + `LocalFileStorage` + `FileProperties` + `FileInfo`/Mapper/Service/Impl/Controller + DTO/VO + pom 依赖 + yml（datasource/whale.file）；`POST /file/upload`、`POST /file/internal/upload`、`GET /file/{id}/download`、`GET /file/page`、`DELETE /file/{id}`；`whale-auth/db/init.sql` 追加 `sys:file:{upload,download,list,delete}` 4 权限点（id 32–35）；8 单测；**顺带修复 `${server.port:8085}` 自引用循环占位符缺陷** ✅
- [x] **T4 网关熔断**：`CircuitBreakerGlobalFilter`（order=-75，滑动窗口失败率 + 超时 + 半开探测）+ `CircuitBreakerProperties`（`whale.gateway.circuit-breaker.*`，`apply-to-routes` 默认 `whale-auth`）+ 503 `Result` 体降级；**未引入 resilience4j（自研轻量实现，符合 spec 风险回退条款）**；48 单测 ✅
- [x] **T5 网关动态路由**：`spring.config.import: optional:nacos:whale-gateway-routes.yaml?group=DEFAULT_GROUP&refreshEnabled=true` + `NacosRouteDefinitionRepository`（Nacos 读 routes JSON）+ 静态路由兜底 ✅
- [x] **T6 网关内网屏蔽**：`InternalApiDenyGlobalFilter`（`/file/internal/**` → 403 `{"code":403,"message":"内网接口不允许外部访问"}`）✅
- [x] **T7 DDD 第 3 步（AUTH-16）**：`domain/{role,menu,permission,dept}/**` 四聚合 + `repository` 接口 + `infrastructure/persistence/{assembler,repository}` 四实现 + `DddMigrationProperties`（`whale.auth.ddd.*`，默认 false，服务层按 flag 双路径）；auth 单测 164 → **219**（无回归）✅
- [x] **T8 台账修订**（零代码）：audit final §2/§6.2 改判、auth backlog AUTH-08/09/16/27 闭合、新建 `docs/plans/whale-gateway-backlog.md`（GW-01~07 + 残余风险）、roadmap §2.2/§2.5 口径、next-steps 加时效提示 ✅

**Wave 5.21-b（并行，依赖 T1 契约）**

- [x] **T2 whale-job 归档导出**：`JobLogArchiveHandler` + `JobArchiveClient`（`RestClient` 直连 `whale.job.file-service-url`）+ `JobProperties#fileServiceUrl/archiveBatchSize` + `db/init.sql` 种子任务 id=2；语义「导出 CSV 上传 whale-file 成功后再逻辑删除，失败不删」；job 48 单测 ✅
- [x] **T3 whale-audit 导出与归档**：`POST /audit/log/export`（`EXPORT` 元审计）+ `AuditLogArchiver`（挂既有 `LogRetentionScheduler`，`archive-enabled` 默认 false，**只导出不物理删**，append-only 不变）+ `ExportProperties`；audit 59 单测 ✅

**Wave 5.21-c（串行：T9 收尾）**

- [x] 全量回归 `mvn clean install -T 1C` **29 模块 BUILD SUCCESS**（0 fail·0 error）+ 关键模块单测（auth 219 / gateway 48 / job 48 / audit 59 / file 8 / starter 68）✅
- [x] 增量种子应用：`sys_permission` 31 → **35**；`job_info` 1 → **2**（`bluewhale_file` 建表 + 增量 `INSERT IGNORE`，未重跑含 `DROP TABLE` 的整份脚本）✅
- [x] 运行时冒烟（起 5 服务 8080/8081/8082/8085/9101 经网关）：login `code=0`；file upload/download/page `code=0`；`/file/internal/upload` **403**；audit export `code=0`（CSV objectKey）；job id=2 run `code=0` ✅
- [x] **熔断运行时取证**：临时 `WHALE_GATEWAY_CIRCUIT_BREAKER_TIMEOUT_MILLIS=1`/`WINDOW_SIZE=4` 重启网关，`/auth/me` 连续失败填满窗口 → 熔断打开 → **503** `{"code":503,"message":"服务暂时不可用，请稍后重试"}`；恢复默认配置后 `/auth/me` 200 ✅
- [x] 检查报告 `docs/reviews/2026-10-19-wave5.21-four-tracks.md` + devlog 追加 + roadmap/task-breakdown 勾选 + commit/push origin（Gitee）✅

> 残余（登记未处理）：whale-job/whale-audit 无 `api_log` 表（每请求报 `Table 'api_log' doesn't exist`，与平台既有行为一致，判为非阻断噪音）；workflow/form/pay/config/monitor/alert 等同用 `${server.port:…}` 自引用写法，存在同类潜在缺陷；熔断半开探测与动态路由热刷新未做真实 Nacos 取证。


## Wave 5.22：四轨并行（网关 backlog 收口 + admin 动态路由 + whale-file 对象存储与归档取证 + auth 控制器单测/DDD 灰度）（2026-10-19）

> 用户指令（2026-10-19）：Wave 5.21 交付后经 AskUserQuestion（四项全选）确定本轮范围。
> 规格与分解：[spec.md](wave5.22-four-tracks/spec.md) / [tasks.md](wave5.22-four-tracks/tasks.md)。
> 冻结契约（spec §三）：指标命名 `gateway.circuit.*`、`menus[]` 类型与 component 映射、`whale.file.storage.type`、`@WebMvcTest` 隔离口径。

**Wave 5.22-a（并行：A 网关 / B admin / C1 whale-file / D1 auth）**

- [x] **A1 GW-04 熔断 Micrometer 指标**（`whale-gateway`）：`ObjectProvider<MeterRegistry>` 可选注入；注册 `gateway.circuit.calls{route,result}` / `gateway.circuit.short_circuits{route}` / `gateway.circuit.state{route}`（0/1/2）
- [x] **A2 GW-05 多路由纳入熔断**：`apply-to-routes` 扩为 `whale-auth, whale-file, whale-job, whale-audit`
- [x] **A3 GW-06 内网屏蔽覆盖**：确认 `patterns` 配置化语义 + 多 pattern/通配单测
- [x] **A4 GW-07 测试基线**：随 A1/A3 扩充，0 违规
- [x] **B1–B3 admin 动态路由**（`frontend/admin`）：`menus` 类型建模 + `router/dynamicRoutes.tsx`（menus → RouteObject + component 映射 + 侧栏 items）+ `App.tsx`/`AdminLayout` 接线 + 空回落静态导航
- [x] **B4 浏览器端到端验证**：侧栏由 menus 驱动、跳转正确、未知 component 不崩
- [x] **C1 whale-file 对象存储抽象**（`whale-file`）：`whale.file.storage.type=local|minio` + `MinioFileStorage`（`io.minio:minio:8.5.12`）+ `MinioProperties` + 装配选择 + 单测
- [x] **D1 auth 控制器层单测**（`whale-auth`）：`@WebMvcTest` + `@MockitoBean` 覆盖 6 控制器关键端点

**Wave 5.22-b（并行：运行时取证）**

- [x] **C2 归档链路 e2e**：job id=2 → CSV 上传 whale-file（`file_info` 落库）→ 过期 `job_log` 逻辑删除；audit `archive-enabled=true` → 导出 CSV 且 `audit_log` 未删
- [x] **D2 DDD 灰度运行时**：`whale.auth.ddd.role/menu/permission/dept=true` 启动 auth，验证 page/tree/detail 与 flag=false 等价
- [x] **A2 熔断 503 取证**：多路由（file/job）经网关降级验证
- [x] **D3 AUTH-10 阻塞登记**：无 MQ 依赖 + 无 RabbitMQ → 台账标注阻塞

**Wave 5.22-c（串行：E 收尾）**

- [x] 全量 `mvn clean install -T 1C` SUCCESS + 前端 `pnpm --filter whale-admin build`
- [x] 检查报告 `docs/reviews/2026-10-19-wave5.22-four-tracks.md` + devlog 追加 + roadmap/task-breakdown/backlog 勾选
- [x] commit + push origin（Gitee）


## Wave 5.23：AUTH-39 残余收口 + whale-form 最小可用（2026-10-19）

> 用户指令（2026-10-19）：① 完成 AUTH-39；② 当前服务任务已完成 → 启动 whale-form 服务。
> 经 AskUserQuestion 确认口径：**AUTH-39 = 前端自动续期 + 运行时取证**；**whale-form = 表单定义 CRUD 最小可用**。
> 规格与分解：[spec.md](wave5.23-auth39-form/spec.md) / [tasks.md](wave5.23-auth39-form/tasks.md)。
> 冻结契约（spec §三）：401→单飞刷新→重放一次；`/form/definition` 5 接口 + `sys:form:*` 权限码；`form_definition` 表结构。

**Wave 5.23-a（并行：T1 admin 续期 / T2 whale-form 服务）**

- [x] **T1-a 前端续期链**（`frontend/admin`）：`api/http.ts` 401→单飞刷新（`refreshOnce`）→重放一次 + `api/auth.ts#refresh()` + `token.ts#saveLogin` 合并既有用户态；`pnpm --filter whale-admin build` SUCCESS（1458 modules）
- [x] **T2-a whale-form 骨架接线**：`application.yml` 补 datasource(`bluewhale_form`)/mybatis/权限头开关 + `db/init.sql`（`form_definition`）；**修复端口自引用占位符 `${server.port:8084}` → `${SERVER_PORT:8084}`**（启动失败根因）
- [x] **T2-b whale-form 业务**：`FormDefinition` + Mapper + `FormQueryRequest` + VO + Service(+Impl) + `FormController`（5 接口 + `@RequirePermission`）；pom 补 `whale-spring-boot-starter`
- [x] **T2-c 权限种子**：whale-auth `db/init.sql` 追加 `sys:form:add/edit/delete/list`（id 36–39），并落 `bluewhale_auth.sys_permission`
- [x] **T2-d 单测**：`FormDefinitionServiceImplTest` 8 用例（CRUD + code 查重 + schema 超限），Tests run 8 / 0 / 0，checkstyle 0 违规

**Wave 5.23-b（运行时取证）**

- [x] **T1-b AUTH-39 轮换取证**：开关开启 → 登录 → refresh 换新（`used:*` 落键）→ 复用旧 token → **HTTP 200 + 业务码 401**；**Redis `used:*` 4 条 / `family:*` 0 条**；顺序复用由**黑名单 fail-fast** 拦截，family 吊销仅并发争抢触发（ADR-001 R3，属预期，未改 `AuthService` 顺序）
- [x] **T2-e 建库与冒烟**：建 `bluewhale_form` + DDL + 权限种子落库；起 8084 经网关 `/form/**` 冒烟——新增/分页/详情/更新 `code=0`、重复 code `code=400`、删除 `code=0`、删后详情 `code=404`

**Wave 5.23-c（串行：E 收尾）**

- [x] 全量 `mvn clean install -T 1C` SUCCESS + 前端 `pnpm --filter whale-admin build`
- [x] 检查报告 `docs/reviews/2026-10-19-wave5.23-auth39-form.md` + devlog 追加 + roadmap/task-breakdown/whale-auth-backlog 勾选
- [x] commit + push origin（Gitee）


## Wave 5.24：whale-workflow 骨架填充——流程定义 CRUD 最小可用（2026-10-19）

> 用户指令（2026-10-19）：「开始搭建表单工作流服务」。
> 经 AskUserQuestion 确认口径：目标服务 = **填充既有 `whale-workflow` 骨架**（不新建服务、不与 whale-form 耦合）；MVP = **流程定义 CRUD 最小可用**。
> 规格与分解：[spec.md](wave5.24-workflow/spec.md) / [tasks.md](wave5.24-workflow/tasks.md)。
> 冻结契约（spec §三）：`/workflow/definition` 5 接口 + `sys:workflow:*` 权限码（id 40–43）；`process_definition` 表结构（`definition_json` MEDIUMTEXT ≤64KB）。

**Wave 5.24-a（并行：whale-workflow 服务 + DDL + 种子 + 单测）**

- [x] **T1 骨架接线**：`pom.xml` 增 `whale-spring-boot-starter`；`application.yml` 修端口自引用 `${server.port:8083}` → `${SERVER_PORT:8083}`，补 datasource(`bluewhale_workflow`)/mybatis-plus/权限头开关
- [x] **T2 业务**：`ProcessDefinition` + Mapper + `ProcessQueryRequest`(继承 `PageQuery`) + `ProcessDefinitionSaveRequest` + VO + Service(+Impl) + `WorkflowController`（5 接口 + `@RequirePermission("sys:workflow:*")`）
- [x] **T3 DDL 与种子**：`whale-workflow/db/init.sql`（`process_definition`）；whale-auth `db/init.sql` 追加 `sys:workflow:add/edit/delete/list`（id 40–43）
- [x] **T4 单测**：`ProcessDefinitionServiceImplTest`（CRUD + code 查重 + definition 超限），`mvn -o -pl :whale-workflow test checkstyle:check` 全绿（Tests run 8 / 0 / 0，Checkstyle 0 违规）

**Wave 5.24-b（运行时取证）**

- [x] **E2E 建库与冒烟**：建 `bluewhale_workflow` + DDL + 权限种子落库；起 8083 经网关 `/workflow/**` 冒烟——新增/分页/详情/更新 `code=0`、重复 code `code=400`、删除 `code=0`、删后详情 `code=404`

**Wave 5.24-c（串行：E 收尾）**

- [x] 全量 `mvn clean install -T 1C` SUCCESS（29/29 模块，Total 1:45）
- [x] 检查报告 `docs/reviews/2026-10-19-wave5.24-workflow.md` + devlog 追加 + roadmap/task-breakdown 勾选
- [x] commit + push origin（Gitee）


## Wave 5.25：whale-workflow 全闭环——流程实例 + 待办任务 + 审批动作（2026-10-19）

> 用户指令（2026-10-19）：「开始搭建表单工作流服务」→ 经 AskUserQuestion 确认：在 Wave 5.24「流程定义」之上，补**流程实例 + 待办任务 + 审批**，形成「定义 → 发起 → 待办 → 审批 → 状态流转」最小闭环。
> 规格与分解：[spec.md](wave5.25-workflow-closed-loop/spec.md) / [tasks.md](wave5.25-workflow-closed-loop/tasks.md)。
> 冻结契约（spec §三）：`process_instance`/`process_task` 两表；`definition_json.nodes` 有序节点契约；`/workflow/instance`、`/workflow/task` 7 接口 + 种 id 44–48；状态流转定义。

**Wave 5.25-a（模块内串行：实体 → 解析 → 服务 → 接口 → DDL → 种子 → 单测）**

- [x] **A-a 实体与映射**：`ProcessInstance`/`ProcessTask` + `ProcessInstanceMapper`/`ProcessTaskMapper`
- [x] **A-b 节点解析与 DTO/VO**：`support/ProcessNodeParser` + `dto/{ProcessInstanceStartRequest,ProcessInstanceQueryRequest,ProcessTaskQueryRequest,ApprovalRequest}` + `vo/{ProcessInstanceVO,ProcessTaskVO}`
- [x] **B-a 实例服务与接口**：`ProcessInstanceService(+Impl)`（start/详情(含 tasks)/分页/撤销）+ `ProcessInstanceController`（4 接口）
- [x] **B-b 待办服务与接口**：`ProcessTaskService(+Impl)`（分页/同意推进/驳回）+ `ProcessTaskController`（2 接口）
- [x] **C-a DDL**：`whale-workflow/db/init.sql` 追加 `process_instance`、`process_task`
- [x] **C-b 权限种子**：whale-auth `db/init.sql` 追加 `sys:workflow:instance:start/list/cancel`（44–46）、`sys:workflow:task:list/approve`（47–48）
- [x] **C-c 单测与门禁**：`ProcessInstanceServiceImplTest` + `ProcessTaskServiceImplTest`；`mvn -o -pl :whale-workflow -am test checkstyle:check` 全绿（Tests run 22 / 0 / 0，Checkstyle 0 违规）

**Wave 5.25-b（运行时取证）**

- [x] **E2E 建表与冒烟**：建表 + 种子落库；经网关 `/workflow/**` 全闭环——发起→同意推进→末节点通过（实例 status=1）；驳回→实例驳回 status=2；撤销→实例 status=3 + 待办 status=3；负向 400「任务已处理」/404

**Wave 5.25-c（串行：E 收尾）**

- [x] 全量 `mvn clean install -T 1C` SUCCESS（29/29 模块，Total 1:45；构建前停 7 服务释放 jar 锁，构建后重启并核验健康全 UP）
- [x] 检查报告 `docs/reviews/2026-10-19-wave5.25-workflow-closed-loop.md` + devlog 追加 + roadmap/task-breakdown 勾选
- [x] commit + push origin（Gitee）


## Wave 5.26：whale-form 全闭环（表单数据 `form_data`）

> 规格：[wave5.26-form-closed-loop/spec.md](wave5.26-form-closed-loop/spec.md) ｜ 分解：[tasks.md](wave5.26-form-closed-loop/tasks.md)。
> 口径（用户确认 2026-10-19）：在 Wave 5.23「表单定义」之上补 `form_data` 表单数据表与提交/查询/删除接口，形成「定义 → 填写 → 数据」最小闭环；**不含**表单↔工作流联动。

**Wave 5.26-a（模块内串行）**

- [x] **A-a 实体与映射**：`FormData`（继承 `BaseEntity`）+ `FormDataMapper`
- [x] **A-b 请求/视图**：`dto/{FormDataSubmitRequest,FormDataQueryRequest}` + `vo/FormDataVO`
- [x] **B 服务与接口**：`FormDataService(+Impl)`（submit/详情/分页/删除）+ `FormDataController`（4 接口）
- [x] **C-a DDL**：`whale-form/db/init.sql` 追加 `form_data` 表
- [x] **C-b 权限种子**：whale-auth `db/init.sql` 追加 `sys:form:data:submit/list/delete`（49–51）
- [x] **C-c 单测与门禁**：`FormDataServiceImplTest`；`mvn -o -pl :whale-form -am test checkstyle:check` 全绿（Tests run 16 / 0 / 0，Checkstyle 0 违规）

**Wave 5.26-b（运行时取证）**

- [x] **E2E 建表与冒烟**：建表 + 种子落库；经网关 `/form/**` 闭环——提交 `code=0` → 分页/详情可见 → 逻辑删除后详情 404；负向：定义不存在 404、`dataJson` 为空 400

**Wave 5.26-c（串行：E 收尾）**

- [x] 全量 `mvn clean install -T 1C` SUCCESS（29/29 模块，Total 1:53；构建前停 7 服务释放 jar 锁，构建后重启并核验健康全 200）
- [x] 检查报告 `docs/reviews/2026-10-19-wave5.26-form-closed-loop.md` + devlog 追加 + roadmap/task-breakdown 勾选
- [x] commit + push origin（Gitee）


## Wave 5.27：whale-form 动态表引擎（主从分离 · 版本化物理表 · 多租户）

> 用户指令（2026-10-19）：「我想的动态表单……业务多了，是不是这张表会炸。」→ 经 AskUserQuestion 逐轮冻结：**业务用户自助动态表单 + 多租户行级 `tenant_id` + 纯后端引擎 + 保留 `data_json` 过渡 + 版本化建新表 + `X-Tenant-Id` 头（缺省租户 1）**。
> 规格：[wave5.27-form-dynamic-table-engine/spec.md](wave5.27-form-dynamic-table-engine/spec.md) ｜ 分解：[tasks.md](wave5.27-form-dynamic-table-engine/tasks.md)。

**Wave 5.27-a（并行：A 表与登记 / B 引擎内核 / C 发布接线）**

- [x] **A-a 登记实体**：`FormTableRegistry` + `FormTableRegistryMapper`
- [x] **A-b DDL**：`whale-form/db/init.sql` 加 `form_data.tenant_id` + `idx_tenant_id`；新增 `form_table_registry`
- [x] **A-c 共享头**：`WebHeaders` 加 `X-Tenant-Id` 常量 + `tenantId(request)`
- [x] **B-a schema 解析器**：`support/{FormField,FormSchemaParser}`（key/type 白名单 + 保留列校验）
- [x] **B-b DDL 生成器**：`support/FormTableDdlGenerator`（表名规则 + 类型映射 + DDL）
- [x] **B-c 动态数据引擎**：`support/FormDataEngine(+impl/JdbcFormDataEngine)`（JdbcTemplate 参数化 + 表名二次正则）
- [x] **B-d 租户解析**：`support/TenantResolver`
- [x] **C-a 发布服务**：`service/FormPublishService(+Impl)`
- [x] **C-b 发布接口**：`FormController` +`POST /form/definition/{id}/publish`、`GET .../tables`；`vo/FormTableRegistryVO`
- [x] **C-d 权限种子**：`sys:form:publish`（id 52）

**Wave 5.27-b（数据改造 D + 门禁 E）**

- [x] **D 数据双写改造**：`FormData`/`FormDataVO` 加 `tenantId`(+`fields`)；`FormDataService(+Impl)`/`FormDataController` 走主表+物理表
- [x] **E-a 单测**：`FormSchemaParserTest`/`FormTableDdlGeneratorTest`/`FormPublishServiceImplTest` + 改造 `FormDataServiceImplTest`
- [x] **E-b 模块门禁**：`mvn -o -pl :whale-form -am test checkstyle:check` 0 违规
- [x] **E-c 全量回归**：`mvn clean install -T 1C` SUCCESS
- [x] **E-d 运行时冒烟**：建库/种子 + 网关 `/form` 发布→提交→详情→列表→删除 + 负向
- [x] **E-e 收尾**：检查报告 + devlog + roadmap/task-breakdown 勾选 + commit & push origin（Gitee）


## Wave 5.28：whale-form 动态表单元数据模型（三层分离 · 表:表单 1:N）

> 用户指令（2026-10-19）：「有一个动态列的详情表（列/类型/长度/必填/可空/备注）……还有一个表单的属性详情表（显示/排序/属性类型/可编辑/关联字段）……表单和表有一个关联。是不是这样的，你优化一下。」
> 经 AskUserQuestion 冻结：**(a) 表:表单=1:N｜(b) 选项独立表｜(c) `schema_json` 保留为发布快照｜(d) 本轮仅后端元数据表 + 迁移**。
> 规格：[wave5.28-form-metadata-model/spec.md](wave5.28-form-metadata-model/spec.md) ｜ 分解：[tasks.md](wave5.28-form-metadata-model/tasks.md)。

**Wave 5.28-a（并行：A 表结构 / B 类型与编译）**

- [x] **A-a 实体**：`FormTableDefinition`/`FormColumnMeta`/`FormFieldConfig`/`FormFieldOption`
- [x] **A-b Mapper**：4 个元数据 Mapper
- [x] **A-c 兼容列**：`FormDefinition(+tableDefinitionId)`、`FormTableRegistry(+tableDefinitionId/tableCode/tableVersion)`、`FormData(+table 维度/formDefinitionId)`
- [x] **A-d DDL**：`whale-form/db/init.sql` 4 新表 + 3 表兼容列/键（+ 增量脚本 `db/migration-5.28.sql`）
- [x] **B-a/B-b 类型**：`FormDataType`（存储↔MySQL 映射 + 默认 widget）、`FormWidgetType`（控件白名单）
- [x] **B-c 编译**：`FormMetadataCompiler`（元数据 → `schema_json` 快照）

**Wave 5.28-b（元数据服务 C + 迁移 D）**

- [x] **C-a/C-b DTO/VO**：表定义（含列）、字段配置（含选项）聚合 DTO/VO
- [x] **C-c 表定义服务**：`FormTableDefinitionService(+Impl)` 聚合 CRUD
- [x] **C-d 表定义接口**：`FormTableDefinitionController`（`/form/table-definition` CRUD + publish + tables）
- [x] **C-e 表单视图服务**：`FormDefinitionService(+Impl)` 持久化字段配置/选项 + 编译快照 + 详情带 fields
- [x] **C-f 权限种子**：`sys:form:table:*`（id 53–57）
- [x] **D-a 迁移**：`FormMetadataMigrationService(+Impl)`（`schema_json.fields[]` → 元数据，幂等 + 回填 registry/form_data）

**Wave 5.28-c（发布/提交改造 E + 门禁 F）**

- [x] **E-a 发布**：`FormPublishService(+Impl)` 表定义发布建物理表 + 视图发布编译快照 + 幂等确保物理表
- [x] **E-b 提交**：`FormDataService(+Impl)` 按视图 `field_config` 选列校验 + 表维度路由 + 主表新列
- [x] **E-c 接线**：`FormController` 表定义发布/列表 + 定义详情带字段
- [x] **F-a 单测**：`FormMetadataCompilerTest`/`FormTableDefinitionServiceImplTest`/`FormMetadataMigrationServiceImplTest` + 回归（44 / 0 / 0）
- [x] **F-b 模块门禁**：`mvn -o -f backend/pom.xml -pl :whale-form -am test checkstyle:check` 0 违规
- [x] **F-c 全量回归**：`mvn -o -f backend/pom.xml clean install -T 1C` 29 模块 SUCCESS
- [x] **F-d 运行时冒烟**：增量 DDL/迁移落库 + 网关 `/form` 表定义→双表单视图（1:N）→提交→详情→负向
- [x] **F-e 收尾**：检查报告 + devlog + roadmap/task-breakdown 勾选 + commit & push origin（Gitee）


## Wave 5.29：拖拽式表单设计器（前后端贯通）

> 用户指令（2026-10-20）：「今天打算搭建拖拽式表单搭建，前端后端一起搭建，并贯通。拆分任务，然后逐步搭建。」
> 经 AskUserQuestion 冻结：**(a) 范围=设计器+渲染填报+数据列表｜(b) `@dnd-kit`｜(c) 独立新应用 8002 真实认证直连网关｜(d) 可新建表也可选已有表｜(e) 栅格 12/24（加 `col_span`）｜(f) 支持纯布局字段**。
> 规格：[wave5.29-form-designer/spec.md](wave5.29-form-designer/spec.md) ｜ 分解：[tasks.md](wave5.29-form-designer/tasks.md)。

**Wave 5.29-a（F1 契约冻结 + 并行：F2 后端补强 / F3 前端骨架）**

- [x] **F1-a/F1-b 规格与分解**：`wave5.29-form-designer/{spec,tasks}.md`
- [x] **F1-c/F1-d 台账**：roadmap §2.5 追加 + task-breakdown 追加 Wave 5.29 分组
- [x] **F1-e 契约冻结**：`whale-form-designer/src/api/form.ts` 类型与接口契约
- [x] **F2 后端补强**：B1 DDL（`col_span` + `migration-5.29.sql`）、B2 实体/DTO/VO、B3 `enrichColumnInfo` 回填 + 单测 + 模块自检
- [x] **F3 前端骨架**：新应用骨架 + `api/{token,http,auth,form}.ts` + guards + AppLayout + Login + 装 `@dnd-kit`

**Wave 5.29-b（页面级并行 F4/F5/F7/F8）**

- [x] **F4 表定义管理页**：`pages/table/TableDefinitionList.tsx`（列编辑器 + 发布 + 新建表单视图跳转）
- [x] **F5 表单视图列表页**：`pages/form/FormList.tsx`（设计/发布/填报/数据/删除）
- [x] **F7 渲染器与填报页**：`render/FormRenderer.tsx` + `render/FormRender.tsx`（序列化契约）
- [x] **F8 数据列表页**：`pages/data/FormDataList.tsx`（分页 + 详情 标签:值 + 删除）

**Wave 5.29-c（设计器 F6 + 门禁 F9）**

- [x] **F6-a~e 设计器**：`designer/{FormDesigner,Palette,Canvas,PropertyPanel,FieldPreview}.tsx`（三栏拖拽 + 保存/发布/预览/回填 + 前置校验）
- [x] **F9-a 后端全量回归**：`mvn -f backend/pom.xml clean install`
- [x] **F9-b 前端构建**：`pnpm -C frontend --filter whale-form-designer build`
- [x] **F9-c 实机 E2E**：7 步正向 + 2 条负向 + `browser_use` 截图
- [x] **F9-e 收尾**：检查报告 + devlog + roadmap/task-breakdown 勾选
- [x] **F9-f 提交推送**：commit & push origin（Gitee）


## Wave 5.30：表单剩余任务 · 问题修复 · 收尾

> 用户指令（2026-10-20）：「完成表单的剩余任务，修复现有问题，然后收尾」。
> 经 AskUserQuestion 冻结范围（3 项）：**设计器撤销/重做 + 拖拽手势复验｜列删除/改类型在线迁移｜表单数据导出（CSV / Excel）**；未选表单↔工作流联动。
> 关键用户口径：「编辑/删除列只改元数据（`form_column_meta`），**发布或确认完成之后**才实际修改真实表结构、执行 `ALTER`」。
> 规格：[wave5.30-form-residual/spec.md](wave5.30-form-residual/spec.md) ｜ 分解：[tasks.md](wave5.30-form-residual/tasks.md) ｜ 报告：[reviews/2026-10-20-wave5.30-form-residual.md](../reviews/2026-10-20-wave5.30-form-residual.md)。

**Wave 5.30-a（契约冻结 + 3 条并行线）**

- [x] **线 A · 后端列变更在线迁移（R2）**：`FormDataEngine`/`JdbcFormDataEngine` +`describeColumns`；`FormTableDdlGenerator` +`columnDef/alterAdd/alterModify/alterDrop/normalizeType/reservedColumns`；`FormPublishServiceImpl` 抽 `ensurePublished`（CREATE/ALTER diff）+ `listMetadataCodes` DROP 护栏；`FormTableDefinitionServiceImpl.update` 走 `mergeColumns` 保列主键（P1 修复）
- [x] **线 B · 后端数据导出（R3）**：`support/FormDataExportSupport`（新，纯 JDK CSV/XLSX）、`vo/FormDataExportVO`（新）、`FormDataService(+Impl).export`（≤5000 行、解析 `dataJson`）、`FormDataController` +`GET /form/data/export`
- [x] **线 C · 前端（R4/R5）**：`api/http.ts downloadBlob` + `api/form.ts ExportFormat/formDataApi.export`；`FormDataList.tsx` 「导出」下拉；`TableDefinitionList.tsx` 迁移语义 `Alert` + 发布文案；`FormDesigner.tsx` 历史栈 + 撤销/重做按钮 + 快捷键
- [x] **线 D · 并行门禁**：`mvn -o -f backend/pom.xml clean install` 28/28 模块 SUCCESS；`pnpm -C frontend --filter whale-form-designer build` 通过（1461 modules）

**Wave 5.30-b（实机 E2E）**

- [x] **E1** 编辑表定义后物理表 `SHOW COLUMNS` 未变（编辑只改元数据；`amount` 列主键保留，P1 成立）
- [x] **E2** 发布后物理表新增 `remark2`、`remark` DROP
- [x] **E3** 改类型 `number → text` 发布后物理列变 `varchar(255)`
- [x] **E4** 导出 CSV / XLSX（表头/行数正确、XLSX 首字节 `50 4B 03 04`）
- [x] **E5** 设计器撤销/重做（删除 → `Ctrl+Z` 恢复 → `Ctrl+Y` 再删，按钮禁用态正确）
- [ ] **E6** 真实指针拖拽入画布复验（**自动化不可复现 → 代码复核通过，需人工复核**；详见报告）
- [x] **E7** 负向：导出缺 `definitionCode` → `PARAM_ERROR`；DROP 被引用列 → 护栏 `PARAM_ERROR`

**Wave 5.30-c（收尾 R7）**

- [x] **G1** 检查报告：`docs/reviews/2026-10-20-wave5.30-form-residual.md`
- [x] **G2** devlog：`docs/devlog/2026-10-20.md` 追加 Wave 5.30 段落
- [x] **G3** 台账勾选：`roadmap.md` §2.5 追加 Wave 5.30 行；`task-breakdown.md` 追加本分组
- [x] **G4** commit & push `origin`（Gitee `https://gitee.com/StrongToStop/blue-whale.git`）


## Wave 5.31：whale-workflow 引擎升级（状态机 + BPMN 2.0 双向 + JSON 事实源）

> 用户指令（2026-10-20）：「现在开始搭建工作流服务吧，包含一下三部分：简单的状态机，BPMN2.0，json格式的工作流数据。」
> 冻结口径（不得更改）：① **扩展现有 `whale-workflow`**（不新建模块、不推倒重来）；② **BPMN 轻量自研双向转换**（不引入 Flowable/Camunda）；③ **通用状态机内核 + 审批跑其上**；④ **本轮仅后端**（前端设计器/BPMN 可视化不在范围）。
> 主线：规范化 JSON `{type,nodes[],flows[]}` 为唯一事实源 → 状态机内核驱动审批 → BPMN 2.0 XML 双向转换；**向后兼容**旧 `{"nodes":[…]}`。
> 并行策略：S1/S2/S3 均为 `support/` 下新增包（无文件交集）可并行；S4（库表/种子）与 S1~S3 无交集可并行；S5 依赖 S1~S4；S6 依赖 S5。
> 规格：[wave5.31-workflow-engine/spec.md](wave5.31-workflow-engine/spec.md) ｜ 分解：[tasks.md](wave5.31-workflow-engine/tasks.md) ｜ 报告：[reviews/2026-10-20-wave5.31-workflow-engine.md](../reviews/2026-10-20-wave5.31-workflow-engine.md)。

**Wave 5.31-a（契约冻结 + 4 条并行线）**

- [x] **线 A · S1 模型层**：`support/` 新增 `ProcessNodeType`/`ProcessModelNode`/`ProcessModelFlow`/`ProcessModel`/`ProcessModelParser`/`ProcessModelValidator`/`ValidationCode`/`ValidationIssue`/`ValidationLevel`/`ValidationReport`/`ProcessModelException`
- [x] **线 B · S2 状态机内核**：`support/machine/` 新增 `StateMachine`/`StateMachineEvent`/`Transition`/`TransitionResult`/`Guard`/`GuardContext`/`AlwaysGuard`/`DefaultFlowGuard`（纯 Java 可独立单测）
- [x] **线 C · S3 BPMN 双向**：`support/bpmn/` 新增 `BpmnXmlSupport`（XXE 加固）/`BpmnXmlParser`/`BpmnXmlWriter`（纯 JDK DOM，无第三方库）
- [x] **线 D · S4 库表/实体/种子**：`process_instance.current_node_key VARCHAR(64) NULL` + `db/migration-5.31.sql` + `entity/ProcessInstance`/`vo/ProcessInstanceVO` 增字段 + `whale-auth/db/init.sql` 种子 58/59

**Wave 5.31-b（S5 服务接线）**

- [x] **E1** `service/ProcessModelService` + `impl`（`validate`/`importBpmn` 委托 `ProcessDefinitionService.create`/`exportBpmn`）
- [x] **E2** `controller/ProcessModelController`（3 端点，权限 58/59/`sys:workflow:list`）
- [x] **E3** `dto/{DefinitionValidateRequest,BpmnImportRequest}`、`vo/{ProcessDefinitionValidateVO,ValidationIssueVO}`
- [x] **E4** `ProcessInstanceServiceImpl#start` 改由内核驱动（legacy 语义零回归）
- [x] **E5** `ProcessTaskServiceImpl#approve` 改由内核驱动（`resolveCurrentKey`：优先 `currentNodeKey`，空则回落 `currentIndex`）

**Wave 5.31-c（S6/S7 测试与静态门禁）**

- [x] **F1–F6** 新增 7 个测试类（parser 14 / validator 21 / stateMachine 11 / bpmn 5+10+5 / serviceImpl 9）+ 既有实例/任务服务测试扩展（8→10、6→9）；whale-workflow 合计 **102 用例全绿**
- [x] **F7** `mvn -o -pl :whale-workflow -am test checkstyle:check` → **0 违规**（修 `ProcessModelParser` 无用导入 + `ProcessModelValidator` 两处圈复杂超阈值）

**Wave 5.31-d（全量回归与运行时冒烟）**

- [x] **G1** 停运行中服务释放 jar → `mvn -o -f backend/pom.xml clean install -T 1C` → **28/28 模块 BUILD SUCCESS** → 重启 whale-auth 8081 / whale-workflow 8083 / whale-gateway 8080
- [x] **G2** legacy 冒烟（定义→发起→同意→同意）：`currentNodeKey` `n1` → `n2` → `status=1`
- [x] **G3** 校验冒烟 18/18：LEGACY + NORMALIZED`[CONDITION_IGNORED]` + 16 个负向码（含 `XML_DOCTYPE_FORBIDDEN`/`BPMN_UNSUPPORTED_ELEMENT`）
- [x] **G4** BPMN 往返：导入 → 落规范化 JSON → 导出 657B XML → 再校验 `mode=BPMN valid=true nodes=4 flows=3`
- [x] **G5** 回落（`current_node_key=NULL` 同意成功推进至 `n2`）+ 负向信封（`404`/`400`/`401`/`403`）
- [x] **D5** 实机 DDL + 种子：`current_node_key varchar(64) NULL` 已存在；权限 58/59 已就位

**Wave 5.31-e（收尾）**

- [x] **H1** 检查报告：`docs/reviews/2026-10-20-wave5.31-workflow-engine.md`
- [x] **H2** devlog：`docs/devlog/2026-10-20.md` 追加 Wave 5.31 段落
- [x] **H3** 台账勾选：`roadmap.md` §2.5 追加 Wave 5.31 行；`task-breakdown.md` 追加本分组
- [x] **H4** commit & push `origin`（Gitee `https://gitee.com/StrongToStop/blue-whale.git`）


## Wave 5.32：whale-workflow-designer 工作流前端（管理 + 绘制 + 表单绑定）

> 用户指令（2026-10-20）：「后端已经完成了吗？现在开始前端？管理和绘制，表单可以选择表单管理器的表单。」
> 冻结口径（AskUserQuestion，4 项）：① 新建独立应用 `whale-workflow-designer`（8003）；② 自研自由画布（SVG + 复用 `@dnd-kit`，不引入 React Flow）；③ 四域（定义管理/发起填表单/待办审批/实例监控）；④ 允许后端最小可加性补强。
> 并行策略：Phase 1 三轨并行（B 后端补强 / S 共享渲染器 / F0 应用骨架，互无文件交集）；Phase 2 五路并行（F1 设计器 / F2 定义管理 / F3 发起 / F4 待办 / F5 实例，F3/F4 依赖 S+B）；Phase 3 串行收尾。
> 规格：[wave5.32-workflow-designer/spec.md](wave5.32-workflow-designer/spec.md) ｜ 分解：[tasks.md](wave5.32-workflow-designer/tasks.md) ｜ 报告：[reviews/2026-10-20-wave5.32-workflow-designer.md](../reviews/2026-10-20-wave5.32-workflow-designer.md)。

**Wave 5.32-a（契约冻结 + 三轨并行）**

- [x] **B 轨 · 后端可加性**：`ProcessModelNode` + `formId`/`formCode`/`x`/`y`；`ProcessModelParser.readNode` 读；`ProcessModelServiceImpl.toJson` 写；`ValidationCode` + 2 新码（`USER_TASK_FORM_CODE_INVALID`/`USER_TASK_FORM_INCOMPLETE`）；`BpmnXmlParser`/`BpmnXmlWriter` 对称 `formKey`/`formId`；`process_task` + `node_key`/`form_id`/`form_code` + 实体/VO + 建任务回填；幂等 `db/migration-5.32.sql`
- [x] **S 轨 · 共享渲染器**：`FormRenderer` 抽进 `elemental/whale-ui`（+ `WhaleFormFieldConfig`）；`whale-form-designer` 改 re-export；`tsup` 补 `external: dayjs`
- [x] **F0 轨 · 应用骨架**：`platform-apps/whale-workflow-designer`（8003 + 正则 proxy）+ `api/{token,http,auth,workflow,form}.ts` + 路由/布局/登录

**Wave 5.32-b（五路并行 · 前端功能）**

- [x] **F1 设计器**：`pages/designer/{WorkflowDesigner,Palette,Canvas,NodeShape,EdgePath,PropertyPanel,FormPicker}.tsx` + `model.ts`（SVG 画布 + dnd-kit + 连线 + 属性面板 + 表单选择 + 保存/校验 + BPMN）
- [x] **F2 定义管理**：`pages/definition/DefinitionList.tsx`
- [x] **F3 发起 + 填表单**：`pages/start/{ProcessStart.tsx,model.ts,formValues.ts}`
- [x] **F4 待办 + 审批**：`pages/task/TaskCenter.tsx`（表单只读回显）
- [x] **F5 实例监控**：`pages/instance/{InstanceList.tsx,InstanceDetailDrawer.tsx,statusTag.tsx}`

**Wave 5.32-c（收尾）**

- [x] **V1** 后端全量回归：`mvn -o -f backend/pom.xml clean install -T 1C` → **28/28 模块 BUILD SUCCESS**（271 测试无失败）
- [x] **V2** DDL 落库（`process_task` 三列 `varchar(64) YES`）+ 运行时冒烟 **15/15 PASS**
- [x] **V3** 前端全量构建：`pnpm -C frontend build` → **14/14 任务**
- [x] **V4** 浏览器实机 E2E：A/B/C/C2/C3/D/E/F/G **全 PASS**；**C4 拖拽手势自动化不可复现 → 需人工复核**
- [x] **V5** 文档收尾：检查报告 + devlog + roadmap §2.5 + task-breakdown 勾选
- [x] **V6** commit & push `origin`（Gitee `https://gitee.com/StrongToStop/blue-whale.git`）


## 附录 A：common 契约（Wave 2 遵守）

```
com.bluewhale.common.core
├── result/Result<T>        { code:int, message:String, data:T } + ok()/ok(data)/fail(code,msg)
├── result/PageResult<T>    { records:List<T>, total:long, size:long, current:long }
├── query/PageQuery         { current=1, size=10 } → 提供 toPage()
├── exception/ErrorCode     枚举：SUCCESS(0)/SYSTEM_ERROR(500)/UNAUTHORIZED(401)/FORBIDDEN(403)/NOT_FOUND(404)/PARAM_ERROR(400)
├── exception/BizException  extends RuntimeException { code, message }
└── handler/GlobalExceptionHandler  @RestControllerAdvice：BizException/MethodArgumentNotValid/兜底
com.bluewhale.common.mybatis
└── config/MybatisPlusConfig      分页插件 + MetaObjectHandler（createTime/updateTime 自动填充）
com.bluewhale.common.entity
└── BaseEntity            { id, createTime, updateTime, deleted(@TableLogic) }
```

- 端口约定：gateway 8080 / auth 8081 / job 8082 / workflow 8083 / form 8084 / file 8085 / pay 8086 / monitor 8087 / audit 8088
- 包名：`com.bluewhale.{module}`；模块间依赖统一 `${project.version}`

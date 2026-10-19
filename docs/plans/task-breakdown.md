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

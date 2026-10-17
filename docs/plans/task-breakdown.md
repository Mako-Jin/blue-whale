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

- [x] T0.1 定义 common 契约（见附录 A），写入本文档供 Wave 1/2 共同遵守
- [x] T0.2 环境检查：Docker 未安装 → Phase 1 容器启动推迟；DDL 改为脚本产出 + SQL 审查
- [x] T0.3 拆分文档提交推送（`8eddae1`）

## Wave 1：基础层（4 路并行）

- [x] T1.1 **whale-common 填充**
  - `common/core`：Result / PageResult / PageQuery / ErrorCode / GlobalException / GlobalExceptionHandler
  - `common/mybatis`：MyBatis-Plus 分页插件 + 字段自动填充 MetaObjectHandler 配置类
  - whale-tools：补充 AssertX / IdUtils 等实用工具
  - 验证：`mvn -pl whale-common/... compile` 通过
- [x] T1.2 **全服务 DDL 脚本**（`backend/<module>/src/main/resources/db/init.sql`）
  - whale-auth：sys_user / sys_role / sys_permission / sys_menu / sys_dept / sys_user_role / sys_role_permission / sys_user_permission(ABAC属性)
  - whale-i18n-sdk：sys_i18n_message
  - whale-audit：audit_log；whale-file：file_info；whale-job：job_info/job_log
  - 验证：语句审查 + 与实体字段一致
- [x] T1.3 **前端基座登录与布局**（whale-platform）
  - 登录页（mock 登录 API）+ 路由守卫 + MainLayout(Header/Sidebar/多标签) + 主题接入
  - 验证：`pnpm --filter @bluewhale/whale-platform build` 通过
- [x] T1.4 **运维脚本补全**：`infrastructure/scripts/` 增加 dev 启动校验脚本（检查 MySQL/Redis/Nacos 可达性），替代 Docker 缺位（check-env.ps1 / check-env.sh）

## Wave 2：核心服务（3 路并行，依赖 T1.1）

- [x] T2.1 **whale-auth 权限中心**（最大任务）
  - JWT：登录/刷新/注销，jjwt 0.12 API
  - Spring Security 过滤器链 + UserDetailsService
  - RBAC 六层级实体/Mapper/Service/Controller：用户、角色、菜单、权限点（接口/按钮）、部门、ABAC 属性
  - 内置数据：admin 用户 + 初始化角色
  - 验证：`mvn -pl whale-services/whale-auth compile` + 启动类加载测试
- [x] T2.2 **whale-gateway 网关**
  - Nacos 动态路由 + 白名单（auth 相关路径放行）
  - JWT 全局过滤器（解析 token 传递用户信息 Header）
  - 全局异常处理 + TraceId 响应头
  - 验证：`mvn -pl whale-services/whale-gateway compile`
- [x] T2.3 **whale-i18n-sdk 国际化**
  - 三级优先级：数据库 > 属性文件 > 默认值（MessageSource 扩展）
  - Redis 缓存 + `I18nDatabaseProvider` SPI 实现
  - 验证：`mvn -pl whale-sdk/whale-i18n-sdk compile` + 单测三级回退逻辑

## Wave 3：集成验证与收尾（串行）

- [x] T3.1 `mvn clean install` 全量构建，修复跨模块问题（25 模块 exit 0）
- [x] T3.2 `pnpm build` 前端全量构建（11/11 successful）
- [x] T3.3 更新 roadmap 勾选 + devlog 记录 + commit + push origin

## Wave 4：工程规范与分布式基础（2026-10-17，串并混合）

> 本轮新增需求见 [requirements/register.md](../requirements/register.md) 第二节（REQ-DOC/QA/CICD/SEC/DIST/ARCH 共 12 条）。
> 本轮执行原则：**先落文档 → 编码 → 按清单自检留报告 → 构建验证**。

- [x] T4.0 **工程规范体系**（REQ-DOC-001/002、REQ-QA-001/002）
  - `docs/requirements/register.md`：REQ 编号体系（8 域）+ 变更控制七步流程 + 四条门禁
  - `docs/checklists/code-review-checklist.md`：5 大类 57 项 + 严重级别 + 报告模板
  - `docs/reviews/`：每次变更一份检查报告（首份已归档）
  - 验证：清单可逐条对照；`docs/README.md` 约定章节同步更新
- [x] T4.0b **四份专项设计文档定稿**（REQ-SEC-001 / REQ-DIST-001~003 / REQ-ARCH-001 / REQ-CICD-001）
  - `docs/design/audit-log.md` / `distributed.md` / `ddd-whale-auth.md` / `cicd.md`
  - 验证：与真实代码与 DDL 核对一致（audit_log 字段已比对 init.sql）
- [x] T4.0c **CI/CD 代码质量门禁**（REQ-CICD-001）
  - `config/checkstyle/whale-checkstyle.xml`（C/D/E 类可自动化项）、`whale-parent/pom.xml` 插件管理、`.github/workflows/code-quality.yml`
  - 修正 `.github/workflows/frontend-ci.yml`：缺省工作目录 `frontend`
  - 验证：`mvn -pl backend/whale-common/whale-tools checkstyle:check`、`.../whale-auth checkstyle:check` 均 0 违规
  - 遗留：Gitee 托管需映射 Gitee Go；门禁当前为报告模式
- [x] T4.1 **分布式令牌黑名单**（REQ-SEC-002 / REQ-DIST-002）
  - 新增 `TokenBlacklist` / `RedisTokenBlacklist`；改造 `JwtAuthenticationFilter`、`AuthService#logout`、`AuthController#logout`、`JwtTokenProvider`、`whale-auth/pom.xml`
  - 验证：`mvn -pl backend/whale-services/whale-auth -am compile` → BUILD SUCCESS（6 模块 / 67 源文件）
  - 遗留：REQ-SEC-003（网关侧校验）、REQ-SEC-004（refreshToken 失效）
- [ ] T4.2 审计日志落地（`@AuditLog` + AOP + 异步写库 + 脱敏 + 7 个 ALTER 字段）
- [ ] T4.3 分布式锁工具（`@DistributedLock` + SpEL key + Redisson AOP）
- [ ] T4.4 auth DDD 迁移第 1 步（四层目录骨架 + 认证上下文试点，API 不中断）
- [ ] T4.5 全量回归（`mvn clean install -T 1C` + `pnpm build`）+ 提交推送

> Wave 5 详细任务清单（含依赖、优先级、验收口径）见 [next-steps.md](next-steps.md)。

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

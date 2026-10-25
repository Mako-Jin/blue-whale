# 需求登记册（Requirements Register）

> 用途：本项目所有需求（业务功能 + 工程规范）的统一台账。
> 规则：**任何需求在动手实现之前，必须先在此登记编号与验收标准**（见 REQ-DOC-002）。
> Wave 5.54：**多租户隔离体系与一人多租户切换**——新增登记 REQ-ARCH-006（多租户隔离，`sys_tenant` + RBAC 租户化 + 网关可信注入 + 租户行插件 + 业务域铺开）与 REQ-FUNC-016（S4 一人多租户切换：`sys_user_tenant`、`/auth/tenants`、`/auth/tenant/switch`、`/tenant/members`）。Phase 0–4 与 Phase 5.1/5.2 已完成并推送（`1bef439`），Phase 5.3 成员与角色管理、5.4 双租户 E2E 进行中；详见 [wave5.54 评估稿](../plans/wave5.54-multi-tenant/assessment.md) 与 [Phase 5.2 检查报告](../reviews/2026-10-25-wave5.54-phase5.2-tenant-switcher.md)。
> 最后更新：2026-10-25（**Wave 5.53 M3 档案检索与 admin 五页完成**：`whale-archive` 新增条目分页/详情/整理/确认/销毁/下载/统计 7 端点 + 审计，沉淀 `CategoryPathResolver` 与 `UserContext`；`frontend/admin` 新增 `api/archive.ts` 与门类/规则/待整理/检索/同步五页，注册 `archive/*` 组件契约键与菜单 11–16；archive **39 用例**全绿 + Checkstyle 0，admin `tsc + vite build`、`eslint --max-warnings 0` 通过；MySQL8 已执行建库/7 表 DDL/权限种子（8.0.45；菜单 11–16 重跑并核验 6 行）；**A13 真机联调已通过**（直连 workflow/form/file/archive：审批办结→可见 28 秒、档号 `BW-A13TEST-2026-000001/000002/000003` 唯一、下载 200 + RFC 5987 + `nosniff`、无权限码/错码 `code=403`、`file_info.status=2`、`audit_log` 7 行；archive **39 用例**全绿 + Checkstyle 0）；REQ-FUNC-014 / REQ-SEC-008 仍为实现中（M4–M5 待做）；详见 [wave5.53 规划](../plans/wave5.53-archive-file-knowledge.md) 与 [M3 检查报告](../reviews/2026-10-25-wave5.53-m3-archive-search-admin.md)；同日前序登记 REQ-FUNC-011/012/013、REQ-SEC-007 见 §四） ｜ 维护：BlueWhale
> Wave 5.16：**审计服务文档收尾（REQ-DOC-002 / REQ-QA-002，REQ-SEC-001/006 文档整合）**——① `docs/whale-audit/originals/` 归置审计相关原始文档副本 **26 份**（design 1 + scheme 1 + specs 5 目录 15 + reviews 6 + devlog 3；**原位文件不删不动**）；② `docs/whale-audit/final/README.md` 整合最终版（八节：需求与范围 / 能力矩阵 / 接口与权限点清单〔2 控制器 9 端点〕/ 关键配置项 / 表结构〔5 表〕/ 已闭环与残余阻塞 / 原文索引 / 版本记录），以原位最新文件与源码为准。**本波零代码变更**。详见 [reviews/2026-10-18-wave5.16-audit-service-finalization.md](../reviews/2026-10-18-wave5.16-audit-service-finalization.md) 与 [whale-audit/final/README.md](../whale-audit/final/README.md)。
> Wave 5.15：**审计服务 ①② 通道查询补齐（REQ-SEC-006）**——为 `api_log`（通道①）/`outbound_log`（通道②）补齐与 ③④ 对称的分页查询端点 `GET /audit/log/api/page`、`GET /audit/log/outbound/page`（`@RequirePermission("sys:audit:log:list")`，返回 `Result<PageResult<VO>>`）；新增 DTO（继承 `PageQuery`，时间范围 `@DateTimeFormat`）+ VO（`Serializable`，字段与实体一一对应）+ Service/Impl（`size` 收敛 `[1,200]`、`create_time` 倒序、等值条件带「是否提供」开关、时间 `ge/le`，命中既有索引）；四通道「写—查」对称达成。门禁：`-pl :whale-audit -am test` **46 用例全绿**（含不回归）、`checkstyle:check` **28 模块 0 违规**。详见 [reviews/2026-10-18-wave5.15-audit-channel-query.md](../reviews/2026-10-18-wave5.15-audit-channel-query.md)。
> Wave 5.14：**前端日志 SDK（Wave B，REQ-SEC-006 通道③④）**——新增共享包 `@bluewhale/whale-log-sdk`（`frontend/shared-packages/whale-log-sdk`，v0.1.0，**零运行时依赖**）：console 劫持采集（保留原输出 + 可还原）+ 行为埋点（`PAGE_VIEW`/`CLICK`/`SUBMIT`）+ 脱敏（递归敏感键 + URL query，键与后端 `SensitiveDataMasker` 对齐）+ 批量通道（采样/去重/满批或定时 flush/**分片 ≤50**/指数退避）+ `fetch` 上报（`X-Trace-Id`）；`whale-platform` 基座接入 `initLogSdk()`。**不改后端**（契约沿用 Wave 5.10 冻结接口）。门禁：tsup **三件产物**（cjs/esm/dts）+ 无依赖冒烟 **30 断言全过** + 前端 `pnpm build` **12/12** + `eslint` **0 error** + `prettier` **0 差异**。详见 [reviews/2026-10-18-wave5.14-whale-log-sdk.md](../reviews/2026-10-18-wave5.14-whale-log-sdk.md)。
> Wave 5.12 + 5.13：**审计后端残余收口**——`api_log.module` 回填（默认取 `spring.application.name`）+ 网关**按路径**限流（上报路径独立阈值）+ 保留期**扩 4 表**（`whale.audit.retention`，默认关闭，**不清理 `audit_log`**）；**AUTH-39 refreshToken 一次性轮换实施**（REQ-SEC-004，ADR-001 方案 B）——`SET NX` 原子消费 + 旧 token 入登出黑名单 + 复用检测整族吊销 + 降级告警，feature flag `whale.auth.refresh.rotation.enabled` 默认 `false`（fail-closed，关闭态逐字节回归）。门禁：`clean verify` **292 用例全绿**、`checkstyle:check` **28 模块 0 违规**。详见 [reviews/2026-10-18-wave5.12-audit-backend-polish.md](../reviews/2026-10-18-wave5.12-audit-backend-polish.md) 与 [reviews/2026-10-18-wave5.13-auth39-refresh-rotation.md](../reviews/2026-10-18-wave5.13-auth39-refresh-rotation.md)。
> Wave 5.10：**审计日志多通道完善（后端 Wave A，REQ-SEC-006）**——4 张 append-only 独立表（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`）随服务库部署；`LogPersistSupport` + 独立 `whaleLogExecutor`；`ApiLogFilter`（tee 透传自研包装器）+ 出站 `OutboundLogInterceptor`（`SensitiveDataMasker#maskJson`）；`whale-audit` 上报/查询接口（`LogIngestController`，`items` 上限 50）；AUTH-06 权限码头（网关注入 + starter 通用拦截器，权限点种子 `sys:audit:log:list`）+ AUTH-11 脱敏规则配置化。门禁：`clean verify` **247 用例全绿**、`checkstyle:check` **29 模块 0 违规**、实时 IT **7/0/0**。详见 [reviews/2026-10-18-wave5.10-multichannel-log.md](../reviews/2026-10-18-wave5.10-multichannel-log.md)。
> Wave 5.6：**横切基础设施落地**（[specs/2026-10-18-wave5.6-crosscutting-infra/spec.md](../specs/2026-10-18-wave5.6-crosscutting-infra/spec.md)）——T5.3/AUTH-18 幂等组件 `@Idempotent`（Redisson SETNX + 结果缓存）、T5.5/AUTH-20 缓存一致性工具（Cache-Aside + 空哨兵 + TTL 抖动 + 延迟双删）、T5.4/AUTH-30 网关限流（Redis 计数器兜底 + 429/Result）、T5.18/AUTH-24 JaCoCo 覆盖率接入（`coverage-gate` profile）、T5.16/AUTH-28 Gitee Go 流水线映射。
> Wave 5.5：**全后端 Javadoc 强制（REQ-QA-003）**——Checkstyle 新增 `MissingJavadocType` / `MissingJavadocMethod`（`scope=private`）/ `JavadocType`（`@author`）/ `NonEmptyAtclauseDescription` 等规则并置 `error`；`backend/**` 全部 21 模块的每个类型与每个方法（含 `private`、构造器）补齐标准中文 Javadoc，全 reactor `checkstyle:check` **0 违规**。详见 [reviews/2026-10-17-wave5.5-javadoc-enforcement.md](../reviews/2026-10-17-wave5.5-javadoc-enforcement.md)。
> Wave 5.4：**质量门禁提级与关键横切类单测补齐**——Checkstyle 默认 `severity` 由 `warning` 提升为 `error`（AUTH-25，违规真正阻断构建；全 reactor `checkstyle:check` 0 违规）；为 `PermissionInterceptor`/`JwtAuthenticationFilter`/`UserDetailsServiceImpl`/`SecretPlaceholderChecker` 新增 4 类 21 用例（AUTH-23），`whale-auth` 单测 34 → 55 全绿。详见 [reviews/2026-10-17-wave5.4-quality-gate-and-tests.md](../reviews/2026-10-17-wave5.4-quality-gate-and-tests.md)。
> Wave 5.3：权限服务（whale-auth）**审计与账号操作收口**——写操作审计埋点 **5 → 20 处**（AUTH-12）、新增改密 `PUT /user/{id}/password` 与启停 `PUT /user/{id}/status`（AUTH-22，含权限码/审计/值对象/单测）、网关 **jjwt 版本统一为 0.12.5**（AUTH-02）、refreshToken 轮换 **[ADR-001](../design/adr/2026-10-17-refresh-token-rotation.md) 定稿**（AUTH-03，实施另立 AUTH-39）、补齐 auth 管理前缀网关路由冒烟通过（AUTH-38）。39 单测全绿 + Checkstyle 0 违规。
> Wave 5.2：权限服务（whale-auth）**P1 收口**——补齐 **31 处 `@RequirePermission`**（3 → 31）+ 22 条权限点种子（AUTH-04）、新增 `GET /auth/me`（AUTH-21）、补齐 auth 管理前缀网关路由（AUTH-38）、DDD 四层骨架 + 4 值对象（AUTH-14 ↔ T5.13）、Checkstyle 0 WARN（AUTH-26）。30 单测全绿。
> Wave 5.1i：发布**全平台服务与端口清单**（[service-catalog.md](../service-catalog.md)，新增登记 **REQ-ARCH-005**）——15 个后端服务、6 个前端应用、8 类第三方组件的端口 ↔ 服务 ↔ 库 ↔ 对外前缀全量映射，并登记 7 项偏差（前端 dev 端口与后端冲突 C-01、网关缺 6 条路由 C-03、8 服务未接数据源 C-06 等）。
> Wave 5.1h：确立「**完成即划线**」文档约定（[docs/README.md](../README.md) 约定第 7 条）——待办台账与蓝图文档中已完成任务一律加删除线；已落实到 backlog / next-steps / roadmap / task-breakdown 共 74 处。
> Wave 5.1g：发布**统一命名规范**（[naming-conventions.md](../naming-conventions.md)，新增登记 **REQ-ARCH-004**）；`nacos` 库按同一前缀整改为 **`bluewhale_nacos`**（Nacos 3.2.4 停服 → 迁表 → 改 `conf/application.properties` → 重启验证，8848/8849 就绪、服务注册与 `lb://` 链路复测通过）；命名符合性纳入检查清单 **E15**（清单由 57 项扩为 58 项）。
> Wave 5.1f：**中间件本机原生进程落地**（Nacos 3.2.4 切 MySQL 外置存储 + Redis 悬空文件归位重启 → `REQ-FUNC-001` 由「已阻塞：本机无 Docker」改为**已就绪**），长期阻塞的 `lb://` 全链路首次打通；`AUTH-29` 网关路由前缀冲突已修复并**新增登记 REQ-ARCH-003**（服务自持一级前缀、网关不剥离）；经网关实测 login `code=0` → logout `code=0` → 重放 401（REQ-SEC-003）→ refresh 业务码 401（REQ-SEC-004）→ `audit_log` LOGIN/LOGOUT 落库（REQ-SEC-001）。详见 [reviews/2026-10-17-wave5.1f-gateway-routing-and-e2e.md](../reviews/2026-10-17-wave5.1f-gateway-routing-and-e2e.md)。
> Wave 5.1d：**服务首次真实启动成功**（`redisson 4.8.0` 与 Boot 4.0.8 实测兼容，P0 关闭；MyBatis-Plus 升 3.5.17 并迁移到 `mybatis-plus-spring-boot4-starter`）；whale-auth 8081 / whale-audit 9101 均可启动。
> Wave 5.1e：审计切面装配缺陷（`@ConditionalOnBean(Mapper)` 时序不可靠）已修复，`audit_log` **0 → 6 行**真实落库、`/audit/log/page` 可见记录；新增登记 **REQ-ARCH-002 数据库可切换适配（含国产化要求）**，当前及近期仍统一使用 MySQL。详见 [reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md) 与 [reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md](../reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md)。

---

## 一、编号规则

`REQ-<域>-<三位序号>`

| 域代码 | 含义 | 说明 |
|---|---|---|
| `FUNC` | 业务功能 | 面向用户的业务能力（ERP/MES/PDM/OA/门户等） |
| `DOC` | 文档与流程 | 文档体系、变更控制、交付规范 |
| `QA` | 质量与检查 | 代码检查、规范、度量 |
| `SEC` | 安全 | 认证、授权、审计、脱敏 |
| `DIST` | 分布式 | 分布式 ID、锁、事务、幂等等 |
| `ARCH` | 架构 | 分层、限界上下文、模块边界 |
| `CICD` | 交付流水线 | 构建、测试、制品、部署 |
| `INFRA` | 基础设施 | 中间件、容器、环境 |

**状态取值**：`待启动` → `设计已定稿` → `实现中` → `待验证` → `已完成` → `已阻塞` / `已取消`

---

## 二、本轮新增需求（2026-10-17 提出）

| 编号 | 需求描述 | 来源 | 优先级 | 状态 | 落点（文档 / 代码） | 验收标准 |
|---|---|---|---|---|---|---|
| REQ-DOC-001 | 每一步任务完成后必须生成文档，覆盖：需求项、完成项、待做项、已修正问题项等 | 用户指令 1 | 高 | 已完成 | [devlog/2026-10-17.md](../devlog/2026-10-17.md) + 本登记册 + [task-breakdown.md](../plans/task-breakdown.md) | 每个任务条目同时具备「需求项 / 完成项 / 待做项 / 已修正问题」四类信息，可逐条追溯 |
| REQ-QA-001 | 制定代码规范检查清单，覆盖：协议漏洞、代码漏洞、圈复杂度、魔法值、命名与格式等 | 用户指令 2 | 高 | 已完成 | [checklists/code-review-checklist.md](../checklists/code-review-checklist.md) | 清单含 5 大类检查项，每项给出判定标准与检查方式 |
| REQ-QA-002 | 每一次新增/变更的代码都必须按上述清单执行检查并留存报告 | 用户指令 2 | 高 | 实现中 | [checklists/code-review-checklist.md](../checklists/code-review-checklist.md) + [reviews/](../reviews/) + [reviews/2026-10-17-wave5.1b-audit-log.md](../reviews/2026-10-17-wave5.1b-audit-log.md) | 每一批代码变更都有对应的检查报告文件，且报告逐项对应清单条目 |
| REQ-QA-003 | **全后端 Javadoc 强制**：`backend/**` 每个类型（类 / 接口 / 枚举 / 记录）与**每个方法（含 `private`、构造器）**都必须按标准 Java 注释规范书写 Javadoc（`@param` / `@return` / `@throws` 齐全、中文描述）；并纳入 Checkstyle 门禁（违规即构建失败） | 用户指令 14（Wave 5.5） | 高 | **已完成**（155 → 0，全 reactor 0 违规） | [checklists/code-review-checklist.md](../checklists/code-review-checklist.md) E5/E16 + [config/checkstyle/whale-checkstyle.xml](../../config/checkstyle/whale-checkstyle.xml) + [reviews/2026-10-17-wave5.5-javadoc-enforcement.md](../reviews/2026-10-17-wave5.5-javadoc-enforcement.md) | ① Checkstyle 新增 `MissingJavadocType` / `MissingJavadocMethod`（`scope=private`）/ `JavadocType`（要求 `@author`）/ `NonEmptyAtclauseDescription` 等规则并置 `error`；② 全 reactor `mvn -f backend/pom.xml checkstyle:check` **0 违规（28/28 模块 BUILD SUCCESS）**；③ 每个类型与每个方法均有 Javadoc，且 `@param`/`@return`/`@throws` 描述非空（80 文件 / +1070 行） |
| REQ-CICD-001 | 搭建 CI/CD 流水线（含代码质量门禁） | 用户指令 3 | 高 | 实现中（**Checkstyle 门禁已提级 `error`**：AUTH-25/Wave 5.4；**Wave 5.6**：JaCoCo 覆盖率接入 + Gitee Go 流水线映射） | [design/cicd.md](../design/cicd.md) + [specs/2026-10-18-wave5.6-crosscutting-infra/spec.md](../specs/2026-10-18-wave5.6-crosscutting-infra/spec.md) + `.github/workflows/` + `.workflow/` | 流水线覆盖 提交→检查→构建→测试→制品→镜像→部署；质量门禁可产出报告；**Wave 5.6 已落地**：JaCoCo 报告产出 + `coverage-gate` profile 阈值校验（行 ≥40% / 分支 ≥30%）、`.workflow/` Gitee Go 流水线（Gitee 侧触发待验证） |
| REQ-SEC-001 | 设计审计日志能力（记录谁、何时、对什么、做了什么、结果） | 用户指令 4 | 高 | 已完成（四通道采集/查询 + 元审计 + 导出 + 归档 + 保留期闭环，Wave 5.10–5.21；写操作埋点 20 处，Wave 5.3；仅阶段二 **MQ 汇聚**因本机无 RabbitMQ 保持 backlog，AUTH-10） | [design/audit-log.md](../design/audit-log.md) + [whale-spring-boot-starter](../../backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/audit/)（`common/audit/*`）+ [whale-audit/db/init.sql](../../backend/whale-ops/whale-audit/src/main/resources/db/init.sql) + whale-audit 查询接口 | 审计表字段（T5.9 已落地 7 字段）与设计一致；注解/切面/脱敏/查询接口已落地（40 单测全绿）；auth 登录/登出/创建用户/分配角色/分配权限已埋点；Wave 5.1c 实机验证：成功/异常两路径各落 1 行、密码为掩码、`page()` 条件过滤 / `size` 收敛 200 / `create_time` 倒序均通过；Wave 5.3 覆盖 [audit-log.md](../design/audit-log.md) §2 全部敏感写操作 |
| REQ-DIST-001 | 分布式 ID 方案（全局唯一、趋势递增、无 JS 精度丢失） | 用户指令 5 | 中 | 实现中 | [design/distributed.md](../design/distributed.md) + whale-spring-boot-starter | 方案定稿；T5.1 已落地 Long→String 序列化 + IdUtils 注释澄清 |
| REQ-DIST-002 | 分布式锁方案（可重入、看门狗续期、锁命名规范、可复用工具） | 用户指令 5 | 中 | 实现中（**运行时已验证**：Wave 5.1c 真实 Redis + Redisson） | [design/distributed.md](../design/distributed.md) + whale-spring-boot-starter | T5.2 已落地 `@DistributedLock` 注解 + AOP 切面，场景接入待做；Wave 5.1c 实机验证：同 key 并发第二个被拒（429）、串行与释放后可再获取 |
| REQ-DIST-003 | 其他分布式问题（幂等、缓存一致性、分布式事务、限流熔断、链路追踪、分布式会话） | 用户指令 5 | 中 | 实现中（**Wave 5.6**：幂等组件 T5.3、缓存一致性工具 T5.5、网关限流 T5.4 落地；分布式事务 / 链路追踪未落地） | [design/distributed.md](../design/distributed.md) + [specs/2026-10-18-wave5.6-crosscutting-infra/spec.md](../specs/2026-10-18-wave5.6-crosscutting-infra/spec.md) | 每项含备选方案对比表与推荐结论；**Wave 5.6 已落地**：`@Idempotent`（SETNX + 结果缓存）、Cache-Aside 工具（空哨兵 + TTL 抖动 + 延迟双删）、网关限流（Redis 计数器兜底 + 429/Result） |
| REQ-DOC-002 | 所有要求先落到文档，每一步都需遵守（文档先行 / 变更控制） | 用户指令 6 | 高 | 已完成 | 本登记册 + [docs/README.md](../README.md) 约定章节 | 未登记编号、未定稿设计的变更不得进入编码阶段 |
| REQ-DOC-003 | **「完成即划线」文档约定**：待办台账（backlog / next-steps）与蓝图（roadmap / task-breakdown）中，已完成任务一律加删除线 `~~…~~`，并保留 `[x]` / `✅` 状态标记与完成结论可读 | 用户指令 13（Wave 5.1h） | 中 | 已完成 | [docs/README.md](../README.md) 文档约定第 7 条 + `docs/plans/` 四份文档 | ① 约定写入 README 并作为后续维护规则；② 已落实 backlog 6 处、next-steps 12 处、roadmap 30 行、task-breakdown 26 行；③ 各波次收尾时对新完成任务同形态划线 |
| REQ-ARCH-001 | 权限服务（whale-auth）改造为 DDD 分层架构 | 用户指令 7 | 高 | 推进中（Wave 5.2 第 1 步落地：四层骨架 + 4 值对象 + 契约冻结清单） | [design/ddd-whale-auth.md](../design/ddd-whale-auth.md) §7/§10 | 四层架构、限界上下文、聚合设计、渐进迁移路线与回滚方案齐备；**Wave 5.2 已建 `domain/application/interfaces/infrastructure` 骨架与 `Username`/`EncryptedPassword`/`RoleCode`/`PermCode` 值对象**（AUTH-14 ↔ T5.13），后续 AUTH-15/16/17 渐进迁移 |
| REQ-ARCH-002 | 数据库可切换适配：按客户要求切换不同数据库，含**国产化要求**（达梦 DM / 人大金仓 KingBase / openGauss / OceanBase 等） | 用户指令 10 | 中 | **设计已定稿（2026-10-25，未实施）**；当前及近期仍统一使用 MySQL | [design/multi-database.md](../design/multi-database.md)（现状基线 + 方言选择 + DDL 映射 + T1–T6 清单 + 风险） | ① 持久层方言可切换：MyBatis-Plus `DbType` + `PaginationInnerInterceptor` 按库选型，不写死 MySQL；② DDL 版本化并在未来按数据库类型分发（Flyway/Liquibase 或等价方案），替换现有 `init.sql` 直连执行；③ 去 MySQL 专有依赖：`ENGINE=InnoDB`、`utf8mb4_general_ci`、`ON UPDATE CURRENT_TIMESTAMP`、`TINYINT`、JDBC 参数 `characterEncoding/serverTimezone` 等；④ 产出「本仓现状与换库改造清单 + 风险」文档。**本轮不实现，仅登记，避免阻塞 Wave 5.1d** |
| REQ-ARCH-003 | 网关路由与前缀约定：**服务自持对外一级路径前缀，网关不剥离前缀** | Wave 5.1f 网关联调实测（AUTH-29 根因） | 高 | 设计已定稿（Wave 5.1f） | [architecture.md §网关路由与前缀约定](../architecture.md) + `whale-gateway/application.yml` | ① 服务控制器路径即对外路径（auth `/auth/login`、audit `/audit/log/page`），网关路由**不得**配置 `StripPrefix`；② 网关 JWT 白名单与服务自身 Spring Security 白名单按同一对外路径书写，保证端到端一致；③ 经网关 `8080` 访问 `/auth/login` 返回业务码 0，`/auth/refresh` 业务码 401（登出后） |
| REQ-ARCH-004 | 统一命名规范：**所有库使用同一前缀**（Nacos 库与业务库一致）、服务 / 模块 / Java 包 / 配置 / Redis key 命名自解释（看名字即知属于哪一部分、做什么） | 用户指令 11（Wave 5.1g） | 高 | 实现中（规范已定稿并落地；存量偏差 N-02/N-03/N-04 待整改） | [naming-conventions.md](../naming-conventions.md) + [architecture.md §命名规范](../architecture.md) + [checklists E15](../checklists/code-review-checklist.md) + `tools/nacos/conf/application.properties` | ① 库名统一 `bluewhale_<域>`，**基础设施同前缀**（Nacos 为 `bluewhale_nacos`，不留裸 `nacos`）；② 服务名满足 `模块目录 = artifactId = spring.application.name = Nacos 服务名`；③ Java 包 `com.bluewhale.<域>[.<层>]`；④ Redis key `whale:<域>:<用途>:<标识>`；⑤ 新增库/服务/包在规范 §八 登记，检查清单 E15 通过 |
| REQ-ARCH-005 | **服务与端口清单**：全平台「端口 ↔ 服务」详细映射 + 服务信息（分组 / 模块 / 启动类 / 对外前缀 / 网关路由 / 库 / 职责 / 状态）+ **第三方组件端口**（MySQL / Redis / Nacos / RabbitMQ / Nginx / Prometheus / Grafana / Sentinel） | 用户指令 12（Wave 5.1i） | 高 | 已完成（清单发布，偏差 C-01~C-07 待整改） | [service-catalog.md](../service-catalog.md) | ① 15 个后端服务逐一给出端口、对外一级前缀、网关路由与库；② 6 个前端应用开发端口与 base 路径；③ 8 类第三方/基础设施组件端口、版本、部署方式与本机状态；④ 端口升序速查表 + 端口分段规划；⑤ 环境变量覆盖表；⑥ 端口/路由/数据源/版本偏差登记（C-01~C-07） |
| REQ-PROC-001 | 按既有波次计划持续推进下一步任务 | 用户指令 8 | 高 | 实现中 | [task-breakdown.md](../plans/task-breakdown.md) + [next-steps.md](../plans/next-steps.md) Wave 5.1b | Wave 5.0 任务逐项闭环，含构建与单测验证 |
| REQ-PROC-002 | 每轮结束输出下一步建议与任务清单 | 用户指令 9 | 中 | 已完成 | [plans/next-steps.md](../plans/next-steps.md) | 清单含任务、依赖、优先级、验收口径 |

---

## 三、本轮实现的支撑需求（派生）

| 编号 | 需求描述 | 来源 | 优先级 | 状态 | 落点 | 验收标准 |
|---|---|---|---|---|---|---|
| REQ-SEC-002 | 登出（logout）令牌失效：接入分布式黑名单，使已登出 token 立即不可用 | REQ-DIST-002 + Wave 4 T4.1 | 高 | **已实机验证（Wave 5.1f）**（Redis 侧：摘要键 `auth:token:blacklist:<sha256>` + TTL>0 + 跨实例共享 + 过期不写入；**HTTP 端到端经网关通过**：登出后原 accessToken 重放 → 401） | `whale-auth` `security/TokenBlacklist` + `JwtAuthenticationFilter` + `AuthService#logout` | 登出后原 accessToken 再访问受保护接口返回 401；Redis 不可用时降级不阻断登录 |
| REQ-SEC-003 | 令牌黑名单的网关侧校验 | REQ-SEC-002 延伸 | 中 | **已实机验证（Wave 5.1f）** | `whale-gateway` `JwtAuthGlobalFilter` + `security/TokenBlacklistChecker` | 网关层命中黑名单直接 401，避免跨服务漏放行；Redis 不可用按未命中降级。**实测**：经网关登出后重放原 accessToken → **HTTP 401 `{"code":401,"message":"未认证"}`**（Nacos 阻塞已解除） |
| REQ-SEC-004 | refreshToken 一并失效：令牌带 `jti`，登出时 access/refresh 双写黑名单，`/auth/refresh` 先校验黑名单 | REQ-SEC-002 延伸 | **高（P1）** | **已实机验证（Wave 5.1f）**（Redis 双写黑名单：access/refresh 均写入且跨实例命中；**HTTP 端到端经网关**：登出后 refreshToken → HTTP 200 + 业务码 401 `refreshToken 已失效`）；**轮换已实施（Wave 5.13，AUTH-39，[ADR-001](../design/adr/2026-10-17-refresh-token-rotation.md) 方案 B）**：`SET NX` 原子消费 + 旧 token 入黑名单 + 复用检测整族吊销，默认关闭 `whale.auth.refresh.rotation.enabled=false`；单测全绿（292 用例），运行时端到端待中间件就绪 | `whale-security` `token/TokenBlacklistKeys` + `whale-auth` `jwt/JwtTokenProvider` + `service/AuthService` + `security/{RefreshTokenRotationStore,RedisRefreshTokenRotationStore}` + `config/RefreshRotationProperties` | 登出后原 refreshToken 调 `/auth/refresh` 返回 401；双令牌均写入同一 Redis 黑名单；**轮换开启后**：同一 refreshToken 二次使用 → 401 且该 family 全部 refreshToken 吊销 |
| REQ-SEC-005 | 生产密钥强制校验：非 dev 环境启动时若 JWT 签名密钥仍为默认占位值则拒绝启动 | 用户指令 5 + Wave 5 T5.8 | 中 | 实现中（**Wave 5.11：网关侧同步校验落地（AUTH-01）**） | `whale-auth` `config/JwtProperties` + `config/SecretPlaceholderChecker`；`whale-gateway` `config/GatewaySecretPlaceholderChecker` + `config/WhaleGatewayProperties` | 非 dev profile 下密钥为占位值时应用启动失败并输出明确告警；dev 环境不受影响。**Wave 5.11**：网关侧 `GatewaySecretPlaceholderChecker`（`@Component` + `InitializingBean`，非 dev 且密钥空/占位 → `IllegalStateException`）**+ 3 单测**；**运行时未验证**：仍需一次非 dev profile 启动实测 |
| REQ-SEC-006 | **多通道日志采集**：补齐①后端接口日志、②后端出站三方调用日志、③前端开发者 console 日志上报、④前端用户行为埋点四类通道，统一「异步 sink → 本地库 → 查询接口」，**不污染 `audit_log` 语义** | 用户指令（Wave 5.10） | 高 | **已完成（Wave A 后端 + Wave B 前端 SDK + Wave 5.15 四通道查询对称）**（Wave 5.10：接口日志 + 出站日志 + 上报/查询接口 + AUTH-06 权限码头落地；**Wave 5.12 残余收口**：`api_log.module` 回填（默认取服务名）+ 网关按路径限流 + 保留期扩 4 表；**Wave 5.14 前端 SDK**：`@bluewhale/whale-log-sdk` 交付通道③④（console 劫持 + 行为埋点 + 脱敏 + 分片批量上报 + 指数退避），`whale-platform` 接入；**Wave 5.15 查询补齐**：`GET /audit/log/api/page`、`GET /audit/log/outbound/page` 补齐通道①②，四通道「写—查」对称；**证据**：[reviews/2026-10-18-wave5.10-multichannel-log.md](../reviews/2026-10-18-wave5.10-multichannel-log.md) + [reviews/2026-10-18-wave5.12-audit-backend-polish.md](../reviews/2026-10-18-wave5.12-audit-backend-polish.md) + [reviews/2026-10-18-wave5.14-whale-log-sdk.md](../reviews/2026-10-18-wave5.14-whale-log-sdk.md) + [reviews/2026-10-18-wave5.15-audit-channel-query.md](../reviews/2026-10-18-wave5.15-audit-channel-query.md) + 后端构建 **292 用例全绿**（Wave 5.15 定向 **46 用例全绿**）+ checkstyle **28 模块 0 违规** + 实时 IT **7/0/0** + 前端 tsup **三件产物** + 冒烟 **30 断言全过** + `pnpm build` **12/12** + 4 表 DDL 落地） | [审计日志服务多通道完善方案](../../.trae/documents/审计日志服务多通道完善方案.md) + [specs/2026-10-18-wave5.10-multichannel-log/spec.md](../specs/2026-10-18-wave5.10-multichannel-log/spec.md) + [specs/2026-10-18-wave5.12-audit-backend-polish/spec.md](../specs/2026-10-18-wave5.12-audit-backend-polish/spec.md) + [specs/2026-10-18-wave5.14-whale-log-sdk/spec.md](../specs/2026-10-18-wave5.14-whale-log-sdk/spec.md) + [reviews/2026-10-18-wave5.12-audit-backend-polish.md](../reviews/2026-10-18-wave5.12-audit-backend-polish.md) + [reviews/2026-10-18-wave5.14-whale-log-sdk.md](../reviews/2026-10-18-wave5.14-whale-log-sdk.md) + `whale-spring-boot-starter`（`common/log/*`）+ `whale-gateway` `RateLimitGlobalFilter` + `whale-audit` `LogIngestController` + `retention/*` + `frontend/shared-packages/whale-log-sdk` + 4 张独立表 | ① 4 张 append-only 独立表（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`）随服务库部署，字段索引与 spec §3.1 一致；② `ApiLogFilter` 采集入参/出参/耗时/结果码/TraceId + `module`，响应体 tee 透传不丢、排除路径防回环；③ `OutboundLogInterceptor` 经 `RestTemplateBuilder` 自动挂载，traceId 透传、异常记 `biz_status=0`；④ 上报/查询接口（`permitAll` + `@RequirePermission("sys:audit:log:list")`）契约冻结，`items` 上限 50，上报路径独立限流；⑤ `whale.audit.sink=local\|mq` 预留并回落 local；⑥ 保留期 `whale.audit.retention`（默认关闭）只清 4 表、**不动 `audit_log`**；⑦ 实时 IT 断言 `api_log` 无明文凭据、上报记录可查；⑧ 前端 SDK 通道③④ 经构建产物冒烟验证（掩码/去重/分片/退避/console 采集全过） |

---

## 四、Wave 5.52 新增需求（WebIM + LangChain 智能体，2026-10-24 登记）

| 编号 | 需求描述 | 来源 | 优先级 | 状态 | 落点 | 验收标准 |
|---|---|---|---|---|---|---|
| REQ-FUNC-011 | **自研 WebIM**：1:1 / 群聊 / 机器人会话，消息历史与游标分页、未读/已读、在线状态、图片与文件附件；会话与消息数据落 `bluewhale_im` | 用户指令（Wave 5.52） | 高 | 设计已定稿（[wave5.52 规划](../plans/wave5.52-webim-langchain-agent.md) §五） | `backend/whale-services/whale-im`（8091，`/im/**`、`/im/ws`）+ `frontend/platform-apps/whale-im`（8107，base `/im`） | ① 5 表（`im_conversation`/`im_conversation_member`/`im_message`/`im_agent_binding`/`im_agent_run`）DDL 落地且索引与规划 §5.1 一致；② REST：会话列表/建会话/历史消息（`beforeSeq` 游标）/发消息/已读上报，`clientMsgId` 唯一索引保证重发幂等；③ WS：`POST /im/ws-ticket` 换 60s 一次性 ticket，**WS URL 不含 JWT**，无 ticket 拒绝升级；④ 断线按 `lastSeq` 补拉后消息完整且顺序正确；⑤ 附件经 `whale-file` 上传只存引用；⑥ 前端 build/lint EXIT=0，`console` 统一走 `createLogger` |
| REQ-FUNC-012 | **LangChain 智能体**：回答平台架构 / 技术实现 / 数据口径问题，流式输出，引用可回溯；IM 单聊与群内 `@蓝鲸助手` 均可触发 | 用户指令（Wave 5.52） | 高 | 设计已定稿（[wave5.52 规划](../plans/wave5.52-webim-langchain-agent.md) §六） | `ai/whale-ai`（Python FastAPI + LangChain/LangGraph，8092 仅内网）+ `whale-agent`（8090，引擎开关 `whale.agent.qa.engine=legacy\|langchain`） | ① Python 服务**不持业务库凭据**，检索/事实/文档工具回调 Java 侧执行；② LangGraph 编排 `intent → retrieve → think → tools → verify_citations → answer`，工具步数 ≤6、总预算 120s；③ 流式帧与既有 SSE 契约（`meta`/`delta`/`done`/`error`）对齐，`done` 引用集合与正文标记一致；④ 记忆写 Redis `whale:ai:memory:<conversationId>`（TTL 7 天）；⑤ 开关默认 `legacy`，关闭态行为与现网一致；⑥ `pytest` 全绿 |
| REQ-FUNC-013 | **平台操作能力（分级 + 人工确认 + 审计）**：L0 只读自动执行、L1 只读数据白名单查询、L2 写操作必须一次性确认令牌 | 用户指令（Wave 5.52） | 高 | 设计已定稿（[wave5.52 规划](../plans/wave5.52-webim-langchain-agent.md) §6.3–6.5） | `whale-ai` 工具声明 + `whale-agent` 确认/执行/审计 + `whale-im` 确认卡片（`POST /im/agent-runs/{id}/confirm`） | ① `knowledge_search`/`fact_get`/`doc_read`（L0，`sys:agent:chat`）自动执行；② `data_query`（L1，`sys:ai:data:read`）仅 SELECT、白名单视图、自动 `LIMIT 200`、5s 超时、敏感字段脱敏，非白名单/写 SQL/多语句一律拒绝；③ `platform_op`（L2）**未确认不下发**，`WAITING_CONFIRM` + `confirmation_id` 一次性令牌 5 分钟有效，绑定同一用户与权限码；④ 审计可按 `confirmation_id` 追溯（发起人/确认人/工具名/参数哈希/目标服务/结果码/耗时） |
| REQ-SEC-007 | **智能体工具权限与确认令牌**：L0/L1/L2 工具分级授权；WS 侧 ticket 鉴权；确认令牌一次性、短时效、绑定用户与权限码；敏感参数不落原文 | 用户指令（Wave 5.52） | 高 | 设计已定稿（[wave5.52 规划](../plans/wave5.52-webim-langchain-agent.md) §5.4、§6.3–6.5） | `whale-im` ticket/`im_agent_run` + `whale-agent` 工具权限校验与审计 + `whale-security`（如需）| ① 每个工具声明风险级与权限码，缺码即拒；② WS ticket 存 `whale:im:ticket:<uuid>`（60s、一次性、绑定 userId），消费即删；③ 确认令牌一次性、5 分钟过期、不可跨用户/跨权限码复用；④ 审计记录参数哈希而非敏感原值；⑤ 单元测试覆盖「无权/过期/已用/跨用户」四类拒绝路径 |

---

## 五、Wave 5.53 新增需求（档案服务 + 文件知识库，2026-10-24 登记）

| 编号 | 需求描述 | 来源 | 优先级 | 状态 | 落点 | 验收标准 |
|---|---|---|---|---|---|---|
| REQ-FUNC-014 | **档案服务**：凡经工作流审批**通过**（`process_instance.status=1`）的文档均可归档；支持档案门类、档号、保管期限、密级、不可变快照、表单附件、审批轨迹、检索与下载 | 用户指令（Wave 5.53） | 高 | **实现中**（M1 服务/DDL/门类/规则/档号、M2 归档同步全链路、M3 检索/整理/确认/销毁/下载/统计 + admin 五页、**A13 真机联调通过** 已完成；**M5 档案知识融合（密级过滤/秘密级权限码）已完成 2026-10-25**） | 新服务 `backend/whale-services/whale-archive`（8093，`/archive/**`，`bluewhale_archive` 5 表）+ `whale-workflow`/`whale-form`/`whale-file` 内网只读接口 + `frontend/admin` 档案管理页 | ① 审批通过 5 分钟内进入待整理/已归档，驳回与撤销永不归档；② 同一实例重复同步 N 次条目恒为 1、档号不重复；③ 快照含实例/表单值/字段标签/审批轨迹/附件清单且 `sha256` 可复算；④ attachment 字段（含子表）附件全部收集并锁定（`file_info.status=2`），锁定文件删除返回冲突；⑤ 并发 20 次确认归档档号唯一；⑥ 无 `sys:archive:*` 拒绝访问，归档/整理/确认/销毁/下载均有审计；⑦ 新模块单测全绿 + Checkstyle 0 + 全模块构建成功 |
| REQ-FUNC-015 | **文件知识库**：`whale-file` 中全部业务文件自动解析、切分、索引，构成可检索知识库，供 `whale-agent` 问答、引用、追溯；支持增量同步、删除下架、失败重试与手工重建 | 用户指令（Wave 5.53） | 高 | **实现中**（M4 已完成 2026-10-25：采集台账/状态机、Tika 解析、切块脱敏、增量索引、删除下架、指数退避、管理接口与权限；M5 档案桶 `kind=archive`、JSON 快照解析、证据文件下载已完成 2026-10-25） | `whale-agent`（8090）`agent_knowledge_file`（`init.sql` + `migration-5.53.sql`）+ `FileDocumentParser`/`FileChunker`/`FileKnowledgeSyncService` + `/agent/knowledge/**`；`whale-file` `/internal/changes`（含 `deleted`）与 `/internal/{id}/content`；`whale-auth` 权限 `sys:agent:knowledge:list/manage`（106/107） | ① 允许 MIME：pdf/docx/xlsx/pptx/txt/md/html/csv + json（档案快照），单文件 ≤20MB、抽取 ≤100 万字符；② 入库文件检索命中且引用含文件名与 `file:<id>`；③ 删除/停用后检索不可见；④ 损坏/超大/不支持文件置 `SKIPPED/FAILED` 且不阻塞队列；⑤ md5 未变不重复抽取，变更触发重建；⑥ 单测覆盖 6 类文档与异常路径，Checkstyle 0、构建成功 |
| REQ-SEC-008 | **档案与知识库可见性**：档案按权限码与密级（1 公开 / 2 内部 / 3 秘密）过滤；索引块新增 `permission_code` 字段，检索先过滤后召回；归档文件加锁防误删 | 用户指令（Wave 5.53） | 高 | **实现中**（M1 权限种子 `sys:archive:*` + 网关 internal-deny、M2 归档锁定 `status=2` 拦截删除、M4 索引 `permission_code` 字段与文件块脱敏已完成；**M5 档案密级查询、检索权限过滤、`sys:archive:secret:read`（151）已完成 2026-10-25**） | `whale-archive` 权限注解 + `whale-agent` 检索过滤 + `whale-file` `status=2` + `whale-auth` 权限种子 | ① 普通文件知识沿用 `sys:agent:chat`；档案快照另需 `sys:archive:item:read`；② 密级 3 内容仅授权用户命中，普通用户不可见；③ 敏感内容（口令/证件/手机号）经 `SensitiveContentMasker` 脱敏后才入索引；④ 归档锁定文件不可删除，删除前置引用校验通过 |

---

## 六、Wave 5.54 新增需求（多租户隔离与一人多租户，2026-10-25 登记）

| 编号 | 需求描述 | 来源 | 优先级 | 状态 | 落点 | 验收标准 |
|---|---|---|---|---|---|---|
| REQ-ARCH-006 | **多租户隔离体系（共享库 + 行级 `tenant_id`）**：① `sys_tenant` 租户实体与生命周期；② `sys_user/sys_role/sys_menu/sys_dept` 租户化，唯一键复合；③ 网关只信 JWT `tenantId` 注入、清除客户端伪造头；④ `TenantContext` + MyBatis-Plus 租户行插件统一过滤；⑤ 业务表（表单/文件/IM/工作流/Agent/档案/任务/支付/告警/审计）补齐 `tenant_id`；⑥ 异步与服务间调用租户传播；⑦ 租户配额与停用 | 用户指令（Wave 5.54） | 高 | **实现中**（Phase 0–4 已完成 2026-10-25：止血/租户模型/RBAC 租户化/横切插件/业务域铺开/前端租户管理与请求头注入；Phase 5 一人多租户进行中） | [wave5.54 评估稿](../plans/wave5.54-multi-tenant/assessment.md) + `whale-auth`（`sys_tenant`/RBAC/租户上下文/开通/管理）、`whale-gateway`（JwtAuthGlobalFilter）、`whale-spring-boot-starter`（租户插件）、各服务 `db/init.sql` 与 `migration-5.54-tenant-*.sql` | ① 客户端自带 `X-Tenant-Id` 一律被清除，受保护路径以 JWT `tenantId` 为准，缺失即 401（MT-01/MT-02）；② 跨租户详情/分页/删除视为不存在，写操作租户条件强制（MT-03~MT-05）；③ 审计写入 `tenant_id`（MT-06）；④ 各域迁移脚本幂等、init.sql 同构（MT-08/MT-12）；⑤ 单测 + Checkstyle 0 + 构建通过 |
| REQ-FUNC-016 | **一人多租户切换（S4）**：同一自然人可属于多个租户，登录后可在启用租户间切换；切换只换当前租户上下文与角色权限，不换账号；租户管理员可维护本租户成员与租户内角色 | 用户指令（Wave 5.54 Phase 5） | 高 | **实现中**（5.1 后端模型/切换、5.2 admin 顶栏切换器已完成 2026-10-25；5.3 成员与角色管理、5.4 双租户 E2E 待做） | [wave5.54 评估稿 §十五](../plans/wave5.54-multi-tenant/assessment.md#十五一人多租户s4切换器设计待评审) + `sys_user_tenant` + `/auth/tenants`、`/auth/tenant/switch`、`/tenant/members`、`/user/{userId}/tenants` + `frontend/admin` 顶栏与租户成员页 | ① 单租户用户不出现切换器；双租户切换后 `userId` 不变、令牌 `tenantId` 与角色/菜单均为目标租户；② 旧 access/refresh 令牌切换后立即 401；③ 关系撤销后 refresh 401，非成员切换 403，目标租户停用/过期 403；④ 租户内角色分配只影响本租户，不删除该用户在其他租户的角色；⑤ 首页关系禁止删除；⑥ `AUTH_TENANT_SWITCH` 审计可按 `userId/fromTenantId/toTenantId` 检索 |
| REQ-ARCH-007 | **运维可观测闭环**：告警通知渠道（邮件 / Webhook）可配置并随告警记录触发；Prometheus 抓取、规则、Alertmanager 与告警中心打通；运维端可跳转 Prometheus / Grafana 查看 | 用户指令（2026-10-25，台账序号 8） | 中 | **已完成**（2026-10-25） | `whale-alert`（`AlertNotifyProperties` / `AlertNotifyService` / `/alert/internal/prometheus`）+ `whale-gateway`（`/alert/internal/**` 内网屏蔽）+ `deploy/monitoring/{prometheus.yml,alertmanager.yml}` + `frontend/ops` Monitor 页外链 | ① 规则 `channel=EMAIL` 且有收件人时告警记录落库后发送邮件；`channel=WEBHOOK` 且配置 URL 时 POST JSON；失败仅记日志不回滚记录；② `/alert/internal/prometheus` 校验 `X-Alert-Token`，网关对外一律 403；③ Alertmanager 按 `firing/resolved` 落 `FIRING/RECOVERED` 记录；④ Prometheus 配置含全部服务抓取目标 + 5 条规则 + Alertmanager；⑤ 单测覆盖邮件/Webhook/跳过路径，Checkstyle 0，构建通过 |

---

## 七、既有需求基线（引自路线图，仅登记不重复展开）

| 编号 | 需求描述 | 状态 | 来源 |
|---|---|---|---|
| REQ-FUNC-001 | 基础设施就绪：Docker Compose 中间件（MySQL/Redis/Nacos/RabbitMQ/Nginx） | **本机原生进程方式已就绪（Wave 5.1f）**：MySQL 3306 / Redis 6379 / **Nacos 3.2.4（8848 服务 + 8849 控制台，MySQL 外置存储）** 均运行中；RabbitMQ / Nginx 未落地（Docker 仍缺） | [roadmap.md](../plans/roadmap.md) Phase 1 |
| REQ-FUNC-002 | whale-auth 权限中心（RBAC + ABAC 六层级） | 已完成（API 齐备 + 31 处权限注解 + 菜单接口；admin 前端已对接，Wave 5.17/5.18） | roadmap Phase 2.1 |
| REQ-FUNC-003 | whale-gateway 网关（动态路由 + JWT 鉴权 + TraceId） | 已完成（限流 + resilience4j 熔断 + 动态路由治理 + 内网屏蔽闭环；Sentinel 经评估**不引入**，Wave 5.35） | roadmap Phase 2.2 |
| REQ-FUNC-004 | whale-common 公共组件（Result / 异常 / 分页 / 自动填充） | 已完成 | roadmap Phase 2.3 |
| REQ-FUNC-005 | whale-i18n 国际化（三级回退 + Redis 缓存） | 已完成（Wave 5.50 改为 SDK 自治：随各服务启动自动建 `sys_i18n_message`、自持 `i18n/<模块>` 文案，本地 CRUD 按服务开关；集中服务 `whale-i18n` 已删除） | roadmap Phase 2.4 |
| REQ-FUNC-006 | 其余服务骨架填充（job / workflow / form / file / pay / audit / monitor / alert / i18n） | 已完成（Wave 5.19–5.33 全部填充；**不含** erp/mes/pdm/oa 业务应用，另列 Phase 4） | roadmap Phase 2.5；[wave5.46 规划](../plans/wave5.46-phase4-business-apps.md) |
| REQ-FUNC-007 | 前端基座（登录 + 路由守卫 + 主布局 + 主题） | 已完成 | roadmap Phase 3.1 |
| REQ-FUNC-008 | whale-ui 组件库（ProTable / FormModal / SearchForm） | 已完成（Wave 5.18；含 PermissionProvider / AuthButton / FormRenderer） | roadmap Phase 3.2 |
| REQ-FUNC-009 | admin 后台管理（用户/角色/菜单 + 对接 auth） | 已完成（Wave 5.17/5.18/5.34：六管理页 + 动态路由 + 按钮级权限） | roadmap Phase 3.3 |
| REQ-FUNC-010 | whale-portal 门户（工作台 / 待办 / 消息） | 已完成（Wave 5.33 ⑦；2026-10-25 占位收口：Dashboard 真实统计、待办审批抽屉、通知接告警 + IM 双源，见 [检查报告](../reviews/2026-10-25-p2-portal-platform-placeholders.md)） | roadmap Phase 3.4 |

---

## 八、变更控制流程（REQ-DOC-002 落地）

```
提出需求
  └─► ① 在本登记册登记 REQ 编号、优先级、验收标准
        └─► ② 产出/更新设计文档（docs/design/ 或 docs/plans/）并定稿
              └─► ③ 拆分为任务，写入 task-breakdown.md（含依赖与波次）
                    └─► ④ 编码实现
                          └─► ⑤ 按 code-review-checklist.md 逐项自检 → 产出 docs/reviews/ 报告
                                └─► ⑥ 构建/测试验证通过
                                      └─► ⑦ 更新 roadmap 勾选 + devlog 记录（四类信息）
                                            └─► ⑧ 提交推送
```

**门禁规则**：

1. 无 REQ 编号的需求不得进入编码；
2. 设计未定稿（状态未到 `设计已定稿`）的架构类需求不得进入编码；
3. 代码变更未产出检查报告（REQ-QA-002）不得标记为 `已完成`；
4. 构建验证未通过的变更不得提交。

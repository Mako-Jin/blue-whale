# whale-auth 权限服务 · 剩余任务台账（Backlog）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-FUNC-002（权限中心 RBAC+ABAC 六层级）、REQ-ARCH-001（DDD 改造）、REQ-SEC-001/002/003/004/005、REQ-DIST-001~003、REQ-QA-002、REQ-CICD-001、REQ-PROC-001 |
| 生成日期 | 2026-10-17 ｜ 作者：BlueWhale |
| 数据来源 | [register.md](../requirements/register.md)、[next-steps.md](next-steps.md)、[task-breakdown.md](task-breakdown.md)、[roadmap.md](roadmap.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md)、[audit-log.md](../design/audit-log.md)、[distributed.md](../design/distributed.md)、[devlog/2026-10-17.md](../devlog/2026-10-17.md) §11~§15、[reviews/2026-10-17-wave5.1b-audit-log.md](../reviews/2026-10-17-wave5.1b-audit-log.md)、[specs/2026-10-17-wave5.1b-audit-log/](../specs/2026-10-17-wave5.1b-audit-log/spec.md)、`backend/whale-services/whale-auth/` 源码与测试目录、`backend/whale-services/whale-gateway/src/main/resources/application.yml` |
| 快照口径 | **已完成**：登录/刷新/登出、令牌黑名单（auth+网关）、审计注解/切面/脱敏/查询接口、审计表 DDL、DDD 设计定稿。**进行中**：REQ-SEC-002 待验证、REQ-SEC-005 待扩展、REQ-SEC-001 运行时待验证。**阻塞**：需 Nacos(8848) 的网关 `lb://` 联调；依赖服务（whale-file/whale-job）与 RabbitMQ 未就绪的项 |
| 环境事实（本轮更新） | Redis（`tools/redis-server.exe`，6379）与 MySQL（3306 mysqld）**本机已可用**；**Nacos（8848）仍不可用** → 服务发现 `lb://` 全链路联调阻塞；网关 `StripPrefix=1` 与控制器前缀不一致问题仍待确认 |

> 说明：本文档**只新增**，不改动任何既有计划文件（register/next-steps/devlog/reviews/specs 有并行子代理维护）。任务编号 `AUTH-01` 起顺延，并逐条标注与既有 `T5.x` / `T4.x` / `T-DDD-x` / `T-AUD-x` / `T-DIST-x` 的映射，避免重复造编号。

---

## 一、whale-auth 现状清单

### 1.1 已具备能力（含接口清单）

依据：`backend/whale-services/whale-auth/src/main/java/com/bluewhale/auth/controller/*.java`（逐文件核对）；`@RequirePermission` / `@AuditLog` 用 Grep 精确统计。

| 控制器 | 方法 + 路径 | 权限注解 | 审计埋点 |
|---|---|---|---|
| `AuthController`（`/auth`） | POST `/login` | 无（`permitAll` 白名单） | `@AuditLog(LOGIN, recordParams=false)` |
| | POST `/refresh` | 无（白名单） | 无 |
| | POST `/logout` | 无（白名单） | `@AuditLog(LOGOUT, recordParams=false)` |
| `UserController`（`/user`） | GET `/page`、GET `/{id}` | 无 | 无 |
| | POST ``（创建） | **`@RequirePermission("sys:user:add")`** | `@AuditLog(CREATE, targetType=sys_user)` |
| | PUT `/{id}`、PUT `/{id}/attrs` | 无 | 无 |
| | DELETE `/{id}` | **`@RequirePermission("sys:user:delete")`** | 无 |
| | POST `/{id}/roles` | 无 | `@AuditLog(GRANT, targetId=#id)` |
| | GET `/{id}/roles`、GET `/{id}/attrs` | 无 | 无 |
| `RoleController`（`/role`） | GET `/page`、GET `/{id}`、POST ``、PUT `/{id}` | 无 | 无 |
| | DELETE `/{id}` | **`@RequirePermission("sys:role:delete")`** | 无 |
| | POST `/{id}/permissions` | 无 | `@AuditLog(GRANT, targetType=sys_role, targetId=#id)` |
| | GET `/{id}/permissions` | 无 | 无 |
| `MenuController`（`/menu`） | GET `/tree`、GET `/{id}`、POST ``、PUT `/{id}`、DELETE `/{id}` | 无 | 无 |
| `PermissionController`（`/permission`） | GET `/page`、GET `/{id}`、POST ``、PUT `/{id}`、DELETE `/{id}` | 无 | 无 |
| `DeptController`（`/dept`） | GET `/tree`、GET `/{id}`、POST ``、PUT `/{id}`、DELETE `/{id}` | 无 | 无 |

**接口统计**：共 6 控制器 / **34 个 REST 接口**；`@RequirePermission` **仅 3 处**（User 创建、User 删除、Role 删除）；`@AuditLog` **仅 5 处**（login / logout / 创建用户 / 分配用户角色 / 分配角色权限）。

| 能力维度 | 现状 | 依据 |
|---|---|---|
| RBAC + ABAC 六层级 | 用户/角色/菜单/权限点/部门/ABAC 属性 的 Mapper(8)/Service(7 接口+6 实现)/Controller(6) 齐备；DDL 8 表 | `controller/`、`service/`、`mapper/`、`db/init.sql`；[roadmap.md](roadmap.md) §2.1 |
| JWT 认证 | `JwtTokenProvider`（jjwt 0.12 API）签发 access/refresh（带 `jti`、`tokenType`）；`JwtAuthenticationFilter` 解析 + 黑名单校验；`SecurityConfig` 无状态 STATELESS | `jwt/JwtTokenProvider.java`、`security/JwtAuthenticationFilter.java`、`config/SecurityConfig.java` |
| 令牌黑名单（REQ-SEC-002/004） | `auth` 侧 `RedisTokenBlacklist`（SHA-256 摘要键 + TTL=剩余有效期 + 降级）；网关侧 `TokenBlacklistChecker`（响应式，降级放行）；键约定下沉 `whale-security` `TokenBlacklistKeys` | [devlog §11/§13](../devlog/2026-10-17.md)、[distributed.md](../design/distributed.md) §3.6 |
| 生产密钥校验（REQ-SEC-005） | `SecretPlaceholderChecker`：非 dev profile 占位密钥拒绝启动 | `config/SecretPlaceholderChecker.java`、`config/JwtProperties.java` |
| 权限拦截（横切） | `PermissionInterceptor` 兜底直查 DB（`listPermissionCodesByUserId`），拦截 `@RequirePermission` 方法 | `security/PermissionInterceptor.java`、`SecurityConfig#permissionInterceptorConfigurer` |
| 审计埋点（REQ-SEC-001） | 注解 `@AuditLog` + `AuditLogAspect`（afterCommit 异步落库）+ `SensitiveDataMasker` 下沉 `whale-spring-boot-starter` `com.bluewhale.common.audit.*`；whale-auth 侧 `db/init.sql` 追加 `audit_log` 建表 | [devlog §14](../devlog/2026-10-17.md)、[audit-log.md](../design/audit-log.md) §5.1/§12 |
| DDD 目录现状 | **未迁移**：仍为经典三层（`controller/service/service.impl/mapper/entity/dto/vo/security/jwt/config`），无 `interfaces/application/domain/infrastructure` | [ddd-whale-auth.md](../design/ddd-whale-auth.md) §1 |
| 单测覆盖 | 仅 3 个测试类：`JwtTokenProviderTest`、`RedisTokenBlacklistTest`、`AuthServiceTest`（19 用例）；控制器 / 6 个 ServiceImpl / `PermissionInterceptor` / `JwtAuthenticationFilter` / `UserDetailsServiceImpl` / `SecretPlaceholderChecker` **0 覆盖** | `src/test/java/com/bluewhale/auth/` 目录 |
| TODO/FIXME 注释 | 源码内 **无** TODO/FIXME/待做标记（已随 T4.1 清理，见 [devlog §三-4](../devlog/2026-10-17.md)） | Grep `TODO\|FIXME\|待做` 无匹配 |
| 前端对接（REQ-FUNC-002） | **未对接**：`frontend/admin` 包存在，但 roadmap §2.1「对接 admin 前端登录」未勾选 | [roadmap.md](roadmap.md) §2.1、`frontend/admin/package.json` |

---

## 二、剩余任务清单

> 优先级：P1 最高。**当前阻塞**为「无 / 阻塞原因」；「无」= 本机 Redis+MySQL 已具备，可立即动工。

### ⓪ 启动阻塞（P0，2026-10-17 实机验证才发现）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-35** | `redisson-spring-boot-starter:3.34.1` 引用 Boot 4 已移除的 `org.springframework.boot.autoconfigure.data.redis.RedisProperties` → `whale-auth` / `whale-audit` / `whale-pay` **`java -jar` 启动即 `ClassNotFoundException`（服务不可用）** | [reviews/2026-10-17-wave5.1c-runtime-verification.md](../reviews/2026-10-17-wave5.1c-runtime-verification.md) §三-1；`whale-parent/pom.xml:42,164`、`whale-auth/pom.xml:71`、`whale-audit/pom.xml:53`、`whale-services/whale-pay/pom.xml:53`、`whale-spring-boot-starter/pom.xml:39` | 无 | **P0** | 三服务 `java -jar` 能启动并就绪（8081/9101）；`whale.live.it=true` 的 HTTP 端到端可跑通 | 无（方案待选：①换 `org.redisson:redisson` 原始依赖 + 自建 `RedissonAutoConfiguration`；②升级到支持 Boot 4 的 Redisson 版本；③改用 Spring Data Redis 自实现锁语义） |
| **AUTH-36** | JDBC URL 参数 `characterEncoding=utf8mb4` 被 Connector/J 拒绝（应写 `utf8`，表字符集用 `connectionCollation`），本轮验证被迫用系统属性覆盖才连库成功 | [reviews §三-2](../reviews/2026-10-17-wave5.1c-runtime-verification.md) | 无 | P1 | auth/audit 等 yml 改为 `characterEncoding=utf8`，真实连库无告警 | 无 |
| **AUTH-37** | `whale-auth/db/init.sql` 种子 admin 的 BCrypt 密文与注释口令「123456」不符，按文档无法登录 | [reviews §三-3](../reviews/2026-10-17-wave5.1c-runtime-verification.md) | 无 | P2 | 注释口令可实际登录，或注释与密文一致并注明生成方式 | 无 |

### ① 安全收口残余

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-01** | 网关侧同步 JWT 占位密钥校验（`whale-gateway` 启动时非 dev 占位密钥拒绝） | [devlog §12/§13 待做](../devlog/2026-10-17.md)；REQ-SEC-005 延伸 | 无 | P2 | 网关非 dev + 占位 `WHALE_GATEWAY_JWT_SECRET` 启动失败并告警 | 无 |
| **AUTH-02** | 统一 jjwt 版本：parent 管理 `0.12.5`，gateway `pom.xml` 硬编码 `0.12.6`（未走父管理） | [next-steps §五-4](next-steps.md)；[devlog §13 待做](../devlog/2026-10-17.md)；`whale-parent/pom.xml:50`、`whale-gateway/pom.xml:60` | 无 | P2 | 全模块 jjwt 版本一致（`mvn dependency:tree` 无分裂） | 无 |
| **AUTH-03** | 评估 refreshToken「一次一换」轮换策略（登出双写已做，但无一次性消费约束，存在重放窗口） | [devlog §13 待做](../devlog/2026-10-17.md)；REQ-SEC-004 | AUTH-14（DDD）可选 | P2 | 策略经决策记录（ADR）定稿；若采纳则旧 refreshToken 使用后即失效并有单测 | 策略未定（不确定项） |
| **AUTH-04** | 补全接口权限注解：34 个接口仅 3 处 `@RequirePermission`，用户更新/角色·菜单·权限·部门全部写接口、分配角色/权限、ABAC 属性写入 **均无鉴权** | Grep 统计 `controller/`；[ddd-whale-auth.md](../design/ddd-whale-auth.md) §1.1「权限校验在横切层兜底」 | 无 | **P1** | 所有写接口均带 `@RequirePermission`；无权限调用返回 403（`RestAccessDeniedHandler`） | 无 |
| **AUTH-05** | 黑名单降级语义的生产告警：Redis 不可用时 auth/网关均按未命中放行（登出形同失效） | [next-steps §五-2](next-steps.md)；[distributed.md](../design/distributed.md) §3.6 | 无 | P2 | Redis 不可用触发告警指标/日志；有降级说明 | 无 |

### ② 审计与合规

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-06** ↔ T5.12/T-AUD-6 延伸 | 审计查询接口鉴权：`AuditLogController` 加 `@RequirePermission("sys:audit:log:list")`（需引入 whale-auth-sdk + 拦截器） | [reviews §三-#3 / §五-3](../reviews/2026-10-17-wave5.1b-audit-log.md)；[audit-log.md](../design/audit-log.md) §9 | whale-auth-sdk | P2 | 未授权用户查询审计返回 403；权限点入库 | 无 |
| **AUTH-07** | 读审计的元审计：查询/导出动作本身记 `@AuditLog(type=QUERY/EXPORT)` | [audit-log.md](../design/audit-log.md) §9/§10 | AUTH-06 | P2 | 查询审计产生元审计记录 | 无 |
| **AUTH-08** ↔ T-AUD-8 / T5.12 延伸 | `POST /audit/log/export` 导出接口（异步 + whale-file） | [audit-log.md](../design/audit-log.md) §9、T-AUD-8；[devlog §14 待做](../devlog/2026-10-17.md) | whale-file（REQ-FUNC-006 待启动） | P2 | 条件导出返回文件；导出动作被审计 | **阻塞：whale-file 未启动** |
| **AUTH-09** ↔ T-AUD-8 | 审计保留与归档定时任务（90 天热 / 90 天–1 年温 / >1 年冷） | [audit-log.md](../design/audit-log.md) §10、T-AUD-8「未落地」 | whale-job（REQ-FUNC-006 待启动） | P2 | 过期数据按策略清理；归档动作记审计 | **阻塞：whale-job 未启动** |
| **AUTH-10** ↔ T-AUD-9 | 阶段二 MQ 汇聚（`@AuditLog` → RabbitMQ → whale-audit 消费落 `bluewhale_audit`） | [audit-log.md](../design/audit-log.md) §6/T-AUD-9；[devlog §14 待做](../devlog/2026-10-17.md) | RabbitMQ | P3 | 容器就绪后跨服务审计汇聚到独立库 | **阻塞：RabbitMQ 未就绪** |
| **AUTH-11** | 脱敏规则配置化（`whale.audit.sensitive.keys`）+ `@Sensitive` 注解兜底 | [audit-log.md](../design/audit-log.md) §7；[devlog §14 待做](../devlog/2026-10-17.md) | 无 | P3 | key 规则可由配置扩展；`@Sensitive` 字段按策略脱敏 | 无 |
| **AUTH-12** | 审计埋点补全：现仅 5 处，缺 用户更新/删除、角色·菜单·权限·部门全部写操作、改密/启停、ABAC 属性写入 | [audit-log.md](../design/audit-log.md) §2 事件分类 vs §12 埋点现状 | 无 | P2 | §2 各分类的敏感写操作均产生审计记录 | 无 |
| **AUTH-13** | `recordResult()` 落库（注解属性已定义但无对应列；需先补列） | [reviews §三-#5](../reviews/2026-10-17-wave5.1b-audit-log.md)；`common/audit/annotation/AuditLog.java` | 补 `audit_log` 列 | P3 | 需记录返回体的接口落库后可查 | 无 |

### ③ DDD 迁移（REQ-ARCH-001）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-14** ↔ **T5.13 / T4.4** | 迁移第 1 步：四层目录骨架（`interfaces/application/domain/infrastructure`）+ 值对象（`Username`/`EncryptedPassword`/`RoleCode`/`PermCode`）+ 契约冻结清单 | [next-steps T5.13](next-steps.md)、[task-breakdown T4.4](task-breakdown.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md) §7/§8 T-DDD-1~3 | 设计已定稿 | **P1** | 新增目录不改变现有 REST 契约；编译 + 既有接口回归通过；domain 无框架注解 | 无 |
| **AUTH-15** ↔ **T5.14** | 迁移第 2 步：`User` 聚合 + 仓储接口（domain）、PO 与 `RepositoryImpl`（infrastructure，MapStruct） | [next-steps T5.14](next-steps.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md) T-DDD-4/8 | AUTH-14 | P2 | 登录链路改走新仓储，REST 契约不变；PO↔领域对象字段比对单测 | 无 |
| **AUTH-16** ↔ **T5.15** | 迁移第 3 步：`Role`/`Menu`/`Permission`/`Dept` 聚合迁移 + feature flag（`whale.auth.ddd.{context}.enabled`）灰度 | [next-steps T5.15](next-steps.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md) T-DDD-5~7/10 | AUTH-15 | P2 | 开关可秒级回滚；新旧实现对比一致 | 无 |
| **AUTH-17** ↔ T-DDD-9~12 | 迁移第 4 步：`application` 层 AppService + Command/Query、领域事件发布、清理旧 `ServiceImpl` | [ddd-whale-auth.md](../design/ddd-whale-auth.md) §7/§8 | AUTH-16 | P3 | 用例编排完成；事件可被订阅；无残留旧实现 | 无 |

### ④ 分布式与幂等

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-18** ↔ **T5.3 / T-DIST-1** | 幂等组件 `@Idempotent`（Token/唯一键），先用于用户注册/创建；唯一索引对齐（现为"先查重再插入"，有并发窗口） | [next-steps T5.3](next-steps.md)、[distributed.md](../design/distributed.md) §3.1 | 无 | P2 | 重复提交返回同一结果且不产生重复数据；`uk_username` 兜底 | 无 |
| **AUTH-19** ↔ T-LOCK-4 / REQ-DIST-002 | `@DistributedLock` 场景接入（注册幂等 / 权限变更 `lock:auth:role:{roleId}`）+ 并发验证（Redis 本机已可用） | [devlog §12 待做](../devlog/2026-10-17.md)、[distributed.md](../design/distributed.md) §2.4 | AUTH-18、Redis | P2 | 并发调用互斥；获取失败按 `failFast` 抛 `BizException` | 无（Redis 已可用） |
| **AUTH-20** ↔ **T5.5 / T-DIST-2** | 缓存一致性工具（Cache-Aside + 延迟双删），统一权限判定缓存（现 `PermissionInterceptor` 兜底每次直查 DB） | [next-steps T5.5](next-steps.md)、[distributed.md](../design/distributed.md) §3.2、[ddd-whale-auth.md](../design/ddd-whale-auth.md) §4.2 | 无 | P2 | 工具类单测通过；权限变更后缓存刷新一致 | 无 |

### ⑤ 接口 / 功能缺口

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-21** | 当前用户菜单/权限接口（如 `/auth/me`、用户菜单树、按钮权限码）：现 `LoginResponse` 仅含 roles，前端无动态路由与按钮级权限所需数据 | [roadmap §2.1/§3.3](roadmap.md)（"动态路由 + 按钮级权限渲染"未勾选）；`controller/` 无对应接口 | 无 | **P1** | 登录后按用户返回菜单树 + 权限码；前端可据此渲染 | 无 |
| **AUTH-22** | 改密 / 重置密码 / 启停用户接口（审计 §2 列为高优敏感操作，当前无对应接口） | [audit-log.md](../design/audit-log.md) §2「敏感操作：改密、重置密码、启停用户」；`controller/` 无对应接口 | AUTH-12（埋点） | P2 | 改密/启停可用且必留痕；密码字段脱敏 | 无 |

### ⑥ 测试与质量（REQ-QA-002 / REQ-CICD-001）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-23** ↔ **T5.19 延伸** | 补齐 0 覆盖类单测：6 个 `ServiceImpl`、`PermissionInterceptor`、`JwtAuthenticationFilter`、`UserDetailsServiceImpl`、`SecretPlaceholderChecker` | `src/test/` 目录事实；[next-steps T5.19](next-steps.md) | 无 | P2 | 新增用例全绿；覆盖正常/降级/边界 | 无 |
| **AUTH-24** ↔ **T5.18** | JaCoCo 覆盖率接入（阈值 40% 行 / 60% 分支 / 增量 70%） | [next-steps T5.18](next-steps.md)、[register REQ-CICD-001](../requirements/register.md) | 无 | P2 | 构建产出覆盖率报告并执行阈值校验 | 无 |
| **AUTH-25** ↔ **T5.17** | Checkstyle 门禁由 `warning` 提升为 `error`（真正阻断） | [next-steps T5.17](next-steps.md)、[devlog §12 待做](../devlog/2026-10-17.md) | AUTH-26 | P2 | 违规时 CI 失败 | 无 |
| **AUTH-26** | 清理存量 WARN：`AssignPermissionsRequest.java` 未使用 import（`NotBlank`/`Size`） | [reviews §五-5](../reviews/2026-10-17-wave5.1b-audit-log.md) | 无 | P3 | `checkstyle:check` 0 WARN | 无 |
| **AUTH-27** ↔ **T5.20** | 全量回归：`mvn clean install -T 1C` + `pnpm build`，按清单出报告后提交 | [next-steps T5.20](next-steps.md) | 上述任务 | P2 | 后端 exit 0 / 前端 11 包成功；报告归档 | 无 |
| **AUTH-28** ↔ **T5.16** | Gitee Go 流水线映射（按 [cicd.md](../design/cicd.md) 第 6 节三条 Workflow） | [next-steps T5.16](next-steps.md)、[register REQ-CICD-001](../requirements/register.md) | 无 | P2 | 推送后 Gitee 可见流水线结果 | 无 |

### ⑦ 网关与联调

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-29** | 确认并修复网关路由前缀冲突：`Path=/auth/**` + `StripPrefix=1` 会把 `/auth/login` 剥成 `/login`，与 `AuthController @RequestMapping("/auth")` 冲突；审计路由同理 | [reviews §三-#2（P1）](../reviews/2026-10-17-wave5.1b-audit-log.md)；[audit-log.md](../design/audit-log.md) §9 待确认；`whale-gateway/application.yml:31-78` | Nacos 联调 | **P1** | 定夺「改路由」或「改控制器前缀」，经网关访问 `/auth/**`、`/audit/**` 均 200 | **阻塞：Nacos(8848) 不可用，无法实跑** |
| **AUTH-30** ↔ **T5.4 / T-DIST-4** | 网关限流熔断：接入 Sentinel（或 Redis 计数器兜底）；`whale-gateway` 现无任何 Sentinel/`RequestRateLimiter` 依赖 | [next-steps T5.4](next-steps.md)、[distributed.md](../design/distributed.md) §3.4；Grep `whale-gateway` 无匹配 | Nacos 联调 | P2 | 超阈值返回 429 + `Result` 体 | 联调受限（需运行环境） |
| **AUTH-31** | 端到端验证：① auth 直连（Redis+MySQL 已就绪）验证登出→401、refresh→401、审计落库；② 经网关全链路（依赖 Nacos） | [register REQ-SEC-002/003/004](../requirements/register.md)（待验证）；[next-steps §五-1/§五-5](next-steps.md) | Nacos（② 部分） | **P1** | ① 可本机执行；② 网关链路待 Nacos | **阻塞：Nacos 不可用（② 部分）** |

### ⑧ 前端对接（REQ-FUNC-002 / 008 / 009）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-32** ↔ **T5.21** | `frontend/admin` 登录对接 `/auth/login` + 登出调 `/auth/logout` | [next-steps T5.21](next-steps.md)、[roadmap §2.1](roadmap.md) | Nacos 联调、AUTH-21 | P2 | 登出后原 token 请求返回 401 | **阻塞：Nacos 不可用** |
| **AUTH-33** ↔ **T5.22** | `whale-ui`：ProTable / FormModal / SearchForm 二次封装 | [next-steps T5.22](next-steps.md)、[roadmap §3.2](roadmap.md) | 无 | P2 | 组件可独立构建，有示例页 | 无 |
| **AUTH-34** ↔ **T5.23** | admin 用户/角色/菜单管理页 + 按钮级权限渲染 | [next-steps T5.23](next-steps.md)、[roadmap §3.3](roadmap.md) | AUTH-32、AUTH-33、AUTH-21 | P2 | 页面可完成 CRUD 且按权限隐藏按钮 | 受 AUTH-21/32 牵连 |

---

## 三、依赖关系与建议执行顺序

```
Wave 0｜启动阻塞，最高优先级（不修则所有服务跑不起来）
  └─ AUTH-35 redisson / Boot 4 不兼容（P0）──► 解锁 Wave C、Wave D 全部运行时验证
       └─ AUTH-36 数据源字符集参数 ｜ AUTH-37 种子口令对齐

Wave A｜无外部依赖，可立即动工（Redis/MySQL 本机已可用）
  ├─ AUTH-04 权限注解覆盖补全（P1，纯安全缺口）
  ├─ AUTH-14 DDD 骨架 + 值对象（P1，↔ T5.13）
  ├─ AUTH-21 当前用户菜单/权限接口（P1，解锁前端）
  ├─ AUTH-02 jjwt 版本统一 ｜ AUTH-05 黑名单降级告警 ｜ AUTH-12/13 审计补口 ｜ AUTH-11 脱敏配置化
  ├─ AUTH-18 幂等组件（↔ T5.3）──► AUTH-19 锁场景接入 ──► AUTH-20 缓存一致性
  ├─ AUTH-22 改密/启停接口 ｜ AUTH-26 存量 WARN 清理
  └─ AUTH-23 单测补口 ｜ AUTH-24 JaCoCo ｜ AUTH-28 Gitee Go（可与上并行）

Wave B｜依赖 Wave A 产物
  ├─ AUTH-15 User 聚合 ──► AUTH-16 Role/Menu/Permission/Dept 聚合 ──► AUTH-17 AppService/领域事件
  ├─ AUTH-06 审计查询鉴权 ──► AUTH-07 元审计
  ├─ AUTH-01 网关占位密钥 ｜ AUTH-03 refresh 轮换策略
  └─ AUTH-25 Checkstyle 提级 ──► AUTH-27 全量回归 ──► 提交推送

Wave C｜Redis/MySQL 已就绪，本机可跑（auth 直连）
  └─ AUTH-31① 登出→401、refresh→401、审计落库（不经网关）

Wave D｜被 Nacos(8848) 不可用阻塞（服务发现 lb:// 无法联调）
  ├─ AUTH-29 网关路由前缀冲突确认（P1）
  ├─ AUTH-31② 网关→auth 全链路端到端
  ├─ AUTH-30 Sentinel 限流联调
  └─ AUTH-32 admin 前端登录/登出对接 ──► AUTH-34 admin CRUD 页
                                        └─ AUTH-33 whale-ui（无阻塞，可前移）

Wave E｜被依赖服务 / 中间件未就绪阻塞
  ├─ AUTH-08 审计导出（whale-file）  ├─ AUTH-09 保留归档任务（whale-job）
  └─ AUTH-10 MQ 汇聚（RabbitMQ，阶段二）
```

---

## 四、风险与不确定项（未被验证 / 证据不足）

| # | 项 | 现状与风险 | 来源 |
|---|---|---|---|
| 1 | **refreshToken 单次轮换策略未定** | 登出已双写黑名单，但 refreshToken 无「一次一换」约束，重放窗口仍存在 | [devlog §13 待做](../devlog/2026-10-17.md)（AUTH-03 待决策） |
| 2 | **jjwt 版本分裂** | parent 管理 `0.12.5`，`whale-gateway/pom.xml` 硬编码 `0.12.6`，未走父管理，存在行为差异隐患 | [next-steps §五-4](next-steps.md)、`whale-parent/pom.xml:50`、`whale-gateway/pom.xml:60` |
| 3 | **网关路由前缀未确认** | `StripPrefix=1` 与 `AuthController`/`AuditLogController` 前缀不一致（P1），**从未在容器环境验证**，改动前无法判定「改路由」或「改前缀」 | [reviews §三-#2](../reviews/2026-10-17-wave5.1b-audit-log.md)、[audit-log.md](../design/audit-log.md) §9 |
| 4 | **黑名单降级语义** | Redis 不可用时 auth 与网关均按未命中放行 → 登出形同失效（可用性优先取舍，无告警） | [next-steps §五-2](next-steps.md) |
| 5 | **权限注解覆盖不足（证据确凿）** | 34 接口仅 3 处 `@RequirePermission`，大量写接口无鉴权，越权风险 | Grep `controller/` 统计；AUTH-04 |
| 6 | **Nacos 不可用** | 8848 未起 → 服务发现 `lb://` 与网关全链路联调、admin 前端对接全部受阻 | 本轮环境事实 |
| 7 | **运行时/端到端验证深度不足** | Wave 5.1c 已把**令牌黑名单、分布式锁、审计落库、审计查询**跑成实机验证；但 **HTTP 端到端（登录/登出/刷新 401）与网关全链路**仍未跑通（被 AUTH-35 redisson P0 与 Nacos 缺位阻塞） | [reviews/2026-10-17-wave5.1c-runtime-verification.md](../reviews/2026-10-17-wave5.1c-runtime-verification.md) §四、[next-steps §五-5](next-steps.md) |
| 8 | **审计埋点不全** | 仅 5 处埋点，与设计 §2 事件分类（数据变更/敏感操作/组织变更）差距大 | [audit-log.md](../design/audit-log.md) §2 vs §12 |
| 9 | **`@DistributedLock` 未接入任何场景** | 工具已落地但零场景接入，无并发实证 | [devlog §12 待做](../devlog/2026-10-17.md) |
| 10 | **权限判定无缓存** | `PermissionInterceptor` 每次兜底直查 DB，高并发下为性能瓶颈，且缓存一致性方案未落地 | [ddd-whale-auth.md](../design/ddd-whale-auth.md) §1.1 |
| 11 | **前端 admin 未对接** | roadmap §2.1「对接 admin 前端登录」未勾选，REQ-FUNC-002 仍标「API 齐备，前端未对接」 | [roadmap.md](roadmap.md) §2.1、[register REQ-FUNC-002](../requirements/register.md) |
| 12 | **`recordResult()` 属性悬空** | 注解已定义但无对应列，属"已声明未实现"，易误判能力已具备 | [reviews §三-#5](../reviews/2026-10-17-wave5.1b-audit-log.md) |
| 13 | **服务无法启动（P0，实测）** | `redisson-spring-boot-starter:3.34.1` 引用 Boot 4 已移除的 `RedisProperties` → whale-auth/whale-audit/whale-pay `java -jar` 启动即 `ClassNotFoundException`；此前从未启动过服务故一直未暴露 | [reviews/2026-10-17-wave5.1c-runtime-verification.md](../reviews/2026-10-17-wave5.1c-runtime-verification.md) §三-1；AUTH-35 |
| 14 | **数据源字符集参数不合法** | `characterEncoding=utf8mb4` 被 Connector/J 拒绝，真实连库需覆盖参数；说明 yml 从未在真机连库验证过 | [reviews §三-2](../reviews/2026-10-17-wave5.1c-runtime-verification.md)；AUTH-36 |
| 15 | **种子数据与文档不一致** | `init.sql` 注释称 admin 口令为 `123456`，实际 BCrypt 密文不匹配，按文档无法登录 | [reviews §三-3](../reviews/2026-10-17-wave5.1c-runtime-verification.md)；AUTH-37 |

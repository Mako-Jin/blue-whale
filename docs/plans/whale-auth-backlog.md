# whale-auth 权限服务 · 剩余任务台账（Backlog）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-FUNC-002（权限中心 RBAC+ABAC 六层级）、REQ-ARCH-001（DDD 改造）、REQ-SEC-001/002/003/004/005、REQ-DIST-001~003、REQ-QA-002、REQ-CICD-001、REQ-PROC-001 |
| 生成日期 | 2026-10-17 ｜ 作者：BlueWhale |
| 数据来源 | [register.md](../requirements/register.md)、[next-steps.md](next-steps.md)、[task-breakdown.md](task-breakdown.md)、[roadmap.md](roadmap.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md)、[audit-log.md](../design/audit-log.md)、[distributed.md](../design/distributed.md)、[devlog/2026-10-17.md](../devlog/2026-10-17.md) §11~§15、[reviews/2026-10-17-wave5.1b-audit-log.md](../reviews/2026-10-17-wave5.1b-audit-log.md)、[specs/2026-10-17-wave5.1b-audit-log/](../specs/2026-10-17-wave5.1b-audit-log/spec.md)、`backend/whale-services/whale-auth/` 源码与测试目录、`backend/whale-services/whale-gateway/src/main/resources/application.yml` |
| 快照口径 | **已完成（Wave 5.2 更新）**：登录/刷新/登出、令牌黑名单（auth+网关）、审计注解/切面/脱敏/查询接口、审计表 DDL、DDD 设计定稿；**本轮新增闭环**：DDD 四层骨架 + 4 值对象（AUTH-14）、**全接口权限注解（AUTH-04，3 → 31）+ 22 条权限点种子**、`GET /auth/me`（AUTH-21）、**auth 管理前缀网关路由（AUTH-38）**；经网关全链路端到端已实机验证（Wave 5.1f）：login `code=0` → logout `code=0` → 重放 401 → refresh 业务码 401 → `audit_log` LOGIN/LOGOUT 落库。**进行中**：REQ-SEC-005（非 dev profile 启动校验）待实测；**Wave 5.3 已补**：AUTH-38 经网关冒烟通过 + AUTH-12 审计埋点 5→20 + AUTH-22 改密/启停接口 + AUTH-02 jjwt 统一 + AUTH-03 ADR-001 定稿。**Wave 5.4 已补**：AUTH-25 Checkstyle 门禁提级 error（全 reactor 0 违规即阻断）+ AUTH-23 新增 4 类 21 用例（`whale-auth` 34 → 55 全绿）。**阻塞**：依赖服务（whale-file/whale-job）与 RabbitMQ 未就绪的项（AUTH-08/09/10） |
| 环境事实（Wave 5.1f 更新） | **Nacos 3.2.4 已就绪**（`tools/nacos`，**8848 服务 + 8849 控制台**，MySQL 外置存储，`nacos` 库 13 表）；Redis 5.0.14.1（6379，散落文件已归位）与 MySQL 8.0.45（3306）本机可用；**`lb://` 全链路联调阻塞已解除**（auth/audit 均 `register finished`）；网关 `StripPrefix=1` 前缀冲突**已确认并修复**（AUTH-29 → REQ-ARCH-003） |

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
| | GET `/me` | **`@RequirePermission`**（Wave 5.2 新增，仅登录校验，无权限码） | 无 |
| `UserController`（`/user`） | GET `/page`、GET `/{id}` | **`sys:user:list`** | 无 |
| | POST ``（创建） | **`sys:user:add`** | `@AuditLog(CREATE, targetType=sys_user)` |
| | PUT `/{id}`（更新） | **`sys:user:edit`** | **`@AuditLog(UPDATE, targetId=#id)`**（Wave 5.3） |
| | PUT `/{id}/attrs`（ABAC 写入） | **`sys:user:edit`** | **`@AuditLog(UPDATE, targetId=#id)`**（Wave 5.3） |
| | DELETE `/{id}` | **`sys:user:delete`** | **`@AuditLog(DELETE, targetId=#id)`**（Wave 5.3） |
| | POST `/{id}/roles` | **`sys:user:assign`** | `@AuditLog(GRANT, targetId=#id)` |
| | PUT `/{id}/password`（**Wave 5.3 新增**） | **`sys:user:resetPwd`** | **`@AuditLog(UPDATE, targetId=#id, recordParams=false)`** |
| | PUT `/{id}/status`（**Wave 5.3 新增**） | **`sys:user:status`** | **`@AuditLog(UPDATE, targetId=#id)`** |
| | GET `/{id}/roles`、GET `/{id}/attrs` | **`sys:user:list`** | 无 |
| `RoleController`（`/role`） | GET `/page`、GET `/{id}` | **`sys:role:list`** | 无 |
| | POST `` | **`sys:role:add`** | **`@AuditLog(CREATE, targetType=sys_role)`**（Wave 5.3） |
| | PUT `/{id}` | **`sys:role:edit`** | **`@AuditLog(UPDATE, targetId=#id)`**（Wave 5.3） |
| | DELETE `/{id}` | **`sys:role:delete`** | **`@AuditLog(DELETE, targetId=#id)`**（Wave 5.3） |
| | POST `/{id}/permissions` | **`sys:role:assign`** | `@AuditLog(GRANT, targetType=sys_role, targetId=#id)` |
| | GET `/{id}/permissions` | **`sys:role:list`** | 无 |
| `MenuController`（`/menu`） | GET `/tree`、GET `/{id}` | **`sys:menu:list`** | 无 |
| | POST `` | **`sys:menu:add`** | **`@AuditLog(CREATE, targetType=sys_menu)`**（Wave 5.3） |
| | PUT `/{id}` | **`sys:menu:edit`** | **`@AuditLog(UPDATE, targetId=#id)`**（Wave 5.3） |
| | DELETE `/{id}` | **`sys:menu:delete`** | **`@AuditLog(DELETE, targetId=#id)`**（Wave 5.3） |
| `PermissionController`（`/permission`） | GET `/page`、GET `/{id}` | **`sys:permission:list`** | 无 |
| | POST `` | **`sys:permission:add`** | **`@AuditLog(CREATE, targetType=sys_permission)`**（Wave 5.3） |
| | PUT `/{id}` | **`sys:permission:edit`** | **`@AuditLog(UPDATE, targetId=#id)`**（Wave 5.3） |
| | DELETE `/{id}` | **`sys:permission:delete`** | **`@AuditLog(DELETE, targetId=#id)`**（Wave 5.3） |
| `DeptController`（`/dept`） | GET `/tree`、GET `/{id}` | **`sys:dept:list`** | 无 |
| | POST `` | **`sys:dept:add`** | **`@AuditLog(CREATE, targetType=sys_dept)`**（Wave 5.3） |
| | PUT `/{id}` | **`sys:dept:edit`** | **`@AuditLog(UPDATE, targetId=#id)`**（Wave 5.3） |
| | DELETE `/{id}` | **`sys:dept:delete`** | **`@AuditLog(DELETE, targetId=#id)`**（Wave 5.3） |

**接口统计（Wave 5.3 更新）**：共 6 控制器 / **37 个 REST 接口**（Wave 5.2 为 35，Wave 5.3 新增 `/user/{id}/password`、`/user/{id}/status`）；`@RequirePermission` **34 处**（除 3 个白名单接口外**全部接口均已鉴权**）；`@AuditLog` **20 处**（Wave 5.3 由 **5** 补齐至 **20**，覆盖 [audit-log.md](../design/audit-log.md) §2 全部敏感写操作）。

| 能力维度 | 现状 | 依据 |
|---|---|---|
| RBAC + ABAC 六层级 | 用户/角色/菜单/权限点/部门/ABAC 属性 的 Mapper(8)/Service(7 接口+6 实现)/Controller(6) 齐备；DDL 8 表；**Wave 5.2 补齐 22 条权限点种子 + 8 个菜单项**（**Wave 5.3 增至 24 条**），此前 `sys_permission` 无种子行，非超管必然 403） | `controller/`、`service/`、`mapper/`、`db/init.sql`；[roadmap.md](roadmap.md) §2.1 |
| JWT 认证 | `JwtTokenProvider`（jjwt 0.12 API）签发 access/refresh（带 `jti`、`tokenType`）；`JwtAuthenticationFilter` 解析 + 黑名单校验；`SecurityConfig` 无状态 STATELESS | `jwt/JwtTokenProvider.java`、`security/JwtAuthenticationFilter.java`、`config/SecurityConfig.java` |
| 令牌黑名单（REQ-SEC-002/004） | `auth` 侧 `RedisTokenBlacklist`（SHA-256 摘要键 + TTL=剩余有效期 + 降级）；网关侧 `TokenBlacklistChecker`（响应式，降级放行）；键约定下沉 `whale-security` `TokenBlacklistKeys` | [devlog §11/§13](../devlog/2026-10-17.md)、[distributed.md](../design/distributed.md) §3.6 |
| 生产密钥校验（REQ-SEC-005） | `SecretPlaceholderChecker`：非 dev profile 占位密钥拒绝启动 | `config/SecretPlaceholderChecker.java`、`config/JwtProperties.java` |
| 权限拦截（横切） | `PermissionInterceptor` 兜底直查 DB（`listPermissionCodesByUserId`），拦截 `@RequirePermission` 方法 | `security/PermissionInterceptor.java`、`SecurityConfig#permissionInterceptorConfigurer` |
| 审计埋点（REQ-SEC-001） | 注解 `@AuditLog` + `AuditLogAspect`（afterCommit 异步落库）+ `SensitiveDataMasker` 下沉 `whale-spring-boot-starter` `com.bluewhale.common.audit.*`；whale-auth 侧 `db/init.sql` 追加 `audit_log` 建表 | [devlog §14](../devlog/2026-10-17.md)、[audit-log.md](../design/audit-log.md) §5.1/§12 |
| DDD 目录现状 | **骨架已建（Wave 5.2，AUTH-14 ↔ T5.13）**：新增 `domain`/`application`/`interfaces`/`infrastructure` 四层（`package-info.java` 固化依赖规则，domain 禁框架注解）+ **4 个值对象**（`Username`/`EncryptedPassword`/`RoleCode`/`PermCode`，构造即校验）；业务代码**仍以经典三层承载**（Strangler Fig 第 1 步），后续 AUTH-15/16/17 渐进迁移 | [ddd-whale-auth.md](../design/ddd-whale-auth.md) §7/§10 |
| 单测覆盖 | **14 个单测类 / 99 用例全绿**（另有 2 个 `*IT` 实机用例默认跳过，需 `-Dwhale.live.it=true`）（Wave 5.2 新增 `ValueObjectsTest`、`SysMenuServiceImplTest`，`AuthServiceTest` 扩至 9；Wave 5.3 新增 `SysUserServiceImplTest` 4；**Wave 5.4 新增 `PermissionInterceptorTest` 8 / `JwtAuthenticationFilterTest` 6 / `UserDetailsServiceImplTest` 4 / `SecretPlaceholderCheckerTest` 3**；**Wave 5.7（AUTH-23 收口）新增 `SysRoleServiceImplTest` 21 / `SysPermissionServiceImplTest` 13 / `SysDeptServiceImplTest` 8 / `SysUserPermissionServiceImplTest` 2，4 个零覆盖 `ServiceImpl` 全部覆盖**）。**覆盖率（Wave 5.7，`clean verify` 口径）**：行 0.3734 → **0.6755**、分支 **0.6718**，`-Pcoverage-gate` 通过。**残余**：控制器层单测、`replaceUserAttrs` 非空批量写路径（依赖真实 Mapper 代理/容器） | `src/test/java/com/bluewhale/auth/` 目录、[reviews/2026-10-18-auth23-serviceimpl-unit-tests.md](../reviews/2026-10-18-auth23-serviceimpl-unit-tests.md) |
| TODO/FIXME 注释 | 源码内 **无** TODO/FIXME/待做标记（已随 T4.1 清理，见 [devlog §三-4](../devlog/2026-10-17.md)） | Grep `TODO\|FIXME\|待做` 无匹配 |
| 前端对接（REQ-FUNC-002） | **未对接**：`frontend/admin` 包存在，但 roadmap §2.1「对接 admin 前端登录」未勾选 | [roadmap.md](roadmap.md) §2.1、`frontend/admin/package.json` |

---

## 二、剩余任务清单

> 优先级：P1 最高。**当前阻塞**为「无 / 阻塞原因」；「无」= 本机 Redis+MySQL 已具备，可立即动工。

### ⓪ 启动阻塞（P0，2026-10-17 实机验证才发现）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| ~~**AUTH-35**~~ ✅ **已解决（Wave 5.1d）** | `redisson-spring-boot-starter:3.34.1` 引用 Boot 4 已移除的 `org.springframework.boot.autoconfigure.data.redis.RedisProperties` → `whale-auth` / `whale-audit` / `whale-pay` **`java -jar` 启动即 `ClassNotFoundException`（服务不可用）** | [reviews/2026-10-17-wave5.1c-runtime-verification.md](../reviews/2026-10-17-wave5.1c-runtime-verification.md) §三-1；[reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md) §三-1；`whale-parent/pom.xml`、`whale-auth/pom.xml`、`whale-audit/pom.xml`、`whale-services/whale-pay/pom.xml`、`whale-spring-boot-starter/pom.xml` | 无 | **P0** | 三服务 `java -jar` 能启动并就绪（8081/9101）；`whale.live.it=true` 的 HTTP 端到端可跑通 | **已解**：升级至 **4.8.0**（按 Boot 4.1 编译、传递 `redisson-spring-data-41`）实测与 Boot 4.0.8 兼容 —— `whale-auth`(8081) / `whale-audit`(9101) 均 `Started …Application`，`LiveDistributedLockIT` 2/0/0 通过，login/logout 端到端跑通。**未回退 4.5.0/4.2.0** |
| ~~**AUTH-36**~~ ✅ **已解决（Wave 5.1d）** | JDBC URL 参数 `characterEncoding=utf8mb4` 被 Connector/J 拒绝（应写 `utf8`，表字符集用 `connectionCollation`） | [reviews 5.1c §三-2](../reviews/2026-10-17-wave5.1c-runtime-verification.md)；[5.1d §三-2](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md) | 无 | P1 | auth/audit 等 yml 改为 `characterEncoding=utf8`，真实连库无告警 | **已解**：auth/audit yml 改为 `characterEncoding=utf8&connectionCollation=utf8mb4_general_ci`；服务以该 URL 真实连库成功（`LiveAuthDatabaseIT` 2/0/0） |
| ~~**AUTH-37**~~ ✅ **已解决（Wave 5.1d）** | `whale-auth/db/init.sql` 种子 admin 的 BCrypt 密文与注释口令「123456」不符，按文档无法登录 | [reviews 5.1c §三-3](../reviews/2026-10-17-wave5.1c-runtime-verification.md)；[5.1d §三-3](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md) | 无 | P2 | 注释口令可实际登录，或注释与密文一致并注明生成方式 | **已解**：重生成 BCrypt 密文；`LiveAuthDatabaseIT` 断言翻转为 `matches("123456")==true` 并通过；端到端 `admin/123456` 登录 `code=0` |

### ① 安全收口残余

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-01** | 网关侧同步 JWT 占位密钥校验（`whale-gateway` 启动时非 dev 占位密钥拒绝） | [devlog §12/§13 待做](../devlog/2026-10-17.md)；REQ-SEC-005 延伸 | 无 | P2 | 网关非 dev + 占位 `WHALE_GATEWAY_JWT_SECRET` 启动失败并告警 | 无 |
| ~~**AUTH-02**~~ ✅ **已闭环（Wave 5.3）** | 统一 jjwt 版本：parent 管理 `0.12.5`，gateway `pom.xml` 硬编码 `0.12.6`（未走父管理） | [next-steps §五-4](next-steps.md)；[devlog §13 待做](../devlog/2026-10-17.md)；`whale-parent/pom.xml:50`、`whale-gateway/pom.xml:60` | 无 | P2 | 全模块 jjwt 版本一致（`mvn dependency:tree` 无分裂） | **已完成**：三处 `<version>` 删除，统一由 `whale-parent` 管理；`dependency:tree "-Dincludes=io.jsonwebtoken:*"` 实测 api/impl/jackson **均为 0.12.5** |
| ~~**AUTH-03**~~ ✅ **已定稿（Wave 5.3，ADR）** | 评估 refreshToken「一次一换」轮换策略（登出双写已做，但无一次性消费约束，存在重放窗口） | [devlog §13 待做](../devlog/2026-10-17.md)；REQ-SEC-004 | AUTH-14（DDD）可选 | P2 | 策略经决策记录（ADR）定稿；若采纳则旧 refreshToken 使用后即失效并有单测 | **策略已定稿**：[ADR-001](../design/adr/2026-10-17-refresh-token-rotation.md) **采纳**「一换一 + 旧 token 黑名单 + 复用检测（family 吊销）」；**实施另立 AUTH-39**，本轮不实现 |
| ~~**AUTH-04**~~ ✅ **已闭环（Wave 5.2）** | 补全接口权限注解：原 34 个接口**仅 3 处** `@RequirePermission`，用户更新/角色·菜单·权限·部门全部写接口、分配角色/权限、ABAC 属性写入 **均无鉴权** | Grep 统计 `controller/`；[ddd-whale-auth.md](../design/ddd-whale-auth.md) §1.1「权限校验在横切层兜底」 | 无 | **P1** | 所有写接口均带 `@RequirePermission`；无权限调用返回 403（`RestAccessDeniedHandler`） | **已完成**：**31 处补齐（3 → 31）**，权限码 `sys:<资源>:<动作>`；`db/init.sql` 新增 **22 条权限点种子**（与注解 `value` 一一对应）+ 2 个菜单项；除 3 个白名单接口外**全部接口均鉴权** |
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
| ~~**AUTH-12**~~ ✅ **已闭环（Wave 5.3）** | 审计埋点补全：现仅 5 处，缺 用户更新/删除、角色·菜单·权限·部门全部写操作、改密/启停、ABAC 属性写入。**前置已解除**：切面装配时序缺陷（`@ConditionalOnBean`）已于 Wave 5.1e 修复（改 `ObjectProvider` 延迟解析），并实机验证 `audit_log` 落库生效，故本任务现在具备可验证前提 | [audit-log.md](../design/audit-log.md) §2 事件分类 vs §12 埋点现状；[reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md](../reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md) | 无 | P2 | §2 各分类的敏感写操作均产生审计记录 | **已完成**：**新增 15 处 `@AuditLog`（5 → 20）**，覆盖 §2「授权变更 / 数据变更 / 组织变更 / 敏感操作」全部写操作；改密埋点 `recordParams=false`（口令不落库）；auth 运行日志无「审计日志落库失败」告警 |
| **AUTH-13** | `recordResult()` 落库（注解属性已定义但无对应列；需先补列） | [reviews §三-#5](../reviews/2026-10-17-wave5.1b-audit-log.md)；`common/audit/annotation/AuditLog.java` | 补 `audit_log` 列 | P3 | 需记录返回体的接口落库后可查 | 无 |
| **AUTH-39**（新增，Wave 5.3） | **refreshToken 一次性轮换实施**（ADR-001 已采纳）：旧 token 黑名单 + 复用检测（family 吊销）+ 前端 token 持久化同步 + 单测 | [ADR-001](../design/adr/2026-10-17-refresh-token-rotation.md)；REQ-SEC-004 | AUTH-32、AUTH-34（前端 token 持久化） | P2 | 旧 refreshToken 用后即失效；复用触发 family 吊销并审计 | 无（待排期 Wave 6） |

### ③ DDD 迁移（REQ-ARCH-001）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| ~~**AUTH-14**~~ ✅ **已闭环（Wave 5.2）** ↔ **T5.13 / T4.4** | 迁移第 1 步：四层目录骨架（`interfaces/application/domain/infrastructure`）+ 值对象（`Username`/`EncryptedPassword`/`RoleCode`/`PermCode`）+ 契约冻结清单 | [next-steps T5.13](next-steps.md)、[task-breakdown T4.4](task-breakdown.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md) §7/§8 T-DDD-1~3 | 设计已定稿 | **P1** | 新增目录不改变现有 REST 契约；编译 + 既有接口回归通过；domain 无框架注解 | **已完成**：四层骨架 + 5 个 `package-info.java`（依赖规则固化，domain 禁框架注解）+ 4 个值对象（构造即校验、`EncryptedPassword` 掩码）+ 3 个 ServiceImpl 前置校验；**契约冻结清单已入 [ddd-whale-auth.md](../design/ddd-whale-auth.md) §10**；30 用例全绿、REST 契约不变 |
| **AUTH-15** ↔ **T5.14** | 迁移第 2 步：`User` 聚合 + 仓储接口（domain）、PO 与 `RepositoryImpl`（infrastructure，MapStruct） | [next-steps T5.14](next-steps.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md) T-DDD-4/8 | AUTH-14 | P2 | 登录链路改走新仓储，REST 契约不变；PO↔领域对象字段比对单测 | 无 |
| **AUTH-16** ↔ **T5.15** | 迁移第 3 步：`Role`/`Menu`/`Permission`/`Dept` 聚合迁移 + feature flag（`whale.auth.ddd.{context}.enabled`）灰度 | [next-steps T5.15](next-steps.md)、[ddd-whale-auth.md](../design/ddd-whale-auth.md) T-DDD-5~7/10 | AUTH-15 | P2 | 开关可秒级回滚；新旧实现对比一致 | 无 |
| **AUTH-17** ↔ T-DDD-9~12 | 迁移第 4 步：`application` 层 AppService + Command/Query、领域事件发布、清理旧 `ServiceImpl` | [ddd-whale-auth.md](../design/ddd-whale-auth.md) §7/§8 | AUTH-16 | P3 | 用例编排完成；事件可被订阅；无残留旧实现 | 无 |

### ④ 分布式与幂等

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| ~~**AUTH-18**~~ ✅ **已闭环（Wave 5.6）** ↔ **T5.3 / T-DIST-1** | 幂等组件 `@Idempotent`（Token/唯一键），先用于用户注册/创建；唯一索引对齐（现为"先查重再插入"，有并发窗口） | [next-steps T5.3](next-steps.md)、[distributed.md](../design/distributed.md) §3.1 | 无 | P2 | 重复提交返回同一结果且不产生重复数据；`uk_username` 兜底 | **已完成**：`whale-spring-boot-starter` 新增 `@Idempotent` + `IdempotentAspect`（Redisson `RBucket.trySet`＝SETNX + 结果 JSON 缓存 + 异常释放 Key）+ 自动配置；`UserController#create` 接入，键 `whale:auth:user:create:<token>`；6 单测全绿。唯一索引沿用既有 `uk_username`（未新增，避免与 `deleted` 逻辑删除冲突） |
| **AUTH-19** ↔ T-LOCK-4 / REQ-DIST-002 | `@DistributedLock` 场景接入（注册幂等 / 权限变更 `lock:auth:role:{roleId}` / **缓存防击穿：同 Key 并发仅一次回源**）+ 并发验证（Redis 本机已可用） | [devlog §26 待做](../devlog/2026-10-18.md)、[distributed.md](../design/distributed.md) §2.4 | ~~AUTH-18~~ ✅ Wave 5.6、Redis | P2 | 并发调用互斥；获取失败按 `failFast` 抛 `BizException` | 无（Redis 已可用） |
| ~~**AUTH-20**~~ ✅ **已闭环（工具落地，Wave 5.6）** ↔ **T5.5 / T-DIST-2** | 缓存一致性工具（Cache-Aside + 延迟双删），统一权限判定缓存（现 `PermissionInterceptor` 兜底每次直查 DB） | [next-steps T5.5](next-steps.md)、[distributed.md](../design/distributed.md) §3.2、[ddd-whale-auth.md](../design/ddd-whale-auth.md) §4.2 | 无 | P2 | 工具类单测通过；权限变更后缓存刷新一致 | **工具已落地**：`CacheConsistencyTemplate`（`getOrLoad` 空哨兵防穿透 + TTL ±10% 抖动防雪崩 + `evict`/`evictWithDelay` 延迟双删；Redis 异常降级告警），键 `whale:cache:<name>:<id>`；8 单测全绿。**场景改造待做**：`PermissionInterceptor` 权限判定缓存接入（防击穿另由 AUTH-19 承担） |

### ⑤ 接口 / 功能缺口

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| ~~**AUTH-21**~~ ✅ **已闭环（Wave 5.2）** | 当前用户菜单/权限接口（`/auth/me`、用户菜单树、按钮权限码）：原 `LoginResponse` 仅含 roles，前端无动态路由与按钮级权限所需数据 | [roadmap §2.1/§3.3](roadmap.md)（"动态路由 + 按钮级权限渲染"未勾选）；`controller/` 无对应接口 | 无 | **P1** | 登录后按用户返回菜单树 + 权限码；前端可据此渲染 | **已完成**：新增 `GET /auth/me`（`AuthMeResponse`：userId/username/nickname/**roles/permissions/menus**）；`SysMenuService#treeByPermissionCodes` 按权限码过滤并**补齐祖先目录**；超管取全量启用权限码与全部启用可见菜单；3 个新用例覆盖（超管/普通用户/匿名） |
| ~~**AUTH-22**~~ ✅ **已闭环（Wave 5.3）** | 改密 / 重置密码 / 启停用户接口（审计 §2 列为高优敏感操作，当前无对应接口） | [audit-log.md](../design/audit-log.md) §2「敏感操作：改密、重置密码、启停用户」；`controller/` 无对应接口 | AUTH-12（埋点） | P2 | 改密/启停可用且必留痕；密码字段脱敏 | **已完成**：新增 `PUT /user/{id}/password`（`sys:user:resetPwd`，`recordParams=false`）与 `PUT /user/{id}/status`（`sys:user:status`），均带 `@RequirePermission` + `@AuditLog`；口令经 `PasswordEncoder`+`EncryptedPassword` 落库；`init.sql` 新增 2 条权限点种子（id 23/24）；新增 4 单测；经网关实测两端点 `code=0` 且改密后重登成功 |

### ⑥ 测试与质量（REQ-QA-002 / REQ-CICD-001）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| ~~**AUTH-23**~~ ✅ **已闭环（Wave 5.7）** ↔ **T5.19 延伸** | 补齐 0 覆盖类单测：6 个 `ServiceImpl`、`PermissionInterceptor`、`JwtAuthenticationFilter`、`UserDetailsServiceImpl`、`SecretPlaceholderChecker` | `src/test/` 目录事实；[next-steps T5.19](next-steps.md) | 无 | P2 | 新增用例全绿；覆盖正常/降级/边界 | **已完成**（Wave 5.2 值对象/菜单；Wave 5.3 `SysUserServiceImpl`；Wave 5.4 4 类 21 用例；**Wave 5.7 补齐 4 个零覆盖 `ServiceImpl`：`SysRoleServiceImplTest` 21 / `SysPermissionServiceImplTest` 13 / `SysDeptServiceImplTest` 8 / `SysUserPermissionServiceImplTest` 2，共 44 用例 → `whale-auth` 55 → 99 全绿**）；`whale-auth` 行覆盖 **0.6755**、分支 **0.6718**（`clean verify` 口径），`-Pcoverage-gate` 通过。**残余**：控制器层单测、`replaceUserAttrs` 非空批量写路径（需真实 Mapper 代理） |
| ~~**AUTH-24**~~ ✅ **已闭环（Wave 5.6）** ↔ **T5.18** | JaCoCo 覆盖率接入（阈值 40% 行 / 60% 分支 / 增量 70%） | [next-steps T5.18](next-steps.md)、[register REQ-CICD-001](../requirements/register.md) | 无 | P2 | 构建产出覆盖率报告并执行阈值校验 | **已完成**：`whale-parent` 新增 `jacoco 0.8.12`（`prepare-agent` + `report` 默认执行）与 `coverage-gate` profile（`check` 行 ≥40% / 分支 ≥30%）；公共库行覆盖 **68.01%** 通过、`whale-auth` 行覆盖 **0.37** 按预期 BUILD FAILURE（门禁生效）。**阈值取 40%/30%**（原设计 60% 分支对存量不现实，已在 spec 记录偏差）；`coverage-gate` 接入 CI 待 AUTH-23 达标后启用 |
| ~~**AUTH-25**~~ ✅ **已闭环（Wave 5.4）** ↔ **T5.17** | Checkstyle 门禁由 `warning` 提升为 `error`（真正阻断） | [next-steps T5.17](next-steps.md)、[devlog §24](../devlog/2026-10-17.md) | ~~AUTH-26~~ ✅ Wave 5.2 | P2 | 违规时 CI 失败 | **已完成**：`config/checkstyle/whale-checkstyle.xml` 默认 `severity` → `error`；全 reactor `mvn checkstyle:check` **BUILD SUCCESS / 0 违规**（违规即阻断） |
| ~~**AUTH-26**~~ ✅ **已闭环（Wave 5.2）** | 清理存量 WARN：3 处未使用 import（`AssignPermissionsRequest.java` 的 `NotBlank`/`Size`、`GatewayExceptionHandler.java` 的 `Ordered`） | [reviews §五-5](../reviews/2026-10-17-wave5.1b-audit-log.md) | 无 | P3 | `checkstyle:check` 0 WARN | **已完成**：删除 3 处 import → `checkstyle:check` **0 WARN**（**AUTH-25 提级为 error 的前置条件已具备**） |
| **AUTH-27** ↔ **T5.20** | 全量回归：`mvn clean install -T 1C` + `pnpm build`，按清单出报告后提交 | [next-steps T5.20](next-steps.md) | 上述任务 | P2 | 后端 exit 0 / 前端 11 包成功；报告归档 | 无 |
| ~~**AUTH-28**~~ ✅ **已闭环（Wave 5.6，实机触发待验证）** ↔ **T5.16** | Gitee Go 流水线映射（按 [cicd.md](../design/cicd.md) 第 6 节三条 Workflow） | [next-steps T5.16](next-steps.md)、[register REQ-CICD-001](../requirements/register.md) | 无 | P2 | 推送后 Gitee 可见流水线结果 | **已完成（即代码）**：新增 [.workflow/backend-ci.yml](../../.workflow/backend-ci.yml)（checkstyle → compile+test → package）与 [.workflow/frontend-ci.yml](../../.workflow/frontend-ci.yml)。**残余**：Gitee 官方云端 `build@maven`/`build@nodejs` 插件文档列 JDK ≤17 / Node ≤15.12.0，本项目要求 JDK 21 / Node 24，**实机触发与插件版本兼容性待 Gitee 侧验证**（YAML 已注释标注） |

### ⑦ 网关与联调

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| ~~**AUTH-29**~~ ✅ **已闭环（Wave 5.1f）** | 确认并修复网关路由前缀冲突：`Path=/auth/**` + `StripPrefix=1` 会把 `/auth/login` 剥成 `/login`，与 `AuthController @RequestMapping("/auth")` 冲突；审计路由同理 | [reviews §三-#2（P1）](../reviews/2026-10-17-wave5.1b-audit-log.md)；[audit-log.md](../design/audit-log.md) §9；`whale-gateway/application.yml` | — | **P1** | 定夺「改路由」或「改控制器前缀」，经网关访问 `/auth/**`、`/audit/**` 均 200 | **已修**：定夺为「**改路由**」——新增 REQ-ARCH-003（服务自持一级前缀，网关不剥离），移除**全部 8 条**路由的 `StripPrefix`；实测经网关 login 200/`code=0`、黑名单重放 401 |
| ~~**AUTH-38**~~ ✅ **已闭环（Wave 5.3，含经网关冒烟）**（新增，Wave 5.1f 发现） | 补齐 auth 其余一级前缀的网关路由：`/user/**`、`/dept/**`、`/role/**`、`/menu/**`、`/permission/**` → `lb://whale-auth` | [reviews/2026-10-17-wave5.1f…](../reviews/2026-10-17-wave5.1f-gateway-routing-and-e2e.md) §四-1；[architecture.md §网关路由与前缀约定](../architecture.md) | 无（Nacos 已就绪） | **P1** | 经网关访问 `/user/page` 等管理接口可达（不再 404） | **已完成（配置 + 冒烟）**：`whale-auth` 路由 `Path` 扩为 `/auth/**,/user/**,/dept/**,/role/**,/menu/**,/permission/**`。**Wave 5.3 完成经网关冒烟**：起 Nacos 8848 + auth 8081 + gateway 8080（新构建），`login code=0`；`/user/page`、`/user/1`、`/role/page`、`/menu/tree`、`/dept/tree`、`/permission/page` **全部 200 + `code=0`**；无 token → 401 |
| ~~**AUTH-30**~~ ✅ **已闭环（Wave 5.6，Sentinel 转 backlog）** ↔ **T5.4 / T-DIST-4** | 网关限流熔断：接入 Sentinel（或 Redis 计数器兜底）；`whale-gateway` 现无任何 Sentinel/`RequestRateLimiter` 依赖 | [next-steps T5.4](next-steps.md)、[distributed.md](../design/distributed.md) §3.4；Grep `whale-gateway` 无匹配 | Nacos 联调 | P2 | 超阈值返回 429 + `Result` 体 | **已完成（Redis 计数器兜底）**：新增 `RateLimitGlobalFilter`（`GlobalFilter` 固定窗口计数，键 `whale:gateway:rate-limit:<窗口>:<客户端>`；超阈值 **429 + `GatewayResponse` 同结构体 + TraceId 透传**；Redis 异常降级放行）+ `RateLimitProperties`；4 单测全绿。**偏差**：设计 §3.4 推荐 Sentinel，本机无 Dashboard + Boot 4 兼容未实测 → **Sentinel 接入转 backlog**。**残余**：端到端 429 冒烟（本机 6379/8848/8080 未监听） |
| ~~**AUTH-31**~~ ✅ **已闭环（Wave 5.1f）** | 端到端验证：① auth 直连验证登出→401、refresh→401、审计落库；② 经网关全链路 | [register REQ-SEC-002/003/004](../requirements/register.md)（**已验证**）；[next-steps §五-1/§五-5](next-steps.md) | — | **P1** | ①② 均通过 | **已完成**：① Wave 5.1c/5.1e 已闭环；② **Wave 5.1f 首次打通**（login `code=0` → logout `code=0` → 重放 401 → refresh 业务码 401 → `audit_log` 落 LOGIN/LOGOUT） |

### ⑧ 前端对接（REQ-FUNC-002 / 008 / 009）

| 任务 | 内容 | 来源 | 依赖 | 优先级 | 验收口径 | 当前阻塞 |
|---|---|---|---|---|---|---|
| **AUTH-32** ↔ **T5.21** | `frontend/admin` 登录对接 `/auth/login` + 登出调 `/auth/logout` | [next-steps T5.21](next-steps.md)、[roadmap §2.1](roadmap.md) | ~~**AUTH-38**（auth 管理前缀路由）~~ ✅ Wave 5.2、~~AUTH-21~~ ✅ Wave 5.2 | P2 | 登出后原 token 请求返回 401 | **前置均已解除**：`/auth/login`、`/auth/me` 与全部管理前缀路由已配，**经网关冒烟已通过（Wave 5.3）** |
| **AUTH-33** ↔ **T5.22** | `whale-ui`：ProTable / FormModal / SearchForm 二次封装 | [next-steps T5.22](next-steps.md)、[roadmap §3.2](roadmap.md) | 无 | P2 | 组件可独立构建，有示例页 | 无 |
| **AUTH-34** ↔ **T5.23** | admin 用户/角色/菜单管理页 + 按钮级权限渲染 | [next-steps T5.23](next-steps.md)、[roadmap §3.3](roadmap.md) | AUTH-32、AUTH-33、~~AUTH-21~~ ✅ Wave 5.2 | P2 | 页面可完成 CRUD 且按权限隐藏按钮 | **AUTH-21 已解除**（`/auth/me` 提供 permissions/menus）；余受 AUTH-32/33 牵连 |

---

## 三、依赖关系与建议执行顺序

```
Wave 0｜启动阻塞 ✅ **已解除**（Wave 5.1d：AUTH-35/36/37 全部关闭）
  └─ ✅ ~~AUTH-35 redisson 升级 4.8.0 实测兼容（auth 8081 / audit 9101 启动成功）~~ ──► 解锁 Wave C、Wave D 运行时验证
       └─ ✅ ~~AUTH-36 数据源字符集参数~~ ｜ ✅ ~~AUTH-37 种子口令对齐~~
       └─ ✅ ~~遗留已闭环（Wave 5.1e）：审计切面装配缺陷已修并实机验证落库（原 `audit_log` 0 行 → 6 行）；refresh 返回码口径已澄清（HTTP 200 + 业务码 401，既有约定非缺陷）~~
       └─ ⏳ 新增遗留（Wave 5.1e）：阶段一审计**跨库可见性**约束（查询服务须与写入服务同库/同实例）

Wave A｜无外部依赖，可立即动工（Redis/MySQL 本机已可用）
  ├─ ✅ ~~AUTH-04 权限注解覆盖补全（P1，纯安全缺口）~~（Wave 5.2：3 → 31 + 22 权限点种子）
  ├─ ✅ ~~AUTH-14 DDD 骨架 + 值对象（P1，↔ T5.13）~~（Wave 5.2：四层骨架 + 4 值对象 + 契约冻结清单）
  ├─ ✅ ~~AUTH-21 当前用户菜单/权限接口（P1，解锁前端）~~（Wave 5.2：`/auth/me`）
  ├─ AUTH-02 jjwt 版本统一 ｜ AUTH-05 黑名单降级告警 ｜ AUTH-12/13 审计补口 ｜ AUTH-11 脱敏配置化
  ├─ AUTH-18 幂等组件（↔ T5.3）──► AUTH-19 锁场景接入 ──► AUTH-20 缓存一致性
  ├─ AUTH-22 改密/启停接口 ｜ ✅ ~~AUTH-26 存量 WARN 清理~~（Wave 5.2：0 WARN）
  └─ ✅ ~~AUTH-23 单测补口~~（Wave 5.7：4 个 ServiceImpl 44 用例）｜ ✅ ~~AUTH-24 JaCoCo~~ ｜ ✅ ~~AUTH-28 Gitee Go~~（可与上并行）

Wave B｜依赖 Wave A 产物
  ├─ AUTH-15 User 聚合 ──► AUTH-16 Role/Menu/Permission/Dept 聚合 ──► AUTH-17 AppService/领域事件
  ├─ AUTH-06 审计查询鉴权 ──► AUTH-07 元审计
  ├─ AUTH-01 网关占位密钥 ｜ AUTH-03 refresh 轮换策略
  └─ AUTH-25 Checkstyle 提级 ──► AUTH-27 全量回归 ──► 提交推送

Wave C｜Redis/MySQL 已就绪，本机可跑（auth 直连）
  └─ ✅ ~~AUTH-31① 登出→401、refresh→401、审计落库（不经网关）~~（Wave 5.1c/5.1e 已闭环）

Wave D｜✅ **Nacos 阻塞已解除（Wave 5.1f：Nacos 3.2.4 以 MySQL 外置存储就绪）**
  ├─ ✅ ~~AUTH-29 网关路由前缀冲突~~（已修 → REQ-ARCH-003）
  ├─ ✅ ~~AUTH-31② 网关→auth 全链路端到端~~（首次打通）
  ├─ ✅ ~~AUTH-38 补齐 auth 管理前缀网关路由（P1，前端对接前置）~~（Wave 5.2：Path 扩为 `/auth,/user,/dept,/role,/menu,/permission`；**Wave 5.3 经网关冒烟通过**）
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
| 1 | **refreshToken 单次轮换策略未定** → **✅ 已定稿（Wave 5.3，AUTH-03）** | 登出已双写黑名单，但 refreshToken 无「一次一换」约束，重放窗口仍存在。**处置**：[ADR-001](../design/adr/2026-10-17-refresh-token-rotation.md) 采纳「一换一 + 旧 token 黑名单 + 复用检测」，**实施另立 AUTH-39** | [devlog §13 待做](../devlog/2026-10-17.md)、[ADR-001](../design/adr/2026-10-17-refresh-token-rotation.md) |
| ~~2~~ | ~~**jjwt 版本分裂**~~ → ✅ **已统一（Wave 5.3，AUTH-02）** | ~~parent 管理 `0.12.5`，`whale-gateway/pom.xml` 硬编码 `0.12.6`，未走父管理，存在行为差异隐患~~。**处置**：移除 gateway 三处硬编码 `<version>`，`dependency:tree` 实测三件均 **0.12.5** | [next-steps §五-4](next-steps.md)、`whale-parent/pom.xml:50`、`whale-gateway/pom.xml` |
| ~~3~~ | ~~**网关路由前缀未确认**~~ → ✅ **已确认并修复（Wave 5.1f）** | 定夺结论：**改路由**（服务自持一级前缀、网关不剥离）→ REQ-ARCH-003；原新增风险 **AUTH-38**（auth 的 `/user`、`/dept`、`/role`、`/menu`、`/permission` 前缀无网关路由）**已于 Wave 5.2 补齐路由**，**经网关冒烟亦已于 Wave 5.3 通过** | [reviews/wave5.1f](../reviews/2026-10-17-wave5.1f-gateway-routing-and-e2e.md) §四-1 |
| 4 | **黑名单降级语义** | Redis 不可用时 auth 与网关均按未命中放行 → 登出形同失效（可用性优先取舍，无告警） | [next-steps §五-2](next-steps.md) |
| 5 | ~~**权限注解覆盖不足（证据确凿）**~~ → ✅ **已消除（Wave 5.2）** | 34 接口仅 3 处 `@RequirePermission`，大量写接口无鉴权，越权风险。**处置**：补齐 31 处（3 → 31）+ 22 条权限点种子；非超管 RBAC 判定不再必然 403 | Grep `controller/` 统计；AUTH-04 |
| ~~6~~ | ~~**Nacos 不可用**~~ → ✅ **已解除（Wave 5.1f）** | Nacos 3.2.4 以 MySQL 外置存储就绪（8848/8849），auth/audit 均 `register finished`；RabbitMQ / Nginx 仍缺（影响 AUTH-08/10） | [reviews/wave5.1f](../reviews/2026-10-17-wave5.1f-gateway-routing-and-e2e.md) |
| ~~7~~ | ~~**运行时/端到端验证深度不足**~~ → ✅ **已闭环（Wave 5.1f）** | Wave 5.1c 已跑通黑名单/分布式锁/审计落库/查询；**Wave 5.1f 补齐 HTTP 端到端与网关全链路**（login→logout→重放 401→refresh 业务码 401→审计 LOGIN/LOGOUT 落库） | [reviews/wave5.1f](../reviews/2026-10-17-wave5.1f-gateway-routing-and-e2e.md) §二-3 |
| 7b | **审计跨库可见性（阶段一固有）** | auth 埋点落 `bluewhale_auth.audit_log`，whale-audit 查询接口读 `bluewhale_audit` → 阶段二 MQ 汇聚（AUTH-10）前，`/audit/log/page` 查不到 auth 事件 | [devlog §17/§18](../devlog/2026-10-17.md)、[design/audit-log.md](../design/audit-log.md) §194 |
| 8 | **审计埋点不全** | 仅 5 处埋点，与设计 §2 事件分类（数据变更/敏感操作/组织变更）差距大 | [audit-log.md](../design/audit-log.md) §2 vs §12 |
| 9 | **`@DistributedLock` 未接入任何场景** | 工具已落地但零场景接入，无并发实证 | [devlog §12 待做](../devlog/2026-10-17.md) |
| 10 | **权限判定无缓存** | `PermissionInterceptor` 每次兜底直查 DB，高并发下为性能瓶颈，且缓存一致性方案未落地 | [ddd-whale-auth.md](../design/ddd-whale-auth.md) §1.1 |
| 11 | **前端 admin 未对接** | roadmap §2.1「对接 admin 前端登录」未勾选，REQ-FUNC-002 仍标「API 齐备，前端未对接」 | [roadmap.md](roadmap.md) §2.1、[register REQ-FUNC-002](../requirements/register.md) |
| 12 | **`recordResult()` 属性悬空** | 注解已定义但无对应列，属"已声明未实现"，易误判能力已具备 | [reviews §三-#5](../reviews/2026-10-17-wave5.1b-audit-log.md) |
| 13 | ~~**服务无法启动（P0，实测）**~~ → ✅ **已消除（Wave 5.1d）** | `redisson-spring-boot-starter:3.34.1` 引用 Boot 4 已移除的 `RedisProperties` → whale-auth/whale-audit/whale-pay `java -jar` 启动即 `ClassNotFoundException`。**处置**：升级至 **4.8.0**（按 Boot 4.1 编译）实测兼容，auth 8081 / audit 9101 启动成功，`LiveDistributedLockIT` 通过 | [reviews 5.1c §三-1](../reviews/2026-10-17-wave5.1c-runtime-verification.md)、[reviews 5.1d §三-1](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md)；AUTH-35 |
| 14 | ~~**数据源字符集参数不合法**~~ → ✅ **已消除（Wave 5.1d）** | `characterEncoding=utf8mb4` 被 Connector/J 拒绝。**处置**：auth/audit yml 改为 `utf8&connectionCollation=utf8mb4_general_ci`，真实连库成功 | [reviews 5.1c §三-2](../reviews/2026-10-17-wave5.1c-runtime-verification.md)、[reviews 5.1d §三-2](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md)；AUTH-36 |
| 15 | ~~**种子数据与文档不一致**~~ → ✅ **已消除（Wave 5.1d）** | `init.sql` 注释称 admin 口令为 `123456`，旧密文不匹配。**处置**：重生成密文 + 断言翻转并通过，端到端 `admin/123456` 登录 `code=0` | [reviews 5.1c §三-3](../reviews/2026-10-17-wave5.1c-runtime-verification.md)、[reviews 5.1d §三-3](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md)；AUTH-37 |
| 16 | ~~**审计切面从未装配（P1，既有缺陷）**~~ → ✅ **已消除（Wave 5.1e 实机验证）** | `AuditLogAutoConfiguration` 的 `@ConditionalOnBean(AuditLogMapper.class)` 在配置类解析期求值，**早于** `@MapperScan`（`MapperScannerConfigurer`）注册 Mapper Bean → 条件恒不成立、`AuditLogAspect` 从未装配、真实服务链路 `audit_log` 落库 0 行（`LiveAuditLogIT` 直连 Mapper 可过，掩盖该缺陷）。**处置**：删除 `@ConditionalOnBean`，改 `ObjectProvider<AuditLogMapper>` 于 Bean 实例化期延迟解析；实测 `bluewhale_auth.audit_log` 由 0 行 → 6 行（LOGIN/LOGOUT、`biz_status=1`、`request_params` 无凭据），`/audit/log/page` 可查、`LiveAuditLogIT` 3/0/0 | [reviews 5.1d §三-5](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md)、[reviews 5.1e](../reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md) |
| 17 | ~~**`/auth/refresh` 返回码口径（P2）**~~ → ✅ **已澄清（非缺陷）** | 登出后 refresh 实测 **HTTP 200 + 业务码 401**，为 `Result` 全局统一包装的既有约定（业务码承载语义、HTTP 层恒 200），非缺陷；验收口径统一改为「**业务码 401**」 | [reviews 5.1d §三-4](../reviews/2026-10-17-wave5.1d-boot4-deps-and-startup.md)、[reviews 5.1e §四](../reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md) |
| 18 | **阶段一审计跨库可见性（P2，固有约束，新发现）** | 阶段一采用「各服务写各自实例库」（auth 写 `bluewhale_auth.audit_log`），而 `whale-audit` 默认读 `bluewhale_audit` → **默认查不到其他服务写入的审计记录**。实测：把 `whale-audit` 数据源临时指向 `bluewhale_auth` 后 `/audit/log/page` 立即可见 `total=4` 条 → 证实「查询服务须与写入服务同库/同实例」。阶段二 MQ 汇聚（AUTH-10）为根治方案 | [reviews 5.1e §四](../reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md)；[audit-log.md](../design/audit-log.md) §6 |
| 19 | **阶段一审计查询无鉴权（承接 AUTH-06）** | `AuditLogController` 无 `@RequirePermission`，审计数据可被任意调用者读取，属敏感数据暴露面 | [audit-log.md](../design/audit-log.md) §9；AUTH-06 |

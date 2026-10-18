# 下一步建议与任务清单（Wave 5）

> 关联需求：REQ-PROC-002
> 生成日期：2026-10-17 ｜ 依据：`docs/devlog/2026-10-17.md`、`docs/requirements/register.md`
> 执行原则：**先落文档（REQ-DOC-002）→ 编码 → 按清单自检留报告（REQ-QA-002）→ 构建验证 → 提交**

---

## 一、当前状态快照

| 维度 | 现状 |
|---|---|
| 后端 | 25 模块可构建；auth/gateway/i18n 核心能力就绪；Redis 首次被 auth 使用（令牌黑名单） |
| 前端 | 11 包可构建；基座登录 + 主布局就绪；admin / whale-ui / portal 未启动 |
| 工程规范 | 需求登记册、检查清单、检查报告机制、Checkstyle 门禁 已建立 |
| 分布式 | 方案已定稿；**已落地 5 项**（令牌黑名单 auth+网关 / ID 序列化 / 分布式锁工具 / **幂等组件（Wave 5.6，T5.3）** / **缓存一致性工具（Wave 5.6，T5.5）**）+ **网关限流（Wave 5.6，T5.4，Redis 计数器兜底）**；分布式事务未落地；Wave 5.1c 实机验证：令牌黑名单与分布式锁**通过**；Wave 5.1d：**redisson 升级 4.8.0 后服务可真实启动** |
| 审计 | 注解/切面/脱敏/查询接口已落地；auth 已埋点；**Wave 5.1e 修复既有缺陷并实机验证落库**：`AuditLogAutoConfiguration` 去掉时序不可靠的 `@ConditionalOnBean(AuditLogMapper)`，改 `ObjectProvider` 延迟解析 → 真实服务链路审计切面**已装配**，`bluewhale_auth.audit_log` 由 0 行 → 6 行（LOGIN/LOGOUT、`biz_status=1`、`request_params` 无凭据），`/audit/log/page` 可查、`LiveAuditLogIT` 3/0/0 |
| 架构 | auth DDD 方案已定稿；代码未迁移 |
| 阻塞项 | Redis/MySQL **已可用**（Wave 5.1c/5.1d 实机验证；MySQL 3306 以任务书凭据直连）；~~**P0**：`redisson-spring-boot-starter 3.34.1` 与 Spring Boot 4.0.8 不兼容~~ → **✅ 已解除（Wave 5.1d：升级 4.8.0 实测兼容，auth 8081 / audit 9101 启动成功）**；**Nacos 8848 未监听** → 网关/`lb://` 未验证 |

---

## 二、Wave 5 任务清单

> **Wave 5.0 执行结果（2026-10-17 更新）**：✅ T5.1 ｜ ✅ T5.2 ｜ ✅ T5.8 ｜ ✅ T5.9 ｜ ✅ T5.19。
> 均以「全模块编译 + 新增单测（13 用例全绿）+ Checkstyle 0 违规」完成静态验证；因本机无 Docker，**未做运行时/端到端验证**。
> 详见 [devlog §12](../devlog/2026-10-17.md) 与 [检查报告](../reviews/2026-10-17-wave5-static-groundwork.md)。
> Wave 5.0 执行中修正的认知偏差：Boot 4.0.8 **已移除** `spring-boot-starter-aop`（改用 `org.aspectj:aspectjweaver`）；HTTP JSON 序列化确为 **Jackson 3**（`JsonMapperBuilderCustomizer` 为唯一定制钩子）。
>
> **Wave 5.1 执行结果（2026-10-17 更新）**：✅ T5.6（REQ-SEC-004，P1）｜ ✅ T5.7（REQ-SEC-003）。
> 以「全模块编译 + 27 单测全绿 + Checkstyle 0 违规」完成静态验证；仍无 Docker，端到端待中间件就绪。
> 详见 [devlog §13](../devlog/2026-10-17.md) 与 [检查报告](../reviews/2026-10-17-wave5.1-security-closure.md)。
>
> **Wave 5.1b 执行结果（2026-10-17 更新）**：✅ T5.10 ｜ ✅ T5.11 ｜ ✅ T5.12。
> 以「全模块编译 SUCCESS + 40 单测全绿 + Checkstyle 0 违规」完成静态验证（审计核心下沉 `whale-spring-boot-starter` + `whale-audit` 查询接口 + auth 埋点）。
> 因本机无 Docker，**未做运行时/端到端验证**。
> 详见 [devlog §14](../devlog/2026-10-17.md) 与 [检查报告](../reviews/2026-10-17-wave5.1b-audit-log.md)。
>
> **Wave 5.2 执行结果（2026-10-17 更新）**：✅ T5.13（权限服务 DDD 骨架 + 值对象，↔ AUTH-14）。
> 同步闭环 AUTH-38（auth 管理前缀网关路由）、AUTH-04（权限注解 3 → 31 + 22 权限点种子）、AUTH-21（`GET /auth/me`）、AUTH-26（Checkstyle 0 WARN）。
> 以「30 单测全绿 + Checkstyle 0 WARN」完成静态验证；网关路由为配置变更，原「经网关冒烟待做」**已于 Wave 5.3 实机验证通过**（AUTH-38：起 Nacos + auth/gateway 后，`/user/page`、`/auth/me` 等全部 HTTP 200 且 `code=0`，无 token 访问 → 401）。
> 详见 [devlog §22](../devlog/2026-10-17.md) 与 [检查报告](../reviews/2026-10-17-wave5.2-auth-p1-closure.md)。
>
> **Wave 5.3 执行结果（2026-10-17 更新）**：✅ AUTH-12（写操作审计埋点 5 → 20）、AUTH-22（新增 `PUT /user/{id}/password`、`PUT /user/{id}/status`，含权限码/审计/值对象/单测）、AUTH-02（gateway jjwt 硬编码 0.12.6 移除，由 whale-parent 统一为 0.12.5）、AUTH-03（refreshToken 轮换 [ADR-001](../design/adr/2026-10-17-refresh-token-rotation.md) 定稿，实施另立 AUTH-39）、AUTH-38（经网关冒烟通过）。
> 以「**39 单测全绿 + Checkstyle 0 违规**」完成验证；详见 [devlog §23](../devlog/2026-10-17.md) 与 [检查报告](../reviews/2026-10-17-wave5.3-auth-audit-and-account-ops.md)。
>
> **Wave 5.6 执行结果（2026-10-18 更新）**：✅ T5.3（幂等组件 `@Idempotent` + 用户创建场景接入）｜ ✅ T5.4（网关限流，**Redis 计数器兜底**替代 Sentinel）｜ ✅ T5.5（缓存一致性工具）｜ ✅ T5.16（Gitee Go 流水线即代码）｜ ✅ T5.18（JaCoCo 覆盖率 + `coverage-gate` 阈值 profile）。
> 以「**7/7 反应堆模块 SUCCESS + 96 单测全绿（starter 29 / auth 55 / gateway 12）+ Checkstyle 0 违规 + JaCoCo 公共库行覆盖 68.01%**」完成验证；`-Pcoverage-gate` 在 `whale-auth`（行 0.37 < 0.40）按预期 **BUILD FAILURE** 证明门禁生效。
> **残余**：限流端到端 429 冒烟（本机 6379/8848/8080 未监听）、`coverage-gate` 接入 CI（待 AUTH-23 达标）、Gitee Go 实机触发验证、Sentinel 接入（backlog）。
> 详见 [devlog §26](../devlog/2026-10-18.md) 与 [检查报告](../reviews/2026-10-18-wave5.6-crosscutting-infra.md)。
>
> **Wave 5.8 执行结果（2026-10-18 更新）**：✅ AUTH-20（缓存一致性工具接入权限判定场景：`PermissionInterceptor` 读路径改 Cache-Aside，角色/用户/权限点共 7 处变更点接入失效）。
> 以「**120 单测全绿（Wave 5.7 基线 99 → 120）+ Checkstyle 全 reactor 0 违规 + JaCoCo 行 0.6755 → 0.7184 / 分支 0.6718 → 0.7238（`clean verify` 口径）**」完成验证，`-Pcoverage-gate` 通过。
> **残余**：~~防击穿（同 Key 并发一次回源）→ AUTH-19~~ ✅ Wave 5.9；多实例失效广播 / Canal 演进；`SysRoleServiceImpl#update` 有意不失效；经网关端到端实测、控制器层单测。
> 详见 [devlog §28](../devlog/2026-10-18.md) 与 [检查报告](../reviews/2026-10-18-wave5.8-auth-permission-cache.md)。
>
> **Wave 5.9 执行结果（2026-10-18 更新）**：✅ ~~AUTH-19~~（`@DistributedLock` 场景接入：① 角色权限变更互斥 `lock:auth:role:{roleId}`；② 缓存防击穿 `lock:cache:auth:user:perm:{userId}`）。
> 以「**126 单测全绿（Wave 5.8 基线 120 → 126）+ Checkstyle 全 reactor 0 违规 + JaCoCo 行 0.7184 → 0.7227 / 分支 0.7238 → 0.7257（`clean verify` 口径）+ 实时 IT `LivePermissionLockIT` 2/0/0（真实 Redis）**」完成验证。
> **残余**：注册锁（`@Idempotent` 已等价覆盖）、定时任务/库存锁、T-LOCK-5 降级开关、多实例失效广播、经网关并发实测。
> 详见 [devlog §29](../devlog/2026-10-18.md) 与 [检查报告](../reviews/2026-10-18-wave5.9-auth-distributed-lock.md)。

> **Wave 5.10 执行结果（2026-10-18 更新）**：✅ ~~审计日志多通道完善（后端 Wave A，REQ-SEC-006）~~（4 表 DDL + `LogPersistSupport`/`whaleLogExecutor` + `ApiLogFilter`（tee 透传）+ 出站拦截器 + `LogIngestController` 上报/查询）。
> 同步闭环 ✅ ~~AUTH-06~~（审计查询鉴权：因 whale-audit 库无 RBAC 表，改**网关注入权限码头 + starter 通用拦截器**，权限点 `sys:audit:log:list`）+ ✅ ~~AUTH-11~~（脱敏规则配置化 `whale.audit.sensitive.keys` + `SensitiveDataMasker#maskJson`）。
> 以「**247 单测/IT 全绿（clean verify BUILD SUCCESS）+ Checkstyle 全 29 模块 0 违规 + 实时 IT `LiveApiLogIT`/`LiveFrontendLogIT` 7/0/0**」完成验证。
> **残余**：`api_log.module` 回填、Wave B 前端 `whale-log-sdk` 未开工、MQ 汇聚、保留期扩多表、异步派发丢采集、网关按路径限流。
> 详见 [devlog §30](../devlog/2026-10-18.md) 与 [检查报告](../reviews/2026-10-18-wave5.10-multichannel-log.md)。

> **Wave 5.11 执行结果（2026-10-18 更新）**：✅ ~~AUTH-01~~（网关侧占位密钥校验：`GatewaySecretPlaceholderChecker` 非 dev 占位/空密钥拒绝启动，对齐 auth 侧 `SecretPlaceholderChecker`）、✅ ~~AUTH-05~~（黑名单降级告警：`whale-security` `SecurityDegradationAlerter` ERROR + `SECURITY_DEGRADATION` 标记 + 计数，auth/网关两处降级分支接入）、✅ ~~AUTH-07~~（读审计**元审计**：`AuditLogController` 三查询接口加 `@AuditLog(type=QUERY)`）、✅ ~~AUTH-13~~（`recordResult()` 返回体落库：`audit_log.result_body` + `AuditLogAspect` 采集脱敏 + 2000 截断 + `AuditLogVO` 暴露）。
> 同步闭环 ✅ ~~B6 归置~~（`docs/whale-auth/originals/` 49 份副本 + `docs/whale-auth/final/README.md` 整合最终版，原位置不删不动）；对应 [spec](../specs/2026-10-18-wave5.11-auth-residual/spec.md)。
> 以「**`clean verify -T 1C` BUILD SUCCESS，28 模块全 SUCCESS / 263 用例全绿（security 3 / i18n-sdk 14 / gateway 23 / starter 65 / audit 31 / auth 127，0 fail·0 error·0 skipped）+ `checkstyle:check` 28 模块 0 违规（含 E16 Javadoc 门禁）**」完成验证。
> **残余**：AUTH-08（审计导出，阻塞 whale-file）/ AUTH-09（保留归档，阻塞 whale-job）/ AUTH-10（MQ 汇聚，阻塞 RabbitMQ）、AUTH-15/16/17（DDD 深水区）、AUTH-32/33/34（前端对接）、AUTH-39（refresh 轮换实施）、Micrometer 指标接入、Wave B 前端 `whale-log-sdk`。
> 详见 [devlog §31](../devlog/2026-10-18.md) 与 [检查报告](../reviews/2026-10-18-wave5.11-auth-residual.md)。

### 组 A：分布式基础落地（承接 REQ-DIST-001~003）

| 任务 | 内容 | 依赖 | 优先级 | 验收口径 | 相对规模 |
|---|---|---|---|---|---|
| ~~T5.1~~ | 分布式 ID 统一：确认各实体主键使用 `IdType.ASSIGN_ID`；`BaseEntity` 显式声明；Long → String 序列化（Jackson 3）避免前端精度丢失 | 无 | 高 | 新插入记录 ID 为 19 位雪花且前端收到字符串 | S |
| ~~T5.2~~ ✅ **已落地（Wave 5.0）+ 场景接入（Wave 5.9，AUTH-19）** | `@DistributedLock` 注解 + SpEL key 解析 + Redisson AOP 切面（或先做 `DistributedLockTemplate` 工具类，视复杂度择一） | 无（Redisson 已在 auth） | 高 | 并发调用同 key 互斥；`tryLock` 超时返回业务异常；单测覆盖锁获取/超时 | M |
| ~~T5.3~~ ✅ **已完成（Wave 5.6）** | 幂等组件：`@Idempotent` + Token/唯一键，先用于用户注册 | T5.2 | 中 | 重复提交返回同一结果，不产生重复数据 | M |
| ~~T5.4~~ ✅ **已完成（Wave 5.6，Redis 计数器兜底）** | 网关限流：接入 Sentinel（或先用 Redis 计数器兜底） | 网关 | 中 | 超阈值返回 429 + `Result` 体 | M |
| ~~T5.5~~ ✅ **已完成（Wave 5.6）** | 缓存一致性约定落地：Cache-Aside + TTL 抖动 + 空值哨兵写入公共工具；**Wave 5.8 已把工具接入权限判定场景（AUTH-20）** | 无 | 中 | 工具类单测通过；i18n 与权限缓存统一改造 | S |

### 组 B：安全补口（承接 REQ-SEC-002/003/004）

| 任务 | 内容 | 依赖 | 优先级 | 验收口径 | 相对规模 |
|---|---|---|---|---|---|
| ~~T5.6~~ | **REQ-SEC-004**：登录时签发 `jti` / token 版本号，登出与刷新时校验版本，使 refreshToken 一并失效 | T4.1 | **P1 高** | 登出后 refreshToken 调 `/auth/refresh` 返回 401 | M |
| ~~T5.7~~ | **REQ-SEC-003**：网关 `JwtAuthGlobalFilter` 增加 Redis 黑名单校验（响应式 Redis 或 Lettuce 同步降级） | T4.1 | 中 | 经网关访问下游服务的已登出 token 返回 401 | M |
| ~~T5.8~~ | 生产密钥强制校验：启动时若 `JWT_SECRET` 为默认占位值则拒绝启动（非 dev profile） | 无 | 中 | 默认值启动失败并给出明确提示 | S |

### 组 C：审计日志落地（承接 REQ-SEC-001）

| 任务 | 内容 | 依赖 | 优先级 | 验收口径 | 相对规模 |
|---|---|---|---|---|---|
| ~~T5.9~~ | `audit_log` 表补充 7 个字段（`operate_type`/`http_method`/`target_type`/`target_id`/`biz_status`/`error_msg`/`tenant_id`）的 ALTER 脚本 | 无 | 高 | `db/init.sql` 更新，字段与设计文档一致 | S |
| ~~T5.10~~ | `whale-common` 增加 `@AuditLog` 注解 + AOP 切面 + 异步写库（`afterCommit`） ✅ 已完成（Wave 5.1b） | T5.9 | 高 | auth 的登录/登出/权限变更写入审计记录 | M |
| ~~T5.11~~ | 敏感字段脱敏器（密码/手机号/身份证/银行卡）接入审计参数序列化 ✅ 已完成（Wave 5.1b） | T5.10 | 高 | 审计记录中敏感字段为掩码 | S |
| ~~T5.12~~ | 审计查询接口（分页 + 条件，`Result<PageResult>`) ✅ 已完成（Wave 5.1b） | T5.10 | 中 | 可按用户/模块/时间范围查询 | S |

### 组 D：权限服务 DDD 迁移（承接 REQ-ARCH-001）

| 任务 | 内容 | 依赖 | 优先级 | 验收口径 | 相对规模 |
|---|---|---|---|---|---|
| ~~T5.13~~ ✅ **已完成（Wave 5.2）** | 迁移第 1 步：搭建 `interfaces/application/domain/infrastructure` 目录骨架 + PO/领域对象分离约定落地示例（认证上下文试点） | 设计已定稿 | 高 | 新目录不影响现有 API；编译与既有接口回归通过 | M |
| T5.14 | 迁移第 2 步：`User` 聚合与仓储接口（domain），PO 与 Repository 实现（infrastructure） | T5.13 | 中 | 登录链路改走新仓储，REST 契约不变 | M |
| T5.15 | 迁移第 3 步：`Role`/`Menu`/`Dept` 聚合逐个迁移 + feature flag 灰度开关 | T5.14 | 中 | 开关可秒级回滚；新旧实现结果一致（对比测试） | L |

### 组 E：交付与验证（承接 REQ-CICD-001 / REQ-PROC-001）

| 任务 | 内容 | 依赖 | 优先级 | 验收口径 | 相对规模 |
|---|---|---|---|---|---|
| ~~T5.16~~ ✅ **已完成（Wave 5.6，Gitee 侧实机触发待验证）** | Gitee Go 流水线映射：按 `docs/design/cicd.md` 第 6 节把三条 Workflow 映射为 Gitee Go 任务 | 无 | 中 | 推送后在 Gitee 可见流水线执行结果 | M |
| T5.17 | 门禁升级：把命名/import/结构类规则由 `warning` 提升为 `error`，使 Checkstyle 转为真正阻断 | T5.1–T5.15 清理完毕 | 中 | 违规时 CI 失败 | S |
| ~~T5.18~~ ✅ **已完成（Wave 5.6）** | JaCoCo 覆盖率接入（阈值 40%/60%/增量 70%，见 cicd.md 第 3 节） | 无 | 中 | 构建产出覆盖率报告并执行阈值校验 | M |
| ~~T5.19~~ ✅ **已完成（Wave 5.0；Wave 5.7 延伸补测 AUTH-23）** | 单测补充：whale-auth 黑名单/登出逻辑（含 Redis 降级路径，用 mock 或嵌入式替代）；**延伸（Wave 5.7）**：4 个零覆盖 `ServiceImpl`（Role/Permission/Dept/UserPermission）补测 44 用例 | T4.1 | 高 | 新增用例全绿；覆盖正常/降级/TTL 边界 | M |
| T5.20 | 全量回归：`mvn clean install -T 1C` + `pnpm build`，按清单出报告后提交推送 | 上述任务 | 高 | 后端 exit 0 / 前端 11 包成功；报告归档 | M |

### 组 F：前端推进（承接 REQ-FUNC-008 / 009 / 010）

| 任务 | 内容 | 依赖 | 优先级 | 验收口径 | 相对规模 |
|---|---|---|---|---|---|
| T5.21 | `frontend/admin` 登录对接 `/auth/login` + 登出调 `/auth/logout`（验证黑名单在真实前端链路生效） | T4.1，中间件就绪 | 中 | 登出后原 token 请求返回 401 | M |
| T5.22 | `whale-ui`：ProTable / FormModal / SearchForm 二次封装 | 无 | 中 | 组件可独立构建，有示例页 | L |
| T5.23 | admin 用户/角色/菜单管理页 + 按钮级权限渲染 | T5.21、T5.22 | 中 | 页面可完成 CRUD 且按权限隐藏按钮 | L |

---

## 三、建议执行顺序（波次）

```
Wave 5.0（无外部依赖）— 全部完成
  ├─ ✅ ~~T5.1 分布式 ID 统一~~        ├─ ✅ ~~T5.9 审计表字段补全~~
  ├─ ✅ ~~T5.2 分布式锁工具~~          └─ ✅ ~~T5.19 auth 单测补充（13 用例）~~
  └─ ✅ ~~T5.8 生产密钥校验~~

Wave 5.1（依赖 5.0 产物）— 安全收口已闭环
  ├─ ✅ ~~T5.6 refreshToken 一并失效（REQ-SEC-004，P1）~~
  ├─ ✅ ~~T5.7 网关侧黑名单校验（REQ-SEC-003）~~
  ├─ ✅ ~~T5.10/T5.11/T5.12 审计落地~~   ├─ ✅ ~~T5.3 幂等组件（Wave 5.6）~~
  ├─ ✅ ~~T5.13 auth DDD 骨架（Wave 5.2 完成）~~  └─ ✅ ~~T5.5 缓存一致性工具（Wave 5.6）~~

Wave 5.1b（审计收口）— 已闭环：✅ ~~T5.10~~ ｜ ✅ ~~T5.11~~ ｜ ✅ ~~T5.12~~（运行时待中间件）

Wave 5.2（架构迁移 + 前端）— ✅ T5.13 已闭环
  ├─ T5.14/T5.15 auth DDD 迁移  ├─ T5.22 whale-ui
  └─ T5.21 → T5.23 admin 对接

Wave 5.6（横切基础设施）— ✅ 已闭环
  ├─ ✅ ~~T5.3 幂等组件~~  ├─ ✅ ~~T5.4 网关限流（Redis 计数器兜底）~~
  ├─ ✅ ~~T5.5 缓存一致性工具~~  ├─ ✅ ~~T5.16 Gitee Go~~  └─ ✅ ~~T5.18 JaCoCo~~
     └─ 残余：T5.4 端到端 429 冒烟（中间件未运行）｜ coverage-gate 接入 CI（待 AUTH-23）｜ Sentinel 接入

Wave 5.3（交付收口）
  ├─ ✅ ~~T5.17 门禁升级（Wave 5.4 完成）~~
  └─ T5.20 全量回归 + 提交推送
```

---

## 四、决策请求（需你确认）

| # | 事项 | 选项 | 建议 |
|---|---|---|---|
| D1 | 中间件环境 | ① 安装 Docker 起 Compose ② 用本机已装中间件（若有） ③ 继续只做静态可验证的工作 | 建议 ①，否则所有联调、审计、分布式锁、端到端登出验证都只能停留在"编译通过" |
| D2 | auth DDD 迁移规模 | ① 一次性整体重写 ② Strangler 渐进迁移（设计文档推荐） | 建议 ②，风险可控且 API 不中断 |
| D3 | 分布式事务 | ① 暂不引入（消息最终一致） ② 引入 Seata | 建议 ①，当前无跨库强一致诉求 |
| D4 | CI 主链路 | ① Gitee Go 为主 + Actions 备份 ② 迁回 GitHub | 建议 ①，当前 origin 在 Gitee |
| D5 | 门禁力度 | ① 报告模式（现状） ② 直接阻断 | 建议先 ①，清理存量违规后再切 ② |

---

## 五、风险提示

1. **服务级阻塞已解除，转为具体缺陷收口**：Wave 5.1d 将 `redisson-spring-boot-starter` 升级至 **4.8.0**（实测兼容），`whale-auth`(8081) / `whale-audit`(9101) **首次真实启动成功**，`login`/`logout` HTTP 端到端跑通 —— 原先 **P0「依赖与框架大版本不兼容」已消除**。剩余为两处可定点修复的问题（见 5/6）。
2. **黑名单降级语义**：Redis 不可用时 auth 侧 `contains` 与网关侧校验均按未命中放行 → 登出形同失效。这是可用性优先的取舍，生产需监控 Redis 可用性并告警。
3. **规范执行成本**：清单 57 项若每次全量人工核对，成本偏高；建议以自动化覆盖 C/D/E，人工聚焦 A/B 语义项。
4. ~~**jjwt 版本分裂**（parent 0.12.5 / gateway 0.12.6）尚未统一，存在行为差异隐患。~~ → **✅ 已统一（Wave 5.3，AUTH-02）**：`whale-gateway/pom.xml` 移除三处 jjwt 硬编码 `0.12.6`，由 `whale-parent` 统一管理为 **0.12.5**；`dependency:tree "-Dincludes=io.jsonwebtoken:*"` 实测 api/impl/jackson 三件均 0.12.5。
5. ~~审计切面装配缺陷（P1，新发现，既有）~~ → **✅ 已修并实机验证（Wave 5.1e）**：`AuditLogAutoConfiguration` 去掉时序不可靠的 `@ConditionalOnBean(AuditLogMapper.class)`（配置类解析期求值，早于 `@MapperScan`），改 `ObjectProvider` 在 Bean 实例化期延迟解析 → 真实服务链路 `AuditLogAspect` 已装配，`bluewhale_auth.audit_log` 由 **0 行 → 6 行**（LOGIN/LOGOUT、`biz_status=1`、`request_params` 无凭据），`/audit/log/page` 可查、`LiveAuditLogIT` 3/0/0。
6. ~~`/auth/refresh` 返回码口径（P2）~~ → **✅ 已澄清（Wave 5.1e，非缺陷）**：登出后 refresh 实测 **HTTP 200 + 业务码 401**，为 `Result` 全局统一包装的既有约定（业务码承载语义、HTTP 层恒 200）；验收口径统一改为「业务码 401」。
7. ~~网关路由仍未确认~~ → **✅ 已确认并修复（Wave 5.1f）**：定夺为「**改路由**」（登记 REQ-ARCH-003：服务自持对外一级前缀、网关**不剥离**），移除全部 8 条路由的 `StripPrefix`；经网关实测 login `code=0`、logout `code=0`、重放已登出 accessToken **HTTP 401**（REQ-SEC-003 达成）、refresh **业务码 401**（REQ-SEC-004 达成）、`audit_log` 落 LOGIN/LOGOUT（REQ-SEC-001 达成）。**新风险**：~~auth 的 `/user`、`/dept`、`/role`、`/menu`、`/permission` 前缀当前**无网关路由**（AUTH-38，P1），管理接口经网关不可达。~~ → **✅ 已修复并冒烟通过（Wave 5.3）**：网关 `whale-auth` 路由 Path 已扩至 `/auth/**,/user/**,/dept/**,/role/**,/menu/**,/permission/**`，经网关实测管理接口全部 HTTP 200 且 `code=0`。
7b. **中间件本机就绪（Wave 5.1f）**：Nacos 3.2.4（8848 服务 / 8849 控制台，MySQL 外置存储）已起，Redis 5.0.14.1（6379）与 MySQL 8.0.45（3306）可用 → **REQ-FUNC-001「无 Docker 阻塞」以本机原生进程方式解除**；RabbitMQ / Nginx 仍缺。
8. ~~数据源 URL 字符集写法缺陷（P1）~~ → **已修（Wave 5.1d）**：auth/audit yml 改为 `characterEncoding=utf8&connectionCollation=utf8mb4_general_ci`，服务真实连库成功。
9. ~~种子口令与文档不符（P1）~~ → **已修（Wave 5.1d）**：重生成 BCrypt 密文，`admin/123456` 端到端登录 `code=0`。
10. **审计跨库可见性约束（P2，新发现）**：阶段一「各服务写各自实例库」，`whale-audit` 默认读 `bluewhale_audit`，**默认查不到其他服务写入的审计记录**；实测将 `whale-audit` 数据源临时指向 `bluewhale_auth` 后 `/audit/log/page` 立即可见（`total=4`）。阶段二 MQ 汇聚为根治方案。

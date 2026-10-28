# 审计服务最终设计文档（whale-audit）

> 关联需求：REQ-SEC-001（审计日志：谁/何时/对什么/做了什么/结果）、REQ-SEC-006（多通道日志采集）、REQ-SEC-002~005（令牌黑名单/密钥校验的审计留痕）、REQ-DIST-001（分布式 ID/雪花）、REQ-QA-002（必检留痕）、REQ-DOC-002（文档先行）
> 落点模块：`backend/whale-ops/whale-audit`（服务端口 `9101`，Nacos 服务名 `whale-audit`，逻辑库 `bluewhale_audit`）。**审计核心代码落点：`backend/whale-common/whale-spring-boot-starter`（`common/audit/*` 注解/AOP/实体/脱敏 + `common/log/*` 多通道采集）；`whale-audit` 承载查询接口与上报接收**
> 生成日期：2026-10-18 ｜ 作者：BlueWhale ｜ 依据：Wave 5.16（审计服务收尾）
> 口径说明：本文为**整合最终版**，以 `docs/{design,plans,requirements,specs,reviews,devlog}` 原位最新文件与 `backend/**` 源码为准；历史副本归置于 `docs/whale-audit/originals/`（原文件保留原位，见第 7 节）。
> 端口偏差：`docs/design/distributed.md` §端口基线与 `docs/design/audit-log.md` 头部记为 `audit 8088`，实际源码 `whale-audit/application.yml` 为 `SERVER_PORT:9101`（默认 9101）；本文以源码为准，8088 系基线文档的存量偏差，待台账统一下沉。

---

## 1. 需求与范围

### 1.1 需求描述

审计日志需回答四个问题：**谁（who）、何时（when）、对什么（what）、做了什么（how）、结果如何（result）**，用于安全追溯、合规取证与问题定位。核心口径为 **append-only**（写入后不允许修改，无 `update_time` / 无 `deleted`，`create_time` 即操作发生时间），落库方式与保留策略均围绕该口径设计。

### 1.2 范围内

- 审计事件范围定义（登录/登出、权限变更、数据增删改、敏感操作、组织变更）
- `@AuditLog` 注解 + `AuditLogAspect` AOP 切面（切点、埋点字段、afterCommit 异步落库）
- 敏感字段脱敏（递归规则 + 配置化扩展）、TraceId 关联链路
- 审计查询接口（分页/详情/按 traceId）+ 读审计的元审计
- **多通道日志采集（REQ-SEC-006）**：①后端接口日志、②后端出站三方调用日志、③前端开发者日志、④前端用户行为埋点，四通道各落独立表、各自查询，**不污染 `audit_log` 语义**
- 保留与归档策略、落库方式选型（本地直写 / MQ 汇聚）

### 1.3 范围外

- 登录失败风控 / 账号锁定策略（另立需求）
- 审计日志可视化大屏（Phase 4 ops 平台）
- 全量 SQL 级 binlog 审计（不采用：字段精度与合规取证能力弱）

---

## 2. 能力矩阵

| 能力 | 状态 | 依据（类名 / 文档路径） |
|---|---|---|
| 审计核心（`@AuditLog` 注解 + `AuditLogAspect` + 异步线程池 + afterCommit） | 已闭环 | `whale-spring-boot-starter` `common/audit/{annotation,aop,config,enums,entity,mapper,support}`；`docs/design/audit-log.md` §5 |
| 敏感字段脱敏（递归规则表 + 配置化扩展） | 已闭环（内置规则 + `whale.audit.sensitive.keys` 并集；`@Sensitive` 注解兜底未实现） | `common/audit/support/SensitiveDataMasker.java` · `common/log/properties/SensitiveKeysProperties.java`（AUTH-11） |
| 返回体落库（`recordResult()`） | 已闭环 | `audit_log.result_body` 列 + `AuditLogRecord#resultBody` + `AuditLogAspect#buildRecord`（AUTH-13） |
| 审计查询接口（分页 / 详情 / 按 traceId） | 已闭环 | `whale-audit` `audit/controller/AuditLogController.java`（`/audit/log/page`、`/audit/log/{id}`、`/audit/log/trace/{traceId}`） |
| 读审计的元审计（`@AuditLog(type=QUERY)`） | 已闭环 | `AuditLogController` 3 个查询方法各标注 `@AuditLog(type=QUERY, targetType="audit_log", recordResult=false)`（AUTH-07） |
| 通道① 后端接口日志 | 已闭环 | `common/log/filter/{ApiLogFilter,LimitedCachingRequestWrapper,LimitedCachingResponseWrapper}.java` · 表 `api_log` · 查询 `GET /audit/log/api/page` |
| 通道② 后端出站三方调用日志 | 已闭环 | `common/log/interceptor/OutboundLogInterceptor.java` · 表 `outbound_log` · 查询 `GET /audit/log/outbound/page` |
| 通道③ 前端开发者日志 | 已闭环（后端接收 + 前端 SDK） | `POST /audit/log/frontend/batch`（permitAll）· 表 `frontend_log` · 查询 `GET /audit/log/frontend/page` · `frontend/shared-packages/whale-log-sdk`（Wave 5.14） |
| 通道④ 前端用户行为埋点 | 已闭环（后端接收 + 前端 SDK） | `POST /audit/log/behavior/batch`（permitAll）· 表 `behavior_log` · 查询 `GET /audit/log/behavior/page` · `whale-log-sdk`（Wave 5.14） |
| 多通道 sink 切换（`whale.audit.sink=local\|mq`） | 部分（`local` 已落地；`mq` 已废弃并回落 local，MQ 汇聚改用 `whale.audit.mq.publish-enabled`，HTTP 转发用 `whale.audit.forward.enabled`） | `common/log/properties/LogSinkProperties.java` · `common/log/sink/LogSink.java` |
| 保留期清理（4 张日志表，默认关闭） | 部分（调度器与清理器已落地，默认 `enabled=false` 不启用；**不清理 `audit_log`**） | `whale-audit` `retention/{LogRetentionScheduler,LogRetentionCleaner}.java` · `config/RetentionProperties.java` |
| 审计导出（`POST /audit/log/export` + EXPORT 元审计） | 已闭环（Wave 5.21 / AUTH-08） | `whale-audit` `AuditLogController#export` + `support/AuditCsvWriter` + `support/AuditArchiveClient` · `config/AuditExportProperties.java` · `@AuditLog(type=EXPORT, targetType="audit_log", recordResult=false)` |
| 审计保留与归档（热/温/冷，`audit_log`） | 已闭环（Wave 5.21 / AUTH-09；归档不删数据） | `whale-audit` `retention/AuditLogArchiver.java` · `config/RetentionProperties#archiveEnabled`（默认 `false`）· 归档导出至 whale-file；**`audit_log` append-only，绝不删除/更新** |
| 阶段二 MQ 汇聚 | 已闭环（AUTH-10，2026-10-23；`whale.audit.forward` HTTP 转发为第二级） | `docs/design/audit-log.md` §6.2 |

---

## 3. 接口与权限点清单

**统计口径**：2 控制器 / **9 个 REST 接口**（`AuditLogController` 3 + `LogIngestController` 6）；鉴权统一走网关权限码头 `X-Permission-Codes` + starter `RequirePermissionInterceptor` 判定 `sys:audit:log:list`（AUTH-06）；上报 2 个接口 `permitAll`（网关白名单，未登录期也需上报）。

### 3.1 接口清单

| 控制器 | 方法与路径 | 鉴权 | 元审计 / 说明 |
|---|---|---|---|
| `AuditLogController`（`/audit/log`） | GET `/audit/log/page` | `@RequirePermission("sys:audit:log:list")` | `@AuditLog(operation="分页查询审计日志", type=QUERY, targetType="audit_log", recordResult=false)` |
| | GET `/audit/log/{id}` | 同上 | `@AuditLog(operation="查看审计日志详情", type=QUERY, …)` |
| | GET `/audit/log/trace/{traceId}` | 同上 | `@AuditLog(operation="按链路查询审计日志", type=QUERY, …)` |
| `LogIngestController`（`/audit/log`） | POST `/audit/log/frontend/batch` | 无（`permitAll`，网关白名单 `/audit/log/frontend/**`） | 前端 console 日志批量上报；`items` 上限 50；投递落库后立即返回 |
| | POST `/audit/log/behavior/batch` | 无（`permitAll`，网关白名单 `/audit/log/behavior/**`） | 前端行为埋点批量上报；`items` 上限 50 |
| | GET `/audit/log/frontend/page` | `@RequirePermission("sys:audit:log:list")` | 通道③ 查询 |
| | GET `/audit/log/behavior/page` | 同上 | 通道④ 查询 |
| | GET `/audit/log/api/page` | 同上 | 通道① 查询（Wave 5.15 补齐） |
| | GET `/audit/log/outbound/page` | 同上 | 通道② 查询（Wave 5.15 补齐） |

> 服务内前缀均为 `/audit/log`；经网关按 `Path=/audit/**` 路由转发（网关**不 `StripPrefix`**，外部路径与服务内一致）。
> 未落地：`POST /audit/log/export`（AUTH-08 阻塞，含 EXPORT 元审计）。
> 上报接口归属：`LogIngestController` 由 `@RequiredArgsConstructor` 注入 `FrontendLogService` / `BehaviorLogService` / `ApiLogService` / `OutboundLogService`；提交用户 ID 以网关 `X-User-Id` 为准，缺失才回落请求体 `userId`（`resolveUserId`）。

### 3.2 权限点种子

| perm_code | perm_name | 类型 | 来源 |
|---|---|---|---|
| `sys:audit:log:list` | 审计日志查询 | API | `whale-auth` `db/init.sql`（种子 id 25，AUTH-06）|

> `whale-audit` 逻辑库无 RBAC 表，故权限判定改由**网关注入权限码头**（`PermissionHeaderGlobalFilter` 读 Redis 权限码缓存）+ starter `RequirePermissionInterceptor` 完成；权限点种子登记在 `whale-auth` 侧。

---

## 4. 关键配置项

| 配置键 | 含义 / 默认值 | 来源 |
|---|---|---|
| `whale.audit.sink` | 落库 sink：`local`（本地异步直写，默认）/ `mq`（已废弃，自动回落 local 并告警；MQ 汇聚用 `whale.audit.mq.publish-enabled`，HTTP 转发用 `whale.audit.forward.enabled`） | `common/log/properties/LogSinkProperties.java` · `docs/design/audit-log.md` §6 |
| `whale.audit.retention.enabled` | 日志保留期清理开关，默认 `false`（不装配调度器、不清理；**仅清 4 张日志表，不动 `audit_log`**） | `whale-audit/application.yml`（`${WHALE_AUDIT_RETENTION_ENABLED:false}`）· `config/RetentionProperties.java` |
| `whale.audit.retention.days` | 保留期天数，默认 `30` | `whale-audit/application.yml`（`${WHALE_AUDIT_RETENTION_DAYS:30}`） |
| `whale.audit.retention.cron` | 清理任务 cron，默认 `0 0 3 * * ?` | `whale-audit/application.yml`（`${WHALE_AUDIT_RETENTION_CRON:0 0 3 * * ?}`） |
| `whale.audit.sensitive.keys` | 脱敏附加敏感键名（与内置规则**并集**，忽略大小写、去 `_`/`-` 后匹配，命中即整体掩码） | `common/log/properties/SensitiveKeysProperties.java`（AUTH-11） |
| `whale.permission.header.enabled` | whale-audit 侧启用 starter `RequirePermissionInterceptor`（默认 `false`，须显式开启；本服务置 `true`） | `whale-audit/application.yml` |
| `SERVER_PORT` | whale-audit 服务端口，默认 `9101` | `whale-audit/application.yml` |
| `MYSQL_USER` / `MYSQL_PASSWORD` / `NACOS_SERVER` / `NACOS_NAMESPACE` / `SPRING_PROFILES_ACTIVE` | 基础设施通用环境变量（MySQL `root/root`；Nacos `127.0.0.1:8848`/`public`；profile `dev`） | `whale-audit/application.yml` |
| `WHALE_GATEWAY_PERMISSION_HEADER_ENABLED` | 网关权限码头注入开关，默认 `true`（`PermissionHeaderGlobalFilter` order=-50） | `whale-gateway/application.yml` |
| `WHALE_GATEWAY_RATE_LIMIT_REPORT` / `…_WINDOW_SECONDS` | 上报路径独立限流阈值，默认 `30` / `1` 秒（防刷量） | `whale-gateway/application.yml` · `RateLimitProperties#pathRules` |

**网关侧约定**：

| 项 | 值 | 说明 |
|---|---|---|
| 路由 | `Path=/audit/**` → `lb://whale-audit`（无 `StripPrefix`） | 外部路径与服务内 `/audit/log` 一致 |
| JWT 白名单 | `/audit/log/frontend/**`、`/audit/log/behavior/**` | 上报接口免 JWT（未登录期也需上报），路径与服务内逐字一致 |
| 上报限流 | `path-rules`：上两路径 `limit=30` / `window=1s`；未命中沿用全局 `200 / 1s` | 公开写库单独收紧阈值 |

**数据源 URL 约定**：`jdbc:mysql://127.0.0.1:3306/bluewhale_audit?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`（`characterEncoding=utf8`）。

---

## 5. 表结构

`whale-audit` `db/init.sql` 共 **5 张表**：核心审计表 `audit_log` + 多通道 4 张独立表（`api_log` / `outbound_log` / `frontend_log` / `behavior_log`）。公共口径：`id BIGINT`（雪花，无自增）、`create_time DATETIME`（默认 `CURRENT_TIMESTAMP`，即记录产生时间）；**均为 append-only**（无 `update_time` / 无 `deleted`），逻辑外键不建物理 FK。

| 表 | 字段要点 | 关键约束 / 索引 |
|---|---|---|
| `audit_log`（操作审计） | trace_id/user_id/tenant_id/username/module/operation/**operate_type**(LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER)/method(全限定类名#方法)/request_uri/http_method/target_type/target_id/request_params/**result_body**/response_code/**biz_status**(1 成功/0 失败)/error_msg/ip/user_agent/cost_ms/create_time | PK(id)；`idx_trace_id`/`idx_user_id`/`idx_module`/`idx_operate_type`/`idx_biz_status`/`idx_tenant_time(tenant_id, create_time)`/`idx_create_time`；append-only |
| `api_log`（通道①，`ApiLogFilter` 采集） | trace_id/user_id/username/module/request_uri/http_method/query_string/request_params/response_body/response_code/biz_status/error_msg/ip/user_agent/cost_ms/create_time | PK(id)；`idx_trace_id`/`idx_create_time`/`idx_user_id`/`idx_biz_status` |
| `outbound_log`（通道②，`OutboundLogInterceptor` 采集） | trace_id/user_id/username/module/target_host/target_uri/http_method/request_params/response_body/response_code/biz_status(超时/连接异常为 0)/error_msg/cost_ms/create_time | PK(id)；`idx_trace_id`/`idx_create_time`/`idx_target_host`/`idx_biz_status` |
| `frontend_log`（通道③） | trace_id/user_id/client_id/page_url/log_level(DEBUG/INFO/WARN/ERROR)/message/stack/browser/create_time | PK(id)；`idx_trace_id`/`idx_create_time`/`idx_client_id`/`idx_log_level` |
| `behavior_log`（通道④） | trace_id/user_id/client_id/event_type(PAGE_VIEW/CLICK/SUBMIT/CUSTOM)/event_name/page_url/target/payload/create_time | PK(id)；`idx_trace_id`/`idx_create_time`/`idx_client_id`/`idx_event_type` |

> **写入归属（阶段一）**：各服务写各自实例下的审计/日志表（微服务 DB 自治）；`whale-audit` 提供统一查询接口。**跨库可见性约束**：查询服务须与写入服务同库/同实例，否则查不到；阶段二 MQ 汇聚（AUTH-10）后消除。
> **`result_body` 语义（AUTH-13）**：仅 `@AuditLog(recordResult=true)` 时经 `SensitiveDataMasker#maskJson` 脱敏并按切面常量上限（`AuditLogAspect#MAX_RESULT_LENGTH`，当前 2000 字符）截断后写入；序列化失败仅告警、该字段留空。
> `whale-auth` 侧 `db/init.sql` 亦含 `audit_log`（第 9 节）与 4 张多通道日志表，DDL 与本文档、`whale-audit` `db/init.sql` 一致（append-only，不设 `update_time`/`deleted`）。

---

## 6. 已闭环项与残余/阻塞项

### 6.1 已闭环项

| 项 | 结论 |
|---|---|
| AUTH-06 | 审计查询鉴权：网关注入权限码头 `X-Permission-Codes` + starter `RequirePermissionInterceptor`，权限点 `sys:audit:log:list` 种入库，无权限 403 |
| AUTH-07 | 读审计元审计：`AuditLogController` 3 个查询方法 `@AuditLog(type=QUERY, targetType="audit_log")`，元审计落同库可反查（`operateType=QUERY`） |
| AUTH-11 | 脱敏规则配置化：`whale.audit.sensitive.keys` + `SensitiveDataMasker#maskJson` |
| AUTH-12 | 写操作审计埋点 5 → 全量敏感写操作（改密 `recordParams=false`） |
| AUTH-13 | `recordResult()` 返回体落库：补 `result_body` 列 + `AuditLogRecord#resultBody` + 切面脱敏截断落库 |
| REQ-SEC-006 | 多通道日志四通道「写—查」对称：Wave 5.10（后端 Wave A：接口/出站日志 + 上报/查询接口 + 权限码头）、Wave 5.12（`api_log.module` 回填 + 网关按路径限流 + 保留期扩 4 表）、Wave 5.14（前端 SDK 交付通道③④）、Wave 5.15（补齐通道①②查询 `GET /audit/log/api/page`、`/outbound/page`） |

### 6.2 残余 / 阻塞项

| 项 | 状态 / 处置 |
|---|---|
| AUTH-08 审计导出 | **已闭环（Wave 5.21）**：`POST /audit/log/export` 落地（`AuditLogController#export` + `AuditLogService#exportLogs`），CSV 上传 whale-file 返回 `objectKey`；带 `@RequirePermission("sys:audit:log:list")` + `@AuditLog(type=EXPORT, targetType="audit_log", recordResult=false)` 元审计 |
| AUTH-09 审计保留与归档 | **已闭环（Wave 5.21，归档不删数据）**：`AuditLogArchiver` 按保留期导出 `audit_log` 至 whale-file，由 `LogRetentionScheduler` 调度（`whale.audit.retention.archive-enabled` 默认 `false`）。**边界：`audit_log` append-only，归档只读导出、绝不删除/更新** |
| AUTH-10 阶段二 MQ 汇聚 | **已闭环（2026-10-23）**：starter `AuditLogMqPublisher`（MQ→HTTP→本地三级降级）+ whale-audit `@RabbitListener` 消费者、`audit_mq_message` 幂等表、DLX/DLQ 死信拓扑；whale-auth 试点实机登录验证通过（`audit_log` +1 / `audit_mq_message` +1 / 原库 0 行），开关 `whale.audit.mq.publish-enabled` / `consume-enabled` 默认 `false` |
| 四通道查询运行时端到端验证 | **已闭环（Wave 5.21）**：经网关实测导出 `code=0` 且返回 `objectKey`；四通道查询端点静态门禁 + 运行时冒烟通过 |
| 多通道查询端点自身元审计 | **残余**：四通道查询端点未加 `@AuditLog(type=QUERY)`（仅 `audit_log` 三查询 + 导出已加） |
| 异步派发丢采集 | **已闭环（2026-10-23 P3）**：`ApiLogFilter` 放行 ASYNC 派发，首次派发暂存采样决策/开始时间/请求体，ASYNC 完成后按最终响应落库，幂等标记防重复；同步/异步均只落库一次 |
| `@Sensitive` 注解兜底 / 脱敏规则表完全配置化 | **残余**：内置规则 + 键名配置化已落地，注解兜底未实现 |

---

## 7. 原文索引

审计服务全部相关历史文档已以**副本**形式归置于 `docs/whale-audit/originals/`（共 **26 份**），**原位置文件（`docs/design`、`docs/specs`、`docs/reviews`、`docs/devlog`、`.trae/documents`）内容与路径均不变**，本文以原位最新文件为准。

| 分类 | 副本位置 | 文件（清单） |
|---|---|---|
| design | `originals/design/` | `audit-log.md` |
| 方案原文 | `originals/scheme/` | `审计日志服务多通道完善方案.md`（源 `.trae/documents/`） |
| specs | `originals/specs/` | `2026-10-17-wave5.1b-audit-log/`、`2026-10-18-wave5.10-multichannel-log/`、`2026-10-18-wave5.12-audit-backend-polish/`、`2026-10-18-wave5.14-whale-log-sdk/`、`2026-10-18-wave5.15-audit-channel-query/`（各含 spec/tasks/checklist） |
| reviews | `originals/reviews/` | `2026-10-17-wave5.1b-audit-log.md`、`2026-10-17-wave5.1e-audit-aspect-wiring-fix.md`、`2026-10-18-wave5.10-multichannel-log.md`、`2026-10-18-wave5.12-audit-backend-polish.md`、`2026-10-18-wave5.14-whale-log-sdk.md`、`2026-10-18-wave5.15-audit-channel-query.md` |
| devlog | `originals/devlog/` | `2026-10-16.md`、`2026-10-17.md`、`2026-10-18.md` |

> 关联落点（原位，未归档副本）：平台级 `docs/requirements/register.md`、`docs/plans/next-steps.md`、`docs/plans/task-breakdown.md` 与源码 `backend/whale-common/whale-spring-boot-starter`（`common/audit/*`、`common/log/*`）、`backend/whale-ops/whale-audit`（`controller`/`service`/`dto`/`vo`/`retention`/`db/init.sql`）、`backend/whale-services/whale-gateway`（`PermissionHeaderGlobalFilter`/`RateLimitGlobalFilter`/`application.yml`）、`frontend/shared-packages/whale-log-sdk`。

---

## 8. 版本与变更记录

| 版本 | 日期 | 作者 | 变更说明 |
|---|---|---|---|
| v1.0 | 2026-10-18 | BlueWhale | 初始整合最终版。依据 **Wave 5.16**（审计服务收尾：originals 副本归档 + 最终版整合）。整合来源：`docs/design/audit-log.md`、`.trae/documents/审计日志服务多通道完善方案.md`、`docs/specs/2026-10-1{7,8}-wave5.{1b,10,12,14,15}-*/spec.md`、`docs/reviews/2026-10-1{7,8}-wave5.*.md`、`docs/requirements/register.md`、`docs/plans/next-steps.md`，及源码 `whale-spring-boot-starter`（`common/audit/*`、`common/log/*`）、`whale-audit`（`AuditLogController`/`LogIngestController`/`retention/*`/`db/init.sql`）、`whale-gateway/application.yml`、`frontend/shared-packages/whale-log-sdk`。 |

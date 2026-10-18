# Wave 5.10 · 审计日志多通道完善（后端 Wave A，REQ-SEC-006）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-SEC-006（多通道日志采集，**本波新增**）、REQ-SEC-001（审计日志）、REQ-SEC-002/003（链路与网关）、REQ-FUNC-002（权限中心 AUTH-06 权限点）、REQ-QA-002（检查留痕） |
| 任务映射 | Wave A ↔ 方案 A1–A7；AUTH-06（审计查询加权限注解 + 权限点种子）、AUTH-11（脱敏规则配置化）在本波一并闭环 |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [审计日志服务多通道完善方案](../documents/审计日志服务多通道完善方案.md)（**执行依据**）；[audit-log.md](../../design/audit-log.md) §4/§7/§8/§9；[distributed.md](../../design/distributed.md)；[naming-conventions.md](../../naming-conventions.md) §二/§八 |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 按 [code-review-checklist.md](../../checklists/code-review-checklist.md) 自检留报告（REQ-QA-002）→ 构建/实时验证 → 提交推送 |

> 本波次**只做后端 Wave A**；前端 `whale-log-sdk`（Wave B）待 A 批接口冻结后另立 `specs/2026-10-18-wave5.11-whale-log-sdk/`。

---

## 一、需求项（本波次范围）

现有审计能力（REQ-SEC-001，`@AuditLog` + AOP）只覆盖**业务事件审计**，回答不了四类观测需求。本波次补齐后端侧两类通道（后端接口日志、出站三方调用日志），并冻结前端上报接口与查询接口契约（Wave B 使用）。

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-SEC-006 | ① 后端接口日志（`ApiLogFilter`）；② 出站三方调用日志（`OutboundLogInterceptor`）；③ 前端上报接收接口 + 查询接口契约冻结 | 4 张独立表（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`）落库；查询接口可查；**不污染 `audit_log` 语义** |
| REQ-SEC-001 | 复用 `AuditLogAspect` 的 `afterCommit` + 独立线程池 + 失败仅 warn 机制与 `SensitiveDataMasker` | 审计既有链路不回归；新通道与审计共用脱敏与隔离策略 |
| REQ-SEC-002/003 | traceId / 用户链路复用网关 `X-Trace-Id` / `X-User-Id` / `X-Username`，不新增 MDC | 落库记录可凭 `trace_id` 串联网关与下游 |
| REQ-FUNC-002 | AUTH-06：审计查询接口加 `@RequirePermission("sys:audit:log:list")` + 权限点种子；AUTH-11：脱敏规则配置化 | 查询接口拒绝无权限；权限点入库；`@Sensitive` 兜底生效 |
| REQ-QA-002 | 单测 + 实时 IT + 检查报告 | 报告逐项对应清单编号 |

---

## 二、总体设计

```
[后端接口]  ApiLogFilter ───────────┐
[出站调用]  OutboundLogInterceptor ─┤
                                    ├─→ LogPersistSupport(脱敏+异步) → api_log / outbound_log
[前端 console] ┐                    │
[前端行为埋点] ┴→ POST /audit/log/{frontend|behavior}/batch → whale-audit ─→ frontend_log / behavior_log
```

- **复用而非新造**：抽用既有 `SensitiveDataMasker`（递归脱敏/截断）与 `AuditLogAspect` 的 `afterCommit + 线程池 + 失败仅 warn` 三段，提为共享 `LogPersistSupport`；`@AuditLog` 切面**继续只写 `audit_log`**。
- **独立线程池**：新建 `whaleLogExecutor`（core=2/max=4/queue=2048/`CallerRunsPolicy`/前缀 `whale-log-`），**不复用** `auditLogExecutor`（core=1，会被高频接口日志打满并阻塞审计）。
- **网关不落日志**：`whale-gateway` 无数据源，落库需新增依赖、收益低；网关仅做**上报接口白名单 + 限流**（复用 `RateLimitGlobalFilter`）。
- **TraceId/用户链路复用现有**：网关 `TraceIdGlobalFilter`(order=-200) 注入 `X-Trace-Id`、`JwtAuthGlobalFilter`(order=-100) 注入 `X-User-Id`/`X-Username`，全部通道从请求头取。

---

## 三、接口与数据契约（冻结）

### 3.1 四张表 DDL（append-only，对齐 `audit_log` 风格）

公共：`id BIGINT`（雪花 `IdType.ASSIGN_ID`）、`create_time DATETIME DEFAULT CURRENT_TIMESTAMP`；**无** `update_time`/`deleted`（追加型数据，见 §3.3 例外登记）。

| 表 | 列（除 id/create_time） | 索引 |
|---|---|---|
| `api_log` | `trace_id`/`user_id`/`username`/`module`/`request_uri`/`http_method`/`query_string`/`request_params`/`response_body`/`response_code`/`biz_status`/`error_msg`/`ip`/`user_agent`/`cost_ms` | `idx_trace_id`、`idx_create_time`、`idx_user_id`、`idx_biz_status` |
| `outbound_log` | 同 `api_log`（去掉 `ip`/`user_agent`/`query_string`）+ `target_host`/`target_uri`/`response_body` | `idx_trace_id`、`idx_create_time`、`idx_target_host`、`idx_biz_status` |
| `frontend_log` | `trace_id`/`user_id`/`client_id`/`page_url`/`log_level`/`message`(TEXT)/`stack`(TEXT)/`browser` | `idx_trace_id`、`idx_create_time`、`idx_client_id`、`idx_log_level` |
| `behavior_log` | `trace_id`/`user_id`/`client_id`/`event_type`/`event_name`/`page_url`/`target`/`payload`(TEXT) | `idx_trace_id`、`idx_create_time`、`idx_client_id`、`idx_event_type` |

DDL 追加到 `whale-audit/db/init.sql`（4 张表）与 `whale-auth/db/init.sql`（4 张表，append-only 风格，同 `audit_log` 随服务库部署）。

### 3.2 HTTP 接口（whale-audit，服务自持前缀 `/audit/log`，网关不剥离）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/audit/log/frontend/batch` | `permitAll`（未登录期报错也需上报） | 体 `LogBatchRequest{ traceId, userId, clientId, pageUrl, List<LogItem> items }`；`items` 上限 50，超限抛 `BizException(PARAM_ERROR)`；`sink.submit` 后立即 `Result.ok()` |
| POST | `/audit/log/behavior/batch` | `permitAll` | 同上结构（行为埋点） |
| GET | `/audit/log/frontend/page` | `@RequirePermission("sys:audit:log:list")` | `FrontendLogQueryRequest extends PageQuery`，`size ∈ [1,200]`，`orderByDesc(create_time)` |
| GET | `/audit/log/behavior/page` | `@RequirePermission("sys:audit:log:list")` | `BehaviorLogQueryRequest extends PageQuery` |

- **token 存在时**以网关 `X-User-Id` 回填 `user_id`（上报体 `userId` 不可信，仅作兜底）；防刷 = 网关限流规则 + 批量上限。
- 路由：`/audit/log` 前缀服务自持，与既有 `audit_log` 路由一致（[REQ-ARCH-003](../../requirements/register.md)）。

### 3.3 配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `whale.audit.sink` | `local` | `local`（本地异步直写）/ `mq`（阶段二预留；未实现时 **fail-fast 提示 + 回落 local**） |
| `whale.log.api.enabled` | `true` | 后端接口日志开关 |
| `whale.log.api.sample-rate` | `1.0` | 采样率 `[0,1]`，`0` 表示全不采集 |
| `whale.log.api.max-body-length` | `8192` | 单请求/响应体缓存上限（字符），超限停缓存 |
| `whale.log.api.exclude-paths` | `/actuator/**,/health,/favicon.ico,/error,/audit/log/frontend/**,/audit/log/behavior/**` | 排除路径（末两项**防上报回环**） |
| `whale.audit.sensitive.keys` | 复用内置规则 | AUTH-11：脱敏规则配置化（附加键名，与内置并集） |

---

## 四、组件与包布局（精确落点）

### 4.1 starter（`whale-spring-boot-starter`）

新增根包 `com.bluewhale.common.log`，子包与类：

| 包 | 类 | 职责 |
|---|---|---|
| `entity` | `ApiLogRecord` / `OutboundLogRecord` / `FrontendLogRecord` / `BehaviorLogRecord` | 4 表实体（`@TableName` + `@TableId(IdType.ASSIGN_ID)`，**不继承 `BaseEntity`**） |
| `mapper` | `ApiLogMapper` / `OutboundLogMapper` / `FrontendLogMapper` / `BehaviorLogMapper` | `extends BaseMapper<...>`（既有 `@MapperScan("com.bluewhale.**.mapper")` 扫描） |
| `sink` | `LogSink`（接口）、`LocalLogSink`、`LogPersistSupport` | `submit(Runnable, channel)`：事务活跃则 `afterCommit` 注册，否则投递 `whaleLogExecutor`；组装/落库异常仅 `log.warn` |
| `properties` | `LogSinkProperties`(`whale.audit`)、`ApiLogProperties`(`whale.log.api`) | 配置绑定 |
| `filter` | `ApiLogFilter`、`LimitedCachingRequestWrapper`、`LimitedCachingResponseWrapper` | 后端接口日志（**自研包装器**，非 Spring `ContentCaching*`） |
| `interceptor` | `OutboundLogInterceptor`、`OutboundLogRestTemplateCustomizer` | 出站三方调用日志（经 `RestTemplateBuilder` 自动挂载） |
| `support` | `LogHeaders`（`X-Trace-Id`/`X-User-Id`/`X-Username` 常量）、`SensitiveDataMasker#maskJson` 扩展 | 头常量复用 + 脱敏复用 |
| `config` | `WhaleLogAutoConfiguration` | `@AutoConfiguration(after={MybatisPlusConfig, AuditLogAutoConfiguration})` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass`；Bean 内 `ObjectProvider<XxxMapper>` 延迟解析 |

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 追加 `com.bluewhale.common.log.config.WhaleLogAutoConfiguration`（现 9 行 → 10 行）。

### 4.2 `SensitiveDataMasker` 扩展（AUTH-11）

新增 `public String maskJson(String json)`：解析为 Map/List 后递归 `mask`，解析失败仅按 `MAX_PARAM_LENGTH` 截断。出站请求/响应与前端接收端共用。`whale.audit.sensitive.keys` 附加规则并入内置键名集合。

### 4.3 whale-audit

| 包 | 类 | 职责 |
|---|---|---|
| `controller` | `LogIngestController`（`/audit/log`） | 上报 2 接口 + 查询 2 接口（见 §3.2） |
| `dto` | `LogBatchRequest`、`LogItem`、`FrontendLogQueryRequest`、`BehaviorLogQueryRequest` | 入参 |
| `vo` | `FrontendLogVO`、`BehaviorLogVO` | 出参 |
| `service`/`service.impl` | `FrontendLogService`/`Impl`、`BehaviorLogService`/`Impl` | 接收落库 + 分页查询（风格同 `AuditLogServiceImpl`） |
| 资源 | `db/init.sql` | 追加 4 张表 DDL |

### 4.4 网关（`whale-gateway`，仅配置）

- `application.yml` 白名单追加上报路径 `/audit/log/frontend/**`、`/audit/log/behavior/**`（`JwtAuthGlobalFilter` 放行）；
- 限流规则追加：上报接口按 `client_id`/IP 维度限速（复用 `RateLimitGlobalFilter`）。

---

## 五、验收

| 项 | 验证方式 | 通过标准 |
|---|---|---|
| 4 表 DDL | 对照 `audit_log` 风格 | 字段/索引与 §3.1 一致；append-only 无 `update_time`/`deleted` |
| 接口日志 | `ApiLogFilterTest` | 放行 / 排除路径 / `sample-rate=0` / 超大 body 截断 / **响应体回写不丢** / 异步派发跳过 |
| 包装器 | `LimitedCachingWrapperTest` | 超 `max-body-length` 停缓存；Content-Type 白名单外不缓存 |
| 出站日志 | `OutboundLogInterceptorTest`（`MockRestServiceServer`） | 状态/耗时/traceId 透传/超时报错/敏感字段掩码 |
| 脱敏 | `SensitiveDataMaskerTest` | 新增 `maskJson` 用例（合法 JSON 脱敏、非法 JSON 仅截断） |
| 上报接口 | `LogIngestControllerTest` | `items` 超 50 抛 `PARAM_ERROR`；`sink` 被调用；`permitAll` 生效 |
| 查询接口 | whale-audit 两个新 `*ServiceImplTest` | 分页 / `size` 收敛 `[1,200]` / `create_time` 倒序 |
| 实时 IT | `LiveApiLogIT` / `LiveFrontendLogIT`（`-Dwhale.live.it=true`，MySQL 3306） | `api_log` ≥1 行且**无明文凭据**；灌 `frontend_log`/`behavior_log` 后查询接口可见 |
| 门禁 | `mvn -f backend/pom.xml clean verify -T 1C`、`mvn -f backend/pom.xml checkstyle:check` | BUILD SUCCESS；全 reactor 0 违规（E16 中文 Javadoc / 行宽 ≤150） |

---

## 六、风险与残余

| 风险 / 残余 | 影响 | 处置 |
|---|---|---|
| 写放大 | 接口日志行数远大于审计 | 采样 + 排除路径 + body 截断；`api_log` 建议保留期 30 天（`whale.audit.retention.days` 待扩多表） |
| 响应包装 | 漏 `copyBodyToResponse()` 致客户端空响应 | 自研 wrapper `finally` **必须**回写；Content-Type 白名单跳过文件/大 body |
| 线程池积压 | `CallerRunsPolicy` 高负载下由业务线程兜底写库 | 队列 2048 + 采样 + 通道可关闭；不静默丢数据 |
| 前端上报被刷量 | `permitAll` 公开写库 | 网关限流 + 批量上限 + `client_id` 维度限速 |
| 跨库可见性 | 阶段一查询服务须与写入服务同库/同实例 | 同 `audit_log` 既有约束；阶段二 MQ 汇聚消除 |
| 无 MQ 可靠性边界 | 进程崩溃丢内存队列记录 | `sink=mq` 仅预留，未就绪回落 local |
| 异步派发丢采集 | `shouldNotFilterAsyncDispatch()=true` | 登记为已知丢采集（异步派发请求不入 `api_log`） |

---

## 七、执行顺序

```
1) 文档先行：本 spec + tasks + checklist + register REQ-SEC-006 + 命名登记（A1）
2) 4 表 DDL + starter 实体/Mapper（A2）
3) LogPersistSupport + whaleLogExecutor + LogSink/LocalLogSink + LogSinkProperties（A3）
4) ApiLogFilter + LimitedCaching{Request,Response}Wrapper（A4）
5) OutboundLogInterceptor + OutboundLogRestTemplateCustomizer + maskJson（A5）
6) whale-audit 上报/查询接口 + 自动配置 + 网关白名单/限流（A6/A7）
7) 单测 + 实时 IT（-Dwhale.live.it=true）→ mvn clean verify + checkstyle:check
8) 检查报告 + devlog + 台账销账
9) commit + push（Gitee origin/main）
```

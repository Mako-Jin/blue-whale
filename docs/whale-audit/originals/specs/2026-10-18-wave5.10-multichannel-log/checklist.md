# Checklist：Wave 5.10 审计日志多通道完善（后端 Wave A，REQ-SEC-006）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含 4 表契约、接口契约、配置项、组件落点、风险
- [x] 1.2 `register.md` 新增 REQ-SEC-006 并关联 REQ-SEC-001/002/003
- [x] 1.3 `naming-conventions.md` 已登记 4 张表名（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`）与 `@bluewhale/whale-log-sdk` 包名

## 二、表结构

- [x] 2.1 4 表 DDL 追加到 `whale-audit/db/init.sql` 与 `whale-auth/db/init.sql`，字段/索引与 spec §3.1 一致
- [x] 2.2 4 表为 append-only（无 `update_time`/`deleted`，`id` 雪花、`create_time DEFAULT CURRENT_TIMESTAMP`），对齐 `audit_log`
- [x] 2.3 starter 4 实体（`@TableName` + `@TableId(ASSIGN_ID)`，不继承 `BaseEntity`）与 4 `Mapper extends BaseMapper` 就位

## 三、落库支撑与 sink

- [x] 3.1 `LogPersistSupport`：事务活跃注册 `afterCommit`，否则投递线程池；组装/落库异常仅 `log.warn`，不冒泡
- [x] 3.2 `whaleLogExecutor` 为**独立**线程池（core=2/max=4/queue=2048/`CallerRunsPolicy`/前缀 `whale-log-`），未复用 `auditLogExecutor`
- [x] 3.3 `LogSink` 抽象 + `LocalLogSink`；`LogSinkProperties`(`whale.audit.sink`) 预留，`mq` 未实现时 fail-fast 提示 + 回落 local

## 四、后端接口日志（Filter）

- [x] 4.1 `ApiLogFilter extends OncePerRequestFilter`；`shouldNotFilterAsyncDispatch()=true`
- [x] 4.2 自研 `LimitedCaching{Request,Response}Wrapper`（**tee 透传方案**）：请求/响应字节**全量透传**给下游/客户端，同时镜像入上限缓存；超 `max-body-length` 停止镜像（**不截断透传**）；Content-Type 白名单只缓存 JSON/表单
- [x] 4.3 `getCapturedBody()` 供 `ApiLogFilter` 采集；因字节已 tee 直达客户端，`finally` 无需 `copyBodyToResponse()`（**对方案参考实现的有意偏离**，避免缓冲式回写截断响应体）
- [x] 4.4 配置 `whale.log.api.*`（enabled/sample-rate/max-body-length/exclude-paths），`exclude-paths` 含上报接口防回环

## 五、出站三方调用日志（Interceptor）

- [x] 5.1 `OutboundLogInterceptor` + `OutboundLogRestTemplateCustomizer`：经 `RestTemplateBuilder` 自动挂载，业务零改动
- [x] 5.2 采集 method/uri/host/cost_ms/status/biz_status/error_msg/request+response 摘要；traceId 透传（缺失生成 32 位 UUID）
- [x] 5.3 超时/连接异常记 `biz_status=0` 并原样抛出
- [x] 5.4 `SensitiveDataMasker#maskJson` 新增（解析失败仅截断）；既有 `maskArguments`/`mask` 语义未变

## 六、接收与查询接口（whale-audit）

- [x] 6.1 `LogIngestController` 上报 2 接口（`POST /whale-frontend/batch`、`POST /behavior/batch`）`permitAll`，`items` 上限 50
- [x] 6.2 上报以网关 `X-User-Id` 回填 `user_id`；`sink.submit` 后立即 `Result.ok()`
- [x] 6.3 查询 2 接口（`GET /whale-frontend/page`、`GET /behavior/page`）带 `@RequirePermission("sys:audit:log:list")`；`size ∈ [1,200]`、`create_time` 倒序
- [x] 6.4 AUTH-06 权限点种子（`sys:audit:log:list`）入库/登记（见 §十一）

## 六·补、AUTH-06 权限码头（§八）

- [x] 6.5 starter `com.bluewhale.common.web.WebHeaders`：单一来源头常量（含 `X-Permission-Codes`），Filter/Interceptor/权限拦截器统一引用
- [x] 6.6 starter `RequirePermissionInterceptor` + `WhalePermissionAutoConfiguration`：按 `whale.permission.header.enabled` 按需装配（默认 `false`，不在 whale-auth 内重复判定）；无注解直放行、`super_admin` 放行、命中权限码放行、否则 403
- [x] 6.7 gateway `PermissionHeaderGlobalFilter`（order=-50）：先移除伪造同名头；命中 `whale:cache:auth:user:perm:{userId}` 则注入；未命中/哨兵/异常回落 `lb://whale-auth/auth/me`；两级均失败注入空串并 warn
- [x] 6.8 whale-audit：引入 `whale-auth-sdk` + `whale.permission.header.enabled=true`；查询接口（`LogIngestController` 2 个 + `AuditLogController` 全部）加 `@RequirePermission("sys:audit:log:list")`
- [x] 6.9 whale-auth `db/init.sql` 新增权限点 `sys:audit:log:list`

## 七、自动配置与网关

- [x] 7.1 `WhaleLogAutoConfiguration`：`@AutoConfiguration(after={MybatisPlusConfig, AuditLogAutoConfiguration})` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass`
- [x] 7.2 Bean 内用 `ObjectProvider<XxxMapper>` **实例化期**延迟解析（**禁止** `@ConditionalOnBean(Mapper)`）；解析不到返回 `null`
- [x] 7.3 `AutoConfiguration.imports` 追加 `WhaleLogAutoConfiguration`
- [x] 7.4 网关白名单放行 `/audit/log/frontend/**`、`/audit/log/behavior/**`；限流规则覆盖上报接口

## 八、测试与门禁

- [x] 8.1 `ApiLogFilterTest`：放行 / 排除路径 / `sample-rate=0` / 超大 body **超限停镜像（不截断透传）** / **响应体全量 tee 透传不丢** / 异步派发跳过
- [x] 8.2 `LimitedCachingWrapperTest`：超限停缓存
- [x] 8.3 `OutboundLogInterceptorTest`（`MockRestServiceServer`）：状态 / 耗时 / traceId 透传 / 超时报错 / 敏感字段掩码
- [x] 8.4 `SensitiveDataMaskerTest`：新增 `maskJson` 用例（合法 JSON 脱敏 / 非法 JSON 仅截断）
- [x] 8.5 `LogIngestControllerTest`：批量上限抛 `PARAM_ERROR` / `sink` 被调用 / `permitAll` 生效
- [x] 8.6 whale-audit 两个新 `*ServiceImplTest`：分页 / `size` 收敛 / 倒序
- [x] 8.7 实时 IT：`LiveApiLogIT`（`api_log` ≥1 行且无明文凭据）、`LiveFrontendLogIT`（上报→查询可见）
- [x] 8.8 `mvn -f whale-backend/pom.xml clean verify -T 1C`：BUILD SUCCESS
- [x] 8.9 `mvn -f whale-backend/pom.xml checkstyle:check`：全 reactor 0 违规（E16 Javadoc / 行宽 ≤150）
- [x] 8.10 `PermissionHeaderGlobalFilterTest`：缓存命中注入 / 未命中回落 `/auth/me` / 两级失败注入空串并告警
- [x] 8.11 `RequirePermissionInterceptorTest`：无注解直放行 / `super_admin` 放行 / 命中权限码放行 / 无码头 403

## 九、留痕与销账

- [x] 9.1 检查报告 `docs/reviews/2026-10-18-wave5.10-multichannel-log.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号
- [x] 9.2 `docs/devlog/2026-10-18.md` 追加本波次条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 9.3 台账销账：`register.md` REQ-SEC-006 + AUTH-06/11、`whale-auth-backlog.md`、`next-steps.md`（删除线 + ✅）
- [x] 9.4 变更已 commit（`701a516`）并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 十、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：**通过**
- 依据：`mvn -f whale-backend/pom.xml clean verify -T 1C` **BUILD SUCCESS**（**247 用例全绿 / 0 fail / 0 error / 0 skipped**）、`mvn -f whale-backend/pom.xml checkstyle:check` **全 29 模块 0 违规**、实时 IT `LiveApiLogIT` + `LiveFrontendLogIT` **7/0/0**（自建 MySQL 3307 实例）、[本波检查报告](../reviews/2026-10-18-wave5.10-multichannel-log.md)
- 规格偏离：① 响应体 tee 透传（替代缓冲式回写）；② 权限码头组件落点改为网关注入 + starter 通用拦截器（§八，用户批准）；③ 单测覆盖超出 A8 原表（补 2 类）；④ `api_log.module` 未赋值（登记残余）
- 残余项：见 [spec §六](./spec.md#六风险与残余) 与 [tasks 待做项](./tasks.md#待做项本波明确不做登记备查)；另 `ApiLogFilter` 两个私有收集方法 6 参数（C4 P2）

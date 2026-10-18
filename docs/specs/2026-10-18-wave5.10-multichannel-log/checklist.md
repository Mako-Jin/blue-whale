# Checklist：Wave 5.10 审计日志多通道完善（后端 Wave A，REQ-SEC-006）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含 4 表契约、接口契约、配置项、组件落点、风险
- [x] 1.2 `register.md` 新增 REQ-SEC-006 并关联 REQ-SEC-001/002/003
- [x] 1.3 `naming-conventions.md` 已登记 4 张表名（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`）与 `@bluewhale/whale-log-sdk` 包名

## 二、表结构

- [ ] 2.1 4 表 DDL 追加到 `whale-audit/db/init.sql` 与 `whale-auth/db/init.sql`，字段/索引与 spec §3.1 一致
- [ ] 2.2 4 表为 append-only（无 `update_time`/`deleted`，`id` 雪花、`create_time DEFAULT CURRENT_TIMESTAMP`），对齐 `audit_log`
- [ ] 2.3 starter 4 实体（`@TableName` + `@TableId(ASSIGN_ID)`，不继承 `BaseEntity`）与 4 `Mapper extends BaseMapper` 就位

## 三、落库支撑与 sink

- [ ] 3.1 `LogPersistSupport`：事务活跃注册 `afterCommit`，否则投递线程池；组装/落库异常仅 `log.warn`，不冒泡
- [ ] 3.2 `whaleLogExecutor` 为**独立**线程池（core=2/max=4/queue=2048/`CallerRunsPolicy`/前缀 `whale-log-`），未复用 `auditLogExecutor`
- [ ] 3.3 `LogSink` 抽象 + `LocalLogSink`；`LogSinkProperties`(`whale.audit.sink`) 预留，`mq` 未实现时 fail-fast 提示 + 回落 local

## 四、后端接口日志（Filter）

- [ ] 4.1 `ApiLogFilter extends OncePerRequestFilter`；`shouldNotFilterAsyncDispatch()=true`
- [ ] 4.2 自研 `LimitedCaching{Request,Response}Wrapper`：超 `max-body-length` 停缓存；Content-Type 白名单只缓存 JSON/表单
- [ ] 4.3 `finally` **必须** `copyBodyToResponse()`（客户端响应体不丢）
- [ ] 4.4 配置 `whale.log.api.*`（enabled/sample-rate/max-body-length/exclude-paths），`exclude-paths` 含上报接口防回环

## 五、出站三方调用日志（Interceptor）

- [ ] 5.1 `OutboundLogInterceptor` + `OutboundLogRestTemplateCustomizer`：经 `RestTemplateBuilder` 自动挂载，业务零改动
- [ ] 5.2 采集 method/uri/host/cost_ms/status/biz_status/error_msg/request+response 摘要；traceId 透传（缺失生成 32 位 UUID）
- [ ] 5.3 超时/连接异常记 `biz_status=0` 并原样抛出
- [ ] 5.4 `SensitiveDataMasker#maskJson` 新增（解析失败仅截断）；既有 `maskArguments`/`mask` 语义未变

## 六、接收与查询接口（whale-audit）

- [ ] 6.1 `LogIngestController` 上报 2 接口（`POST /frontend/batch`、`POST /behavior/batch`）`permitAll`，`items` 上限 50
- [ ] 6.2 上报以网关 `X-User-Id` 回填 `user_id`；`sink.submit` 后立即 `Result.ok()`
- [ ] 6.3 查询 2 接口（`GET /frontend/page`、`GET /behavior/page`）带 `@RequirePermission("sys:audit:log:list")`；`size ∈ [1,200]`、`create_time` 倒序
- [ ] 6.4 AUTH-06 权限点种子（`sys:audit:log:list`）入库/登记

## 七、自动配置与网关

- [ ] 7.1 `WhaleLogAutoConfiguration`：`@AutoConfiguration(after={MybatisPlusConfig, AuditLogAutoConfiguration})` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass`
- [ ] 7.2 Bean 内用 `ObjectProvider<XxxMapper>` **实例化期**延迟解析（**禁止** `@ConditionalOnBean(Mapper)`）；解析不到返回 `null`
- [ ] 7.3 `AutoConfiguration.imports` 追加 `WhaleLogAutoConfiguration`
- [ ] 7.4 网关白名单放行 `/audit/log/frontend/**`、`/audit/log/behavior/**`；限流规则覆盖上报接口

## 八、测试与门禁

- [ ] 8.1 `ApiLogFilterTest`：放行 / 排除路径 / `sample-rate=0` / 超大 body 截断 / 响应体回写不丢 / 异步派发跳过
- [ ] 8.2 `LimitedCachingWrapperTest`：超限停缓存
- [ ] 8.3 `OutboundLogInterceptorTest`（`MockRestServiceServer`）：状态 / 耗时 / traceId 透传 / 超时报错 / 敏感字段掩码
- [ ] 8.4 `SensitiveDataMaskerTest`：新增 `maskJson` 用例（合法 JSON 脱敏 / 非法 JSON 仅截断）
- [ ] 8.5 `LogIngestControllerTest`：批量上限抛 `PARAM_ERROR` / `sink` 被调用 / `permitAll` 生效
- [ ] 8.6 whale-audit 两个新 `*ServiceImplTest`：分页 / `size` 收敛 / 倒序
- [ ] 8.7 实时 IT：`LiveApiLogIT`（`api_log` ≥1 行且无明文凭据）、`LiveFrontendLogIT`（上报→查询可见）
- [ ] 8.8 `mvn -f backend/pom.xml clean verify -T 1C`：BUILD SUCCESS
- [ ] 8.9 `mvn -f backend/pom.xml checkstyle:check`：全 reactor 0 违规（E16 Javadoc / 行宽 ≤150）

## 九、留痕与销账

- [ ] 9.1 检查报告 `docs/reviews/2026-10-18-wave5.10-multichannel-log.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号
- [ ] 9.2 `docs/devlog/2026-10-18.md` 追加本波次条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [ ] 9.3 台账销账：`register.md` REQ-SEC-006 + AUTH-06/11、`whale-auth-backlog.md`、`next-steps.md`（删除线 + ✅）
- [ ] 9.4 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 十、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：待填
- 依据：待填（构建/覆盖率/实时 IT/检查报告）
- 规格偏离：待填（如有）
- 残余项：见 [spec §六](./spec.md#六风险与残余) 与 [tasks 待做项](./tasks.md#待做项本波明确不做登记备查)

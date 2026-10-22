# P1-3（用户口径）审计日志与脱敏 任务清单

> 对应规格：[spec.md](./spec.md) ｜ 状态：**已完成（2026-10-22，实机验收通过）**
> 执行方式：按 T1 → T5 顺序；T2/T3/T4 可在 T1 完成后并行改码，构建与实机验收必须串行在最后。

## T1 查询串脱敏（starter）

- [x] `SensitiveDataMasker` 新增 `maskQueryString(String)`；`SECRET_KEYS` 扩充 7 键。
- [x] `ApiLogFilter#fillRequest` 查询串改走 `maskQueryString`。
- [x] `OutboundLogInterceptor#buildTargetUri` 改实例方法并脱敏查询串。
- [x] 单测：`SensitiveDataMaskerTest` / `ApiLogFilterTest` / `OutboundLogInterceptorTest`。
- 完成判据：单测全绿；明文 token/password 不出现在落库字段中。

## T2 审计转发器（starter）

- [x] 新增 `AuditLogForwardProperties`（`whale.audit.forward.enabled/url/timeout-millis`）。
- [x] 新增 `AuditLogForwarder`（RestClient + 超时 + Jackson 3，失败返回 false 不抛出）。
- [x] `AuditLogAspect` 构造器加 `forwarder`，`persist` 改为「转发成功即返回，否则本地落库」。
- [x] `AuditLogAutoConfiguration` 条件装配 forwarder 并注入切面。
- [x] 修正 `AuditLogAspectTest` / `LiveAuditLogIT` 构造器调用；补转发成功/失败两条单测。
- 完成判据：`enabled=false` 时行为与现状完全一致；`enabled=true` 且转发失败时仍有本地记录。

## T3 whale-audit 摄入端点

- [x] `AuditLogService` 增加 `int ingest(List<AuditLogRecord>)`。
- [x] `AuditLogServiceImpl` 实现：≤50 条、强制 `id=null`、事务、返回条数。
- [x] `AuditLogController` 增加 `POST /audit/log/ingest`。
- [x] `ApiLogProperties` 默认排除 `/audit/log/ingest/**`。
- 完成判据：单次 51 条返回参数错误；正常批量入库且主键为目标库生成。

## T4 网关与试点配置

- [x] 网关 `application.yml` internal-deny 增加 `/audit/log/ingest/**`。
- [x] whale-auth `application.yml` 增加 `whale.audit.forward.*`（默认开启，9101）。
- 完成判据：经网关访问摄入端点 403；auth 能解析到转发配置。

## T5 构建、验收与交付

- [x] 构建 4 个模块（starter/auth/audit/gateway）`package -DskipTests` 成功。
- [x] 启动 whale-audit（9101）、重启 auth（8081），登录后确认 `bluewhale_audit.audit_log` 出现 `module=whale-auth`。
- [x] 实机确认 `bluewhale_auth.api_log.query_string` 已脱敏。
- [x] 反向验证：停 whale-audit 后登录，auth 本地 `audit_log` 仍落库且有转发失败告警。
- [x] 反向验证：网关 `POST /audit/log/ingest` → 403。
- [x] 同步 `docs/devlog/2026-10-22.md`、整改清单、roadmap。
- [x] 提交并推送 Gitee `origin`，再推 GitHub `ci`。
- 完成判据：正向通过 + 两项反向验证通过 + 文档同步 + 推送成功。

## 实机验收记录（2026-10-22）

| 项 | 结果 |
|---|---|
| whale-audit 9101 / whale-auth 8081 / whale-gateway 8080 | 均 `Started`，Nacos 注册成功 |
| `POST /auth/login`（admin/123456） | 200，返回 access/refresh token |
| `bluewhale_audit.audit_log` 转发落库 | 新记录 `id=2113156718682673154`、`module=whale-auth`、`operation=用户登录`、`biz_status=1` |
| `bluewhale_auth.api_log.query_string` | `/user/page` 落库 `current=1&size=5&token=***&password=******&mobile=138****8000` |
| 转发失败回退 | 停 9101 后登录 → `bluewhale_auth.audit_log` 新增 LOGIN 记录；auth 日志 `审计日志转发失败，回退本地落库` |
| 网关内网屏蔽 | `POST /audit/log/ingest` → 403 `内网接口不允许外部访问` |

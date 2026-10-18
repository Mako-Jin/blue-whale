# Tasks：Wave 5.12 审计日志后端补强（REQ-SEC-006 残余收口）

> 关联 spec：[spec.md](./spec.md)
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [ ] E1 | `api_log.module` 回填：`ApiLogProperties` 新增 `module`（默认 `${spring.application.name:unknown}`）+ `ApiLogFilter#fillRequest` 设值 + `ApiLogFilterTest` 断言 | 无 | 高 | `module` 非空且等于配置值 | ⏳ |
| [ ] E2 | 网关按路径限流：`RateLimitProperties` 新增 `pathRules`（`PathRule`：`pathPattern`/`limit`/`windowSeconds`）+ `RateLimitGlobalFilter` 最长匹配选取 + 计数键加规则维度 + `whale-gateway/application.yml` 追加上报路径规则 + `RateLimitGlobalFilterTest` | E1 | 高 | 上报路径命中独立规则；其他路径沿用全局；超阈值 429 | ⏳ |
| [ ] E3 | 保留期扩多表：新增 `RetentionProperties`（`whale.audit.retention`：`enabled=false`/`days=30`/`cron`）+ `LogRetentionScheduler`（`@ConditionalOnProperty` + `@Scheduled` 清理 4 表）+ `WhaleAuditApplication` 加 `@EnableScheduling` + 单测 | E1 | 高 | `enabled=false` 不清理；`enabled=true` 删除阈值前记录 | ⏳ |
| [ ] E4 | 构建与门禁：`mvn -B -f backend/pom.xml clean verify -T 1C` + `checkstyle:check` | E1–E3 | 高 | BUILD SUCCESS；全 reactor 0 违规 | ⏳ |
| [ ] E5 | 留痕收口：检查报告 `docs/reviews/2026-10-18-wave5.12-audit-backend-polish.md` + devlog 追加 + 台账销账（`register.md` REQ-SEC-006 / `next-steps.md`） | E4 | 中 | 四类信息齐备 | ⏳ |
| [ ] E6 | 自动 commit + push（Gitee origin/main） | E5 | 中 | 工作区干净且 `main == origin/main` | ⏳ |

## 实施注意（给实现者的事实约束）

- **E1 唯一改动点**：`ApiLogFilter#fillRequest` 内新增 `record.setModule(...)`；**不要**改动 `fillResponse`/`fillResult` 或采样/排除路径逻辑。`module` 默认取 `spring.application.name`，勿硬编码服务名（D5 配置外置）。
- **E2 键约定**：Redis 限流键前缀仍为 `whale:gateway:rate-limit`（REQ-ARCH-004）；新增的「规则维度」段仅用于区分窗口，不得引入新前缀。`pathRules` 默认空列表时行为与现状**完全一致**（回归安全）。
- **E2 匹配**：使用 `AntPathMatcher`，多条命中取**最长 `pathPattern`**（特化优先）；避免规则顺序敏感。
- **E3 默认关闭**：`whale.audit.retention.enabled` 默认 `false`；**只清理 4 张日志表**（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`），**严禁**清理 `audit_log`（属 AUTH-09）。删除失败仅 `warn`。
- **E3 装配**：`LogRetentionScheduler` 用 `@ConditionalOnProperty(name="whale.audit.retention.enabled", havingValue="true")`；`@EnableScheduling` 加在 `WhaleAuditApplication`。
- **C4 方法参数 ≤ 4 / D1–D5 魔法值常量化 / E16 中文 Javadoc**：新增/改动的所有方法、类（含 `private`）须有中文 Javadoc 与 `@author`；数值/字符串上限一律 `static final` 常量。
- **不触碰**：本波不改 `AuditLogAspect`、`SensitiveDataMasker`、`LogPersistSupport`、`LogIngestController` 既有语义。

## 待做项（本波明确不做，登记备查）

- **MQ 汇聚（AUTH-10）**：RabbitMQ 未就绪。
- **异步派发丢采集**：`shouldNotFilterAsyncDispatch()=true` 维持已知丢采集。
- **前端 `whale-log-sdk`（Wave B）**：另立 [Wave 5.14](../2026-10-18-wave5.14-whale-log-sdk/spec.md)。
- **`audit_log` 保留/归档（AUTH-09）**：阻塞于 whale-job。

# Wave 5.21 任务分解（并行执行）

> 规格：[spec.md](spec.md)。执行原则：**先冻结契约（spec §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归**。
> 文件所有权：各轨仅改自己模块与已授权的 `whale-auth/db/init.sql`（仅 T1）；跨轨文件冲突为零。

## 并行轨与任务

| 编号 | 任务 | 落点模块 | 依赖 | 产物 | 自检 |
|---|---|---|---|---|---|
| **T1** | whale-file 最小可用：`FileStorage` SPI + `LocalFileStorage` + `FileProperties` + `FileInfo`/Mapper/Service/Impl/Controller + `FileUploadVO`/DTO + pom 依赖 + yml（datasource/whale.file）+ `whale-auth/db/init.sql` 4 权限点种子 + 单测 | `whale-services/whale-file`、`whale-auth/.../db/init.sql` | 无 | 5 端点 + 2 测试类 | `mvn -o -pl whale-backend/whale-services/whale-file test` + `checkstyle:check` |
| **T2** | whale-job 归档导出：`JobLogArchiveHandler` + `JobArchiveClient`（RestClient，直连 `whale.job.file-service-url`）+ `JobProperties#fileServiceUrl/archiveBatchSize` + `db/init.sql` 种子任务 id 2 + 单测（上传成功才删 / 失败不删） | `whale-services/whale-job` | T1 契约（字面量冻结，不阻塞编码） | 1 handler + 2 测试类 | `mvn -o -pl whale-backend/whale-services/whale-job test` + `checkstyle:check` |
| **T3** | whale-audit 导出与归档：`POST /audit/log/export`（EXPORT 元审计）+ `AuditLogArchiver`（挂既有 `LogRetentionScheduler`，`archive-enabled` 默认 false，只导出不物理删）+ `ExportProperties` + 单测 | `whale-ops/whale-audit` | T1 契约 | 1 端点 + 2 测试类 | `mvn -o -pl whale-backend/whale-ops/whale-audit test` + `checkstyle:check` |
| **T4** | 网关熔断：`whale-gateway/pom.xml` 增 `spring-cloud-starter-circuitbreaker-reactor-resilience4j` + `CircuitBreakerProperties` + 为 4 条关键路由配 `CircuitBreaker`/`TimeLimiter` + fallback 503 `Result` + 单测 | `whale-services/whale-gateway` | 无 | 配置 + 1 测试类 | `mvn -o -pl whale-backend/whale-services/whale-gateway test` + `checkstyle:check` |
| **T5** | 网关动态路由：`spring.config.import: optional:nacos:whale-gateway-routes.yaml` + `NacosRouteDefinitionRepository`（Nacos dataId 读 routes JSON）+ 静态兜底 + `whale-gateway/application.yml` 调整 + 单测 | `whale-services/whale-gateway` | 无（与 T4 同模块，**文件不重叠**：T4 改 pom/新增 `CircuitBreakerProperties`/`application.yml` → **application.yml 归 T5**） | 1 仓储实现 + 1 测试类 | 同上 |
| **T6** | 网关内网屏蔽：`InternalApiDenyGlobalFilter`（`/file/internal/**` → 403 `Result`）+ 单测 | `whale-services/whale-gateway` | T1 契约 | 1 过滤器 + 1 测试类 | 同上 |
| **T7** | DDD 第 3 步（AUTH-16）：`domain/{role,menu,permission,dept}/**` 四聚合 + 仓储接口 + `infrastructure/persistence/{assembler,repository}` + `DddMigrationProperties`（`whale.auth.ddd.*`，默认 false）+ 服务层按 flag 双路径 + 单测 | `whale-services/whale-auth`（**不改 `db/init.sql`**） | 无 | 4 聚合 + 4 仓储实现 + flag + 3 测试类 | `mvn -o -pl whale-backend/whale-services/whale-auth test` + `checkstyle:check` |
| **T8** | 台账修订（零代码）：审计 final §6.2 三项改判、auth backlog AUTH-09/16/27、新增 `docs/plans/whale-gateway-backlog.md`、roadmap §2.2 口径、next-steps 过期提示 | `docs/**` | 无 | 文档 | 人工核对，无构建 |
| **T9** | 收尾（串行，主执行）：停服务 → `mvn clean install -T 1C` → 起中间件与 5 服务冒烟（上传/归档/导出/403/熔断）→ 检查报告 `docs/reviews/2026-10-19-wave5.21-four-tracks.md` → devlog 追加 → roadmap/task-breakdown 勾选 → commit + push origin | 全仓 | T1–T8 | 报告 + 日志 + 提交 | 全量 BUILD SUCCESS + 冒烟证据 |

> **T4 / T5 / T6 由同一执行单元串行完成**：三者同属 `whale-gateway`，`application.yml` 为单点所有权文件（由该单元统一改写），避免并行写冲突。

## 执行波次

```
Wave 5.21-a（并行）：T1 ｜ T4 ｜ T5 ｜ T6 ｜ T7 ｜ T8
Wave 5.21-b（并行，依赖 T1 契约）：T2 ｜ T3
Wave 5.21-c（串行）：T9 收尾
```

> 注：T2/T3 仅依赖「字面量与 VO 字段」契约（spec §三），契约已冻结故可与 T1 同波开工；为降低构建竞争风险，仍建议 T2/T3 在 T1 返回后启动。

## 完成定义（DoD）

- 各轨模块级 `test` + `checkstyle:check` 全绿（0 违规）。
- 全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时冒烟证据：上传 `code=0`、归档任务导出成功且过期 `job_log` 被删、`/audit/log/export` 返回文件、`/file/internal/**` 403、熔断 fallback 503。
- `docs/reviews/` 检查报告 + `docs/devlog/2026-10-19.md` 追加 + 台账勾选。
- commit 并 push origin（Gitee）。

# Tasks：权限中心残余收口（Wave 5.11）

> 关联 spec：[spec.md](./spec.md) ｜ 关联台账：AUTH-01 / AUTH-05 / AUTH-07 / AUTH-13
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [x] B1 | 文档先行：本波 spec/tasks/checklist + `docs/design/audit-log.md` 更新（`result_body` 列、`recordResult()` 语义、元审计）+ `docs/design/distributed.md` §3.6 降级告警口径 | 无 | 高 | 文档一致可追溯 | ✅ |
| [x] B2 | AUTH-01：`WhaleGatewayProperties.Jwt` 提取 `DEFAULT_PLACEHOLDER_SECRET` 常量 + 新增 `GatewaySecretPlaceholderChecker`（`@Component` + `InitializingBean`，非 dev 命中占位值抛 `IllegalStateException`）+ 单测（非 dev 占位拒绝 / dev 放行 / 已覆盖放行） | B1 | 高 | 三个 Scenario 单测全绿 | ✅ |
| [x] B3 | AUTH-05：`whale-security` 新增 `SecurityDegradationAlerter`（ERROR + `SECURITY_DEGRADATION` 标记 + 计数）+ `RedisTokenBlacklist`（add/contains 两分支）与网关 `TokenBlacklistChecker` 接入 + 单测 | B1 | 高 | 降级计数 +1、日志含标记；既有行为不变 | ✅ |
| [x] B4 | AUTH-13：`audit_log` 补列 `result_body TEXT`（`whale-auth`/`whale-audit` 两份 `db/init.sql`）+ `AuditLogRecord.resultBody` + `AuditLogAspect` 采集（`recordResult()` 门控 → JSON 序列化 → `maskJson` → 上限截断）+ `AuditLogVO` 暴露 + 单测（开启/默认不落/超大截断/序列化失败仅告警） | B1 | 高 | 四类分支单测全绿；未开启时既有审计用例不回归 | ✅ |
| [x] B5 | AUTH-07：`AuditLogController` 3 个查询接口加 `@AuditLog(type = OperateType.QUERY)` + 单测（注解存在性/属性断言） | B1 | 高 | 元审计可落同库 `audit_log`（QUERY）；导出不在范围 | ✅ |
| [x] B6 | 收尾归置：新建 `docs/whale-auth/originals/`（design/plans/requirements/specs/reviews/devlog 全部相关文档副本，原位置不动）+ `docs/whale-auth/final/` 整合最终版 | B1 | 中 | 副本齐备且原文件未改；最终版含能力矩阵/接口与权限点/配置/表结构/残余 | ✅ |
| [x] B7 | 留痕与门禁：检查报告 `docs/reviews/2026-10-18-wave5.11-auth-residual.md` + devlog §31 + 台账销账（`whale-auth-backlog.md` / `register.md` / `next-steps.md`）+ `clean verify -T 1C` + `checkstyle:check` | B2–B6 | 高 | BUILD SUCCESS；0 违规；四节留痕齐备 | ✅ |
| [x] B8 | 自动 commit + push（Gitee origin/main） | B7 | 中 | 工作区干净且 `main == origin/main` | ✅ |

## 实施注意（给实现者的事实约束）

- **网关无 autoconfig imports 文件**：`whale-gateway` 用 `@SpringBootApplication` 组件扫描 `com.bluewhale.gateway`，因此新类用 `@Component` 即可（对齐 `WhaleGatewayProperties` 既有风格），**不要**新建 `META-INF/spring/*.imports`。
- **网关不可引 starter**：Servlet/WebFlux 栈冲突；AUTH-05 的共用类只能放 `whale-security`（网关已依赖，纯库无 Spring 依赖）。`SecurityDegradationAlerter` 用 `lombok @Slf4j`（whale-security 已有 lombok）+ `java.util.concurrent.atomic.AtomicLong`，静态工具式（不改任何构造器签名 → 不破坏既有测试）。
- **C4 方法参数 ≤ 4**：`AuditLogAspect` 构造器已 4 参（mapper/masker/executor/environment），**禁止**再加参数；返回体序列化用类内 `private static final ObjectMapper`（Jackson 3 `tools.jackson.databind.ObjectMapper`，线程安全），勿改构造器。
- **脱敏复用**：`SensitiveDataMasker#maskJson`（Wave 5.10 新增，解析失败仅按 `MAX_PARAM_LENGTH` 截断、不抛）为唯一脱敏入口；**不得**新增脱敏逻辑或改既有 `maskArguments`/`mask` 语义。
- **长度上限常量化**：`result_body` 截断上限、占位密钥字面量、告警标记字符串一律 `static final` 常量（D1–D5 魔法值门禁）。
- **审计切面装配时序**：`AuditLogAutoConfiguration` 用 `ObjectProvider<AuditLogMapper>` 实例化期解析（Wave 5.1e 修复）；whale-audit 的 `AuditLogMapper` 由 starter `MybatisPlusConfig` 的 `@MapperScan("com.bluewhale.**.mapper")` 覆盖，**勿**新增 `@ConditionalOnBean(Mapper)`。
- **元审计只加查询接口**：`AuditLogController` 只有 `/page`、`/{id}`、`/trace/{traceId}` 三个方法；**没有**导出接口，`EXPORT` 元审计不在本波。
- **DDL 双份一致**：`audit_log` 增列须同时改 `whale-auth/db/init.sql` 与 `whale-audit/db/init.sql`，字段注释/顺序对齐。
- **归置不删不动**：`docs/whale-auth/originals/` 仅放**副本**，`docs/**` 原文件路径与内容均不得改动或删除。

## 待做项（本波明确不做，登记备查）

- **AUTH-08 审计导出**（whale-file 未启动）→ `EXPORT` 元审计随之不做。
- **AUTH-09 保留归档任务**（whale-job 未启动）。
- **AUTH-10 MQ 汇聚**（RabbitMQ 未就绪）。
- **AUTH-15/16/17 DDD 深水区迁移、AUTH-32/33/34 前端对接、AUTH-39 refresh 轮换实施**：另行排期。
- **Micrometer/Prometheus 指标接入**：AUTH-05 以进程内计数代替，偏差已登记。

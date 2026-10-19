# Wave 5.21 规格：四轨收口（网关熔断+动态路由 / 归档链路 / 台账修订 / DDD 第 3 步）

- 日期：2026-10-19 ｜ 作者：BlueWhale
- 用户指令：对「权限 / 审计 / 网关 / 定时任务」四服务未完成项盘点后，**四项全选**推进：
  1. 网关熔断与动态路由
  2. 解开归档链路（whale-file → AUTH-08 审计导出 / whale-job `job_log` 归档 / AUTH-09 剩余）
  3. 修文档台账
  4. 权限中心 DDD 第 3 步（AUTH-16）
- 关联需求：REQ-DIST-003（限流熔断）、REQ-ARCH-001（DDD）、REQ-FUNC-006（文件/定时任务）、REQ-SEC-001/006（审计与其归档）、REQ-DOC-002 / REQ-QA-002（文档与检查报告）
- 台账来源：`docs/plans/whale-auth-backlog.md`（AUTH-08/09/10/16/27）、`docs/whale-audit/final/README.md` §6.2、`docs/reviews/2026-10-19-wave5.20-job-completion.md` §三、`docs/plans/roadmap.md` §2.2/§3.3

## 一、现状事实（侦察结论，2026-10-19）

| 事实 | 证据 |
|---|---|
| `whale-file` 仅骨架：启动类 + `db/init.sql`（`file_info` 表）+ pom（web/validation/actuator/nacos/tools）+ yml（8085），**无 controller/service/entity、无存储配置项** | `backend/whale-services/whale-file/**` |
| 本机**无 MinIO / 对象存储** | `infrastructure/` 无 compose 与存储配置 |
| `whale-job` 已有 `JobHandler` SPI、`JobScheduler`、`JobLog` 实体（`startTime`/`endTime`/`status`/`result`/`errorMsg`）、`JobProperties`（`whale.job.log-retention-days`）、`JobLogCleanupHandler`（仅逻辑删除） | Wave 5.20 交付物 |
| 网关**无限流之外的熔断**：全模块无 resilience4j / CircuitBreaker | `whale-gateway/pom.xml`、源码 Grep |
| 网关 Nacos Config 坐标存在但 **yml 无 `spring.config.import`**，路由为本地静态 `routes`，**无 `RouteDefinitionRepository` 实现** | `whale-gateway/src/main/resources/application.yml` |
| `whale-auth` DDD 仅完成 `domain/user/**` + `infrastructure/persistence/**`（User 聚合 + 手写 Assembler + 仓储实现）；**无 `whale.auth.ddd.*` feature flag** | `backend/whale-services/whale-auth/src/main/java/com/bluewhale/auth/{domain,infrastructure}/**` |
| 审计核心与四通道均闭环；`@Sensitive` 注解兜底与四通道查询元审计**已由 Wave 5.19 落地**，但 `docs/whale-audit/final/README.md` §6.2 仍记为残余 → **文档陈旧** | `Sensitive.java` 存在；`LogIngestController` 四查询端均带 `@AuditLog(type=QUERY)` |

## 二、范围与设计决策

### 轨 A：归档链路（文件服务落地 → 导出/归档闭环）

**A1 whale-file 最小可用服务**（新能力，非重写）

- 存储抽象 `FileStorage` SPI + `LocalFileStorage` 实现（落盘 `${whale.file.storage.root:D:/data/whale-file}`，`bucket` 作为一级目录）；`FileProperties` 承载 `storage.root` / `bucket.default` / 上传大小上限。
- 实体 `FileInfo`（对齐既有 `db/init.sql`，含 `BaseEntity` 公共字段）+ `FileInfoMapper` + `FileInfoService/Impl` + `FileController`。
- 接口（**冻结契约**）：
  | 方法 | 路径 | 鉴权 | 说明 |
  |---|---|---|---|
  | POST | `/file/upload` | `@RequirePermission("sys:file:upload")` | `multipart/form-data`：`file`（必填）、`bucket`（可选，默认 `${whale.file.bucket.default:bluewhale}`）→ `Result<FileUploadVO>` |
  | POST | `/file/internal/upload` | 无（服务间内网接口，**网关侧显式 403 屏蔽**） | 同上传语义，供 whale-job / whale-audit 归档调用，不经网关 |
  | GET | `/file/{id}/download` | `@RequirePermission("sys:file:download")` | 流式下载（`Content-Disposition` 用 `originalName`） |
  | GET | `/file/page` | `@RequirePermission("sys:file:list")` | 分页（`originalName` 模糊 / `bucket` 等值 / 时间范围） |
  | DELETE | `/file/{id}` | `@RequirePermission("sys:file:delete")` | 逻辑删除元数据（物理对象保留，附说明） |
- `FileUploadVO` 字段（冻结）：`id`/`fileName`/`originalName`/`bucket`/`objectKey`/`fileSize`/`contentType`/`md5`。
- 依赖：`whale-spring-boot-starter`（Result/异常/MyBatis 填充/权限码头拦截，`whale.permission.header.enabled: true`）+ `mysql-connector-j`；`application.yml` 增 `datasource`（`bluewhale_file`）+ `whale.file` 段。
- 权限点种子：`whale-auth/db/init.sql` 追加 `sys:file:upload|download|list|delete`（**仅 A1 拥有该文件改动权**）。

**A2 whale-job 归档导出（落 Wave 5.20 遗留）**

- 新增 `JobLogArchiveHandler`（Bean 名 `jobLogArchiveHandler`，种子任务 id 2，cron 每日 04:00，`misfire_policy=SMART`）：① 按 `whale.job.log-retention-days` 取过期 `job_log`；② 生成 CSV（表头 + 行）；③ 调 `POST {whale.job.file-service-url}/file/internal/upload` 上传（`multipart`，`bucket=bluewhale-job-archive`）；④ **仅上传成功后**才逻辑删除该批 `job_log`；⑤ whale-file 不可达时**跳过不做任何删除**并写 `error_msg`。
- 客户端：`RestClient` + `@ConfigurationProperties("whale.job")` 增 `fileServiceUrl`（默认 `http://127.0.0.1:8085`，**不走 lb://，避免新增 loadbalancer 依赖**）+ `archiveBatchSize`（默认 2000）。
- 保留 `JobLogCleanupHandler` 作为无文件服务时的降级清理路径（不删除，仅在上传成功路径删除——即归档优先）。

**A3 whale-audit 导出与审计归档**

- `POST /audit/log/export`（`@RequirePermission("sys:audit:log:list")` + `@AuditLog(type=EXPORT, targetType="audit_log", recordResult=false)`）：按既有查询条件导出 CSV → 上传 whale-file `internal/upload`（`bucket=bluewhale-audit-archive`）→ 返回 `FileUploadVO`；`size` 上限收敛（导出上限可配 `whale.audit.export.max-rows`，默认 5 万）。
- `audit_log` 归档：新增 `AuditLogArchiver`，挂在既有 `LogRetentionScheduler`（复用 `whale.audit.retention.*` 的 cron/开关语义），新增 `whale.audit.retention.archive-enabled`（**默认 false**）：导出 `create_time < now - retention.days` 的 `audit_log` 到 whale-file 后**仅逻辑/条件清理需显式开启**（本波只实现「导出归档 + 记录归档结果」，**不做物理删除**，合规数据安全优先）。
- 说明（与设计的偏差，写入文档）：AUTH-09 原设计「依赖 whale-job 调度」，本波改为**whale-audit 自身调度器承载**，理由是避免跨服务强耦合且组件已存在；偏差需登记。

### 轨 B：网关熔断与动态路由

- **B1 熔断降级**：引入 `spring-cloud-starter-circuitbreaker-reactor-resilience4j`（版本由 Spring Cloud BOM 管理，不硬编码）；为 `whale-auth` / `whale-audit` / `whale-job` / `whale-file` 四条关键路由配置 `CircuitBreaker` + `TimeLimiter` 过滤器（`whale.gateway.circuit-breaker.*` 可配超时/失败率/窗口），fallback 返回 `Result` 体（`code=503` 语义，HTTP 503）。
- **B2 动态路由**：`spring.config.import: nacos:whale-gateway-routes.yaml`（或 `optional:nacos:...`）启用配置中心；新增 `NacosRouteDefinitionRepository`（`RouteDefinitionRepository` 实现）从 Nacos dataId 读取 `routes` JSON，`SimpleRouteDefinitionLocator` 组合静态 + Nacos 动态路由；Nacos 不可用时**回落静态 `routes`** 不阻断启动。
- **B3 内部接口屏蔽**：新增 `InternalApiDenyGlobalFilter`（order 靠前）：外部请求 `/file/internal/**` → 403 `Result` 体（配合轨 A 的内网上传端，避免暴露面）。
- 测试：过滤器的单测（WebFlux 栈，Mockito）+ 构建验证；熔断端到端（Nacos/Redis 实机）尽力而为，不做强要求。

### 轨 C：权限中心 DDD 第 3 步（AUTH-16）

- 迁移 `Role` / `Menu` / `Permission` / `Dept` 四聚合（对齐 `User` 既有范式）：`domain/<ctx>/{aggregate,entity,repository}`（纯领域、无框架注解）+ `infrastructure/persistence/{assembler,repository}`（手写 Assembler + PO 复用既有 `SysXxx` Mapper）。
- feature flag `whale.auth.ddd.{role,menu,permission,dept}.enabled`（`@ConfigurationProperties("whale.auth.ddd")`，**默认 false**）；flag 关闭时走旧 `ServiceImpl` 路径（逐字节回归），开启时经新仓储读写，服务层按 flag 选择 —— 保证可秒级回滚。
- 不改 REST 契约；新增单测覆盖「聚合不变量 + Assembler 字段比对 + flag 双路径结果一致」。

### 轨 D：文档台账修订（零代码）

- `docs/whale-audit/final/README.md` §6.2：将「`@Sensitive` 注解兜底未实现」「四通道查询端点未加元审计」「四通道运行时端到端待验证」三项改为已闭环（Wave 5.19 D-a/D-b/D-c，附证据路径）。
- `docs/plans/whale-auth-backlog.md`：AUTH-09 更新为「已落地清理 + 归档导出（Wave 5.21）」、AUTH-27 勾选（每波已在跑）、AUTH-16 勾选（Wave 5.21）。
- 新增 `docs/plans/whale-gateway-backlog.md`（网关独立台账：闭环项 + 本轮熔断/动态路由 + 残余），并在 `docs/plans/roadmap.md` §2.2 勾选「限流熔断」的实际口径。
- `docs/plans/next-steps.md` 头部加「状态快照已过期，以 backlog/roadmap/final 为准」提示。

## 三、跨轨契约（先冻结，后并行）

1. whale-file 上传响应 `FileUploadVO` 字段名与 JSON 结构（见 A1）。
2. 内网上传路径字面量：`/file/internal/upload`（A1 实现端、A2/A3 调用端、B3 屏蔽端三方一致）。
3. 归档 CSV 形态：UTF-8（含 BOM）、首行表头、`\n` 行尾；文件名 `{jobLog|auditLog}-archive-{yyyyMMddHHmmss}.csv`。
4. 归档批次上限与失败语义：上传失败 → 本批**不删除**、记 `error_msg`、下次调度重试。
5. 权限点种子归属：`whale-auth/db/init.sql` 仅 A1 写入 `sys:file:*`；轨 C 不改该文件。

## 四、验证方案

| 层级 | 手段 | 通过口径 |
|---|---|---|
| 单测 | 各模块 `mvn -o -pl backend/<module> test` | 全绿，无回归（auth 164+ / audit 46+ / job 42+ / gateway 23+ / file 新增） |
| 静态门禁 | `mvn -o -pl backend/<module> checkstyle:check` | 0 违规（含 Javadoc 强制） |
| 全量构建 | `mvn clean install -T 1C`（停服务后） | 全模块 BUILD SUCCESS |
| 运行时冒烟 | 起 MySQL/Redis/Nacos + whale-file/whale-auth/whale-audit/whale-job/whale-gateway | `/file/upload` 经网关 `code=0`；`PUBLISH` 归档任务执行后 `job_log` 过期行被导出且删除、文件落 whale-file；`POST /audit/log/export` 返回文件；`/file/internal/**` 经网关 403；熔断过滤器生效（异常时 503 `Result`） |
| 台账文档 | 人工核对 | AUTH-08/09/16/27 口径更新；审计 final 三项改判有据 |

## 五、风险与回退

| # | 风险 | 处置 |
|---|---|---|
| 1 | resilience4j 与 Boot 4.0.8 兼容性未验证 | 若 BOM 版本不兼容，回退为「网关自研超时+失败率熔断过滤器」（不引三方依赖），并在报告记录偏差 |
| 2 | Nacos Config 引入 `spring.config.import` 后启动前置条件变化（无 Nacos 时应仍可启动） | 用 `optional:nacos:` 前缀 + 本地静态 `routes` 兜底；启动不依赖 Nacos |
| 3 | DDD 迁移引入行为差异 | feature flag 默认 `false`，新旧双路径单测对比；出问题即刻关闭 flag（秒级回滚） |
| 4 | 归档删除数据不可逆 | **上传成功后**才删除；上传失败不删除；本波 `audit_log` 不做物理删除 |
| 5 | 并行执行导致 Maven 本地仓库/构建竞争 | 各轨仅做模块级 `-o` 构建；全量 `clean install` 由收尾串行执行 |
| 6 | 新增内网接口暴露面 | 网关 `InternalApiDenyGlobalFilter` 显式 403（轨 B3），并单测覆盖 |

## 六、任务分解

见 [tasks.md](tasks.md)（Wave 5.21 T1–T9）。

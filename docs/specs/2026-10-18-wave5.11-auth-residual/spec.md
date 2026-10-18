# 权限中心残余收口 Spec（Wave 5.11）

> 关联需求：REQ-SEC-005（生产密钥校验）、REQ-SEC-002/003（令牌黑名单）、REQ-SEC-001（审计能力）、REQ-FUNC-002（权限中心六层级）、REQ-QA-002（必检留痕）、REQ-DOC-002（文档先行）、REQ-ARCH-004（命名规范）
> 台账来源：[whale-auth-backlog.md](../../plans/whale-auth-backlog.md) AUTH-01 / AUTH-05 / AUTH-07 / AUTH-13
> 关联设计：[audit-log.md](../../design/audit-log.md)、[ddd-whale-auth.md](../../design/ddd-whale-auth.md)、[distributed.md](../../design/distributed.md) §3.6

## Why

权限中心（whale-auth）主体能力已闭环（登录/登出、令牌黑名单、RBAC+ABAC 六层级、DDD 骨架、全接口鉴权 + 权限点种子、审计切面/脱敏/查询、幂等/缓存/分布式锁场景），但台账仍余 **4 项可立即动工**的残余：网关侧占位密钥无校验、黑名单降级无告警、审计读取无元审计、`@AuditLog#recordResult()` 属性悬空（无列可落）。同时权限中心原始文档散落在 `docs/{design,plans,requirements,specs,reviews,devlog}` 多处，需归置并输出整理后的最终版。

其余项（AUTH-08 导出 / AUTH-09 保留归档 / AUTH-10 MQ 汇聚）分别被 whale-file / whale-job / RabbitMQ 阻塞，本波不动。

## What Changes

- **AUTH-01 网关占位密钥校验**：`whale-gateway` 新增 `GatewaySecretPlaceholderChecker`（`InitializingBean`，非 dev profile 且密钥为占位值 → `IllegalStateException` 拒绝启动），占位值常量化到 `WhaleGatewayProperties.Jwt`，范式对齐 whale-auth 既有 `SecretPlaceholderChecker`。
- **AUTH-05 黑名单降级告警**：`whale-security` 新增 `SecurityDegradationAlerter`（`ERROR` + 固定标记 `SECURITY_DEGRADATION` + 自增计数），由 whale-auth `RedisTokenBlacklist` 与 网关 `TokenBlacklistChecker` 两处降级分支接入；同步在 `docs/design/distributed.md` §3.6 补降级语义与告警口径说明。
- **AUTH-07 审计读取元审计**：`AuditLogController` 的 3 个查询接口加 `@AuditLog(type = OperateType.QUERY)`，使「读审计」本身留痕（元审计落同库 `audit_log`，可经 `/audit/log/page` 反查）。
- **AUTH-13 `recordResult()` 落库**：`audit_log` 补列 `result_body TEXT`（`whale-auth` / `whale-audit` 两份 `db/init.sql` 同改）；`AuditLogRecord` 补 `resultBody`；`AuditLogAspect#buildRecord` 在 `recordResult()=true` 时序列化返回体 → `SensitiveDataMasker#maskJson` 脱敏 → 按上限截断；`AuditLogVO` 暴露该字段使「落库后可查」。
- **收尾归置**：新建 `docs/whale-auth/originals/`（权限中心全部相关文档的副本，原位置**不删不动**）与 `docs/whale-auth/final/`（整合后的最终版《权限中心最终设计文档》）。
- 不改动任何既有 REST 契约；除 `audit_log` 增列外无破坏性变更。

## Impact

- 影响能力：网关安全启动校验、令牌黑名单降级可观测、审计读取自审计、审计返回体留痕、权限中心文档归置。
- 影响代码：
  - `backend/whale-services/whale-gateway/`（新增 `config/GatewaySecretPlaceholderChecker.java`、改 `config/WhaleGatewayProperties.java`、`security/TokenBlacklistChecker.java`）
  - `backend/whale-common/whale-security/`（新增 `degradation/SecurityDegradationAlerter.java`）
  - `backend/whale-common/whale-spring-boot-starter/`（改 `audit/aop/AuditLogAspect.java`、`audit/entity/AuditLogRecord.java`、`audit/annotation/AuditLog.java` 注释）
  - `backend/whale-services/whale-auth/`（改 `security/RedisTokenBlacklist.java`、`src/main/resources/db/init.sql`）
  - `backend/whale-ops/whale-audit/`（改 `controller/AuditLogController.java`、`vo/AuditLogVO.java`、`src/main/resources/db/init.sql`）
  - `docs/`（design/audit-log.md、design/distributed.md、plans/whale-auth-backlog.md、requirements/register.md、devlog、本波 spec 三件套、`docs/whale-auth/**`）

## ADDED Requirements

### Requirement: 网关占位 JWT 密钥拒绝启动（REQ-SEC-005 延伸，AUTH-01）

网关 SHALL 在非 `dev` profile 启动时校验 JWT 签名密钥，命中占位值即以启动失败告警；`dev` profile 与已覆盖密钥 SHALL 正常启动。

#### Scenario: 非 dev + 占位密钥
- **WHEN** 网关以 `prod` profile 启动且 `whale.gateway.jwt.secret` 为默认占位值
- **THEN** `GatewaySecretPlaceholderChecker` 抛 `IllegalStateException`，应用启动失败并输出可定位的告警信息

#### Scenario: dev profile
- **WHEN** 网关以 `dev` profile 启动且密钥为占位值
- **THEN** 校验放行，应用正常启动

#### Scenario: 非 dev + 已覆盖密钥
- **WHEN** 网关以非 dev profile 启动且 `whale.gateway.jwt.secret` 已被环境变量覆盖为自定义值
- **THEN** 校验放行，应用正常启动

### Requirement: 安全降级告警（REQ-SEC-002/003，AUTH-05）

令牌黑名单在 Redis 不可用时的降级 SHALL 产生可检索的告警（ERROR 级固定标记 + 自增计数），且不阻断鉴权链路。

#### Scenario: 网关降级
- **WHEN** 网关 `TokenBlacklistChecker` 查询黑名单时 Redis 不可用
- **THEN** 记一条 `SECURITY_DEGRADATION` 告警（含场景标识与异常、ERROR 级）、计数 +1，并返回「未命中」放行

#### Scenario: whale-auth 降级
- **WHEN** `RedisTokenBlacklist#add` 或 `#contains` 遇到 Redis 异常
- **THEN** 同上记告警并计数 +1，`add` 仅告警不抛、`contains` 返回未命中

### Requirement: 审计读取的元审计（REQ-SEC-001，AUTH-07）

读审计接口被调用时 SHALL 产生 `operate_type=QUERY` 的元审计记录。

#### Scenario: 查询审计分页
- **WHEN** 具备 `sys:audit:log:list` 的用户调用 `GET /audit/log/page`
- **THEN** `audit_log` 新增一条 `operate_type=QUERY`、`target_type` 标识审计资源的记录（含 traceId/用户/URI/耗时）

#### Scenario: 详情与链路查询
- **WHEN** 调用 `GET /audit/log/{id}` 或 `GET /audit/log/trace/{traceId}`
- **THEN** 同样各产生一条 `QUERY` 元审计记录

> 说明：导出接口（AUTH-08）因 whale-file 未启动而**不存在**，故 `EXPORT` 元审计不在本波范围，登记为残余。

### Requirement: 审计返回体落库（REQ-SEC-001，AUTH-13）

`@AuditLog(recordResult = true)` 标注的方法 SHALL 将返回体（脱敏 + 截断后）写入 `audit_log.result_body`；未开启该属性的方法与 `recordResult` 默认值 SHALL 保持不落库。

#### Scenario: 开启记录返回体
- **WHEN** 标注 `@AuditLog(recordResult = true)` 的方法成功返回
- **THEN** `result_body` 写入经 `SensitiveDataMasker#maskJson` 脱敏且不超过上限长度的内容，且可经查询接口读出

#### Scenario: 未开启（默认）
- **WHEN** 方法仅标注 `@AuditLog(operation = "...")`
- **THEN** `result_body` 为 `NULL`（既有语义不变，回归全绿）

#### Scenario: 返回体序列化失败或超大
- **WHEN** 返回体无法序列化为 JSON，或序列化后超上限
- **THEN** 仅告警或按上限截断，**不影响**业务主流程与其余审计字段

### Requirement: 权限中心文档归置（REQ-DOC-002）

权限中心原始文档 SHALL 以副本形式归置到 `docs/whale-auth/originals/`（原文件保留原位），并 SHALL 在 `docs/whale-auth/final/` 输出一份整理后的最终版。

#### Scenario: 归置副本
- **WHEN** 执行归置
- **THEN** `originals/` 按 `design/plans/requirements/specs/reviews/devlog` 分类存放副本；原位置文件内容与路径均不变

#### Scenario: 最终版
- **WHEN** 输出最终版
- **THEN** `final/` 含一份整合文档：能力矩阵、接口与权限点清单、关键配置项、表结构、已闭环项与残余/阻塞项、原文索引

## MODIFIED Requirements

### Requirement: `audit_log` 表结构

在既有字段基础上新增 `result_body TEXT NULL COMMENT '返回体（脱敏 + 截断；recordResult=true 时写入）'`；`whale-auth` 与 `whale-audit` 两份 `db/init.sql` SHALL 保持一致。其余字段、索引与 append-only 语义（无 `update_time`/`deleted`）不变。

## REMOVED Requirements

无。

## 六、风险与残余

| # | 项 | 说明 / 处置 |
|---|---|---|
| 1 | 元审计自指膨胀 | 每次读审计都新增一行，审计表体量增长；属设计取舍（可反查读行为），已登记为已知语义 |
| 2 | `result_body` 存储放大 | 仅 `recordResult=true` 时写入且按上限截断 + 脱敏；上限为常量（非魔法值） |
| 3 | `EXPORT` 元审计 | 导出接口不存在（AUTH-08 阻塞于 whale-file），本波不做，登记残余 |
| 4 | 无 Docker / RabbitMQ 缺席 | 不阻断本波（均为本地可验证改动）；但不做实时端到端冒烟，单测 + 构建门禁为准 |
| 5 | 黑名单降级告警的「指标」形态 | 采用进程内自增计数（无 Prometheus/Micrometer 接入）；已登记偏差 |

## 七、验收口径

- `mvn -f backend/pom.xml clean verify -T 1C` → BUILD SUCCESS，全模块用例不回归。
- `mvn -f backend/pom.xml checkstyle:check` → 全 reactor 0 违规（E16 Javadoc / 行宽 ≤150 / C4 方法参数 ≤4）。
- 新增单测覆盖上述 4 个 Scenario 的成功/降级/边界分支。
- 检查报告（`docs/reviews/2026-10-18-wave5.11-auth-residual.md`）+ devlog 四节 + 台账销账 + 提交推送。

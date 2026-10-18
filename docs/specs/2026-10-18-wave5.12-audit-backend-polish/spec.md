# Wave 5.12 · 审计日志后端补强（REQ-SEC-006 残余收口）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-SEC-006（多通道日志采集）、REQ-SEC-001（审计日志）、REQ-SEC-002/003（链路与网关）、REQ-ARCH-004（命名规范）、REQ-QA-002（检查留痕）、REQ-DOC-002（文档先行） |
| 任务映射 | 承接 Wave 5.10 后端 Wave A 的「残余」项：`api_log.module` 回填、网关按路径限流、保留期扩多表 |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [Wave 5.10 spec](../2026-10-18-wave5.10-multichannel-log/spec.md) §六风险与残余；[Wave 5.10 检查报告](../../reviews/2026-10-18-wave5.10-multichannel-log.md)；[audit-log.md](../../design/audit-log.md)；[naming-conventions.md](../../naming-conventions.md) §二 |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 按 [code-review-checklist.md](../../checklists/code-review-checklist.md) 自检留报告（REQ-QA-002）→ 构建设计门禁 → 提交推送 |

> 本波**只做后端补强**；前端 `whale-log-sdk`（Wave B）另立 [Wave 5.14](../2026-10-18-wave5.14-whale-log-sdk/spec.md)。MQ 汇聚（RabbitMQ 未就绪）与「异步派发丢采集」维持登记不做。

---

## 一、需求项（本波次范围）

Wave 5.10 已交付 4 张独立表（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`）、`ApiLogFilter`、`OutboundLogInterceptor`、上报/查询接口与权限码头。本波收口三项已登记残余：

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-SEC-006 | **E1 `api_log.module` 回填**（`ApiLogRecord.module` 列已存在但 `ApiLogFilter` 未赋值） | 落库记录的 `module` 非空，值 = `spring.application.name`（可被 `whale.log.api.module` 覆盖） |
| REQ-SEC-006 | **E2 网关按路径限流**（`RateLimitGlobalFilter` 现仅全局阈值，未对上报表 `permitAll` 公开写库接口做更严约束） | 上报路径 `/audit/log/frontend/**`、`/audit/log/behavior/**` 命中独立限流规则；其余路径沿用全局阈值 |
| REQ-SEC-006 | **E3 保留期扩多表**（`whale.audit.retention.days` 原先只对 `audit_log` 有语义） | `whale.audit.retention.*` 配置生效；whale-audit 定时清理 4 张日志表的过期行；默认关闭，需显式开启 |
| REQ-QA-002 | 单测 + 检查报告 | 报告逐项对应清单编号 |

---

## 二、总体设计

### 2.1 E1 —— `api_log.module` 回填

- `ApiLogProperties`（`com.bluewhale.common.log.properties`）新增字段 `module`，默认值 `${spring.application.name:unknown}`：
  - 各服务 `spring.application.name` 已存在（`whale-auth` / `whale-audit` / …），无需额外配置即得正确模块名；
  - 保留配置覆盖能力（`whale.log.api.module`）以兼容非标准服务名。
- `ApiLogFilter#fillRequest` 增加 `record.setModule(properties.getModule())`（**唯一改动点**）。

### 2.2 E2 —— 网关按路径限流

- `RateLimitProperties`（`com.bluewhale.gateway.config`）新增 `List<PathRule> pathRules`，`PathRule{ pathPattern, limit, windowSeconds }`（默认空列表）。
- `RateLimitGlobalFilter` 执行逻辑：
  1. 用 `AntPathMatcher` 在 `pathRules` 中选取**最长匹配**的规则（特化优先）；
  2. 未命中任何规则 → 沿用全局 `limit` / `windowSeconds`；
  3. Redis 计数键加入**规则标识维度**（命中规则用其 `pathPattern`，未命中用固定 `default`），避免不同规则窗口互相污染；
  4. 阈值判定与 429 短路语义不变。
- `whale-gateway` `application.yml` 追加两条更严规则：`/audit/log/frontend/**`、`/audit/log/behavior/**`。

### 2.3 E3 —— 保留期扩多表

- 新增 `RetentionProperties`（前缀 `whale.audit.retention`）：
  - `enabled`（默认 `false`，**显式开启**才清理，避免误删）；
  - `days`（默认 `30`，保留天数）；
  - `cron`（默认每日 03:00）。
- 新增 `LogRetentionScheduler`（`com.bluewhale.audit.retention`）：
  - `@ConditionalOnProperty(name = "whale.audit.retention.enabled", havingValue = "true")`；
  - `@Scheduled(cron = ...)`，对 `api_log`/`outbound_log`/`frontend_log`/`behavior_log` 执行 `DELETE ... WHERE create_time < now - days`；
  - 删除失败仅 `warn`，不影响服务；
  - **`audit_log` 不在本波自动清理范围**（其保留/归档策略属 AUTH-09，阻塞于 whale-job）。
- `WhaleAuditApplication` 增加 `@EnableScheduling`。

---

## 三、组件与落点

| 模块 | 类/文件 | 动作 |
|---|---|---|
| `whale-spring-boot-starter` | `com/bluewhale/common/log/properties/ApiLogProperties.java` | 新增 `module` 字段（默认 `${spring.application.name:unknown}`） |
| `whale-spring-boot-starter` | `com/bluewhale/common/log/filter/ApiLogFilter.java` | `fillRequest` 设 `module` |
| `whale-spring-boot-starter` | `.../filter/ApiLogFilterTest.java` | 增 `module` 回填断言 |
| `whale-gateway` | `com/bluewhale/gateway/config/RateLimitProperties.java` | 新增 `List<PathRule> pathRules` + 内部类 `PathRule` |
| `whale-gateway` | `com/bluewhale/gateway/filter/RateLimitGlobalFilter.java` | 规则选取 + 键维度 + 判定复用 |
| `whale-gateway` | `.../filter/RateLimitGlobalFilterTest.java` | 增路径规则命中/未命中用例 |
| `whale-gateway` | `src/main/resources/application.yml` | 追加上报路径规则 |
| `whale-audit` | `com/bluewhale/audit/config/RetentionProperties.java`（新增） | 配置绑定 |
| `whale-audit` | `com/bluewhale/audit/retention/LogRetentionScheduler.java`（新增） | 定时清理 4 表 |
| `whale-audit` | `com/bluewhale/audit/WhaleAuditApplication.java` | 加 `@EnableScheduling` |
| `whale-audit` | `.../retention/LogRetentionSchedulerTest.java`（新增） | 清理逻辑单测 |

---

## 四、验收

| 项 | 验证方式 | 通过标准 |
|---|---|---|
| E1 module 回填 | `ApiLogFilterTest` | 记录 `module` 等于配置值（默认取服务名） |
| E2 路径规则 | `RateLimitGlobalFilterTest` | 命中上报路径用规则阈值；其他路径用全局阈值；超阈值 429；未启用直放行 |
| E3 保留清理 | `LogRetentionSchedulerTest` | `enabled=false` 不装配/不清理；`enabled=true` 删除阈值前记录（Mock 断言 mapper 调用） |
| 门禁 | `mvn -B -f backend/pom.xml clean verify -T 1C`、`mvn -B -f backend/pom.xml checkstyle:check` | BUILD SUCCESS；全 reactor 0 违规（E16 中文 Javadoc / 行宽 ≤150 / C4 ≤4 参 / 魔法值常量化） |

---

## 五、风险与残余

| 风险 / 残余 | 影响 | 处置 |
|---|---|---|
| 限流键维度变更 | 既有全局限流计数的 Redis 键格式变化 | 键前缀仍为 `whale:gateway:rate-limit`；新增段仅区分规则，`enabled=false` 与全局路径行为不变；单测覆盖 |
| 保留清理误删 | `enabled=true` 后按天删除 | **默认关闭**；仅 4 张日志表；`create_time` 索引已存在（Wave 5.10 DDL）；`audit_log` 不清理 |
| 异步派发丢采集 | `shouldNotFilterAsyncDispatch()=true` 仍跳过异步派发 | **本波维持不做**，登记为已知丢采集（与 Wave 5.10 一致） |
| MQ 汇聚 | 阶段二未就绪 | **本波不做**（RabbitMQ 未启动） |

---

## 六、执行顺序

```
1) 文档先行：本 spec + tasks + checklist（E1/E2/E3）
2) E1 module 回填（ApiLogProperties + ApiLogFilter）+ 单测
3) E2 网关路径限流（RateLimitProperties + Filter + yml）+ 单测
4) E3 保留期（RetentionProperties + LogRetentionScheduler + @EnableScheduling）+ 单测
5) mvn clean verify -T 1C + checkstyle:check
6) 检查报告 + devlog + 台账（register/next-steps）→ commit + push
```

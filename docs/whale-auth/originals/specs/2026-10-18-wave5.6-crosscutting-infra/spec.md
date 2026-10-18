# Wave 5.6 · 横切基础设施规格（幂等 / 缓存一致性 / 网关限流 / JaCoCo / Gitee Go）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-DIST-003（幂等、缓存一致性、限流熔断）、REQ-CICD-001（覆盖率门禁、流水线）、REQ-QA-002（代码检查报告） |
| 任务映射 | T5.3 / AUTH-18、T5.5 / AUTH-20、T5.4 / AUTH-30、T5.18 / AUTH-24、T5.16 / AUTH-28 |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [distributed.md](../../design/distributed.md) §3.1/§3.2/§3.4、[cicd.md](../../design/cicd.md) §3/§6、[next-steps.md](../../plans/next-steps.md) 组 A/组 E |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 按 [code-review-checklist.md](../../checklists/code-review-checklist.md) 自检留报告（REQ-QA-002）→ 构建验证 → 提交推送 |

---

## 一、需求项（本波次新增/推进）

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-DIST-003 | 幂等 Token 组件（T5.3）、缓存一致性工具（T5.5）、网关限流（T5.4） | 见下表逐项验收 |
| REQ-CICD-001 | JaCoCo 覆盖率接入（T5.18）、Gitee Go 流水线映射（T5.16） | 构建产出覆盖率报告；Gitee 侧可见流水线定义 |
| REQ-QA-002 | 本波次产出检查报告 | `docs/reviews/` 报告逐项对应清单条目 |

---

## 二、任务与验收

### T5.3 / AUTH-18 幂等组件

| 项 | 内容 |
|---|---|
| 内容 | `@Idempotent` 注解 + `IdempotentAspect`，基于 Redisson `RBucket.trySet`（SETNX 语义）实现「一次一消费」；首次执行成功后缓存返回体，重复提交返回同一结果；执行异常时释放 key 允许重试 |
| 落点 | `whale-spring-boot-starter` 新包 `com.bluewhale.common.idempotent`（annotation/aop/config） |
| 依赖 | Redisson（starter 已 optional 引入，沿用 `DistributedLockAutoConfiguration` 的 `@ConditionalOnClass` 模式） |
| Key 约定 | `idem:{biz}:{token}`，token 来源优先级：注解 `key` SpEL → 请求头 `X-Idempotent-Token` |
| 验收 | ① 相同 token 第二次调用返回与首次相同的业务结果，不产生重复数据；② 首次执行抛异常时释放 key，可重试；③ 重复并发（处理中）返回业务码 409；④ 单测覆盖：首次/重复命中结果、处理中拒绝、异常释放、token 缺失放行 |
| 场景接入 | `whale-auth` `UserController#create`（`@Idempotent(biz="auth:user:create", key="#request.username")`）；唯一索引兜底沿用既有 `uk_username`（若 `init.sql` 未定义则不新增，避免与 `deleted` 逻辑删除字段冲突，记为残余项） |

### T5.5 / AUTH-20 缓存一致性工具

| 项 | 内容 |
|---|---|
| 内容 | Cache-Aside 工具：`getOrLoad`（未命中回源 + 空值哨兵防穿透 + TTL 抖动防雪崩）、`evict`、`evictWithDelay`（延迟双删） |
| 落点 | `whale-spring-boot-starter` 新包 `com.bluewhale.common.cache`；`spring-boot-starter-data-redis` 以 `optional` 引入，自动配置 `@ConditionalOnClass(StringRedisTemplate.class)` |
| 空哨兵/抖动 | 空值写入哨兵串（默认 `__NULL__`）短 TTL；正常值 TTL 叠加 ±`ttlJitterPercent` 随机抖动 |
| Key 约定 | `whale:cache:{name}:{id}`（与命名规范 REQ-ARCH-004 Redis key 约定一致） |
| 验收 | ① 未命中回源并写入缓存；② 命中缓存直接返回；③ 空值写哨兵且后续读不再回源（防穿透）；④ TTL 抖动落在 ±`ttlJitterPercent` 区间（防雪崩）；⑤ `evict` 与延迟双删删除成功；⑥ Redis 异常降级回源不阻断；⑦ 单测覆盖上述路径。**击穿防护**（同 key 并发仅一次回源）按设计 §2.4 由 `@DistributedLock` 场景接入（AUTH-19）承担，不在本工具内实现 |

### T5.4 / AUTH-30 网关限流

| 项 | 内容 |
|---|---|
| 内容 | 网关入口限流：`RateLimitGlobalFilter`（Redis 计数器 + 固定窗口），超阈值短路返回 **HTTP 429 + `Result` JSON 体**；Redis 不可用降级放行并告警 |
| 落点 | `whale-gateway`（`filter/` + `config/RateLimitProperties`），复用现有 `ReactiveStringRedisTemplate` |
| 配置 | `whale.gateway.rate-limit.enabled`（默认 true）、`limit`（默认 200）、`window-seconds`（默认 1） |
| 偏差说明 | 设计 §3.4 推荐 **Sentinel**（网关 + 应用）；因本机无 Sentinel Dashboard、Boot 4 兼容性未实测，本波次按 next-steps T5.4「**或先用 Redis 计数器兜底**」落地 Redis 计数器，Sentinel 作为容器/控制台就绪后的接续项（记入 backlog） |
| 验收 | ① 超阈值返回 HTTP 429 且响应体为 `{"code":429,"message":"请求过于频繁，请稍后重试"}`；② 阈值内正常转发；③ Redis 不可用放行；④ 端到端经网关实测（本机 Redis 已就绪） |

### T5.18 / AUTH-24 JaCoCo 覆盖率接入

| 项 | 内容 |
|---|---|
| 内容 | `whale-parent` 引入 `jacoco-maven-plugin`：`prepare-agent` + `report`（默认生命周期产出报告，不阻断）；`check` 规则置于 `coverage-gate` profile（CI 以 `-Pcoverage-gate` 启用） |
| 阈值 | 行 ≥ 40%、分支 ≥ 30%（[cicd.md](../../design/cicd.md) §3.3 首轮基线） |
| 默认不阻断说明 | 现有代码含大量骨架，若默认启用 `check` 会致 `mvn clean install` 红灯，违反「提交前 BUILD SUCCESS」约定；故门禁经 profile 显式启用（CI 链路启用即阻断），本地默认仅产出报告 |
| 验收 | ① 构建产出 `target/site/jacoco/index.html` 与 `jacoco.xml`；② `-Pcoverage-gate` 下未达阈值构建失败；③ 默认 `mvn test` 仍 SUCCESS |

### T5.16 / AUTH-28 Gitee Go 流水线映射

| 项 | 内容 |
|---|---|
| 内容 | 新增 `.workflow/` 流水线定义（后端：静态检查 → 编译 → 单测 → 打包；前端：安装 → lint → 构建），与 [cicd.md](../../design/cicd.md) §6.3 对齐；`.github/workflows/` 保留为 GitHub 侧备份 |
| 验收 | ① `.workflow/` 文件落地且语法自洽；② 推送后 Gitee Go 侧可识别并触发（**待 Gitee 侧实机验证**，本机无法验证） |

---

## 三、执行顺序与依赖

```
1) T5.3 幂等（starter，无外部依赖）
2) T5.5 缓存一致性（starter，无外部依赖；与 T5.3 同模块，串行）
3) T5.4 网关限流（gateway，依赖本机 Redis）
4) T5.18 JaCoCo（whale-parent，与上述文件集不重叠，可并行）
5) T5.16 Gitee Go（仓库根 .workflow/，与上述不重叠，可并行）
6) 全量 checkstyle + 单测回归 → 检查报告 → devlog/register → 提交推送
```

---

## 四、测试计划

| 任务 | 测试方式 |
|---|---|
| T5.3 | 单测（Mockito mock RedissonClient/RBucket）：首次成功并缓存结果、重复命中缓存返回同结果、处理中 409、异常释放 key、token 缺失放行 |
| T5.5 | 单测（mock StringRedisTemplate）：命中缓存、未命中回源并写入、空值哨兵、TTL 抖动范围、evict/延迟双删、Redis 异常降级回源 |
| T5.4 | 端到端（本机 Nacos 8848 + gateway 8080 + Redis 6379）：循环请求超阈值 → HTTP 429 + Result 体；阈值内 → 200 |
| T5.18 | 构建验证：`mvn -pl :whale-auth test` 产出 jacoco 报告；`-Pcoverage-gate` 触发阈值校验 |
| T5.16 | 静态校验（YAML 语法）；Gitee 侧触发为待验证项 |

---

## 五、风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 本机无 Sentinel Dashboard | 无法按设计 §3.4 完整接入 Sentinel | 本波次用 Redis 计数器兜底（next-steps 允许），Sentinel 列 backlog |
| JaCoCo 阈值低于现状 | 默认阻断会致构建红灯 | 门禁置于 `coverage-gate` profile，CI 显式启用 |
| Gitee Go 语法与官方 schema 偏差 | 推送后可能不被识别 | 本机无法验证，标注「待 Gitee 侧验证」，保留 `.github/workflows/` 备份 |
| 幂等结果缓存序列化 | 返回体类型反序列化偏差 | 以方法返回类型 + Jackson 3 `JsonMapper` 反序列化；`void` 以哨兵处理 |

# 分布式基础设施方案设计（Distributed Infrastructure）

| 项 | 内容 |
|---|---|
| 需求编号 | REQ-DIST-001（分布式 ID）/ REQ-DIST-002（分布式锁）/ REQ-DIST-003（其他分布式问题） |
| 状态 | 设计已定稿 / 实现待启动 |
| 最后更新 | 2026-10-17 |
| 作者 | BlueWhale |
| 落点 | `backend/whale-common`（工具与 starter）、各业务服务 |

---

## 0. 现状总览

| 能力 | 现状 | 缺口 |
|---|---|---|
| 分布式 ID | `BaseEntity` 主键 `@TableId(type = IdType.ASSIGN_ID)`（MP 内置雪花）；`whale-tools` `IdUtils` 提供 `nextId()` / `nextUUID()` | 无 workerId 显式治理；无 Long→String 序列化（JS 精度） |
| 分布式锁 | `redisson-spring-boot-starter` 已引入 whale-auth（parent 管理 3.34.1） | 未使用 `RLock`，无锁工具与规范 |
| 幂等 | 无统一机制 | 写接口可重复提交 |
| 缓存一致性 | `whale-i18n-sdk` 用 `StringRedisTemplate` + TTL + 空哨兵（既有惯例） | 业务缓存无统一策略 |
| 分布式事务 | 无 | 跨服务写操作无一致性保证 |
| 限流熔断 | roadmap 标注"Sentinel 限流待做" | 网关与应用均未接入 |
| 链路追踪 | 网关 `TraceIdGlobalFilter` 已有 `X-Trace-Id` | 无调用链可视化、无 span |
| 会话 | JWT 无状态；登出黑名单为 TODO（REQ-SEC-002） | 令牌即时失效未落地 |

约定基线：端口 gateway 8080 / auth 8081 / job 8082 / workflow 8083 / form 8084 / file 8085 / pay 8086 / monitor 8087 / audit 8088；包名 `com.bluewhale.{module}`；中间件版本 MySQL 8.4 / Redis 7 / Nacos 2.3 / RabbitMQ 3。

---

## 1. REQ-DIST-001 分布式 ID

### 1.1 现状核查

- `com.bluewhale.common.entity.BaseEntity`：

```java
@TableId(type = IdType.ASSIGN_ID)
private Long id;   // 主键 ID（雪花算法）
```

`IdType.ASSIGN_ID` 使 MyBatis-Plus **默认使用内置雪花算法**（`DefaultIdentifierGenerator`，默认 `datacenterId`/`workerId` 由网卡地址与主机名派生），无需业务代码调用即可生成全局唯一、趋势递增的 `long`。

- `com.bluewhale.tools.utils.IdUtils`（已存在，**已覆盖"非实体场景"的 ID 生成**）：

| 方法 | 实现 | 适用场景 |
|---|---|---|
| `IdUtils.nextId()` | `(System.currentTimeMillis() << 20) | 20 位随机数` | 对趋势有序要求不高的场景（注释已明确非严格雪花） |
| `IdUtils.nextUUID()` | 去横线 UUID 字符串 | 业务流水号、幂等 token、TraceId 等 |

**结论**：`IdUtils.nextId()` 是"**时间戳 + 随机数简易实现**"，不是严格雪花（无 workerId 位、无同毫秒自增序列）。它**可以继续用于非主键场景**，但**实体主键不应改用它**。

### 1.2 可选方案对比

| 方案 | 全局唯一 | 趋势递增 | 依赖 | 性能 | 缺点 |
|---|---|---|---|---|---|
| 数据库自增 | 单库唯一 | 是 | 无 | 低（写放大） | 分库分表后冲突；暴露量；需额外号段服务 |
| UUID | 是 | 否 | 无 | 高 | 36 字符、无顺序、索引碎片严重；BIGINT 主键不兼容 |
| 雪花 Snowflake | 是 | 是 | 无 | 高 | 依赖机器时钟；需治理 workerId；JS 精度 |
| 号段模式（Leaf-segment） | 是 | 是 | DB/Redis | 高 | 需号段服务与双 buffer 实现 |
| Redis INCR | 是 | 是 | Redis | 高 | 强依赖 Redis 可用性；持久化丢失风险 |

### 1.3 推荐方案与理由

**推荐：实体主键沿用 MyBatis-Plus 内置雪花（`IdType.ASSIGN_ID`），不额外引入 Leaf/Redis 发号器。**

理由：

1. `BaseEntity` 已是全平台公共基类，所有实体天然覆盖，**零额外成本**；
2. 无外部依赖，符合"小体量平台优先去组件化"的取舍；
3. 与 init.sql 注释"id BIGINT（雪花算法生成）"完全一致，无需改表。

### 1.4 workerId 分配方式

| 方式 | 说明 | 适用 |
|---|---|---|
| MP 内置默认 | `datacenterId`/`workerId` 由网卡 + 主机名派生 | **当前采用**，单机/少量实例够用 |
| Nacos 配置下发 | 每个服务实例在 Nacos 配置中心读 `whale.id.worker-id` | 自研雪花、实例数 > 32 时 |
| Redis INCR 分配 | 启动时 `INCR whale:id:worker` 取模分配并绑定 | 自研雪花、容器弹性伸缩时 |

自研雪花（如需）应实现 `IdentifierGenerator` 接口并通过 `@Bean` 注入，配置项建议 `whale.id.worker-id` / `whale.id.datacenter-id`。

### 1.5 时钟回拨处理

| 场景 | 处理策略 |
|---|---|
| 小幅回拨（< 5ms） | 自旋等待至时间追平后继续 |
| 中等回拨（5ms–1s） | 使用备用 workerId/序列位补偿；仍失败则抛异常告警 |
| 大幅回拨（> 1s） | **快速失败**（抛异常，不生成 ID），避免产生重复/乱序 ID |

说明：MP 内置 `DefaultIdentifierGenerator` **不处理时钟回拨**；生产环境建议启用 NTP 校时并监控回拨告警。若确认需强时钟保障，再切换自研实现。

### 1.6 JS 精度丢失（Long → String 序列化）

雪花 ID 为 64 位 `long`，**超过 JavaScript `Number.MAX_SAFE_INTEGER`（2^53-1）**，前端直接解析会丢精度（如 `1251323419503984640` → `1251323419503984600`）。

处理方式（推荐全平台统一）：

- 全局配置 Jackson 序列化器，将 `Long` / `long` 输出为 JSON **字符串**（`ToStringSerializer`）。
- 前端（`frontend/`）类型定义将 `id` 声明为 `string`，避免 `number` 推断。
- 注意：**Spring Boot 4.0.8 已切换 Jackson 3**（包名 `tools.jackson.*`），配置类需按 Jackson 3 的 `JsonMapper`/`SimpleModule` API 编写，不能沿用 Jackson 2 的 `jackson-databind` 写法。

### 1.7 任务拆分

| 编号 | 任务 | 依赖 | 工作量 | 验收 |
|---|---|---|---|---|
| T-ID-1 | 确认所有实体继承 `BaseEntity` 或显式 `IdType.ASSIGN_ID` | 无 | S | 全模块主键为雪花 |
| T-ID-2 | 全平台 Long→String 序列化配置（Jackson 3） | 无 | M | 接口响应 `id` 为字符串 |
| T-ID-3 | `IdUtils` 补 Javadoc 明确"非严格雪花，勿用于实体主键" | 无 | S | 注释与结论一致 |
| T-ID-4 | 前端类型 `id: string` 对齐 | T-ID-2 | S | 联调无精度问题 |
| T-ID-5 | 时钟回拨监控告警项（可选） | 无 | M | 回拨触发告警 |

风险：Jackson 3 序列化配置改动面全平台，需回归验证；时钟回拨无 NTP 时存在重复 ID 风险。

---

## 2. REQ-DIST-002 分布式锁

### 2.1 现状

`whale-auth/pom.xml` 已声明 `org.redisson:redisson-spring-boot-starter`（parent 统一管理 3.34.1），但**代码中尚无 `RLock` 使用与锁工具**。Redis 7 已在 `infrastructure/docker/docker-compose.yml` 编排。

### 2.2 RLock 使用规范

| 规范项 | 要求 |
|---|---|
| Key 命名 | `lock:{biz}:{id}`（如 `lock:auth:user:register:{phone}`、`lock:job:daily-report`） |
| 加锁方式 | `lock.tryLock(waitTime, leaseTime, unit)`，**禁止无超时的 `lock()`** |
| 看门狗续期 | 仅当 `leaseTime = -1` 且未手动解锁时启用（默认 30s 续期）；显式给 `leaseTime` 时**不续期**，须评估业务耗时 |
| 可重入 | Redisson `RLock` 天然可重入；同线程重复加锁需配对解锁次数 |
| 锁粒度 | 最小资源粒度（用户级/单据级），禁止全局大锁 |
| 防误删 | `finally` 中先判断 `isLocked() && isHeldByCurrentThread()` 再 `unlock()` |
| 获取失败 | 默认快速失败抛 `BizException`；确需等待时限制 `waitTime`（建议 ≤ 3s） |
| 降级 | Redis 不可用时按业务决定"降级放行"或"快速失败"，不允许静默死锁 |

### 2.3 可复用锁工具设计（`@DistributedLock` + SpEL + AOP）

置于 `whale-common`（starter 暴露），基于 Redisson `RedissonClient`。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String prefix() default "lock";       // key 前缀
    String biz();                          // 业务标识，如 "auth:user:register"
    String key() default "";               // SpEL 表达式，如 "#phone" / "#request.userId"
    long waitTime() default 0;             // 最长等待（秒），0=快速失败
    long leaseTime() default 30;           // 持有时间（秒），-1=看门狗续期
    boolean failFast() default true;       // 获取失败时抛异常还是返回
}
```

切面要点：

- 切点：`@Around("@annotation(distributedLock)")`；
- Key 解析：`MethodBasedEvaluationContext` + SpEL 拼装 `lock:{biz}:{解析值}`；SpEL 为空时退化到方法签名（如 `ClassName#methodName:argHash`）；
- 执行：`RLock lock = redissonClient.getLock(key)` → `tryLock(waitTime, leaseTime, SECONDS)`；成功则 `proceed()`，失败按 `failFast` 抛 `BizException` 或返回；
- 释放：`finally` 内 `if (lock.isLocked() && lock.isHeldByCurrentThread()) lock.unlock()`；
- 与事务的边界：加锁应**包住整个事务**（切面 order 高于 `@Transactional`），避免"锁已释放但事务未提交"。

### 2.4 典型场景

| 场景 | Key 设计 | 说明 |
|---|---|---|
| 用户注册幂等 | `lock:auth:user:register:{phone}` | 防并发创建同名账号（叠加唯一索引 `uk_username`） |
| 定时任务防重复执行 | `lock:job:{jobCode}` | 多实例 `whale-job` 仅一实例执行 |
| 库存扣减 | `lock:stock:{skuId}` | 先锁后校验再扣减（高并发优先 DB 乐观锁，锁为兜底） |
| 缓存重建防击穿 | `lock:cache:{cacheKey}` | 未命中时仅一个线程回源 DB |
| 权限变更并发 | `lock:auth:role:{roleId}` | 分配权限时避免关联表交错写 |

### 2.5 任务拆分

| 编号 | 任务 | 依赖 | 工作量 | 验收 |
|---|---|---|---|---|
| T-LOCK-1 | `RedissonClient` 自动配置（starter 内）+ 连接参数接入 Nacos | 无 | M | 服务启动注入成功 |
| T-LOCK-2 | `@DistributedLock` 注解 + `DistributedLockAspect` | T-LOCK-1 | M | 并发用例仅一成功 |
| T-LOCK-3 | 锁 Key 命名规范文档化 + 常量类 | T-LOCK-2 | S | 命名统一 |
| T-LOCK-4 | 场景接入：注册幂等 / 定时任务 / 权限变更 | T-LOCK-2 | M | 对应场景验证通过 |
| T-LOCK-5 | Redis 不可用降级策略开关 | T-LOCK-2 | S | 降级不阻断主流程 |

风险：leaseTime 过短导致业务未完成锁已过期（并发穿透）；Redis 单点导致锁服务不可用；看门狗续期与显式 leaseTime 混用易误判。

---

## 3. REQ-DIST-003 其他分布式问题

### 3.1 接口幂等

| 方案 | 原理 | 优点 | 缺点 |
|---|---|---|---|
| Token 机制 | 提交前申请 token，服务端 `SETNX` 校验并消费 | 通用、可前端介入 | 需额外流程与存储 |
| 唯一索引 | 业务唯一键（如 `uk_username`）约束 | 强一致、零额外组件 | 仅覆盖唯一性场景 |
| 状态机 | 仅允许合法状态跃迁（如订单 待支付→已支付） | 无重复副作用 | 需业务建模 |

**推荐**：写操作三层组合——**唯一索引兜底（必须有）+ 幂等 Token（对外提交类接口）+ 状态机（有状态流转的单据）**。既有的 `IdUtils.nextUUID()` 可直接用作幂等 token。用户创建接口当前已有"先查重再插入"逻辑，应升级为**唯一索引为准**（避免并发窗口竞态）。

### 3.2 缓存一致性

| 方案 | 原理 | 一致性 | 复杂度 |
|---|---|---|---|
| Cache-Aside + 先更库后删缓存 | 更新 DB 后删除缓存 | 中（极端并发短暂脏读） | 低 |
| Cache-Aside + 延迟双删 | 更新后删 + 延迟再删（如 500ms） | 中高 | 低 |
| Canal 订阅 binlog | 劫持变更异步刷新缓存 | 高 | 高（需 Canal + MQ） |

**推荐**：**Cache-Aside + 先更库后删缓存，对高一致要求场景叠加延迟双删**；`whale-i18n-sdk` 的 `StringRedisTemplate` + 空哨兵防穿透已是既有惯例，业务缓存沿用同一套模式。Canal 作为规模化后的演进选项，当前不引入。

### 3.3 分布式事务

| 方案 | 一致性 | 侵入性 | 适用 |
|---|---|---|---|
| Seata AT | 强（最终） | 低（自动回滚） | 同库多数据源、链路短 |
| Seata TCC | 强 | 高（手写 try/confirm/cancel） | 金融级、需精确控制 |
| 消息最终一致性 | 最终 | 中（本地消息表 + MQ） | 跨服务异步、可容忍延迟 |

**推荐**：本项目体量下 **优先"本地事务 + 消息最终一致性"**（本地消息表 / 事务消息，MQ 用 RabbitMQ），**暂不引入 Seata**。理由：微服务边界清晰、跨服务强一致写场景少；引入 Seata 需额外 TC 服务与全局锁，收益低于运维成本。若出现"同请求跨服务必须同成同败"的场景，再按需评估 Seata AT。

### 3.4 限流熔断

| 方案 | 层级 | 说明 |
|---|---|---|
| Sentinel | 网关 + 应用 | 阿里生态，控制台可视化，与 Spring Cloud Alibaba 同源 |
| Resilience4j | 应用 | 轻量，网关 Sidecar 模式 |
| Gateway RequestRateLimiter | 网关 | 基于 Redis 令牌桶，仅网关限流 |

**推荐**：**Sentinel 统一接入网关与应用**（gateway 已写明"Sentinel 限流熔断"为待做；`spring-cloud-starter-alibaba-sentinel` 与当前 Alibaba 2025.1.0.0 版本一致）。网关做入口 QPS 限流与降级，应用做热点方法保护。

### 3.5 链路追踪

| 方案 | 原理 | 现状适配 |
|---|---|---|
| 沿用 TraceId | 网关生成/透传 `X-Trace-Id`，日志打印 | **已有**，零成本 |
| Micrometer Tracing + OTLP | 标准 Span，可对接 Tempo/Jaeger | 与 Boot 4 原生兼容，侵入低 |
| SkyWalking | Agent 探针自动埋点 | 功能全，但需 Agent 与 OAP 服务 |

**推荐**：**短期沿用 TraceId（已落地）**；中期引入 **Micrometer Tracing + OTLP Exporter**（Boot 4 原生、无需 Agent、可与日志 TraceId 统一）；SkyWalking 作为 APM 完整方案备选，待容器环境就绪后评估。

### 3.6 分布式会话

| 方案 | 说明 |
|---|---|
| JWT 无状态 | **现状**，网关与 auth 双层校验，无需会话存储 |
| JWT + 黑名单 | 登出即刻失效（REQ-SEC-002 待验证），key `auth:token:blacklist:{token}`，TTL=剩余有效期 |
| Session + Redis | 传统有状态会话，与 JWT 方案冲突，不采用 |

**推荐**：**JWT 无状态 + Redis 黑名单**。`AuthService#logout()` 已有 TODO 预留（key 形如 `auth:token:blacklist:{token}`），需在 `JwtAuthenticationFilter` 解析后先查黑名单；**网关侧同步校验黑名单**（REQ-SEC-003），避免跨服务漏放行。

### 3.7 任务拆分

| 编号 | 任务 | 依赖 | 工作量 | 验收 |
|---|---|---|---|---|
| T-DIST-1 | 幂等 Token 组件 + 用户创建唯一索引校验对齐 | 无 | M | 重复提交返回同一结果 |
| T-DIST-2 | 缓存策略规范 + 延迟双删工具 | 无 | S | 高一致场景验证 |
| T-DIST-3 | 本地消息表 + RabbitMQ 最终一致骨架 | 无 | L | MQ 就绪后联调 |
| T-DIST-4 | Sentinel 接入网关（限流/熔断规则） | 无 | M | 超阈值返回 429 |
| T-DIST-5 | Sentinel 接入应用（热点保护） | T-DIST-4 | M | 规则生效 |
| T-DIST-6 | 令牌黑名单落地（auth + gateway） | 无 | M | 登出后 401（REQ-SEC-002/003） |
| T-DIST-7 | Micrometer Tracing + OTLP 接入（评估后） | 无 | M | 链路可可视化 |

风险：引入组件与当前"无 Docker"环境冲突，多项需容器就绪后联调；MQ 最终一致需处理消息重复与顺序；Sentinel 规则需压测调参。

---

## 4. 总体风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 本机未安装 Docker | Redis/RabbitMQ/Sentinel 控制台均不可实跑 | 先完成编码与单测（可 mock），容器就绪后统一联调 |
| 组件引入过快 | 运维复杂度上升 | 按"能不加就不加"取舍：ID 复用 MP 雪花、事务不加 Seata |
| Jackson 3 与旧经验差异 | 序列化配置易写错 | 全平台序列化改动统一由一人沉淀并回归 |
| 锁/幂等语义不统一 | 各处实现不一致 | 收敛到 `whale-common` 唯一切面与工具 |

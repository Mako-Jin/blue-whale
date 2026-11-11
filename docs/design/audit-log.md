# 审计日志方案设计（Audit Log）

| 项 | 内容 |
|---|---|
| 需求编号 | REQ-SEC-001（关联派生需求 REQ-SEC-002 / REQ-SEC-003） |
| 状态 | 设计已定稿 / 核心能力已落地（Wave 5.1b，运行时待验证） |
| 最后更新 | 2026-10-18 |
| 作者 | BlueWhale |
| 落点模块 | `whale-backend/whale-ops/whale-audit`（服务端口 `9101`）。**审计核心代码落点：`whale-spring-boot-starter`；`whale-audit` 仅承载查询接口**（详见 §5.1） |
| 整合最终版 | [whale-audit/final/README.md](../whale-audit/final/README.md)（Wave 5.16 收尾：八节整合视图 + `docs/whale-audit/originals/` 副本索引；本文档为设计总纲，整合版以最新口径为准） |

---

## 1. 需求与范围

### 1.1 需求描述

审计日志需回答四个问题：**谁（who）、何时（when）、对什么（what）、做了什么（how）、结果如何（result）**，用于安全追溯、合规取证与问题定位。

### 1.2 范围内

- 审计事件范围定义（登录/登出、权限变更、数据增删改、敏感操作）
- 审计表结构与字段补全（ALTER 建议）
- `@AuditLog` 注解 + AOP 切面设计（切点、埋点字段、异步落库）
- 落库方式选型结论、敏感字段脱敏、TraceId 关联链路、查询接口、保留与归档
- 任务拆分与风险

### 1.3 范围外

- 登录失败风控 / 账号锁定策略——**已于 2026-11-11 由 Wave 6.22 落地**（`LoginLockService`，
  Redis 计数，login 与 step-up 验密共用）；本文档只消费其审计事件，不重复设计策略
- 审计日志可视化大屏（Phase 4 ops 平台）
- 全量 SQL 级 binlog 审计（不采用，见 §6 选型对比）

---

## 2. 审计对象分类

| 分类 | 典型事件 | 触发位置 | 优先级 | 说明 |
|---|---|---|---|---|
| 认证事件 | 登录成功/失败、登出、刷新令牌 | whale-auth `AuthController` | 高 | 登录失败必须记录（含原因） |
| 授权变更 | 用户分配角色、角色分配权限、权限点增删改 | `UserController` / `RoleController` / `PermissionController` | 高 | 权限变更属于"敏感操作"，必留痕 |
| 数据变更 | 增/删/改（用户、角色、菜单、部门、业务单据） | 各业务 Controller | 中 | 查询类默认不记（避免日志风暴） |
| 敏感操作 | 改密、重置密码、导出、导入、启停用户、批量删除 | 全平台 | 高 | 强制记录，含操作对象 |
| 组织变更 | 部门增删改、负责人变更 | `DeptController` | 中 | ABAC 数据范围依赖，需可追溯 |

---

## 3. 现状盘点

`whale-backend/whale-ops/whale-audit/src/main/resources/db/init.sql` 已存在 `audit_log` 表，**当前为纯建表、无读写代码**（roadmap Phase 2.5「audit 服务骨架填充」为待启动）。

现状关键事实：

- 表为 **append-only** 设计：注释明确"写入后不允许修改"，因此 **无 `update_time`、无 `deleted`**；`create_time` 即操作发生时间。
- 主键 `id BIGINT`（雪花算法生成），表内 **无自增**。
- 已建索引：`idx_trace_id` / `idx_user_id` / `idx_module` / `idx_create_time`。
- 库里 `method` 字段语义为「请求方法（**全限定类名.方法名**）」，**不是 HTTP 方法**——这是后续字段补全的关键差异点。
- 建议库名 `bluewhale_audit`，字符集 utf8mb4 / `utf8mb4_general_ci`，引擎 InnoDB。
- 网关已具备 `X-Trace-Id`（`TraceIdGlobalFilter`，order=-200）与 `X-User-Id` / `X-Username` / `X-User-Roles`（`JwtAuthGlobalFilter`，order=-100）注入能力，**审计所需的上下文字段已具备来源**。

---

## 4. 表结构（按真实 init.sql 字段）

### 4.1 现有字段（`audit_log`，与 init.sql 一致）

| 字段 | 类型 | 可空 | 注释（原文语义） |
|---|---|---|---|
| id | BIGINT | 否 | 主键ID（雪花算法） |
| trace_id | VARCHAR(64) | 是 | 链路追踪ID（跨服务串联） |
| user_id | BIGINT | 是 | 操作用户ID（匿名访问为 NULL） |
| username | VARCHAR(64) | 是 | 操作用户名（冗余存储，用户删除后仍可追溯） |
| module | VARCHAR(64) | 是 | 业务模块，如 whale-auth |
| operation | VARCHAR(128) | 是 | 操作描述，如 创建用户 |
| method | VARCHAR(255) | 是 | 请求方法（全限定类名.方法名） |
| request_uri | VARCHAR(255) | 是 | 请求URI |
| request_params | TEXT | 是 | 请求参数（JSON，敏感字段须脱敏后写入） |
| result_body | TEXT | 是 | 返回体（JSON，脱敏后写入；仅当注解 `recordResult()=true` 时落库，默认不记录） |
| response_code | INT | 是 | 响应状态码 |
| ip | VARCHAR(64) | 是 | 操作IP地址（兼容 IPv6） |
| user_agent | VARCHAR(512) | 是 | 用户代理 |
| cost_ms | BIGINT | 是 | 接口耗时（毫秒） |
| create_time | DATETIME | 否 | 创建时间（默认 CURRENT_TIMESTAMP，即操作发生时间） |

主键 `PRIMARY KEY (id)`；索引 `idx_trace_id` / `idx_user_id` / `idx_module` / `idx_create_time`。

### 4.2 需要补充的字段（ALTER 建议）

上述字段可表达"谁 + 何时 + 做了什么 + 耗时 + 结果码"，但**缺少**：操作类型枚举（便于聚合统计与索引）、HTTP 方法、操作对象（target）、业务成败与失败原因、多租户维度。建议 ALTER：

```sql
ALTER TABLE audit_log
    ADD COLUMN tenant_id    BIGINT       DEFAULT NULL COMMENT '租户ID（多租户扩展预留）' AFTER user_id,
    ADD COLUMN operate_type VARCHAR(32)  DEFAULT NULL COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER' AFTER operation,
    ADD COLUMN http_method  VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP 方法：GET/POST/PUT/DELETE' AFTER request_uri,
    ADD COLUMN target_type  VARCHAR(64)  DEFAULT NULL COMMENT '操作对象类型，如 sys_user / sys_role' AFTER http_method,
    ADD COLUMN target_id    VARCHAR(64)  DEFAULT NULL COMMENT '操作对象ID（批量操作为逗号分隔或 JSON 数组）' AFTER target_type,
    ADD COLUMN biz_status   TINYINT      NOT NULL DEFAULT 1 COMMENT '业务结果：1 成功 / 0 失败' AFTER response_code,
    ADD COLUMN error_msg    VARCHAR(512) DEFAULT NULL COMMENT '失败原因（异常类名 + 消息，已脱敏）' AFTER biz_status,
    ADD KEY idx_operate_type (operate_type),
    ADD KEY idx_biz_status (biz_status),
    ADD KEY idx_tenant_time (tenant_id, create_time);
```

> 字段类型与命名风格与既有表保持下划线风格 + `map-underscore-to-camel-case` 对齐；`id` 由 `IdType.ASSIGN_ID`（雪花）填充，与 init.sql 注释一致。

---

## 5. `@AuditLog` 注解 + AOP 切面设计

### 5.1 注解定义

落地于 `whale-backend/whale-common/whale-spring-boot-starter` 的 `com.bluewhale.common.audit.*`（`annotation` / `enums` / `aop` / `support` / `entity` / `mapper` / `config` 七个包）。

**落点修正理由**：原方案拟置于 `com.bluewhale.audit.annotation`（whale-audit 内提供），但 `whale-audit` 经 `spring-boot-maven-plugin:repackage` 打包为可执行 jar 后，类位于 `BOOT-INF/classes`，**不能作为库被 whale-auth 依赖**；而 `whale-spring-boot-starter` 为普通库模块且 auth 已依赖，与既有 `@DistributedLock`（annotation + aop + config 同址）模式一致。**原 `com.bluewhale.audit.annotation` 路径废弃**。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String module() default "";          // 业务模块，默认取 spring.application.name
    String operation();                  // 操作描述，如 "创建用户"，支持 SpEL 占位
    OperateType type() default OperateType.OTHER;
    String targetType() default "";      // 操作对象类型，如 sys_user
    String targetId() default "";        // SpEL 表达式取目标ID，如 "#id" / "#request.userId"
    boolean recordParams() default true; // 是否记录请求参数（脱敏后）
    boolean recordResult() default false;// 是否记录返回体（默认关闭，避免体积膨胀）
}
```

`OperateType` 为枚举，与 §4.2 `operate_type` 取值一一对应。

`recordResult()` 语义：为 `false`（默认）时**不落返回体**；为 `true` 时切面把返回体经 `SensitiveDataMasker#maskJson` 脱敏并按切面常量上限（`AuditLogAspect#MAX_RESULT_LENGTH`，当前 2000 字符）截断后写入 `result_body` 列（序列化失败仅告警、该字段留空）。因返回体体积通常远大于请求参数、且查询类返回含大量业务数据，**仅对确需取证的核心写操作显式开启**。

### 5.2 切面设计（`AuditLogAspect`）

| 项 | 设计 |
|---|---|
| 切点 | `@Around("@annotation(auditLog)")`，绑定注解实例（非 `@within`，只拦显式标注方法，避免面过宽） |
| 环绕逻辑 | 记录 `start=System.nanoTime()`；`try proceed()` → `biz_status=1`；`catch Throwable` → `biz_status=0` 且 `error_msg=简写异常`，**记录后原样抛出**（不吞异常） |
| 耗时 | `cost_ms = (System.nanoTime()-start)/1_000_000`，与表内 `cost_ms` 对齐 |
| 用户上下文 | **只取** Header `X-User-Id` / `X-Username`（网关注入）；未取到则相应字段留空。**已移除**原「回落 `SecurityContextHolder` 的 `LoginUser`」设计——starter 不引入 spring-security 硬依赖，避免无 security 的服务出现 `NoClassDefFoundError` |
| TraceId | 取 Header `X-Trace-Id`；缺失则按网关同规格生成 32 位无横线 UUID；**不回落 MDC**（全仓无 MDC 使用） |
| IP | 优先 `X-Forwarded-For` 首段 → `X-Real-IP` → `request.getRemoteAddr()`，兼容 IPv6 |
| URI/方法 | `request_uri = request.getRequestURI()`；`http_method = request.getMethod()`；`method = declaringTypeName#name`（如 `com.bluewhale.auth.controller.AuthController#login`，**非** `MethodSignature#toLongString()`） |
| 参数 | `request_params` 取方法参数 JSON（经脱敏 + 截断，默认上限 4KB），`recordParams=false` 时不写 |
| targetId | 通过 `MethodBasedEvaluationContext` + `SpelExpressionParser` 求值；空串则忽略。要求目标参数对象具备 getter（Lombok `@Data` 满足），否则求值异常**降级为不记录 targetId** |

**已实现要点（Wave 5.1b）**：

- 切面 `@Order(Ordered.LOWEST_PRECEDENCE)`，`@Around("@annotation(auditLog)")` 绑定注解实例，只拦显式标注方法。
- 落库时序双路径：`TransactionSynchronizationManager.isSynchronizationActive()` 为真时注册 `afterCommit` 回调，为假时直接投递。
- 线程池 `auditLogExecutor`：core=1 / max=2 / queue=256 / `CallerRunsPolicy` / 线程名前缀 `audit-log-`（队列满时由调用线程兜底执行，不丢记录）。
- 组装/落库失败仅 `log.warn`；业务异常**原样抛出**（审计失败不影响业务主流程）。

### 5.3 埋点字段映射表

| audit_log 字段 | 采集来源 |
|---|---|
| id | MP `IdType.ASSIGN_ID` 雪花 |
| trace_id | 网关 `X-Trace-Id`（无 MDC 回落；缺失生成 32 位 UUID） |
| user_id / username | 网关 `X-User-Id` / `X-Username`（无 `LoginUser` 回落） |
| module | 注解 `module()`，默认 `spring.application.name` |
| operation / operate_type | 注解 `operation()` / `type()` |
| method | `declaringTypeName#name` |
| request_uri / http_method | `HttpServletRequest` |
| request_params | 参数 JSON（脱敏后） |
| result_body | 返回体 JSON（`maskJson` 脱敏 + 2000 截断，仅 `recordResult=true`） |
| response_code | `Result.code` 或 HTTP 状态码 |
| biz_status / error_msg | 切面 try/catch 结果 |
| target_type / target_id | 注解 + SpEL |
| ip / user_agent | 请求头 / 请求对象 |
| cost_ms / create_time | 环绕计时 / 落库时间 |

### 5.4 异步与事务边界

- 落库走**独立线程池**（`auditLogExecutor`，有界队列 + `CallerRunsPolicy` 兜底），切面内主流程零阻塞。
- **事务一致性**：审计记录必须在业务事务提交后写入（否则会出现"审计到不存在的数据"）。方案：切面内注册 `TransactionSynchronizationManager.registerSynchronization(afterCommit)`，无事务时直接异步提交。
- 线程池拒绝/异常时**只告警不影响主流程**（审计不可成为业务可用性的单点）。
- **自动装配条件（Wave 5.1e 修正，必须遵守）**：切面的自动配置**不得**用 `@ConditionalOnBean(AuditLogMapper.class)` 判 Mapper。理由：`@ConditionalOnBean` 是**条件判定**，只在配置类解析期（`ConfigurationClassPostProcessor`）求值，**早于** `@MapperScan`（`MapperScannerConfigurer`）注册 Mapper Bean 的时点 → 条件**恒不成立**、`AuditLogAspect` 永不装配（实证：真实服务 `audit_log` 落库 0 行，Wave 5.1d 发现）。正确做法：用「静态可判定」条件控制装配 —— `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass({AuditLogMapper.class, Aspect.class, ObjectMapper.class})`；并在 `@Bean` 方法（**Bean 实例化期**，晚于全部 `BeanFactoryPostProcessor`，Mapper 定义已就绪）内通过 `ObjectProvider<AuditLogMapper>` **延迟解析** Mapper，解析不到则不装配（返回 `null`）。

---

## 6. 落库方式选型对比

| 维度 | 方案A 本地表直接写（异步线程池） | 方案B RabbitMQ 异步汇聚 | 方案C 日志采集（Filebeat + ELK/Loki） |
|---|---|---|---|
| 实现成本 | 低（注解 + AOP + Mapper） | 中（生产者 + 消费者 + 死信 + 幂等） | 中高（日志规范 + 采集链路 + 存储） |
| 对主流程影响 | 极小（异步） | 极小（异步，但依赖 MQ 可用性） | 小（仅打日志） |
| 可靠性 | 中（应用宕机丢内存队列数据） | 高（持久化队列 + 死信重试） | 中（日志落盘后采集） |
| 数据归属 | 与业务服务同库或独立 audit 库 | 集中落 `bluewhale_audit` | 集中在日志平台 |
| 查询能力 | 强（SQL 条件分页） | 强（SQL） | 弱（需 ES 检索） |
| 中间件依赖 | 无 | RabbitMQ（**本机未装 Docker，暂不可实跑**） | ES/Loki（**未装容器**） |
| 结论 | **阶段一采用** | **阶段二目标形态** | 暂不采用 |

**结论与理由**：

1. **阶段一（当前）**：采用「`@AuditLog` + AOP + 异步线程池 + 本地异步直写」。理由：审计服务当前仅有 DDL、无代码骨架；本机未安装 Docker，RabbitMQ/ES 均不可实跑；本地直写可在无中间件前提下先跑通链路与验证。
   落库归属建议：**各服务写各自实例下的 `bluewhale_audit` 逻辑库**（微服务 DB 自治），由 whale-audit 提供**统一查询接口**（跨库查询交给 ops 平台聚合）。
2. **阶段二（演进）**：当审计量与跨服务取证需求上升，切换为「`@AuditLog` → RabbitMQ → whale-audit 消费落 `bluewhale_audit`」。理由：集中存储、支持重试与死信，符合"审计是运维域能力"的定位。`spring-boot-starter-amqp` 已在 `whale-parent` 统一管理（当前无模块实际引入），落地时按需加入 whale-audit 与各业务服务。
3. **方案C 不采用**：审计需要强结构化查询与精确字段（who/what/result），日志平台以全文检索为主，字段精度与合规取证能力弱。

> 说明：方案A 使用 `whale.audit.sink=local`；方案B 的 MQ 汇聚使用 `whale.audit.mq.publish-enabled`，
> HTTP 转发使用 `whale.audit.forward.enabled`。
> 历史开关 `whale.audit.sink=mq` 已废弃，配置后自动回落 `local` 并告警。

### 6.1 RabbitMQ vs Kafka 再评估（2026-10-23）

- **历史事实**：上表只做了「本地直写 / RabbitMQ 汇聚 / 日志采集」三方案对比，**未做过 RabbitMQ 与 Kafka 的横向选型**；RabbitMQ 的引入依据是「需要持久化队列 + 死信重试 + 运维域集中落库」，Kafka 从未进入对比。
- **现状变化**：P1-3 已落地 `AuditLogForwarder`（HTTP 批量转发到 whale-audit `/audit/log/ingest`），跨库可见性问题已解决，AUTH-10 由「必需」降为「可选优化」。
- **若确需引入消息队列**（审计量上升或要求削峰/重放）：

| 维度 | RabbitMQ | Kafka |
|---|---|---|
| 模型 | 队列（消费即删除） | 分区日志（可回放、可多订阅） |
| 吞吐 | 万级/秒，足够审计汇聚 | 十万级+/秒 |
| 路由能力 | exchange/routing key、死信、延迟队列 | 依赖分区与消费组，延迟/死信需自行实现 |
| 运维成本 | 轻（单节点即可） | 重（KRaft/ZK、分区规划、磁盘与保留策略） |
| 适用结论 | **本项目审计汇聚首选** | 仅在需要大流量流式处理或长期回放时考虑 |

- **决策**：当前保持 `AuditLogForwarder` 为默认通道；若后续引入 MQ，选 RabbitMQ，并在同一 PR 内补生产者/消费者幂等与死信重放验证。

### 6.2 AUTH-10 落地记录（2026-10-23）

- **通道顺序**：`AuditLogAspect` 落库改为 **MQ → HTTP 转发 → 本地 insert** 三级降级；MQ 与 HTTP 均对外只返回布尔值，失败不抛异常，保证审计不阻断主业务。
- **拓扑与开关**：交换机 `bluewhale.audit.exchange`（direct）/ 队列 `bluewhale.audit.queue` / 死信 `bluewhale.audit.dlx` + `bluewhale.audit.dlq`，全部 durable；生产者 `whale.audit.mq.publish-enabled`、消费者 `whale.audit.mq.consume-enabled` 默认 `false`，按需开启。
- **幂等**：生产者消息设 `messageId`（32 位 UUID）；消费者以 `audit_mq_message(message_id PK)` 去重，去重行与 `audit_log` 落库同事务，冲突即跳过（RabbitMQ 至少一次语义下的必要保障）。
- **死信**：业务队列声明 `x-dead-letter-exchange` / `x-dead-letter-routing-key`，监听器 `default-requeue-rejected=false` + 3 次指数退避重试；非法 JSON / 缺失 `messageId` 直接拒绝且不重新入队。
- **验证**：单测覆盖幂等冲突、毒消息拒绝、正常落库与发布失败降级；实机（RabbitMQ 4.4.0）经网关登录 1 次 → `bluewhale_audit.audit_log` +1、`audit_mq_message` +1，且 `bluewhale_auth.audit_log` 无新增，证明 MQ 通道生效。详见 [devlog 2026-10-23 §十五](../devlog/2026-10-23.md)。

---

## 7. 敏感字段脱敏策略

| 类别 | 字段示例 | 脱敏规则 | 落库值 |
|---|---|---|---|
| 密码 | password / oldPassword / newPassword | 命中字段名一律置 `******` | 永不落明文/密文 |
| 手机号 | phone / mobile | 保留前 3 后 4，中间 `****` | `138****8888` |
| 身份证 | idCard / idNo | 保留前 6 后 4 | `110101********1234` |
| 银行卡 | bankCard / cardNo | 保留后 4 | `**** **** **** 1234` |
| 令牌 | Authorization / token / refreshToken | 整体替换为 `***` | 不落库 |
| 邮箱 | email | 名称首字符保留 + 域名保留 | `a***@bluewhale.com` |
| 其他 | 自定义 `@Sensitive` 标注字段 | 按注解策略 | 按策略 |

实现方式：

- **递归脱敏**：`request_params` 先序列化为 JSON 树，按 key 名（忽略大小写）匹配规则表递归替换，再落库；覆盖嵌套对象与数组。
- **注解兜底**：对 DTO/VO 字段加 `@Sensitive(type=...)`，序列化器优先读注解，其次读 key 规则。
- **永不记录**：`recordParams=false` 的接口（如改密）直接跳过参数落库。
- 脱敏规则集中配置（`whale.audit.sensitive.keys`），便于扩展与审计规则调整。

**实施状态（Wave 5.1b）**：已落地 `SensitiveDataMasker`——纯 Java 反射递归 + 一次 `ObjectMapper.writeValueAsString`（不触碰 Jackson 树模型 API），递归深度上限 8，参数 JSON 超 4096 字符截断；规则按 key 归一化（转小写、去 `_` / `-`）匹配，覆盖密码 / 令牌 / 手机号 / 身份证 / 银行卡 / 邮箱。`@Sensitive` 注解兜底与脱敏规则配置化（`whale.audit.sensitive.keys`）**未实现**（登记待做）。

---

## 8. TraceId 与网关 X-User-Id 关联链路

```
客户端请求
  │
  ▼
whale-gateway
  ├─ TraceIdGlobalFilter(order=-200)：读入站 X-Trace-Id，缺失则生成 32 位 UUID；
  │                                      写入下游请求头 + 响应头
  └─ JwtAuthGlobalFilter(order=-100)：解析 JWT → 注入 X-User-Id / X-Username / X-User-Roles
        （先移除外部伪造同名头）
  │
  ▼
业务服务（如 whale-auth）
  ├─ 认证/鉴权上下文：JwtAuthenticationFilter → SecurityContext(LoginUser)
  ├─ AuditLogAspect：
  │    user_id   ← X-User-Id（缺失回落 LoginUser.userId）
  │    username  ← X-Username（缺失回落 LoginUser.username）
  │    trace_id  ← X-Trace-Id（缺失回落 MDC）
  └─ 异步落库 audit_log
```

**链路价值**：一次用户请求经网关 -> N 个微服务，所有服务以同一 `trace_id` 写入各自 `audit_log`；排查时以 `trace_id` 聚合即可还原完整操作轨迹。`user_id` 与 `trace_id` 双维度（索引已建）支持"某人做了什么"与"某次请求发生了什么"两类查询。

---

## 9. 查询接口设计

统一返回 `Result<T>` / `PageResult<T>`；路径经网关 `Path=/audit/**` + `StripPrefix=1` 路由（配置已就绪）。

| 方法 | 路径 | 说明 | 入参 |
|---|---|---|---|
| GET | `/audit/log/page` | 分页条件查询 | `current` / `size`（`PageQuery`）、`userId`、`username`、`module`、`operateType`、`bizStatus`、`traceId`、`startTime`、`endTime` |
| GET | `/audit/log/{id}` | 单条详情 | 路径 `id` |
| GET | `/audit/log/trace/{traceId}` | 按链路聚合 | 路径 `traceId` |
| POST | `/audit/log/export` | 导出（敏感操作，自身也需审计） | 条件同分页 |

设计要点：

- 时间范围查询走 `idx_create_time`；仅 `traceId` 命中 `idx_trace_id`；避免无索引组合导致全表扫描。
- 分页上限（如 `size<=200`）保护；导出走异步任务 + 文件服务（whale-file 8085）。
- 查询本身属于敏感操作，**读审计也要被审计**：3 个查询接口已各标注 `@AuditLog(type=QUERY)`，产生 QUERY 元审计并落同库 `audit_log`（见下方「元审计」）。

**实施状态（2026-10-17，Wave 5.1b）**：`GET /audit/log/page`、`GET /audit/log/{id}`、`GET /audit/log/trace/{traceId}` **已实现**（`AuditLogController`，服务内前缀 `/audit/log`；分页按 `create_time` 倒序、`size` 收敛到 `[1, 200]`、`getDetail` 为空抛 `BizException(NOT_FOUND)`）；`@RequirePermission("sys:audit:log:list")` **已实现**。

**元审计（读审计的审计，AUTH-07，2026-10-18）**：3 个查询方法已各标注
`@AuditLog(operation=…, type=OperateType.QUERY, targetType="audit_log", recordResult=false)`，
`operation` 分别为「分页查询审计日志」/「查看审计日志详情」/「按链路查询审计日志」；`operate_type` 用枚举 `QUERY`，`target_type` 统一 `audit_log`（控制器内提为常量 `TARGET_TYPE`）。查询本身即敏感操作，故其调用会经 `AuditLogAspect` 异步落一条 QUERY 记录到**同库** `audit_log`，可经 `GET /audit/log/page`（`operateType=QUERY`）反查——元审计数据自证可溯。字段映射遵循 §5.3：`method=declaringTypeName#name`、`request_uri=/audit/log/…`、`request_params` 为查询条件（脱敏后）、`biz_status`/`cost_ms` 由切面采集；`recordResult=false` 故元审计不落 `result_body`（避免查询结果体积膨胀）。

**残余（EXPORT 元审计未落地）**：`POST /audit/log/export` 及其 `@AuditLog(type=EXPORT)` 元审计**未落地**——导出接口本身尚不存在，AUTH-08 阻塞于 whale-file（8085）未启动；待文件服务就绪后与导出接口一并补齐（见 §12 遗留）。

**多通道查询接口（Wave 5.10 起，Wave 5.15 补齐通道①②）**：`audit_log` 之外的四张独立表（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`，见多通道完善方案）各自提供分页查询端点，与本 §9 的 `audit_log` 查询**并行、互不污染**：
| 方法 | 路径 | 覆盖通道 | 权限 |
|---|---|---|---|
| GET | `/audit/log/api/page` | ①后端接口日志 | `@RequirePermission("sys:audit:log:list")` |
| GET | `/audit/log/outbound/page` | ②后端出站三方调用日志 | `@RequirePermission("sys:audit:log:list")` |
| GET | `/audit/log/frontend/page` | ③前端开发者日志 | `@RequirePermission("sys:audit:log:list")` |
| GET | `/audit/log/behavior/page` | ④前端用户行为埋点 | `@RequirePermission("sys:audit:log:list")` |

**实施状态（Wave 5.15，2026-10-18）**：通道③④查询于 Wave 5.10 随 `LogIngestController` 落地；通道①②查询于 Wave 5.15 补齐（`ApiLogService`/`OutboundLogService` + `ApiLogQueryRequest`/`OutboundLogQueryRequest` + `ApiLogVO`/`OutboundLogVO`，`size` 收敛 `[1, 200]`、`create_time` 倒序、等值条件带「是否提供」开关、时间 `ge/le`、命中既有索引），四通道「写—查」对称达成。门禁：`-pl :whale-audit -am test` 46 用例全绿、`checkstyle:check` 28 模块 0 违规。详见 [reviews/2026-10-18-wave5.15-audit-channel-query.md](../reviews/2026-10-18-wave5.15-audit-channel-query.md)、[specs/2026-10-18-wave5.15-audit-channel-query/spec.md](../specs/2026-10-18-wave5.15-audit-channel-query/spec.md)。

**运行时验证（2026-10-17，Wave 5.1e）**：审计**落库已实机验证**。修复自动配置装配缺陷（见 §5.4「自动装配条件」）后，真实启动 `whale-auth`（8081）跑通 login/logout，`bluewhale_auth.audit_log` 落库 **≥2 行**（LOGIN/LOGOUT，`biz_status=1`，`request_params` 无明文凭据）；`whale-audit`（9101，数据源**临时指向 `bluewhale_auth`**）`GET /audit/log/page` **可见**这些记录。**跨库可见性约束**：阶段一各服务写各自实例下的审计表，故**查询服务必须与写入服务同库/同实例**（或提供合并查询），否则查不到数据；阶段二 MQ 汇聚（§6 方案B / T-AUD-9）落地后消除该约束。详见 [reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md](../reviews/2026-10-17-wave5.1e-audit-aspect-wiring-fix.md)。

**待确认项（网关路由前缀冲突）**：网关既有路由 `Path=/audit/**` + `StripPrefix=1` 会把 `/audit/log/page` 剥成 `/log/page`，与控制器前缀 `/audit/log` 不一致。该问题属既有配置，本批未改，待中间件就绪后联调定夺。

---

## 10. 保留与归档策略

| 阶段 | 时间窗 | 存放 | 动作 |
|---|---|---|---|
| 热数据 | 0–90 天 | `audit_log` 在线表 | 正常查询 |
| 温数据 | 90 天–1 年 | `audit_log_archive_yyyyMM` / 独立表 | 定时任务搬迁后删除原表数据 |
| 冷数据 | > 1 年 | 对象存储 / 归档库 | 导出压缩包长期留存，按需恢复 |

- 按月 **RANGE 分区**（`PARTITION BY RANGE (TO_DAYS(create_time))`）为推荐演进方向；当前表未分区，先以定时任务清理，后续再评估分区改造。
- 保留期可配置（`whale.audit.retention.days=90`），到期整批删除，符合 append-only 无逻辑删除位的既有设计。
- 归档/清理动作本身记入审计（元审计）。

---

## 11. 风险与任务拆分

### 11.1 风险

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 审计写入成为业务单点 | 审计异常拖垮主流程 | 独立线程池 + 有界队列 + 失败仅告警 |
| 记录时机早于事务提交 | 记到不存在的数据 | afterCommit 回调写库 |
| 日志体积膨胀 | 存储/查询成本高 | 查询类默认不记 + 参数截断 + 保留期清理 |
| 脱敏遗漏 | 敏感信息泄露 | 递归脱敏 + 注解兜底 + 单测覆盖规则表 |
| 本机未装 Docker | 无法实跑联调 | 先跑通编译 + 单测，容器就绪后再联调 |
| 跨库查询性能 | 多服务审计分散 | 阶段二 MQ 汇聚到 `bluewhale_audit` |

### 11.2 任务拆分

| 编号 | 任务 | 依赖 | 工作量 | 验收 |
|---|---|---|---|---|
| T-AUD-1 | `audit_log` 表 ALTER 脚本（§4.2）落地到 init.sql ✅ 已落地（Wave 5.1b） | 无 | S | 字段与实体一致 |
| T-AUD-2 | `AuditLog` 实体 + `AuditLogMapper` + `AuditLogVO` ✅ 已落地（Wave 5.1b） | T-AUD-1 | S | 编译通过 |
| T-AUD-3 | `@AuditLog` 注解 + `OperateType` 枚举 ✅ 已落地（Wave 5.1b） | 无 | S | 注解可被 AOP 绑定 |
| T-AUD-4 | `AuditLogAspect` + 上下文采集 + afterCommit 异步落库 ✅ 已落地（Wave 5.1b） | T-AUD-3 | M | 单测覆盖成功/失败两路径 |
| T-AUD-5 | 脱敏组件（规则表 + 递归脱敏） ✅ 已落地（Wave 5.1b） | 无 | M | 密码/手机号/身份证/银行卡用例通过 |
| T-AUD-6 | whale-audit 查询接口（分页/详情/按 traceId） ✅ 已落地（Wave 5.1b） | T-AUD-2 | M | 接口返回 `PageResult` |
| T-AUD-7 | 业务服务接入：auth 登录/登出/权限变更打点 ✅ 已落地（Wave 5.1b） | T-AUD-4 | M | 登录、分配角色产生审计记录 |
| T-AUD-8 | 保留与归档定时任务 ⚠️ 未落地 | T-AUD-2 | M | 过期数据按策略清理 |
| T-AUD-9 | 阶段二 MQ 汇聚（whale-audit 消费） ✅ 已落地（AUTH-10，2026-10-23） | T-AUD-4 | L | 幂等去重 + 死信拓扑 + 实机登录链路验证 |
| T-AUD-10 | 读审计的元审计（3 查询接口 `@AuditLog(type=QUERY)`） ✅ 已落地（AUTH-07，2026-10-18） | T-AUD-6 | S | 查询调用产生 QUERY 记录，可经 `/audit/log/page` 反查 |

> 工作量相对量级：S 小 / M 中 / L 大，不含具体工期。

---

## 12. 实施记录（2026-10-17，Wave 5.1b）

本批把 T-AUD-1 ~ T-AUD-7 落地为可运行代码（核心下沉 `whale-spring-boot-starter`，查询接口留在 `whale-audit`）。

| 变更点 | 文件 | 说明 |
|---|---|---|
| 枚举 | `whale-backend/whale-common/whale-spring-boot-starter/.../common/audit/enums/OperateType.java` | LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER |
| 注解 | `.../common/audit/annotation/AuditLog.java` | `module()`（空则取 `spring.application.name`）/`operation()`/`type()`/`targetType()`/`targetId()`（SpEL）/`recordParams()=true`/`recordResult()=false` |
| 实体 | `.../common/audit/entity/AuditLogRecord.java` | `@TableName("audit_log")`，21 字段，`@TableId(type = IdType.ASSIGN_ID)`；**不继承 `BaseEntity`**（append-only，无 `update_time`/`deleted`） |
| Mapper | `.../common/audit/mapper/AuditLogMapper.java` | `extends BaseMapper`，被 `@MapperScan("com.bluewhale.**.mapper")` 扫描 |
| 脱敏器 | `.../common/audit/support/SensitiveDataMasker.java` | 递归脱敏（深度 8）+ 参数 JSON 4096 截断 + key 归一化匹配规则 |
| 切面 | `.../common/audit/aop/AuditLogAspect.java` | `@Order(LOWEST_PRECEDENCE)`；采集 module/method(`declaringTypeName#name`)/uri/httpMethod/targetType/targetId/requestParams/responseCode/bizStatus/errorMsg/ip/userAgent/costMs/createTime；用户上下文只取 `X-User-Id`/`X-Username`，traceId 取 `X-Trace-Id`（无 MDC 回落） |
| 自动配置 | `.../common/audit/config/AuditLogAutoConfiguration.java` | `@AutoConfiguration(after = MybatisPlusConfig.class)` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass`；**Wave 5.1e 修正**：去除 `@ConditionalOnBean(AuditLogMapper.class)`（时序不可靠），`auditLogAspect(...)` 改注入 `ObjectProvider<AuditLogMapper>` 在实例化期延迟解析（见 §5.4「自动装配条件」）；Bean：`auditLogExecutor`（core=1/max=2/queue=256/`CallerRunsPolicy`/前缀 `audit-log-`）、`sensitiveDataMasker`、`auditLogAspect` |
| 单测 | `.../src/test/.../common/audit/aop/AuditLogAspectTest.java`、`.../support/SensitiveDataMaskerTest.java` | 覆盖切面成功/失败/落库路径与脱敏规则 |
| 自动配置注册 | `.../src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 追加 `com.bluewhale.common.audit.config.AuditLogAutoConfiguration` |
| starter pom | `whale-backend/whale-common/whale-spring-boot-starter/pom.xml` | 保持普通库模块（不 repackage），可被业务服务依赖 |
| 查询 DTO | `whale-backend/whale-ops/whale-audit/.../audit/dto/AuditLogQueryRequest.java` | `extends PageQuery` + userId/username/module/operateType/bizStatus/traceId/startTime/endTime |
| 查询 VO | `.../audit/vo/AuditLogVO.java` | 与 `AuditLogRecord` 字段一一对应 |
| 查询服务 | `.../audit/service/AuditLogService.java` + `.../service/impl/AuditLogServiceImpl.java` | 分页（`MAX_PAGE_SIZE=200`、`orderByDesc(create_time)`）、`getDetail`（空则抛 `BizException(ErrorCode.NOT_FOUND)`）、`listByTraceId` |
| 查询控制器 | `.../audit/controller/AuditLogController.java` | `@RequestMapping("/audit/log")`：`GET /page`、`GET /{id}`、`GET /trace/{traceId}` |
| 查询单测 | `.../src/test/.../audit/service/impl/AuditLogServiceImplTest.java` | 覆盖分页/详情/按 traceId |
| 服务构建 | `whale-backend/whale-ops/whale-audit/pom.xml`、`src/main/resources/application.yml` | pom 新增 `whale-spring-boot-starter`；yml 新增 `spring.datasource`（逻辑库 `bluewhale_audit`） |
| 埋点（auth） | `whale-backend/whale-services/whale-auth/.../controller/AuthController.java` | `#login`（LOGIN，`recordParams=false`）/`#logout`（LOGOUT，`recordParams=false`） |
| 埋点（auth） | `.../controller/UserController.java` | `#create`（CREATE，targetType `sys_user`）/`#assignRoles`（GRANT，`targetId="#id"`） |
| 埋点（auth） | `.../controller/RoleController.java` | `#assignPermissions`（GRANT，`targetId="#id"`） |
| 建表（auth 侧） | `whale-backend/whale-services/whale-auth/src/main/resources/db/init.sql` | 追加第 9 节 `audit_log` 建表（与 whale-audit DDL 一致，含 7 索引；阶段一各服务写各自实例下的审计表，DDL 以本设计文档与 whale-audit init.sql 为准，append-only 故不设 `update_time`/`deleted`） |

**验证证据**：

- 全量编译：`mvn -B clean compile -T 1C` → BUILD SUCCESS。
- 单测：`mvn -B -pl whale-common/whale-spring-boot-starter,whale-ops/whale-audit,whale-services/whale-auth -am test` → starter 15 / auth 19（不回归）/ audit 6，全绿（合计 40）。
- 静态检查：上述三模块 `checkstyle:check` → 0 violations。
- **Wave 5.1e 实机验证（2026-10-17）**：修复自动配置装配缺陷后，starter 单测 `Tests run: 15, Failures: 0, Errors: 0`；`whale-auth` 真实启动（`Started in 9.64 seconds`）+ login/logout `code=0`；`bluewhale_auth.audit_log` 落库 **6 行**（LOGIN/LOGOUT，`biz_status=1`，`request_params` 无凭据）；`whale-audit`（数据源临时指向 `bluewhale_auth`）`/audit/log/page` 可见记录；`LiveAuditLogIT` `Tests run: 3, Failures: 0, Errors: 0`。

**遗留 / 待做**：T-AUD-8 保留归档、T-AUD-9 MQ 汇聚（阶段二）、`POST /audit/log/export`（含 EXPORT 元审计，AUTH-08 阻塞于 whale-file）、`@Sensitive` 注解兜底、脱敏规则配置化、网关路由前缀冲突确认、**阶段一跨库可见性约束**（查询服务须与写入服务同库/同实例，见 §9 运行时验证）。~~whale-audit 侧 `@RequirePermission`、读审计的元审计~~ 已补齐（AUTH-06 / AUTH-07）。**运行时/端到端验证已完成（Wave 5.1e：落库 + 查询可见）**。

> 变更留痕：检查报告见 [docs/reviews/2026-10-17-wave5.1b-audit-log.md](../reviews/2026-10-17-wave5.1b-audit-log.md)，任务台账见 [docs/plans/task-breakdown.md](../plans/task-breakdown.md) 与 [docs/plans/next-steps.md](../plans/next-steps.md)。

---

## 13. 实施记录（2026-10-18，AUTH-07 元审计 + `result_body`）

本批为「读审计的元审计」补齐查询接口埋点，并补充返回体落库列（T-AUD-10）。

| 变更点 | 文件 | 说明 |
|---|---|---|
| 元审计埋点 | `whale-backend/whale-ops/whale-audit/.../audit/controller/AuditLogController.java` | 3 个查询方法各加 `@AuditLog(type=QUERY, targetType="audit_log", recordResult=false)`；`operation` 为「分页查询审计日志」/「查看审计日志详情」/「按链路查询审计日志」；`TARGET_TYPE` 提为常量；REST 路径 / 返回类型 / `@RequirePermission` 语义不变 |
| 元审计单测 | `.../src/test/.../audit/controller/AuditLogControllerTest.java` | 反射断言 3 个方法的 `@AuditLog` 存在、`type()=QUERY`、`operation()` 非空、`targetType()=audit_log` |
| 返回体列 | `audit_log.result_body`（DDL） | 见 §4.1；仅 `recordResult()=true` 时落库 |
| 返回体语义 | `recordResult()` / `SensitiveDataMasker#maskJson` | 见 §5.1 / §5.3 |

**残余**：`POST /audit/log/export` 与 EXPORT 元审计未落地（AUTH-08 阻塞于 whale-file 未启动）。

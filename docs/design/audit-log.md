# 审计日志方案设计（Audit Log）

| 项 | 内容 |
|---|---|
| 需求编号 | REQ-SEC-001（关联派生需求 REQ-SEC-002 / REQ-SEC-003） |
| 状态 | 设计已定稿 / 核心能力已落地（Wave 5.1b，运行时待验证） |
| 最后更新 | 2026-10-17 |
| 作者 | BlueWhale |
| 落点模块 | `backend/whale-ops/whale-audit`（端口 8088）。**审计核心代码落点：`whale-spring-boot-starter`；`whale-audit` 仅承载查询接口**（详见 §5.1） |

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

- 登录失败风控 / 账号锁定策略（另立需求）
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

`backend/whale-ops/whale-audit/src/main/resources/db/init.sql` 已存在 `audit_log` 表，**当前为纯建表、无读写代码**（roadmap Phase 2.5「audit 服务骨架填充」为待启动）。

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

落地于 `backend/whale-common/whale-spring-boot-starter` 的 `com.bluewhale.common.audit.*`（`annotation` / `enums` / `aop` / `support` / `entity` / `mapper` / `config` 七个包）。

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
| response_code | `Result.code` 或 HTTP 状态码 |
| biz_status / error_msg | 切面 try/catch 结果 |
| target_type / target_id | 注解 + SpEL |
| ip / user_agent | 请求头 / 请求对象 |
| cost_ms / create_time | 环绕计时 / 落库时间 |

### 5.4 异步与事务边界

- 落库走**独立线程池**（`auditLogExecutor`，有界队列 + `CallerRunsPolicy` 兜底），切面内主流程零阻塞。
- **事务一致性**：审计记录必须在业务事务提交后写入（否则会出现"审计到不存在的数据"）。方案：切面内注册 `TransactionSynchronizationManager.registerSynchronization(afterCommit)`，无事务时直接异步提交。
- 线程池拒绝/异常时**只告警不影响主流程**（审计不可成为业务可用性的单点）。

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

> 说明：方案A/B 需通过配置开关（`whale.audit.sink=local\|mq`）切换，保证降级路径可回退。

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
- 查询本身属于敏感操作，**读审计也要被审计**（`@AuditLog(type=EXPORT/QUERY)`）。
- 权限：`@RequirePermission("sys:audit:log:list")`（复用既有注解 + 拦截器），仅审计管理员可见。

**实施状态（2026-10-17，Wave 5.1b）**：`GET /audit/log/page`、`GET /audit/log/{id}`、`GET /audit/log/trace/{traceId}` **已实现**（`AuditLogController`，服务内前缀 `/audit/log`；分页按 `create_time` 倒序、`size` 收敛到 `[1, 200]`、`getDetail` 为空抛 `BizException(NOT_FOUND)`）；`POST /audit/log/export`、`@RequirePermission("sys:audit:log:list")`、读审计的元审计**未实现**（登记待做）。

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
| T-AUD-9 | 阶段二 MQ 汇聚（whale-audit 消费） ⚠️ 未落地（阶段二） | T-AUD-4 | L | 容器就绪后联调 |

> 工作量相对量级：S 小 / M 中 / L 大，不含具体工期。

---

## 12. 实施记录（2026-10-17，Wave 5.1b）

本批把 T-AUD-1 ~ T-AUD-7 落地为可运行代码（核心下沉 `whale-spring-boot-starter`，查询接口留在 `whale-audit`）。

| 变更点 | 文件 | 说明 |
|---|---|---|
| 枚举 | `backend/whale-common/whale-spring-boot-starter/.../common/audit/enums/OperateType.java` | LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER |
| 注解 | `.../common/audit/annotation/AuditLog.java` | `module()`（空则取 `spring.application.name`）/`operation()`/`type()`/`targetType()`/`targetId()`（SpEL）/`recordParams()=true`/`recordResult()=false` |
| 实体 | `.../common/audit/entity/AuditLogRecord.java` | `@TableName("audit_log")`，21 字段，`@TableId(type = IdType.ASSIGN_ID)`；**不继承 `BaseEntity`**（append-only，无 `update_time`/`deleted`） |
| Mapper | `.../common/audit/mapper/AuditLogMapper.java` | `extends BaseMapper`，被 `@MapperScan("com.bluewhale.**.mapper")` 扫描 |
| 脱敏器 | `.../common/audit/support/SensitiveDataMasker.java` | 递归脱敏（深度 8）+ 参数 JSON 4096 截断 + key 归一化匹配规则 |
| 切面 | `.../common/audit/aop/AuditLogAspect.java` | `@Order(LOWEST_PRECEDENCE)`；采集 module/method(`declaringTypeName#name`)/uri/httpMethod/targetType/targetId/requestParams/responseCode/bizStatus/errorMsg/ip/userAgent/costMs/createTime；用户上下文只取 `X-User-Id`/`X-Username`，traceId 取 `X-Trace-Id`（无 MDC 回落） |
| 自动配置 | `.../common/audit/config/AuditLogAutoConfiguration.java` | `@AutoConfiguration(after = MybatisPlusConfig.class)` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass` + `@ConditionalOnBean(AuditLogMapper.class)`；Bean：`auditLogExecutor`（core=1/max=2/queue=256/`CallerRunsPolicy`/前缀 `audit-log-`）、`sensitiveDataMasker`、`auditLogAspect` |
| 单测 | `.../src/test/.../common/audit/aop/AuditLogAspectTest.java`、`.../support/SensitiveDataMaskerTest.java` | 覆盖切面成功/失败/落库路径与脱敏规则 |
| 自动配置注册 | `.../src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 追加 `com.bluewhale.common.audit.config.AuditLogAutoConfiguration` |
| starter pom | `backend/whale-common/whale-spring-boot-starter/pom.xml` | 保持普通库模块（不 repackage），可被业务服务依赖 |
| 查询 DTO | `backend/whale-ops/whale-audit/.../audit/dto/AuditLogQueryRequest.java` | `extends PageQuery` + userId/username/module/operateType/bizStatus/traceId/startTime/endTime |
| 查询 VO | `.../audit/vo/AuditLogVO.java` | 与 `AuditLogRecord` 字段一一对应 |
| 查询服务 | `.../audit/service/AuditLogService.java` + `.../service/impl/AuditLogServiceImpl.java` | 分页（`MAX_PAGE_SIZE=200`、`orderByDesc(create_time)`）、`getDetail`（空则抛 `BizException(ErrorCode.NOT_FOUND)`）、`listByTraceId` |
| 查询控制器 | `.../audit/controller/AuditLogController.java` | `@RequestMapping("/audit/log")`：`GET /page`、`GET /{id}`、`GET /trace/{traceId}` |
| 查询单测 | `.../src/test/.../audit/service/impl/AuditLogServiceImplTest.java` | 覆盖分页/详情/按 traceId |
| 服务构建 | `backend/whale-ops/whale-audit/pom.xml`、`src/main/resources/application.yml` | pom 新增 `whale-spring-boot-starter`；yml 新增 `spring.datasource`（逻辑库 `bluewhale_audit`） |
| 埋点（auth） | `backend/whale-services/whale-auth/.../controller/AuthController.java` | `#login`（LOGIN，`recordParams=false`）/`#logout`（LOGOUT，`recordParams=false`） |
| 埋点（auth） | `.../controller/UserController.java` | `#create`（CREATE，targetType `sys_user`）/`#assignRoles`（GRANT，`targetId="#id"`） |
| 埋点（auth） | `.../controller/RoleController.java` | `#assignPermissions`（GRANT，`targetId="#id"`） |
| 建表（auth 侧） | `backend/whale-services/whale-auth/src/main/resources/db/init.sql` | 追加第 9 节 `audit_log` 建表（与 whale-audit DDL 一致，含 7 索引；阶段一各服务写各自实例下的审计表，DDL 以本设计文档与 whale-audit init.sql 为准，append-only 故不设 `update_time`/`deleted`） |

**验证证据**：

- 全量编译：`mvn -B clean compile -T 1C` → BUILD SUCCESS。
- 单测：`mvn -B -pl whale-common/whale-spring-boot-starter,whale-ops/whale-audit,whale-services/whale-auth -am test` → starter 15 / auth 19（不回归）/ audit 6，全绿（合计 40）。
- 静态检查：上述三模块 `checkstyle:check` → 0 violations。

**遗留 / 待做**：T-AUD-8 保留归档、T-AUD-9 MQ 汇聚（阶段二）、`POST /audit/log/export`、`@Sensitive` 注解兜底、脱敏规则配置化、whale-audit 侧 `@RequirePermission("sys:audit:log:list")`、运行时/端到端验证（本机无 Docker）、网关路由前缀冲突确认。

> 变更留痕：检查报告见 [docs/reviews/2026-10-17-wave5.1b-audit-log.md](../reviews/2026-10-17-wave5.1b-audit-log.md)，任务台账见 [docs/plans/task-breakdown.md](../plans/task-breakdown.md) 与 [docs/plans/next-steps.md](../plans/next-steps.md)。

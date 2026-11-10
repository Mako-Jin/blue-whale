# Spec：Wave 5.1b 审计日志落地（T5.10 / T5.11 / T5.12）

| 项 | 内容 |
|---|---|
| 需求编号 | REQ-SEC-001（设计基线 [audit-log.md](../../design/audit-log.md)） |
| 状态 | 待批准 → 批准后编码 |
| 日期 | 2026-10-17 |
| 作者 | BlueWhale |
| 前置 | Wave 5.0（T5.9 表字段补全）、Wave 5.1（REQ-SEC-003/004 已闭环，commit 1d7a4e6） |

---

## 1. 目标与验收（对应 register 验收口径）

| 任务 | 目标 | 验收口径 | 验证方式（无 Docker 前提） |
|---|---|---|---|
| T5.10 | `@AuditLog` 注解 + AOP 切面 + 异步落库（afterCommit） | auth 的登录/登出/权限变更产生审计记录 | 编译 + 切面单测（mock Mapper + mock HttpServletRequest） |
| T5.11 | 敏感字段脱敏器接入审计参数序列化 | 审计 `request_params` 中敏感字段为掩码 | 脱敏器单测（密码/手机号/身份证/银行卡/令牌/邮箱/嵌套） |
| T5.12 | 审计查询接口（分页 + 条件，`Result<PageResult>`） | 可按用户/模块/时间范围查询 | 编译 + 查询服务单测（mock Mapper，校验 wrapper 条件与分页映射） |

**不在本次范围**（登记为待做项，避免范围膨胀）：MQ 汇聚（T-AUD-9）、导出接口（需 whale-file）、保留归档任务（T-AUD-8）、`@Sensitive` 注解兜底、脱敏规则配置化（`whale.audit.sensitive.keys`）、whale-audit 侧 `@RequirePermission` 鉴权（需引入 auth-sdk + 拦截器）。

## 2. 关键决策

### D1（修正设计 §5.1 落点）：审计核心下沉到 `whale-spring-boot-starter`
- 理由：`whale-audit` 使用 `spring-boot-maven-plugin:repackage` → 产物类位于 `BOOT-INF/classes`，**不能作为库被 auth 依赖**；starter 为普通库模块，auth/gateway 已依赖，且与已落地的 `@DistributedLock`（annotation+aop+config 同处 starter）模式一致。
- 包路径：`com.bluewhale.common.audit.*`（原设计 `com.bluewhale.audit.annotation` 废弃）。
- 服务侧（whale-audit）自带 `spring-boot-starter-web/validation/actuator/nacos/mybatis-plus/mysql/redisson/tools`，本次新增依赖 starter 以复用实体、Mapper、统一响应体。

### D2：用户上下文仅取「网关注入头」，不引入 spring-security 依赖
- starter 无 spring-security（不可为审计新增硬依赖，否则无 security 的服务会 NoClassDefFoundError）。
- 取值优先级：`X-User-Id` / `X-Username`（网关 `JwtAuthGlobalFilter` 注入）→ 缺失留空。
- 设计 §5.2 中「回落 `SecurityContextHolder(LoginUser)`」「回落 MDC」本次不实现（登记待做项；全仓无任何 MDC 使用，MDC 回落无实际来源）。

### D3：实体不继承 `BaseEntity`
- `audit_log` 为 append-only：无 `update_time`、无 `deleted`（见 init.sql 注释），继承 `BaseEntity` 会引入不存在的列。
- `id` 用 `@TableId(type = IdType.ASSIGN_ID)`（雪花），`create_time` 由切面显式写入。

### D4：脱敏实现方式（纯 Java 递归 + 一次序列化，规避 Jackson 3 树 API 差异）
- `SensitiveDataMasker`：`mask(Object)` 递归把对象转成 `LinkedHashMap/List/标量` 结构（Map/Iterable/数组/JavaBean 反射，深度上限 8），按 **key 名忽略大小写** 命中规则表替换值；再由注入的 Jackson **3** `ObjectMapper.writeValueAsString` 输出 JSON（只依赖稳定 API）。
- 规则：password/oldPassword/newPassword/confirmPassword → `******`；phone/mobile → 前 3 后 4；idCard/idNo → 前 6 后 4；bankCard/cardNo → 后 4；authorization/token/refreshToken/accessToken → `***`；email → 首字符 + 域名。
- 参数 JSON 上限 4KB（超出截断，避免体积膨胀）。

### D5：切面顺序与事务边界
- `@Order(Ordered.LOWEST_PRECEDENCE)`（内层，可捕获业务结果与异常）。
- 落库：`TransactionSynchronizationManager.isSynchronizationActive()` 为真 → `registerSynchronization(afterCommit)` 后再提交线程池；否则直接提交线程池。两条路径都以 `auditLogExecutor`（有界队列 256 + `CallerRunsPolicy`）执行。
- 审计写入失败/线程池拒绝：仅 `log.warn`，**不得影响主流程**，异常原样抛出（不吞异常）。

### D6：埋点接入范围（auth）
| 接口 | 注解参数 |
|---|---|
| `AuthController#login` | `type=LOGIN`, `recordParams=false`（密码绝不落库） |
| `AuthController#logout` | `type=LOGOUT` |
| `UserController#create`（创建用户） | `type=CREATE`, `targetType=sys_user`（`recordParams=true`，密码走脱敏） |
| 角色/权限分配（UserController#assignRoles / RoleController#assignPermissions） | `type=GRANT`, `targetId="#request.userId"` |

## 3. 变更清单（精确到文件与函数）

### 3.1 `whale-backend/whale-common/whale-spring-boot-starter`
| 文件 | 内容 |
|---|---|
| `.../common/audit/enums/OperateType.java`（新增） | `LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER` |
| `.../common/audit/annotation/AuditLog.java`（新增） | `module()`，`operation()`，`type()`，`targetType()`，`targetId()`(SpEL)，`recordParams()=true`，`recordResult()=false` |
| `.../common/audit/entity/AuditLogRecord.java`（新增） | `@TableName("audit_log")`，21 字段与 init.sql 一一对应 |
| `.../common/audit/mapper/AuditLogMapper.java`（新增） | `extends BaseMapper<AuditLogRecord>`（被 `@MapperScan("com.bluewhale.**.mapper")` 扫描） |
| `.../common/audit/support/SensitiveDataMasker.java`（新增） | `mask(Object)`、`maskParams(Object)`（含 4KB 截断） |
| `.../common/audit/aop/AuditLogAspect.java`（新增） | `around(ProceedingJoinPoint, AuditLog)`：计时→try/catch→`buildRecord`→`publish`（afterCommit/直提）；私有方法 `resolveTraceId/resolveIp/resolveUserHeaders/resolveTargetId/serializeParams` |
| `.../common/audit/config/AuditLogAutoConfiguration.java`（新增） | `@AutoConfiguration(after = MybatisPlusConfig.class)` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass` + `@ConditionalOnBean(AuditLogMapper.class)`；Bean：`auditLogExecutor`(ThreadPoolTaskExecutor, core=1,max=2,queue=256,CallerRunsPolicy,前缀 `audit-log-`)、`sensitiveDataMasker`、`auditLogAspect` |
| `src/main/resources/META-INF/spring/...AutoConfiguration.imports`（改） | 追加 `com.bluewhale.common.audit.config.AuditLogAutoConfiguration` |
| `pom.xml`（改） | 新增 `spring-boot-starter-test`（test 作用域） |
| `src/test/.../support/SensitiveDataMaskerTest.java`（新增） | 规则表 + 嵌套/数组 + 深度上限 + 截断用例 |
| `src/test/.../aop/AuditLogAspectTest.java`（新增） | mock `AuditLogMapper`；`MockHttpServletRequest` + `RequestContextHolder`；成功/异常两路径（`biz_status` 1/0、`error_msg`）、头取用户与 traceId、`recordParams=false` 不落参、无请求上下文不抛错、`insert` 恰好 1 次 |

### 3.2 `whale-backend/whale-ops/whale-audit`
| 文件 | 内容 |
|---|---|
| `pom.xml`（改） | 新增 `whale-spring-boot-starter` 依赖 |
| `src/main/resources/application.yml`（改） | 新增 `spring.datasource`（`bluewhale_audit`，风格与 auth 一致） |
| `.../audit/dto/AuditLogQueryRequest.java`（新增） | `extends PageQuery`：`userId/username/module/operateType/bizStatus/traceId/startTime/endTime` |
| `.../audit/vo/AuditLogVO.java`（新增） | 21 字段只读视图 |
| `.../audit/service/AuditLogService.java`（新增，接口） | `page(AuditLogQueryRequest)`、`getById(Long)`、`listByTraceId(String)` |
| `.../audit/service/impl/AuditLogServiceImpl.java`（新增） | `LambdaQueryWrapper`：等值条件 + `create_time between` + `orderByDesc(create_time)` + `size<=200` 保护 |
| `.../audit/controller/AuditLogController.java`（新增） | `GET /audit/log/page`、`GET /audit/log/{id}`、`GET /audit/log/trace/{traceId}` → `Result<...>` |
| `src/test/.../service/AuditLogServiceImplTest.java`（新增） | mock `AuditLogMapper`：分页映射、条件构造、size 上限 |

### 3.3 `whale-backend/whale-services/whale-auth`
| 文件 | 内容 |
|---|---|
| `controller/AuthController.java`（改） | `#login` / `#logout` 增加 `@AuditLog`（D6） |
| `controller/UserController.java`（改） | 创建用户 `@AuditLog(CREATE)`、分配角色 `@AuditLog(GRANT)` |
| `controller/RoleController.java`（改） | 分配权限 `@AuditLog(GRANT)` |

## 4. 验证步骤（必须全部执行并留痕）

1. `cd backend && mvn clean compile -T 1C` → 28 模块 BUILD SUCCESS。
2. `mvn -pl whale-common/whale-spring-boot-starter -am test`（新增单测全绿）。
3. `mvn -pl whale-ops/whale-audit -am test`。
4. `mvn -pl whale-services/whale-auth -am test`（既有 19 用例不回归）。
5. `cd f:\blue-whale && mvn -f whale-backend/pom.xml -pl <模块> checkstyle:check` → 0 违规（warning 模式）。
6. 产出 `docs/reviews/2026-10-17-wave5.1b-audit-log.md`（逐项对应 57 项清单）。
7. 更新 `docs/design/audit-log.md`（落点修正 + 实施记录）、`register.md`、`task-breakdown.md`、`next-steps.md`、`devlog/2026-10-17.md §14`（四类信息）。
8. 自动 commit + push（Gitee `origin/main`，多 `-m`，正文中文）。

## 5. 风险

| 风险 | 缓解 |
|---|---|
| `@ConditionalOnBean(AuditLogMapper.class)` 依赖 `@MapperScan` 注册时序 | `@AutoConfiguration(after = MybatisPlusConfig.class)`；编译 + 单测验证；如失效则降级为 `ObjectProvider` 软依赖 |
| Jackson 3 API 与 Jackson 2 差异 | 脱敏只依赖 `writeValueAsString`（稳定 API），不触碰 JsonNode 树 API |
| 无 Docker：无法验证真实落库/Redis | 明确记录为「静态验证通过、运行时待中间件就绪」（沿用既有口径） |
| 各服务写各自 `bluewhale_audit` 逻辑库 | 本次仅 auth 接入；网关无 DataSource 不生效 |

## 6. 需批准事项

1. **D1 落点修正**（核心代码放 starter 而非 whale-audit）——与原设计 §5.1 文本不一致，需确认。
2. **不实现导出接口 / `@Sensitive` / 脱敏规则配置化 / audit 侧 `@RequirePermission`**——登记为待做项，需确认。
3. **D2 不引入 spring-security 依赖**（用户上下文只取网关注入头）。

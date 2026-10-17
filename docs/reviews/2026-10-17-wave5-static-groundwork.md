# 代码检查报告 — Wave 5.0 静态可落地批次（分布式 ID / 分布式锁 / 生产密钥校验 / 审计表字段 / auth 单测）

- 关联需求：REQ-DIST-001（T5.1）、REQ-DIST-002（T5.2）、REQ-SEC-005（T5.8）、REQ-SEC-001（T5.9）、REQ-QA-002（T5.19）
- 检查依据：[code-review-checklist.md](../checklists/code-review-checklist.md)
- 检查日期：2026-10-17 ｜ 检查人：BlueWhale
- 结论：**有条件通过**（无 P0/P1 遗留；1 项 P2 已知取舍需前端配合；1 项 P3 继承问题记录）

---

## 一、变更范围

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `whale-spring-boot-starter` `common/jackson/config/JacksonConfig.java` | 新增 | 全局 Long/long → String 序列化（Jackson 3） |
| 2 | `whale-spring-boot-starter` `common/lock/annotation/DistributedLock.java` | 新增 | 分布式锁注解 |
| 3 | `whale-spring-boot-starter` `common/lock/aop/DistributedLockAspect.java` | 新增 | SpEL Key 解析 + Redisson `RLock` 切面 |
| 4 | `whale-spring-boot-starter` `common/lock/config/DistributedLockAutoConfiguration.java` | 新增 | 条件装配（`@ConditionalOnClass`） |
| 5 | `whale-spring-boot-starter` `common/core/exception/ErrorCode.java` | 修改 | 新增 `LOCK_ACQUIRE_FAILED(429)` |
| 6 | `whale-spring-boot-starter` `pom.xml` | 修改 | 新增 `aspectjweaver`；`redisson-spring-boot-starter` 设为 `optional` |
| 7 | `whale-spring-boot-starter` `META-INF/spring/…AutoConfiguration.imports` | 修改 | 注册 2 个新增自动配置类 |
| 8 | `whale-tools` `utils/IdUtils.java` | 修改 | Javadoc 明确「非严格雪花、勿作实体主键」 |
| 9 | `whale-auth` `config/JwtProperties.java` | 修改 | 提取 `DEFAULT_PLACEHOLDER_SECRET` 常量 |
| 10 | `whale-auth` `config/SecretPlaceholderChecker.java` | 新增 | 非 dev 环境占位密钥强制校验 |
| 11 | `whale-audit` `db/init.sql` | 修改 | 补 7 字段 + 3 索引（ALTER 折叠进 CREATE） |
| 12 | `whale-auth` `src/test/java/…/jwt/JwtTokenProviderTest.java` | 新增 | 6 个用例 |
| 13 | `whale-auth` `src/test/java/…/security/RedisTokenBlacklistTest.java` | 新增 | 7 个用例 |
| 14 | `docs/design/distributed.md` | 修改 | §1.6 补「落地钩子（已实测）」与影响面说明 |

---

## 二、逐项检查结果

### A 类 — 协议与接口安全

| 编号 | 检查项 | 结论 | 证据 | 说明 |
|---|---|---|---|---|
| A1 | 凭据传输 | 通过（未变更） | `whale-auth/config/JwtProperties.java:25-31` | 密钥仅从配置读取；本批未新增 token 传输路径 |
| A2 | JWT 算法与密钥 | 通过（增强） | `whale-auth/config/SecretPlaceholderChecker.java:32-45` | 非 dev 环境占位密钥直接拒绝启动，避免弱密钥 HS256；签发/校验仍固定算法 |
| A3 | 越权防护 | 不适用 | — | 本批未新增对外接口与鉴权路径 |
| A4–A10 | 请求头注入/CSRF/重放/上传/SSRF/限流/CORS | 不适用 | — | 本批无对应面（网关与文件服务未涉及） |
| A11 | 错误信息 | 通过 | `DistributedLockAspect.java:92,99` 抛 `BizException(429)` | 对外统一错误码枚举，不含堆栈/SQL/内部路径 |
| A12 | 安全响应头 | 不适用 | — | 网关/Nginx 层配置项，本批未涉及 |

### B 类 — 代码漏洞与健壮性

| 编号 | 检查项 | 结论 | 证据 | 说明 |
|---|---|---|---|---|
| B1 | SQL 注入 | 不适用 | `whale-audit/db/init.sql` | 本次仅 DDL（建表 + 索引），无动态 SQL 拼接 |
| B2 | 反序列化 | 通过 | `JacksonConfig.java:33-39` | 仅**注册序列化器**，不注册任何多态/默认类型反序列化 |
| B3 | 表达式注入 | 通过（受控） | `DistributedLockAspect.java:114-124` | SpEL 表达式来源为**注解字面量**（开发者编写，非用户输入），用户数据只作为变量值传入，不参与表达式解析 |
| B5 | 硬编码凭据 | 通过 | `JwtProperties.java:22`；`whale-auth/application.yml:32` | 占位密钥常量仅用于 dev 且已被 `SecretPlaceholderChecker` 强制拦截；无其他明文凭据 |
| B6 | 日志脱敏 | 通过 | `DistributedLockAspect.java:91,98,101`、`SecretPlaceholderChecker.java:38` | 日志仅打印锁 Key 与异常消息，不含 token/密钥原文 |
| B8 | 资源泄漏 | 通过 | `DistributedLockAspect.java:70-76` | `try/finally` 保证锁释放；无手工连接/流 |
| B9 | 异常处理 | 通过 | `DistributedLockAspect.java:88-93` | 捕获 `InterruptedException` 后**恢复中断标志**再抛，不吞异常；不捕获 `Throwable` |
| B10 | 空值与边界 | 通过 | `DistributedLockAspect.java:114,120`；`SecretPlaceholderChecker.java:50-56` | `key()` 判空走退化分支；profile 遍历判空 |
| B12 | 事务边界 | 通过 | `DistributedLockAspect.java:37-42,62-77` | 切面 order 高于 `@Transactional`，锁覆盖整个事务；未使用同类自调用 |
| B13 | 幂等性 | 通过（部分） | `DistributedLockAspect.java:73-75` | 锁本身提供互斥；对外写接口的幂等组件（T5.3 `@Idempotent`）本批未实现，已登记下一批 |
| B14 | 缓存问题 | 通过 | `DistributedLockAspect.java:73` | 锁 Key 由 `getLock` 生成，Redisson 管理 TTL（`leaseTime=-1` 时看门狗续期） |
| B15 | 时间与随机 | 通过（未变更） | `whale-tools/utils/IdUtils.java:8-13,35-42` | 注释已明确「非严格雪花、无 workerId/同毫秒序列」，实体主键走 MP 雪花；时钟回拨策略待自研时落地 |
| B16 | 并发安全 | 通过 | `DistributedLockAspect.java:52-56` | `SpelExpressionParser`/`DefaultParameterNameDiscoverer` 均为线程安全实现，无共享可变状态；无双重检查锁 |

### C 类 — 圈复杂度与结构

| 编号 | 检查项 | 阈值 | 实测（本次新增/变更类） | 结论 |
|---|---|---|---|---|
| C1 | 单方法圈复杂度 | ≤ 10 | `lockAround`=3、`acquire`=4、`buildLockKey`=3、`afterPropertiesSet`=3、`isDevProfile`=3 | 通过 |
| C2 | 方法行数 | ≤ 60 | 最长 `lockAround` 16 行 | 通过 |
| C3 | 嵌套深度 | ≤ 3 | 最深 2（`try` → `if`） | 通过 |
| C4 | 方法参数 | ≤ 4 | 最多 3（`acquire`） | 通过 |
| C5 | 类规模 | ≤ 500 行 | `DistributedLockAspect` 128 行、3 方法 | 通过 |
| C6 | 重复代码 | ≥3 处须抽取 | 锁 Key 分隔符/退化拼接均常量与单一实现；无重复片段 | 通过 |
| C7 | 死代码 | 必须删除 | 新增文件无注释代码块、无未使用 import（Checkstyle 0 违规） | 通过 |
| C8 | 条件复杂度 | ≥4 分支改分派 | `acquire` 为 2 分支，未达重构阈值 | 通过 |
| C9 | 职责单一 | 单一职责 | `JacksonConfig`=序列化定制；`DistributedLockAspect`=加锁编排；`SecretPlaceholderChecker`=启动校验 | 通过 |

### D 类 — 魔法值

| 编号 | 检查项 | 结论 | 证据 |
|---|---|---|---|
| D1 | 数值字面量常量化 | 通过 | `LOCK_ASPECT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100`（`DistributedLockAspect.java:42`）；注解默认值 `waitTime=0/leaseTime=30` 属**声明式配置默认值**，非散落字面量 |
| D2 | 字符串字面量常量化 | 通过 | `KEY_SEPARATOR`、`METHOD_KEY_SEPARATOR`（`DistributedLockAspect.java:45,48`）、`MODULE_NAME`（`JacksonConfig.java:27`）、`DEV_PROFILE`（`SecretPlaceholderChecker.java:29`） |
| D3 | 状态与类型 | 通过 | 错误码用 `ErrorCode.LOCK_ACQUIRE_FAILED` 枚举，未裸比较 |
| D4 | 常量命名与位置 | 通过 | 全部 `UPPER_SNAKE_CASE`，就近定义于使用方类 |
| D5 | 配置外置 | 通过 | 密钥/过期时间仍走 `application.yml` + 环境变量；锁参数由注解显式声明 |
| D6 | 前端魔法值 | 不适用 | 本批无前端变更 |

### E 类 — 命名、格式与工程规范

| 编号 | 检查项 | 结论 | 说明 |
|---|---|---|---|
| E1 | 包名 | 通过 | `com.bluewhale.common.jackson.config` / `com.bluewhale.common.lock.{annotation,aop,config}`，与既有 `common.mybatis.config` 风格一致 |
| E2 | 命名 | 通过 | `JacksonConfig`、`DistributedLock`、`DistributedLockAspect`、`SecretPlaceholderChecker`，无拼音/缩写 |
| E3 | 布尔命名 | 通过 | `failFast` / `acquired` / `isDevProfile` |
| E4 | 分层对象命名 | 通过 | 注解 `DistributedLock`、切面 `…Aspect`、自动配置 `…AutoConfiguration` |
| E5 | 注释与文档 | 通过 | 新增类/公有方法均有中文 Javadoc，含契约（降级语义、锁覆盖事务、看门狗限制） |
| E6 | 格式 | 通过 | Checkstyle 校验 3 个变更模块 0 违规 |
| E7 | 依赖注入 | 通过 | 切面/校验器均**构造器注入**（`DistributedLockAspect` 显式构造器、`@RequiredArgsConstructor`） |
| E8 | 日志规范 | 通过 | 全部 `{}` 占位符，级别恰当（warn 用于降级/拒绝路径） |
| E9 | 分层依赖 | 通过 | starter 内 `lock.config → lock.aop → lock.annotation`，单向；`ErrorCode` 为公共枚举 |
| E10 | 统一契约 | 通过 | 锁失败抛 `BizException(ErrorCode)`，复用统一异常体系 |
| E11 | 事务与缓存注解 | 通过 | 本批未新增 `@Transactional`；切面与事务顺序已在 Javadoc 明确 |
| E12 | 前端格式 | 不适用 | 无前端变更 |
| E13 | SQL 规范 | 通过 | 表名/字段 `snake_case`；索引命名 `idx_`；每个字段含 COMMENT；无 `update_time`/`deleted` 属 append-only 审计表**有意设计**（表头已注明） |
| E14 | 脚本与 Dockerfile | 通过 | `init.sql` 幂等可重跑（`DROP TABLE IF EXISTS` + `CREATE TABLE`），未引入新的 migration 机制，决策已写入表头注释 |

---

## 三、自动化校验证据

| 手段 | 命令 | 结果 |
|---|---|---|
| 单模块编译 | `mvn -pl backend/whale-common/whale-spring-boot-starter -am compile` | **BUILD SUCCESS** |
| 全模块编译 | `mvn clean compile -T 1C` | **BUILD SUCCESS**（29 个聚合/模块） |
| 单元测试 | `mvn -pl backend/whale-services/whale-auth -am test` | **Tests run: 13, Failures: 0, Errors: 0, Skipped: 0**（`JwtTokenProviderTest` 6 + `RedisTokenBlacklistTest` 7） |
| Checkstyle | `mvn -pl …starter,…whale-tools,…whale-auth checkstyle:check` | 0 Checkstyle violations，BUILD SUCCESS |

> 说明：Checkstyle 当前为「报告模式」（仅 `error` 级阻断，规则集为 `warning`）；`whale-auth` 出现的 2 条 `UnusedImports` 告警来自**既有未改动文件** `dto/AssignPermissionsRequest.java`（见第五节遗留）。

---

## 四、未通过项与修复记录

| 问题 | 级别 | 定位 | 修复方式 | 复验 |
|---|---|---|---|---|
| POM 引用了 Boot 4.0.8 **不存在**的受管构件 `spring-boot-starter-aop`（`dependencies.dependency.version is missing`，构建失败） | P1 | `whale-spring-boot-starter/pom.xml:32` | 核实 Boot 4.0.8 BOM 已移除该 starter；改用 BOM 统一管理的 `org.aspectj:aspectjweaver`（AOP 自动代理所需 `Advice` 类来源） | `mvn -pl …starter -am compile` → BUILD SUCCESS |
| 曾误判「Boot 4 用 Jackson 2」 | P1（认知） | `docs/design/distributed.md` §1.6 | 实测 BOM/构件：`spring-boot-starter-web → spring-boot-starter-jackson → tools.jackson.core:jackson-databind:3.1.5`，HTTP 序列化确为 **Jackson 3**；据此用 `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`（Boot 4 钩子，旧 `Jackson2ObjectMapperBuilderCustomizer` 已不存在）实现 | 编译通过；序列化器类 `tools.jackson.databind.ser.std.ToStringSerializer` 已由 `javap` 核实 |

---

## 五、遗留（P3 / 已知取舍 / 下一批）

| # | 项 | 级别 | 说明 | 处置 |
|---|---|---|---|---|
| 1 | `PageResult.total/size/current`（`long`）随全局规则变为 JSON 字符串 | P2（取舍） | 为覆盖所有雪花 `long` 字段（含 `id`）而注册 `Long`/`long` 两类序列化器，`long` 分页字段一并转字符串；`Result.code`（`int`）不受影响 | 已在 `distributed.md §1.6` 与本文档记录；前端 `PageResult` 类型需按 `string` 声明（T-ID-4） |
| 2 | 网关侧未同步 JWT 密钥占位校验 | P3 | 本批仅按 REQ-SEC-005 落点实现 auth 侧；`whale-gateway/application.yml` 存在同源默认值 | 登记为下一批待办 |
| 3 | 锁工具尚无场景接入与并发验证 | P3 | `@DistributedLock` 仅完成能力实现，未接入注册幂等/定时任务等场景；无 Redis 实机 | 随 T-LOCK-4 与中间件就绪后验证 |
| 4 | 既有 `AssignPermissionsRequest.java` 存在 2 处未使用 import | P3 | Checkstyle `UnusedImports` 告警（warning 级，非本次变更引入） | 记入待办，择机清理 |
| 5 | 分布式锁无单测 | P3 | `DistributedLockAspect` 依赖 `RedissonClient`，纯 mock 可覆盖 Key 解析与失败分支 | 登记为下一批（与 T5.3 幂等组件一并补） |

---

## 六、结论

本批 5 个任务（T5.1/T5.2/T5.8/T5.9/T5.19）在**无中间件环境**下完成了可静态验证的全部内容：全模块编译通过、13 个新增单测全绿、3 个变更模块 Checkstyle 0 违规。**无 P0/P1 遗留**；P2 取舍项（`long` 分页字段转字符串）已显式记录并需前端类型配合；其余 P3 项已登记为下一批待办。

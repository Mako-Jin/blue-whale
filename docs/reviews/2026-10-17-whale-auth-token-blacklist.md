# 代码检查报告 — whale-auth 分布式令牌黑名单

- 关联需求：REQ-SEC-002（登出令牌失效）、REQ-DIST-002（分布式基础设施）、REQ-QA-002（每次必检）
- 检查依据：[code-review-checklist.md](../checklists/code-review-checklist.md)
- 检查日期：2026-10-17 ｜ 检查人：BlueWhale
- 结论：**有条件通过**（1 项 P1 残余风险已登记待办，2 项 P3 继承问题记录）

---

## 一、变更范围

| # | 文件 | 类型 |
|---|---|---|
| 1 | `whale-auth` `security/TokenBlacklist.java` | 新增 |
| 2 | `whale-auth` `security/RedisTokenBlacklist.java` | 新增 |
| 3 | `whale-auth` `security/JwtAuthenticationFilter.java` | 修改（加入黑名单校验、抽出 `authenticate`） |
| 4 | `whale-auth` `jwt/JwtTokenProvider.java` | 修改（新增带前缀常量 + `getRemainingValiditySeconds`） |
| 5 | `whale-auth` `service/AuthService.java` | 修改（`logout(String)` 落黑名单） |
| 6 | `whale-auth` `controller/AuthController.java` | 修改（读取 `Authorization` 头） |
| 7 | `whale-auth` `pom.xml` | 修改（新增 `spring-boot-starter-data-redis`） |
| 8 | `config/checkstyle/whale-checkstyle.xml` | 新增 |
| 9 | `whale-parent` `pom.xml` | 修改（Checkstyle 插件管理） |
| 10 | `.github/workflows/code-quality.yml` | 新增 |
| 11 | `.github/workflows/frontend-ci.yml` | 修改（修正工作目录） |

---

## 二、逐项检查结果（关键项）

### A 类 — 协议与接口安全

| 编号 | 检查项 | 结论 | 证据 | 说明 |
|---|---|---|---|---|
| A1 | 凭据传输 | 通过 | `AuthService.java:99-106`、`AuthController.java:44-49` | token 仅从 `Authorization` 头读取，未落入 URL/query |
| A2 | JWT 算法与密钥 | 通过（未变更） | `JwtTokenProvider.java:62,78` | 校验侧固定 `verifyWith(secretKey)`，不信任 `alg` 头；本次未改动签发逻辑 |
| A3 | 越权防护 | 通过 | `JwtAuthenticationFilter.java:52-56` | auth 侧身份完全来自 token，不信任任何请求头；网关侧已有「先移除伪造 `X-User-*`」逻辑 |
| A11 | 错误信息 | 通过 | 未认证统一由 `RestAuthenticationEntryPoint` 返回 | 本次未新增对外错误细节 |
| A12 | 敏感值不入日志 | 通过 | `RedisTokenBlacklist.java:52,62`、`AuthService.java:102` | 日志只记录异常消息与用户名，**不打印 token 原文** |

### B 类 — 代码漏洞与健壮性

| 编号 | 检查项 | 结论 | 证据 | 说明 |
|---|---|---|---|---|
| B5 | 硬编码凭据 | 通过 | `RedisTokenBlacklist.java:35-46` | 新增代码无任何明文凭据；键前缀/占位符/算法名均常量化 |
| B6 | 日志脱敏 | 通过 | 见 A12 | 黑名单键使用 SHA-256 摘要，明文 token 不入 Redis keyspace 亦不入日志 |
| B8 | 资源泄漏 | 通过 | 使用 `StringRedisTemplate`，无手工连接 | 无流/连接需手工释放 |
| B9 | 异常处理 | 通过（有意宽捕获） | `RedisTokenBlacklist.java:51,60` | 捕获 `RuntimeException` 属**有意降级**设计（Redis 客户端异常均为 RuntimeException），非吞异常：均记录 warn 且 `contains` 有明确兜底语义 |
| B12 | 事务边界 | 不适用 | — | 本次无事务操作 |
| B13 | 幂等性 | 通过 | `RedisTokenBlacklist.java:47-52` | `add` 重复调用安全（`SET` 覆盖）；已过期 token 直接跳过 |
| B14 | 缓存问题 | 通过 | `RedisTokenBlacklist.java:50` | TTL = token 剩余有效期，键自动过期，无无限增长 |
| B15 | 时间与随机 | 通过 | `JwtTokenProvider.java:110-117`、`RedisTokenBlacklist.java:38` | 用 `MessageDigest`（线程安全，每次新建实例，避免共享 `SimpleDateFormat` 类问题）；TTL 取绝对值秒且下限为 1 |
| B10 | 空值与边界 | 通过 | `AuthService.java:114-120`、`JwtTokenProvider.java:111-113` | 头缺失/前缀不符/空 token 均返回 `null` 并短路；`claims`/`expiration` 判空 |

### C 类 — 圈复杂度与结构

| 编号 | 检查项 | 阈值 | 实测 | 结论 |
|---|---|---|---|---|
| C1 | 单方法圈复杂度 | ≤ 10 | `authenticate` ≈ 5；`add`/`contains` ≤ 3；`getRemainingValiditySeconds` = 3 | 通过 |
| C2 | 方法行数 | ≤ 60 | 最长 `authenticate` 33 行 | 通过 |
| C3 | 嵌套深度 | ≤ 3 | 最深 2（`try` 内单层 `if`） | 通过（抽出 `authenticate` 后由 5 层降为 2 层） |
| C4 | 方法参数 | ≤ 4 | 最多 2 | 通过 |
| C6 | 重复代码 | ≥3 处须抽取 | `"Bearer "` 原在 filter/service 两处各自定义 → 收敛为 `JwtTokenProvider.BEARER_PREFIX` | 通过（已消除） |
| C7 | 死代码 | 必须删除 | 原 `logout()` 的 TODO 占位注释已随实现移除 | 通过 |

### D 类 — 魔法值

| 编号 | 检查项 | 结论 | 证据 |
|---|---|---|---|
| D1 | 数值字面量常量化 | 通过 | `MIN_TTL_SECONDS = 1L`、`MILLIS_PER_SECOND = 1000L`（`JwtTokenProvider.java:40`）、`RedisTokenBlacklist.java:38` |
| D2 | 字符串字面量常量化 | 通过 | `KEY_PREFIX`、`PLACEHOLDER_VALUE = "1"`、`DIGEST_ALGORITHM = "SHA-256"`、`BEARER_PREFIX` |
| D5 | 可变参数外置 | 通过 | 密钥/过期时间仍走 `application.yml`；黑名单 TTL 由 token 有效期派生，无需额外配置 |

### E 类 — 命名与规范

| 编号 | 检查项 | 结论 | 说明 |
|---|---|---|---|
| E1–E4 | 包/类/方法/常量命名 | 通过 | `com.bluewhale.auth.security`；`TokenBlacklist`/`RedisTokenBlacklist`；常量 `UPPER_SNAKE_CASE` |
| E5 | 注释与文档 | 通过 | 新增类与公有方法均有 Javadoc，含契约与降级语义说明 |
| E6 | 格式 | 通过 | Checkstyle 校验 `whale-auth` 0 违规 |
| E7 | 依赖注入 | 通过 | 全部 `@RequiredArgsConstructor` 构造器注入，无字段注入 |
| E8 | 日志规范 | 通过 | 使用 `{}` 占位符，级别恰当（debug 正常路径 / warn 降级路径） |
| E9 | 分层依赖 | 通过 | `AuthController`（取头）→ `AuthService`（编排）→ `TokenBlacklist`（能力）；无反向依赖 |
| E10 | 统一契约 | 通过 | 返回 `Result.ok()`；未新增裸响应 |

### 自动化校验证据

| 手段 | 命令 | 结果 |
|---|---|---|
| 编译 | `mvn -pl whale-backend/whale-services/whale-auth -am compile` | **BUILD SUCCESS**（6 模块，67 源文件） |
| Checkstyle（规则集自验） | `mvn -pl whale-backend/whale-common/whale-tools checkstyle:check` | 0 violations，BUILD SUCCESS |
| Checkstyle（变更模块） | `mvn -pl whale-backend/whale-services/whale-auth checkstyle:check` | 0 violations，BUILD SUCCESS |

> 说明：当前 Checkstyle 门禁为「报告模式」（仅 `error` 级阻断，规则集为 `warning`），故显示 0 违规；`checkstyle:checkstyle` 仍会产出含全部级别的 XML 报告用于趋势跟踪。

---

## 三、未通过项与修复记录

| 问题 | 级别 | 定位 | 修复方式 | 复验 |
|---|---|---|---|---|
| `MagicNumber` 规则含非法属性 `ignoreEnums`，导致 Checkstyle 配置初始化失败 | P1 | `config/checkstyle/whale-checkstyle.xml:81` | 移除 `ignoreEnums`（该属性在 Checkstyle 10.x 不存在） | 重跑 `checkstyle:check` → 0 违规 |
| 前端 CI 未指定工作目录，`pnpm install` 将在无 `package.json` 的仓库根执行而失败 | P1 | `.github/workflows/frontend-ci.yml` | 增加 `defaults.run.working-directory: frontend`，并为 `setup-node` 指定 `cache-dependency-path` | 结构复核（CI 需推送后验证） |
| `"Bearer "` 前缀在过滤器与服务中各自定义（重复代码 / 魔法值） | P2 | `JwtAuthenticationFilter`、`AuthService` | 收敛为 `JwtTokenProvider.BEARER_PREFIX` 单一来源 | 编译通过 |

---

## 四、残余风险（已登记待办）

| 风险 | 级别 | 说明 | 处置 |
|---|---|---|---|
| 登出未使 **refreshToken** 失效 | P1 | 当前仅黑名单 accessToken；已登出用户的 refreshToken 在 24h 内仍可换取新 accessToken，登出强度不完整 | 登记 **REQ-SEC-004**，登录时记录 token 版本号 / `jti`，刷新与登出时校验版本（待启动） |
| 网关侧未校验黑名单 | P2 | 黑名单在 `whale-auth` 内生效；经网关访问其他服务时，网关仅校验签名，登出 token 仍可到达下游 | 登记 **REQ-SEC-003**（已在需求登记册中），网关接入 Redis 校验（待启动） |
| `whale-auth` 8081 可被内网直连绕过网关 | P2 | 部署约束问题：需保证 auth 不对公网暴露，仅经网关访问 | 写入部署检查项（Phase 5 收口） |

---

## 五、继承问题（本次未引入，登记待清）

| 问题 | 级别 | 定位 | 说明 |
|---|---|---|---|
| 使用已过时 API | P3 | `JwtAuthenticationFilter.java`（`WebAuthenticationDetailsSource`） | 编译期 deprecation 提示，**原实现即存在**，本次重写沿用；待 Spring Security 7 明确替代 API 后统一替换 |
| 魔法值 `60_000` | P3 | `JwtTokenProvider.java:54,71` | 既有代码中 `expireMinutes * 60_000` 未常量化；本次未改动该行，登记待清 |
| JWT secret 默认值 | P2 | `whale-auth` / `whale-gateway` `application.yml` | 开发占位值 `bluewhale-jwt-secret-2026-...`，生产必须由 `JWT_SECRET` 环境变量覆盖；需在部署清单中强制校验 |
| jjwt 版本不一致 | P3 | `whale-parent`(0.12.5) vs `whale-gateway`(0.12.6) | 建议统一为父 POM 管理版本 |

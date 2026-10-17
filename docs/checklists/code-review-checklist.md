# 代码规范检查清单（Code Review Checklist）

> 用途：本项目**每一次新增或变更的代码**都必须按本清单逐项自检，并产出检查报告。
> 关联需求：REQ-QA-001（制定清单）、REQ-QA-002（每次必检）
> 报告路径：`docs/reviews/YYYY-MM-DD-<主题>.md`（模板见第六节）
> 最后更新：2026-10-17 ｜ 维护：BlueWhale

---

## 一、适用范围

| 项 | 说明 |
|---|---|
| 触发时机 | 每次代码变更完成、提交（commit）之前 |
| 覆盖对象 | 后端 Java（`backend/**`）、前端 TS/TSX（`frontend/**`）、SQL（`**/db/init.sql`）、脚本与 Dockerfile（`infrastructure/**`、`deploy/**`） |
| 执行者 | 变更作者自检 + 复核者抽查 |
| 留痕 | 报告必须逐条对应本清单编号，标注 `通过 / 不通过 / 不适用`，不通过项须有处置 |

---

## 二、A 类 —— 协议与接口安全（Protocol）

| 编号 | 检查项 | 判定标准 | 检查方式 |
|---|---|---|---|
| A1 | 凭据传输 | 生产必须 HTTPS；token 只走 `Authorization: Bearer` 头，禁止出现在 URL/query/日志 | 代码检索 `token` 出现位置 |
| A2 | JWT 算法与密钥 | 校验时**固定算法**（本项目 HS256），不信任 token 头里的 `alg`；密钥长度 ≥ 256 bit；access/refresh 过期时间明确 | 审 `JwtTokenProvider` / `JwtAuthGlobalFilter` |
| A3 | 越权防护 | 纵向：接口有权限注解；横向：按数据归属过滤（IDOR）；**网关必须清除入站伪造的 `X-User-Id / X-Username / X-User-Roles`** | 审过滤器头处理逻辑 |
| A4 | 请求头注入 | 用户可控值写入响应头前须过滤 CRLF（`\r\n`）；不无条件信任 `X-Forwarded-*`、`Host` | 审 header 写入点 |
| A5 | CSRF | 无状态 Bearer 接口豁免；若引入 Cookie 会话则必须 CSRF token | 审 SecurityConfig |
| A6 | 重放防护 | 敏感写操作（支付、审批）需 nonce/时间戳或幂等 token | 审写接口设计 |
| A7 | 文件上传 | 扩展名 + MIME 白名单、大小限制、存储路径与 Web 根隔离、防路径穿越 | 审 file 服务 |
| A8 | SSRF | 用户可控 URL 需域名白名单 + 内网段（127/10/172.16/192.168）拦截 | 检索 HTTP 客户端调用 |
| A9 | 限流与体积 | 公网接口有限流；请求体大小限制 | 审网关配置 |
| A10 | CORS | 禁止 `Access-Control-Allow-Origin: *` 与 `allowCredentials=true` 并存 | 审跨域配置 |
| A11 | 错误信息 | 对外错误响应不含堆栈、SQL、内部路径、组件版本 | 审 `GlobalExceptionHandler` |
| A12 | 安全响应头 | 对外响应建议含 `X-Content-Type-Options: nosniff` 等 | 审 Nginx / 网关 |

---

## 三、B 类 —— 代码漏洞与健壮性（Code Security）

| 编号 | 检查项 | 判定标准 | 检查方式 |
|---|---|---|---|
| B1 | SQL 注入 | MyBatis 一律 `#{}`；`${}` 仅用于白名单校验后的动态列/排序 | 检索 `${` |
| B2 | 反序列化 | 禁止对不可信输入做多态反序列化；Redis 中避免存 Java 序列化对象 | 审缓存与消息体 |
| B3 | 表达式注入 | SpEL / OGNL / 脚本引擎不得执行用户输入 | 检索 `SpelExpressionParser` |
| B4 | 路径穿越 | 文件路径拼接前做规范化 + 前缀校验 | 审 IO 代码 |
| B5 | 硬编码凭据 | 源码与仓库配置无明文密码/密钥；一律走环境变量；已有 JWT secret 默认值仅作开发占位 | 检索 `password`、`secret`、`key` 字面量 |
| B6 | 日志脱敏 | 禁止打印密码、token、身份证、银行卡、完整手机号；防日志注入（换行） | 审 `log.*` 语句 |
| B7 | 敏感数据存储 | 密码用 BCrypt（已有 `PasswordEncoder`）；敏感字段入库加密/掩码 | 审实体与 VO 转换 |
| B8 | 资源泄漏 | 流/连接/锁用 try-with-resources 或 finally 释放；线程池有界且自定义命名 | 审 IO 与并发代码 |
| B9 | 异常处理 | 不吞异常（空 catch）；`catch` 具体异常不用 `Throwable`；不将栈信息返回前端 | 检索 `catch` |
| B10 | 空值与边界 | 外部输入做校验；集合空判断；数组越界；除零；`Optional` 合理使用 | 审公共方法 |
| B11 | 依赖安全 | 版本由父 POM 统一管理；新增依赖须检查已知 CVE；不使用 SNAPSHOT 于生产 | 审 `pom.xml` diff |
| B12 | 事务边界 | `@Transactional` 不用于同类自调用；传播行为明确；避免长事务与事务内远程调用 | 审 Service |
| B13 | 幂等性 | 写接口（注册、支付、回调）具备幂等保证（唯一索引/状态机/Token） | 审写接口 |
| B14 | 缓存问题 | 防穿透（空值哨兵）、防击穿（互斥重建）、防雪崩（TTL 抖动） | 审缓存代码 |
| B15 | 时间与随机 | 安全场景用 `SecureRandom`；统一时区（Asia/Shanghai）；考虑时钟回拨 | 审 ID/随机/时间代码 |
| B16 | 并发安全 | 共享可变状态加锁或用并发容器；避免双重检查锁陷阱；`SimpleDateFormat` 不共享 | 审 static 字段 |

---

## 四、C 类 —— 圈复杂度与结构（Complexity）

| 编号 | 检查项 | 阈值 | 处置 |
|---|---|---|---|
| C1 | 单方法圈复杂度 | ≤ 10 合格；11–15 警告；> 15 必须重构 | 拆分方法 / 卫语句 / 表驱动 |
| C2 | 方法行数 | ≤ 60 行为宜；> 100 必须拆分 | 抽取方法 |
| C3 | 嵌套深度 | if/for/while/try 嵌套 ≤ 3 层 | 卫语句提前返回 |
| C4 | 方法参数 | ≤ 4 个；超出用参数对象封装 | 引入 DTO |
| C5 | 类规模 | 单类 ≤ 500 行、方法数 ≤ 20、字段 ≤ 15 | 按职责拆分 |
| C6 | 重复代码 | 同一逻辑出现 ≥ 3 处须抽取；跨模块重复须下沉公共模块 | 检索相似片段 |
| C7 | 死代码 | 注释掉的代码块、未使用的 private 方法/字段/import 一律删除 | IDE 检查 + `mvn compile` 告警 |
| C8 | 条件复杂度 | 长 `if/else if` 链 ≥ 4 分支改为枚举/策略/Map 分派 | 人工审阅 |
| C9 | 职责单一 | 类名与职责一致，无「上帝类」；Controller 只做参数校验与编排 | 审分层 |

---

## 五、D 类 —— 魔法值（Magic Value）

| 编号 | 检查项 | 判定标准 | 例外 |
|---|---|---|---|
| D1 | 数值字面量 | 状态码、超时、阈值、容量、端口、重试次数须提为 `static final` 常量 | `0` / `1` / `-1` / 循环下标 / `2` 作对半拆分 |
| D2 | 字符串字面量 | 错误消息模板、路径、Header 名、正则、业务关键字须常量化 | 单次使用的日志文案 |
| D3 | 状态与类型 | 禁止裸 `int`/字符串比较状态；必须用枚举（参考 `StatusEnum`） | —— |
| D4 | 常量命名与位置 | `UPPER_SNAKE_CASE`；就近定义于使用方类；语义自解释 | —— |
| D5 | 配置外置 | 可变参数（超时、密钥、地址、开关）不得硬编码，须走 `application.yml` / 环境变量 | —— |
| D6 | 前端魔法值 | 路由路径、API 前缀、状态映射、主题色 token 集中定义，禁止散落 | —— |

---

## 六、E 类 —— 命名、格式与工程规范（Convention）

| 编号 | 检查项 | 判定标准 |
|---|---|---|
| E1 | 包名 | 全小写 `com.bluewhale.{module}.{layer}`，层级语义清晰 |
| E2 | 类/方法/变量命名 | 类 `UpperCamelCase`；方法变量 `lowerCamelCase`；常量 `UPPER_SNAKE_CASE`；禁止拼音与无意义缩写 |
| E3 | 布尔命名 | `is/has/can/should` 前缀 |
| E4 | 分层对象命名 | `XxxController` / `XxxService(+Impl)` / `XxxMapper` / `XxxRequest` / `XxxVO` / `Xxx`（实体） |
| E5 | 注释与文档 | 类级 Javadoc（职责 + 作者）；公有方法说明关键契约；注释与代码一致、用中文；不写「废话注释」 |
| E6 | 格式 | 4 空格缩进；行宽 ≤ 120；无多余空行、无未使用 import；与既有风格一致 |
| E7 | 依赖注入 | 优先构造器注入（`@RequiredArgsConstructor`），禁止字段 `@Autowired` |
| E8 | 日志规范 | 占位符 `{}` 而非字符串拼接；级别恰当（error 带栈、debug 带上下文）；不重复打印 |
| E9 | 分层依赖 | Controller → Service → Mapper 单向；禁止反向依赖与跨层直连；跨服务走 HTTP/MQ |
| E10 | 统一契约 | 对外返回统一 `Result<T>` / `PageResult<T>`；业务异常抛 `BizException`；错误码用 `ErrorCode` |
| E11 | 事务与缓存注解 | 注解使用规范、边界清晰、无自调用失效 |
| E12 | 前端格式 | ESLint 0 error；Prettier 已格式化；TS 严格模式无 `any` 滥用；无 `console.log` 残留 |
| E13 | SQL 规范 | 表名/字段 snake_case；必备 `id / create_time / update_time / deleted`；建表含注释；索引命名 `idx_/uk_` |
| E14 | 脚本与 Dockerfile | 幂等可重跑；写操作有确认；镜像多阶段构建、非 root 运行、锁定基础镜像版本 |

---

## 七、严重级别与处置约定

| 级别 | 典型项 | 处置 |
|---|---|---|
| **P0 阻断** | A2 A3 A7 A8、B1 B2 B3 B5、明文密钥 | 立即修复，禁止提交 |
| **P1 严重** | A1 A6 A11、B6 B8 B9 B12 B13 B16、C1 超标 | 本批次内修复 |
| **P2 一般** | C2–C8、D1–D5、E1–E4 E7 E9 E10 | 本批次或紧邻批次修复 |
| **P3 建议** | E5 E6 E8 E13、可读性优化 | 记入待办，择机处理 |

---

## 八、检查报告模板

```markdown
# 代码检查报告 — <主题>

- 关联需求：REQ-xxx
- 变更范围：<文件清单>
- 检查日期：YYYY-MM-DD ｜ 检查人：BlueWhale
- 结论：通过 / 有条件通过 / 不通过

## 逐项检查结果

| 编号 | 检查项 | 结论 | 证据（文件:行） | 处置 |
|---|---|---|---|---|
| A2 | JWT 算法固定 | 通过 | JwtTokenProvider.java:62 | — |
| C1 | 圈复杂度 | 通过 | 最高 6（TokenBlacklist.java:xx） | — |
| D1 | 魔法值 | 不通过 → 已修复 | 原 `3600` 字面量 → `DEFAULT_TTL_SECONDS` | 已提常量 |

## 未通过项与修复记录
（逐条：问题 → 定位 → 修复方式 → 复验结论）

## 遗留（P3 建议项）
```

---

## 九、与自动化流水线的衔接

| 类别 | 自动化覆盖 | 工具 |
|---|---|---|
| C / D / E 类（结构、魔法值、格式） | 部分可自动 | Checkstyle（`config/checkstyle/whale-checkstyle.xml`） |
| B 类（漏洞模式） | 部分可自动 | SpotBugs（可选，二期接入） |
| A / B 类语义项（越权、脱敏、幂等） | 需人工 | 本清单人工评审 |
| E12（前端） | 可自动 | ESLint（已接入 `frontend-ci.yml`） |
| B11（依赖 CVE） | 可自动 | Maven Dependency Check（可选，二期） |

> 自动化能覆盖的项由 CI 兜底；**语义类问题（越权、脱敏、幂等、事务边界）必须人工按本清单核对**。

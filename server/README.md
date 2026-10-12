# server — 后端工作区

Maven 多模块 · Java 21 · Spring Boot 4.1。包名统一 `com.bluewhale.sts`。升级决策与破坏性变更清单见 `docs/decisions/0008`。

后端**按业务域切分，不按前端的两端切分** —— 管理端与用户端操作的是同一批业务对象、同一套状态机，按端切成两套等于把领域逻辑复制两份，改一条规则要改两处，两边还会逐渐跑偏。两端差异由接口面隔离。详见 `docs/decisions/0001`。

---

## 一、模块结构

**依赖方向只能自上而下**，禁止反向、禁止跨层跳跃：

```
sts-app  →  sts-server  →  sts-components  →  sts-common  →  sts-parent
业务应用     可部署服务      通用能力组件        技术底座        依赖管理
```

当前**已落地**的模块（未列出的层尚未创建，见下方说明）：

```
server/
├── pom.xml                        聚合根 —— 只列模块，不做依赖管理
├── sts-parent/                    父 POM —— 依赖版本统一治理（零代码）
├── sts-common/                    技术底座层
│   ├── sts-common-basic/              统一响应 · 错误码 · 业务异常
│   ├── sts-common-web/                全局异常处理 + CORS + Web 自动配置
│   └── sts-common-db/                 MyBatis-Plus 配置（分页 / 乐观锁 / 防全表更新）
├── sts-components/                通用能力组件层（ADR-0019 首次落地）
│   └── sts-components-audit/          @Audited 注解 + 审计目标上下文（只提供类型，不装配 Bean）
├── sts-server/                    服务层
│   ├── sts-server-log/                日志域服务（客户端技术日志上报与查询）
│   ├── sts-server-track/              行为事件域服务（埋点上报、查询、按事件名聚合）
│   ├── sts-server-auth/               权限域服务（身份 / 访问控制 / 认证 / 鉴权）
│   └── sts-server-audit/              审计域服务（拦截器自动产生、只读查询、按动作聚合）
└── sts-app/                       业务应用层
    └── sts-app-server/                唯一可启动进程（装配 + 健康检查 + 接口文档）
```

| 模块               | 打包 | 职责                                                                                                             | 依赖                                                                                                            |
| ------------------ | ---- | ---------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `bluewhale-server` | pom  | 聚合根，一次构建产出全部模块                                                                                     | —                                                                                                               |
| `sts-parent`       | pom  | 依赖版本与全局插件配置，不含任何代码                                                                             | `spring-boot-starter-parent`                                                                                    |
| `sts-common-basic` | jar  | `R<T>` 统一响应、`ResultCode` 错误码、`BusinessException`                                                        | **零依赖**                                                                                                      |
| `sts-common-web`   | jar  | `GlobalExceptionHandler`、CORS 配置 + 自动配置                                                                   | `sts-common-basic`、Spring Web（provided）、springdoc 注解、Bean Validation                                     |
| `sts-common-db`    | jar  | MyBatis-Plus 拦截器（分页 / 乐观锁 / 防全表更新）+ 自动配置                                                      | `sts-common-basic`、MyBatis-Plus                                                                                |
| `sts-server-log`   | jar  | 客户端**技术日志**的上报 / 分页 / 详情 / 链路 / 聚合                                                             | `sts-common-web`、`sts-common-db`                                                                               |
| `sts-server-track` | jar  | 客户端**行为事件**的上报 / 分页 / 详情 / 按事件名聚合                                                            | `sts-common-web`、`sts-common-db`                                                                               |
| `sts-server-auth`  | jar  | **权限域**：租户 / 用户 / 组织 / 角色 / 用户组 / 资源，认证（登录 / 登出 / 刷新）与鉴权（`@RequiresPermission`） | `sts-common-web`、`sts-common-db`、`sts-components-audit`（标 `@Audited` 用）                                    |
| `sts-server-audit` | jar  | **审计域**：拦截器自动产生审计记录、只读查询、按动作聚合                                                          | `sts-components-audit`、`sts-server-auth`（取操作人）、`sts-common-web`、`sts-common-db`                         |
| `sts-app-server`   | jar  | 可执行应用：装配各域服务、健康检查、`application.yml`                                                            | `sts-server-log`、`sts-server-track`、`sts-server-auth`、`sts-server-audit`、springdoc UI、H2 + MySQL + 达梦驱动（runtime）、Flyway |

> **为什么需要 `sts-app` 这一层**：`sts-server-*` 是"可部署的服务能力"，但**不是进程** —— 它们不知道自己的端口、数据源，也不知道要暴露哪些接口文档。必须有一个应用层把这些装配成一个能 `java -jar` 跑起来的东西。这层**只做装配，不写业务逻辑**：一旦出现业务代码，就该下沉到 `sts-server` 或 `sts-components`。
>
> **`sts-components` 层在 ADR-0019 首次落地**（`sts-components-audit`）。它成立的前提是
> 「一个类型被**两个**域同时需要，而下沉它不引入业务语义」：`@Audited` 由权限域（标注解）
> 与审计域（读注解）共用，谁依赖谁都会成环。刻意**不带 `-starter` 后缀** ——
> 该模块不装配任何 Bean，名字里的后缀是个承诺，不能随便给。
> 仍未创建的：文件、消息这类通用能力。依旧遵循「**模块按需创建，不预置空壳**」。

`sts-common-basic` 刻意保持**零依赖**：它处在依赖链最底层，任何引入都会扩散到全仓。

> **依赖"按需声明"的三个坑**（都是编译期看不出来、只在运行时炸的类型）：
>
> | 现象                                                   | 原因                                                                   | 处理                                          |
> | ------------------------------------------------------ | ---------------------------------------------------------------------- | --------------------------------------------- |
> | Mapper 扫不到、`SqlSessionFactory` 不存在              | MyBatis-Plus 的 starter 坐标没跟着 Boot 大版本走                       | Boot 4 用 `mybatis-plus-spring-boot4-starter` |
> | `ClassNotFoundException: PaginationInnerInterceptor`   | 3.5.9 起分页 / 防全表更新拦截器拆到了独立坐标                          | 显式引 `mybatis-plus-jsqlparser`              |
> | mysql profile 启动报 `Unsupported Database: MySQL 8.0` | Flyway 10 起各数据库支持拆成独立模块，`flyway-core` 只内置 H2 / SQLite | 显式引 `flyway-mysql`                         |
>
> 共同规律：**starter 不再传递引入全部能力，谁用谁声明**。详见 `docs/decisions/0009` 第 4 条。
>
> 还有一类更彻底的情况：**依赖根本不存在**。达梦就是如此 —— Flyway 官方没有达梦模块，
> 引什么坐标都不管用。这类问题没有"补一条依赖"的解法，见第六节与 `docs/decisions/0010`。

---

## 二、统一响应与错误码

所有接口返回同一形状：

```json
{
  "code": 0,
  "message": "成功",
  "data": { "application": "bluewhale-server", "status": "UP" },
  "timestamp": 1759999999999
}
```

**业务失败仍返回 HTTP 200。** HTTP 状态码描述的是传输层是否送达；业务失败（余额不足、重复提交）在传输层是成功的。若混用状态码表达业务语义，网关、CDN、重试中间件会把业务失败当成传输故障去重试，反而制造重复扣款一类的故障。前端统一判断 `code === 0`。

> **响应体里没有 `success` 字段，这是刻意的。** `R.isSuccess()` 标了 `@JsonIgnore`：Jackson 会把 `isXxx()` 视作 getter，不标注就会自动序列化出一个 `success` 键。多余字段本身无害，但会诱导前端写出 `if (res.success)` 这种与 `code` 并存的第二套判断标准，两套标准迟早不一致。`HealthControllerTest` 里有断言钉死这一点。

错误码为 5 位，编码规则 **HTTP 状态码(3 位) + 业务细分(2 位)**：

| 错误码  | 含义               | 抛出方式                                  |
| ------- | ------------------ | ----------------------------------------- |
| `0`     | 成功               | —                                         |
| `40000` | 请求参数错误       | `R.fail(ResultCode.BAD_REQUEST)`          |
| `40100` | 未认证或登录已过期 | 认证过滤器                                |
| `40300` | 没有访问权限       | 鉴权拦截器                                |
| `40400` | 资源不存在         | `BusinessException(ResultCode.NOT_FOUND)` |
| `40500` | 请求方法不被支持   | 框架                                      |
| `40900` | 资源状态冲突       | 重复创建、并发修改                        |
| `42900` | 请求过于频繁       | 限流组件                                  |
| `50000` | 系统内部错误       | 兜底处理，**不透出细节**                  |

**域内细分码从 `xx01` 起。** 上表里每类的 `00` 号是"通用码"，被各域共用；
某个域要表达更精确的语义时，占用同一 HTTP 类别下的 `01` 起的空位。
权限域（`sts-server-auth`）目前用到的：

| 错误码  | 含义             | 错误码  | 含义                   |
| ------- | ---------------- | ------- | ---------------------- |
| `40001` | 请求参数不合法   | `40104` | 令牌无效               |
| `40101` | 用户名或密码错误 | `40105` | 令牌已过期             |
| `40102` | 账号已被锁定     | `40106` | 会话已失效，请重新登录 |
| `40103` | 账号已停用       | `40301` | 没有该操作权限         |
| `40401` | 用户不存在       | `40901` | 用户名已存在           |
| `40402` | 租户不存在       | `40904` | 租户编码已存在         |
| `40403` | 角色不存在       | `40912` | 资源标识已存在         |

> 这样分段的直接好处：`40101` 一眼看出是"认证问题、责任在调用方"，
> `40904` 是"唯一性冲突"，`50000` 才是"服务端故障"。
> 反面例子是参照实现 `SystemTerminator`：它把认证错误放在 `004xxx` 段，
> 其中 `004000` 与公共 `PARAM_ERROR` 撞码，同一串数字在两层含义间摇摆。

### 在业务代码里怎么用

```java
// 成功
return R.ok(data);
return R.ok();

// 业务拒绝 —— 抛异常，交给 GlobalExceptionHandler 统一翻译
throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
throw new BusinessException(ResultCode.CONFLICT);   // 用枚举默认文案
```

**不要在 Controller 里手动 `try/catch` 再 `return R.fail(...)`** —— 那会把异常处理逻辑散到每个方法里，`GlobalExceptionHandler` 就没意义了。

日志级别是**有意区分**的：业务异常记 WARN（它是预期的拒绝，不该触发告警），未预期异常记 ERROR 并带完整堆栈。两者都用 ERROR 的话，告警会被正常业务拒绝淹没，最终没人再看告警。

---

## 三、模块内部如何组织

模块间按**业务域**切分，模块内按**特性（feature）**分包：

```
com.bluewhale.sts.log                    ← sts-server-log，包名用业务域（不带 server）
└── clientlog/                           ← 特性包
    ├── ClientLogController.java             接口层
    ├── ClientLogService.java                应用层
    ├── ClientLogMapper.java                 数据访问
    ├── ClientLogEntity.java / *VO.java      模型
    └── ...                                  约十个类，尚未到拆分阈值
```

**为什么不用 `controller/` `service/` `repository/` 作顶层分包**：改动一个功能要跨四个包来回跳；更要紧的是包之间没有任何边界——谁都可以从 `controller` 直接调 `repository` 绕过业务规则，编译器和评审都拦不住。按特性分包后，特性包本身就是天然的模块化候选点，将来拆服务只需整个包搬走。

单个特性超过约 10 个类时，再在其内部按 `domain` / `application` / `infrastructure` 细分。

### 日志域的几条硬规则

| 规则                                | 原因                                                                                                                                                                |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **逐条校验，不整批拒绝**            | 一条畸形数据若能让整批 500 条全被拒，客户端日志会成片丢失，且丢的正是"出事那一刻最需要的那批"。信封级问题（`app` 为空、批次超限）才整体 400 —— 那是调用方写错了代码 |
| **超长截断而不是丢弃**              | 超长的通常是消息夹带整段响应体，丢弃等于丢掉"这里出过事"这个事实，截断至少保留开头                                                                                  |
| **级别解析不兜底成 `INFO`**         | 把非法值悄悄变成合法值，上游的拼写错误就永远暴露不出来。识别不了 → 该条计入 `rejected`                                                                              |
| **排序用 `serverTime` + `id` 双键** | 同一毫秒内的多条记录若只按时间排序，翻页时顺序不稳定，会出现"某条在第 1 页和第 2 页都出现"                                                                          |
| **`clientTime` 仅作参考**           | 客户端时钟不可信；排序、时间窗、统计一律用服务端接收时间                                                                                                            |

**技术日志与审计日志是两条管道。** 本模块处理的是技术日志（排查故障用：量大、短保留、可采样、可丢弃）。审计日志（谁在何时对哪个资源做了什么）走另一条管道，且**权威来源必须是后端拦截器** —— 这些事实前端根本不知道，客户端上报的内容也不可信。见 `docs/decisions/0004` 与 `docs/decisions/0019`。

**行为事件**（`sts-server-track`）：回答"用户做了什么"，用于漏斗与留存分析。它与技术日志的差别不是"换个名字"，而是**逐项相反**：事件**不可采样**（采样会毁掉漏斗）、契约**极稳定**（改名即历史数据断裂）、量级高一到两个数量级。因此它是独立模块、独立表、独立接口前缀（`/api/events/client`），只在传输通道那一层与日志共用实现。见 `docs/decisions/0011`。

**审计日志**（`sts-server-audit`）：三条管道里**唯一不由前端产生**的一条，回答"谁在什么时候对什么做了什么"。它此前长期停在"已设计未实现"，2026-10-12 落地，见 `docs/decisions/0019`。至此**三条管道全部落地**，且各自独立模块独立表 —— 这正是那句"内容不同 ⇒ 独立模块独立表、通道相同 ⇒ 复用"的检验结果。

### 审计域的几条硬规则

| 规则                                                                             | 原因                                                                                                                                                                                    |
| -------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **没有写接口、没有写方法、没有 `deleted`、没有 `update_*`**                      | 不可篡改**不靠权限保护，靠结构上做不到**。`POST /api/audit/logs` 返回 `40500`；前端 SDK 的 `AuditApi` 同样只有三个读方法                                                               |
| **显式 `@Audited`，不自动拦所有非 GET**                                          | 自动拦会把登录 / 上报 / 健康检查全记进去，审计表被自己的上报灌满；而且 **HTTP 方法推不出"做了什么"**（`PUT` 既可能是改邮箱也可能是重置口令）                                          |
| **查询（`GET`）不记审计**                                                        | 审计记的是**变更**。要记"谁看了敏感数据"必须另起一条管道与另一张表（量级与保留期都不是一个数量级）                                                                                      |
| **审计拦截器 order 必须小于鉴权拦截器**（`WebInterceptorOrder.AUDIT = -100`）    | Spring 的 `HandlerExecutionChain` 只对**成功执行过 `preHandle`** 的拦截器回调 `afterCompletion`。排在鉴权之后 → 鉴权失败时审计永不执行 → **"越权尝试"这类最该留痕的记录静默丢失** |
| **`GlobalExceptionHandler` 每个 handler 都要 `ResponseOutcome.markFailure`**     | `afterCompletion` 收到的 `exception` **恒为 null**，而本仓"业务失败仍返回 HTTP 200" —— 两条加起来等于"失败看不出是失败"。漏调不报错，只让那一类失败被记成 `SUCCESS`                  |
| **写审计走 `REQUIRES_NEW`**                                                      | 业务事务回滚时审计不能跟着回滚 —— 而"操作被拒绝"恰恰最需要留痕。异常处理必须在**调用方**（self-invocation 会让 `@Transactional` 静默失效）                                            |
| **三套标识不可混用**                                                             | 资源 `auth:user`（`AuthResources`）≠ 权限码 `auth:user:create`（`AuthPermissions`）≠ 动作 `auth.user.create`（`AuthAuditActions`）。把资源填成权限码 → 按资源过滤永远查空且**不报错** |
| **只有服务端时间；匿名不产生审计**                                               | 审计不由客户端产生，故不存在双时间戳问题。匿名请求无操作人可归责，且公网扫描会灌满表；**已认证但无权限的必须记**（有明确操作人，且那正是最该留痕的一类）                              |
| **`AuditRecorder` 写库失败只记 WARN，不重试、不外抛**                            | 接受"极少数记录会丢"：记审计失败不能连累业务响应                                                                                                                                        |

> **`sts-components-audit` 刻意不带 `-starter` 后缀** —— 它只提供 `@Audited` 注解与 `AuditContext`，
> **不装配任何 Bean**；真正装配拦截器的是审计域。名字里的后缀是个承诺。
>
> **一处已记录的同层依赖**：`sts-server-audit` → `sts-server-auth`（只读 `AuthContext` 与权限码，
> 不调用其服务）。与 `architecture/后端分层与依赖方向.md` 的"同层 ✗"不符，理由与触发条件见 `docs/decisions/0019`。

### 权限域的几条硬规则

`sts-server-auth` 是本仓第一个"从表到接口"完整闭环的服务，设计取舍见 `docs/decisions/0016`。

| 规则                                                               | 原因                                                                                                                                    |
| ------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| **表前缀 `bw_auth_`，主键 `BIGINT` 自增**                          | 与 `bw_client_log` 同一形态；对外稳定标识交给各表的 `*_code` 业务编码                                                                   |
| **逻辑删除统一 `deleted`：0 正常 / 1 已删除**                      | 由 `@TableLogic` 承接，零配置。参照实现里不同表的 0/1 含义相反，是必须修掉的缺陷                                                        |
| **用户名在租户内唯一**（`uk(tenant_id, username)`），不是全库唯一  | 多租户下不同租户可以有同名账号                                                                                                          |
| **口令只存哈希，出参永不包含任何口令字段**                         | `UserVO` 手写逐字段映射，而非"把实体拷一遍"——后者会让实体新增字段自动泄露到响应里                                                       |
| **不区分"账号不存在"与"口令错误"**                                 | 都回 `40101`。区分开来等于提供一个账号枚举接口                                                                                          |
| **先验口令、再透露账号状态**                                       | 锁定 / 停用等提示只在口令正确后给出，否则同样构成信息泄露                                                                               |
| **认证（401）与授权（403）分开**                                   | 前端据此决定"跳登录页"还是"提示无权限"                                                                                                  |
| **刷新令牌只存摘要 + 轮换 + 重放检测**                             | 已作废的 jti 被再次使用即视为泄露，撤销该用户全部刷新令牌                                                                               |
| **认证上下文只有一套**（`AuthContext`），且由过滤器 `finally` 清理 | 参照实现有两套同名上下文（一套是空实现）；不清理 ThreadLocal 会在复用线程上串号                                                         |
| **鉴权入口只有一处**：`@RequiresPermission` + `AuthInterceptor`    | 判定逻辑散落时，漏洞往往出在"某个接口只查了是否登录"的那一处                                                                            |
| **`jwt.secret` 生产必须用环境变量覆盖**（`BW_JWT_SECRET`）         | 配置里的默认值是**只给本地开发用的占位符**，而且已进版本库 ⇒ 对全世界公开。它属密钥级配置（`docs/decisions/0003`），HS512 建议 ≥64 字节 |

> **未做**：组织 / 角色 / 用户组 / 资源的管理接口（表与实体已落地）、数据权限判定、时态授权过滤、
> 验证码 / 短信 / 多因子、网关侧令牌校验。逐项见 `docs/decisions/0016` 末节。
> （原先列的"行为审计"已于 2026-10-12 落地，但**不在权限域** —— 见 `docs/decisions/0019`。）

---

## 四、新增一个模块

1. 确定它属于哪一层（决定 `groupId`，见 `docs/decisions/0005`）
2. 建立目录与 `pom.xml`，`<parent>` 指向同层的聚合 POM，**不要写 `<version>`**（从 `sts-parent` 继承）
3. 若是自制模块间的依赖，在 `sts-parent` 的 `dependencyManagement` 里登记坐标
4. 把新模块加进所属聚合 POM 的 `<modules>`
5. 同步更新：本文件模块表 · 根 `README.md` 结构图 ·
   `docs/architecture/后端分层与依赖方向.md` 第六节的「当前进度」表
   （这张表最容易烂 —— 它只是速览，没人主动看就没人改）

---

## 五、构建与运行

```bash
cd server

mvn clean install -DskipTests                      # 构建全部模块
mvn clean install                                  # 含测试（当前 80 例）

# 启动应用（在 server/ 目录下执行，需要 JDK 21）—— 默认走 h2，零外部依赖
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar
# 连本机 MySQL（数据落在磁盘上）—— 凭据走环境变量
BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=mysql
# 连达梦 DM8（国产库）—— 表结构需先执行脚本，见第六节
BW_DB_USERNAME=<用户> BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=dameng
# 需要换端口时（命令行参数优先级最高）
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar --server.port=18080
```

服务默认监听 `8080`。

```bash
curl http://localhost:8080/api/health
```

接口文档（springdoc）：`http://localhost:8080/swagger-ui.html`。健康检查的响应里带 `profile` 字段，一眼就能看出当前连的是哪个库。

全部接口的清单与契约见 `docs/api/README.md`；前端配套的演示页见 `web/platform/platform-log-console`（日志）、`web/platform/platform-event-console`（行为事件）与 `web/platform/platform-audit-console`（审计）。

---

## 六、数据源与数据库迁移

数据源由 **profile** 决定，不是改文件：用哪个库是启动参数，显式出现在命令行与健康检查里。决策与踩坑清单见 `docs/decisions/0009`。

| profile          | 数据源                                  | 适用           | 数据                   | H2 控制台                                 |
| ---------------- | --------------------------------------- | -------------- | ---------------------- | ----------------------------------------- |
| `h2`（**默认**） | `jdbc:h2:mem:bluewhale`（`MODE=MySQL`） | 开发、演示、CI | **进程内存，重启即失** | 开启 `/h2-console`（用户 `sa`，密码留空） |
| `mysql`          | `jdbc:mysql://127.0.0.1:3306/bluewhale` | 本地联调       | 磁盘，重启后仍在       | 关闭                                      |
| `dameng`         | `jdbc:dm://127.0.0.1:5236`              | 信创环境       | 磁盘                   | 关闭                                      |

**默认必须是 h2** —— 它保证"clone 下来不装任何数据库就能跑"，`HealthControllerTest` 里有断言钉住这一点。

### 表结构怎么建：H2 / MySQL 走 Flyway，达梦走交付脚本

```
sts-server-log/src/main/resources/db/migration/
├── h2/V1__create_bw_client_log.sql       H2 方言
├── mysql/V1__create_bw_client_log.sql    MySQL 方言
└── dameng/V1__create_bw_client_log.sql   达梦方言（不由 Flyway 执行，见下）

sts-server-track/src/main/resources/db/migration/
├── h2/V2__create_bw_client_event.sql     H2 方言
├── mysql/V2__create_bw_client_event.sql  MySQL 方言
└── dameng/V2__create_bw_client_event.sql 达梦方言（不由 Flyway 执行，见下）

sts-server-auth/src/main/resources/db/migration/
├── h2/V3__create_bw_auth_tables.sql      身份 / 访问控制 / 会话共 12 张表
├── mysql/V3__create_bw_auth_tables.sql
└── dameng/V3__create_bw_auth_tables.sql

sts-server-audit/src/main/resources/db/migration/
├── h2/V4__create_bw_audit_log.sql        审计流水表 + 5 个索引
├── mysql/V4__create_bw_audit_log.sql
└── dameng/V4__create_bw_audit_log.sql
```

> **版本号是"全仓唯一"，不是"每个模块各从 V1 开始"。**
> `spring.flyway.locations` 是全模块共享的 `classpath:db/migration/<vendor>`，
> 因此同一个 location 下出现两个 `V1` 会让 Flyway 在**启动阶段**直接失败：
> `Found more than one migration with version 1`。
> 事件表排到 `V2` 正是这个原因（日志域已占用 `V1`）。
> **新增建表脚本时取全仓当前最大号 + 1。** 详见 `docs/decisions/0009` 的「补充」章节。

- 迁移脚本由**领域模块自带**（谁拥有表结构谁写脚本），`spring.flyway.locations` 按 profile 指向对应厂商目录
- 各写各的**原生方言**，不追求一份通用 DDL —— 硬凑通用版只能取交集，MySQL 的列注释、存储引擎、字符集排序规则就全写不了
- 用 `flyway_schema_history` 表保证"只执行一次"，因此脚本里**不写** `IF NOT EXISTS`：那样会把"表已存在但结构不对"静默吞掉

```bash
# 新增表结构：追加脚本，不要改历史脚本
#   db/migration/<vendor>/V2__加个字段.sql
```

mysql profile 需要**先建好库**（Flyway 只建表、不建库）：

```sql
CREATE DATABASE bluewhale DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

**达梦没有 Flyway 支持**，该 profile 下 `flyway.enabled=false`，表结构走**交付脚本**：

```bash
BW_DB_PASSWORD=xxx bash scripts/init-dameng.sh     # 需要达梦客户端 disql 在 PATH 中
# 或由 DBA 直接执行 db/migration/dameng/V1__create_bw_client_log.sql
```

为什么不做自研 Flyway 扩展（约 300–400 行、全部绑定 Flyway 内部 API、升级即可能破损），
以及达梦在 DDL 上的方言差异清单，见 `docs/decisions/0010`。

> **迁移脚本一旦在真实环境执行过就不可修改。** `validate-on-migrate: true` 会因校验和不匹配而拒绝启动 —— 这是特性不是麻烦：迁移脚本是历史，改了历史就该新增一条，不是覆盖它。本地开发可以重建库绕过；有数据的环境必须追加新版本。

### 换到真实库后没有账号可登录

种子账号（`admin` / `auditor`）**只在 h2 profile 自动种**，这是有取舍的：

| profile            | 为什么这样定                                                                                                                     |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------- |
| `h2`               | 每次启动都是空库，**不自动种就一个账号都没有可登录** —— 而死锁在于"建账号的接口本身要求已登录"。种一套默认账号解开这个引导环     |
| `mysql` / `dameng` | 数据留得住，不需要每次补；而且**真实库不该被应用每次启动塞进一个固定口令的管理员**（默认口令写在 `AuthProperties` 里，等于公开） |

所以换库后要**显式种一次**，之后它就一直在了：

```bash
BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=mysql \
    --bluewhale.auth.seed-admin.enabled=true --bluewhale.auth.seed-auditor.enabled=true
```

- 种子是**幂等**的（`ensure*` 语义：缺什么补什么、已存在就跳过），重复执行没有副作用
- `seed-auditor` **依赖** `seed-admin`：只读账号挂在 admin 种下的租户与资源上，少了 admin 它没有可挂的东西，开了也不生效
- 不想在任何库留固定口令的账号，就别开这两个开关 —— 用已有管理员账号调 `/api/auth/users`、`/api/auth/tenants` 建号

两个口令以 `AuthProperties.SeedAdmin` / `SeedAuditor` 的默认值为准，基座登录页的开发提示里也列了一份，**两处必须一致**。

### 三条必须先查的规矩

1. **新增会落库的字段前，先查一遍 MySQL 保留字。** `release`（发布版本号）是自然的领域命名，但 `RELEASE` 是 MySQL 8 的保留字，而 **H2 不认它** —— 同一个列名在 H2 上一切正常、在 MySQL 上 `ERROR 1064`。这是最难查的一类差异。
2. **别用 `@TableField` 把列名映射成另一个名字。** MyBatis-Plus 发现"列名与属性名不一致"会**自动补 `列名 AS 属性名` 的别名**，别名若撞了保留字照样报错。正确做法是让属性名与列名同构（`appRelease → app_release`，与 `traceId → trace_id` 一致），不产生别名。`ClientLogEntityMappingTest` 直接断言生成的 SQL 里不出现 `AS release`。
3. **新增字段也要查一遍达梦保留字 —— 两个库的结论不可互推。** `RELEASE` 是 MySQL 8 的保留字，达梦却完全不认它；反过来 `LEVEL` 是达梦的关键字（属非保留，可作列名），MySQL 也不管。查法：`SELECT KEYWORD FROM V$RESERVED_WORDS WHERE RESERVED='Y'`。`bw_client_log` 的 14 个列名已逐列核对过、无冲突，详见 `docs/decisions/0010`。审计表 `bw_audit_log` 的 16 个列名落地时也按同样口径核对过（`action` / `resource` / `result` 在 MySQL 8 **都不是**保留字）。

> 为什么这条要靠测试而不是靠跑用例？因为**默认跑在 H2 上，而 H2 不认这个保留字** —— 所有接口测试都会是绿的，直到换 MySQL 才炸。这类差异只能对生成物本身做断言。

---

## 七、环境要求

| 工具  | 版本    | 说明                                                                                      |
| ----- | ------- | ----------------------------------------------------------------------------------------- |
| JDK   | **21+** | 由 `maven-enforcer-plugin` 在构建期强制（`requireJavaVersion [21,)`），任何模块上都会先跑 |
| Maven | 3.6.3+  | Boot 4 的最低要求，同样由 enforcer 检查（本机已在 3.8.1 验证）                            |

> **版本不符时**：构建会在 enforcer 阶段直接失败，并提示 `JAVA_HOME` 的设置方法——
> 这是有意的：与其让编译器报一句含混的 `release version 21 not supported`
> （不同 Maven 版本表现还不一致），不如一开始就说清"当前是几、需要几、怎么改"。
>
> ```bash
> JAVA_HOME=/path/to/jdk-21 mvn clean install
> ```
>
> 脚本同样受保护：`scripts/export-openapi.sh` 会先检查 java 版本，低于 21 直接拒绝。

### 版本一览（由 `sts-parent` 统一收口）

| 组件              | 版本                     | 备注                                                                                                   |
| ----------------- | ------------------------ | ------------------------------------------------------------------------------------------------------ |
| Spring Boot       | 4.1.1                    | Spring Framework 7.0.9 · Tomcat 11 · Jakarta EE 11 · JUnit 6                                           |
| springdoc-openapi | 3.1.1                    | 3.x 才支持 Boot 4；契约规范为 **OpenAPI 3.1**                                                          |
| MyBatis-Plus      | 3.5.17                   | 坐标 `mybatis-plus-spring-boot4-starter`                                                               |
| Flyway            | 12.4.0                   | `flyway-core` + **`flyway-mysql`**（后者别漏，见第一节三个坑）                                         |
| MySQL 驱动        | 9.7.0                    | 坐标 `com.mysql:mysql-connector-j`，`runtime` 范围                                                     |
| 达梦驱动          | 8.1.3.140                | 坐标 `com.dameng:DmJdbcDriver18`，`runtime` 范围。**Boot 的 BOM 不纳管**，版本由 `sts-parent` 手工收口 |
| Jackson           | 3.x（`tools.jackson.*`） | 注解仍是 `com.fasterxml.jackson.annotation`，包名没变                                                  |

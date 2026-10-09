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
├── sts-server/                    服务层
│   └── sts-server-log/                日志域服务（客户端日志上报与查询）
└── sts-app/                       业务应用层
    └── sts-app-server/                唯一可启动进程（装配 + 健康检查 + 接口文档）
```

| 模块 | 打包 | 职责 | 依赖 |
|------|------|------|------|
| `bluewhale-server` | pom | 聚合根，一次构建产出全部模块 | — |
| `sts-parent` | pom | 依赖版本与全局插件配置，不含任何代码 | `spring-boot-starter-parent` |
| `sts-common-basic` | jar | `R<T>` 统一响应、`ResultCode` 错误码、`BusinessException` | **零依赖** |
| `sts-common-web` | jar | `GlobalExceptionHandler`、CORS 配置 + 自动配置 | `sts-common-basic`、Spring Web（provided）、springdoc 注解、Bean Validation |
| `sts-common-db` | jar | MyBatis-Plus 拦截器（分页 / 乐观锁 / 防全表更新）+ 自动配置 | `sts-common-basic`、MyBatis-Plus |
| `sts-server-log` | jar | 客户端日志的上报 / 分页 / 详情 / 链路 / 聚合 | `sts-common-web`、`sts-common-db` |
| `sts-app-server` | jar | 可执行应用：装配各域服务、健康检查、`application.yml` | `sts-server-log`、springdoc UI、H2（runtime） |

> **为什么需要 `sts-app` 这一层**：`sts-server-*` 是"可部署的服务能力"，但**不是进程** —— 它们不知道自己的端口、数据源，也不知道要暴露哪些接口文档。必须有一个应用层把这些装配成一个能 `java -jar` 跑起来的东西。这层**只做装配，不写业务逻辑**：一旦出现业务代码，就该下沉到 `sts-server` 或 `sts-components`。
>
> **规划中但尚未创建的层**：`sts-components/`（通用能力组件，如后续的审计、文件）。
> 遵循「**模块按需创建，不预置空壳**」——空模块会让构建变慢、索引变重，而且"以后放什么"的意图很快就没人记得。
> 创建时无需改动任何已有模块的坐标。

`sts-common-basic` 刻意保持**零依赖**：它处在依赖链最底层，任何引入都会扩散到全仓。

> **MyBatis-Plus 的两个坑**：
> ① starter 坐标跟着 Spring Boot 大版本走（Boot 4 → `mybatis-plus-spring-boot4-starter`）。用错 starter 的症状是**自动配置静默失效**（Mapper 扫不到、`SqlSessionFactory` 不存在），不报编译错误，最难排查。
> ② 依赖 JSqlParser 的拦截器（分页、防全表更新）在独立坐标 `mybatis-plus-jsqlparser`，漏引会在**运行时**才报 `ClassNotFoundException: PaginationInnerInterceptor`，编译期看不出来。

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

| 错误码 | 含义 | 抛出方式 |
|--------|------|----------|
| `0` | 成功 | — |
| `40000` | 请求参数错误 | `R.fail(ResultCode.BAD_REQUEST)` |
| `40100` | 未认证或登录已过期 | 认证过滤器 |
| `40300` | 没有访问权限 | 鉴权拦截器 |
| `40400` | 资源不存在 | `BusinessException(ResultCode.NOT_FOUND)` |
| `40500` | 请求方法不被支持 | 框架 |
| `40900` | 资源状态冲突 | 重复创建、并发修改 |
| `42900` | 请求过于频繁 | 限流组件 |
| `50000` | 系统内部错误 | 兜底处理，**不透出细节** |

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

| 规则 | 原因 |
|------|------|
| **逐条校验，不整批拒绝** | 一条畸形数据若能让整批 500 条全被拒，客户端日志会成片丢失，且丢的正是"出事那一刻最需要的那批"。信封级问题（`app` 为空、批次超限）才整体 400 —— 那是调用方写错了代码 |
| **超长截断而不是丢弃** | 超长的通常是消息夹带整段响应体，丢弃等于丢掉"这里出过事"这个事实，截断至少保留开头 |
| **级别解析不兜底成 `INFO`** | 把非法值悄悄变成合法值，上游的拼写错误就永远暴露不出来。识别不了 → 该条计入 `rejected` |
| **排序用 `serverTime` + `id` 双键** | 同一毫秒内的多条记录若只按时间排序，翻页时顺序不稳定，会出现"某条在第 1 页和第 2 页都出现" |
| **`clientTime` 仅作参考** | 客户端时钟不可信；排序、时间窗、统计一律用服务端接收时间 |

**技术日志与审计日志是两条管道。** 本模块处理的是技术日志（排查故障用：量大、短保留、可采样、可丢弃）。审计日志（谁在何时对哪个资源做了什么）走另一条管道，且**权威来源必须是后端拦截器** —— 这些事实前端根本不知道，客户端上报的内容也不可信。见 `docs/decisions/0004`。

---

## 四、新增一个模块

1. 确定它属于哪一层（决定 `groupId`，见 `docs/decisions/0005`）
2. 建立目录与 `pom.xml`，`<parent>` 指向同层的聚合 POM，**不要写 `<version>`**（从 `sts-parent` 继承）
3. 若是自制模块间的依赖，在 `sts-parent` 的 `dependencyManagement` 里登记坐标
4. 把新模块加进所属聚合 POM 的 `<modules>`
5. 同步更新：本文件模块表 · 根 `README.md` 结构图

---

## 五、构建与运行

```bash
cd server

mvn clean install -DskipTests                      # 构建全部模块
mvn clean install                                  # 含测试（当前 22 例）

# 启动应用（在 server/ 目录下执行，需要 JDK 21）
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar
# 或用 Maven 插件
mvn -pl :sts-app-server spring-boot:run
# 需要换端口时（命令行参数优先级最高）
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar --server.port=18080
```

服务默认监听 `8080`。数据源是 **H2 内存库**（`MODE=MySQL`），零外部依赖，clone 下来就能跑；换生产库只改 `application.yml` 的数据源几行 + 驱动依赖，代码不动。

```bash
curl http://localhost:8080/api/health
```

接口文档（springdoc）：`http://localhost:8080/swagger-ui.html`；H2 控制台：`/h2-console`（JDBC URL 填 `jdbc:h2:mem:bluewhale`，用户 `sa`，密码留空）。

日志接口的完整清单与契约见 `docs/api/README.md`；前端配套的演示页见 `web/platform/platform-log-console`。

---

## 六、环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | **21+** | 由 `maven-enforcer-plugin` 在构建期强制（`requireJavaVersion [21,)`），任何模块上都会先跑 |
| Maven | 3.6.3+ | Boot 4 的最低要求，同样由 enforcer 检查（本机已在 3.8.1 验证） |

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

| 组件 | 版本 | 备注 |
|------|------|------|
| Spring Boot | 4.1.1 | Spring Framework 7.0.9 · Tomcat 11 · Jakarta EE 11 · JUnit 6 |
| springdoc-openapi | 3.1.1 | 3.x 才支持 Boot 4；契约规范为 **OpenAPI 3.1** |
| MyBatis-Plus | 3.5.17 | 坐标 `mybatis-plus-spring-boot4-starter` |
| Jackson | 3.x（`tools.jackson.*`） | 注解仍是 `com.fasterxml.jackson.annotation`，包名没变 |

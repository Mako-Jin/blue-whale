# server — 后端工作区

Maven 多模块 · Java 21 · Spring Boot 3.3。包名统一 `com.bluewhale.sts`。

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
│   └── sts-common-web/                全局异常处理 + Web 自动配置
└── sts-server/                    服务层
    └── sts-server-system/             系统域服务
```

| 模块 | 打包 | 职责 | 依赖 |
|------|------|------|------|
| `bluewhale-server` | pom | 聚合根，一次构建产出全部模块 | — |
| `sts-parent` | pom | 依赖版本与全局插件配置，不含任何代码 | `spring-boot-starter-parent` |
| `sts-common-basic` | jar | `R<T>` 统一响应、`ResultCode` 错误码、`BusinessException` | **零依赖** |
| `sts-common-web` | jar | `GlobalExceptionHandler` + 自动配置 | `sts-common-basic`、Spring Web（provided） |
| `sts-server-system` | jar | 系统域服务，当前提供健康检查 | `sts-common-web`、Spring Web |

> **规划中但尚未创建的层**：`sts-components/`（通用能力组件，如后续的审计、文件）、`sts-app/`（业务应用）。
> 遵循「**模块按需创建，不预置空壳**」——空模块会让构建变慢、索引变重，而且"以后放什么"的意图很快就没人记得。
> 创建时无需改动任何已有模块的坐标。

`sts-common-basic` 刻意保持**零依赖**：它处在依赖链最底层，任何引入都会扩散到全仓。

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
com.bluewhale.sts.system
├── SystemApplication.java
└── health/                          ← 特性包
    ├── HealthController.java            接口层
    ├── HealthService.java               应用层
    └── HealthVO.java                    出参模型
```

**为什么不用 `controller/` `service/` `repository/` 作顶层分包**：改动一个功能要跨四个包来回跳；更要紧的是包之间没有任何边界——谁都可以从 `controller` 直接调 `repository` 绕过业务规则，编译器和评审都拦不住。按特性分包后，特性包本身就是天然的模块化候选点，将来拆服务只需整个包搬走。

单个特性超过约 10 个类时，再在其内部按 `domain` / `application` / `infrastructure` 细分。

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
mvn clean install                                  # 含测试

# 启动系统域服务（在 server/ 目录下执行）
mvn -pl :sts-server-system spring-boot:run
# 或先打包再运行
java -jar sts-server/sts-server-system/target/sts-server-system-0.0.1-SNAPSHOT.jar
```

服务默认监听 `8080`。

```bash
curl http://localhost:8080/api/health
```

---

## 六、环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | **21** | `sts-parent` 声明 `maven.compiler.release=21` |
| Maven | 3.8+ | 已在 3.8.1 上验证 |

> **若构建报版本错误**，先确认 Maven 实际使用的是 JDK 21 —— `mvn -v` 会打印 `Java version`。
> 机器上同时存在多个 JDK 时，`JAVA_HOME` 可能指向旧版本，需显式指定：
>
> ```bash
> JAVA_HOME=/path/to/jdk-21 mvn clean install
> ```

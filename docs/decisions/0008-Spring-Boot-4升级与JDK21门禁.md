# 0008. 升级 Spring Boot 4 与 JDK 21 构建门禁

- **状态**：已采纳
- **日期**：2026-10-09
- **决策者**：舰长

## 背景

后端起步时（ADR-0005）落在 Spring Boot 3.3.5。到 2026 年 10 月：

- 3.3.x 的 OSS 支持 2025-06 就已结束，连 3.x 的最后一支 3.5.x 也于 2026-06 停止 OSS 支持；
- Boot 4.0 于 2025-11 GA（Spring Framework 7、Tomcat 11、Jackson 3、Jakarta EE 11），
  4.1 为当前稳定线（2026-06 GA，OSS 支持到 2027-07）；
- 4.0.x 的 OSS 支持只到 2026-12 —— 选它等于两个月后又要升一次。

与此同时，"JDK 21"一直只是 `sts-parent` 里的一行属性：编译目标确实是 21，
但**没有任何机制阻止有人用 JDK 17 跑 `mvn`**，报错会是编译器的
`release version 21 not supported`——信息含混，且不同 Maven 版本表现不一。

## 决策

### 1. 目标版本：Boot 4.1.1，一步到位

不经过 4.0 中转。理由：3.5 → 4.0 与 4.0 → 4.1 的迁移动作高度重叠，
分两步只多付一次验证成本；而 4.1 的支持窗口明显更长。

| 依赖 | 旧 | 新 | 说明 |
|------|----|----|------|
| `spring-boot-starter-parent` | 3.3.5 | **4.1.1** | Spring Framework 7.0.9 · Tomcat 11.0.24 · Jakarta EE 11 |
| Java 基线 | 21（仅属性） | 21（**enforcer 强制**） | Boot 4 基线是 17，项目选择 21（当前 LTS） |
| `springdoc-openapi` | 2.6.0 | **3.1.1** | 2.x 配 Boot 4 启动即失败（`WebMvcProperties` 换包），必须 3.x |
| MyBatis-Plus | 3.5.9（`boot3-starter`） | **3.5.17**（`boot4-starter`） | starter 坐标跟着 Boot 大版本走，见下 |
| H2 / Lombok / JUnit | — | 由 Boot 4 管理 | JUnit 5 → **JUnit 6**（Jupiter 6.0.3），API 无感 |

### 2. starter 坐标三处改名

| 旧 | 新 | 原因 |
|----|----|------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | 4.1 起旧名 deprecated；新名与 WebFlux 明确区分 |
| `mybatis-plus-spring-boot3-starter` | `mybatis-plus-spring-boot4-starter` | 用错 starter 的症状是**自动配置静默失效**（Mapper 扫不到），不报编译错误，最难排查 |
| `spring-boot-starter-test`（含 MockMvc） | + `spring-boot-starter-webmvc-test` | 测试设施按技术栈拆分，`@AutoConfigureMockMvc` 挪进了独立模块 |

### 3. Jackson 2 → 3：包名迁移，注解坐标不动

Jackson 3 的 split 设计：

- `jackson-core` / `jackson-databind` → **`tools.jackson.*`**（包名与 groupId 都变）
- `jackson-annotations` → **保持 `com.fasterxml.jackson.core` 与 `com.fasterxml.jackson.annotation` 包名**，
  且继续用 2.x 版本序列 —— 注解必须被 Jackson 2/3 共用

因此：`ClientLogService` 的 `ObjectMapper` / `TypeReference` 改 import 到 `tools.jackson.*`；
`R` 上的 `@JsonIgnore` **不用改**。

另一个语义变化：`JsonProcessingException`（checked）变为 `JacksonException`（unchecked）。
现有 `catch (Exception)` 的容错写法不受影响，但新代码不应再为它写 `throws`。

### 4. JDK 21 变成构建期强制（enforcer）

`sts-parent` 新增 `maven-enforcer-plugin`（继承给所有子模块，单独 `mvn -pl :sts-app-server` 也受保护）：

- `requireJavaVersion [21,)` —— 用错 JDK 时**立即失败**并给出可执行的指引；
- `requireMavenVersion [3.6.3,)` —— Boot 4 的最低要求。

**一个实现陷阱**：失败提示里不要写 `${java.version}`。它会被 Maven 的属性插值先吃掉，
取到的是本 POM 里定义的那行 `21`，而不是运行 JVM 的版本——于是提示会自相矛盾地显示
"要求 21，当前 21"。真正检测到的版本由规则自己判断，消息里只写动作。

### 5. 契约升级到 OpenAPI 3.1 的连带影响

springdoc 3.x 生成的契约从 3.0.1 变为 **3.1.0**，两处对前端可见：

1. **信息更全**：类级 JavaDoc 进入 schema 的 `description`，`@Size` 等约束被表达为
   `minLength`/`maxLength` —— 契约评审的价值变高了；
2. **"任意值"字段的类型从 `Record<string, never>` 变为 `unknown`**：
   前者语义是"不允许有任何字段的对象"（错误），后者又过宽（函数也能通过编译，
   而 `JSON.stringify` 遇到它会抛错，让整批上报失败）。`common-api` 的 `JsonValue`
   收紧层因此**保留**，理由已更新到代码注释；同时它暴露了一个一直存在的类型裂缝——
   分页结果 `records` 的元素与导出的 `ClientLogVO` 不是同一个类型——已用
   `WithJsonRecords` 修复，并用 `expectTypeOf` 断言钉住。

## 验证

- 10/10 模块构建通过，22 个后端测试全绿（含真实穿透 Controller → Service → Mapper → H2 的 API 测试）
- JDK 17 跑 `mvn validate` 被 enforcer 拦截，提示准确；`export-openapi.sh` 同样加了门禁
- 应用启动 3.3s，健康检查 / 上报 / 链路查询 / 聚合 / `/v3/api-docs` / Swagger UI 全部正常
- 契约重新导出，前端类型重新生成，`common-api`（11 测试）与 demo 应用（25 测试）全绿
- 真实浏览器（CDP）端到端：点击产生日志 → 批量上报 → 落库 → 服务端表格出现 → 链路可查

## 后果

**正面**

- 回到官方支持窗口内，安全补丁不再断供
- 契约表达力提升（description / 校验约束进契约）
- "用错 JDK"从含混的编译错误变成一句可执行的中文提示
- Tomcat 11 + 虚拟线程能力就位（`spring.threads.virtual.enabled` 打开即用，默认仍关闭——
  本项目的接入等有真实并发诉求时再评估）

**负面 / 代价**

- Jackson 3 的资料存量少，遇到问题要同时查两套文档（classpath 上 Jackson 2 仍因
  springdoc / MyBatis 传递存在，同一个工程里两套 ObjectMapper 并存是常态，import 时要认准 `tools.jackson`）
- MyBatis-Plus 的 boot4-starter 起步于 3.5.13，版本演进节奏与 Boot 官方解耦，升级时要单独确认
- JUnit 6、Servlet 6.1 的基线变化对将来的测试与容器部署有隐性要求

**后续约束**

- 后端构建一律走 JDK 21+；任何"本机只有 17"的环境先装 21，不要绕过 enforcer
- 升级 Boot 大版本时：starter 坐标（`web→webmvc`、`boot3→boot4`）、springdoc 大版本、
  MyBatis-Plus starter 坐标**三处必须一起动**，漏一处就是静默失效
- 新代码不再 import `com.fasterxml.jackson.databind.*`；注解（`@JsonIgnore` 等）继续用
  `com.fasterxml.jackson.annotation`

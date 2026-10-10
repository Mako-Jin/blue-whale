# AGENTS.md — 给 AI 助手的项目约定

本文件是 AI 助手在本仓库工作时的**硬性约定**。开始任何任务前先读它。

## 必读文档

| 文档 | 内容 |
|------|------|
| `README.md` | 项目结构、六条铁律、技术栈 |
| `docs/README.md` | 文档规范（「改动 → 需同步哪些文档」对照表） |
| `CONTRIBUTING.md` | 环境、Git 钩子、提交规范、契约更新流程 |
| `docs/decisions/` | 已定架构决策（ADR），动手前先看有没有相关约束 |
| `docs/architecture/` | 分层与依赖方向的落地细则（后端改模块前必看） |

## 硬性要求

1. **每完成一步实质性改动，同步更新对应文档**——对照表见 `docs/README.md` 第二节。文档与代码走同一次提交。
2. **提交信息必须符合 Conventional Commits**（`commit-msg` 钩子会拦截）。
3. **不要提前空建**未被使用的包 / 模块——按业务需要创建。
4. **不混用两端构建体系**：前端只用 pnpm，后端只用 Maven。
5. **不手写前端 SDK**——它由 `docs/api` 的 OpenAPI 契约生成。改后端接口后必须跑 `scripts/export-openapi.sh` + `pnpm --filter common-api gen:api`，并与代码同一次提交（流程见 `CONTRIBUTING.md` 第七节）。
6. **前端调接口一律走 `common-api`**，禁止直接 `fetch`；错误按 `HttpError.kind` 分支处理，禁止匹配错误文案。
7. **后端只用 JDK 21+** —— enforcer 已在构建期强制（低于 21 会直接失败并给出提示），但**运行 `java -jar` / 脚本时没有这道门**：本机 `JAVA_HOME` 可能指向 JDK 17，启动或跑 `scripts/export-openapi.sh` 前用 `mvn -v` / `java -version` 确认。
8. **重大技术决策要写 ADR**，模板见 `docs/decisions/TEMPLATE.md`。

## 已知的环境坑（排查前先看这里）

| 现象 | 原因 | 处理 |
|------|------|------|
| `java -jar` 启动报「Port 56491 already in use」 | 执行环境注入了 `SERVER__PORT` 环境变量 | 命令行显式覆盖：`--server.port=8080`（命令行参数优先级最高） |
| `curl localhost` 报 `upstream connect failed` | 请求走了环境的 HTTP 代理 | 加 `--noproxy '*'` |
| 本机后端启动失败 / 端口异常 | 同上两类问题 | 先区分是代码问题还是环境问题，**不要**对着端口反复排查 |
| `mvn clean` 报「另一个程序正在使用此文件」 | 正在运行的后端进程持有 target 下的 jar | 先结束 java 进程再构建；Windows 下运行中的 jar 无法删除 |
| Boot 4 应用启动失败，`NoClassDefFoundError: ...WebMvcProperties` | springdoc 用了 2.x（只支持 Boot 3） | springdoc 必须用 3.x；版本对应表见 `docs/decisions/0008` |
| 依赖树里同时出现 `tools.jackson` 与 `com.fasterxml.jackson.databind` | Boot 4 的常态：Jackson 3 是默认，Jackson 2 由 springdoc / MyBatis 传递引入 | 代码 import 一律用 `tools.jackson.*`；注解（`@JsonIgnore`）继续用 `com.fasterxml.jackson.annotation`（包名没变） |
| mysql profile 启动报 `Unsupported Database: MySQL 8.0` | Flyway 10 起各数据库支持拆成独立模块，`flyway-core` 只内置 H2 / SQLite | 必须显式引 `org.flywaydb:flyway-mysql`（H2 profile 一切正常，只在切库时炸） |
| 启动报 `Migration checksum mismatch for migration version 1` | **修改了已执行过的迁移脚本**。Flyway 用校验和守护"迁移脚本是历史" | 正确做法是新增 `V2__xxx.sql`；本地开发库可重建（`DROP DATABASE bluewhale; CREATE ...`）绕过，有数据的环境不行 |
| 某个查询在 H2 上正常、切 MySQL 报 `ERROR 1064` | 列名或**别名**撞了 MySQL 保留字（如 `release`）。H2 不认这些保留字，所以接口测试全绿 | 见下条——这是本项目最隐蔽的一类差异 |
| 切到达梦后报「表或视图不存在」 | 达梦 profile 下 **Flyway 是关闭的**（Flyway 官方没有达梦模块），表结构不由应用创建 | 先执行交付脚本：`BW_DB_PASSWORD=xxx bash scripts/init-dameng.sh`，或由 DBA 执行 `db/migration/dameng/V1__*.sql`。见 `docs/decisions/0010` |
| 达梦上插入中文报「数据超长」，但字数明明没超 | 达梦 `VARCHAR(n)` 默认按**字节**算，而 Java 侧的截断按**字符**算（UTF-8 汉字 3 字节） | 达梦脚本用 `VARCHAR(n CHAR)` 显式声明字符语义（`dameng/V1__*.sql` 已处理） |
| 达梦分页查询变慢、`Page` 似乎没生效 | URL 没写成 `jdbc:dm://` 开头，MyBatis-Plus 认不出方言，分页拦截器**静默跳过**（不报错） | 保持 `jdbc:dm://` 前缀；`DatasourceDialectTest` 会断言这一点 |

## 写数据库相关的代码前，先记住四件事

1. **新增落库字段，先查 MySQL 保留字。** `release`、`rank`、`groups`、`system`、`window` 都是常见踩坑词。`RELEASE` 是 MySQL 8 的保留字而 H2 不认——写错了表现为"H2 正常、MySQL 建表失败"。
2. **不要用 `@TableField` 把列名映射成与属性名不同的名字。** MyBatis-Plus 会**自动补 `列名 AS 属性名` 的别名**，别名若撞保留字照样 1064。让属性名与列名同构（`appRelease → app_release`，与 `traceId → trace_id` 一致）。
3. **这类问题不要指望业务用例发现** —— 默认 profile 是 H2，它不认这些保留字，所有接口测试都会是绿的。只能对**生成的 SQL** 做断言（见 `ClientLogEntityMappingTest`）。
4. **现在有两个库要查保留字，而且结论不可互推。** `RELEASE` 是 MySQL 8 的保留字，达梦完全不认；反过来 `LEVEL` 是达梦的关键字（属非保留、可作列名），MySQL 也不管。达梦的查法：`SELECT KEYWORD FROM V$RESERVED_WORDS WHERE RESERVED='Y'`。

> 数据源、profile 与表结构变更的完整说明见 `server/README.md` 第六节、`docs/decisions/0009`（H2 / MySQL）与 `docs/decisions/0010`（达梦）。

## 目录边界（勿越界）

```
web/packages/*  ──▶ 被引用，不可反向依赖 web/platform/*
web/platform/*  ──▶ 应用，互不引用
server/         ──▶ sts-app → sts-server → sts-components → sts-common → sts-parent
                    （单向；禁止反向、禁止跨层跳跃，细则见 docs/decisions/0005）
```

## 项目记忆

- `.workbuddy/memory/MEMORY.md` —— 长期项目约定（技术栈、踩坑、铁律）
- `.workbuddy/memory/YYYY-MM-DD.md` —— 每日工作日志（**追加，不覆盖**）

实质性工作完成后更新记忆；日常寒暄与简单查询不必记录。

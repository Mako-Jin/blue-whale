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

# AGENTS.md — 给 AI 助手的项目约定

本文件是 AI 助手在本仓库工作时的**硬性约定**。开始任何任务前先读它。

## 必读文档

| 文档 | 内容 |
|------|------|
| `README.md` | 项目结构、六条铁律、技术栈 |
| `docs/README.md` | 文档规范（「改动 → 需同步哪些文档」对照表） |
| `CONTRIBUTING.md` | 环境、Git 钩子、提交规范 |
| `docs/decisions/` | 已定架构决策（ADR），动手前先看有没有相关约束 |
| `docs/architecture/` | 分层与依赖方向的落地细则（后端改模块前必看） |

## 硬性要求

1. **每完成一步实质性改动，同步更新对应文档**——对照表见 `docs/README.md` 第二节。文档与代码走同一次提交。
2. **提交信息必须符合 Conventional Commits**（`commit-msg` 钩子会拦截）。
3. **不要提前空建**未被使用的包 / 模块——按业务需要创建。
4. **不混用两端构建体系**：前端只用 pnpm，后端只用 Maven。
5. **不手写前端 SDK**——它由 `docs/api` 的 OpenAPI 契约生成。
6. **重大技术决策要写 ADR**，模板见 `docs/decisions/TEMPLATE.md`。

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

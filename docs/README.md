# docs — 文档与契约中心

代码回答"**是什么**"，本目录回答"**为什么**"与"**怎么约定**"。

---

## 一、目录结构

| 目录 | 内容 | 何时必须更新 |
|------|------|--------------|
| `architecture/` | 系统架构、分层图、部署拓扑、关键时序 | 组件划分、分层关系或数据流发生变化时 |
| `api/` | OpenAPI 契约文件（**跨端唯一真相源**） | 后端接口有任何增删改时（与代码**同一次提交**） |
| `decisions/` | 架构决策记录（ADR） | 做出不可逆或影响面大的技术决策时 |

---

## 二、文档同步规范（硬性要求）

**文档与代码必须同一次提交。** 不允许"先写代码，文档回头补"——回头就是永不。

每次改动，按下表自查必须同步哪些文档：

| 改动类型 | 必须同步的文档 |
|----------|----------------|
| 新增 / 删除前端应用 | 根 `README.md` 结构图 · `web/README.md` 目录结构 |
| 修改前端规范工具链（ESLint/Prettier/Stylelint…） | `web/README.md` 规范章节 · `packages/common-config/README.md` |
| 新增 / 删除公共包 | `web/README.md` · 根 `README.md` · 该包的 `README.md` |
| 新增 / 修改公共包对外 API | 该包的 `README.md` |
| 新增日志上报维度或上报通道 | `packages/common-tools/README.md`（`LogRecord` 章节） |
| 修改请求传输策略（错误分类、超时、重试、鉴权钩子） | `packages/common-tools/README.md`（HTTP 章节） · `decisions/0007-前端请求分层与错误模型.md` |
| 修改客户端日志上报通道（批量、退避、缓冲上限） | `decisions/0004-前端日志上报架构.md` |
| 调整客户端日志的校验 / 截断 / 存储策略 | `server/README.md`（日志域硬规则表） |
| 修改契约的生成或消费流程（导出脚本、生成器、类型修正） | `api/README.md` · `decisions/0006-接口契约的生成与消费.md` |
| 调整配置分层或新增配置层 | `decisions/0003-配置分域与自研边界.md` |
| 新增 / 删除第三方依赖 | `web/pnpm-workspace.yaml` 的 `catalog:` · `CONTRIBUTING.md` 依赖治理 |
| 新增 / 删除后端模块 | `server/README.md` 模块表 · 根 `README.md` 结构图 |
| 调整后端分层或依赖方向 | `decisions/0005-后端模块划分与依赖约束.md` · `architecture/后端分层与依赖方向.md` · `server/README.md` |
| 修改统一响应结构或错误码 | `server/README.md` 错误码表 · `docs/api/openapi.json` |
| 新增 / 修改后端接口 | `docs/api/openapi.json`（用 `scripts/export-openapi.sh` 导出）→ `pnpm --filter common-api gen:api` |
| 变更顶层目录或横向层 | 根 `README.md` |
| 技术选型 / 架构决策 | 新增 `docs/decisions/NNNN-*.md` |
| 引入新的全局工程约定 | `CONTRIBUTING.md` |

---

## 三、ADR（架构决策记录）

命名格式：

```
docs/decisions/NNNN-简短标题.md
```

- `NNNN` 为四位序号，从 `0001` 递增，**只增不改**。
- 模板见 `decisions/TEMPLATE.md`。
- 结构固定三段：**背景**（为什么必须决策）→ **决策**（选了什么）→ **后果**（得到什么、付出什么）。

> 决策被推翻时，**不要删改原 ADR**，而是新写一篇说明"取代 000X"。

---

## 四、当前文档索引

| 文档 | 说明 |
|------|------|
| `decisions/0001-架构基线与技术选型.md` | 仓库结构、前后端分离、技术栈基线 |
| `decisions/0002-前端公共包与公共依赖治理.md` | pnpm catalog、`common-*` 包划分、logger 重写决策 |
| `decisions/0003-配置分域与自研边界.md` | 配置三层分域、自研边界、客户端配置五条底线 |
| `decisions/0004-前端日志上报架构.md` | 采集与传输分层、`LogRecord` 契约、采样与脱敏约束 |
| `decisions/0005-后端模块划分与依赖约束.md` | 五层依赖方向、groupId 分级、分包方式、统一响应契约 |
| `decisions/0006-接口契约的生成与消费.md` | 契约由代码生成、生成物入库、任意值字段的类型修正 |
| `decisions/0007-前端请求分层与错误模型.md` | 传输内核与契约装配的分工、六类错误、重试幂等边界 |
| `architecture/后端分层与依赖方向.md` | 后端分层落地细则、依赖矩阵、前后端结构对照 |
| `api/README.md` | 契约接口清单、更新流程、任意值字段的处理 |
| `../web/packages/common-tools/README.md` | 公共能力包（logger / env / http）使用说明与修复清单 |
| `../web/packages/common-api/README.md` | 前端契约包的使用方式与分层边界 |
| `../server/README.md` | 后端模块表、统一响应与错误码、日志域硬规则、构建命令 |

---

## 五、写作约定

1. **中文为主**，专有名词保留英文原文（如 `platform-portal`、`sts-common`）。
2. 目录结构用代码块，让读者能直接复制路径。
3. 表格优先于长段落——大多数规范本质是"对照表"。
4. 换行符统一 LF（见根目录 `.gitattributes`）。

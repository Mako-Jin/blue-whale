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
| 新增 / 删除第三方依赖 | `web/pnpm-workspace.yaml` 的 `catalog:` · `CONTRIBUTING.md` 依赖治理 |
| 新增 / 删除后端模块 | `server/README.md` 模块表 · 根 `README.md` 结构图 |
| 新增 / 修改后端接口 | `docs/api/openapi.json` → 重新生成前端 SDK |
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
| `../web/packages/common-tools/README.md` | 公共能力包（logger / env）使用说明与修复清单 |
| `api/` | 待后端契约产出后填充 |
| `architecture/` | 待系统成形后填充 |

---

## 五、写作约定

1. **中文为主**，专有名词保留英文原文（如 `platform-portal`、`sts-common`）。
2. 目录结构用代码块，让读者能直接复制路径。
3. 表格优先于长段落——大多数规范本质是"对照表"。
4. 换行符统一 LF（见根目录 `.gitattributes`）。

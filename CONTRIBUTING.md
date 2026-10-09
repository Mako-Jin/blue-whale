# 贡献与开发约定

面向所有参与 BlueWhale 的人（含 AI 助手）。

---

## 一、环境要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Node.js | >= 20 | 前端工作区 |
| pnpm | 10.x（`packageManager` 已锁定 10.33.0） | 前端包管理 |
| JDK | 21 | 后端工作区 |
| Maven | 3.9+ | 后端构建 |
| Git | 2.40+ | 版本控制 |

---

## 二、首次搭建

```bash
# 前端
cd web && pnpm install          # 会自动执行 prepare 钩子挂载 Git 钩子

# 后端
cd server && mvn clean install -DskipTests
```

### Git 钩子

钩子脚本位于 `web/.husky/`，通过 Git 原生 `core.hooksPath` 挂载（**不是** `.git/hooks/`）。

`web/package.json` 的 `prepare` 脚本会自动执行 `husky` 完成挂载。若钩子未生效，手动挂载：

```bash
# 在仓库根执行
git config core.hooksPath web/.husky
```

| 钩子 | 作用 |
|------|------|
| `pre-commit` | 对**暂存文件**跑 lint-staged：ESLint --fix、Prettier --write、Stylelint --fix |
| `commit-msg` | 校验提交信息符合 Conventional Commits，不合规直接拦截 |

---

## 三、提交规范

格式：

```
<type>(<scope>): <subject>
```

- `type` 取值：`feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- 标题上限 100 字符

```bash
feat(portal): 新增子应用挂载容器
fix(portal): 修复路由前缀匹配错误
docs(decisions): 补充 ADR-0001
```

---

## 四、文档同步（硬性要求）

**改代码必须同步改文档，且在同一次提交里完成。**

对照表见 `docs/README.md` 第二节——那里列了每类改动需要同步哪些文档。

---

## 五、换行符约定

仓库统一以 **LF** 存储与检出，由根目录 `.gitattributes` 强制（Windows 专有脚本如 `.bat`/`.ps1` 保留 CRLF）。

Windows 用户首次 clone 后若看到全文件差异，执行一次规范化即可：

```bash
git add --renormalize .
```

> 为什么强制 LF：husky 的 shell 钩子一旦变成 CRLF，在 Linux/macOS 上会因 `\r` 直接执行失败。

---

## 六、常用命令

```bash
cd web

pnpm install          # 安装依赖
pnpm dev              # 启动全部应用（turbo 聚合日志）
pnpm build            # 按依赖顺序构建
pnpm lint             # ESLint
pnpm stylelint        # 样式规范
pnpm typecheck        # 类型检查
pnpm test             # 单元测试
pnpm format           # Prettier 格式化
```

---

## 七、目录归属速查

| 我想加… | 放哪 |
|---------|------|
| 一个新的前端应用 | `web/platform/<应用名>/` |
| 一个跨应用复用的前端包 | `web/packages/common-<名字>/` |
| 一个新的后端业务模块 | `server/sts-app/` 或对应层次的聚合模块下 |
| 一份架构决策 | `docs/decisions/NNNN-短标题.md` |
| 一个跨端脚本 | `scripts/` |
| 一份部署 / CI 配置 | `infra/` |

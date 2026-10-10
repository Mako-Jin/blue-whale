# 贡献与开发约定

面向所有参与 BlueWhale 的人（含 AI 助手）。

---

## 一、环境要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Node.js | >= 20 | 前端工作区 |
| pnpm | 10.x（`packageManager` 已锁定 10.33.0） | 前端包管理 |
| JDK | **21+** | 后端工作区（enforcer 在构建期强制，低于 21 直接失败） |
| Maven | 3.6.3+ | 后端构建（enforcer 同样检查；已在 3.8.1 验证） |
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

## 六、依赖治理

### 公共依赖的版本

第三方依赖的版本**只写一处**——`web/pnpm-workspace.yaml` 的 `catalog:` 段。`package.json` 里出现具体版本号即视为违规：

```json
{ "devDependencies": { "typescript": "catalog:" } }
```

新增依赖的正确流程：

```bash
# 1. 在 web/pnpm-workspace.yaml 的 catalog 里加一行
# 2. 在用到它的 package.json 里写 "catalog:"
cd web && pnpm install
```

> 为什么不是"再建一个包放依赖"：那会产生**幽灵依赖**——某个库能被引用到，却没出现在引用方的 `package.json` 里，运行时解析不到、IDE 也提示不出来。catalog 只统一版本号，依赖归属关系仍留在各自的 `package.json`。详见 `docs/decisions/0002-前端公共包与公共依赖治理.md`。

### 公共代码

自研公共代码放 `web/packages/common-<能力>/`。建包前先问一句：**这份代码是不是只有一个应用会用？** 是则不建包。

当前已有的公共包：

| 包 | 内容 |
|----|------|
| `common-config` | 共享 ESLint / Stylelint / TypeScript 配置 |
| `common-tools` | 日志（`logger`）· 环境探测（`env`）· 请求内核（`http`） |
| `common-api` | 契约包：OpenAPI 生成的类型 + 接口装配 |

> `common-tools` 与框架无关（不依赖 React），应用、构建脚本、Node 工具链都能直接用。本地演示：`pnpm --filter common-tools playground`（logger）、`pnpm --filter platform-log-console dev`（日志上报全链路）。

---

## 七、接口契约的更新流程（改后端接口必走）

```bash
cd server && mvn -DskipTests install     # 1. 构建后端
cd .. && bash scripts/export-openapi.sh  # 2. 从运行中的应用导出契约（默认端口 18080）
cd web && pnpm --filter common-api gen:api   # 3. 重新生成前端类型
```

三步的产物（`docs/api/openapi.json` 与 `common-api/src/generated/schema.d.ts`）都入库，且**必须与接口代码同一次提交**。

- 契约由代码生成，**不要手改** `docs/api/openapi.json` 或 `generated/` 下的任何文件；
- 唯一允许人工介入类型的位置是 `common-api/src/types.ts` 的 `JsonValue` 修正（生成器表达不了"任意值"），细则见 `docs/decisions/0006`；
- 前端调用接口一律走 `common-api`，**禁止**直接 `fetch`。

---

## 八、常用命令

### 前端

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

### 后端

```bash
cd server

mvn clean install -DskipTests                # 构建全部模块

# 启动（默认 8080）—— 默认 profile 是 h2 内存库，零外部依赖
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar

# 连本机 MySQL（数据落磁盘，重启后仍在）。库要先建好，见 server/README.md 第六节
BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=mysql
```

> **数据源由 profile 决定，凭据走环境变量**，两者都不要写进文件 ——
> 用哪个库应当是启动参数（显式可见），密码进版本库则会永久留在 git 历史里。
> 详见 `docs/decisions/0009`。

> **Maven 实际使用的 JDK 必须是 21+**，低于 21 时构建会在 enforcer 阶段直接失败并给出中文提示
> （不再依赖编译器那句含混的 `release version 21 not supported`）。用 `mvn -v` 可查看当前指向。
> 注意：enforcer 只保护 `mvn` 构建；直接运行 `java -jar` 或 `scripts/export-openapi.sh` 时
> 版本检查由脚本自查，`JAVA_HOME` 指向旧版本仍会失败：
>
> ```bash
> JAVA_HOME=/path/to/jdk-21 mvn clean install
> ```

---

## 九、目录归属速查

| 我想加… | 放哪 |
|---------|------|
| 一个新的前端应用 | `web/platform/<应用名>/` |
| 一个跨应用复用的前端包 | `web/packages/common-<名字>/` |
| 一个新的后端业务模块 | `server/` 下按层次归位，规则见 `docs/decisions/0005` |
| 一个新的后端接口 | `sts-server-<域>` 的对应特性包内，随后走第七节流程更新契约 |
| 一份架构决策 | `docs/decisions/NNNN-短标题.md` |
| 一个跨端脚本 | `scripts/` |
| 一份部署 / CI 配置 | `infra/` |

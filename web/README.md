# web — 前端工作区

pnpm workspace + Turborepo。React 19 + TypeScript 6 + Vite 8，微前端用 qiankun。

## 一、目录结构

```
web/
├── package.json           工作区根，统一入口脚本
├── pnpm-workspace.yaml    工作区成员声明 + 公共依赖版本（catalog）
├── turbo.json             任务编排与缓存策略
├── eslint.config.js       工作区根 ESLint 配置（lint-staged 依赖它）
├── .prettierrc.json       代码格式化（全工作区共用）
├── commitlint.config.js   提交信息规范
├── .husky/                Git 钩子
├── packages/              公共包（common-* 前缀）
│   ├── common-config          共享工程配置：ESLint / Stylelint / TypeScript
│   ├── common-tools           公共能力包：logger（日志）· env（环境探测）· http（请求内核）
│   └── common-api             契约包：由 docs/api/openapi.json 生成的类型 + 接口装配
└── platform/              平台级应用
    ├── platform-portal        主应用（qiankun 容器）
    └── platform-log-console   日志控制台（端到端演示，见下方说明）
```

`packages/` 放**被引用但跑不起来**的包；`platform/` 放**能独立启动**的应用。这条线不能模糊。

新增应用时直接建目录，`pnpm-workspace.yaml` 用的是通配符，不需要改配置。

> `platform-log-console` 的定位是**可运行的验证工具**，不是产品功能：它把「前端产生日志 → 批量上报 → 服务端落库 → 查询与链路追踪」这条链路完整跑通并可视化，左栏显示客户端视角、右栏显示服务端视角，两者对照才能判断上报到底成没成。它同时也是 `common-tools/http` 与 `common-api` 的活体示例。

## 二、命令

在 `web/` 目录下执行：

```bash
pnpm install              # 安装全部依赖

pnpm dev                  # 启动所有应用的开发服务（turbo 聚合日志）
pnpm dev --filter platform-portal   # 只启动某一个

pnpm build                # 按依赖顺序构建全部
pnpm lint                 # ESLint
pnpm stylelint            # 样式规范
pnpm typecheck            # 类型检查
pnpm test                 # 单元测试
pnpm format               # Prettier 格式化

pnpm --filter common-tools playground   # 启动 logger 可视化演示（:3100）
pnpm --filter platform-log-console dev  # 启动日志控制台（:3200，需后端在 :8080）
pnpm --filter common-api gen:api        # 从 docs/api/openapi.json 重新生成前端类型
```

### 日志控制台怎么跑

```bash
# 1. 起后端（在 server/ 目录）
mvn -DskipTests install
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar

# 2. 起前端（在 web/ 目录）
pnpm --filter platform-log-console dev   # http://127.0.0.1:3200
```

开发期 `/api` 由 vite 代理到 `127.0.0.1:8080`，请求与服务端同源 —— 排查日志功能时被跨域问题挡住是最不划算的事。

## 三、规范工具链

| 工具                | 作用                                 | 配置位置                                  |
| ------------------- | ------------------------------------ | ----------------------------------------- |
| ESLint 10           | 代码规范（flat config）              | `packages/common-config/src/eslint.js`    |
| Prettier 3          | 代码格式化                           | 工作区根 `.prettierrc.json`               |
| Stylelint 17        | 样式规范（已适配 Less）              | `packages/common-config/src/stylelint.js` |
| commitlint          | 提交信息规范（Conventional Commits） | 工作区根 `commitlint.config.js`           |
| Husky + lint-staged | 提交前只检查改动文件                 | `.husky/` + 根 `package.json`             |
| Vitest 5            | 单元测试                             | 各应用 `vite.config.ts` 的 `test` 字段    |

**规则只维护一份。** 子项目的 `stylelint.config.js` 是一行 re-export；`eslint.config.js` 必须**调用工厂函数**（ESLint 要求默认导出为配置数组）。要改规则就改 `common-config`。

> **pnpm 依赖隔离的两个坑**（已在 `common-config` 里规避，改配置时勿踩回去）：
>
> 1. Stylelint 的 `extends` 若用字符串写包名，子项目解析不到——已改用 `require.resolve` 解析为绝对路径。
> 2. ESLint 只从**当前工作目录**向上找配置，故工作区根也需要一份 `eslint.config.js`（lint-staged 在 `web/` 下执行 `eslint`）。

**Prettier 例外。** 它会自动向上查找最近的配置文件，所以用工作区根那份即可，子项目不用配。

提交信息格式：

```
<type>(<scope>): <subject>

feat(portal): 新增子应用挂载容器
fix(portal): 修复路由前缀匹配错误
```

`type` 取值：`feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`

## 四、Turborepo 用法

任务定义在 `turbo.json`，核心是 `dependsOn: ["^build"]` —— 保证依赖的包先构建。

```bash
pnpm dev --filter platform-portal...     # 含其依赖
pnpm build --dry                        # 查看任务执行计划（不实际执行）
pnpm build --force                      # 忽略缓存强制重跑
```

缓存产物在 `.turbo/` 下（已 gitignore）。若怀疑缓存有陈旧问题，删掉 `.turbo/` 再跑即可。

## 五、依赖治理（铁律）

### 公共依赖的版本

第三方依赖的版本**只写一处** —— `pnpm-workspace.yaml` 的 `catalog:` 段，各 `package.json` 一律写 `"catalog:"`。

| 写法                       | 判定                                |
| -------------------------- | ----------------------------------- |
| `"typescript": "catalog:"` | ✅ 版本由 catalog 统一裁决          |
| `"typescript": "^6.0.3"`   | ❌ 违规，版本会随子项目漂移         |
| 建一个包专门"存放依赖"     | ❌ 产生**幽灵依赖**，运行时解析不到 |

新增依赖：先在 `pnpm-workspace.yaml` 的 `catalog:` 加一行 → 在用到它的 `package.json` 写 `"catalog:"` → `pnpm install`。

> catalog 只统一**版本号**，依赖归属关系仍留在各自的 `package.json`，因此不会产生幽灵依赖。详见 `docs/decisions/0002-前端公共包与公共依赖治理.md`。

### 依赖方向

```
platform/*  ──▶  packages/*        （允许）
packages/*  ──▶  platform/*        （禁止）
应用之间相互引用                     （禁止，复用内容下沉到 packages/）
```

## 六、Git 钩子

钩子脚本在 `web/.husky/`，通过 Git 原生 `core.hooksPath` 挂载——仓库根是 `BlueWhale/`，前端只是其中一个工作区。

| 钩子         | 作用                                                                      |
| ------------ | ------------------------------------------------------------------------- |
| `pre-commit` | 对暂存文件跑 lint-staged：ESLint --fix、Prettier --write、Stylelint --fix |
| `commit-msg` | 校验提交信息符合 Conventional Commits，不合规直接拦截                     |

`pnpm install` 时由 `prepare` 脚本自动挂载。若钩子未生效，在**仓库根**执行：

```bash
git config core.hooksPath web/.husky
```

> 钩子脚本内部会先 `cd` 到 `web/`——因为 Git 执行钩子时的当前目录是仓库根。

## 七、当前状态

各项目均通过全部校验（逐包直跑）。

| 校验项                  | platform-portal | common-tools             | common-api    | platform-log-console             |
| ----------------------- | --------------- | ------------------------ | ------------- | -------------------------------- |
| `tsc --noEmit` 类型检查 | 通过            | 通过                     | 通过          | 通过                             |
| `eslint .`              | 通过            | 通过                     | 通过          | 通过                             |
| `stylelint`             | 通过            | 通过（playground）       | —             | 通过（含 `App.less`）            |
| `vitest run` 单元测试   | 通过（1/1）     | 通过（101/101）          | 通过（11/11） | 通过（25/25，含浏览器渲染测试）  |
| `vite build` 生产构建   | 通过            | 通过（14.76 kB）         | —             | 通过（CSS 11.92 kB · JS 257 kB） |
| dev server 启动         | 通过（:3000）   | playground 通过（:3100） | —             | 通过（:3200）                    |

> **关于 `turbo run`**：本次验证时 turbo 报 `os error 231`（管道句柄耗尽，`unable to spawn child process`）。这是当前运行环境的进程创建限制，**不是工程配置问题**——上表中的每个任务脚本逐个直跑全部通过，同一配置在此前也能通过 turbo 编排。本地正常终端下 `pnpm dev` / `pnpm build` 等不受影响。

> **`openapi-typescript` 的 peer 警告**：其 peer 约束为 `typescript@^5.x`，本项目用 6.0.3，`pnpm install` 会打一条警告。生成功能已验证正常，升级生成器时需重新确认（见 `packages/common-api/README.md`）。

## 八、待补充

- 子应用 `platform-user`、`platform-admin` 尚未创建
- qiankun 子应用注册与全局状态下发尚未接入
- `common-tools` 的 http 内核目前没有自动重试、请求缓存与并发去重 —— 需要时按 `docs/decisions/0007` 的边界补，不要在各接口里散写
- 客户端日志目前只有采集与上报；**采样与脱敏**尚未实现，等有真实生产流量再做
- 公共依赖已由 `pnpm-workspace.yaml` 的 catalog 统一管理，新增依赖务必走 catalog

# web — 前端工作区

pnpm workspace + Turborepo。React 19 + TypeScript + Vite，微前端用 qiankun。

## 一、目录结构

```
web/
├── package.json           工作区根，统一入口脚本
├── pnpm-workspace.yaml    工作区成员声明
├── turbo.json             任务编排与缓存策略
├── .prettierrc.json       代码格式化（全工作区共用）
├── commitlint.config.js   提交信息规范
├── .husky/                Git 钩子
├── packages/              公共包（common-* 前缀）
│   └── common-config          共享工程配置：ESLint / Stylelint / TypeScript
└── platform/              平台级应用
    └── platform-portal        主应用（qiankun 容器）
```

`packages/` 放**被引用但跑不起来**的包；`platform/` 放**能独立启动**的应用。这条线不能模糊。

新增应用时直接建目录，`pnpm-workspace.yaml` 用的是通配符，不需要改配置。

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
```

## 三、规范工具链

| 工具                | 作用                                 | 配置位置                                  |
| ------------------- | ------------------------------------ | ----------------------------------------- |
| ESLint 10           | 代码规范（flat config）              | `packages/common-config/src/eslint.js`    |
| Prettier 3          | 代码格式化                           | 工作区根 `.prettierrc.json`               |
| Stylelint 17        | 样式规范（已适配 Less）              | `packages/common-config/src/stylelint.js` |
| commitlint          | 提交信息规范（Conventional Commits） | 工作区根 `commitlint.config.js`           |
| Husky + lint-staged | 提交前只检查改动文件                 | `.husky/` + 根 `package.json`             |
| Vitest 5            | 单元测试                             | 各应用 `vite.config.ts` 的 `test` 字段    |

**规则只维护一份。** 子项目的 `stylelint.config.js` 是一行 re-export；`eslint.config.js` 是两行——因为 ESLint 要求默认导出为**配置数组**，需调用 `createEslintConfig()`。要改规则就改 `common-config`。

> pnpm 依赖隔离的坑：共享包里的 `stylelint` 配置若用字符串写 `extends`，子项目解析时会找不到包。`common-config` 已用 `require.resolve` 解析为绝对路径规避。

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

## 五、依赖方向（铁律）

```
platform/*  ──▶  packages/*        （允许）
packages/*  ──▶  platform/*        （禁止）
应用之间相互引用                     （禁止，复用内容下沉到 packages/）
```

## 六、当前状态

工程基座已跑通并验证，`platform-portal` 可正常开发与构建。

| 校验项                  | 结果                            |
| ----------------------- | ------------------------------- |
| `tsc --noEmit` 类型检查 | 通过                            |
| `eslint .`              | 通过                            |
| `stylelint`             | 通过                            |
| `vitest run` 单元测试   | 通过（1/1）                     |
| `vite build` 生产构建   | 通过                            |
| `turbo run` 任务编排    | 通过                            |
| dev server 启动         | 通过（HTTP 200，默认端口 3000） |

## 七、待补充

- 子应用 `platform-user`、`platform-admin` 尚未创建
- qiankun 子应用注册与全局状态下发尚未接入
- `common-api` 尚未创建（等后端 OpenAPI 契约就绪）
- `packages/` 下仅 `common-config` 一个包，其余 `common-*` 按需再建
- 尚未初始化 Git 仓库（Husky 钩子需要 Git 才生效）

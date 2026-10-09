# BlueWhale — 项目长期备忘

## 项目定位
大型全栈项目，前后端分离。工作空间：`D:\workspace\BlueWhale`。

## 参考项目（舰长指定的结构范本）
- `D:\workspace\platform-terminator` — 纯前端 monorepo：pnpm workspace、`common-*` 公共包、按业务域分组、qiankun 微前端、React
- `D:\workspace\SystemTerminator` — 纯后端 Maven 多模块：`sts-parent`/`sts-common`/`sts-components`/`sts-server`/`sts-app`/`sts-gateway`，包名 `com.yaocode.sts`
> 新需求优先参考这两个项目的既有做法，不要另起结构。

## 顶层划分
```
web/      前端工作区（pnpm workspace）
server/   后端工作区（Maven 多模块，包名 com.bluewhale.sts）
docs/ scripts/ infra/   横向层，不属于任何一端
```

## Git 与文档约定（舰长明确要求）
- 仓库根 = `D:\workspace\BlueWhale`，分支 `main`
- Husky 钩子目录 `web/.husky`，用 Git 原生 `core.hooksPath` 挂载（**不是** `.git/hooks`）；在仓库根执行 `git config core.hooksPath web/.husky`
- 沙箱环境下 husky 自动安装会因 `spawn git EBUSY` 失败 → 改手动挂钩；钩子脚本内需先 `cd "$(git rev-parse --show-toplevel)/web"`
- `.gitattributes` 强制 **LF**（husky 的 shell 脚本若变 CRLF，在 Linux 上会执行失败）
- `blender_mcp/` 已忽略（第三方独立仓库 mcp-for-blender，舰长决定不纳入主仓）
- 提交信息走 Conventional Commits（`commit-msg` 钩子强制拦截）
- **舰长要求：从现在开始每一步都完善文档**。改动 → 需同步哪些文档 的对照表在 `docs/README.md`；ADR 在 `docs/decisions/`；`CONTRIBUTING.md` + `AGENTS.md` 记录开发与 AI 协作约定
- 铁律：**改代码必须同步改文档，同一次提交完成**

## 技术栈（已定）
- 前端：React 19.3 + Vite 8 + TypeScript **6.0.3**（不用 7.x，typescript-eslint peer 限制 `<6.1.0`）+ React Router 7
- 微前端：**qiankun 2.x**（platform-portal 为主应用，user/admin 为子应用）
- 前端包管理：pnpm workspace + **Turborepo 2.11** 任务编排
- 前端规范：ESLint 10（flat config）+ Prettier 3 + Stylelint 17（Less）+ commitlint/Husky/lint-staged + Vitest 5
- 前端样式：**Less**（`css.preprocessorOptions.less` 注入 `variables.less`）
- 后端：Java 21 + Spring Boot 3.3.5 + Spring Cloud 2023.0.3
- 数据访问：MyBatis-Plus 3.5.9
- 后端构建：Maven 多模块
- 接口契约：OpenAPI 3（`docs/api/`）

## 前端结构
- `web/packages/` 放公共包（`common-*` 前缀）；`web/platform/` 放平台级应用；`elemental/` 预留
- **当前实际存在**：`packages/common-config`（共享工程配置）、`platform/platform-portal`（qiankun 主应用，已跑通）、根 `web/eslint.config.js`（lint-staged 依赖）
- 规划的 `common-{tools,theme,components,api}` 与 `platform-{user,admin}` **按需再建**，不要提前空建
- 规范规则只维护一份在 `common-config`：子项目 `stylelint.config.js` 一行 re-export；`eslint.config.js` 须调用 `createEslintConfig()` 且默认导出数组

## 前端踩坑备忘（pnpm 隔离 + monorepo）
- 共享配置里的 stylelint `extends` 必须用 `require.resolve` 转绝对路径，否则子项目解析不到
- TS 6 起 `baseUrl` 弃用，tsconfig 不要再写它
- ESLint 10 要求 `eslint.config.js` 默认导出为**配置数组**，不能导出函数
- ESLint flat config 只从**当前工作目录**向上找配置（不像 stylelint 按文件位置查找）→ 工作区根也要有一份 `eslint.config.js`
- typescript-eslint 必须显式给 `tsconfigRootDir`（否则 monorepo 多 tsconfig 报 multiple candidate）；`projectService: false` 实测无效

## 后端分层（依赖只能自上而下）
`sts-app` → `sts-server` → `sts-components` → `sts-common` → `sts-parent`
- `sts-parent`：只放 dependencyManagement 与 properties，**零代码**
- 版本号只允许在 sts-parent 出现一次，子模块写版本号即违规

## 六条铁律
1. 两端不共用构建体系（pnpm / Maven 互不侵入）
2. 跨端只认 OpenAPI 契约；前端 SDK 禁止手写，由契约生成到 `web/packages/common-api`
3. 跨端共享物（契约、枚举、错误码表）只放 `docs/`，不放任何一端代码里
4. `packages/*` 不得反向依赖 `platform/*`
5. `platform-user` 与 `platform-admin` 互不引用，复用内容下沉到 `packages/`
6. 后端模块依赖禁止反向、禁止跨层跳跃

## 沟通偏好
- 舰长偏好可视化方案与可运行演示，讲方案配图优于纯文字
- 给方案时先说结论和理由，再给细节

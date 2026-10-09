# BlueWhale — 项目长期备忘

## 项目定位
大型全栈项目，前后端分离。工作空间：`D:\workspace\BlueWhale`。

## 参考项目（舰长指定的结构范本）
- `D:\workspace\platform-terminator` — 纯前端 monorepo：pnpm workspace、`common-*` 公共包、按业务域分组、qiankun 微前端、React
- `D:\workspace\SystemTerminator` — 纯后端 Maven 多模块：`sts-*` 体系，包名 `com.yaocode.sts`
> 新需求优先参考这两个项目的既有做法，不要另起结构。

## 顶层划分
`web/`（前端·pnpm）· `server/`（后端·Maven，包名 `com.bluewhale.sts`）· `docs/ scripts/ infra/`（横向层）

## Git 与文档约定
- 仓库根 `D:\workspace\BlueWhale`，分支 `main`
- Husky 钩子目录 `web/.husky`，用 Git 原生 `core.hooksPath` 挂载；沙箱下 husky 自动安装会因 `spawn git EBUSY` 失败 → 手动挂钩；钩子脚本内先 `cd "$(git rev-parse --show-toplevel)/web"`
- `.gitattributes` 强制 LF（husky shell 脚本变 CRLF 会在 Linux 上执行失败）
- `blender_mcp/` 已忽略（第三方独立仓库，舰长决定不纳入主仓）
- 提交信息走 Conventional Commits（`commit-msg` 钩子拦截）
- **舰长要求：每一步都完善文档**；对照表在 `docs/README.md`，ADR 在 `docs/decisions/`，开发与 AI 约定在 `CONTRIBUTING.md` / `AGENTS.md`
- 铁律：改代码必须同步改文档，同一次提交完成

## 技术栈
- 前端：React 19 + Vite 8 + TS **6.0.3**（7.x 不被 typescript-eslint 支持）+ React Router 7
- 微前端 qiankun 2.x；包管理 pnpm workspace + Turborepo；样式 Less
- 规范：ESLint 10（flat config）+ Prettier 3 + Stylelint 17 + commitlint/Husky/lint-staged + Vitest 5
- 后端：Java 21 + Spring Boot 3.3.5 + Spring Cloud 2023.0.3 + MyBatis-Plus 3.5.9（Maven 多模块）
- 契约：OpenAPI 3（`docs/api/`）

## 前端结构
- `packages/` 公共包（`common-*`）、`platform/` 应用、`elemental/` 预留
- 实际存在：`common-config`（共享工程配置）、`common-tools`（公共能力包）、`platform-portal`（qiankun 主应用）、根 `web/eslint.config.js`
- 规划的 `common-{theme,components,api}`、`platform-{user,admin}` **按需再建**，不空建
- 规范只维护一份在 `common-config`：子项目 `stylelint.config.js` 一行 re-export；`eslint.config.js` 须调用 `createEslintConfig()`

## 公共依赖与公共包治理（2026-10-09 定 · ADR-0002）
- **依赖版本统一**：`web/pnpm-workspace.yaml` 的 `catalog:` 段是唯一来源，各 `package.json` 写 `"catalog:"`
- **不建"存放依赖的包"** —— 会产生幽灵依赖（库可被引用却没声明，运行时解析不到）。新增依赖：先写 catalog 再引用
- **公共代码**按能力建包 `packages/common-<能力>`；建包前先问"是否只有一个应用会用"
- 新建公共包的 `eslint.config.js` 需 `...createEslintConfig()` 再追加自定义规则（如日志包关 `no-console`）

## logger（common-tools）关键决策
- 7 级严重度（trace/debug/info/success/perf/warn/error）+ 阈值过滤；**过滤前置于格式化**
- 级别映射到 `console.debug/log/warn/error`（参考实现全走 log，导致筛选与堆栈定位失效）
- 渲染三态自适应：`css`（浏览器 `%c`）/ `ansi`（终端真彩）/ `plain`（管道、`NO_COLOR`）
- 命名空间层级 `child()`、`withContext()`、主题按级别按字段深合并、`LogSink` 可替换输出通道
- 消息里的 `%o`/`%c` 必须转义（用零宽空格隔断，否则会吞掉后续参数）
- 参考实现共修复 16 项问题，清单见 `web/packages/common-tools/README.md`
- 演示：`pnpm --filter common-tools playground`（:3100），把控制台输出镜像渲染到页面

## 前端踩坑备忘
- stylelint 的 `extends` 必须 `require.resolve` 转绝对路径（pnpm 隔离）
- TS 6 起 `baseUrl` 弃用
- ESLint 10 要求 flat config **默认导出数组**，不能导出函数
- ESLint 只从**当前工作目录**向上找配置（stylelint 按文件位置）→ 工作区根也要有 `eslint.config.js`
- typescript-eslint 必须显式 `tsconfigRootDir`；`projectService: false` 实测无效
- **沙箱限制**：Node `spawn git` 报 `EBUSY`；`turbo run` 报 `os error 231`（管道句柄耗尽）。均为环境限制，逐包直跑脚本均通过

## 后端分层（依赖只能自上而下）
`sts-app` → `sts-server` → `sts-components` → `sts-common` → `sts-parent`；`sts-parent` 零代码，版本号只出现一次

## 六条铁律
1. 两端不共用构建体系（pnpm / Maven 互不侵入）
2. 跨端只认 OpenAPI 契约；前端 SDK 禁止手写
3. 跨端共享物只放 `docs/`
4. `packages/*` 不得反向依赖 `platform/*`
5. `platform-user` 与 `platform-admin` 互不引用，复用下沉到 `packages/`
6. 后端模块依赖禁止反向、禁止跨层跳跃

## 沟通偏好
- 舰长偏好可视化方案与可运行演示，讲方案配图优于纯文字
- 给方案时先说结论和理由，再给细节

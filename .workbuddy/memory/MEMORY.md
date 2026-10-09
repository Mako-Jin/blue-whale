# BlueWhale — 项目长期备忘

## 项目定位
大型全栈项目，前后端分离。工作空间：`D:\workspace\BlueWhale`。

## 参考项目（舰长指定的结构范本）
- `D:\workspace\platform-terminator` — 纯前端 monorepo：pnpm workspace、`common-*` 公共包、按业务域分组（elemental/platform/games）、qiankun 微前端、React
- `D:\workspace\SystemTerminator` — 纯后端 Maven 多模块：`sts-parent`/`sts-common`/`sts-components`/`sts-server`/`sts-app`/`sts-gateway`，包名 `com.yaocode.sts`
> 新需求优先参考这两个项目的既有做法，不要另起结构。

## 顶层划分
```
web/      前端工作区（pnpm workspace）
server/   后端工作区（Maven 多模块，包名 com.bluewhale.sts）
docs/ scripts/ infra/   横向层，不属于任何一端
```

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
- **当前实际存在**：`packages/common-config`（共享工程配置，唯一包）、`platform/platform-portal`（qiankun 主应用，已跑通）
- 规划的 `common-{tools,theme,components,api}` 与 `platform-{user,admin}` **按需再建**，不要提前空建
- 规范规则只维护一份在 `common-config`：子项目 `stylelint.config.js` 一行 re-export；`eslint.config.js` 需调用 `createEslintConfig()` 且默认导出数组

## 前端踩坑备忘（pnpm 隔离相关）
- 共享配置里的 stylelint `extends` 必须用 `require.resolve` 转绝对路径，否则子项目解析不到
- TS 6 起 `baseUrl` 弃用，tsconfig 不要再写它
- ESLint 10 要求 `eslint.config.js` 默认导出为配置数组，不能导出函数

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

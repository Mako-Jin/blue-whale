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
│   ├── common-config          共享工程配置：ESLint / Stylelint / TypeScript / Vite
│   ├── common-tools           公共能力包：logger · env · http · json · report · events · micro · session
│   └── common-api             契约包：由 docs/api/openapi.json 生成的类型 + 接口装配（含认证链路）
└── platform/              平台级应用
    ├── platform-portal        主应用（qiankun 容器，唯一入口，:3000）
    ├── platform-log-console   日志控制台（子应用 + 可独立运行，:3200）
    ├── platform-event-console 行为事件控制台（子应用 + 可独立运行，:3300）
    ├── platform-environment   三维环境应用（子应用 + 可独立运行，:3400，three.js）
    ├── platform-admin         权限管理后台（子应用 + 可独立运行，:3500，按钮级权限演示）
    └── platform-audit-console 审计日志控制台（子应用 + 可独立运行，:3600，只读 + 演练区）
```

`packages/` 放**被引用但跑不起来**的包；`platform/` 放**能独立启动**的应用。这条线不能模糊。

新增应用时直接建目录，`pnpm-workspace.yaml` 用的是通配符，不需要改配置。

### 微前端结构

`platform-portal` 是**唯一的容器应用**，负责导航骨架、全局状态与子应用挂载；子应用既是被它挂载的子应用，**也保留独立启动能力**（子应用单独调试比在基座里调试快得多，且排查"到底是子应用的问题还是基座的问题"时必须有这条退路）。

```
platform-portal (:3000)
├── /                 首页（子应用入口与启动命令）
├── /log-console/*    ──loadMicroApp──▶  platform-log-console (:3200 dev)
├── /event-console/*  ──loadMicroApp──▶  platform-event-console (:3300 dev)
├── /environment/*    ──loadMicroApp──▶  platform-environment (:3400 dev)
├── /admin/*          ──loadMicroApp──▶  platform-admin (:3500 dev)
└── /audit/*          ──loadMicroApp──▶  platform-audit-console (:3600 dev)
```

接线代码集中在 `platform-portal/src/micro/`（5 个文件：类型 · 注册表 · 全局状态 · 加载器 · 出口），完整说明见 `platform/platform-portal/README.md`，决策与取舍见 `docs/decisions/0012`。

子应用侧只做两件事：入口用 `renderWithQiankun()` 导出生命周期、`vite.config.ts` 挂上 `vite-plugin-qiankun` 与 `qiankunDevPreamble()`。二者都不需要理解 qiankun 内核。

> `platform-log-console` 的定位是**可运行的验证工具**，不是产品功能：它把「前端产生日志 → 批量上报 → 服务端落库 → 查询与链路追踪」这条链路完整跑通并可视化，左栏显示客户端视角、右栏显示服务端视角，两者对照才能判断上报到底成没成。它同时也是 `common-tools/http` 与 `common-api` 的活体示例。

> `platform-event-console` 是同一个思路用在**行为事件**上：左栏点按钮产生事件、右栏看服务端落库结果。它额外演示三件事——**身份缝合**（匿名 `deviceId` 与登录 `userId` 同时挂在事件上）、**中间件脱敏**、**中间件屏蔽**。日志与事件是两条独立管道，因此这是两个应用而不是一个应用里的两个页签。

> `platform-environment` 是**与任何业务域无关的环境应用**：一块 three.js 三维画布（内含古建筑 / 原始丛林 / 现代写字楼 / 太空飞船 / 渲染基线五个可切换场景），外加"WebGL 渲染生命周期该怎么管"的参考实现 —— 画布与上下文同生共死、卸载必 `forceContextLoss()`、GPU 资源可断言地释放、切场景只换场景图不换渲染器。古建筑是第一个**可操控**场景：殿前广场上有可走的主人公（WASD/方向键 + 右键设行动终点 + 滚轮缩放）与左上角小地图（正北朝上、人物朝向与相机视野分画）。三维依赖重（gzip 后约 229 kB），独立成子应用让其他路由不受拖累。决策见 `docs/decisions/0013`（渲染生命周期）、`docs/decisions/0014`（多场景架构）与 `docs/decisions/0015`（场景交互）。

> `platform-admin` 是**按钮级权限的活体演示**，也是权限域实体（用户 / 租户 / 资源 / 角色 / 用户组 / 组织，六个标签页）的管理界面：同一段代码、同一个页面，管理员看到全部操作入口，只读的审计员只看得到列表 —— 差异不写在代码里，只写在基座下发的权限集合里。六个标签页的顺序就是授权链路的顺序：**资源**（能做什么）→ **角色**（打包 + 授资源 + 挂成员）→ **用户组**（把角色批量授给一群人）→ **组织**（数据范围的坐标系，回答"他看得到哪些数据"），而**用户**页挂的「角色来源」反查回答反向的问题 —— 这个人持有的角色是从哪来的（直接授予 / 经用户组授予，含两组时态）。它同时回答了"子应用怎么发已鉴权请求"：令牌**值**不下发，基座经 `HostProps.auth` 下发"取令牌"与"要一次刷新"两个动作（`docs/decisions/0018` 第 9 条），本应用不含登录页。后端 h2 profile 会种下两个对照账号（`admin` 全权限 / `auditor` 仅读权限），端到端验证见 `node scripts/verify-admin-permissions.mjs`。

> `platform-audit-console` 是**第三条管道**（审计日志）的控制台，形态与上面两个**刻意不同**：日志台与事件台都是左右分栏（客户端视角 / 服务端视角），因为"只看一边无法判断上报成没成"；审计**没有"两边"** —— 前端根本没有、也不该有把审计记录送出去的路径。页面从上到下三块：**流水**（按服务端时间倒序，带时间窗）→ **按动作聚合**（`action` 分组，失败数单列）→ **演练区**。`common-api` 的 `AuditApi` **一个写方法都没有**，`tests/audit.test.ts` 用快照式断言把"只有三个读方法"钉住。演练区的按钮**刻意不按权限显隐**：真实建一个租户、等审计记录出现、再删掉 —— 用只读账号点下去会得到两条 `FAILURE`，而**那次拒绝同样留痕**，正是本区要展示的东西。决策见 `docs/decisions/0019`，端到端验证见 `node scripts/verify-audit-pipeline.mjs`。

> **日志台与事件台的认证接线（ADR-0026）**：子应用**不拥有会话** —— 跑在基座里用宿主下发的认证桥，独立运行时自建一套。这段分支逻辑此前在 `platform-admin` / `platform-audit-console` 各写了一遍，现收敛为 `common-api` 的 **`createConsoleAuth`**（宿主桥优先；开发账号由调用方在 `import.meta.env.DEV` 为真时才构造，契约包不自己读环境变量；**不传即不自动登录**，因而生产构建下整段凭据会被静态摇掉 —— 见 `docs/decisions/0033`）。两个控制台本轮接上它并补了两个 provider（`accessTokenProvider` / `onUnauthorized`）—— **此前它们的 401 是"漏接"不是"缺实现"**：基座在 `createMicroAppProps` 里恒带 `auth`，只是没人读，于是后端刚上线鉴权时（ADR-0026 给日志 / 事件查询接口补 `@RequiresPermission`）控制台直接 401。**四个子应用一律用 `createConsoleAuth`**（ADR-0034 已把 `platform-admin` / `platform-audit-console` 那两份副本收进来 —— 它们当年是"有意留着的重复"，而动因是那份重复**已经产生过它预言的缺陷**：同一条分支上四处副本写出了两种写法，两个控制台丢掉了那次拒绝）。接线至此逐字同形，**新子应用照抄一处即可**；`auth/hooks.ts`（三行 `useSyncExternalStore` 适配）仍各应用一份，是**刻意不合并** —— 合并要把 React 引进与框架无关的内核。

> **独立运行分支的写法（四处一致，改一处就四处一起改）**：会话准备**失败也要渲染** ——
> 没有会话只影响需要鉴权的面板，左栏与那句"后端不可达"仍然有内容可看；
> 但**必须接住那次拒绝**（`try/catch` 后记一条 error），不能写成
> `void ensureSession().finally(render)` —— 丢掉返回值会让"后端没起"这种预期内的状况
> 变成控制台里一条 `unhandledrejection`，真正的异常会被这种噪声淹掉。
> 这条有真机脚本盯着：`scripts/verify-standalone-dev-login.mjs`
> （后端在跑 / 没起两种状态各跑一遍四个应用）。

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

pnpm --filter common-tools playground      # 启动 logger 可视化演示（:3100）
pnpm --filter platform-log-console dev     # 启动日志控制台（:3200，需后端在 :8080）
pnpm --filter platform-event-console dev   # 启动行为事件控制台（:3300，需后端在 :8080）
pnpm --filter platform-environment dev     # 启动三维环境应用（:3400，无后端依赖）
pnpm --filter platform-admin dev           # 启动权限管理后台（:3500，需后端在 :8080）
pnpm --filter platform-audit-console dev   # 启动审计日志控制台（:3600，需后端在 :8080）
pnpm --filter common-api gen:api           # 从 docs/api/openapi.json 重新生成前端类型
```

### 微前端怎么跑

基座与子应用是**六个独立的 dev server**，调试微前端要同时起（基座按 `devPort` 去找子应用）：

```bash
# 多个窗口（或一个窗口用 turbo，但日志会混在一起）
pnpm --filter platform-portal dev         # http://127.0.0.1:3000  基座
pnpm --filter platform-log-console dev    # http://127.0.0.1:3200  子应用
pnpm --filter platform-event-console dev  # http://127.0.0.1:3300  子应用
pnpm --filter platform-environment dev    # http://127.0.0.1:3400  子应用
pnpm --filter platform-admin dev          # http://127.0.0.1:3500  子应用
pnpm --filter platform-audit-console dev  # http://127.0.0.1:3600  子应用
```

打开 <http://127.0.0.1:3000> 点导航即可挂载子应用；顶栏有「全局主题」「修改口令」「登出」三个按钮 —— 前者用来验证全局状态下发到子应用，后两者是**会话级动作**（不依赖任何菜单码，因此只有基座有）。

一键端到端验证（用真实 Chrome 走完整链路，含 WebGL 上下文释放断言）：

```bash
node scripts/verify-micro-frontend.mjs    # 需上面六个服务已在跑
```

> **子应用单独跑是常态，不是降级。** 让子应用独立启动（直接开 :3200 / :3300 / :3400 / :3500 / :3600）能立刻区分"问题在子应用本身"还是"问题在基座的加载链路"。基座里调试的代价是：任何入口改动都要等重新加载，且报错栈混在多个进程的日志里。对三维应用尤其如此 —— 被基座加载时排查渲染问题要同时面对三层日志。

### 三个控制台怎么跑

三个演示页都只依赖同一个后端，起一次即可：

```bash
# 1. 起后端（在 server/ 目录）
mvn -DskipTests install
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar

# 2. 起前端（在 web/ 目录，按需选择）
pnpm --filter platform-log-console dev    # http://127.0.0.1:3200  日志管道
pnpm --filter platform-event-console dev  # http://127.0.0.1:3300  事件管道
pnpm --filter platform-audit-console dev  # http://127.0.0.1:3600  审计管道（只读 + 演练）
```

开发期 `/api` 由 vite 代理到 `127.0.0.1:8080`，请求与服务端同源 —— 排查日志功能时被跨域问题挡住是最不划算的事。

> **同源对事件管道不只是方便。** 页面卸载时的兜底发送走 `fetch(..., { keepalive: true })`；若前端与服务端不同源，这个请求会带上预检，而预检在卸载阶段无法完成 → 静默丢数据。因此代理配置是这条链路的一部分，不是可选的调试便利。

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

> **跨应用共享的 Vite 片段也走 `common-config`**（`common-config/vite` 导出 `qiankunDevPreamble()`）。目前只有它一个导出，但两个子应用都要用 —— 与其复制两份，不如放在共享配置包里。加新片段前先问一句：**是不是有两个以上应用会用？** 否就不放。

> **pnpm 依赖隔离的两个坑**（已在 `common-config` 里规避，改配置时勿踩回去）：
>
> 1. Stylelint 的 `extends` 若用字符串写包名，子项目解析不到——已改用 `require.resolve` 解析为绝对路径。
> 2. ESLint 只从**当前工作目录**向上找配置，故工作区根也需要一份 `eslint.config.js`（lint-staged 在 `web/` 下执行 `eslint`）。

**Prettier 例外。** 它会自动向上查找最近的配置文件，所以用工作区根那份即可，子项目不用配。

### 测试约定（子应用组件测试）

三条**看起来像"测试写错了"、其实是机制**的约定，改测试前先对一遍：

- **`vi.mock('common-api')` / `vi.mock('common-tools/micro')` 必须用 `importOriginal` 保留真身导出**：
  `vi.mock('common-api', async (importOriginal) => { const actual = await importOriginal(); … })`。
  整体替换掉模块，会让被测组件 import 的**其它**导出变成 `undefined`，报
  `No "XXX" export is defined on the mock` —— 而那句话点的是**没被 mock 的那个导出**，极易查偏。
- **权限 store 必须有人接，而生产代码里接它的只有 `main.tsx`。** 测试不走 `main.tsx`，
  忘了接的界面**全空** —— 与"这个身份确实没权限"**长得一模一样**：不报错、不警告，
  连断言都不会失败（如果它断的是"看不见"）。要断言"有权限时看得到"，就得在测试里
  自己接一次（`platform-admin` / `platform-audit-console` 测试里那句
  `disconnect = connectPermissionSource()` 就是它），否则**那条"看得见"的断言永远不会红**。
- **替身要照真实响应形态造。** 这条是 `0044` 花掉一小时换来的：那次给改密接口写用例时，
  fetch 替身里填了 `status: 401`，于是"测出"一个很具体的缺陷（内核把 `40101` 当成令牌失效 →
  刷新 → 重试 → 清会话跳登录页），连注释、防护开关和一份 ADR 初稿都写出来了 ——
  而**本仓业务失败一律返回 HTTP 200**（`GlobalExceptionHandler` 全部返回 `R.fail`），
  那个 401 根本不会出现。**用替身造出来的缺陷，测出来的一定是替身的属性**；
  一个"只有在我的替身里才存在"的问题，再讨论一小时也不会有结论，因为它不在系统里。
  照着真实形态造还有一个附带好处：断言本身会变成那条约定的守卫
  （后端若改成按业务码回真实状态码，这里会红）。

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

**输出声明与实际产物不符时，`turbo` 不报错，只打一行 `WARNING no output files found for task …`。**
本仓有两处要显式声明 `outputs: []`：

- **`test` 任务本身。** 它原先声明 `outputs: ["coverage/**"]`，而各包的 `test` 脚本是
  `vitest run`（**不带 `--coverage`**）—— coverage 目录根本不会出现，于是**一次 `pnpm test` 打 8 行警告**
  （每包一行）。覆盖率的 `provider` 在各自的 `vite.config.ts` 里配着，真的要看覆盖率就跑单个包
  （`pnpm --filter <包名> test --coverage`），那份产物不需要进缓存。见 `docs/decisions/0039`。
- **`common-api#build`。** 该包的 `build` 是 `tsc --noEmit`，不产出任何文件（它在工作区内被
  **源码引用** —— `exports` 直接指向 `src/*.ts`），所以通用规则里的 `outputs: ["dist/**"]`
  对它永远匹配不到东西。**没有删掉那个脚本**：删掉会让 `pnpm build` 不再覆盖 `common-api`
  的类型检查，那是一次静默的覆盖缩减。见 `docs/decisions/0037`。

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

**校验的通过状态由 CI 持续保证，本文件不复述** —— 定义在 `.github/workflows/ci.yml` 的 `web`
job（ESLint → Stylelint → 全树格式 → 类型 → 单测 → 生产构建 → 产物自检），本地等价命令见
`CONTRIBUTING.md` 第八节。要确认"现在是不是绿的"，直接看 CI 即可。

> **这里以前有一张"逐包通过 + 用例数 / 产物体积"的表，已删除。** 它没有任何机制能发现自己过期：
> 实测时那张表的逐包用例加起来是 **485**，而同一份文档别处写的是 **549** —— 同一份文档里
> 自相矛盾，且多数单包数字早已漂移。**会变的值不写进活文档**，理由与权威源见 `docs/decisions/0039`。

下表只记**静态配置事实** —— 它们不随代码量变化，却决定了"某个检查为什么没覆盖某个包"：

| 包                        | 单测 | Stylelint                        | 生产构建 | dev 端口              |
| ------------------------- | ---- | -------------------------------- | -------- | --------------------- |
| `platform-portal`（基座） | 有   | 有                               | 有       | 3000                  |
| `platform-log-console`    | 有   | 有                               | 有       | 3200                  |
| `platform-event-console`  | 有   | 有                               | 有       | 3300                  |
| `platform-environment`    | 有   | 有                               | 有       | 3400                  |
| `platform-admin`          | 有   | 有                               | 有       | 3500                  |
| `platform-audit-console`  | 有   | 有                               | 有       | 3600                  |
| `common-tools`            | 有   | 有（仅 `src/` 与 `playground/`） | 有       | playground 3100       |
| `common-api`              | 有   | —（没有样式代码）                | 有       | —（无 dev server）    |
| `common-config`           | —    | —                                | —        | —（只有配置，无脚本） |

> **`common-api` 没有 Stylelint、`common-config` 一个脚本都没有** —— 所以 `pnpm stylelint` 跑的是
> **9** 个任务而不是 10 个：`turbo` 会**静默跳过**没有该脚本的包（不报错、也不提示）。这不是漏配。
> `common-api` 的 `build` 是 `tsc --noEmit`（零产物），见 `docs/decisions/0037`。

**微前端链路**另有一项端到端验证（`node scripts/verify-micro-frontend.mjs`，需后端与基座、以及要验证的子应用在跑）：真实登录 → 导航挂载子应用 → 全局状态下发（含真实会话身份）→ 子应用经基座代理请求后端 → 切换子应用 → 返回首页验证卸载清理（标记清空、`window.__MICRO_HOST__` 归 `undefined`；三维应用额外断言 WebGL 上下文已 lost、画布已移除）。这是一项**运行时**验证，与上面那张静态配置表无关。

> **`vite build` 不是"顺手跑一下"。** `platform-event-console` 首次交付时，它与 `platform-log-console` 共用一套版式，其中 `.pill--err` 引用了后者才有的 LESS 令牌 `@level-error`。dev server 与单元测试**都不会报错**（前者按需编译没人访问的样式分支，后者不碰样式），只有生产构建会失败：`variable @level-error is undefined`。**新增/复制版式的应用，构建这一项必须实际跑过。**

> **构建产物的 `base` 分环境**：各子应用的 `base` 在 dev 下是 `/`、在 build 下才带 `/log-console/`、`/event-console/`、`/environment/` 前缀。原因见 `docs/decisions/0012` 第 9 条 —— dev 保持 `/` 是为了让 `vite-plugin-qiankun` 的 `useDevMode` 能定位 `/@vite/client`，带前缀会导致热更新静默失效。改这几个文件时不要"顺手统一成同一个值"。

> **三维应用的画布不能由组件持有。** WebGL 上下文被 `forceContextLoss()` 之后那张 canvas 就永久不可再用，而 React 开发期 StrictMode 会双跑 effect（建 → 毁 → 建）—— 组件用 JSX 持有固定 `<canvas>` 必然在第二次踩到 `Cannot read properties of null (reading 'precision')`，且 jsdom 测试测不出来（无 GPU 走降级分支）。画布由 `createStage` 创建并随舞台销毁，详见 `docs/decisions/0013` 第 4 条。

> **切场景不换渲染器。** `stage.loadScene()` 只释放旧场景图、建新场景图，渲染器 / 画布 / 相机 / 控制器 / 环境贴图全部复用 —— 反复切场景不会产生第二个 WebGL 上下文，也不会累积 GPU 资源。场景清单用前端注册表（`Record<SceneId, SceneDefinition>` 钉住编译期完整性），元数据契约与三条护栏见 `docs/decisions/0014`。另有 `node scripts/verify-scene-switching.mjs` 用真实 Chrome 断言反复切换无显存泄漏。

> **可操控场景的相机跟随必须"整体平移"。** 场景通过可选的 `createControls(context)` 提供交互控制，只上报焦点；舞台把相机与轨道目标挪**同样的位移**，环绕姿态与距离才得以保留。左键旋转与滚轮缩放仍归 OrbitControls，右键在有控制器的场景里让给"设置行动终点"（`enablePan` 被关掉）。移动算法是独立纯内核（`scene/player/motor.ts`），小地图自绘 canvas 不走 React 状态。见 `docs/decisions/0015`，另有 `node scripts/verify-player-controls.mjs` 验证操控链路。

> **`openapi-typescript` 的 peer 警告**：其 peer 约束为 `typescript@^5.x`，本项目用 6.0.3，`pnpm install` 会打一条警告。生成功能已验证正常，升级生成器时需重新确认（见 `packages/common-api/README.md`）。

## 八、待补充

- 子应用 `platform-user` 尚未创建（qiankun 主应用与四个子应用的接线已完成，新增子应用按 `platform/platform-portal/README.md` 的三步走）。**前置依赖已全部就绪**：登录态、路由守卫、权限下发，以及子应用发已鉴权请求所需的认证桥（`docs/decisions/0018` 第⑨条）
- 认证链路的**前端消费已全线接通**：登录页、路由守卫、启动时恢复会话、登出、`userId` / `roles` / `permissions` 经 `HostGlobalState` 下发，以及第一个消费方 `platform-admin`（按钮级权限）。端到端验证：`node scripts/verify-portal-auth.mjs`（登录态链路）与 `node scripts/verify-admin-permissions.mjs`（两种身份的按钮差异 + 真实增删）。**已补齐**：权限域的**全部**管理面 —— `platform-admin` 现有用户 / 租户 / 资源 / 角色 / 用户组 / 组织六个标签页，用户页另有角色来源与数据范围反查（`common-api/src/access.ts` 相应地从 5 条路径扩到 20 条）；`platform-log-console` / `platform-event-console` 经 `createConsoleAuth` 接上认证桥（ADR-0026）。**已补齐**：**页面级权限过滤**（ADR-0031 —— 种子按 `AuthMenus` 建出 `MENU` / `PAGE` 行，基座 `BasicLayout` 按权限过滤导航且 `RequireMenu` 拦住 URL 直达）与**组织**管理面（ADR-0030，前端第六个标签页）。**尚缺**：401 掉线在真实后端下的恢复演练（单测已覆盖）
- **被基座加载时，子应用的 React 组件级热更新不生效**（样式热更新、以及子应用单独跑时的组件热更新都正常）。根因未定位，当前工作方式是改子应用组件时单独跑该子应用。详见 `docs/decisions/0012` 的「已知限制」
- 微前端**生产部署**尚未落地：现在只有 dev（`//localhost:<port>`）与占位 `prodEntry`，真实静态目录与网关规则需要与部署方案一起定
- 子应用的**样式隔离**当前没有保障（Vite dev 下框架级隔离拿不到），靠各应用自己的命名与 Less 令牌自律
- `common-tools` 的 http 内核目前没有自动重试、请求缓存与并发去重 —— 需要时按 `docs/decisions/0007` 的边界补，不要在各接口里散写
- 客户端日志目前只有采集与上报；**采样与脱敏**尚未实现，等有真实生产流量再做
- 行为事件的**采样、服务端二次脱敏、事件注册表**尚未实现；当前落库到普通数据库，接入数仓时替换落点即可（`docs/decisions/0011` 列了完整清单）
- 审计日志已全线打通（后端拦截器自动产生 + 只读查询 + `platform-audit-console`）；**采样、脱敏、保留期与归档、导出、失败告警**均未做，`docs/decisions/0019` 末节列了完整清单
- 四个子应用的 `auth/` 里，权限的 **React 适配层（`usePermissionState` / `usePermission`）仍是逐字重复的 4 份**（`platform-admin` / `platform-audit-console` / `platform-log-console` / `platform-event-console`）。**内核已下沉**：`common-tools/micro` 的 `createPermissionStore` 接管了会话订阅与"未就绪 ⇒ 空列表"这条规则，各应用只是把它接到自己的 `consoleAuth` 上。触发条件**已经到达过一次**（两个控制台成为第三、第四个消费方），当时下沉的是 store 而不是 Hook：再往下沉要给**刻意不引 React** 的 `common-tools` 加 React peer 与子路径导出，属于"再建一层"，与那一轮的收敛方向相反 —— 剩下的重复是 2~4 行正确且不会变的代码，**有意不做**（下次触发条件：适配层开始承载真实逻辑）
- 公共依赖已由 `pnpm-workspace.yaml` 的 catalog 统一管理，新增依赖务必走 catalog

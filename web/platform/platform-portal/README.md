# platform-portal

平台主应用，qiankun 容器。负责导航骨架、子应用挂载与全局状态下发。

## 职责边界

**该做的：**

- 维护全局状态（当前用户、主题、语言），通过 qiankun 的 `initGlobalState` 下发给子应用
- 提供导航骨架与子应用挂载点
- 子应用加载失败时的兜底提示

**不该做的：**

- 具体业务页面（那是子应用的事）
- 直接依赖任何子应用的代码

## 目录说明

```
src/
├── main.tsx              应用入口
├── App.tsx               根组件（挂载路由）
├── router/               路由表（子应用路由由注册表推导）
├── micro/                微前端接线（见下）
├── components/
│   └── MicroAppContainer.tsx  子应用挂载容器：加载态 / 失败兜底 / 卸载清理
├── layouts/              布局骨架（含导航与全局状态调试开关）
├── pages/                主应用自身的页面（首页、404 等）
├── styles/
│   ├── variables.less    设计令牌（由 vite 自动注入，无需手动 import）
│   └── index.less        全局样式
└── test/                 测试环境初始化
```

## 微前端接线

`src/micro/` 是主应用与 qiankun 之间的全部胶水，五个文件各管一件事：

| 文件        | 职责                                                              |
| ----------- | ----------------------------------------------------------------- |
| `types.ts`  | `MicroAppMeta`（注册信息）· `GlobalState` · `MicroAppProps`       |
| `apps.ts`   | 子应用注册表 `MICRO_APPS` · 按名查找 · 按环境解析 entry           |
| `state.ts`  | 全局状态：`initGlobalState` + **基座自己维护的本地副本**          |
| `loader.ts` | `mountMicroApp()`：组装 props、`loadMicroApp`、沙箱与资源过滤配置 |
| `index.ts`  | 出口                                                              |

**为什么用 `loadMicroApp` 而不是 `registerMicroApps` + `start`**、**为什么关掉 JS 沙箱**、
**样式隔离为什么指望不上框架** —— 这些取舍与理由写在
`docs/decisions/0012-微前端接入与子应用改造.md`，改这里之前先读它。

### 两个容易踩的点

1. **`initGlobalState` 不返回 `getGlobalState`。** 它只有 `onGlobalStateChange` /
   `setGlobalState` / `offGlobalStateChange`。想让子应用能读当前值，基座必须自己留一份副本 ——
   这就是 `state.ts` 里 `currentState` 存在的唯一理由。
2. **应用名是隐式契约。** `apps.ts` 里的 `name` 必须与子应用 `vite.config.ts` 中
   `vite-plugin-qiankun` 声明的名字逐字一致。写错**不会有任何报错**，只会一直卡在"加载中"。

### 全局状态的调试开关

页头右上角的「全局主题」与「登录」两个按钮**不是产品功能**，是全局状态下发的触发器：
基座自己还没有主题体系与登录态，它们存在的意义是让子应用侧"收到全局状态并响应"这段代码
可被触发、可被观察（子应用右下角会显示状态标记）。

## 新增一个子应用

1. 子应用侧按 `docs/decisions/0012` 改造入口与 `vite.config.ts`（导出 qiankun 生命周期、
   加 `vite-plugin-qiankun`、开 CORS、锁端口）；
2. 在 `src/micro/apps.ts` 的 `MICRO_APPS` 里加一条：`name` 与子应用声明一致、`routePath`
   是路由前缀、`devPort` 是开发期端口、`prodEntry` 是生产期静态目录；
3. 路由与导航会自动出现 —— **不需要改 `router/` 和 `layouts/`**。

## 命令

```bash
pnpm dev          # 开发（端口 3000）
pnpm build        # 类型检查 + 构建
pnpm lint         # ESLint
pnpm stylelint    # 样式规范
pnpm typecheck    # 仅类型检查
pnpm test         # 单元测试
```

后端接口通过 `/api` 前缀代理到 `http://localhost:8080`，配置见 `vite.config.ts`。
**子应用被加载后，它的 `/api` 请求也走这里** —— 子应用代码里只需要写相对路径。

## 样式约定

- 用 **Less**，BEM 命名（`.block__element--modifier`）。
- 设计令牌写在 `src/styles/variables.less`，由 `vite.config.ts` 的 `less.additionalData` 注入到每个 `.less` 文件——**样式文件里直接用变量，不要手动 `@import`**。
- 该变量文件只放变量，不产生 CSS 输出，因此不会造成重复代码。

## 调试微前端

需要同时起三个服务（子应用没起时入口会显示"加载失败"并提示端口）：

```bash
pnpm --filter platform-portal dev          # :3000  主应用
pnpm --filter platform-log-console dev     # :3200  日志控制台
pnpm --filter platform-event-console dev   # :3300  行为事件控制台
```

端到端自动验证（需要 Chrome）：

```bash
node scripts/verify-micro-frontend.mjs
```

> **开发子应用时请单独打开它（`:3200` / `:3300`）。** 被基座加载时 React 组件级热更新不生效
> （样式热更新正常），根因尚未定位，详见 ADR-0012 的「已知限制」。

# platform-portal

平台主应用，qiankun 容器。负责登录态、导航骨架、主题注入与子应用挂载。

## 职责边界

**该做的：**

- 维护全局状态（当前用户、权限集、主题），通过 qiankun 的 `initGlobalState` 下发给子应用
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
├── router/               路由表
├── layouts/              布局骨架
├── pages/                主应用自身的页面（首页、404 等）
├── components/           主应用私有组件
├── styles/
│   ├── variables.less    设计令牌（由 vite 自动注入，无需手动 import）
│   └── index.less        全局样式
└── test/                 测试环境初始化
```

## 样式约定

- 用 **Less**，BEM 命名（`.block__element--modifier`）。
- 设计令牌写在 `src/styles/variables.less`，由 `vite.config.ts` 的 `less.additionalData` 注入到每个 `.less` 文件——**样式文件里直接用变量，不要手动 `@import`**。
- 该变量文件只放变量，不产生 CSS 输出，因此不会造成重复代码。

## 新增子应用挂载点

在 `src/router/index.tsx` 的 children 中按前缀添加：

```tsx
{ path: 'user/*', element: <UserAppContainer /> }
{ path: 'admin/*', element: <AdminAppContainer /> }
```

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

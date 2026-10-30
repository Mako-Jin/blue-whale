# 前端开发指南

> 应用清单（端口 / base 路径 / 角色）权威来源：[service-catalog.md](service-catalog.md) §三。

## 工程结构

- Monorepo：pnpm workspace + Turborepo（[frontend/package.json](../frontend/package.json)）；Node 24 / pnpm 10。
- 应用族：
  - 基座：`frontend/platform-apps/whale-platform`（qiankun 主应用，8000）
  - 子应用：`platform-apps/whale-{portal,erp,oa,mes,pdm,im,pm,contract,doc}`（8101–8110）
  - 独立 SPA：`frontend/admin`（8001）、`frontend/ops`（8104）、`platform-apps/whale-{form-designer,workflow-designer,agent}`（8002–8004）
  - E2E：`frontend/e2e`（Playwright）
- 共享包（`@bluewhale/*`）：`shared-packages/whale-{common-tools,common-theme,log-sdk}`、`elemental/whale-ui`；tsup 构建，turbo 依赖链保证先构建。
- 冻结包（不升级不扩张）：`shared-packages/whale-common-three`、`games/whale-games-core`（[frozen-optional-modules.md](design/frozen-optional-modules.md)）。

## 常用命令

```bash
cd frontend
pnpm install
pnpm run dev        # turbo 并行启动全部应用
pnpm run build      # 全量构建
pnpm run lint       # eslint
pnpm run e2e        # Playwright（需后端就绪）
```

## 约定

- 微前端：qiankun `strictStyleIsolation` + CSS Modules；应用间通信 `initGlobalState`；子应用 `vite.config.ts` 需配 base 与 qiankun entry。
- 登录态：`@bluewhale` 共享认证包（`createAuthStorage`）；令牌存储方案的安全决策见待办 T-02（B-08①）。
- 按钮级权限：子应用以 `utils/permission.ts` 按 `sys:<app>:*:add/edit/delete` 控制操作按钮；接口侧由 `@RequirePermission` 兜底。
- 环境变量：可独立部署 app 应提供 `.env.example`（覆盖情况见盘点 T-11）。

## 门禁

lint + build 通过、关键变更补 vitest（CI 已接 `turbo run test`）；UI 重大变更留 Playwright 或截图证据。

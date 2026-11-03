# 前端开发指南

> 应用清单（端口 / base 路径 / 角色）权威来源：[service-catalog.md](service-catalog.md) §三。

## 工程结构

- Monorepo：pnpm workspace + Turborepo（[frontend/package.json](../frontend/package.json)）；Node 24 / pnpm 10。
- 应用族：
  - 基座：`frontend/platform-apps/whale-platform`（qiankun 主应用，8000）
  - 子应用：`platform-apps/whale-{portal,erp,oa,mes,pdm,im,pm,contract,doc,music,video}`（8101–8114）
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

- 微前端：qiankun `strictStyleIsolation` + CSS Modules；登录态与用户上下文经基座 `apps.ts` props 注入（`initGlobalState` 已移除，无消费方）；子应用 `vite.config.ts` 需配 base 与 qiankun entry。
- 登录态：`@bluewhale` 共享认证包；access 令牌内存化 + refresh 走 HttpOnly Cookie（`whale_refresh_token`）+ CSRF 双提交，方案已落地（T-02，见 [2026-10-30 devlog](devlog/2026-10-30.md)）。
- 按钮级权限：子应用以 `utils/permission.ts` 按 `sys:<app>:*:add/edit/delete` 控制操作按钮；接口侧由 `@RequirePermission` 兜底。
- 环境变量：各可独立部署 app 均提供 `.env.example`，全量登记见下文[环境变量登记](#环境变量登记-t-11)。

## 环境变量登记（T-11）

各可独立部署 app 目录下均有 `.env.example`（`frontend/admin`、`frontend/ops`、`frontend/platform-apps/whale-{platform,form-designer,workflow-designer,agent}`）；全部变量均可选：

| 变量 | 适用 app | 未配置行为 |
|---|---|---|
| `VITE_API_BASE_URL` | 全部独立 SPA（admin / ops / whale-platform / whale-form-designer / whale-workflow-designer / whale-agent） | 请求走同源相对路径：本地开发经 vite proxy 转发网关，生产由 Nginx 同源反代；仅跨域直连网关时才需配置 |
| `VITE_PROMETHEUS_URL` / `VITE_GRAFANA_URL` | ops | 监控页不显示对应跳转按钮 |
| `VITE_SUBAPP_ENTRY_<NAME>`（PORTAL / ERP / OA / MES / PDM / IM / PM / CONTRACT / DOC / MUSIC / VIDEO） | whale-platform | 回退 `//localhost:<port>`，仅限本地开发；生产部署必填 |
| `VITE_PLATFORM_TOKEN` | whale-platform | 回退占位 token，仅兼容旧子应用 host 逻辑；正常登录态经 props 注入 |

qiankun 子应用（whale-{portal,erp,oa,mes,pdm,im,pm,contract,doc,music,video}）不消费任何 `VITE_*` 变量：请求走同源相对路径（经基座反代），登录态经基座 `apps.ts` props 注入（`getToken` / `getUserId` / `getPermissions`）。

## 门禁

lint + build 通过、关键变更补 vitest（CI 已接 `turbo run test`）；UI 重大变更留 Playwright 或截图证据。

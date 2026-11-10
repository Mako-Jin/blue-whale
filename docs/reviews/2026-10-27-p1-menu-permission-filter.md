# T1 检查报告：前端静态菜单按权限裁剪

- 日期：2026-10-27
- 范围：`whale-platform` 基座、`whale-ops` 运维台、`whale-admin` 静态回退导航
- 关联：P1 #4 边界「菜单可见但接口 403」、待办清单 T1

## 1. 问题

`/auth/me` 已按权限返回菜单（`AuthService`：超管取全部启用菜单，普通用户按权限码裁剪并保留祖先），但三个前端仍存在**绕过该数据源**的静态菜单：

- 基座 `whale-platform` 的侧栏直接渲染 `menuConfig`，所有登录用户都能看到系统管理、ERP、OA、MES、PDM、消息、项目管理、合同入口；点击后业务接口按权限返回 403。
- 运维台 `whale-ops` 的 `NAV_ITEMS` 固定渲染监控/审计/告警，受限账号可见但接口 403。
- 后台 `whale-admin` 在 `/auth/me` 返回空菜单时回退到完整静态导航（含用户/角色/菜单/租户），同样不裁剪。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `whale-frontend/platform-apps/whale-platform/src/router/menu.ts` | `MenuNode` 增加可选 `permission`；为系统管理三个子项与 ERP/OA/MES/PDM/IM/PM/合同入口标注权限码；新增 `filterMenuByPermissions`：叶子无权限即隐藏、目录子项全隐藏时目录一并隐藏、未声明权限的入口（工作台/门户）始终可见 |
| `whale-frontend/platform-apps/whale-platform/src/layouts/MainLayout.tsx` | 侧栏菜单改为 `filterMenuByPermissions(menuConfig, user?.permissions ?? [])`，权限未加载时只显示无权限要求的入口，避免先闪现全量菜单 |
| `whale-frontend/platform-apps/whale-portal/src/pages/Dashboard.tsx` | ERP/OA/MES 入口卡片按 `sys:erp:item:list` / `sys:oa:leave:list` / `sys:mes:workorder:list` 过滤，避免卡片跳转后 403 |
| `whale-frontend/ops/src/layouts/BasicLayout.tsx` | `NAV_ITEMS` 增加 `permission`（`sys:monitor:list` / `sys:audit:log:list` / `sys:alert:record:list`）；按当前用户权限过滤后再渲染；`findSelectedKey` 改为在当前可见项内匹配，无可见项时不选中 |
| `whale-frontend/admin/src/layouts/AdminLayout.tsx` | 保留 `/auth/me` 动态菜单为主路径；仅在动态菜单为空时使用静态回退，并新增 `STATIC_NAV_PERMISSIONS` + `filterStaticNav` 按权限裁剪（用户/角色/菜单/租户），组件示例与仪表盘保持登录可见 |

菜单与接口权限码同源，均为后端 `sys_permission.permission_code`：

| 入口 | 权限码 |
|---|---|
| 用户/角色/菜单管理 | `sys:user:list` / `sys:role:list` / `sys:menu:list` |
| ERP / OA / MES / PDM | `sys:erp:item:list` / `sys:oa:leave:list` / `sys:mes:workorder:list` / `sys:pdm:product:list` |
| 消息 / 项目管理 / 合同 | `sys:im:chat` / `sys:pm:project:list` / `sys:contract:list` |
| 运维台监控/审计/告警 | `sys:monitor:list` / `sys:audit:log:list` / `sys:alert:record:list` |

`super_admin` 不受影响：`AuthService` 为其返回全部启用权限码，所有入口均命中。

## 3. 验证

- `pnpm --filter @bluewhale/whale-platform build` → 通过（`tsc -b && vite build`）；
- `pnpm --filter whale-ops build` → 通过；
- `pnpm --filter whale-admin build` → 通过（先重建 `@bluewhale/http` 的 `dist`，原有类型声明陈旧导致 `getTenantId` 报错，与本次改动无关）。
- `pnpm --filter whale-portal build` → 通过。
- 四个应用的 `eslint src --max-warnings 0` → 全部通过。

未执行浏览器实机：需登录受限账号验证菜单隐藏；当前环境未启动前端/后端，仅做类型检查与构建验证。

## 4. 边界

- ~~**只收口模块级导航**：子应用（ERP/OA/MES/PDM/PM/合同）内部的「新增/编辑/删除」按钮仍不按权限隐藏，见待办 F-10。~~
  2026-10-27 已补齐：PM/合同原有 `hasPermission`，本轮为 ERP/OA/MES/PDM 增加 `utils/permission.ts` 并按 `sys:{app}:*` 权限码隐藏新增/编辑/审批/删除按钮，4 个子应用 lint+build 通过，接口仍由 `@RequirePermission` 兜底。
- 直接输入 URL 仍可进入页面，接口按权限返回 403；本轮不做路由级 403 拦截页。
- 门户本身保持登录可见（统计卡片对无权限接口已用 `—` 兜底）；门户内 ERP/OA/MES 入口卡片已按权限过滤。
- 运维台/后台受限账号若已停留在无权限页面，刷新后菜单隐藏但页面内容仍由接口 403 兜底。

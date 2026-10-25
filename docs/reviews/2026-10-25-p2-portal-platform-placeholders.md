# Wave 5.46 轨 G：基座与门户占位收口检查报告（2026-10-25）

> 对应台账：[priority-execution-ledger-2026-10-25.md](../plans/priority-execution-ledger-2026-10-25.md) 序号 7（P2）
> 对应计划：[wave5.46-phase4-business-apps.md](../plans/wave5.46-phase4-business-apps.md) 轨 G（G3/G4/G5）
> 范围：`whale-portal` 三处占位、`whale-platform` 三个 `PlaceholderPage`

---

## 一、变更清单

### 1. whale-portal

| 文件 | 变更 | 结论 |
|---|---|---|
| [client.ts](../../frontend/platform-apps/whale-portal/src/api/client.ts) | token 键由 `whale_token` 改为基座实际键 `whale_platform_access_token`；新增只读 `getCurrentUser()` / `hasPermission()` | **修复真实缺陷**：原键与基座不一致，子应用请求实际未携带 `Authorization`，接真实接口后必然 401 |
| [api/workflow.ts](../../frontend/platform-apps/whale-portal/src/api/workflow.ts) | 新增 `pageMyTaskPage`（含 total）、`countRunningInstances`、`approveTask`、`rejectTask` | 统计与审批数据源 |
| [api/notice.ts](../../frontend/platform-apps/whale-portal/src/api/notice.ts) | 新增告警记录分页（`/alert/record/page`）与 IM 会话（`/im/conversations`） | 通知数据源 |
| [api/monitor.ts](../../frontend/platform-apps/whale-portal/src/api/monitor.ts) | 新增 `/monitor/service/list` 服务计数 | 系统服务数数据源 |
| [pages/Dashboard.tsx](../../frontend/platform-apps/whale-portal/src/pages/Dashboard.tsx) | 四项统计全部接真实接口；按权限选择通知数据源；无权限/接口失败展示 `—` | 占位 `0` 清零 |
| [pages/Todo.tsx](../../frontend/platform-apps/whale-portal/src/pages/Todo.tsx) | 列表按当前账号 + `status=0` 过滤；「去处理」打开审批抽屉，支持意见 + 同意/驳回并刷新 | 占位动作清零 |
| [pages/Notice.tsx](../../frontend/platform-apps/whale-portal/src/pages/Notice.tsx) | 删除静态 `MOCK_NOTICES`；告警记录优先、IM 会话兜底、均无权限时空态 | 静态 mock 清零 |
| [main.tsx](../../frontend/platform-apps/whale-portal/src/main.tsx) | 包裹 antd `App`，为 `App.useApp()` 提供上下文 | 审批反馈可用 |

**统计口径**

| 卡片 | 数据源 | 权限/降级 |
|---|---|---|
| 我的待办 | `GET /workflow/task/page?assignee={当前用户}&status=0` 的 `total` | 无当前用户或请求失败 → `—` |
| 进行中流程 | `GET /workflow/instance/page?status=0` 的 `total` | 无 `sys:workflow:instance:list` 或失败 → `—` |
| 未读通知 | 有 `sys:im:chat` → IM 会话未读求和；否则有 `sys:alert:record:list` → `FIRING` 告警总数 | 两者均无或失败 → `—` |
| 系统服务数 | `GET /monitor/service/list` 数组长度 | 无 `sys:monitor:list` 或失败 → `—` |

> 说明：仓库内**没有独立「通知中心」后端**。本轮按 wave5.46 G5「优先 `/alert/record/page`，无则站内消息接口」落地为告警记录 + IM 会话双数据源；真正的个人站内通知中心仍是后续独立需求。

### 2. whale-platform

| 文件 | 变更 | 结论 |
|---|---|---|
| [api/system.ts](../../frontend/platform-apps/whale-platform/src/api/system.ts) | 新增用户/角色分页、菜单树只读查询 | 复用既有 whale-auth 接口 |
| [pages/system/UserList.tsx](../../frontend/platform-apps/whale-platform/src/pages/system/UserList.tsx) | 关键字搜索 + 分页表格（用户名/昵称/手机号/邮箱/状态/创建时间） | 真实页 |
| [pages/system/RoleList.tsx](../../frontend/platform-apps/whale-platform/src/pages/system/RoleList.tsx) | 关键字搜索 + 分页表格（编码/名称/描述/排序/状态/创建时间） | 真实页 |
| [pages/system/MenuList.tsx](../../frontend/platform-apps/whale-platform/src/pages/system/MenuList.tsx) | 菜单树表格（名称/路径/组件/类型/权限码/排序/状态） | 真实页 |
| [router/routes.tsx](../../frontend/platform-apps/whale-platform/src/router/routes.tsx) | 三个路由由 `PlaceholderPage` 切换为真实页 | 占位清零 |
| `pages/PlaceholderPage.tsx` | 删除（无其他引用） | 组件清零 |

> 写操作（新增/编辑/删除/授权）**不**在基座复刻，仍由管理端 `whale-admin` 承担，避免两套 CRUD 分叉。

---

## 二、验证记录

| 项 | 命令 | 结果 |
|---|---|---|
| 门户构建 | `pnpm build`（whale-portal） | **通过**（tsc + vite，2093 modules） |
| 门户 lint | `pnpm lint`（whale-portal，`--max-warnings 0`） | **通过**（0 error / 0 warning） |
| 基座构建 | `pnpm build`（whale-platform） | **通过**（tsc + vite，3905 modules） |
| 基座 lint | `pnpm lint`（whale-platform，`--max-warnings 0`） | **通过**（0 error / 0 warning） |
| 残留占位扫描 | `grep PlaceholderPage / MOCK_NOTICES / TODO`（两应用 src） | 0 命中 |

未执行：浏览器实机 E2E（需网关 8080 + whale-auth/workflow/alert/im/monitor 与 MySQL/Redis/Nacos 全部就绪；当前环境 Redis 6379 / Nacos 8848 未监听）。

---

## 三、边界与残留

1. **通知中心**：无独立后端，当前为告警 + IM 双源；个人通知（审批抄送、系统公告）需后续新增通知表与接口。
2. **审批体验**：门户抽屉仅支持「意见 + 同意/驳回」；转办/委派/回退/加签及绑定表单编辑仍由 `whale-workflow-designer`（8003）承担。台账原文「跳转 `/workflow/task/{id}`」在本轮改为门户内抽屉闭环，因基座尚未挂载工作流设计器子应用，直接跳转无落点。
3. **统计范围**：`/workflow/instance/page` 与 `/monitor/service/list` 均为全局视图（非“我的”），当前仅作概览数字并受权限码约束。
4. **基座系统页只读**：与 admin 的完整 CRUD 并存，需在部署文档中明确「基座=查询入口，admin=维护入口」。

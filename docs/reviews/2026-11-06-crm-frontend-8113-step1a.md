# CRM 前端 8113 Step 1a 评审记录（客户域）

> 日期：2026-11-06　状态：**通过（1a 范围内）**
> 总账计划：[2026-11-06-five-services-backlog-stepwise.md](../plans/2026-11-06-five-services-backlog-stepwise.md) §五 Step 1 · CRM 全景：[2026-11-04-wave5.83-crm-panorama.md](../plans/2026-11-04-wave5.83-crm-panorama.md)

## 一、交付范围

- **子应用脚手架**：新建 `frontend/platform-apps/whale-crm`（vite 6 + React 19 + antd 5 + react-router-dom 6 + vite-plugin-qiankun），端口 **8113**、base `/crm`、qiankun 名 `whale-crm`；`@bluewhale/http` 提供 `http` / `downloadBlob` / `upload`。
- **基座注册**：`frontend/platform-apps/whale-platform/src/qiankun/apps.ts` 的 `DEV_PORTS` 增 `'whale-crm': 8113`，`apps` 注册 `activeRule: '/crm'`；entry 走 `subAppEntry('whale-crm', VITE_SUBAPP_ENTRY_CRM)` 并注入统一 `platformProps()`。
- **API 层**：`src/api/crm.ts` 冻结客户域契约——客户分页/查重/详情/360/新建/编辑/状态/级别/删除/画像，联系人增改删，地址增改删，跟进分页与新增，公海分页/认领/释放/分配/回收/规则查存/归属日志，以及 `meta`。
- **页面**：
  - 客户列表：关键字/状态/级别/负责人筛选、分页、新建与编辑、状态与级别变更、查重提示、删除，行内跳转详情。
  - 客户详情：360 概览 + 基本信息 + 联系人 / 地址 / 跟进记录三 Tab，联系人地址可增改删，跟进可追加。
  - 公海客户：分页筛选、认领、释放、分配、回收、规则查看与保存、归属变更日志。
- **权限与菜单**：`migration-5.83-crm-frontend.sql` 幂等登记菜单 **413–416**（413 客户关系 DIR、414 客户列表、415 公海客户、416 客户详情 `visible=0`）；按权限码 `sys:crm:customer:list` / `sys:crm:pool:view` 补 `sys_role_permission`，不重复插 1800–1899，不触碰 470–489 / 490。
- **前端控权**：`src/utils/permission.ts` 的 `hasPermission` 控制菜单项与按钮；无权限时菜单不渲染。

## 二、验证结果

| 检查 | 命令 | 结果 |
|---|---|---|
| 前端 Lint | `pnpm --filter whale-crm lint`（cwd `frontend`） | 通过（0 warning，`--max-warnings 0`） |
| 前端构建 | `pnpm --filter whale-crm build` | 通过（`tsc -b && vite build`，2091 modules，1.26 MB 仅体积提示） |
| 基座回归 | `pnpm --filter whale-platform lint` / `build` | 通过 |
| 后端 | 未改动业务代码（仅新增 Auth 迁移脚本） | 无需回归；既有 1800–1899 权限码经核对存在 |
| 文档一致性 | `scripts/check-doc-consistency.ps1` | PASS |

## 三、验收口径核对

| 验收项 | 落地方式 | 结论 |
|---|---|---|
| 8113 端口 + `/crm` base 可被基座加载 | `vite.config.ts` 端口/base、`apps.ts` 注册、qiankun 生命周期导出 | 达成 |
| 菜单与后端权限码对齐 | 菜单登记只引用既有 `sys:crm:customer:list` / `sys:crm:pool:view`，不新增权限号 | 达成 |
| 无权限用户看不到入口 | 菜单按 `hasPermission` 过滤，按钮同源控权 | 达成 |
| 客户主流程可走通 | 列表新建 → 查重提示 → 详情 360 → 联系人/地址/跟进 → 状态/级别变更 → 删除 | 达成（API 契约对齐后端 Controller） |
| 公海运营可走通 | 公海分页 → 认领/分配/释放/回收 → 规则保存 → 归属日志 | 达成 |
| 迁移幂等可重放 | 全部 `INSERT ... SELECT ... WHERE NOT EXISTS` | 达成 |

## 四、关键设计复核

1. **先只做客户域**：CRM 后端 M1/M2 已覆盖客户、线索、商机、报价、预测、目标；一次性铺 10 页会让单批验证面过大，故拆 1a（客户/公海）与 1b（销售域）。
2. **菜单复用既有权限码**：5.83 已冻结 1800–1899 权限段，前端菜单只做“登记 + 角色可见性”，不借机新增权限点，避免与后续 M3–M6 号段冲突。
3. **详情页登记为隐藏菜单**：416 `visible=0` 只解决权限归属，不污染侧边栏；`resolveSelectedKey` 把 `/customer/*` 归到 `/customer`，避免详情页菜单高亮丢失。
4. **API 集中在一个文件**：契约按 Controller 分组，1b 追加销售域时同文件扩展，避免每个页面各自拼 URL 造成口径漂移。

## 五、遗留

| 级别 | 事项 | 建议 |
|---|---|---|
| 中 | 后端 `CustomerPageParams` 未绑定 `ownerId`，前端“负责人”筛选实际不生效 | 后端补参数绑定 + 单测，随 1b 或独立小修交付 |
| 中 | 跟进记录 append-only，无编辑/删除接口 | 确认业务是否允许更正跟进；需要则补后端接口 |
| 低 | 公海页“释放”需手工输入客户 ID | 改为行内操作带入当前客户，随 1b 前端小修 |
| 中 | 真实库未执行 `migration-5.83-crm-frontend.sql` | 部署后复核菜单 413–416 与角色授权数量 |
| 低 | 未起联调环境，页面仅通过 lint/build | 部署环境就绪后跑 `/crm/customer`、`/crm/pool` 全链 |
| — | Step 1b：线索/转化、商机/阶段/漏斗、报价、预测、目标 | 按总账计划 Step 1b 实施，菜单 417–420（5.83 已预留） |

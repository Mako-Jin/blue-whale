# CRM 前端 8113 Step 1b 评审记录（销售域）

> 日期：2026-11-06　状态：**通过（1b 范围内）**
> 总账计划：[2026-11-06-five-services-backlog-stepwise.md](../plans/2026-11-06-five-services-backlog-stepwise.md) §五 Step 1 · Step 1a 记录：[2026-11-06-crm-frontend-8113-step1a.md](2026-11-06-crm-frontend-8113-step1a.md)

## 一、交付范围

- **页面**（4 路由覆盖 5 业务域）：
  - `/lead` 线索管理：筛选分页、新建/编辑、状态变更、分配、标记无效、转化、线索动态抽屉。
  - `/opportunity` 商机管理：列表 + 漏斗视图 Tab、新建/编辑、阶段推进、阶段历史抽屉。
  - `/quote` 销售报价：列表、明细行编辑、提交、修订、详情查看。
  - `/forecast` 销售预测与目标：预测 Tab（生成/调整/调整记录）+ 目标 Tab（增删改/完成度/调整记录）。
- **API**：`src/api/crm.ts` 按 Controller 分组追加销售域函数与类型，客户域签名未改。
- **菜单**：`migration-5.83-crm-sales-frontend.sql` 幂等登记 **417–420**，父菜单 413；不新增权限点。
- **控权**：菜单按 `sys:crm:lead:list` / `opportunity:list` / `quote:list` / `forecast:list` 过滤；目标 Tab 额外要求 `sys:crm:target:list`；写操作按既有 `add/edit/delete` 等权限码控制。

## 二、验证结果

| 检查 | 命令 | 结果 |
|---|---|---|
| Lint | `pnpm --filter whale-crm lint`（cwd `frontend`） | 通过（`--max-warnings 0`） |
| 构建 | `pnpm --filter whale-crm build` | 通过（仅 vite 包体积提示） |
| 类型 | 新增代码无 `any`、无未使用变量 | 通过（eslint 0 warning） |
| 契约核对 | 对读 `LeadQuery` / `OpportunityQuery` / `QuoteQuery` / `ForecastQuery` / `SalesTargetQuery` / `LeadSaveCommand` / `QuoteSaveCommand` | 字段名与分页（`current/size`）一致 |
| 依赖 | `package.json` 未改 | 未引入新依赖 |

## 三、验收口径核对

| 验收项 | 落地方式 | 结论 |
|---|---|---|
| 线索全流程 | 列表 → 新建/编辑 → 状态 → 分配 → 无效 → 转化 → 动态 | 达成（转化弹窗字段以 DTO 为准） |
| 商机阶段与漏斗 | 漏斗 Tab + 阶段推进 + 阶段历史 | 达成 |
| 报价主流程 | 明细行编辑 → 提交 → 修订 → 详情 | 达成 |
| 预测与目标 | 预测生成/调整、目标增删改/完成度/调整记录 | 达成 |
| 菜单与权限对齐 | 只引用 5.84–5.86 既有权限码，迁移幂等 | 达成 |
| 无权限不可见 | 菜单与按钮均 `hasPermission` 控权 | 达成 |

## 四、遗留

| 级别 | 事项 | 建议 |
|---|---|---|
| 中 | 日期/时间字段为文本输入，格式 `yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss` | 下一轮统一 DatePicker，避免手输格式错 |
| 中 | 未起联调环境，页面仅 lint/build + 静态契约核对 | 部署后跑线索→商机→报价→预测→目标全链冒烟 |
| 低 | 线索敏感字段（电话/邮箱）在无 `sys:crm:sensitive:view` 时不回填 | 后端忽略 null 保留原值，需实机确认一次 |
| 低 | 详情/阶段历史/调整记录用抽屉，未设独立路由 | 需要深链时再补路由 |
| 中 | 真实库未执行 `migration-5.83-crm-sales-frontend.sql` | 部署后复核菜单 417–420 可见性与角色差异 |
| — | M3–M6（商务/售后/营销/分析） | 按 CRM 全景计划与总账 Step 7 推进 |

# CRM 前端 8113 实施计划（M0-7 骨架 + M1/M2 页面）

## Context

CRM 后端 M0–M2 已完成并通过 123 项测试，但全景计划中的 `frontend/platform-apps/whale-crm`（端口 8113、base `/crm`）仍是规划态，基座也没有 `/crm` 路由、菜单与网关代理前缀。因此 CRM 当前无法从平台进入，M1 客户主数据与 M2 销售过程的接口能力对用户不可见。

本切片交付一个可验收、可上线的完整前端：基座可进入 `/crm`，按权限渲染菜单与按钮，覆盖客户主数据（列表/详情/画像/跟进/公海/360）与销售过程（线索/商机/漏斗/报价/预测/目标）全部 M1/M2 页面。不新增后端接口、不改数据库。

## 范围与不做

- 做：子应用脚手架、请求层、权限过滤、8 个菜单页面 + 客户详情/360 子路由、基座注册（apps.ts / routes.tsx / menu.ts / 网关代理）、文档与验证。
- 不做：M3 合同/订单、M4 售后、M5 营销、M6 分析；导入导出与客户合并（全景计划中的 M1-4/M1-9 后置项）；数据库菜单种子（基座使用静态 `menu.ts`，避免与 `sys_menu` 双源）。

## 一、新建子应用 `frontend/platform-apps/whale-crm`

参照 `frontend/platform-apps/whale-pdm` 原样复制结构，仅改名称/端口/base：

| 文件 | 要点 |
|---|---|
| `package.json` | 与 `whale-pdm` 依赖一致（`@bluewhale/*`、antd、react-router-dom、vite-plugin-qiankun），name=`whale-crm`，scripts `dev/build/preview/clean/lint` |
| `tsconfig.json` | 继承 `../../tsconfig.base.json`，`include: ["src","vite.config.ts"]` |
| `vite.config.ts` | microAppName=`whale-crm`，`server.port=8113`，`base:'/crm'`，CORS 头 |
| `index.html` | 标题 `Whale CRM - 客户关系管理`，挂载 `#root` |
| `src/main.tsx` / `src/public-path.ts` / `src/index.css` / `src/vite-env.d.ts` | 与 PDM 一致；qiankun `mount` 注入 `getToken` |
| `src/api/client.ts` | 复制 PDM：`createHttpClient` + `setPlatformHostProps`，解包 `Result` 信封 |
| `src/utils/permission.ts` | 复制 PDM：从 `PLATFORM_USER_KEY` 读取权限码 |
| `src/utils/format.ts` | 新增：金额千分位、日期/时间展示与 `<input type="date|datetime-local">` 双向转换、等级/状态/阶段/渠道文案映射 |

### 请求层与类型（字段严格对齐后端 DTO）

- `src/api/types.ts`：`PageResult<T>{records,total,current,size}`；`CustomerLevel=A|B|C|D`、`CustomerStatus=ACTIVE|INACTIVE|MERGED`、`PoolStatus=OWNED|IN_POOL`、`FollowUpChannel=CALL|VISIT|EMAIL|IM|WECHAT|OTHER`、`LeadStatus=NEW|CONTACTED|QUALIFIED|CONVERTED|INVALID`、`OpportunityStage=CONTACT|PROPOSAL|NEGOTIATION|WON|LOST`、`QuoteStatus=DRAFT|PENDING_APPROVAL|APPROVED|REJECTED`、`ForecastPeriodType=MONTH|QUARTER`、`ScopeType=USER|DEPT`。
- `src/api/customer.ts`：`/crm/customer`（page / duplicate-check / {id} / {id}/360 / POST / PUT / status / level / DELETE / profile GET+PUT）、`/crm/customer/{customerId}/contact|address`、`/crm/customer/contact|address/{id}`、`/crm/follow-up`（page?customerId、POST）、`/crm/pool`（page、claim、release?remark、assign、recycle、rule GET+PUT、owner-logs）。
- `src/api/sales.ts`：`/crm/lead`（page、duplicate-check、{id}、POST、PUT、status、assign、invalid、convert、activity GET+POST）、`/crm/opportunity`（page、funnel、{id}、{id}/history、POST、PUT、{id}/stage）。
- `src/api/commerce.ts`：`/crm/quote`（page、{id}、POST、PUT、{id}/submit、{id}/revise）。
- `src/api/performance.ts`：`/crm/forecast`（page、{id}、{id}/adjustments、generate、{id}/adjust）、`/crm/sales-target`（page、{id}、{id}/completion、{id}/adjustments、POST、PUT、DELETE）。

所有 DTO 直接复用已读取后端字段：客户 `CustomerSaveCommand/View/DetailOnly/360`、联系人、地址、画像 `DecisionChainEntry{contactId,role,influence,remark}`、跟进、线索、商机、报价 `QuoteSaveCommand{opportunityId,title,currency,validUntil,discountAmount,remark,lines[]}`、预测 `pipelineAmount/bestAmount/commitAmount/weightedAmount`、目标 `targetAmount/completionRate/achievedAmount`。

## 二、页面（`src/App.tsx` 注册路由与权限菜单）

| 路由 | 页面 | 关键交互 / 权限 |
|---|---|---|
| `/customer` | `pages/customer/List.tsx` | 关键字/等级/状态/来源筛选、分页；新建时先查重，HARD 阻断、SOFT 二次确认；行内编辑、等级、停用、删除；按钮受 `customer:create/update/level/status/delete` 控制 |
| `/customer/:id` | `pages/customer/Detail.tsx` | 档案 Descriptions + Tabs：联系人（主联系人唯一）、地址（默认收货/账单）、画像（决策链必须选本客户联系人）、跟进（append-only + 下次动作） |
| `/customer/:id/360` | `pages/customer/ThreeSixty.tsx` | 档案/联系人/画像/最近跟进/商机摘要/负责人变更；`unavailableIntegrations` 非空时 Alert「数据暂不可用」，不伪造合同/ERP 数据 |
| `/pool` | `pages/pool/Index.tsx` | 公海分页、领取、释放、分配（负责人 ID）、手动回收、规则编辑（未跟进/无商机天数、等级、领取上限、启用） |
| `/lead` | `pages/lead/Index.tsx` | 列表/筛选、新建（电话/邮箱查重提示）、推进 CONTACTED/QUALIFIED、分配、标记无效、转化（可选既有客户/商机金额/预计关闭）、活动抽屉 |
| `/opportunity` | `pages/opportunity/Index.tsx` | 列表、新建/编辑、阶段流转（仅顺序 CONTACT→PROPOSAL→NEGOTIATION→WON，任意未关闭→LOST 需原因）、阶段历史抽屉 |
| `/opportunity/funnel` | `pages/opportunity/Funnel.tsx` | 阶段数量/金额/转化率、输单数量金额、在途合计；按负责人与预计关闭日期筛选 |
| `/quote` | `pages/quote/Index.tsx` | 列表、新建/编辑（明细行动态增删、金额实时合计）、详情、提交审批、改价新版本；已审批只读 |
| `/forecast` | `pages/forecast/Index.tsx` | 快照列表、生成/重算（周期/周期类型/范围/负责人/部门）、手工调整（PIPELINE/BEST/COMMIT + 原因）、调整记录抽屉 |
| `/sales-target` | `pages/target/Index.tsx` | 目标列表、新建、调整金额（含原因）、完成率/已赢单金额、调整记录、删除 |

页面统一使用 antd `Table/Form/Modal/Drawer/Tabs/Descriptions/Tag/Progress/Alert/Statistic`；金额展示 `utils/format`，日期输入使用原生 input 以避开 dayjs 直接依赖；空态/错误态用 `Alert` + `Empty`；按钮隐藏依据 `hasPermission`，接口仍由后端 403 兜底。

## 三、基座接入（4 个既有文件）

1. `frontend/platform-apps/whale-platform/src/qiankun/apps.ts`：`DEV_PORTS` 增加 `'whale-crm': 8113`；`apps` 增加 `{name:'whale-crm', entry: subAppEntry('whale-crm', import.meta.env.VITE_SUBAPP_ENTRY_CRM), activeRule:'/crm', container:'#subapp-container', props: platformProps()}`，同步 `SubAppName` 联合类型。
2. `frontend/platform-apps/whale-platform/src/router/routes.tsx`：新增 `{ path: 'crm/*', element: <SubAppContainer /> }`。
3. `frontend/platform-apps/whale-platform/src/router/menu.ts`：新增 `{ key:'/crm', label:'CRM 应用', children:[客户管理/公海/线索/商机/销售漏斗/报价/预测/目标] }`，权限码分别用 `sys:crm:customer:list`、`pool:view`、`lead:list`、`opportunity:list`、`quote:list`、`forecast:list`、`target:list`。
4. `frontend/platform-apps/whale-platform/vite.config.ts`：`GATEWAY_PREFIXES` 增加 `'/crm'`（网关转发 9007），保持 `gatewayBypass` 对 HTML 导航旁路。

权限码以后端 `CrmPermissions` 与迁移 `migration-5.83/5.84/5.85/5.86-crm*.sql` 为准（1810–1859、1800）。

## 四、文档（硬约束：文档先行 + 回填）

1. 新建 `docs/specs/2026-11-06-wave5.84-crm-frontend/spec.md`：需求项（对照全景 M0-7/M1-10/M2）、验收项、待做项、已修正问题项。
2. 新建 `docs/specs/2026-11-06-wave5.84-crm-frontend/tasks.md`：任务分解与勾选状态。
3. 新建 `docs/reviews/2026-11-06-wave5.84-crm-frontend.md`：lint/build 结果、页面与权限矩阵、已知缺口。
4. 回填 `docs/plans/2026-11-04-wave5.83-crm-panorama.md`：§0 前端行改为「8113 已交付 M1/M2 页面」；M0-7、M1-10 标注完成；M1-4/M1-9 保持待做。
5. 回填 `docs/plans/roadmap.md` 对应 CRM 条目与 `docs/devlog/2026-11-06.md`。

## 五、验证

```powershell
pnpm --filter whale-crm lint
pnpm --filter whale-crm build
pnpm --filter whale-platform build
```

验收口径：

1. 基座登录后左侧出现「CRM 应用」，无权限子项自动隐藏；访问 `/crm/customer` 可加载子应用。
2. 客户新建：同名/同电话 HARD 冲突被阻断；SOFT 冲突确认后可保存；等级/状态/删除按钮与权限一致。
3. 客户详情四 Tab 可读写；主联系人唯一约束生效；跟进提交后客户最近跟进时间更新；360 显示降级提示而非报错。
4. 公海领取/释放/分配/规则保存可用；线索状态推进与转化返回客户/商机 ID；商机非法跳阶段被拒绝；漏斗转化率与阶段计数一致。
5. 报价至少一行明细、金额合计正确，可提交审批与改价；预测可生成与调整；目标完成率展示正确。
6. lint 0 warning、两个应用 build 成功。

## 六、提交

`git add` 指定目录与文档 → `feat(crm): add frontend app with customer and sales pages` → `git push origin main`。

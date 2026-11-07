# PDM / MES / OA / CRM / ERP 待办总账与分步实施计划

> 日期：2026-11-06　状态：**已批准，执行中**（Step 1 → Step 7 逐批实施，每批独立验证、提交、推送）
> 进度：**Step 1 已完成**（2026-11-06，CRM 前端 8113 Step 1a 客户域 + Step 1b 销售域）；**Step 2 已完成**（2026-11-06，ERP MRP：BOM 展开 / 净需求 / 计划订单 / 追溯 / 运行记录）；**Step 3 已完成**（2026-11-07，3a PDM 工艺路线快照 / 工作中心 / 产能负荷与甘特 / 订单工序快照；3b 工序报工 / 倒冲领料 / 退料报废）；**Step 4 已完成**（2026-11-07，ERP 生产订单下发 MES / MES 完工与进度回传 / 双向幂等 / 对账页）；**Step 5 已完成**（2026-11-07，OA M2 首批：考勤 / 报销 / 用车 / 用章 / 采购申请后端 + 前端 10 页）；**Step 6 进行中**（6a 已完成 2026-11-07）；Step 7 未开始。
> 依据：[roadmap.md](roadmap.md) 状态对账（2026-11-06）、[PDM 计划 §十九](2026-11-04-wave5.80-pdm-panorama.md)、[MES 计划](2026-11-04-wave5.79-mes-panorama.md)、[OA 计划](2026-11-04-wave5.82-oa-panorama.md)、[CRM 计划](2026-11-04-wave5.83-crm-panorama.md)、[ERP 计划](2026-11-03-wave5.78-erp-panorama.md)
> 纪律：每批动工前先做一次代码对账（文档标注 ⏸ 的项可能已被后续波次补齐），只做确认缺失的；只提交本批文件；每批结束输出四段式报告。

---

## 一、结论

| 服务 | 已完成 | 剩余主体 |
|---|---|---|
| PDM | M0–M5 全量 + 2026-11-06 收口（图文档导出、集成告警） | **无仓库内代码缺口**；6 项外部依赖（CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调）见 PDM §十九 |
| ERP | M0–M6（含 M2 七期、M3 一阶段、M4 一阶段、M5 一期） | M3 剩余 **MRP/CRP/预测增强/生产增强**；M2 剩余 **批次追溯/财务扩展**；ERP↔MES 工单下发与完工回传 |
| MES | M0–M5 全量（70 表、370+ 文件）+ Step 6a（有限产能排程 / 开工门禁 / 谱系冲销 / 设备维度追溯，2026-11-07） | Step 6b：数据范围上下文过滤、线边领料联动、归档物理清理；采集推送通道（SSE）已于 M5-B 交付 |
| OA | M0 + M1A 全量 + M1B-1/M1B-2 + M2 首批（考勤/报销/用车/用章/采购，2026-11-07） | 出差预算超限条件分支（报销侧变量已落地）；**M3–M6 未开始**（协同、公文档案、文化投票、报表试点）；M1A-6 移动端适配 |
| CRM | M0 + M1 + M2 **后端** | **前端 8113 完全未建**（仓库无 `frontend/platform-apps/whale-crm`）；M1-4 客户合并、M1-9 导入导出未做；M3–M6 全部未开始 |

> 结论：真正“有后端无界面”的只有 CRM；真正“里程碑级空白”的是 OA M3–M6、CRM M3–M6、ERP M3 剩余。PDM 已无代码可做，MES 只剩增强项。

---

## 二、分步顺序（依赖优先，不插队）

| 步骤 | 主题 | 范围 | 依赖 | 预估批量 |
|---|---|---|---|---|
| **Step 1** | CRM 前端 8113 | 脚手架（8113、qiankun 注册、登录/布局/路由）、客户/联系人/跟进/公海、线索/转化/商机/阶段/漏斗、报价/预测/目标共 10 页 + API 层 | 后端 M1/M2 已完成，仅缺前端 | 1 波（可拆 1a 客户域 / 1b 销售域）——**1a、1b 均已完成** |
| **Step 2** | ERP MRP | BOM 展开（取 PDM `/pdm/internal/bom/**` 快照）、计划订单生成、净需求与需求追溯、MRP 运算 Job 与结果页 | PDM 快照契约已交付 | 1 波 |
| **Step 3** | ERP CRP + 生产增强 | 工作中心/产能负荷（取 PDM 工艺路线）、排程与甘特；工序报工、倒冲领料、退料/报废 | PDM M3 工艺已交付 | 2 波已完成（3a/3b，2026-11-07） |
| **Step 4** | ERP ↔ MES 打通 | ERP 生产订单下发 MES、MES 完工/报工回传 ERP、双向幂等与对账页 | ERP M3、MES M1–M2 已完成 | 1 波（已完成 2026-11-07） |
| **Step 5** | OA M2 首批 | 考勤 + 报销 + 用车/用章/采购申请的领域化（复用 Workflow/Form）；M1B 预算超限条件分支 | Workflow 条件表达式运行期求值 | 2–3 波——**已完成 2026-11-07**（5a 考勤 / 5b 报销 / 5c 用车用章采购 + ERP/Contract 内网契约 / 5d 前端 10 页与文档；V7 10 表 + V8 13 表、权限 1680–1717、菜单 373–382；出差侧预算条件分支仍待 Workflow 运行期求值） |
| **Step 6** | MES 遗留增强 | 有限产能排程、采集推送通道（SSE/WS）、数据范围上下文过滤、谱系冲销/线边联动/设备追溯/开工门禁、归档清理 | auth `data-scope` 就绪（第 3 项） | 2 波——**6a 已完成 2026-11-07**（有限产能排程 + 开工门禁 + 谱系冲销 + 设备维度追溯；采集推送 SSE 已于 M5-B 交付）；6b 待做 |
| **Step 7** | OA M3–M6 + CRM M3–M6 + ERP 批次/财务扩展 | 协同办公、公文档案、文化投票、报表试点；CRM 合同联动/售后/营销/分析；ERP 批次追溯与财务四切片（D5 定稿后） | 业务决策 D5/D6/D8/D11、真实联调环境 | 多波，按决策推进 |

**默认执行顺序**：Step 1 → Step 2 → Step 3 → Step 4 → Step 5 → Step 6 → Step 7。若用户指定某一步优先，按指定执行。

---

## 三、每步的通用验收与交付

| 检查 | 口径 |
|---|---|
| 后端 | 对应模块 `mvn -o -pl <module> test` 全绿；Checkstyle 无新增违规；ArchUnit 通过 |
| 前端 | 对应应用 `pnpm --filter <app> lint` / `build`（cwd `frontend`） |
| 迁移 | 新增 Flyway 只追加不改历史；真实库可重放 |
| 文档 | 计划/roadmap/service-catalog/devlog/review 同步；`check-doc-consistency.ps1` → PASS |
| 交付 | 仅提交本批文件；推送 `origin/main`；四段式报告（需求项/完成项/待做项/已修正问题项） |

---

## 四、工作区红线（每批必须遵守）

- 5.89 邮箱与 5.91 地图未提交内容不得 `git add`：`backend/whale-services/pom.xml`、`whale-gateway/application.yml`、`deploy/monitoring/prometheus.yml`、`infrastructure/docker/mysql/init/01-init-databases.sql`、`backend/whale-services/whale-map/`、`migration-5.91-map-m0.sql`。
- `.trae/documents/crm-frontend-8113-implementation.md` 与 `docs/plans/2026-06-06-mail-server-and-map-service.md` 不提交；stash `pre-pull-local-edits-20261106` 不得 pop。
- 新功能一律先文档（计划/ADR/权限号段）后代码；权限号段与菜单号段先查 `naming-conventions`，避免与 5.89/5.91 冲突。

---

## 五、Step 1 启动清单（1a + 1b 已完成）

1. ~~对账：读 CRM 后端 10 个 Controller 的端点和 DTO，冻结 `api/crm.ts` 契约。~~（已完成：客户域 + 销售域契约）
2. ~~脚手架：`frontend/platform-apps/whale-crm`（8113、base `/crm`），注册进基座 `apps.ts` 与菜单。~~（已完成）
3. ~~页面：客户（列表/详情/联系人/跟进/公海）、线索/转化、商机/阶段/漏斗、报价、预测、目标。~~（已完成：4 路由 + 页内 Tab）
4. 权限：CRM 已有 1800–1899，按页面按钮补齐 `list/detail/add/edit/delete/export` 缺失项（只追加）。本次未新增权限号，仅登记菜单 413–420。
5. 验证：前端 lint/build 已通过（1a、1b 各自验证）。

---

## 六、Step 2 实施明细（ERP MRP，2026-11-06 已完成）

> 范围：按 PDM 内网契约拉取已发布 BOM 快照 → 多级用量展开 → 结合库存可用量计算净需求 → 生成计划订单并保留需求追溯 → MRP 运行记录可查询、可重跑 → 前端结果页。本期**不做**计划订单转采购/生产订单、CRP、自动读取 MPS、在途与安全库存占用、异步线程池。

### 6.1 数据表（`whale-erp` Flyway `V13__mfg_mrp.sql`）

| 表 | 用途 | 关键约束 |
|---|---|---|
| `erp_mfg_mrp_run` | MRP 运行记录（RUNNING/COMPLETED/FAILED、统计数、错误信息、起止时间） | UK(tenant_id, run_no) |
| `erp_mfg_mrp_run_line` | 运行输入行（物料、BOM 号/版本、数量、需求日期） | 支撑重跑与复现；UK(tenant_id, run_id, line_no) |
| `erp_mfg_mrp_planned_order` | 计划订单（采购/生产建议、毛需求/可用/净需求、层级、BOM、需求日期） | UK(tenant_id, order_no) |
| `erp_mfg_mrp_trace` | 需求追溯（来源需求行、BOM path、depth、单位用量、损耗率、贡献量） | IDX(run_id, planned_order_id) |

### 6.2 算法口径

- 快照行按深度优先、`path` 形如 `1` / `1.1`；累计用量 `cum(path) = cum(parentPath) × qty × (1 + lossRate/100)`，根路径取 1，`lossRate` 为空按 0，4 位小数 HALF_UP。
- 净需求 `net = max(0, 毛需求 − 可用量)`；可用量为 ERP 现有库存余额跨仓库汇总（不含在途、安全库存与已分配）。
- 同一零件在一次运行中只扣一次可用量；仅 `net > 0` 生成计划订单。
- 计划订单类型：本次快照中任一行 `parentPartNo` 命中的零件为 `PRODUCTION`，否则 `PURCHASE`。
- PDM 不可达、快照缺行、快照产品编码与需求物料不一致、子件未在 ERP 维护 → 运行标记 `FAILED` 并记录错误，不产出错误计划。

### 6.3 接口与权限

| 方法与路径 | 权限码 | 说明 |
|---|---|---|
| `POST /erp/mfg/mrp/runs` | `sys:erp:mrp:run`（542） | 执行运算（同步），返回运行记录 |
| `POST /erp/mfg/mrp/runs/{id}/rerun` | 542 | 按原输入重跑 |
| `GET /erp/mfg/mrp/runs/page`、`/runs/{id}` | `sys:erp:mrp:run:list`（543） | 运行记录分页/详情（含输入行与计划订单概览） |
| `GET /erp/mfg/mrp/orders/page`、`/orders/{id}` | `sys:erp:mrp:order:list`（544） | 计划订单分页/详情（含追溯） |

- 菜单 104「MRP 运算」、105「计划订单」；脚本 `migration-5.78-erp-mrp.sql` 幂等，545–548 与菜单 106 预留。
- 角色：`tenant_admin / erp_manager / erp_mfg` 授 542–544；`erp_member / erp_finance` 只授 543/544。

### 6.4 前端

`frontend/platform-apps/whale-erp` 新增 `MrpRunPage`（可编辑需求行 + 运行记录 + 详情抽屉 + 重跑）与 `MrpOrderPage`（筛选 + 计划订单 + 追溯抽屉），`api/mfg.ts` 增加 MRP 类型与 6 个函数，`App.tsx` 注册菜单与路由映射。

### 6.5 验收

- 后端 `mvn -o -pl backend/whale-apps/whale-erp test` 全绿（含多级累计、损耗率、净需求不为负、可用量只扣一次、类型判定、PDM 失败 FAILED、重跑）。
- 前端 `pnpm --filter whale-erp lint` / `build` 通过。
- 文档一致性脚本：除 `service-catalog.md` 待补 V13 记录外其余通过；该文件同时含未提交的商城 5.94 改动，本批不混提，待 5.94 落地时一并回填。

---

## 七、Step 3 实施明细（ERP CRP + 生产增强，3a 已完成 2026-11-07）

> 分批：**3a（本批已完成）**= PDM 工艺路线内网快照 + 工作中心 + 产能负荷/排程甘特 + 生产订单工序快照；**3b（待做）**= 工序报工、倒冲领料、退料/报废。
> 方案过程稿：`.trae/documents/erp-crp-step3-implementation.md`（不提交）。

### 7.1 数据表（`V14__mfg_crp.sql`）

| 表 | 用途 | 关键约束 |
|---|---|---|
| `erp_mfg_work_center` | 工作中心（编码、名称、日产能工时、效率率、工作日、启停） | UK(tenant_id, code)；效率 0–100；工作日 `1–7` 逗号串 |
| `erp_mfg_production_operation` | 生产订单工序快照（工序号、名称、工作中心、标准/准备工时、计划/合格/报废量、首末报工时间） | UK(tenant_id, order_id, step_no) |
| `erp_mfg_production_order` | 追加 `routing_no` / `routing_version` / `routing_status` | ALTER 追加列，允许空值（历史订单不受影响） |

- `routing_status`：`NONE`（无已发布工艺）、`SNAPSHOT`（已冻结）、`FAILED`（PDM 拉取失败，可重试同步）。

### 7.2 PDM 内网契约

| 方法与路径 | 说明 |
|---|---|
| `GET /pdm/internal/process/routes/by-part/{partCode}` | 取该零件最新已发布（`RELEASED`）工艺路线快照；无则 404 |
| `GET /pdm/internal/process/routes/{routeNo}/versions/{versionNo}` | 按路线号 + 版本号取指定快照 |

- 响应 `{code,data}`，`data` 含 `routeNo/versionNo/partCode/partName/releasedAt` 与 `steps[]`（`stepNo/name/workCenter/standardHours/setupHours`）；PDM 只读、不新增表。

### 7.3 CRP 算法口径

- **无限负荷**：计划窗口内按工作中心工作日均摊「准备工时 + 标准工时 × 待完工数量」，用于识别每日超载（`overloaded`）与区间外/未排程工时。
- **有限前推排程**：按订单/工序顺序逐日占用剩余产能，超出当日则顺延至下一工作日；排到窗口结束仍未完成标记 `delayed`。
- 缺少工作中心档案的订单计入 `excludedOrderCount`，不参与负荷与排程；区间为空或跨度 > 62 天抛 `CRP_INVALID`；公开以今天的日期为默认起始，默认窗口 30 天。
- 结果为**同步计算、不落库**；相同输入可复现。

### 7.4 接口与权限

| 方法与路径 | 权限码 | 说明 |
|---|---|---|
| `GET /erp/mfg/work-centers/page`、`/all` | `sys:erp:mfg:workcenter:list`（545） | 工作中心分页/下拉 |
| `POST /erp/mfg/work-centers` | `sys:erp:mfg:workcenter:add`（546） | 新增（编码租户唯一，冲突 40942） |
| `PUT /erp/mfg/work-centers/{id}` | `sys:erp:mfg:workcenter:edit`（547） | 编辑 |
| `DELETE /erp/mfg/work-centers/{id}` | `sys:erp:mfg:workcenter:delete`（548） | 删除（不存在 40443） |
| `GET /erp/mfg/crp/load` | `sys:erp:mfg:crp:view`（549） | 产能负荷 + 排程（`fromDate/toDate/workCenterCode` 可选） |
| `POST /erp/mfg/production/{id}/routing/sync` | `sys:erp:production:operation:sync`（550） | 重新同步并冻结工艺路线（仅 DRAFT/RELEASED 且无报工） |

- 脚本 `migration-5.78-erp-crp.sql`：权限 545–550、菜单 106「工作中心」/107「产能负荷」，全部 `NOT EXISTS` 幂等；`tenant_admin / erp_manager / erp_mfg` 授全量，`erp_member` 只授 545/549。
- 建单时 best-effort 拉取工艺：无已发布路线 → `NONE`；PDM 不可达/契约失败 → `FAILED`，均不阻断建单。

### 7.5 前端

`frontend/platform-apps/whale-erp` 新增 `WorkCenterPage`（列表/搜索/状态筛选/新增编辑/删除）与 `CrpPage`（日期区间 + 工作中心筛选、负荷表与纯 CSS 甘特条带），`api/mfg.ts` 增补工作中心/CRP/工序类型与 8 个函数，`App.tsx` 注册 `work-center`、`crp` 菜单与初始路由。

### 7.6 验收

- 后端 `mvn -o -pl backend/whale-apps/whale-erp test` 全绿（479 + 14 新增）；PDM `mvn -o -pl backend/whale-apps/whale-pdm test` 全绿（含快照 4 新增）。
- 前端 `pnpm --filter whale-erp lint` / `build` 通过。
- `scripts/check-doc-consistency.ps1` → PASS（补记 V14）。
- 3b 见 §7.7。

### 7.7 3b：生产增强（2026-11-07 完成）

#### 数据表（`V15__mfg_production_enhance.sql`）

| 变更 | 用途 |
|---|---|
| `erp_mfg_production_material` 追加 `backflush` / `issued_cost` | 倒冲标记与累计已领成本（退料/报废按成本冲减） |
| 新建 `erp_mfg_production_material_adj` | 用料调整记录（`RETURN`/`SCRAP`、单号租户唯一、数量/金额/原因/操作人） |

#### 业务规则

- **工序报工**：仅 `RELEASED/IN_PROGRESS`；非首道工序累计合格量不得超过前道；报工后订单转 `IN_PROGRESS`。
- **完工约束**：存在工序时「已完工 + 本批 ≤ 末道工序累计合格量」；无工序订单维持原有行为。
- **倒冲领料**：完工时按 `需求 × (已完工+本批)/计划数量` 自动领料（最后一批取整单需求结清尾差），出库成本计入材料成本；领料仓库存不足整单回滚。
- **退料**：仅 `RELEASED/IN_PROGRESS`；按 `已领成本 × 退料量 / 已领量` 冲减，全退结清尾差；回冲领料仓并写 `PRODUCTION_RETURN` 流水。
- **报废**：同口径扣减已领数量与成本，不回库存，仅写调整记录。
- 领料/完工凭证口径不变：完工凭证仍按当时「累计材料成本 ÷ 计划量 × 完工量」结转。

#### 接口与权限

| 方法与路径 | 权限码 |
|---|---|
| `POST /erp/mfg/production/{id}/operations/{stepNo}/report` | `sys:erp:production:operation:report`（551） |
| `POST /erp/mfg/production/{id}/material/return` | `sys:erp:production:return`（552） |
| `POST /erp/mfg/production/{id}/material/scrap` | `sys:erp:production:scrap`（553） |

- 脚本 `migration-5.78-erp-production-enhance.sql` 幂等插入 551–553；`tenant_admin / erp_manager / erp_mfg` 授权，`erp_member` 不授；不新增菜单。

#### 前端

生产订单详情改为「用料 / 工序」Tabs：用料显示已领成本与倒冲标记，支持退料/报废行弹窗；工序表支持报工弹窗；详情头显示工艺路线与状态，`NONE/FAILED` 可「同步工艺」；建单用料行新增「倒冲」勾选。

#### 验收

- 后端测试全绿（新增领域 6 例 + 应用 3 例）；前端 `lint` / `build` 通过；文档一致性 PASS（补记 V15）。
- 未做：联产品/副产品、MES 回传、计划订单转单（归 M3 剩余后续批次）。

---

## 八、Step 4 实施明细（ERP ↔ MES 打通，2026-11-07 已完成）

> 分批：**4a** ERP 表/下发/回执；**4b** MES 进度事件/投递器/内部查询；**4c** 对账接口/权限/前端；**4d** 文档回填、验证与提交。四批同窗口完成。
> 方案过程稿：`.trae/documents/erp-mes-step4-implementation.md`（不提交）。

### 8.1 数据表（`V16__mfg_mes.sql`）

| 变更 | 用途 | 关键约束 |
|---|---|---|
| `erp_mfg_production_order` 追加 `mes_sync_status` / `mes_work_order_code` / `mes_synced_at` / `mes_last_error` | MES 下发状态（`NOT_SYNCED`/`PUSHED`/`FAILED`）、MES 工单号、最近成功下发时间、失败摘要 | 默认 `NOT_SYNCED`，历史订单不受影响 |
| 新建 `erp_mfg_mes_callback` | MES 回调流水（完工/进度、事件 ID、处理态 `APPLIED`/`REJECTED`/`DUPLICATE`、原始 payload、错误、接收时间） | UK `uk_mfg_mes_callback_event(tenant_id, source_event_id)` + 单号/状态索引 |
| 新建 `erp_mfg_mes_progress` | MES 进度快照（MES 状态、计划/报工/合格/完工数量、任务明细 JSON、最近事件 ID、接收时间） | UK `uk_mfg_mes_progress_order(tenant_id, erp_order_no)`，按单覆盖写 |

### 8.2 双向契约

| 方向 | 方法与路径 | 说明 |
|---|---|---|
| ERP → MES | `POST /mes/internal/work-order`（复用已有） | 下达后推送；MES 按 `erpOrderNo` 幂等建单/更新 |
| MES → ERP | `POST /erp/internal/production-order/{erpOrderNo}/completion` | 完工回传，body `{eventId, payload}` |
| MES → ERP | `POST /erp/internal/production-order/{erpOrderNo}/progress` | 进度回传（只记快照） |
| ERP → MES | `GET /mes/internal/work-order/{erpOrderNo}` | 对账时拉取 MES 实时进度；无工单返回 404 |

- 内网统一 `InternalTokenSupport.restClient()` + `DiscoveryClient("whale-mes")` + `WebHeaders.TENANT_ID`；响应 `{code,data}`，`code=0` 成功、`404` 视为无数据。
- MES 发件箱 `MesOutboxDispatcher` 扩展 `WorkOrderProgress` → `/progress`、`WorkOrderCompleted` → `/completion`；后台投递无租户上下文，租户号由 payload `tenantId` 携带并写入请求头。
- 进度事件幂等键按「工单 ID + 报工/合格/完工数量 + 状态」生成，数量或状态变化才产生新事件。

### 8.3 业务口径

- **下达后置推送**：`release()` 只发布 `ProductionOrderReleasedEvent`，由 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` 触发推送；外部调用失败不撤回下达、落 `FAILED` 并记录错误，可由对账页重推。
- **完工按增量入库**：`delta = MES 累计完工 − ERP 已完工`；`delta ≤ 0` 记 `DUPLICATE`（重复事件或已入库），订单不存在/状态不允许记 `REJECTED`，人工确认可重放。
- **入库事务隔离**：实际调用 `productionOrderAppService.complete` 用 `TransactionTemplate(PROPAGATION_REQUIRES_NEW)`，失败只回滚入库、不改写回调流水，便于定位与人工处理。
- **进度只记快照**：MES 报工进度只写 `erp_mfg_mes_progress` 供对账展示，不回写 ERP 工序报工，避免与 ERP 手工报工双计。
- **幂等**：回调统一先查 `(tenant_id, source_event_id)`，命中直接记 `DUPLICATE` 返回；唯一键兜底并发。
- **对账差异**：`ERP 已完工 − MES 累计完工` 不为 0 时标记差异，同页可重推/拉取/人工确认入库。

### 8.4 接口与权限

| 方法与路径 | 权限码 | 说明 |
|---|---|---|
| `GET /erp/mfg/mes/reconcile/page` | `sys:erp:mes:reconcile:list`（554） | 对账分页（关键字/日期区间/MES 状态/下发状态） |
| `GET /erp/mfg/mes/reconcile/{orderId}` | 554 | 详情：订单、回调流水、任务快照、差异 |
| `POST /erp/mfg/mes/reconcile/{orderId}/push` | `sys:erp:mes:push`（555） | 手动重推 |
| `POST /erp/mfg/mes/reconcile/{orderId}/pull` | `sys:erp:mes:pull`（556） | 手动拉取并刷新快照 |
| `POST /erp/mfg/mes/reconcile/{orderId}/completion-apply` | `sys:erp:mes:complete`（557） | 人工确认完工入库 |

- 脚本 `migration-5.78-erp-mes.sql`：权限 554–557、菜单 **108 MES 对账**，全部 `NOT EXISTS` 幂等；`tenant_admin / erp_manager / erp_mfg` 全授，`erp_member` 仅 554。
- 内网回传接口 `/erp/internal/**` 不登记业务权限，由内网令牌保护。
- 错误码：`MES_GATEWAY_FAILED=40030`、`MES_CALLBACK_INVALID=40031`、`MES_CALLBACK_STATE_CONFLICT=40946`（40026/40029/40444/40945 已被既有场景占用）。

### 8.5 前端

`frontend/platform-apps/whale-erp` 新增 `MesReconcilePage`：关键字/日期/MES 状态/下发状态筛选，差异标记，详情抽屉（回调流水 + 任务明细），行内重推/拉取，人工确认入库弹窗；`api/mfg.ts` 增补对账类型与 5 个函数、生产订单视图补 4 个 MES 字段；`App.tsx` 注册 `mes-reconcile` 菜单与 `/erp/mfg/mes-reconcile` 路由。

### 8.6 验收

- `mvn -o -pl backend/whale-apps/whale-erp test`：**509 项全绿**（新增集成服务 7 例）；Checkstyle/ArchUnit 随构建通过。
- `mvn -o -pl backend/whale-apps/whale-mes test`：全绿（新增进度查询 2 例）。
- `pnpm --filter whale-erp lint` / `build`：通过。
- `scripts/check-doc-consistency.ps1`：PASS（`service-catalog.md` 补记 V16）；whale-mall V5 为商城 5.94 未跟踪文件，不在本批范围。
- 未做：MES 侧对账视图（ERP 为准）、进度回写 ERP 工序报工（刻意不做）、实机联调（待环境）。


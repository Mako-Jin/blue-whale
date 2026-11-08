# Step 3 实施方案：ERP CRP + 生产增强

> 状态：**待用户确认**（确认后按 3a → 3b 两批实施，每批独立提交/推送）
> 上游依据：[五服务分步计划](../../docs/plans/2026-11-06-five-services-backlog-stepwise.md) Step 3；[ERP 全景 §13.4](../../docs/plans/2026-11-03-wave5.78-erp-panorama.md)
> 本文件为实施文档，按仓库规则**不提交**。

---

## 一、背景与目标

Step 2 已完成 MRP 一期（BOM 展开、净需求、计划订单、追溯）。Step 3 补齐 M3 剩余的两块：

1. **CRP**：工作中心与产能、按 PDM 已发布工艺路线生成工序快照、产能负荷（无限负荷）与排程甘特（有限前推）。
2. **生产增强**：工序报工、倒冲领料、退料、报废。

边界保持不变：车间扫码执行、设备采集、现场检验仍归 MES；Step 4 再做 ERP↔MES 工单下发与回传。

---

## 二、批次划分

| 批次 | 范围 | 提交范围 |
|---|---|---|
| **3a** | PDM 工艺路线内网契约；工作中心 CRUD；生产订单工序快照；CRP 负荷与排程；前端工作中心页 + 产能负荷页 | PDM 契约、V14、ERP 后端 CRP、权限 545–549 / 菜单 106–107、前端两页 |
| **3b** | 工序报工；倒冲领料；退料；报废；生产订单详情扩展；前端报工/退料/报废弹窗 | V15、ERP 生产增强、权限 550–553、前端生产页增强 |

**明确不做**：计划订单转采购/生产订单；MPS 自动驱动 CRP；有限产能优化算法（APS）；班组/设备级排程；联产品/副产品；人工与制造费用分摊、标准成本差异（D5 未定）；MES 工单下发与回传（Step 4）；报废损失独立凭证。

---

## 三、跨服务契约：PDM 工艺路线（3a）

PDM 目前只有 BOM 内网契约，**无工艺路线内网契约**。本次在 PDM 增加只读快照端点，复用既有 `/pdm/internal/**` 内网 token 过滤器与网关 internal-deny，不叠功能权限。

| 方法与路径 | 说明 |
|---|---|
| `GET /pdm/internal/process/routes/by-part/{partCode}` | 取该零件**最新已发布**（`RELEASED`，`version_seq` 最大）路线快照；无则 404 |
| `GET /pdm/internal/process/routes/{routeNo}/versions/{versionNo}` | 按编号+版本取已发布快照，供复现；非 RELEASED 返回 404 |

快照 VO（新增 `PdmPrcRouteSnapshotVO`）：`routeNo/versionNo/partCode/partName/releasedAt/steps[]`；步骤字段 `stepNo/name/workCenter/standardHours/setupHours`。字段顺序固定，便于幂等与对账。

修改点：`PdmPrcRouteService` 增 `snapshot(routeNo, versionNo)`、`latestReleased(partCode)`；新增 `PdmInternalProcessController`；服务实现复用现有 `PdmPrcRouteMapper` 查询与 `PdmPrcStepMapper` 组装，不新建表、不加迁移。

ERP 侧新增端口 `RoutingCatalog` + 模型 `RoutingSnapshot/RoutingStep`，适配器 `PdmRoutingCatalogImpl` 仿照 [PdmBomCatalogImpl](../../backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/mfg/infrastructure/client/PdmBomCatalogImpl.java)：`InternalTokenSupport.restClient()` + `DiscoveryClient("whale-pdm")` + `X-Tenant-Id`；失败转新错误码 `ROUTING_FETCH_FAILED`。

---

## 四、数据库

### 4.1 `V14__mfg_crp.sql`（3a）

| 对象 | 关键字段 / 约束 |
|---|---|
| `erp_mfg_work_center` | `code`（租户内唯一）、`name`、`department`、`daily_capacity_hours DECIMAL(18,2)`、`efficiency_rate DECIMAL(5,2)`（默认 100）、`work_days VARCHAR(20)`（ISO 1–7，逗号分隔，默认 `1,2,3,4,5`）、`status`、`remark`；`UK(tenant_id, code)` |
| `erp_mfg_production_operation` | `order_id`、`step_no`、`name`、`work_center_code`、`standard_hours`、`setup_hours`、`planned_quantity`、`qualified_quantity`、`scrapped_quantity`、`status`（`PENDING/IN_PROGRESS/COMPLETED`）、`first_report_at`、`last_report_at`；`UK(tenant_id, order_id, step_no)` |
| `ALTER erp_mfg_production_order` | ADD `routing_no VARCHAR(64)`、`routing_version VARCHAR(32)`、`routing_status VARCHAR(16)`（`NONE/SNAPSHOT/FAILED`） |

规则：仅 CREATE TABLE IF NOT EXISTS / INSERT IGNORE / ALTER ADD COLUMN；禁止 DROP/TRUNCATE/DELETE。

### 4.2 `V15__mfg_production_enhance.sql`（3b）

| 对象 | 关键字段 / 约束 |
|---|---|
| `ALTER erp_mfg_production_material` | ADD `backflush TINYINT NOT NULL DEFAULT 0`、`issued_cost DECIMAL(18,2) NOT NULL DEFAULT 0` |
| `erp_mfg_production_material_adj` | `order_id`、`doc_no`（租户内唯一）、`adj_type`（`RETURN/SCRAP`）、`line_no`、物料快照、`quantity`、`amount`、`reason`、`operator_id`、`occurred_at`；`UK(tenant_id, doc_no)`、`IDX(tenant_id, order_id)` |

---

## 五、领域与应用设计（ERP `mfg`）

### 5.1 新增领域对象

- `WorkCenter`：编码/名称/日产能/效率/工作日/状态；不变量：日产能 > 0，效率 0–100，工作日必须为 1–7 且非空。
- `ProductionOperation`：工序快照 + 累计合格/报废数量；`report(qualified, scrapped)` 校验累计 ≤ 计划量；`remainingQuantity()`、`completed()`。
- `ProductionMaterial` 扩展：`backflush`、`issuedCost`；新增 `returnQuantity(qty)` / `scrapQuantity(qty)`（扣减已领与已领成本，禁止超过已领量）。
- `RoutingSnapshot/RoutingStep`、`CrpLoadBucket`、`CrpScheduleSlot` 值对象。
- `CrpCalculator`（纯领域计算器，无 Spring）：输入订单+工序+工作中心+日期区间，输出日负荷与工序排程。

### 5.2 `ProductionOrder` 聚合扩展

- 聚合内新增 `operations`、`routingNo/routingVersion/routingStatus`。
- `reportOperation(stepNo, qualified, scrapped)`：状态必须 `RELEASED/IN_PROGRESS`；首笔报工置 `IN_PROGRESS`；累计不得超过计划量；非首道工序的**累计合格量不得超过前道累计合格量**。
- `complete(qty, date)` 新增约束：**存在工序时**，`已完工 + 本批 ≤ 末道工序累计合格量`；无工序订单保持原行为（兼容历史订单）。
- `returnMaterial(lineNo, qty)` / `scrapMaterial(lineNo, qty)`：状态 `RELEASED/IN_PROGRESS`；扣减已领数量与已领成本。
- 领料完成校验拆分：非倒冲料仍需全部领完；倒冲料在完工时按完工比例自动领料（见 5.4）。

### 5.3 工序快照生成

- 建生产订单时按产出物料编码调用 `RoutingCatalog.latestReleased`：
  - 成功：写入 `operations`，`routingStatus=SNAPSHOT`；
  - PDM 返回 404：订单照常创建，`routingStatus=NONE`；
  - PDM 不可达/解析失败：订单照常创建，`routingStatus=FAILED`，记录日志。
- 新增 `POST /erp/mfg/production/{id}/routing/sync`（权限 551）：`DRAFT/RELEASED` 且无报工时可重新拉取，覆盖工序快照。
- 已下达但无工序的订单：不参与 CRP，CRP 返回 `excludedOrderCount` 提示。

### 5.4 倒冲领料

- 用料可标记 `backflush=true`（建单入参新增）。
- 完工时先对倒冲料自动领料：本次目标累计领料 = `requiredQuantity × (completedQuantity + batchQty) / plannedQuantity`（4 位 HALF_UP；最后一批取整单剩余量），差额按移动加权平均出库、累计 `issuedCost/totalMaterialCost`；库存不足按 `STOCK_NOT_FOUND/NEGATIVE_STOCK` 回滚。
- 非倒冲料维持“完工前必须领完”。

### 5.5 CRP 算法

输入：`fromDate/toDate`（默认今天起 30 天，最多 62 天）、可选 `workCenterCode`。取 `RELEASED/IN_PROGRESS` 且有余量、有工序的订单。

**产能口径**：某工作中心某日产能 = `daily_capacity_hours × efficiency_rate / 100`；不在 `work_days` 的日期产能 0。

1. **负荷（无限负荷，用于超载识别）**：每道工序工时 = `setupHours + standardHours × 剩余数量`；在订单计划窗口 `[planStartDate, planEndDate（缺省=planStartDate）]` 内按工作日**平均分摊**；按工作中心/日期汇总。利用率 = 负荷/产能；负荷 > 产能标记 `overload=true`。
2. **排程（有限前推，用于甘特）**：订单按 `planStartDate, orderNo` 升序；工序按 `stepNo` 升序；工序起始日 = `max(订单计划开工日, 前道结束日)`；逐日占用该工作中心**剩余产能**，占满顺延至下一工作日，得到每道工序开始/结束日；结束日晚于订单计划完工日标记 `delayed=true`。
3. 超出查询区间的工时计入 `beyondHorizonHours`，不生成区间外条带。

**结果**：`CrpLoadView{workCenterCode, date, capacityHours, loadHours, utilization, overload}` + `CrpScheduleView{orderNo,itemCode,stepNo,name,workCenterCode,quantity,hours,startDate,endDate,status,delayed}` + `excludedOrderCount/beyondHorizonHours`。计算**同步、确定性**，不落库、不做异步任务。

### 5.6 退料 / 报废

- 退料：`POST /erp/mfg/production/{id}/return`，行 `{lineNo, quantity, reason}`；按 `issuedCost/issuedQuantity` 计价（4 位 HALF_UP；全退时取剩余成本结清尾差）入库领料仓，生成 `IN` 库存流水（来源 `PRODUCTION_RETURN`），冲减 `issuedQuantity/issuedCost/totalMaterialCost`。
- 报废：`POST /erp/mfg/production/{id}/scrap`，同样扣减已领与成本，但**不回库存、不生成流水**；仅写调整记录。报废损失凭证留待 D5 成本方案。
- 两者累计不得超过已领量；每次写 `erp_mfg_production_material_adj`（类型 `RETURN/SCRAP`）。

### 5.7 端口与仓储

- 新增 `WorkCenterRepository`、`RoutingCatalog`；`ProductionOrderRepository` 扩展：保存/读取工序、调整记录，`listActiveForCrp(from,to)`。
- 适配器：`PdmRoutingCatalogImpl`；持久化沿用 MyBatis-Plus Entity/Mapper/RepositoryImpl 现有分层。
- ArchUnit：domain 不得依赖 Spring/MyBatis；application 不得依赖 infrastructure；PDM 客户端只在 infrastructure。

---

## 六、接口与权限

| 方法与路径 | 权限码 | 批次 |
|---|---|---|
| `GET/POST/PUT/DELETE /erp/mfg/work-centers/**` | 545 list / 546 add / 547 edit / 548 delete | 3a |
| `GET /erp/mfg/crp/load` | 549 `sys:erp:mfg:crp:view` | 3a |
| `POST /erp/mfg/production/{id}/routing/sync` | 550 `sys:erp:production:operation:sync` | 3a |
| `POST /erp/mfg/production/{id}/operations/{stepNo}/report` | 551 `sys:erp:production:operation:report` | 3b |
| `POST /erp/mfg/production/{id}/return` | 552 `sys:erp:production:return` | 3b |
| `POST /erp/mfg/production/{id}/scrap` | 553 `sys:erp:production:scrap` | 3b |

- 权限脚本 `db/migration-5.78-erp-crp.sql`（幂等，545–553；554–569 预留）；菜单 106「工作中心」`/erp/mfg/work-center`、107「产能负荷」`/erp/mfg/crp`（108 预留）。
- 角色：`tenant_admin / erp_manager / erp_mfg` 授全部；`erp_member` 仅 545/549；`erp_finance` 不授。
- 注意：3a 只写 545–550 与菜单 106–107；3b 只追加 551–553，避免未上线权限提前下发。

### 错误码（新增，均已核对未占用）

`40027 CRP_INVALID`、`40028 ROUTING_FETCH_FAILED`、`40029 OPERATION_INVALID`、`40443 WORK_CENTER_NOT_FOUND`、`40444 PRODUCTION_OPERATION_NOT_FOUND`、`40942 WORK_CENTER_CODE_CONFLICT`、`40943 OPERATION_REPORT_EXCEEDED`、`40944 MATERIAL_RETURN_EXCEEDED`、`40945 MATERIAL_SCRAP_EXCEEDED`。

---

## 七、前端（`frontend/platform-apps/whale-erp`）

- `api/mfg.ts`：新增工作中心、CRP、工序、报工、退料/报废类型与函数；沿用 `buildQuery`。
- 新增 `WorkCenterPage.tsx`：列表 + 新建/编辑弹窗（编码、名称、部门、日产能、效率、工作日、状态）。
- 新增 `CrpPage.tsx`：日期区间 + 工作中心筛选；Tab「产能负荷」表格（产能/负荷/利用率/超载标记）+「排程甘特」纯 CSS 日格甘特（不新增依赖，antd + dayjs 已具备）；顶部展示 `excludedOrderCount/beyondHorizonHours`。
- `ProductionPage.tsx`：详情抽屉扩为「用料 / 工序」两 Tab；工序 Tab 展示累计合格/报废与状态，提供「报工」弹窗（权限 551）；用料行展示倒冲标记，新增「退料」「报废」弹窗（权限 552/553）；建单用料行可勾选倒冲；无工艺路线时展示状态并提供「同步工艺」（权限 550）。
- `App.tsx`：菜单项、`PageKind`、`initialPage`（`/erp/mfg/work-center`、`/erp/mfg/crp` 需在 `/erp/mfg/production` 之前判断）、`PAGE_TITLE` 注册。
- 权限显隐沿用 `hasPermission('sys:erp:...')`。

---

## 八、测试与验收

| 层 | 用例 |
|---|---|
| PDM | 最新已发布路线快照、指定版本快照、非 RELEASED 404、无工序路线返回空 steps 或拒绝（按发布规则必有一道工序） |
| ERP 领域 | 工作中心校验；工序报工累加/超量/前道约束；末道合格量限制完工；退料/报废扣减与上限；倒冲比例与尾差 |
| ERP CRP | 工作日跳过周末、产能×效率、无限负荷超载标记、有限排程顺延与前道约束、计划完工延误标记、区间截断 |
| ERP 应用 | PDM 404/不可达创建订单的三种 routingStatus；sync 覆盖；倒冲库存不足回滚；退料库存与成本、凭证口径不变 |
| 前端 | `pnpm --filter whale-erp lint`、`build`；现有测试不回归 |

命令：

```
mvn -o -pl backend/whale-apps/whale-erp test
mvn -o -pl backend/whale-apps/whale-pdm test
cd frontend; pnpm --filter whale-erp lint; pnpm --filter whale-erp build
powershell scripts/check-doc-consistency.ps1
```

文档回填（实施完成后）：五服务计划 Step 3 行与进度、ERP 全景 §13.6 与变更记录、roadmap、devlog；`service-catalog.md` 若仅因既有未提交 5.94 内容缺记录，如实报告不混提。

交付：每批仅暂存本批文件，自动提交并推送 `origin/main`，输出四段式报告（需求项/完成项/待做项/已修正问题项）。

---

## 九、风险与约束

| 风险 | 处置 |
|---|---|
| PDM 新增契约属跨服务改动 | 只读、复用内网 token 与 internal-deny，不改表；先于 ERP 合入同一批 |
| 工序报工与 MES 边界重叠 | ERP 只做订单级工序进度；车间扫码/设备采集仍归 MES，Step 4 对接 |
| 完工约束收紧影响历史订单 | 仅“存在工序”的订单生效；历史无工序订单行为不变 |
| 倒冲/退料成本尾差 | 4 位 HALF_UP + 最后一批/最后一次结清剩余成本 |
| CRP 计算量 | 同步 + 区间上限 62 天 + 只取活动订单；后续可换异步 |
| 未提交红线 | 不触碰 5.89/5.91 与商城文件；不提交本方案文档 |

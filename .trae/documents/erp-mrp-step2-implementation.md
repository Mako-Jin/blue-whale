# ERP MRP（Step 2）实施方案

> 状态：待批准　｜　日期：2026-11-06　｜　服务：whale-erp（9001 / `/erp/**` / `bluewhale_erp`）
> 上游计划：[five-services-backlog-stepwise](../docs/plans/2026-11-06-five-services-backlog-stepwise.md) Step 2、[erp-panorama](../docs/plans/2026-11-03-wave5.78-erp-panorama.md) §13
> 依赖契约：PDM `GET /pdm/internal/bom/{bomNo}/versions/{versionNo}`（`PdmBomSnapshotVO`，行含 `path/depth/parentPartNo/childPartNo/qty/lossRate`，`lossRate` 为 0–100 百分比）

## 一、背景与范围

ERP M0–M6 已落地，M3 仅完成预测 / MPS / 生产订单（无 MRP）。本期补齐 MRP 一期闭环：拉取 PDM 已发布 BOM 快照 → 多级用量展开 → 结合库存可用量算净需求 → 生成计划订单并保留需求追溯 → MRP 运行记录可查询、可重跑 → 前端结果页。

**本期做**：BOM 快照拉取与本地运行留痕、多级展开、净需求、计划订单（区分采购/生产建议）、需求追溯、运行 Job（同步执行，状态 RUNNING/COMPLETED/FAILED）、重跑、列表/详情接口、权限与菜单、两个 React 页面。

**本期不做**：计划订单转采购订单/生产订单、CRP、自动读取 MPS、在途/安全库存占用、异步线程池、前端单元测试。

## 二、文档先行（编码前完成）

1. `docs/plans/2026-11-06-five-services-backlog-stepwise.md`：新增「Step 2 实施明细」小节（范围、表、接口、权限号段、算法口径、验收），进度行改为 Step 1/2 已完成。
2. `docs/plans/2026-11-03-wave5.78-erp-panorama.md`：§13 增补 MRP 小节（表、端点、算法、权限、测试证据），§9 变更记录加 v1.7，页首进度更新。
3. `docs/devlog/2026-11-06.md` 当前混有商城 5.94 未提交内容 → **不写入、不提交**，报告中说明。

## 三、数据库

### 3.1 ERP 架构迁移 `backend/whale-apps/whale-erp/src/main/resources/db/migration/V13__mfg_mrp.sql`

4 张表（`IF NOT EXISTS`，字段规范同 V5）：

| 表 | 关键列 | 说明 |
|---|---|---|
| `erp_mfg_mrp_run` | `run_no`、`status`、`demand_line_count`、`snapshot_line_count`、`planned_order_count`、`error_message`、`start_time`、`end_time` | UK(tenant_id, run_no)；IDX(status, create_time) |
| `erp_mfg_mrp_run_line` | `run_id`、`line_no`、`item_code`、`bom_no`、`bom_version`、`quantity`、`due_date` | 持久化输入，支撑重跑与复现；UK(tenant_id, run_id, line_no) |
| `erp_mfg_mrp_planned_order` | `run_id`、`order_no`、`order_type`(PURCHASE/PRODUCTION)、`item_id/code/name`、`uom`、`gross_quantity`、`available_quantity`、`net_quantity`、`item_level`、`bom_no`、`bom_version`、`need_date`、`status`(PLANNED) | UK(tenant_id, order_no)；IDX(run_id)、(item_id) |
| `erp_mfg_mrp_trace` | `run_id`、`planned_order_id`、`demand_line_no`、`bom_path`、`depth`、`unit_qty`、`loss_rate`、`gross_quantity` | 需求追溯；IDX(run_id, planned_order_id)、(run_id, demand_line_no) |

数量统一 `DECIMAL(18,4)`；逻辑外键，无物理 FK；只追加不改历史。

### 3.2 权限/菜单 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.78-erp-mrp.sql`

- 权限 **542–544**（441–541 已占用，545–548 预留）：
  - 542 `sys:erp:mrp:run`（POST runs、rerun）
  - 543 `sys:erp:mrp:run:list`（GET runs/page、runs/{id}）
  - 544 `sys:erp:mrp:order:list`（GET orders/page、orders/{id}）
- 菜单 **104** `MRP 运算`（`/erp/mfg/mrp`）、**105** `计划订单`（`/erp/mfg/mrp/planned-order`）（106 预留）。
- 角色：`tenant_admin/erp_manager/erp_mfg` 授 542–544；`erp_member/erp_finance` 只授 543/544；沿用 `erp_mfg`，不新增角色。
- 全部 `INSERT ... SELECT ... WHERE NOT EXISTS` 幂等。

## 四、后端（`whale-apps/whale-erp`，mfg 上下文）

### 4.1 领域层（`com.bluewhale.erp.mfg.domain`，纯 Java，不依赖 Spring/MyBatis/其他上下文）

- `model/BomSnapshot`、`model/BomLine`：快照值对象（含 checksum、path/depth/parentPartNo/childPartNo/qty/uom/lossRate）。
- `model/MrpRun`、`MrpRunLine`、`MrpPlannedOrder`、`MrpTrace`、`MrpQuery extends PageQuery`、`MrpStatus` 常量。
- `model/MrpExpansionCalculator`：纯函数，输入快照 + 需求数量，输出各子件的累计用量与 path 追溯。
  - 累计口径：`cum(path) = cum(parentPath) × qty × (1 + lossRate/100)`，根路径取 1；`lossRate` 空按 0；4 位小数 HALF_UP。
  - 判定：本次快照中任一行 `parentPartNo` 命中的零件 → `PRODUCTION`，否则 `PURCHASE`。
- `port/BomCatalog#fetch(bomNo, versionNo)`、`port/StockAvailability#availableQuantity(itemId)`。

### 4.2 基础设施

- `infrastructure/client/PdmBomCatalogImpl`：`DiscoveryClient` 取 `whale-pdm` + `InternalTokenSupport.restClient()` + `WebHeaders.TENANT_ID`；解析信封 `code/data`；服务不可达、非 0、`lines` 为空或 `lineCount != lines.size()` → `MRP_BOM_FETCH_FAILED`，不降级。
- `infrastructure/adapter/StockAvailabilityAdapter`：注入 scm `StockRepository`（mfg.infrastructure 允许依赖 scm domain）。
- **scm 增量**：`StockRepository` 增 `BigDecimal sumQuantityByItemId(Long itemId)`，`StockRepositoryImpl` 用 `LambdaQueryWrapper` + `COALESCE(SUM(quantity),0)`（或 selectList 求和，保持实现简单）。
- `infrastructure/persistence/{entity,mapper,repository}`：4 张表对应实体/Mapper，`MrpRepositoryImpl` 参照 `MpsRepositoryImpl` 写法（`toDomain`/`fill`/分页/批量插入）。

### 4.3 应用层

- `command/MrpRunCommand`：`remark` + `lines[{itemCode, bomNo, versionNo, quantity, dueDate}]`，Bean Validation（非空、qty>0、日期必填）。
- `view/MrpRunView`（含 demandLines、plannedOrders 概览）、`MrpPlannedOrderView`（含 traces）、`MrpRunLineView`、`MrpTraceView`。
- `service/MrpAppService` + `impl/MrpAppServiceImpl`：
  - `run(command)`：校验 → 生成 `runNo`（`MRP-yyyyMMdd-####`）→ 落 RUNNING 与输入行 → 调 `MrpRunExecutor` 计算 → 成功落订单/追溯并置 COMPLETED；异常置 FAILED 并返回 run（不回滚失败记录）。
  - `rerun(id)`：读取原运行输入行，新建运行并重算。
  - `pageRuns/detailRun/pageOrders/detailOrder`。
- `service/impl/MrpRunExecutor`（独立 Bean，`@Transactional`）：BOM 拉取（run 内按 `bomNo@versionNo` 缓存）→ 校验 `productCode == itemCode` → 校验全部子件在 ERP 存在且启用（缺失 `ITEM_INVALID` 快速失败）→ 展开 → 按 itemCode 聚合毛需求（同一零件只查一次可用量）→ `net = max(0, gross - available)` → 仅 net>0 生成计划订单并写每条贡献的 trace。
- `common/ErpErrorCode` 追加：`MRP_INVALID=40025`、`MRP_BOM_FETCH_FAILED=40026`、`MRP_RUN_NOT_FOUND=40441`、`MRP_PLANNED_ORDER_NOT_FOUND=40442`，同步 `MESSAGES`。
- `interfaces/controller/MrpController`（`/erp/mfg/mrp`）：
  - `POST /runs`（542，AuditLog CREATE，targetType `erp_mfg_mrp_run`）
  - `GET /runs/page`、`GET /runs/{id}`（543）
  - `POST /runs/{id}/rerun`（542，AuditLog UPDATE）
  - `GET /orders/page`、`GET /orders/{id}`（544）
  - 分页沿用 `PageParams.current/size` 兼容写法与 `PageResult`。
- ArchUnit 回归：新包在既有规则内，无需新增规则。

### 4.4 测试

`src/test/java/com/bluewhale/erp/mfg/`：
- `MrpExpansionCalculatorTest`（纯领域）：多级累计、损耗率、空损耗、path 追溯。
- `MrpAppServiceImplTest`（Mockito，参考 `MpsAppServiceImplTest`）：净需求不为负、同一零件跨需求行可用量只扣一次、PRODUCTION/PURCHASE 判定、PDM 失败 → FAILED 且无订单、物料缺失 → ITEM_INVALID、productCode 不符 → MRP_INVALID、重跑记录独立。
- 编译与 Checkstyle 随 `mvn test` 验证。

## 五、前端（`frontend/platform-apps/whale-erp`，React + Ant Design）

- `src/App.tsx`：
  - import `MrpRunPage`、`MrpOrderPage`；菜单项 `mrp-run`「MRP 运算」、`mrp-order`「计划订单」；
  - `PageKind` 增 `mrp-run | mrp-order`；`initialPage` 中 `/erp/mfg/mrp/planned-order` 必须先于 `/erp/mfg/mrp` 判断；
  - `pageOfKey`、`PAGE_TITLE`、条件渲染补两页。
- `src/api/mfg.ts`：新增 `MrpRunInput/MrpRunView/MrpRunLineView/MrpPlannedOrderView/MrpTraceView/MrpQueryParams` 与 `runMrp/pageMrpRuns/getMrpRun/rerunMrp/pageMrpPlannedOrders/getMrpPlannedOrder`；查询串复用 `buildQuery`，详情页沿用 `current/size`。
- `src/pages/MrpRunPage.tsx`：
  - 上半区：可编辑需求行表格（物料编码、BOM 号、版本、数量、需求日期）+「执行运算」按钮（`sys:erp:mrp:run`）；
  - 下半区：运行记录表（单号、状态标签、需求行数、计划订单数、起止时间、错误信息），「查看」抽屉展示输入行与计划订单概览，FAILED 可「重跑」。
- `src/pages/MrpOrderPage.tsx`：筛选（运行记录、类型、关键字）+ 计划订单表（单号、物料、类型、毛需求/可用/净需求、层级、BOM 版本、需求日期）+「追溯」抽屉（来源需求行、path、单位用量、损耗率、贡献数量）；权限 `sys:erp:mrp:order:list`。
- 权限判断复用 `src/utils/permission.ts` 的 `hasPermission`；样式与 `ProductionPage.tsx` 保持一致。

## 六、验证顺序

1. 后端：`$env:JAVA_HOME='D:\java'; mvn -o -pl backend/whale-apps/whale-erp test`（ArchUnit + 新单测全绿）。
2. 前端：cwd `frontend`，`pnpm --filter whale-erp lint`、`pnpm --filter whale-erp build`。
3. 文档：`powershell -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1` → `DOC-CONSISTENCY-PASS`。
4. 迁移脚本自查：空库可重放、重复执行幂等（仅静态复核 + 既有 Flyway 基线测试）。

## 七、提交与红线

- 只暂存本批文件：`whale-erp` 的新增/修改源码与 `V13__mfg_mrp.sql`、`migration-5.78-erp-mrp.sql`、两份计划文档、前端 `whale-erp` 文件。
- **绝不 `git add`**：`backend/whale-apps/pom.xml`、`whale-gateway/application.yml`、`deploy/monitoring/prometheus.yml`、`infrastructure/docker/mysql/init/01-init-databases.sql`、`whale-map/`、`migration-5.91-map-m0.sql`、`whale-mall/`、`migration-5.94-mall.sql`、`docs/plans/2026-11-06-wave5.94-mall-service-m0.md`、`.github/workflows/docker-deploy.yml`、`docs/naming-conventions.md`、`docs/service-catalog.md`、`docs/devlog/2026-11-06.md`、`.trae/documents/crm-frontend-8113-implementation.md`、`.trae/documents/erp-mrp-step2-implementation.md`。
- 验证通过后提交并推送 `origin/main`，输出四段式报告（需求项/完成项/待做项/已修正问题项）。

## 八、风险与取舍

| 风险 | 处置 |
|---|---|
| 同步执行长 BOM 可能超时 | 一期接受；运行记录已留痕，后续可换异步执行器 |
| `lossRate` 语义（0–100 百分比） | 已由 PDM 实体注释确认为 0–100，按 `(1+rate/100)` 计 |
| 可用量仅现货、不含在途/安全库存/已分配 | 文档明示口径，后续 Step 3/6 增强 |
| PDM 不可用导致整单失败 | fail-fast + 运行记录 FAILED + 错误信息，不产出错误计划 |
| 跨上下文依赖 scm | 仅 mfg.infrastructure 适配器调用 scm domain，ArchUnit 规则允许 |

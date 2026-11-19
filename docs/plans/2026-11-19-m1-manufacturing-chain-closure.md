# M1 制造链闭环执行计划（P0）

> 立项：2026-11-19 ｜ 依据：[bluewhale-blueprint-2026Q4.md](bluewhale-blueprint-2026Q4.md) §六 M1、[roadmap.md](roadmap.md) Phase 15
> 执行原则：文档先行 → 编码 → 单测/构建 → 精确暂存 → 提交推送。

## 一、验收口径（蓝图原文）

- [ ] 销售订单（鲸客 CRM）→ 生产订单（鲸造 MES）→ 排产报工 → 质检 → 入库（鲸营 ERP）→ 3D 看板（鲸城）
- [ ] 5 个环节全在系统内完成，全程只点页面，不碰 SQL
- [ ] 一张订单从下达到入库，状态机每步有留痕（谁、何时、改了什么）
- [ ] 3D 看板数量 == 数据库数量（对账脚本可验证）
- [ ] 压测基线：权限判定 P95 / 订单列表分页 P95 / 并发报工 3 组数据写进文档
- [ ] 一条链 ≥3 个自动化冒烟用例进 CI

## 二、现状盘点（2026-11-19 代码核对）

| 环节 | 现状 | 证据 | 缺口 |
|---|---|---|---|
| CRM 销售订单 | 已实现：建单/提交审批/outbox 下发 ERP，`crmOrderNo` 幂等 | `whale-crm/.../commerce/domain/model/SalesOrder.java`、`.../infrastructure/client/ErpOrderInternalClient.java` | 无 |
| ERP 销售订单 | 已实现：`/erp/scm/sales-order` 内网接入 + 台账 | `whale-erp/.../scm/interfaces/controller/SalesOrderController.java` | 无 |
| ERP 生产订单 | 已实现：建单/下达/领料/完工入库/结案/取消，下达后事件下发 MES | `whale-erp/.../mfg/interfaces/controller/ProductionOrderController.java`、`.../MesIntegrationAppServiceImpl.java` | **建单无来源销售订单字段/转单入口**，"订单从哪里来"不可追溯 |
| ERP→MES 下发 | 已实现：`MesWorkOrderGateway` → `POST /mes/internal/work-order`，`erpOrderNo` 幂等 | `whale-erp/.../mfg/infrastructure/client/MesWorkOrderGatewayImpl.java`、`whale-mes/.../dispatch/controller/MesInternalWorkOrderController.java` | 无 |
| MES 派工/报工 | 已实现：拆分派工、开工/暂停/恢复/完工/报废、扫码报工、在制品 | `MesDispatchController`、`MesExecutionController` | 无 |
| MES 质检 | 已实现：首件/IPQC 检验任务、结果录入、判定、首件门禁 | `MesInspectionController` | 无 |
| MES 完工→ERP | 已实现：outbox → `POST /erp/internal/production-order/{no}/completion` → `complete()` 入库并生成凭证 | `whale-mes/.../common/outbox/MesOutboxDispatcher.java`、`MesIntegrationAppServiceImpl#handleCompletion` | 无 |
| 3D 看板 | **静态布景**：城市坐标/建筑写死在 `city-layout.json`，3D 页面只渲测试立方体 | `whale-frontend/platform-apps/whale-world/src/world/city-layout.json`、`whale-scene/src/pages/Scene3DPage.tsx` | **无真实数量数据源** |
| 对账 | ERP 侧有 MES 对账页/明细（已完工数量 vs MES 累计） | `whale-erp/.../mfg/interfaces/controller/MesReconcileController.java` | 缺「3D 看板数量 vs 库存/产量」对账脚本 |
| 链上冒烟 | 无跨 CRM→ERP→MES 的自动化用例 | `scripts/smoke/` 仅有单服务冒烟 | 缺 3 条链上冒烟 + CI 接入 |
| 压测基线 | 无 | - | 三组数据缺失 |

**结论**：后端主链路已具备，P0 的真实缺口集中在「转单可追溯」「3D 看板真实数据 + 对账」「链上冒烟与压测」。

## 三、里程碑

### M-A 转单可追溯（后端 + 前端）
1. `mfg_production_order` 增加来源单据字段（`source_type` / `source_order_no` / `source_order_id`），迁移脚本幂等。
2. 新增 `POST /erp/mfg/production/from-sales-order/{salesOrderId}`：按销售订单行生成生产订单（一行一单或合并，取已确认数量），校验销售订单状态与重复转单。
3. ERP 生产订单列表/详情展示来源单号；销售订单页增加「转生产」入口（权限 `sys:erp:production:add`）。
4. 单测：转单幂等、状态校验、数量校验、权限。

### M-B 链上冒烟（3 条进 CI）
1. `crm-order-to-erp`：CRM 建单→审批通过→ERP 销售订单落地（幂等重推）。
2. `erp-to-mes-to-erp`：ERP 生产订单下达→MES 收单→报工完工→ERP 完工入库数量一致。
3. `chain-status-audit`：全链状态与 `audit_log`/回调流水留痕可查。
4. 接入现有 smoke 目录与 CI workflow（无中间件时跳过并显式标注）。

### M-C 3D 看板真实数据 + 对账
1. 后端新增聚合接口（scene 或 ERP）：按仓库/物料/生产订单返回实时数量，内网 token + 权限双控。
2. 3D 场景从接口取数并渲染数量标签，接口不可用时显式降级提示（不得静默显示假数据）。
3. `scripts/verify/chain-reconcile.ps1`：3D 接口数量 vs ERP 库存/完工数量，差异非零即失败。

### M-D 压测基线
1. 权限判定 P95、订单列表分页 P95、并发报工三组脚本与结果写进 `docs/ops/`。

## 四、约束与风险

- 网关不剥前缀，新增接口路径必须以服务名开头（`/erp/...`、`/mes/...`）。
- 内网接口必须挂 `X-Internal-Token`，网关 `internal-deny` 不放行。
- 迁移脚本幂等，按版本号数值升序。
- 3D 真实数据依赖 ERP/MES 内网可达；不可达时接口明确返回降级标识，而不是返回 0。
- 压测需本机中间件与多服务起服，环境不满足时先落脚本与口径，数据后补并标注。

## 五、执行记录

### 2026-11-19 立项

- 完成代码级现状盘点；按 M-A → M-B → M-C → M-D 顺序推进。

### 2026-11-19 M-A 完成（销售订单 → 生产订单可追溯）

- **需求项**：生产订单能记录来源销售订单与行号；同一销售订单行只能转一张生产订单；转单入口在页面完成，不碰 SQL。
- **完成项**：
  - 迁移 `V19__mfg_production_source.sql`：`erp_mfg_production_order` 新增 `source_type/source_order_id/source_order_no/source_line_no` 与唯一键。
  - 领域/实体/视图/仓储全链路补齐来源字段，新增 `findBySource`。
  - 新增接口 `POST /erp/mfg/production/from-sales-order/{salesOrderId}/lines/{lineNo}`：校验销售订单状态（仅已审核/部分发货）、行存在、重复转单；产出物料与数量取自订单行；写审计。
  - 前端销售订单页新增「转生产」弹窗（订单行、领/完工仓、计划日期、用料），生产订单列表新增「来源单号」列。
- **验证**：ERP 模块 `mvn -o test` 524 用例全通过 + BUILD SUCCESS；前端 `pnpm lint` 0 告警、`vitest` 15 用例通过。
- **待做项**：M-B 链上冒烟、M-C 3D 看板真实数据与对账、M-D 压测基线。
- **已修正问题项**：无。

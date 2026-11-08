# Step 4：ERP ↔ MES 打通 实施方案

> 日期：2026-11-07　状态：**待确认**　对应计划：[五服务分步计划](../../docs/plans/2026-11-06-five-services-backlog-stepwise.md) Step 4
> 本文件为过程稿，按约定不提交。

---

## 一、背景与目标

Step 3 已交付 ERP 生产订单、工序快照、报工、倒冲、退料/报废。现在补齐 ERP 与 MES 的双向闭环：

1. **ERP → MES 下发**：生产订单下达（RELEASED）后，把订单头信息推送到 MES；失败可重试，状态可查。
2. **MES → ERP 回传**：MES 报工后回传进度快照；MES 工单完工后回传累计完工数量，ERP 幂等入库。
3. **双向幂等与对账**：重复下发、重复回传均不产生重复数据；提供「MES 对账」页，展示下发状态、MES 状态、数量差异与失败原因，支持重推、拉取刷新、人工确认入库。

**范围外**（不在本批）：MES 有限产能排程/采集推送/数据范围等增强；ERP 计划订单转生产订单；定时批量对账 Job；新的库存/凭证口径（完工入库全部复用 Step 3 的 `complete()`）。

---

## 二、现状对账（已核对代码）

| 方向 | 现状 | 结论 |
|---|---|---|
| MES 接收工单 | `POST /mes/internal/work-order` 已存在，按 `erpOrderNo` 幂等（[MesInternalWorkOrderController](../../backend/whale-apps/whale-mes/src/main/java/com/bluewhale/mes/dispatch/controller/MesInternalWorkOrderController.java)、[MesDispatchServiceImpl](../../backend/whale-apps/whale-mes/src/main/java/com/bluewhale/mes/dispatch/service/impl/MesDispatchServiceImpl.java#L142-L162)） | MES 侧可用，ERP 从未调用 |
| ERP 下发 | `release()` 只改状态，无 MES 调用、无同步字段 | **需新增** |
| MES 完工回传 | `MesOutboxDispatcher` 定时投递 `WorkOrderCompleted` 到 `/erp/internal/production-order/{erpOrderNo}/completion`，默认关闭、需配置 ERP 地址 | 投递器已有，**ERP 端点缺失** |
| MES 报工回传 | 无事件；`MesTaskProgressServiceImpl.refreshWorkOrder()` 每次状态回流后更新工单 | **需新增进度事件** |
| ERP 完工入库 | `ProductionOrderAppServiceImpl.complete()` 已含倒冲领料、库存入库、自动凭证 | 直接复用 |
| 内网调用范式 | `InternalTokenSupport.restClient()` + `DiscoveryClient` + `WebHeaders.TENANT_ID`（[PdmPrcMesClientImpl](../../backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/process/client/PdmPrcMesClientImpl.java)） | 按此实现 MES client |
| 号段 | ERP 权限已用至 553，菜单已用至 107；Flyway 最新 V15 | 554–557 / 菜单 108 / V16 可用 |

---

## 三、接口契约

### 3.1 ERP → MES 下发

调用 MES：`POST /mes/internal/work-order`（复用现有 DTO）

请求体（字段与 `MesWorkOrderReceiveRequest` 对齐）：

```json
{
  "erpOrderNo": "MO-20261107-0001",
  "itemCode": "P-1001",
  "itemName": "产品A",
  "plannedQuantity": 100,
  "uom": "PCS",
  "routingVersion": "V1",
  "priority": "NORMAL",
  "plannedStart": "2026-11-07T00:00:00",
  "plannedEnd": "2026-11-10T23:59:59",
  "code": "MES-MO-20261107-0001",
  "name": "产品A",
  "remark": "ERP 下发"
}
```

- 幂等键：MES 侧 `erpOrderNo`（已实现：重复下发覆盖未派工字段，已派工且数量不一致则拒绝）。
- ERP 触发点：`release()` 成功后 best-effort 调用；失败写 `mes_sync_status=FAILED` + `mes_last_error`，**不阻断下达**。
- 手动重试：`POST /erp/mfg/mes/reconcile/{orderId}/push`。

### 3.2 MES → ERP 进度回传

调用 ERP：`POST /erp/internal/production-order/{erpOrderNo}/progress`

```json
{
  "eventId": "mes:work-order-progress:12:3",
  "payload": "{\"erpOrderNo\":\"MO-...\",\"workOrderId\":12,\"workOrderCode\":\"MES-...\",\"status\":\"IN_PROGRESS\",\"plannedQuantity\":100,\"reportedQuantity\":30,\"qualifiedQuantity\":28,\"completedQuantity\":20,\"tasks\":[{\"taskNo\":\"TASK-...\",\"seqNo\":1,\"operationCode\":\"OP10\",\"plannedQuantity\":100,\"reportedQuantity\":30,\"qualifiedQuantity\":28,\"completedQuantity\":20,\"status\":\"IN_PROGRESS\"}]}"
}
```

- 幂等键：`eventId`（MES 发件箱 `source_event_id`，ERP 侧唯一）。
- ERP 只记录快照（`erp_mfg_mes_progress`）与回调流水，**不回写 ERP 工序报工**，避免与 ERP 手工报工双计。

### 3.3 MES → ERP 完工回传

调用 ERP：`POST /erp/internal/production-order/{erpOrderNo}/completion`

```json
{
  "eventId": "mes:work-order-completed:12",
  "payload": "{\"erpOrderNo\":\"MO-...\",\"workOrderId\":12,\"workOrderCode\":\"MES-...\",\"itemCode\":\"P-1001\",\"completedQuantity\":100,\"uom\":\"PCS\"}"
}
```

ERP 处理规则：

1. 按 `orderNo` 找订单；找不到 → 记录回调 `REJECTED`，返回 code 0（避免 MES 无限重试）。
2. 重复 `eventId` → 直接返回成功（`DUPLICATE`）。
3. `delta = 回传累计完工量 − ERP 已完工数量`；`delta <= 0` → 记录 `DUPLICATE`，返回成功。
4. `delta > 0` 且订单为 `RELEASED/IN_PROGRESS` → 复用 `complete(delta, 今天, 系统操作人)`（含倒冲、入库、凭证），成功记 `APPLIED`。
5. 领域校验失败（未领完料、末道工序合格量不足、超计划等）→ 记录 `REJECTED` + 错误摘要，返回 code 0；对账页可「人工确认入库」（指定数量重新调用）。

### 3.4 ERP → MES 拉取（对账补数）

调用 MES：`GET /mes/internal/work-order/{erpOrderNo}`（**新增 MES 端点**）

返回：工单状态、计划/报工/合格/完工数量、任务列表（`seqNo/operationCode/planned/reported/qualified/completed/status`）；不存在返回 404。

---

## 四、数据模型（ERP `V16__mfg_mes.sql`）

### 4.1 `erp_mfg_production_order` 追加列

| 列 | 类型 | 说明 |
|---|---|---|
| `mes_sync_status` | `VARCHAR(16) NOT NULL DEFAULT 'NOT_SYNCED'` | `NOT_SYNCED/PUSHED/FAILED` |
| `mes_work_order_code` | `VARCHAR(64) NULL` | MES 工单编码（回传或拉取后回填） |
| `mes_synced_at` | `DATETIME NULL` | 最近成功下发时间 |
| `mes_last_error` | `VARCHAR(255) NULL` | 最近一次下发/拉取失败摘要 |

### 4.2 `erp_mfg_mes_callback`（回调幂等流水）

| 列 | 说明 |
|---|---|
| `id` / `tenant_id` / `order_id` / `erp_order_no` | 主键、租户、订单 |
| `callback_type` | `COMPLETION` / `PROGRESS` |
| `source_event_id` | MES 事件幂等键 |
| `status` | `APPLIED` / `REJECTED` / `DUPLICATE` |
| `payload` | 原始 payload（TEXT） |
| `error` | 失败摘要（255） |
| `received_at` | 接收时间 |

约束：`UK(tenant_id, source_event_id)`、`KEY(tenant_id, erp_order_no)`。

### 4.3 `erp_mfg_mes_progress`（进度快照）

| 列 | 说明 |
|---|---|
| `id` / `tenant_id` / `order_id` / `erp_order_no` | 主键与定位 |
| `mes_status` | MES 工单状态 |
| `planned_quantity` / `reported_quantity` / `qualified_quantity` / `completed_quantity` | 数量快照 |
| `task_json` | 任务明细 JSON（TEXT） |
| `event_id` / `received_at` | 最近事件与时间 |

约束：`UK(tenant_id, erp_order_no)`，按事件覆盖更新。

---

## 五、后端改造清单

### 5.1 ERP（whale-erp）

| 层 | 文件/新增 | 内容 |
|---|---|---|
| domain | `mfg/domain/model/MesStatus.java`（新增） | 同步状态、回调类型/状态常量 |
| domain | `ProductionOrder` 追加字段 | `mesSyncStatus/mesWorkOrderCode/mesSyncedAt/mesLastError` |
| domain | `mfg/domain/port/MesWorkOrderGateway.java`（新增） | `push(order)`、`pull(erpOrderNo)` |
| domain | `mfg/domain/model/MesProgressSnapshot.java`（新增） | 拉取/回传快照 |
| domain | `mfg/domain/repository/MesSyncRepository.java`（新增） | 回调去重、进度 upsert、对账分页查询 |
| infra | `mfg/infrastructure/client/MesWorkOrderGatewayImpl.java`（新增） | `InternalTokenSupport` + `DiscoveryClient("whale-mes")` + 租户头；响应 `{code,data}` 校验 |
| infra | `mfg/infrastructure/persistence/entity/{MesCallbackEntity,MesProgressEntity}.java` + mapper（新增） | 两张新表 |
| infra | `ProductionOrderEntity` / `ProductionOrderRepositoryImpl` | 映射追加列，`update()` 写入同步字段 |
| app | `mfg/application/service/MesIntegrationAppService.java` + impl（新增） | 下发、拉取、完工回执、进度回执、对账分页/详情、人工确认入库 |
| app | `ProductionOrderAppServiceImpl.release()` | 下达成功后 best-effort 下发 MES |
| app | `ProductionOrderAppService` 增补 | `completeFromMes(orderId, qty, operatorId, date)`（内部复用，不新增对外权限端点） |
| interfaces | `mfg/interfaces/controller/MesInternalCallbackController.java`（新增） | `/erp/internal/production-order/{erpOrderNo}/completion|progress`，无权限注解，走内网令牌 |
| interfaces | `mfg/interfaces/controller/MesReconcileController.java`（新增） | 对账分页/详情/重推/拉取/人工确认入库 |
| view | `MesReconcileView` / `MesCallbackView`（新增） | 对账视图 |
| common | `ErpErrorCode` 追加 | `MES_GATEWAY_FAILED=40027`、`MES_CALLBACK_INVALID=40028`、`MES_CALLBACK_STATE_CONFLICT=40943` |

### 5.2 MES（whale-mes）

| 文件 | 改动 |
|---|---|
| `MesTaskProgressServiceImpl` | 进度快照事件：`refreshWorkOrder` 末尾按工单 `version` 写 `WorkOrderProgress` 发件箱（含任务明细、计划/报工/合格/完工累计） |
| `MesOutboxDispatcher` | 支持 `WorkOrderProgress` → `/erp/internal/production-order/{erpOrderNo}/progress`；两种事件请求体统一为 `{eventId,payload}`；**补发 `WebHeaders.TENANT_ID`**（从事件租户字段读取，若实体无 tenantId 需先核实发件箱表结构） |
| `dispatch/controller/MesInternalWorkOrderController` + `MesDispatchService` | 新增 `GET /mes/internal/work-order/{erpOrderNo}`，返回工单 + 任务累计 |
| `MesWorkOrderVO` / 新 DTO | 增加任务列表与累计数量（仅内网查询用） |

> 不改 MES 现有接收/派工/报工语义；不新增 MES 表。

---

## 六、权限、菜单与错误码

| 编号 | 类型 | 内容 |
|---|---|---|
| 554 | 权限 | `sys:erp:mes:reconcile:list` — `GET /erp/mfg/mes/reconcile/page`、`/{orderId}` |
| 555 | 权限 | `sys:erp:mes:push` — `POST /erp/mfg/mes/reconcile/{orderId}/push` |
| 556 | 权限 | `sys:erp:mes:pull` — `POST /erp/mfg/mes/reconcile/{orderId}/pull` |
| 557 | 权限 | `sys:erp:mes:complete` — `POST /erp/mfg/mes/reconcile/{orderId}/completion-apply` |
| 108 | 菜单 | 「MES 对账」`/erp/mfg/mes-reconcile`，component `erp/mes-reconcile`，permission `sys:erp:mes:reconcile:list` |

- 脚本：`migration-5.78-erp-mes.sql`，全部 `NOT EXISTS` 幂等；沿用 `tenant_admin / erp_manager / erp_mfg`，`erp_member` 仅授 554。
- 错误码接续：40027/40028、40943（40026 与 40942 已被 MRP/工作中心占用）。

---

## 七、前端（whale-erp）

新增 `MesReconcilePage.tsx`：

- 筛选：日期区间（计划开工）、同步状态、MES 状态、订单号。
- 表格列：ERP 单号/物料/计划量/ERP 已完工、MES 状态/MES 报工/合格/完工、同步状态/失败原因、差异标记。
- 行操作：推送（554/555）、拉取（556）、人工确认入库（557，弹窗填数量，默认差异量）。
- 详情抽屉：回调流水（时间/类型/状态/错误）+ MES 任务明细。
- `api/mfg.ts` 增加类型与 5 个函数；`App.tsx` 注册 `mes-reconcile` 菜单、`PageKind`、`initialPage`、`PAGE_TITLE`。

---

## 八、测试与验收

| 检查 | 内容 |
|---|---|
| ERP 单测 | 下发成功/失败落状态；完工回执重复 eventId 幂等；delta 入库只应用一次；状态不允许 → REJECTED；进度快照覆盖；对账差异标记；人工确认入库；既有 502 例不回归 |
| MES 单测 | 进度事件在报工后入发件箱、按 version 幂等；投递体含 eventId/租户头；内网查询返回任务累计 |
| 编译 | `mvn -o -pl backend/whale-apps/whale-erp test`、`mvn -o -pl backend/whale-apps/whale-mes test` |
| 前端 | `cd frontend && pnpm --filter whale-erp lint && pnpm --filter whale-erp build` |
| 文档 | `powershell scripts/check-doc-consistency.ps1` → PASS（service-catalog 补记 V16） |
| 手工联调 | 启动 ERP(9001)+MES(9002)+auth；`whale.mes.outbox.enabled=true`、`whale.mes.integration.erp-base-url=http://localhost:9001`；下达 → MES 出现工单 → MES 派工/报工/完工 → ERP 对账页显示 APPLIED 与数量一致 |

---

## 九、风险与配置

1. **外部调用在事务内**：下发在 `release()` 事务内 best-effort 执行，失败只落状态不回滚；不做分布式事务。
2. **MES 发件箱默认关闭**：联调/生产需显式开启 `whale.mes.outbox.enabled=true` 并配置 ERP 地址；未开启时对账页可手动「拉取」持平。
3. **自动完工可能被拒**：MES 完工回传时 ERP 可能尚未领料或末道工序合格量不足，此时记 `REJECTED`，不阻断 MES 生产；由对账页人工确认或先在 ERP 领料/报工后重推。
4. **多租户**：MES 投递器当前未带租户头，需补 `WebHeaders.TENANT_ID`；否则 ERP 按默认租户处理。
5. **重复回传**：以 `(tenant_id, source_event_id)` 唯一约束兜底；同日多次部分完工以累计量差计算。

---

## 十、交付批次

| 批次 | 内容 | 验证 |
|---|---|---|
| 4a | ERP V16 + 领域字段 + MES client + 下发/重推 + 内部完工/进度回执 + 错误码 | ERP 单测 |
| 4b | MES 进度事件 + 投递器扩展 + 内网查询 | MES 单测 |
| 4c | 对账查询/详情/人工确认 + 权限脚本 + 前端页面 + App 注册 | ERP/MES 测试、前端 lint/build |
| 4d | 文档回填（五服务计划、ERP 全景、service-catalog、devlog）、一致性脚本、精确提交推送、四段式报告 | 全部检查 |

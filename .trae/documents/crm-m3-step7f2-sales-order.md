# CRM M3 / Step 7f-2：商务订单实施计划

## Context（为什么做）

五服务分步计划 Step 7 的 CRM M3 已完成 7f-1（合同台账投影 + 回款计划，V7 / 权限 1860–1865 / 菜单 427–428，已提交 `abac0d15`）。按计划 §M3 与设计稿，下一子项是 **7f-2 商务订单**：CRM 从合同/报价生成商务订单，审批走 `whale-workflow`，审批通过后通过 outbox 幂等下发 ERP，回写 `erpOrderNo`，失败可重试，并定时对账。ERP 仍是发货/库存/应收/信用/记账的唯一所有者。

已确认边界：D3 定 CRM 建商务订单、幂等键 `crmOrderNo`、ERP 保留履约订单；D4 回款/发票归后续 7f-3。**D2（客户主数据映射）未确认，因此不写客户同步代码**：CRM 订单携带 `customer_code` 快照，ERP 侧按既有 `CustomerRepository.findByCode` 解析，解析失败返回明确错误，由人工维护映射。

## 范围

- 后端：ERP 内网销售订单契约（幂等建单/按 CRM 单号查询/取消）+ CRM 商务订单领域、审批、outbox 下发、回写、重试、对账。
- 数据：ERP `V17__scm_crm_order_source.sql`（仅 ALTER 加列与唯一键）；CRM `V8__commerce_order.sql`（3 张新表）。
- 权限/菜单：CRM 权限 1866–1873、菜单 429；Auth 幂等脚本 `migration-5.83-crm-m3-order.sql`。
- 前端：`whale-crm` 新增「商务订单」页 + API。
- 单测/Checkstyle/前端 lint+build/文档回填/doc consistency。
- **不做**：客户主数据同步、发票与应收对账（7f-3）、ERP 发货状态实时回调（本批用定时查询对账）。

## 一、ERP 侧（whale-erp）

1. 迁移 `src/main/resources/db/migration/V17__scm_crm_order_source.sql`
   - `ALTER TABLE erp_scm_sales_order ADD COLUMN source_type VARCHAR(32) NULL`、`source_id VARCHAR(64) NULL`、`crm_order_no VARCHAR(64) NULL`，并加 `UNIQUE KEY uk_so_crm_order_no (tenant_id, crm_order_no)`（MySQL 唯一索引允许多个 NULL）。
   - 迁移测试允许 ALTER，禁止 DROP/TRUNCATE/DELETE；不改历史 V3。
2. 实体/领域/仓储补字段 `sourceType/sourceId/crmOrderNo`
   - `scm/infrastructure/persistence/entity/SalesOrderEntity.java`
   - `scm/domain/model/SalesOrder.java`
   - `scm/infrastructure/persistence/repository/SalesOrderRepositoryImpl.java`：`fill()`、`toDomain()` 补字段；新增 `findByCrmOrderNo`、`findByDocNo`（含分录）。
   - `scm/domain/repository/SalesOrderRepository.java` 增加同名接口方法。
3. 命令与服务
   - `SalesOrderCreateCommand` 增加可选 `docNo`、`sourceType`、`sourceId`、`crmOrderNo`；`create()` 在 `docNo` 非空时使用给定单号，否则仍走 `nextDocNo()`（不影响既有调用）。
   - `SalesAppService` 增加 `createFromCrm(...)` 与 `cancelByDocNo(...)`；`SalesAppServiceImpl`：
     - 先 `findByCrmOrderNo`，存在直接返回原单（幂等）。
     - 客户按 `CustomerRepository.findByCode(crmCustomerCode)` 解析，缺失抛 `CUSTOMER_NOT_FOUND`；仓库未传时用新增的 `WarehouseRepository.findFirstEnabled()`。
     - 单号固定 `CRM-SO-{crmOrderNo}`，`sourceType=CRM`、`sourceId=crmOrderNo`、`crmOrderNo` 同写；插入捕获 `DuplicateKeyException` 后重查返回原单（并发幂等）。
     - 取消：已发货/已关闭/已取消按既有 `SalesOrder.cancel()` 规则，已取消重复请求直接返回成功（幂等）。
4. 内网控制器 `controller/ErpInternalScmController.java`（`/erp/internal/scm`，无权限注解，由 `InternalTokenFilter` 保护）
   - `POST /sales-order`：`crmOrderNo`、`crmCustomerCode`、`docDate`、`deliveryDate`、`warehouseId`（可选）、`currency`、`remark`、`lines[{itemCode, itemName, uom, quantity, unitPrice}]` → 返回 `{erpOrderId, erpOrderNo, status}`。
   - `GET /sales-order/by-crm-order-no?crmOrderNo=` → 返回履约摘要（`erpOrderNo/status/totalAmount/shippedAmount`）。
   - `POST /sales-order/{docNo}/cancel` → 幂等取消。
5. `SalesOrderView` 补 `sourceType/sourceId/crmOrderNo`，便于内网返回与对账。

## 二、CRM 数据模型（V8__commerce_order.sql，仅新增表）

1. `crm_sales_order`
   - `order_no`（uk tenant+order_no）、`source_type`（CONTRACT/QUOTE/MANUAL）、`source_id`、`source_order_no`（可空，uk tenant+source_order_no）、`customer_id`、`customer_code`、`title`、`currency`、`total_amount`、`status`（DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/PUSHING/PUSHED/PUSH_FAILED/CANCELLED）、`approval_instance_id`、`submitted_at`、`approved_at`、`erp_order_no`、`erp_order_id`、`erp_status`、`pushed_at`、`push_attempts`、`last_push_error`、`owner_id`、`dept_id`、`remark`、`lock_version`、公共字段。
   - 防重复转单：`UNIQUE KEY uk_crm_order_source (tenant_id, source_type, source_id)`（手工单 source_id 为 NULL 不冲突）。
2. `crm_sales_order_line`：`order_id`、`line_no`、`item_code`（**可空**，转单后在前端补录）、`item_name`、`specification`、`uom`、`quantity`、`unit_price`、`amount`、`remark`；`uk(tenant_id, order_id, line_no)`。
3. `crm_order_push_log`：`order_id`、`attempt_no`、`request_ref`（crmOrderNo）、`response_ref`（erpOrderNo）、`status`（SUCCESS/FAILED）、`error_msg`、`cost_ms`、`create_time`。
4. 下发事件复用既有 `crm_common_outbox`（V2 已建）：`aggregate_type='crm_sales_order'`、`event_type='SALES_ORDER_PUSH'`、`aggregate_id=订单ID`、`payload={"orderId":...}`、`status/retry_count/next_retry_at/last_error`。

## 三、CRM 代码（commerce 包）

- **domain/model**：`SalesOrder`（状态机与校验：提交前至少一行且每行 `itemCode` 非空、金额>0；已审批不可改；已发货不可取消由 ERP 返回驱动）、`SalesOrderLine`、`SalesOrderStatus`、`SalesOrderQuery`。
- **domain/repository**：`SalesOrderRepository`（page/count/findById/findLines/insert/updatePushResult/findBySource/maxSequence）；`OrderOutboxRepository`（`enqueue`、`claimPending`、`markSent`、`markFailed`、`resetForRetry`）。
- **domain/port**：`OrderApprovalPort`（发起审批，仿 `QuoteApprovalPort`）；`ErpOrderPort`（create/findByCrmOrderNo/cancel）。
- **infrastructure**：
  - entity/mapper/repository：`SalesOrderEntity`、`SalesOrderLineEntity`、`OrderPushLogEntity`、`OutboxEntity` + mapper + repository 实现。
  - client：`WorkflowOrderApprovalClient`（复制 `WorkflowQuoteApprovalClient` 模式，`/workflow/instance`）；`ErpOrderInternalClient`（`InternalTokenSupport.builder()` + 租户头，`/erp/internal/scm/sales-order`）。
  - config：`CrmErpProperties`（`enabled` 默认 false、`base-url`、超时、`push-cron`、`reconcile-cron`、`batch-size`、`max-attempts`），并在 `WhaleCrmApplication` 的 `@EnableConfigurationProperties` 注册；`application.yml` 增 `whale.crm.erp.*` 配置块。
- **application**：
  - `SalesOrderService`/`Impl`：`page/detail/create/update/submit/cancel/retry/reconcile/handleApprovalCallback/pushPending`。
  - 审批回调：按 `approvalInstanceId` 找待审订单，通过→`APPROVED` 并在**同一事务**写 outbox（`next_retry_at=now`）；驳回→`REJECTED`；重复回调返回 false。
  - 分发：定时任务先乐观领取 outbox（`PENDING`/到期 `FAILED`）并置订单 `PUSHING`，HTTP 调用在事务外，回写独立事务（成功→`PUSHED`+`erpOrderNo/erpStatus`+push log；失败→`PUSH_FAILED`+`last_push_error`+push log+指数退避 `next_retry_at`）。
  - 对账：扫描 `PUSHING/PUSH_FAILED/PUSHED` 订单，按 `crmOrderNo` 查 ERP；查得则采纳（防超时重复建单），刷新 `erp_status`；`PUSH_FAILED` 未查得则重置 outbox 待重试。
  - 从报价生成：复制 `crm_quote_line`（`item_code` 留空，由前端补录），订单创建后报价置 `CONVERTED`（若 `QuoteStatus` 无该值则新增常量并加仓储更新方法，不改既有状态流转）。
- **interfaces**：
  - `CrmSalesOrderController`（`/crm/order`）：`GET /page`、`GET /{id}`、`POST`（生成/创建）、`PUT /{id}`（DRAFT 可改）、`POST /{id}/submit`、`POST /{id}/cancel`、`POST /{id}/retry`、`POST /reconcile`；写接口挂 `@AuditLog(operation/type/targetType)`。
  - `SalesOrderInternalController`（`/crm/internal/order/approval-callback`）：workflow 回调，幂等。
  - `CrmOrderPushJob`（`@Scheduled`，仅在 `whale.crm.erp.enabled=true` 执行；扫描 outbox 时按事件的 `tenant_id` 设置/清理 `TenantContext`）。

## 四、权限与菜单（Auth 脚本）

`db/migration-5.83-crm-m3-order.sql`，仿 `migration-5.83-crm-m3-commerce.sql` 的 `INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS` 幂等写法：

| 权限码 | 名称 |
|---|---|
| 1866 | 商务订单查询 |
| 1867 | 商务订单详情 |
| 1868 | 商务订单新建 |
| 1869 | 商务订单编辑 |
| 1870 | 商务订单提交审批 |
| 1871 | 商务订单下发重试 |
| 1872 | 商务订单取消 |
| 1873 | 商务订单对账 |

菜单 429「商务订单」（路由 `/order`），并授予既有 CRM 角色（25128–25132）。

## 五、前端（frontend/platform-apps/whale-crm）

- `src/api/commerce.ts`：新增 `SalesOrderView/SalesOrderLineView/SalesOrderQuery` 与 `pageOrders/detailOrder/createOrder/updateOrder/submitOrder/cancelOrder/retryOrder/reconcileOrders`，沿用 `PageResult<T>` 与 `GET /page`、`GET /{id}`、`POST/PUT` 写法。
- `src/pages/order/index.tsx`：列表（关键字/状态/客户筛选、分页、`PUSHED/PUSH_FAILED` 状态标识、`erpOrderNo` 与失败原因列）；新建/编辑抽屉（来源合同/报价、订单行编辑，**补录 itemCode**）；操作：提交审批、取消、重试、对账。
- `App.tsx`：注册菜单「商务订单」与 `/order` 路由（参照合同/回款注册位置）。
- 遵守 `eslint src --max-warnings 0`（无未使用变量、无 `any`）。

## 六、验证

1. 后端编译与测试（沿用 7f-1 构建命令）：
   - `mvn -q -pl backend/whale-apps/whale-erp -am test`（含迁移测试、新增幂等单测）
   - `mvn -q -pl backend/whale-apps/whale-crm -am test`（订单状态机/回调幂等/下发失败重试单测 + Checkstyle 0）
2. 前端：`npm run lint`、`npm run build`（`frontend/platform-apps/whale-crm`）。
3. 迁移动态校验由既有 Flyway 测试覆盖：版本递增、无 DROP/TRUNCATE/DELETE。
4. 文档回填后执行 `powershell -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1`，必须 PASS。

## 七、文档回填

- `docs/plans/2026-11-04-wave5.83-crm-panorama.md`：§M3 标记 7f-2 已完成（V8 三表、权限 1866–1873、菜单 429、内网下发契约、outbox 重试对账）。
- `docs/plans/2026-11-06-five-services-backlog-stepwise.md`：Step 7 状态补 7f-2。
- `docs/service-catalog.md`：CRM（C-11 / 9007）迁移与能力行补 V8 与商务订单；ERP 补 V17 来源字段。
- `docs/devlog/2026-11-07.md`：追加「Step 7f-2：CRM M3 商务订单」小节。
- 设计稿 `docs/design/crm-module-panorama.md` §6.2 注明订单三表落在 V8（V7 已交付合同/回款）。

## 八、提交范围（精确暂存）

- `backend/whale-apps/whale-erp/**`（V17、实体/仓储/服务/内网控制器）
- `backend/whale-apps/whale-crm/**`（V8、commerce 订单代码、配置、单测）
- `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.83-crm-m3-order.sql`
- `frontend/platform-apps/whale-crm/**`
- 上述 5 份文档
- 排除红线：`.trae/documents/*.md`、`whale-mall/**`、`backend/whale-services/pom.xml`、`whale-gateway/application.yml`、`deploy/monitoring/prometheus.yml`、`infrastructure/docker/mysql/init/01-init-databases.sql`、`whale-map/**`、`migration-5.91-map-m0.sql`。

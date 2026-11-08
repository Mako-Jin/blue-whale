# 7f-4c 退换货模块实施计划

## Context

CRM M4 售后 4a（工单 / SLA）与 4b（知识引用 / 满意度）已完成并推送。本批按已批准计划
[.trae/documents/crm-m4-step7f4-aftersale.md](file:///f:/blue-whale/.trae/documents/crm-m4-step7f4-aftersale.md#L86-L119)
完成 4c：CRM 退换货申请（仅退货执行，换货字段预留）、审批 → outbox 幂等下发 ERP 退货过账、
回读 `erpReturnNo` / 状态、前端退换货页与菜单 436。

约束不变：只追加 Flyway；CRM 不写库存 / 应收 / 凭证（复用 ERP `post`）；`/crm/internal/**` 仅内网；
每批独立验证；精确 `git add`；不触碰红线文件。

## 现状事实

- `V10__service.sql` 已建 `crm_return_request` / `crm_return_item`（4a 提交内建齐，无需改历史）。
- ERP 迁移最新 `V17__scm_crm_order_source.sql`，本批新增 `V18`。
- CRM 错误码已用到 41068；41069–41073 空闲。权限 `RETURN_LIST` / `RETURN_MANAGE` 与 Auth 菜单 436 已登记。
- outbox 表 `crm_common_outbox` 与 `CrmErpProperties`（重试退避 / 批大小 / 开关）可直接复用。
- ERP 销售退货 `create` / `post` 已实现库存入库、应收 offset、红字凭证；本批只加内网幂等入口。
- 可参照实现：
  - [SalesOrderAppServiceImpl.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/commerce/application/service/impl/SalesOrderAppServiceImpl.java)（submit / 回调 / outbox / 退避）
  - [OrderOutboxRepositoryImpl.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/commerce/infrastructure/persistence/repository/OrderOutboxRepositoryImpl.java)
  - [KbAppServiceImpl.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/service/application/service/impl/KbAppServiceImpl.java)（service 上下文分层）
  - [ErpOrderInternalClient.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/commerce/infrastructure/client/ErpOrderInternalClient.java)
  - [SalesReturnAppServiceImpl.java](file:///f:/blue-whale/backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/scm/application/service/impl/SalesReturnAppServiceImpl.java)（`create` / `post`）
  - [SalesAppServiceImpl.java](file:///f:/blue-whale/backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/scm/application/service/impl/SalesAppServiceImpl.java#L156-L208)（`createFromCrm` 幂等范式）
  - [pages/order/index.tsx](file:///f:/blue-whale/frontend/platform-apps/whale-crm/src/pages/order/index.tsx)（列表 + 建单 + 详情 + 提交审批 / 重试 / 取消 / 对账）

## 实施步骤

### 1. CRM 公共与配置

1. `common/CrmErrorCode.java` 追加常量并登记 `MESSAGES`：
   - 41069 `RETURN_REQUEST_NOT_FOUND`、41070 `RETURN_STATE_CONFLICT`、41071 `RETURN_QUANTITY_EXCEEDED`、
     41072 `RETURN_ERP_UNAVAILABLE`、41073 `RETURN_ERP_FAILED`。
2. 新增 `config/CrmReturnApprovalProperties.java`（前缀 `whale.crm.return-approval`：`enabled=true`、
   `definitionId`、`workflowUrl=http://127.0.0.1:8083`、连接 / 读取超时），并在
   [WhaleCrmApplication.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/WhaleCrmApplication.java#L32-L34)
   的 `@EnableConfigurationProperties` 注册。

### 2. CRM 领域层（`com.bluewhale.crm.service`）

新增文件：

- `domain/model/ReturnRequest.java`：DDL 全字段映射 + 状态机行为
  `submit(instanceId, at)`、`applyApproval(approved, at)`、`beginExecute()`、
  `markCompleted(erpReturnNo, erpStatus)`、`markFailed(error)`、`refreshErpStatus(...)`、`cancel()`。
  状态：`DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/EXECUTING/COMPLETED/FAILED/CANCELLED`；
  仅 `DRAFT/REJECTED` 可编辑 / 提交 / 取消；仅 `APPROVED/EXECUTING/FAILED` 可执行；`COMPLETED` 终态。
- `domain/model/ReturnRequestItem.java`：`lineNo/orderLineNo/itemCode/itemName/quantity/unitPrice/amount`。
- `domain/model/ReturnStatus.java`：枚举 + `from(String)`（非法值抛 41070）。
- `domain/model/ReturnRequestQuery.java`：`current/size/keyword/status/customerId/orderId/ownerId`。
- `domain/model/ReturnOutboxEvent.java`：`id/tenantId/aggregateId/status/retryCount/nextRetryAt/lastError`。
- `domain/repository/ReturnRequestRepository.java`：
  `page/count/findById/findItems/findByApprovalInstanceId/insert/update/applyApprovalResult/findByStatuses/maxSequence/sumReturnedQuantity(orderId, orderLineNo)`。
  可退量只统计 `APPROVED/EXECUTING/COMPLETED`（按计划口径；ERP 为最终权威）。
- `domain/repository/ReturnOutboxRepository.java`：
  `enqueue/findPending/markSent/markFailed/resetForRetry/cancelByRequestId`。
- `domain/port/ErpReturnPort.java` + `ErpReturnCommand` / `ErpReturnResult`
  （command：`crmReturnNo/erpOrderNo/docDate/remark/lines[orderLineNo,quantity]`；
  result：`erpReturnId/erpReturnNo/status/totalAmount`）。
- `domain/port/ReturnApprovalPort.java`：`start(definitionId, title, businessKey, variables, actor)`。

### 3. CRM 应用层

新增：

- `application/command/ReturnRequestCommand.java`（`requestType/orderId/reason/lines`）、
  `ReturnRequestLineCommand.java`（`orderLineNo/quantity`）、
  `ReturnApprovalCallbackCommand.java`（`instanceId/approved/comment`）。
- `application/view/ReturnRequestView.java`（全部字段 + `items`，`from(ReturnRequest, List<ReturnRequestItem>)`）。
- `application/service/ReturnRequestAppService.java`：
  `page/detail/create/update/submit/cancel/retry/reconcile(id)/handleApprovalCallback/pushPending/reconcileAll`。
- `application/service/impl/ReturnRequestAppServiceImpl.java`：
  - `assertGate()`：`CrmProperties.demandGatePassed` + `CrmServiceProperties.enabled`。
  - 数据范围：非 `allDataScope` 按 `ownerId` 过滤（page 与 detail / 写操作一致）。
  - `create`：读取 `SalesOrderRepository.findById`，要求存在且 `erpOrderNo` 非空（否则 41070）；
    用 `CustomerAppService.detail` 取客户编码 / 名称；逐行校验 `orderLineNo` 存在、数量 > 0、
    `已申请量 + 本行数量 ≤ 订单行 quantity`（超限抛 41071）；快照物料 / 单价并算行金额；
    单号 `RET-yyyyMMdd-` + `maxSequence(prefix)+1` 四位；状态 `DRAFT`。
  - `update`：仅 `DRAFT/REJECTED`，全量替换行并重算金额。
  - `submit`：仅 `DRAFT/REJECTED`；审批未启用 / 无定义抛 41070 自定义提示；
    `ReturnApprovalPort.start(..., "crm-return:" + id, vars, actor)` → `PENDING_APPROVAL`。
  - `handleApprovalCallback`：按 `approvalInstanceId` 找单；`applyApprovalResult` 条件更新（仅
    `PENDING_APPROVAL` 命中）；通过时同事务 `ReturnOutboxRepository.enqueue(id, payload, now)`。
  - `pushPending`：受 `CrmErpProperties.enabled` 控制；逐条 `TenantContext.set(tenantId)`；
    `pushOne` 在 `COMPLETED` / 非可执行状态时直接 `markSent`；成功 `markCompleted` + `markSent`，
    失败 `markFailed` + outbox 退避（复用订单的指数退避与 `maxAttempts` 静默 30 天口径）。
  - `retry`：仅 `FAILED/APPROVED`（ERP 未启用抛 41072），`resetForRetry`。
  - `reconcile(id)` 与 `reconcileAll()`：按 `crmReturnNo` 调 `ErpReturnPort.findByCrmReturnNo`；
    命中则 `markCompleted` 并关闭 outbox；未命中且执行中 / 失败则重置 outbox 重试。
  - ERP 调用异常统一转 `41072/41073` 文案并落 `last_error`（长度截断 1000）。

### 4. CRM 基础设施与接口

新增：

- `infrastructure/persistence/entity/ReturnRequestEntity.java`、`ReturnRequestItemEntity.java`（`@TableName` + `BaseEntity`）。
- `infrastructure/persistence/mapper/ReturnRequestMapper.java`、`ReturnRequestItemMapper.java`。
- `infrastructure/persistence/repository/ReturnRequestRepositoryImpl.java`
  （MyBatis-Plus，分页 `limit offset,size`，`applyApprovalResult` 条件更新，插入头 + 行，行全量替换）。
- `infrastructure/persistence/repository/ReturnOutboxRepositoryImpl.java`
  （同表 `crm_common_outbox`，`AGGREGATE_TYPE=crm_return_request`、`EVENT_TYPE=RETURN_REQUEST_EXECUTE`）。
- `infrastructure/client/ErpReturnInternalClient.java`（`InternalTokenSupport` + `RestClient` + 租户头）；
  路径 `POST /erp/internal/scm/sales-return`、`GET /erp/internal/scm/sales-return/by-crm-return-no`。
- `infrastructure/client/WorkflowReturnApprovalClient.java`（参照 `WorkflowOrderApprovalClient`）。
- `interfaces/controller/CrmReturnController.java`：
  `GET /crm/return/page`、`GET /crm/return/{id}`（1889）；
  `POST /crm/return`、`PUT /crm/return/{id}`、`POST /{id}/submit|retry|cancel|reconcile`（1890）；
  写接口挂 `@AuditLog`，返回 `Result` / `PageResult`。
- `interfaces/controller/ReturnInternalController.java`：`POST /crm/internal/return/approval-callback`。
- `interfaces/scheduler/CrmReturnSyncJob.java`：复用 `whale.crm.erp.push-cron` / `reconcile-cron`，
  调用 `pushPending()` / `reconcileAll()`。

### 5. ERP 侧

- 新增 `V18__scm_crm_return_source.sql`（仅 `ALTER TABLE`）：
  `erp_scm_sales_return` 增 `crm_return_no/source_type/source_id`，
  `ADD UNIQUE KEY uk_sr_crm_return_no (tenant_id, crm_return_no)`；文件头沿用 V17 风格。
- `SalesReturn` 领域模型 / `SalesReturnEntity` / `SalesReturnRepositoryImpl` 增三字段映射。
- `SalesReturnRepository` 增 `Optional<SalesReturn> findByCrmReturnNo(String)`。
- `SalesReturnCreateCommand` 增可选 `docNo/crmReturnNo/sourceType/sourceId`；
  `SalesReturnAppServiceImpl.create` 优先使用传入 `docNo`，并写入来源字段（不影响既有调用）。
- 新增 `SalesReturnInternalCreateCommand`（`crmReturnNo` 必填、`salesOrderId` 或 `erpOrderNo`、`docDate`、`remark`、`lines[orderLineNo,quantity]`）。
- `SalesReturnAppService` / Impl 增：
  - `createInternal`：先按 `crmReturnNo` 回查幂等返回；缺失则解析订单（`salesOrderId` 或
    `findByDocNo(erpOrderNo)`），组装 `SalesReturnCreateCommand`（`docNo=CRM-SR-{crmReturnNo}`），
    复用 `create` 后立即 `post(id, null)`；捕获 `DuplicateKeyException` 后回查返回。
  - `findByCrmReturnNo`：不存在返回 `null`。
- [ErpInternalScmController.java](file:///f:/blue-whale/backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/controller/ErpInternalScmController.java)
  追加 `POST /sales-return` 与 `GET /sales-return/by-crm-return-no`。

### 6. 前端（`frontend/platform-apps/whale-crm`）

- `src/api/service.ts` 追加退货类型与接口：
  `ReturnRequestView/ReturnItemView/ReturnPageQuery/ReturnCreateInput`、
  `pageReturns/getReturn/createReturn/updateReturn/submitReturn/retryReturn/cancelReturn/reconcileReturn`。
- `src/utils/dict.ts` 追加 `RETURN_STATUS_OPTIONS/LABEL/COLOR` 与 `RETURN_TYPE_LABEL`。
- 新增 `src/pages/return/index.tsx`（参照订单页）：
  列表 + 状态 / 关键字筛选；建单弹窗选择商务订单并按其行填写退货数量；
  详情抽屉展示 ERP 退货单号、状态、失败原因；提交审批 / 重试 / 取消 / 对账按钮均按
  `sys:crm:return:manage` 控制。
- `src/App.tsx` 注册菜单 `{ key: '/return', label: '退换货', permission: 'sys:crm:return:list' }`、
  `resolveSelectedKey` 分支与路由 `<Route path="/return" element={<ReturnPage />} />`。

### 7. 测试

- CRM `service/domain/model/ReturnRequestTest`：状态机合法 / 非法迁移。
- CRM `service/application/service/impl/ReturnRequestAppServiceImplTest`：
  建单超可退量抛 41071；提交审批写实例号；审批通过写 outbox；执行成功置 `COMPLETED` 并回填 ERP 单号；
  执行失败置 `FAILED` 并退避。
- ERP `SalesReturnAppServiceImplTest` 追加：
  `createInternal` 已存在时幂等返回且不重复建单；缺失时单号 `CRM-SR-*` 且调用 `post`。
- 迁移测试自动覆盖 `V18`（`ErpFlywayMigrationScriptTest` 版本递增 / 无破坏语句）。

## 明确不做

- 换货自动补发、ERP 履约事件订阅、匿名 H5、退换货附件。
- 不新增 CRM Flyway（V10 已含两表）；不改 `whale-gateway/application.yml`。

## 验证

```powershell
mvn -o -q -pl backend/whale-apps/whale-crm test
mvn -o -q -pl backend/whale-apps/whale-erp test
mvn -o -q -pl backend/whale-apps/whale-crm checkstyle:check
mvn -o -q -pl backend/whale-apps/whale-erp checkstyle:check
cd frontend; node ../../node_modules/eslint/bin/eslint.js platform-apps/whale-crm/src --max-warnings 0
cd frontend/platform-apps/whale-crm; node ../../../node_modules/typescript/bin/tsc -b
cd frontend/platform-apps/whale-crm; node ../../../node_modules/vite/bin/vite.js build
powershell -File scripts/check-doc-consistency.ps1
```

全部通过后精确 `git add` 本批文件（CRM service 新增、CrmErrorCode、CrmReturnApprovalProperties、
WhaleCrmApplication、ERP V18 与退货文件、前端 return / service.ts / dict.ts / App.tsx），
提交并推送 `origin/main`，输出四段式报告。

# Wave 5.78 ERP M2 剩余 B 第一期：采购退货 + 销售退货

> 状态：**待确认（Spec）**，确认后进入实现。
> 关联计划：[2026-11-03-wave5.78-erp-panorama.md](../../plans/2026-11-03-wave5.78-erp-panorama.md) §12.4。
> 关联设计：[erp-module-panorama.md](../../design/erp-module-panorama.md) §3.2 / §5.1。
> 前置：M0–M6 已落地；采购订单、销售订单、库存移动加权、自动记账、应收应付核销均已可用。

---

## 1. 目标与边界

### 1.1 本期交付

实现**采购退货**（红字出库）与**销售退货**（红字入库）两张单据的完整闭环：

```
建单（引用源订单行的已收/已发数量）
  → 过账：库存反向流水
          → 应付 / 应收按未核销余额冲减
          → 红字凭证（借贷反向）
```

### 1.2 明确不做（留后续）

采购申请、销售报价、批次/安全库存/呆滞、价格表、信用预占、财务扩展 D5、退货审批流（OA）、退货换货与补货、按收货单/发货单粒度精确冲减。

### 1.3 关键业务假设（可评审）

| 编号 | 假设 | 理由 |
|---|---|---|
| A1 | 退货必须引用一张已收货/已发货的订单，数量不得超过「已收/已发 − 已退货」 | 与订单闭环一致，避免凭空退货 |
| A2 | 退货仓库取源订单仓库，建单时不可改 | 库存与订单口径一致 |
| A3 | 采购退货按**订单单价**出库（`OUT`），库存余额数量与金额同步扣减 | 与应付冲减金额一致，凭证天然平衡 |
| A4 | 销售退货按**当前移动加权平均成本**入库（`IN`）；无余额时成本为 0，跳过成本分录 | 现有模型未保存发货批次成本，取当前均价是最近似值 |
| A5 | 应付/应收冲减按**往来方未核销台账**从早到晚分摊；未核销不足则整单拒绝 | 台账未记录收货单号，无法精确回溯到原收货单；绝不生成负数单据 |
| A6 | 单据状态 `DRAFT → POSTED`，`DRAFT` 可 `CANCELLED`；过账后不可撤销 | 与调拨/盘点一致；红冲通过反向单据实现 |
| A7 | 幂等键：库存流水 `sourceType + sourceId`（退货单号）；凭证 `uk_voucher_source`；冲减由过账状态保证只执行一次 | 复用既有唯一键，不新增 |

---

## 2. 数据模型（V9__scm_returns.sql）

> 规则：仅 `CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`；禁止 DROP/TRUNCATE/DELETE；金额 `DECIMAL(18,2)`，数量/单价/成本 `DECIMAL(18,4)`；逻辑外键；`tenant_id` 行级隔离；`deleted` 逻辑删除。

### 2.1 `erp_scm_purchase_return`（采购退货单头）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键（雪花） |
| tenant_id | BIGINT | 租户 |
| doc_no | VARCHAR(64) | 退货单号 `PR-yyyyMMdd-####`，`uk_pr_doc_no(tenant_id, doc_no)` |
| purchase_order_id | BIGINT | 源采购订单 |
| supplier_id / supplier_name | BIGINT / VARCHAR(128) | 供应商快照 |
| doc_date | DATE | 退货日期 |
| warehouse_id | BIGINT | 退货出库仓库 |
| status | VARCHAR(24) | `DRAFT/POSTED/CANCELLED`，默认 `DRAFT` |
| currency | VARCHAR(8) | 默认 `CNY` |
| total_amount | DECIMAL(18,2) | 退货总额 |
| remark | VARCHAR(255) | 备注 |
| create_time / update_time / deleted | 标准审计列 | 与既有表一致 |

索引：`idx_pr_order(tenant_id, purchase_order_id)`、`idx_pr_supplier(tenant_id, supplier_id)`、`idx_pr_date(doc_date)`。

### 2.2 `erp_scm_purchase_return_line`

| 字段 | 类型 | 说明 |
|---|---|---|
| id / tenant_id | BIGINT | 标准 |
| return_id | BIGINT | 退货单头 |
| line_no | INT | 单内行号 |
| order_line_no | INT | 源订单行号（用于可退数量校验） |
| item_id / item_code / item_name / uom | 同 V3 分录快照 | 物料快照 |
| quantity | DECIMAL(18,4) | 退货数量 |
| unit_price | DECIMAL(18,2) | 订单单价 |
| amount | DECIMAL(18,2) | 行金额 |
| unit_cost | DECIMAL(18,4) | 出库单位成本（= unit_price） |
| cost_amount | DECIMAL(18,2) | 出库成本金额（= amount） |

唯一键 `uk_pr_line(tenant_id, return_id, line_no)`；索引 `idx_pr_line_item(tenant_id, item_id)`、`idx_pr_line_order(tenant_id, order_line_no)`。

### 2.3 `erp_scm_sales_return`（销售退货单头）

字段同上，替换为 `sales_order_id`、`customer_id`、`customer_name`；唯一键 `uk_sr_doc_no(tenant_id, doc_no)`、`idx_sr_order`、`idx_sr_customer`、`idx_sr_date`。

### 2.4 `erp_scm_sales_return_line`

字段同 2.2，`return_id` 指向销售退货头；`unit_cost/cost_amount` 在过账时写入（入库成本）。

---

## 3. 后端实现（`com.bluewhale.erp.scm` / `finance` / `common`）

### 3.1 领域与常量

| 文件 | 变更 |
|---|---|
| `scm/domain/model/ScmStatus.java` | 新增 `SOURCE_PURCHASE_RETURN="PURCHASE_RETURN"`、`SOURCE_SALES_RETURN="SALES_RETURN"` |
| `scm/domain/model/PurchaseReturn.java` | 聚合根：头字段 + `List<PurchaseReturnLine>`；`post()` 仅 `DRAFT→POSTED`；`cancel()` 仅 `DRAFT→CANCELLED`；失败抛 `RETURN_STATE_CONFLICT` |
| `scm/domain/model/PurchaseReturnLine.java` | 行字段 + `calculateAmount()`（单价 × 数量，2 位） |
| `scm/domain/model/SalesReturn.java` / `SalesReturnLine.java` | 同上结构 |
| `scm/domain/repository/PurchaseReturnRepository.java` | `page(ScmQuery)`、`count`、`findById`（含行）、`insert`、`update`、`nextDocNo(LocalDate)`、`sumReturnedQuantity(Long purchaseOrderId, Integer orderLineNo)` |
| `scm/domain/repository/SalesReturnRepository.java` | 同上（`sumReturnedQuantity(Long salesOrderId, Integer orderLineNo)`） |

`sumReturnedQuantity` 实现：先按 `purchase_order_id`/`sales_order_id` 查非 `CANCELLED` 退货头 ID，再按 `return_id in (...) + order_line_no` 汇总行数量；无单据返回 `BigDecimal.ZERO`。

### 3.2 库存余额领域方法

`scm/domain/model/StockBalance.java` 新增：

```java
public BigDecimal outboundAt(BigDecimal quantity, BigDecimal unitCost)
```

- 校验数量 > 0、成本 ≥ 0；可用数量不足抛 `NEGATIVE_STOCK`；
- `quantity -= qty`、`amount -= unitCost × qty`（2 位 HALF_UP）；
- 数量归零时数量与金额同步归零（与 `outbound` 一致）；
- 返回本次出库成本金额。

### 3.3 应用服务

| 文件 | 内容 |
|---|---|
| `scm/application/command/PurchaseReturnCreateCommand.java` | `purchaseOrderId`、`docDate`、`remark`、`lines[{orderLineNo, quantity}]`，`@NotNull/@Valid` |
| `scm/application/command/SalesReturnCreateCommand.java` | `salesOrderId`、`docDate`、`remark`、`lines[{orderLineNo, quantity}]` |
| `scm/application/view/ReturnLineView.java` | 退货行公共视图（`lineNo/orderLineNo/itemCode/itemName/uom/quantity/unitPrice/amount/unitCost/costAmount`） |
| `scm/application/view/PurchaseReturnView.java` / `SalesReturnView.java` | 头视图 + `lines`，提供静态 `of(...)` |
| `scm/application/service/PurchaseReturnAppService.java` | `page`、`detail`、`create`、`post(id, operatorId)`、`cancel(id)` |
| `scm/application/service/SalesReturnAppService.java` | 同上 |
| `scm/application/service/impl/PurchaseReturnAppServiceImpl.java` | 见 3.3.1 |
| `scm/application/service/impl/SalesReturnAppServiceImpl.java` | 见 3.3.2 |

#### 3.3.1 采购退货

- `create`：加载源订单（不存在 → `PURCHASE_ORDER_NOT_FOUND`）；状态须为 `PARTIALLY_RECEIVED/RECEIVED`（否则 `RETURN_INVALID`）；仓库取订单仓库；逐行校验 `orderLineNo` 存在、数量 > 0、`已退 + 本次 ≤ receivedQuantity`（否则 `RETURN_QUANTITY_EXCEEDED`）；`unit_price` 取订单行；汇总 `totalAmount`；`status=DRAFT`；`docNo=nextDocNo(docDate)`；保存。
- `post`：状态须 `DRAFT`；逐行复核 `sumReturnedQuantity(orderId, orderLineNo) ≤ receivedQuantity`（此时已含本单）；查余额（无则 `STOCK_NOT_FOUND`）→ `outboundAt(qty, unitPrice)` → 保存余额 → 写 `OUT` 流水（`sourceType=PURCHASE_RETURN`、`sourceId=docNo`）；累计金额；调 `arApAppService.offsetPayable(supplierId, totalAmount)`；调 `autoVoucherAppService.postPurchaseReturn(cmd)`；`status=POSTED`；更新单头。
- `cancel`：仅 `DRAFT`。

#### 3.3.2 销售退货

- `create`：加载源订单（不存在 → `SALES_ORDER_NOT_FOUND`）；状态须 `PARTIALLY_SHIPPED/SHIPPED`；仓库取订单仓库；逐行校验 `已退 + 本次 ≤ shippedQuantity`；`unit_price` 取订单行；`DRAFT` 保存。
- `post`：状态须 `DRAFT`；逐行复核累计已退 ≤ `shippedQuantity`；读取余额（不存在则按 0 成本新建余额）；`unitCost = 数量>0 ? 余额金额/余额数量 : 0`（4 位），`balance.inbound(qty, unitCost)`，保存并写 `IN` 流水（`sourceType=SALES_RETURN`）；累计销售金额与成本金额；回写行 `unit_cost/cost_amount`；调 `arApAppService.offsetReceivable(customerId, totalAmount)`；调 `autoVoucherAppService.postSalesReturn(cmd with costAmount)`；`POSTED`。
- `cancel`：仅 `DRAFT`。

### 3.4 应收应付冲减

| 文件 | 变更 |
|---|---|
| `finance/domain/model/Payable.java` | 新增 `offsetReturn(BigDecimal)`：金额必须 > 0；`offset ≤ remaining()` 否则 `RETURN_OFFSET_EXCEEDED`；`amount -= offset`；`refreshStatus()`（`remaining=0 → SETTLED`，否则保持/`PARTIALLY_SETTLED`） |
| `finance/domain/model/Receivable.java` | 新增 `offsetReturn(BigDecimal)`，口径含坏账金额；复用 `refreshStatus()` |
| `finance/application/service/ArApAppService.java` | 新增 `void offsetPayable(Long supplierId, BigDecimal amount)`、`void offsetReceivable(Long customerId, BigDecimal amount)` |
| `finance/application/service/impl/ArApAppServiceImpl.java` | 过滤 `listOutstanding()` 中该往来方、`remaining>0`，按 `docDate, id` 升序分摊 `min(remaining, 剩余待冲)`；每笔调用 `offsetReturn` 并 `update`；分摊后仍有余额 → `RETURN_OFFSET_EXCEEDED("未核销余额不足：需冲减 X，可用 Y")` |

### 3.5 自动凭证

| 文件 | 变更 |
|---|---|
| `finance/application/service/AutoVoucherAppService.java` | 新增 `postPurchaseReturn(AutoVoucherCommand)`、`postSalesReturn(AutoVoucherCommand)` |
| `finance/application/service/impl/AutoVoucherAppServiceImpl.java` | 新增两个方法，复用 `existingId` / `requireOpenPeriod`，凭证类型 `MANUAL`、状态 `DRAFT→post` 后记 `POSTED` |

分录规则（`ErpFinanceAccountProperties`）：

- **采购退货**：借 `payable`（金额）/ 贷 `inventory`（金额），摘要「采购退货 {docNo}」。
- **销售退货**：借 `revenue`（金额）/ 贷 `receivable`（金额）；若 `costAmount > 0`，借 `inventory`（成本）/ 贷 `cost`（成本）。

### 3.6 接口（`interfaces/controller`）

`PurchaseReturnController`（`/erp/scm/purchase-return`）：

| 方法 | 路径 | 权限码 | 审计 |
|---|---|---|---|
| GET | `/page` | `sys:erp:purchase-return:list` | - |
| GET | `/{id}` | `sys:erp:purchase-return:list` | - |
| POST | `` | `sys:erp:purchase-return:add` | `CREATE` |
| POST | `/{id}/post` | `sys:erp:purchase-return:post` | `UPDATE` |
| POST | `/{id}/cancel` | `sys:erp:purchase-return:cancel` | `UPDATE` |

`SalesReturnController`（`/erp/scm/sales-return`）同构，权限码 `sys:erp:sales-return:*`。分页沿用 `PageParams.current/size` 与 `Result.ok` / `PageResult.of`。

### 3.7 错误码（`common/ErpErrorCode.java`）

| 码 | 常量 | 文案 |
|---|---|---|
| 40023 | `RETURN_INVALID` | 退货参数非法：源订单未收货/发货、仓库或行号不正确 |
| 40435 | `PURCHASE_RETURN_NOT_FOUND` | 采购退货单不存在或无权访问 |
| 40436 | `SALES_RETURN_NOT_FOUND` | 销售退货单不存在或无权访问 |
| 40936 | `RETURN_QUANTITY_EXCEEDED` | 退货数量超过订单可退数量 |
| 40937 | `RETURN_STATE_CONFLICT` | 退货单当前状态不允许该操作 |
| 40938 | `RETURN_OFFSET_EXCEEDED` | 退货冲减额超过往来未核销余额 |

同时补充 `MESSAGES` 映射。

---

## 4. 权限与菜单（`migration-5.78-erp-returns.sql`）

- 权限 **507–514**：采购退货 `list/add/post/cancel` = 507–510；销售退货 = 511–514。
- 菜单 **98**「采购退货」`/erp/scm/purchase-return`、**99**「销售退货」`/erp/scm/sales-return`。
- 角色：复用 `25112 erp_scm`；`tenant_admin / erp_manager / erp_scm` 获 8 项；`erp_member / erp_finance` 仅 `507/511`（查询）。
- 全部 `NOT EXISTS` 幂等；不新增/不修改既有数据。

---

## 5. 前端（`frontend/platform-apps/whale-erp`、`whale-platform`）

| 文件 | 变更 |
|---|---|
| `src/api/scm.ts` | 新增 `ReturnLineView`、`PurchaseReturnView`、`SalesReturnView`、两套 Input、`pagePurchaseReturns/getPurchaseReturn/createPurchaseReturn/postPurchaseReturn/cancelPurchaseReturn` 与销售同构函数 |
| `src/pages/PurchaseReturnPage.tsx` | 列表 + 新建弹窗（选择已收货订单 → 加载行 → 输入退货数量）+ 详情 + 过账/取消；`hasPermission` 控制按钮 |
| `src/pages/SalesReturnPage.tsx` | 同构，选择已发货订单 |
| `src/App.tsx` | 新增菜单项「采购退货/销售退货」、`PageKind`、`initialPage` 路径识别、渲染分支 |
| `whale-platform/src/router/menu.ts` | 追加两条菜单（**保留全部既有条目**） |

---

## 6. 测试与门禁

### 6.1 新增测试

- `PurchaseReturnAppServiceImplTest`：建单成功、超可退数量拒绝、过账（库存出库 + 应付冲减 + 凭证调用）、重复过账拒绝、取消后不可过账。
- `SalesReturnAppServiceImplTest`：建单成功、超可发数量拒绝、过账（入库成本 = 当前均价、应收冲减、凭证带成本）。
- `Payable/Receivable.offsetReturn` 单测：正常冲减、超未核销拒绝、冲减后状态。
- `ErpFlywayMigrationScriptTest`：新增 V9 用例，断言 4 表与 `uk_pr_doc_no/uk_sr_doc_no/uk_pr_line/uk_sr_line`。
- ArchUnit 无需新增规则（沿用分层与上下文边界）。

### 6.2 门禁命令

1. `.\mvnw.cmd -pl :whale-erp test`
2. `.\mvnw.cmd -pl :whale-erp "-Pcoverage-gate" verify`（Checkstyle 0 违规）
3. `pnpm --filter whale-erp lint` / `test -- --run` / `build`（cwd `f:\blue-whale\frontend`）
4. `pnpm --filter whale-platform lint` / `build`
5. `powershell -File scripts/check-doc-consistency.ps1`
6. `powershell -File scripts/ci/bw-deploy-contract-check.ps1`

---

## 7. 文档回填

- 计划：§12.4 状态更新、§9 变更记录 v0.9。
- 设计：§3.2 供应链状态标记、变更记录 v0.10。
- 新增 `docs/reviews/2026-11-04-wave5.78-erp-m2-returns.md`（范围/实现/验证证据/偏差）。
- `docs/devlog/2026-11-04.md` 追加当日记录。
- `docs/service-catalog.md`：表数 62→66、端点 +10。
- `docs/requirements/register.md`：REQ-FUNC-038 备注退货已落地。

---

## 8. 交付与提交

- 只提交本 Spec 范围内文件（whale-erp、auth 迁移脚本、前端 whale-erp/whale-platform、上述文档）。
- **严禁**提交工作区中 MES/PDM/OA/CRM/Workflow 并行波次改动。
- 共享文档用「备份 → `git checkout HEAD --` → 仅写本期内容 → `git add` → `git commit -- <paths>` → 恢复备份」，避免夹带并行内容。
- 建议提交信息：`feat(erp): M2 剩余 B 第一期 采购退货与销售退货闭环`。

---

## 9. 验收标准

1. 采购退货过账后：库存减少、应付未核销减少、生成「借应付/贷库存」已记账凭证；四者金额一致。
2. 销售退货过账后：库存增加（按当前均价）、应收未核销减少、生成「借收入/贷应收 + 借库存/贷成本」凭证。
3. 任一退货行数量超过可退数量 → 40936；未核销余额不足 → 40938；重复过账 → 40937。
4. 空库 Flyway 可重放；V1–V9 全绿；后端/前端门禁与文档一致性全部 PASSED。

---

## 10. 待确认

- 是否接受 A3/A4/A5 的简化口径（按订单单价出库、按当前均价入库、按往来方未核销分摊）。
- 退货单是否需要「草稿→审核」双人审批（本期按 DRAFT→POSTED 单人闭环）。

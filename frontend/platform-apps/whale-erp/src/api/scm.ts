// ERP 供应链 API：采购订单、销售订单、库存余额/流水、库存调拨与盘点（经网关代理到 whale-erp）
import { http } from './client';
import type { PageResult } from './masterdata';

/** 单据分录视图（采购收货数量与销售发货数量分列） */
export interface OrderLineView {
  lineNo: number;
  itemId?: number;
  itemCode: string;
  itemName?: string;
  uom?: string;
  quantity: number;
  /** 采购订单已收数量 */
  receivedQuantity?: number;
  /** 销售订单已发数量 */
  shippedQuantity?: number;
  unitPrice: number;
  amount?: number;
}

/** 采购订单 */
export interface PurchaseOrderView {
  id: number;
  docNo: string;
  supplierId: number;
  supplierName?: string;
  docDate: string;
  expectedDate?: string;
  warehouseId: number;
  status: string;
  currency?: string;
  totalAmount: number;
  receivedAmount?: number;
  remark?: string;
  lines?: OrderLineView[];
}

/** 销售订单 */
export interface SalesOrderView {
  id: number;
  docNo: string;
  customerId: number;
  customerName?: string;
  docDate: string;
  deliveryDate?: string;
  warehouseId: number;
  status: string;
  currency?: string;
  totalAmount: number;
  shippedAmount?: number;
  remark?: string;
  lines?: OrderLineView[];
}

/** 订单分录入参 */
export interface OrderLineInput {
  itemCode: string;
  quantity: number;
  unitPrice: number;
  uom?: string;
}

/** 采购订单入参 */
export interface PurchaseOrderInput {
  supplierId: number;
  docDate: string;
  expectedDate?: string;
  warehouseId: number;
  currency?: string;
  remark?: string;
  lines: OrderLineInput[];
}

/** 销售订单入参 */
export interface SalesOrderInput {
  customerId: number;
  docDate: string;
  deliveryDate?: string;
  warehouseId: number;
  currency?: string;
  remark?: string;
  lines: OrderLineInput[];
}

/** 收货/发货行入参 */
export interface MovementLineInput {
  lineNo: number;
  quantity: number;
}

/** 采购收货入参 */
export interface ReceiveInput {
  receiveDate: string;
  lines: MovementLineInput[];
}

/** 销售发货入参 */
export interface ShipInput {
  shipDate: string;
  lines: MovementLineInput[];
}

/** 库存余额 */
export interface StockBalanceView {
  id: number;
  itemId: number;
  itemCode: string;
  itemName?: string;
  warehouseId: number;
  quantity: number;
  amount: number;
}

/** 库存流水 */
export interface StockMovementView {
  id: number;
  docNo: string;
  sourceType: string;
  itemId: number;
  itemCode: string;
  itemName?: string;
  warehouseId: number;
  direction: string;
  quantity: number;
  unitCost?: number;
  amount?: number;
  occurredAt?: string;
  remark?: string;
}

/** 库存调整入参（正数入库、负数出库） */
export interface StockAdjustInput {
  itemCode: string;
  warehouseId: number;
  quantity: number;
  remark?: string;
}

/** 库存调拨分录视图 */
export interface TransferLineView {
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName?: string;
  uom?: string;
  quantity: number;
  unitCost?: number;
  amount?: number;
}

/** 库存调拨单 */
export interface TransferView {
  id: number;
  docNo: string;
  transferDate: string;
  fromWarehouseId: number;
  fromWarehouseName?: string;
  toWarehouseId: number;
  toWarehouseName?: string;
  status: string;
  totalQuantity?: number;
  totalAmount?: number;
  remark?: string;
  lines?: TransferLineView[];
}

/** 库存调拨入参 */
export interface TransferInput {
  transferDate: string;
  fromWarehouseId: number;
  toWarehouseId: number;
  remark?: string;
  lines: { itemCode: string; quantity: number; uom?: string }[];
}

/** 库存盘点分录视图 */
export interface StocktakeLineView {
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName?: string;
  uom?: string;
  bookQuantity: number;
  countedQuantity: number;
  difference: number;
  unitCost?: number;
  amount?: number;
}

/** 库存盘点单 */
export interface StocktakeView {
  id: number;
  docNo: string;
  stocktakeDate: string;
  warehouseId: number;
  warehouseName?: string;
  status: string;
  gainAmount?: number;
  lossAmount?: number;
  remark?: string;
  lines?: StocktakeLineView[];
}

/** 库存盘点入参 */
export interface StocktakeInput {
  stocktakeDate: string;
  warehouseId: number;
  remark?: string;
  lines: { itemCode: string; countedQuantity: number; uom?: string }[];
}

/** 供应链查询参数 */
export interface ScmQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  partyId?: number;
  warehouseId?: number;
  itemId?: number;
  fromDate?: string;
  toDate?: string;
}

/** 构造查询串（空值不下发，默认每页 20 条） */
function buildQuery(params: ScmQueryParams): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  });
  if (!search.has('current')) search.set('current', '1');
  if (!search.has('size')) search.set('size', '20');
  return search.toString();
}

/** 采购订单分页 */
export function pagePurchaseOrders(
  params: ScmQueryParams = {},
): Promise<PageResult<PurchaseOrderView>> {
  return http.get<PageResult<PurchaseOrderView>>(
    `/erp/scm/purchase-order/page?${buildQuery(params)}`,
  );
}

/** 采购订单详情（含分录） */
export function getPurchaseOrder(id: number): Promise<PurchaseOrderView> {
  return http.get<PurchaseOrderView>(`/erp/scm/purchase-order/${id}`);
}

/** 新增采购订单 */
export function createPurchaseOrder(input: PurchaseOrderInput): Promise<PurchaseOrderView> {
  return http.post<PurchaseOrderView>('/erp/scm/purchase-order', input);
}

/** 审核采购订单 */
export function approvePurchaseOrder(id: number): Promise<PurchaseOrderView> {
  return http.post<PurchaseOrderView>(`/erp/scm/purchase-order/${id}/approve`);
}

/** 采购收货 */
export function receivePurchaseOrder(id: number, input: ReceiveInput): Promise<PurchaseOrderView> {
  return http.post<PurchaseOrderView>(`/erp/scm/purchase-order/${id}/receive`, input);
}

/** 取消采购订单 */
export function cancelPurchaseOrder(id: number): Promise<PurchaseOrderView> {
  return http.post<PurchaseOrderView>(`/erp/scm/purchase-order/${id}/cancel`);
}

/** 销售订单分页 */
export function pageSalesOrders(params: ScmQueryParams = {}): Promise<PageResult<SalesOrderView>> {
  return http.get<PageResult<SalesOrderView>>(`/erp/scm/sales-order/page?${buildQuery(params)}`);
}

/** 销售订单详情（含分录） */
export function getSalesOrder(id: number): Promise<SalesOrderView> {
  return http.get<SalesOrderView>(`/erp/scm/sales-order/${id}`);
}

/** 新增销售订单 */
export function createSalesOrder(input: SalesOrderInput): Promise<SalesOrderView> {
  return http.post<SalesOrderView>('/erp/scm/sales-order', input);
}

/** 审核销售订单 */
export function approveSalesOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/erp/scm/sales-order/${id}/approve`);
}

/** 销售发货 */
export function shipSalesOrder(id: number, input: ShipInput): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/erp/scm/sales-order/${id}/ship`, input);
}

/** 取消销售订单 */
export function cancelSalesOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/erp/scm/sales-order/${id}/cancel`);
}

/** 库存余额分页 */
export function pageStockBalances(
  params: ScmQueryParams = {},
): Promise<PageResult<StockBalanceView>> {
  return http.get<PageResult<StockBalanceView>>(`/erp/scm/stock/balance/page?${buildQuery(params)}`);
}

/** 库存流水分页 */
export function pageStockMovements(
  params: ScmQueryParams = {},
): Promise<PageResult<StockMovementView>> {
  return http.get<PageResult<StockMovementView>>(
    `/erp/scm/stock/movement/page?${buildQuery(params)}`,
  );
}

/** 库存调整（盘盈/盘亏） */
export function adjustStock(input: StockAdjustInput): Promise<StockMovementView> {
  return http.post<StockMovementView>('/erp/scm/stock/adjust', input);
}

/** 库存调拨单分页 */
export function pageTransfers(params: ScmQueryParams = {}): Promise<PageResult<TransferView>> {
  return http.get<PageResult<TransferView>>(`/erp/scm/transfer/page?${buildQuery(params)}`);
}

/** 库存调拨单详情（含分录） */
export function getTransfer(id: number): Promise<TransferView> {
  return http.get<TransferView>(`/erp/scm/transfer/${id}`);
}

/** 新增库存调拨单 */
export function createTransfer(input: TransferInput): Promise<TransferView> {
  return http.post<TransferView>('/erp/scm/transfer', input);
}

/** 库存调拨过账 */
export function postTransfer(id: number): Promise<TransferView> {
  return http.post<TransferView>(`/erp/scm/transfer/${id}/post`);
}

/** 取消库存调拨单 */
export function cancelTransfer(id: number): Promise<TransferView> {
  return http.post<TransferView>(`/erp/scm/transfer/${id}/cancel`);
}

/** 库存盘点单分页 */
export function pageStocktakes(params: ScmQueryParams = {}): Promise<PageResult<StocktakeView>> {
  return http.get<PageResult<StocktakeView>>(`/erp/scm/stocktake/page?${buildQuery(params)}`);
}

/** 库存盘点单详情（含分录） */
export function getStocktake(id: number): Promise<StocktakeView> {
  return http.get<StocktakeView>(`/erp/scm/stocktake/${id}`);
}

/** 新增库存盘点单（账面数量由后端快照） */
export function createStocktake(input: StocktakeInput): Promise<StocktakeView> {
  return http.post<StocktakeView>('/erp/scm/stocktake', input);
}

/** 库存盘点过账（生成差异流水与凭证） */
export function postStocktake(id: number): Promise<StocktakeView> {
  return http.post<StocktakeView>(`/erp/scm/stocktake/${id}/post`);
}

/** 取消库存盘点单 */
export function cancelStocktake(id: number): Promise<StocktakeView> {
  return http.post<StocktakeView>(`/erp/scm/stocktake/${id}/cancel`);
}

/** 退货分录视图 */
export interface ReturnLineView {
  lineNo: number;
  orderLineNo: number;
  itemId: number;
  itemCode: string;
  itemName?: string;
  uom?: string;
  quantity: number;
  unitPrice: number;
  amount?: number;
  unitCost?: number;
  costAmount?: number;
}

/** 采购退货单 */
export interface PurchaseReturnView {
  id: number;
  docNo: string;
  purchaseOrderId: number;
  supplierId: number;
  supplierName?: string;
  docDate: string;
  warehouseId: number;
  status: string;
  currency?: string;
  totalAmount: number;
  remark?: string;
  lines?: ReturnLineView[];
}

/** 销售退货单 */
export interface SalesReturnView {
  id: number;
  docNo: string;
  salesOrderId: number;
  customerId: number;
  customerName?: string;
  docDate: string;
  warehouseId: number;
  status: string;
  currency?: string;
  totalAmount: number;
  remark?: string;
  lines?: ReturnLineView[];
}

/** 退货分录入参（按源订单行号与数量） */
export interface ReturnLineInput {
  orderLineNo: number;
  quantity: number;
}

/** 采购退货入参 */
export interface PurchaseReturnInput {
  purchaseOrderId: number;
  docDate: string;
  remark?: string;
  lines: ReturnLineInput[];
}

/** 销售退货入参 */
export interface SalesReturnInput {
  salesOrderId: number;
  docDate: string;
  remark?: string;
  lines: ReturnLineInput[];
}

/** 采购退货单分页 */
export function pagePurchaseReturns(
  params: ScmQueryParams = {},
): Promise<PageResult<PurchaseReturnView>> {
  return http.get<PageResult<PurchaseReturnView>>(
    `/erp/scm/purchase-return/page?${buildQuery(params)}`,
  );
}

/** 采购退货单详情（含分录） */
export function getPurchaseReturn(id: number): Promise<PurchaseReturnView> {
  return http.get<PurchaseReturnView>(`/erp/scm/purchase-return/${id}`);
}

/** 新增采购退货单 */
export function createPurchaseReturn(input: PurchaseReturnInput): Promise<PurchaseReturnView> {
  return http.post<PurchaseReturnView>('/erp/scm/purchase-return', input);
}

/** 采购退货过账 */
export function postPurchaseReturn(id: number): Promise<PurchaseReturnView> {
  return http.post<PurchaseReturnView>(`/erp/scm/purchase-return/${id}/post`);
}

/** 取消采购退货单 */
export function cancelPurchaseReturn(id: number): Promise<PurchaseReturnView> {
  return http.post<PurchaseReturnView>(`/erp/scm/purchase-return/${id}/cancel`);
}

/** 销售退货单分页 */
export function pageSalesReturns(params: ScmQueryParams = {}): Promise<PageResult<SalesReturnView>> {
  return http.get<PageResult<SalesReturnView>>(`/erp/scm/sales-return/page?${buildQuery(params)}`);
}

/** 销售退货单详情（含分录） */
export function getSalesReturn(id: number): Promise<SalesReturnView> {
  return http.get<SalesReturnView>(`/erp/scm/sales-return/${id}`);
}

/** 新增销售退货单 */
export function createSalesReturn(input: SalesReturnInput): Promise<SalesReturnView> {
  return http.post<SalesReturnView>('/erp/scm/sales-return', input);
}

/** 销售退货过账 */
export function postSalesReturn(id: number): Promise<SalesReturnView> {
  return http.post<SalesReturnView>(`/erp/scm/sales-return/${id}/post`);
}

/** 取消销售退货单 */
export function cancelSalesReturn(id: number): Promise<SalesReturnView> {
  return http.post<SalesReturnView>(`/erp/scm/sales-return/${id}/cancel`);
}

/** 采购申请分录视图 */
export interface RequisitionLineView {
  lineNo: number;
  itemCode: string;
  itemName: string;
  uom?: string;
  quantity: number;
  unitPrice: number;
  amount: number;
}

/** 采购申请单 */
export interface PurchaseRequisitionView {
  id: number;
  docNo: string;
  supplierId: number;
  supplierName: string;
  docDate: string;
  requiredDate?: string;
  warehouseId: number;
  status: string;
  currency: string;
  totalAmount: number;
  convertedOrderNo?: string;
  remark?: string;
  lines: RequisitionLineView[];
}

/** 销售报价分录视图 */
export interface QuotationLineView {
  lineNo: number;
  itemCode: string;
  itemName: string;
  uom?: string;
  quantity: number;
  unitPrice: number;
  amount: number;
}

/** 销售报价单 */
export interface SalesQuotationView {
  id: number;
  docNo: string;
  customerId: number;
  customerName: string;
  docDate: string;
  validUntil?: string;
  warehouseId: number;
  status: string;
  currency: string;
  totalAmount: number;
  convertedOrderNo?: string;
  remark?: string;
  lines: QuotationLineView[];
}

/** 采购申请入参 */
export interface PurchaseRequisitionInput {
  supplierId: number;
  docDate: string;
  requiredDate?: string;
  warehouseId: number;
  currency?: string;
  remark?: string;
  lines: { itemCode: string; quantity: number; unitPrice: number; uom?: string }[];
}

/** 销售报价入参 */
export interface SalesQuotationInput {
  customerId: number;
  docDate: string;
  validUntil?: string;
  warehouseId: number;
  currency?: string;
  remark?: string;
  lines: { itemCode: string; quantity: number; unitPrice: number; uom?: string }[];
}

/** 采购申请单分页 */
export function pagePurchaseRequisitions(
  params: ScmQueryParams = {},
): Promise<PageResult<PurchaseRequisitionView>> {
  return http.get<PageResult<PurchaseRequisitionView>>(
    `/erp/scm/purchase-requisition/page?${buildQuery(params)}`,
  );
}

/** 采购申请单详情（含分录） */
export function getPurchaseRequisition(id: number): Promise<PurchaseRequisitionView> {
  return http.get<PurchaseRequisitionView>(`/erp/scm/purchase-requisition/${id}`);
}

/** 新增采购申请单 */
export function createPurchaseRequisition(
  input: PurchaseRequisitionInput,
): Promise<PurchaseRequisitionView> {
  return http.post<PurchaseRequisitionView>('/erp/scm/purchase-requisition', input);
}

/** 审核采购申请单 */
export function approvePurchaseRequisition(id: number): Promise<PurchaseRequisitionView> {
  return http.post<PurchaseRequisitionView>(`/erp/scm/purchase-requisition/${id}/approve`);
}

/** 采购申请转采购订单 */
export function convertPurchaseRequisition(id: number): Promise<PurchaseRequisitionView> {
  return http.post<PurchaseRequisitionView>(`/erp/scm/purchase-requisition/${id}/convert`);
}

/** 取消采购申请单 */
export function cancelPurchaseRequisition(id: number): Promise<PurchaseRequisitionView> {
  return http.post<PurchaseRequisitionView>(`/erp/scm/purchase-requisition/${id}/cancel`);
}

/** 销售报价单分页 */
export function pageSalesQuotations(
  params: ScmQueryParams = {},
): Promise<PageResult<SalesQuotationView>> {
  return http.get<PageResult<SalesQuotationView>>(
    `/erp/scm/sales-quotation/page?${buildQuery(params)}`,
  );
}

/** 销售报价单详情（含分录） */
export function getSalesQuotation(id: number): Promise<SalesQuotationView> {
  return http.get<SalesQuotationView>(`/erp/scm/sales-quotation/${id}`);
}

/** 新增销售报价单 */
export function createSalesQuotation(input: SalesQuotationInput): Promise<SalesQuotationView> {
  return http.post<SalesQuotationView>('/erp/scm/sales-quotation', input);
}

/** 客户接受销售报价 */
export function acceptSalesQuotation(id: number): Promise<SalesQuotationView> {
  return http.post<SalesQuotationView>(`/erp/scm/sales-quotation/${id}/accept`);
}

/** 销售报价转销售订单 */
export function convertSalesQuotation(id: number): Promise<SalesQuotationView> {
  return http.post<SalesQuotationView>(`/erp/scm/sales-quotation/${id}/convert`);
}

/** 取消销售报价单 */
export function cancelSalesQuotation(id: number): Promise<SalesQuotationView> {
  return http.post<SalesQuotationView>(`/erp/scm/sales-quotation/${id}/cancel`);
}

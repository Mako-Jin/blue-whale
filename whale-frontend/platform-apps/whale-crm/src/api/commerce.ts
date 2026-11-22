// CRM 商务域 API 层（M3 / 7f-1、7f-2）：合同台账投影 + 回款计划与催收 + 商务订单
//
// 对应控制器：
// - CrmContractController    /crm/contract
// - CrmCollectionController  /crm/collection
// - CrmSalesOrderController  /crm/order
//
// 合同正文与签署归 whale-contract；实收与记账归 ERP，本层只读写 CRM 投影与计划；
// 商务订单审批归 whale-workflow，履约 / 发货 / 库存 / 应收归 ERP，CRM 只保留下发与回写。

import { http } from './client';
import type { PageResult } from './client';

/** 合同台账视图（ContractView） */
export interface ContractView {
  id: number;
  contractId: number;
  contractNo?: string | null;
  customerId: number;
  title?: string | null;
  category?: string | null;
  amount?: number | string | null;
  currency?: string | null;
  status?: string | null;
  effectiveDate?: string | null;
  expireDate?: string | null;
  renewalStatus?: string | null;
  renewalOwnerId?: number | null;
  renewalRemark?: string | null;
  renewalAt?: string | null;
  lastSyncAt?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  remark?: string | null;
  daysToExpire?: number | null;
  expired?: boolean | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 合同台账分页查询 */
export interface ContractPageQuery {
  keyword?: string;
  customerId?: number | null;
  status?: string;
  renewalStatus?: string;
  expireFrom?: string;
  expireTo?: string;
  current?: number;
  size?: number;
}

/** 续约标记入参 */
export interface ContractRenewalInput {
  renewalStatus: string;
  renewalOwnerId?: number | null;
  renewalRemark?: string;
}

/** 回款计划视图（CollectionPlanView） */
export interface CollectionPlanView {
  id: number;
  contractId: number;
  customerId: number;
  installmentNo: number;
  plannedAmount: number | string;
  plannedDate: string;
  status: string;
  priority: string;
  promiseDate?: string | null;
  promiseAmount?: number | string | null;
  overdueDays?: number | null;
  overdue?: boolean | null;
  ownerId?: number | null;
  deptId?: number | null;
  remark?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 回款计划分页查询 */
export interface CollectionPageQuery {
  contractId?: number | null;
  customerId?: number | null;
  status?: string;
  overdueOnly?: boolean;
  plannedFrom?: string;
  plannedTo?: string;
  current?: number;
  size?: number;
}

/** 回款分期入参 */
export interface CollectionInstallmentInput {
  plannedAmount: number;
  plannedDate: string;
  priorityOverride?: string;
  remark?: string;
}

/** 回款计划整批保存入参 */
export interface CollectionPlanSaveInput {
  contractId: number;
  installments: CollectionInstallmentInput[];
}

/** 承诺还款入参 */
export interface CollectionPromiseInput {
  promiseDate: string;
  promiseAmount: number;
  remark?: string;
}

/** 催收记录入参 */
export interface CollectionActionInput {
  actionType: string;
  content?: string;
  promiseDate?: string;
  promiseAmount?: number;
}

/** 催收记录视图（CollectionActionView） */
export interface CollectionActionView {
  id: number;
  planId: number;
  contractId: number;
  actionType: string;
  content?: string | null;
  promiseDate?: string | null;
  promiseAmount?: number | string | null;
  operatorId?: number | null;
  createTime?: string | null;
}

/** 合同台账分页 */
export function pageContracts(query: ContractPageQuery): Promise<PageResult<ContractView>> {
  return http.get<PageResult<ContractView>>('/crm/v1/contract/page', query as Record<string, unknown>);
}

/** 合同台账详情 */
export function getContract(id: number): Promise<ContractView> {
  return http.get<ContractView>(`/crm/v1/contract/${id}`);
}

/** 到期提醒 */
export function expiringContracts(days?: number): Promise<ContractView[]> {
  return http.get<ContractView[]>('/crm/v1/contract/expiring', { days });
}

/** 按客户同步合同投影 */
export function syncContracts(customerId: number): Promise<number> {
  return http.post<number>('/crm/v1/contract/sync', undefined, { customerId });
}

/** 标记合同续约 */
export function markContractRenewal(
  id: number,
  payload: ContractRenewalInput,
): Promise<ContractView> {
  return http.put<ContractView>(`/crm/v1/contract/${id}/renewal`, payload);
}

/** 回款计划分页 */
export function pageCollections(
  query: CollectionPageQuery,
): Promise<PageResult<CollectionPlanView>> {
  return http.get<PageResult<CollectionPlanView>>(
    '/crm/v1/collection/page',
    query as Record<string, unknown>,
  );
}

/** 回款计划详情 */
export function getCollection(id: number): Promise<CollectionPlanView> {
  return http.get<CollectionPlanView>(`/crm/v1/collection/${id}`);
}

/** 按合同整批保存回款分期 */
export function saveCollectionPlan(
  payload: CollectionPlanSaveInput,
): Promise<CollectionPlanView[]> {
  return http.put<CollectionPlanView[]>('/crm/v1/collection/plan', payload);
}

/** 登记承诺还款 */
export function promiseCollection(
  id: number,
  payload: CollectionPromiseInput,
): Promise<CollectionPlanView> {
  return http.post<CollectionPlanView>(`/crm/v1/collection/${id}/promise`, payload);
}

/** 追加催收记录 */
export function addCollectionAction(
  id: number,
  payload: CollectionActionInput,
): Promise<CollectionActionView> {
  return http.post<CollectionActionView>(`/crm/v1/collection/${id}/action`, payload);
}

/** 查询催收记录 */
export function listCollectionActions(id: number): Promise<CollectionActionView[]> {
  return http.get<CollectionActionView[]>(`/crm/v1/collection/${id}/actions`);
}

// ---------------------------------------------------------------------
// 商务订单（7f-2）
// ---------------------------------------------------------------------

/** 订单来源类型 */
export type OrderSourceType = 'MANUAL' | 'QUOTE' | 'CONTRACT';

/** 订单状态 */
export type OrderStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'PUSHING'
  | 'PUSHED'
  | 'PUSH_FAILED'
  | 'CANCELLED';

/** 订单行视图（SalesOrderView.LineView） */
export interface SalesOrderLineView {
  id?: number;
  lineNo?: number;
  itemCode?: string | null;
  itemName?: string | null;
  specification?: string | null;
  uom?: string | null;
  quantity?: number | string | null;
  unitPrice?: number | string | null;
  amount?: number | string | null;
  remark?: string | null;
}

/** 商务订单视图（SalesOrderView） */
export interface SalesOrderView {
  id: number;
  orderNo: string;
  sourceType?: string | null;
  sourceId?: number | null;
  sourceOrderNo?: string | null;
  customerId: number;
  customerCode?: string | null;
  title?: string | null;
  currency?: string | null;
  totalAmount?: number | string | null;
  status: OrderStatus | string;
  approvalInstanceId?: number | null;
  submittedAt?: string | null;
  approvedAt?: string | null;
  erpOrderNo?: string | null;
  erpOrderId?: number | null;
  erpStatus?: string | null;
  pushedAt?: string | null;
  pushAttempts?: number | null;
  lastPushError?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  remark?: string | null;
  lines?: SalesOrderLineView[];
}

/** 订单行入参 */
export interface SalesOrderLineInput {
  itemCode?: string;
  itemName?: string;
  specification?: string;
  uom?: string;
  quantity: number;
  unitPrice: number;
  remark?: string;
}

/** 新建订单入参：QUOTE 来源可省略行（复制报价行，物料编码待补录） */
export interface SalesOrderCreateInput {
  sourceType?: OrderSourceType;
  sourceId?: number | null;
  customerId?: number | null;
  title?: string;
  currency?: string;
  remark?: string;
  lines?: SalesOrderLineInput[];
}

/** 编辑订单入参（仅草稿） */
export interface SalesOrderUpdateInput {
  title?: string;
  currency?: string;
  remark?: string;
  lines?: SalesOrderLineInput[];
}

/** 订单分页查询 */
export interface SalesOrderPageQuery {
  keyword?: string;
  customerId?: number | null;
  status?: string;
  current?: number;
  size?: number;
}

/** 订单分页 */
export function pageOrders(query: SalesOrderPageQuery): Promise<PageResult<SalesOrderView>> {
  return http.get<PageResult<SalesOrderView>>('/crm/v1/order/page', query as Record<string, unknown>);
}

/** 订单详情 */
export function getOrder(id: number): Promise<SalesOrderView> {
  return http.get<SalesOrderView>(`/crm/v1/order/${id}`);
}

/** 新建订单 */
export function createOrder(payload: SalesOrderCreateInput): Promise<SalesOrderView> {
  return http.post<SalesOrderView>('/crm/v1/order', payload);
}

/** 编辑草稿订单 */
export function updateOrder(id: number, payload: SalesOrderUpdateInput): Promise<SalesOrderView> {
  return http.put<SalesOrderView>(`/crm/v1/order/${id}`, payload);
}

/** 提交订单审批 */
export function submitOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/crm/v1/order/${id}/submit`);
}

/** 取消订单 */
export function cancelOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/crm/v1/order/${id}/cancel`);
}

/** 重试下发 ERP */
export function retryOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/crm/v1/order/${id}/retry`);
}

/** 手动对账 */
export function reconcileOrders(): Promise<number> {
  return http.post<number>('/crm/v1/order/reconcile');
}

// ---------------------------------------------------------------------
// 发票与应收对账（7f-3）
// ---------------------------------------------------------------------

/** 开票申请类型 */
export type InvoiceRequestType = 'NORMAL' | 'RED_FLUSH';

/** 开票申请状态 */
export type InvoiceStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'ISSUED'
  | 'SENT'
  | 'RED_FLUSHED'
  | 'CANCELLED';

/** 开票申请行视图 */
export interface InvoiceItemView {
  id?: number;
  lineNo?: number;
  itemName?: string | null;
  specification?: string | null;
  quantity?: number | string | null;
  unitPrice?: number | string | null;
  taxRate?: number | string | null;
  taxAmount?: number | string | null;
  amount?: number | string | null;
  remark?: string | null;
}

/** 开票申请视图（InvoiceView） */
export interface InvoiceView {
  id: number;
  invoiceNo: string;
  requestType?: InvoiceRequestType | string | null;
  originInvoiceId?: number | null;
  sourceType?: string | null;
  sourceId?: number | null;
  sourceOrderNo?: string | null;
  contractId?: number | null;
  contractNo?: string | null;
  customerId?: number | null;
  customerCode?: string | null;
  customerName?: string | null;
  currency?: string | null;
  amount?: number | string | null;
  taxAmount?: number | string | null;
  invoiceType?: string | null;
  titleType?: string | null;
  invoiceTitle?: string | null;
  taxNo?: string | null;
  status: InvoiceStatus | string;
  approvalInstanceId?: number | null;
  submittedAt?: string | null;
  approvedAt?: string | null;
  invoiceCode?: string | null;
  invoiceNumber?: string | null;
  issuedAt?: string | null;
  issuedAmount?: number | string | null;
  courier?: string | null;
  trackingNo?: string | null;
  sentAt?: string | null;
  redFlushReason?: string | null;
  redFlushedAt?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  remark?: string | null;
  availableAmount?: number | string | null;
  items?: InvoiceItemView[];
}

/** 开票申请行入参 */
export interface InvoiceItemInput {
  itemName?: string;
  specification?: string;
  quantity: number;
  unitPrice: number;
  taxRate?: number;
  taxAmount?: number;
  remark?: string;
}

/** 新建 / 编辑开票申请入参 */
export interface InvoiceSaveInput {
  sourceType?: 'CONTRACT' | 'ORDER';
  sourceId?: number | null;
  currency?: string;
  amount: number;
  invoiceType?: string;
  titleType?: string;
  invoiceTitle?: string;
  taxNo?: string;
  remark?: string;
  items?: InvoiceItemInput[];
}

/** 登记开票入参 */
export interface InvoiceIssueInput {
  invoiceCode?: string;
  invoiceNumber?: string;
  issuedAmount: number;
  taxAmount?: number;
}

/** 登记寄送入参 */
export interface InvoiceSendInput {
  courier: string;
  trackingNo: string;
}

/** 红冲入参 */
export interface InvoiceRedFlushInput {
  amount: number;
  reason: string;
}

/** 开票申请分页查询 */
export interface InvoicePageQuery {
  keyword?: string;
  customerId?: number | null;
  contractId?: number | null;
  status?: string;
  requestType?: string;
  current?: number;
  size?: number;
}

/** 应收快照视图（ArSnapshotView） */
export interface ArSnapshotView {
  id: number;
  customerId: number;
  customerCode?: string | null;
  customerName?: string | null;
  snapshotDate?: string | null;
  currency?: string | null;
  receivableCount?: number | null;
  totalRemaining?: number | string | null;
  notDue?: number | string | null;
  overdue1To30?: number | string | null;
  overdue31To60?: number | string | null;
  overdue61To90?: number | string | null;
  overdueOver90?: number | string | null;
  source?: string | null;
  lastSyncAt?: string | null;
}

/** 应收快照分页查询 */
export interface ArSnapshotPageQuery {
  keyword?: string;
  customerId?: number | null;
  snapshotDate?: string;
  current?: number;
  size?: number;
}

/** 应收同步结果（ArSyncResultView） */
export interface ArSyncResultView {
  synced: number;
  skipped: number;
  failed: number;
  message?: string | null;
}

/** 开票申请分页 */
export function pageInvoices(query: InvoicePageQuery): Promise<PageResult<InvoiceView>> {
  return http.get<PageResult<InvoiceView>>('/crm/v1/invoice/page', query as Record<string, unknown>);
}

/** 开票申请详情 */
export function getInvoice(id: number): Promise<InvoiceView> {
  return http.get<InvoiceView>(`/crm/v1/invoice/${id}`);
}

/** 新建开票申请 */
export function createInvoice(payload: InvoiceSaveInput): Promise<InvoiceView> {
  return http.post<InvoiceView>('/crm/v1/invoice', payload);
}

/** 编辑草稿开票申请 */
export function updateInvoice(id: number, payload: InvoiceSaveInput): Promise<InvoiceView> {
  return http.put<InvoiceView>(`/crm/v1/invoice/${id}`, payload);
}

/** 提交开票审批 */
export function submitInvoice(id: number): Promise<InvoiceView> {
  return http.post<InvoiceView>(`/crm/v1/invoice/${id}/submit`);
}

/** 登记开票记录 */
export function issueInvoice(id: number, payload: InvoiceIssueInput): Promise<InvoiceView> {
  return http.post<InvoiceView>(`/crm/v1/invoice/${id}/issue`, payload);
}

/** 登记发票寄送 */
export function sendInvoice(id: number, payload: InvoiceSendInput): Promise<InvoiceView> {
  return http.post<InvoiceView>(`/crm/v1/invoice/${id}/send`, payload);
}

/** 发票红冲 */
export function redFlushInvoice(id: number, payload: InvoiceRedFlushInput): Promise<InvoiceView> {
  return http.post<InvoiceView>(`/crm/v1/invoice/${id}/red-flush`, payload);
}

/** 取消开票申请 */
export function cancelInvoice(id: number): Promise<InvoiceView> {
  return http.post<InvoiceView>(`/crm/v1/invoice/${id}/cancel`);
}

/** 应收快照分页 */
export function pageArSnapshots(query: ArSnapshotPageQuery): Promise<PageResult<ArSnapshotView>> {
  return http.get<PageResult<ArSnapshotView>>('/crm/v1/ar/page', query as Record<string, unknown>);
}

/** 手动同步应收快照 */
export function syncArSnapshots(): Promise<ArSyncResultView> {
  return http.post<ArSyncResultView>('/crm/v1/ar/sync');
}

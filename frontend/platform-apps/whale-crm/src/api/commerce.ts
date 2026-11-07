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
  return http.get<PageResult<ContractView>>('/crm/contract/page', query as Record<string, unknown>);
}

/** 合同台账详情 */
export function getContract(id: number): Promise<ContractView> {
  return http.get<ContractView>(`/crm/contract/${id}`);
}

/** 到期提醒 */
export function expiringContracts(days?: number): Promise<ContractView[]> {
  return http.get<ContractView[]>('/crm/contract/expiring', { days });
}

/** 按客户同步合同投影 */
export function syncContracts(customerId: number): Promise<number> {
  return http.post<number>('/crm/contract/sync', undefined, { customerId });
}

/** 标记合同续约 */
export function markContractRenewal(id: number, payload: ContractRenewalInput): Promise<ContractView> {
  return http.put<ContractView>(`/crm/contract/${id}/renewal`, payload);
}

/** 回款计划分页 */
export function pageCollections(query: CollectionPageQuery): Promise<PageResult<CollectionPlanView>> {
  return http.get<PageResult<CollectionPlanView>>('/crm/collection/page', query as Record<string, unknown>);
}

/** 回款计划详情 */
export function getCollection(id: number): Promise<CollectionPlanView> {
  return http.get<CollectionPlanView>(`/crm/collection/${id}`);
}

/** 按合同整批保存回款分期 */
export function saveCollectionPlan(payload: CollectionPlanSaveInput): Promise<CollectionPlanView[]> {
  return http.put<CollectionPlanView[]>('/crm/collection/plan', payload);
}

/** 登记承诺还款 */
export function promiseCollection(id: number, payload: CollectionPromiseInput): Promise<CollectionPlanView> {
  return http.post<CollectionPlanView>(`/crm/collection/${id}/promise`, payload);
}

/** 追加催收记录 */
export function addCollectionAction(id: number, payload: CollectionActionInput): Promise<CollectionActionView> {
  return http.post<CollectionActionView>(`/crm/collection/${id}/action`, payload);
}

/** 查询催收记录 */
export function listCollectionActions(id: number): Promise<CollectionActionView[]> {
  return http.get<CollectionActionView[]>(`/crm/collection/${id}/actions`);
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
  return http.get<PageResult<SalesOrderView>>('/crm/order/page', query as Record<string, unknown>);
}

/** 订单详情 */
export function getOrder(id: number): Promise<SalesOrderView> {
  return http.get<SalesOrderView>(`/crm/order/${id}`);
}

/** 新建订单 */
export function createOrder(payload: SalesOrderCreateInput): Promise<SalesOrderView> {
  return http.post<SalesOrderView>('/crm/order', payload);
}

/** 编辑草稿订单 */
export function updateOrder(id: number, payload: SalesOrderUpdateInput): Promise<SalesOrderView> {
  return http.put<SalesOrderView>(`/crm/order/${id}`, payload);
}

/** 提交订单审批 */
export function submitOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/crm/order/${id}/submit`);
}

/** 取消订单 */
export function cancelOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/crm/order/${id}/cancel`);
}

/** 重试下发 ERP */
export function retryOrder(id: number): Promise<SalesOrderView> {
  return http.post<SalesOrderView>(`/crm/order/${id}/retry`);
}

/** 手动对账 */
export function reconcileOrders(): Promise<number> {
  return http.post<number>('/crm/order/reconcile');
}

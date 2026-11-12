// 支付服务 API（经网关 → whale-pay /pay）
// 权限码：sys:pay:list/add/pay/cancel（70–73）、refund/reconcile（244–245）

import { get, post, type PageResult } from './client';

/** 支付单状态 */
export type PayOrderStatus = 'CREATED' | 'PAID' | 'CANCELLED' | 'REFUNDED';

/** 支付单（字段对齐后端 PayOrderVO） */
export interface PayOrderVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  /** 支付单号（业务唯一） */
  orderNo: string;
  /** 业务标识 */
  businessKey?: string;
  /** 运行时为 number（BigDecimal 不走 Long→String） */
  amount: number;
  currency: string;
  /** 支付渠道：ALIPAY / WECHAT / BALANCE 等 */
  channel?: string;
  status: PayOrderStatus;
  payTime?: string;
  /** 渠道流水号（mock 模式为模拟流水号） */
  channelTradeNo?: string;
  /** 运行时为 number（BigDecimal） */
  refundAmount?: number;
  refundTime?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 支付/退款流水（字段对齐后端 PayTransactionVO） */
export interface PayTransactionVO {
  id: string;
  orderId: string;
  orderNo: string;
  /** PAY / REFUND */
  type: string;
  channel?: string;
  /** 运行时为 number（BigDecimal） */
  amount: number;
  currency: string;
  providerTradeNo?: string;
  /** SUCCESS / FAILED */
  status: string;
  errorMessage?: string;
  createTime?: string;
}

/** 对账结果（字段对齐后端 PayReconcileVO） */
export interface PayReconcileVO {
  orderId: string;
  orderNo: string;
  localStatus: string;
  channelStatus: string;
  consistent: boolean;
  providerTradeNo?: string;
  checkedAt?: string;
}

/** 支付单创建入参 */
export interface PayOrderInput {
  orderNo: string;
  businessKey?: string;
  /** 运行时为 number（BigDecimal），最多 2 位小数 */
  amount: number;
  currency: string;
  channel?: string;
  remark?: string;
}

/** 支付单分页查询参数 */
export interface PayOrderPageParams {
  keyword?: string;
  status?: string;
  channel?: string;
  current: number;
  size: number;
}

/** 分页查询支付单 */
export function pagePayOrders(params: PayOrderPageParams): Promise<PageResult<PayOrderVO>> {
  return get<PageResult<PayOrderVO>>('/pay/order/page', { ...params });
}

/** 创建支付单 */
export function createPayOrder(body: PayOrderInput): Promise<PayOrderVO> {
  return post<PayOrderVO>('/pay/order', body);
}

/** 支付支付单（CREATED → PAID） */
export function payPayOrder(id: string): Promise<PayOrderVO> {
  return post<PayOrderVO>(`/pay/order/${id}/pay`);
}

/** 取消支付单（CREATED → CANCELLED） */
export function cancelPayOrder(id: string): Promise<PayOrderVO> {
  return post<PayOrderVO>(`/pay/order/${id}/cancel`);
}

/** 退款支付单（PAID → REFUNDED，当前为全额退款） */
export function refundPayOrder(id: string): Promise<PayOrderVO> {
  return post<PayOrderVO>(`/pay/order/${id}/refund`);
}

/** 支付单对账：比对本地状态与渠道状态 */
export function reconcilePayOrder(id: string): Promise<PayReconcileVO> {
  return get<PayReconcileVO>(`/pay/order/${id}/reconcile`);
}

/** 支付/退款流水列表 */
export function listPayTransactions(id: string): Promise<PayTransactionVO[]> {
  return get<PayTransactionVO[]>(`/pay/order/${id}/transactions`);
}

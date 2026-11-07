// CRM 售后服务 API 层（M4 / 7f-4a）：工单 + SLA 策略与工作日历
//
// 对应控制器：
// - CrmTicketController       /crm/ticket
// - CrmServiceConfigController /crm/service
//
// SLA 按工作时段推进（7×8 日历），逾期状态由后端只读派生；工单处理记录追加式不删除。

import { http } from './client';
import type { PageResult } from './client';

/** 工单优先级 */
export type TicketPriority = 'P1' | 'P2' | 'P3';

/** 工单状态 */
export type TicketStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'PROCESSING'
  | 'WAITING_CUSTOMER'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REOPENED'
  | 'CANCELLED';

/** 工单处理记录视图（TicketActionView） */
export interface TicketActionView {
  id: number;
  actionType: string;
  fromStatus?: string | null;
  toStatus?: string | null;
  operatorId?: number | null;
  operatorName?: string | null;
  content?: string | null;
  createdAt?: string | null;
}

/** 工单视图（TicketView） */
export interface TicketView {
  id: number;
  ticketNo: string;
  customerId: number;
  customerCode?: string | null;
  customerName?: string | null;
  contactId?: number | null;
  contactName?: string | null;
  contractId?: number | null;
  orderId?: number | null;
  erpOrderNo?: string | null;
  productCode?: string | null;
  serialNo?: string | null;
  category?: string | null;
  priority: TicketPriority | string;
  title: string;
  description?: string | null;
  status: TicketStatus | string;
  assigneeId?: number | null;
  assigneeName?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  responseDeadline?: string | null;
  resolveDeadline?: string | null;
  firstRespondedAt?: string | null;
  pausedSeconds?: number | null;
  resolvedAt?: string | null;
  closedAt?: string | null;
  solution?: string | null;
  reopenCount?: number | null;
  responseOverdue?: boolean | null;
  resolveOverdue?: boolean | null;
  createTime?: string | null;
  updateTime?: string | null;
  actions?: TicketActionView[];
}

/** 建单入参（TicketCreateCommand） */
export interface TicketCreateInput {
  customerId: number;
  contactId?: number | null;
  contactName?: string;
  contractId?: number | null;
  orderId?: number | null;
  productCode?: string;
  serialNo?: string;
  category?: string;
  priority: TicketPriority;
  title: string;
  description?: string;
  assigneeId?: number | null;
  assigneeName?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 分派入参（TicketAssignCommand） */
export interface TicketAssignInput {
  assigneeId: number;
  assigneeName?: string;
}

/** 处理动作入参（TicketHandleCommand） */
export interface TicketHandleInput {
  content?: string;
  solution?: string;
  reason?: string;
}

/** 工单分页查询 */
export interface TicketPageQuery {
  keyword?: string;
  status?: string;
  priority?: string;
  customerId?: number | null;
  assigneeId?: number | null;
  current?: number;
  size?: number;
}

/** SLA 策略视图（SlaPolicyView） */
export interface SlaPolicyView {
  priority: TicketPriority | string;
  responseHours: number;
  resolveHours: number;
  enabled: boolean;
}

/** SLA 策略保存入参 */
export interface SlaPoliciesInput {
  policies: SlaPolicyView[];
}

/** 工作日历视图（ServiceCalendarView） */
export interface ServiceCalendarView {
  calendarCode: string;
  workdayStart: string;
  workdayEnd: string;
  breakMinutes: number;
  weekdays: string;
  enabled: boolean;
}

/** 工作日历保存入参（ServiceCalendarCommand） */
export interface ServiceCalendarInput {
  calendarCode?: string;
  workdayStart: string;
  workdayEnd: string;
  breakMinutes: number;
  weekdays: string;
  enabled?: boolean;
}

/** 工单分页 */
export function pageTickets(query: TicketPageQuery): Promise<PageResult<TicketView>> {
  return http.get<PageResult<TicketView>>('/crm/ticket/page', query as Record<string, unknown>);
}

/** 工单详情（含处理时间线） */
export function getTicket(id: number): Promise<TicketView> {
  return http.get<TicketView>(`/crm/ticket/${id}`);
}

/** 新建工单 */
export function createTicket(payload: TicketCreateInput): Promise<TicketView> {
  return http.post<TicketView>('/crm/ticket', payload);
}

/** 分派处理人 */
export function assignTicket(id: number, payload: TicketAssignInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/assign`, payload);
}

/** 记录处理进展 */
export function processTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/process`, payload);
}

/** 追加备注 */
export function commentTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/comment`, payload);
}

/** 暂停工单（等待客户） */
export function pauseTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/pause`, payload);
}

/** 恢复工单 */
export function resumeTicket(id: number, payload?: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/resume`, payload);
}

/** 升级优先级 */
export function escalateTicket(id: number, payload?: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/escalate`, payload);
}

/** 标记已解决 */
export function resolveTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/resolve`, payload);
}

/** 关闭工单 */
export function closeTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/close`, payload);
}

/** 重开工单 */
export function reopenTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/reopen`, payload);
}

/** 取消工单 */
export function cancelTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/ticket/${id}/cancel`, payload);
}

/** 查询 SLA 策略 */
export function listSlaPolicies(): Promise<SlaPolicyView[]> {
  return http.get<SlaPolicyView[]>('/crm/service/sla');
}

/** 保存 SLA 策略 */
export function saveSlaPolicies(payload: SlaPoliciesInput): Promise<SlaPolicyView[]> {
  return http.put<SlaPolicyView[]>('/crm/service/sla', payload);
}

/** 查询工作日历 */
export function getServiceCalendar(): Promise<ServiceCalendarView> {
  return http.get<ServiceCalendarView>('/crm/service/calendar');
}

/** 保存工作日历 */
export function saveServiceCalendar(payload: ServiceCalendarInput): Promise<ServiceCalendarView> {
  return http.put<ServiceCalendarView>('/crm/service/calendar', payload);
}

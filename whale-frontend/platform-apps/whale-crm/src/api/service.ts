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
  return http.get<PageResult<TicketView>>('/crm/v1/ticket/page', query as Record<string, unknown>);
}

/** 工单详情（含处理时间线） */
export function getTicket(id: number): Promise<TicketView> {
  return http.get<TicketView>(`/crm/v1/ticket/${id}`);
}

/** 新建工单 */
export function createTicket(payload: TicketCreateInput): Promise<TicketView> {
  return http.post<TicketView>('/crm/v1/ticket', payload);
}

/** 分派处理人 */
export function assignTicket(id: number, payload: TicketAssignInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/assign`, payload);
}

/** 记录处理进展 */
export function processTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/process`, payload);
}

/** 追加备注 */
export function commentTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/comment`, payload);
}

/** 暂停工单（等待客户） */
export function pauseTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/pause`, payload);
}

/** 恢复工单 */
export function resumeTicket(id: number, payload?: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/resume`, payload);
}

/** 升级优先级 */
export function escalateTicket(id: number, payload?: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/escalate`, payload);
}

/** 标记已解决 */
export function resolveTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/resolve`, payload);
}

/** 关闭工单 */
export function closeTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/close`, payload);
}

/** 重开工单 */
export function reopenTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/reopen`, payload);
}

/** 取消工单 */
export function cancelTicket(id: number, payload: TicketHandleInput): Promise<TicketView> {
  return http.post<TicketView>(`/crm/v1/ticket/${id}/cancel`, payload);
}

/** 查询 SLA 策略 */
export function listSlaPolicies(): Promise<SlaPolicyView[]> {
  return http.get<SlaPolicyView[]>('/crm/v1/service/sla');
}

/** 保存 SLA 策略 */
export function saveSlaPolicies(payload: SlaPoliciesInput): Promise<SlaPolicyView[]> {
  return http.put<SlaPolicyView[]>('/crm/v1/service/sla', payload);
}

/** 查询工作日历 */
export function getServiceCalendar(): Promise<ServiceCalendarView> {
  return http.get<ServiceCalendarView>('/crm/v1/service/calendar');
}

/** 保存工作日历 */
export function saveServiceCalendar(payload: ServiceCalendarInput): Promise<ServiceCalendarView> {
  return http.put<ServiceCalendarView>('/crm/v1/service/calendar', payload);
}

// ---------------------------------------------------------------------------
// 知识引用（M4 / 7f-4b）：正文归 whale-doc，CRM 只维护元数据 / 版本引用 / 命中
// ---------------------------------------------------------------------------

/** 知识引用视图（KbRefView） */
export interface KbRefView {
  id: number;
  title: string;
  category?: string | null;
  productTags?: string | null;
  faultTags?: string | null;
  visibleScope?: string | null;
  docId?: number | null;
  docVersion?: number | null;
  docAvailable?: boolean | null;
  hitCount?: number | null;
  status?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 知识引用维护入参（KbRefCommand） */
export interface KbRefInput {
  title: string;
  category?: string;
  productTags?: string;
  faultTags?: string;
  visibleScope?: string;
  docId?: number | null;
  docVersion?: number | null;
  status?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 知识引用分页查询 */
export interface KbPageQuery {
  keyword?: string;
  category?: string;
  visibleScope?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 分页查询知识引用 */
export function pageKbRefs(query: KbPageQuery): Promise<PageResult<KbRefView>> {
  return http.get<PageResult<KbRefView>>('/crm/v1/kb/page', query as Record<string, unknown>);
}

/** 按标题 / 标签检索启用中的知识引用 */
export function searchKbRefs(keyword?: string): Promise<KbRefView[]> {
  return http.get<KbRefView[]>('/crm/v1/kb/search', { keyword });
}

/** 知识引用详情 */
export function getKbRef(id: number): Promise<KbRefView> {
  return http.get<KbRefView>(`/crm/v1/kb/${id}`);
}

/** 新建知识引用 */
export function createKbRef(payload: KbRefInput): Promise<KbRefView> {
  return http.post<KbRefView>('/crm/v1/kb', payload);
}

/** 更新知识引用 */
export function updateKbRef(id: number, payload: KbRefInput): Promise<KbRefView> {
  return http.put<KbRefView>(`/crm/v1/kb/${id}`, payload);
}

/** 逻辑删除知识引用 */
export function deleteKbRef(id: number): Promise<void> {
  return http.del<void>(`/crm/v1/kb/${id}`);
}

/** 记录一次引用命中 */
export function hitKbRef(id: number): Promise<KbRefView> {
  return http.post<KbRefView>(`/crm/v1/kb/${id}/hit`);
}

// ---------------------------------------------------------------------------
// 满意度问卷（M4 / 7f-4b）：工单关闭后生成一次性令牌，答卷提交后置 USED
// ---------------------------------------------------------------------------

/** 满意度答卷视图（SatisfactionResponseView） */
export interface SatisfactionResponseView {
  score?: number | null;
  comment?: string | null;
  speedScore?: number | null;
  attitudeScore?: number | null;
  professionalScore?: number | null;
  lowScore?: boolean | null;
  followUpRequired?: boolean | null;
  submittedAt?: string | null;
}

/** 满意度问卷视图（SatisfactionSurveyView） */
export interface SatisfactionSurveyView {
  id: number;
  surveyNo: string;
  ticketId: number;
  status: string;
  sentAt?: string | null;
  expiresAt?: string | null;
  usedAt?: string | null;
  token?: string | null;
  response?: SatisfactionResponseView | null;
}

/** 生成 / 重发问卷入参 */
export interface SatisfactionSurveyInput {
  ticketId: number;
}

/** 令牌答卷入参（SurveyResponseCommand） */
export interface SurveyResponseInput {
  token: string;
  score: number;
  comment?: string;
  speedScore?: number | null;
  attitudeScore?: number | null;
  professionalScore?: number | null;
}

/** 满意度问卷分页查询 */
export interface SurveyPageQuery {
  ticketId?: number | null;
  status?: string;
  current?: number;
  size?: number;
}

/** 满意度统计视图（SurveyStatsView） */
export interface SurveyStatsView {
  sentCount?: number | null;
  usedCount?: number | null;
  averageScore?: number | null;
  lowScoreCount?: number | null;
  followUpCount?: number | null;
}

/** 分页查询问卷 */
export function pageSurveys(query: SurveyPageQuery): Promise<PageResult<SatisfactionSurveyView>> {
  return http.get<PageResult<SatisfactionSurveyView>>(
    '/crm/v1/satisfaction/page',
    query as Record<string, unknown>,
  );
}

/** 满意度统计 */
export function getSurveyStats(): Promise<SurveyStatsView> {
  return http.get<SurveyStatsView>('/crm/v1/satisfaction/stats');
}

/** 问卷详情（含答卷） */
export function getSurvey(id: number): Promise<SatisfactionSurveyView> {
  return http.get<SatisfactionSurveyView>(`/crm/v1/satisfaction/${id}`);
}

/** 按工单生成或重发问卷 */
export function sendSurvey(payload: SatisfactionSurveyInput): Promise<SatisfactionSurveyView> {
  return http.post<SatisfactionSurveyView>('/crm/v1/satisfaction/send', payload);
}

/** 按一次性令牌提交答卷 */
export function submitSurveyResponse(
  payload: SurveyResponseInput,
): Promise<SatisfactionSurveyView> {
  return http.post<SatisfactionSurveyView>('/crm/v1/satisfaction/response', payload);
}

/** 作废问卷 */
export function revokeSurvey(id: number): Promise<void> {
  return http.post<void>(`/crm/v1/satisfaction/${id}/revoke`);
}

// ---------------------------------------------------------------------------
// 退换货申请（M4 / 7f-4c）：CRM 管申请与审批，库存 / 应收 / 凭证均由 ERP 退货过账完成
// ---------------------------------------------------------------------------

/** 退换货申请状态 */
export type ReturnStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXECUTING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

/** 退换货申请行视图（ReturnRequestView.Item） */
export interface ReturnItemView {
  lineNo?: number | null;
  orderLineNo?: number | null;
  itemCode?: string | null;
  itemName?: string | null;
  quantity?: number | string | null;
  unitPrice?: number | string | null;
  amount?: number | string | null;
}

/** 退换货申请视图（ReturnRequestView） */
export interface ReturnRequestView {
  id: number;
  returnNo: string;
  requestType?: string | null;
  orderId?: number | null;
  erpOrderNo?: string | null;
  customerId?: number | null;
  customerCode?: string | null;
  customerName?: string | null;
  reason?: string | null;
  status: ReturnStatus | string;
  approvalInstanceId?: number | null;
  erpReturnNo?: string | null;
  erpStatus?: string | null;
  pushAttempts?: number | null;
  lastError?: string | null;
  totalAmount?: number | string | null;
  ownerId?: number | null;
  deptId?: number | null;
  items?: ReturnItemView[];
}

/** 退换货申请行入参 */
export interface ReturnLineInput {
  orderLineNo: number;
  quantity: number;
}

/** 新建 / 编辑退换货申请入参（ReturnRequestCommand） */
export interface ReturnRequestInput {
  requestType?: string;
  orderId: number;
  reason?: string;
  lines: ReturnLineInput[];
}

/** 退换货申请分页查询 */
export interface ReturnPageQuery {
  keyword?: string;
  status?: string;
  customerId?: number | null;
  orderId?: number | null;
  current?: number;
  size?: number;
}

/** 退换货申请分页 */
export function pageReturns(query: ReturnPageQuery): Promise<PageResult<ReturnRequestView>> {
  return http.get<PageResult<ReturnRequestView>>(
    '/crm/v1/return/page',
    query as Record<string, unknown>,
  );
}

/** 退换货申请详情 */
export function getReturn(id: number): Promise<ReturnRequestView> {
  return http.get<ReturnRequestView>(`/crm/v1/return/${id}`);
}

/** 新建退换货申请 */
export function createReturn(payload: ReturnRequestInput): Promise<ReturnRequestView> {
  return http.post<ReturnRequestView>('/crm/v1/return', payload);
}

/** 编辑草稿退换货申请 */
export function updateReturn(id: number, payload: ReturnRequestInput): Promise<ReturnRequestView> {
  return http.put<ReturnRequestView>(`/crm/v1/return/${id}`, payload);
}

/** 提交退换货审批 */
export function submitReturn(id: number): Promise<ReturnRequestView> {
  return http.post<ReturnRequestView>(`/crm/v1/return/${id}/submit`);
}

/** 重试下发 ERP 退货 */
export function retryReturn(id: number): Promise<ReturnRequestView> {
  return http.post<ReturnRequestView>(`/crm/v1/return/${id}/retry`);
}

/** 取消退换货申请 */
export function cancelReturn(id: number): Promise<ReturnRequestView> {
  return http.post<ReturnRequestView>(`/crm/v1/return/${id}/cancel`);
}

/** 对账单张申请的 ERP 退货状态 */
export function reconcileReturn(id: number): Promise<ReturnRequestView> {
  return http.post<ReturnRequestView>(`/crm/v1/return/${id}/reconcile`);
}

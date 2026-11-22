// PDM 变更单（ECO）API 层：经网关 8080 代理到 whale-pdm（/pdm/change/order/**）
import { http, type PageResult } from './client';
import type { ChgImpactObject, ChgObjectType } from './change';

/** 变更单状态 */
export type ChgOrderStatus =
  | 'DRAFT'
  | 'ASSESSING'
  | 'APPROVING'
  | 'APPROVED'
  | 'EXECUTING'
  | 'COMPLETED'
  | 'CLOSED'
  | 'REJECTED'
  | 'CANCELLED';

/** 变更任务状态 */
export type ChgTaskStatus = 'PENDING' | 'COMPLETED';

/** 变更任务 */
export interface ChgOrderTask {
  id: string;
  ecoId: string;
  taskName: string;
  taskType: string;
  targetType?: ChgObjectType;
  targetId?: number;
  targetCode?: string;
  planDesc?: string;
  ownerId?: number;
  status: ChgTaskStatus;
  resultVersionNo?: string;
  sortNo?: number;
  completeTime?: string;
}

/** 变更单 */
export interface ChgOrder {
  id: string;
  ecoNo: string;
  ecrId: string;
  title: string;
  impactAssessment?: string;
  impactObjects: ChgImpactObject[];
  executePlan?: string;
  status: ChgOrderStatus;
  initiatorId?: number;
  submitTime?: string;
  approveBy?: number;
  approveTime?: string;
  approveComment?: string;
  executeTime?: string;
  completeTime?: string;
  closeTime?: string;
  createTime?: string;
  tasks: ChgOrderTask[];
}

/** 分页查询条件 */
export interface ChgOrderQuery {
  current?: number;
  size?: number;
  ecoNo?: string;
  status?: ChgOrderStatus;
  ecrId?: string;
  initiatorId?: number;
}

/** 新建/编辑入参 */
export interface ChgOrderInput {
  ecrId?: string | number;
  title: string;
  impactAssessment?: string;
  executePlan?: string;
}

/** 影响评估入参 */
export interface ChgOrderAssessInput {
  impactAssessment: string;
  executePlan?: string;
}

/** 变更任务入参 */
export interface ChgTaskInput {
  taskName: string;
  taskType: string;
  targetType?: ChgObjectType;
  targetId?: number;
  targetCode?: string;
  planDesc?: string;
  ownerId?: number;
  sortNo?: number;
}

/** 分页查询变更单 */
export function pageChgOrders(query: ChgOrderQuery): Promise<PageResult<ChgOrder>> {
  return http.get<PageResult<ChgOrder>>(
    '/pdm/v1/change/order/page',
    query as Record<string, unknown>,
  );
}

/** 变更单详情 */
export function getChgOrder(id: string): Promise<ChgOrder> {
  return http.get<ChgOrder>(`/pdm/v1/change/order/${id}`);
}

/** 从已通过 ECR 新建变更单 */
export function createChgOrder(input: ChgOrderInput): Promise<ChgOrder> {
  return http.post<ChgOrder>('/pdm/v1/change/order', input);
}

/** 修改草稿/评估中变更单 */
export function updateChgOrder(id: string, input: ChgOrderInput): Promise<ChgOrder> {
  return http.put<ChgOrder>(`/pdm/v1/change/order/${id}`, input);
}

/** 删除草稿变更单 */
export function deleteChgOrder(id: string): Promise<void> {
  return http.del<void>(`/pdm/v1/change/order/${id}`);
}

/** 影响评估 */
export function assessChgOrder(id: string, input: ChgOrderAssessInput): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/assess`, input);
}

/** 提交审批 */
export function submitChgOrder(id: string): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/submit`);
}

/** 审批通过 */
export function approveChgOrder(id: string, comment?: string): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/approve`, { comment });
}

/** 审批驳回（意见必填） */
export function rejectChgOrder(id: string, comment: string): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/reject`, { comment });
}

/** 执行变更单 */
export function executeChgOrder(id: string): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/execute`);
}

/** 完成任务并登记结果版本号 */
export function completeChgTask(
  id: string,
  taskId: string,
  resultVersionNo: string,
): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/tasks/${taskId}/complete`, {
    resultVersionNo,
  });
}

/** 完成变更单 */
export function completeChgOrder(id: string): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/complete`);
}

/** 取消变更单 */
export function cancelChgOrder(id: string): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/cancel`);
}

/** 关闭变更单 */
export function closeChgOrder(id: string): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/close`);
}

/** 新增变更任务 */
export function addChgTask(id: string, input: ChgTaskInput): Promise<ChgOrder> {
  return http.post<ChgOrder>(`/pdm/v1/change/order/${id}/tasks`, input);
}

/** 修改变更任务 */
export function updateChgTask(id: string, taskId: string, input: ChgTaskInput): Promise<ChgOrder> {
  return http.put<ChgOrder>(`/pdm/v1/change/order/${id}/tasks/${taskId}`, input);
}

/** 删除变更任务 */
export function deleteChgTask(id: string, taskId: string): Promise<ChgOrder> {
  return http.del<ChgOrder>(`/pdm/v1/change/order/${id}/tasks/${taskId}`);
}

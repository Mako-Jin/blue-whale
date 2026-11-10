// PDM 变更申请（ECR）API 层：经网关 8080 代理到 whale-pdm（/pdm/change/request/**）
import { http, type PageResult } from './client';

/** 变更申请状态 */
export type ChgRequestStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CLOSED';

/** 影响对象类型 */
export type ChgObjectType = 'DOCUMENT' | 'BOM' | 'PART';

/** 变更影响对象 */
export interface ChgImpactObject {
  objectType: ChgObjectType;
  objectId?: number;
  objectCode?: string;
  objectName?: string;
}

/** 变更申请 */
export interface ChgRequest {
  id: string;
  changeNo: string;
  changeReason: string;
  changeScope?: string;
  impactObjects: ChgImpactObject[];
  status: ChgRequestStatus;
  initiatorId?: number;
  submitTime?: string;
  approveBy?: number;
  approveTime?: string;
  approveComment?: string;
  closeTime?: string;
  createTime?: string;
}

/** 分页查询条件 */
export interface ChgRequestQuery {
  current?: number;
  size?: number;
  changeNo?: string;
  status?: ChgRequestStatus;
  initiatorId?: number;
}

/** 新建/编辑入参 */
export interface ChgRequestInput {
  changeReason: string;
  changeScope?: string;
  impactObjects?: ChgImpactObject[];
}

/** 分页查询变更申请 */
export function pageChgRequests(query: ChgRequestQuery): Promise<PageResult<ChgRequest>> {
  return http.get<PageResult<ChgRequest>>(
    '/pdm/change/request/page',
    query as Record<string, unknown>,
  );
}

/** 变更申请详情 */
export function getChgRequest(id: string): Promise<ChgRequest> {
  return http.get<ChgRequest>(`/pdm/change/request/${id}`);
}

/** 新建变更申请 */
export function createChgRequest(input: ChgRequestInput): Promise<ChgRequest> {
  return http.post<ChgRequest>('/pdm/change/request', input);
}

/** 修改草稿变更申请 */
export function updateChgRequest(id: string, input: ChgRequestInput): Promise<ChgRequest> {
  return http.put<ChgRequest>(`/pdm/change/request/${id}`, input);
}

/** 删除草稿变更申请 */
export function deleteChgRequest(id: string): Promise<void> {
  return http.del<void>(`/pdm/change/request/${id}`);
}

/** 提交变更申请 */
export function submitChgRequest(id: string): Promise<ChgRequest> {
  return http.post<ChgRequest>(`/pdm/change/request/${id}/submit`);
}

/** 审批通过 */
export function approveChgRequest(id: string, comment?: string): Promise<ChgRequest> {
  return http.post<ChgRequest>(`/pdm/change/request/${id}/approve`, { comment });
}

/** 审批驳回（意见必填） */
export function rejectChgRequest(id: string, comment: string): Promise<ChgRequest> {
  return http.post<ChgRequest>(`/pdm/change/request/${id}/reject`, { comment });
}

/** 关闭变更申请 */
export function closeChgRequest(id: string): Promise<ChgRequest> {
  return http.post<ChgRequest>(`/pdm/change/request/${id}/close`);
}

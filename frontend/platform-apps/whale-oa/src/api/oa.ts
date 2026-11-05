// OA 请假 API 层：对齐 whale-oa 的 6 个端点（page/detail/create/update/updateStatus/delete）
//
// 后端字段：id 为雪花 Long（JSON number）；DATE 序列化为 YYYY-MM-DD；状态为枚举字符串。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 请假状态 */
export type OaLeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** 请假类型 */
export type OaLeaveType = 'ANNUAL' | 'SICK' | 'PERSONAL';

/** 请假时段 */
export type OaLeavePeriod = 'AM' | 'PM';

/** 请假申请（字段对齐 backend OaLeaveVO） */
export interface OaLeave {
  id: number;
  code: string;
  applicant: string;
  applicantId?: number;
  leaveType: OaLeaveType;
  startDate: string;
  startPeriod?: OaLeavePeriod;
  endDate: string;
  endPeriod?: OaLeavePeriod;
  handoverUserId?: number;
  approvalInstanceId?: number;
  days: number;
  reason?: string;
  status: OaLeaveStatus;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 新增 / 编辑入参（字段对齐 backend OaLeaveSaveRequest） */
export interface OaLeaveInput {
  code: string;
  applicant: string;
  applicantId?: number;
  leaveType: OaLeaveType;
  startDate: string;
  startPeriod?: OaLeavePeriod;
  endDate: string;
  endPeriod?: OaLeavePeriod;
  handoverUserId?: number;
  days: number;
  reason?: string;
  remark?: string;
}

/** 分页查询入参 */
export interface OaLeaveQuery {
  current?: number;
  size?: number;
  code?: string;
  status?: OaLeaveStatus | '';
  applicant?: string;
  leaveType?: OaLeaveType | '';
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: OaLeaveQuery): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询请假申请 */
export function pageLeaves(params: OaLeaveQuery = {}): Promise<PageResult<OaLeave>> {
  return http.get<PageResult<OaLeave>>(`/oa/leave/page${buildQuery(params)}`);
}

/** 请假申请详情 */
export function getLeave(id: number): Promise<OaLeave> {
  return http.get<OaLeave>(`/oa/leave/${id}`);
}

/** 新增请假申请 */
export function createLeave(input: OaLeaveInput): Promise<OaLeave> {
  return http.post<OaLeave>('/oa/leave', input);
}

/** 更新请假申请 */
export function updateLeave(id: number, input: OaLeaveInput): Promise<OaLeave> {
  return http.put<OaLeave>(`/oa/leave/${id}`, input);
}

/** 审批 / 撤销请假申请 */
export function updateLeaveStatus(id: number, status: OaLeaveStatus): Promise<void> {
  return http.put<void>(`/oa/leave/${id}/status`, { status });
}

/** 删除请假申请（逻辑删除） */
export function deleteLeave(id: number): Promise<void> {
  return http.del<void>(`/oa/leave/${id}`);
}

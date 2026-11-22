// OA 加班 API 层：对齐 whale-oa 的 6 个端点（page/detail/create/update/updateStatus/delete）
//
// 后端字段：id 为雪花 Long（JSON number）；DATE 序列化为 YYYY-MM-DD；
// TIME 序列化为 HH:mm:ss，页面展示时截取前 5 位。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 加班状态 */
export type OaOvertimeStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** 补偿方式 */
export type OaOvertimeCompensation = 'COMP_TIME' | 'PAY';

/** 加班申请（字段对齐 backend OvertimeView） */
export interface OaOvertime {
  id: number;
  code: string;
  applicant: string;
  applicantId?: number;
  overtimeDate: string;
  startTime: string;
  endTime: string;
  hours: number;
  compensation: OaOvertimeCompensation;
  projectName?: string;
  reason?: string;
  status: OaOvertimeStatus;
  approvalInstanceId?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 新增 / 编辑入参（字段对齐 backend OvertimeSaveCommand） */
export interface OaOvertimeInput {
  code: string;
  applicant: string;
  applicantId?: number;
  overtimeDate: string;
  startTime: string;
  endTime: string;
  compensation: OaOvertimeCompensation;
  projectName?: string;
  reason?: string;
  remark?: string;
}

/** 分页查询入参 */
export interface OaOvertimeQuery {
  current?: number;
  size?: number;
  code?: string;
  status?: OaOvertimeStatus | '';
  applicant?: string;
  compensation?: OaOvertimeCompensation | '';
  dateFrom?: string;
  dateTo?: string;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: OaOvertimeQuery): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询加班申请 */
export function pageOvertimes(params: OaOvertimeQuery = {}): Promise<PageResult<OaOvertime>> {
  return http.get<PageResult<OaOvertime>>(`/oa/v1/overtime/page${buildQuery(params)}`);
}

/** 加班申请详情 */
export function getOvertime(id: number): Promise<OaOvertime> {
  return http.get<OaOvertime>(`/oa/v1/overtime/${id}`);
}

/** 新增加班申请 */
export function createOvertime(input: OaOvertimeInput): Promise<OaOvertime> {
  return http.post<OaOvertime>('/oa/v1/overtime', input);
}

/** 更新加班申请 */
export function updateOvertime(id: number, input: OaOvertimeInput): Promise<OaOvertime> {
  return http.put<OaOvertime>(`/oa/v1/overtime/${id}`, input);
}

/** 审批 / 撤销加班申请 */
export function updateOvertimeStatus(id: number, status: OaOvertimeStatus): Promise<void> {
  return http.put<void>(`/oa/v1/overtime/${id}/status`, { status });
}

/** 删除加班申请（逻辑删除） */
export function deleteOvertime(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/overtime/${id}`);
}

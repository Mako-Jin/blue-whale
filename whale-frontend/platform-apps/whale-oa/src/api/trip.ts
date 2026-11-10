// OA 出差 API 层：page/detail/create/update/updateStatus/submitReport/delete
//
// 后端字段：id 为雪花 Long（JSON number）；DATE 序列化为 YYYY-MM-DD；
// 金额为 number；天数由服务端按自然日计算。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 出差状态 */
export type OaTripStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** 交通方式 */
export type OaTripTransport = 'TRAIN' | 'PLANE' | 'CAR' | 'OTHER';

/** 报告状态 */
export type OaTripReportStatus = 'PENDING' | 'SUBMITTED';

/** 出差行程 */
export interface OaTripItinerary {
  dayNo: number;
  tripDate: string;
  fromPlace: string;
  toPlace: string;
  transport?: OaTripTransport;
  remark?: string;
}

/** 出差申请（字段对齐 backend TripView） */
export interface OaTrip {
  id: number;
  code: string;
  applicant: string;
  applicantId?: number;
  destination: string;
  startDate: string;
  endDate: string;
  days: number;
  transportType: OaTripTransport;
  budgetAmount: number;
  advanceAmount: number;
  reportStatus: OaTripReportStatus;
  reportContent?: string;
  reportTime?: string;
  status: OaTripStatus;
  approvalInstanceId?: number;
  remark?: string;
  itineraries?: OaTripItinerary[];
  advanceId?: number;
  advanceStatus?: string;
  createTime?: string;
  updateTime?: string;
}

/** 新增 / 编辑入参（字段对齐 backend TripSaveCommand） */
export interface OaTripInput {
  code: string;
  applicant: string;
  applicantId?: number;
  destination: string;
  startDate: string;
  endDate: string;
  transportType: OaTripTransport;
  budgetAmount?: number;
  advanceAmount?: number;
  itineraries?: OaTripItinerary[];
  remark?: string;
}

/** 分页查询入参 */
export interface OaTripQuery {
  current?: number;
  size?: number;
  code?: string;
  status?: OaTripStatus | '';
  applicant?: string;
  destination?: string;
  reportStatus?: OaTripReportStatus | '';
  dateFrom?: string;
  dateTo?: string;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: OaTripQuery): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询出差申请 */
export function pageTrips(params: OaTripQuery = {}): Promise<PageResult<OaTrip>> {
  return http.get<PageResult<OaTrip>>(`/oa/trip/page${buildQuery(params)}`);
}

/** 出差申请详情 */
export function getTrip(id: number): Promise<OaTrip> {
  return http.get<OaTrip>(`/oa/trip/${id}`);
}

/** 新增出差申请 */
export function createTrip(input: OaTripInput): Promise<OaTrip> {
  return http.post<OaTrip>('/oa/trip', input);
}

/** 更新出差申请 */
export function updateTrip(id: number, input: OaTripInput): Promise<OaTrip> {
  return http.put<OaTrip>(`/oa/trip/${id}`, input);
}

/** 审批 / 撤销出差申请 */
export function updateTripStatus(id: number, status: OaTripStatus): Promise<void> {
  return http.put<void>(`/oa/trip/${id}/status`, { status });
}

/** 提交出差报告 */
export function submitTripReport(id: number, content: string): Promise<void> {
  return http.post<void>(`/oa/trip/${id}/report`, { content });
}

/** 删除出差申请（逻辑删除） */
export function deleteTrip(id: number): Promise<void> {
  return http.del<void>(`/oa/trip/${id}`);
}

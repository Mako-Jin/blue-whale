// PDM 变更追溯 API 层：经网关代理到 whale-pdm（/pdm/change/trace/**）
import { http, type PageResult } from './client';

/** 追溯对象类型 */
export type ChgTraceObjectType = 'PART' | 'BOM' | 'DOCUMENT';

/** 变更追溯记录 */
export interface ChgTrace {
  id: string;
  bizType: string;
  bizId: string;
  bizNo?: string;
  objectType?: ChgTraceObjectType;
  objectId?: number;
  objectCode?: string;
  action: string;
  fromVersion?: string;
  toVersion?: string;
  content?: string;
  operatorId?: number;
  traceTime: string;
}

/** 查询条件 */
export interface ChgTraceQuery {
  current?: number;
  size?: number;
  bizType?: string;
  bizId?: string;
  objectType?: ChgTraceObjectType;
  objectId?: number;
  objectCode?: string;
  fromTime?: string;
  toTime?: string;
}

/** 分页查询追溯 */
export function pageChgTraces(query: ChgTraceQuery): Promise<PageResult<ChgTrace>> {
  return http.get<PageResult<ChgTrace>>('/pdm/v1/change/trace/page', query as Record<string, unknown>);
}

/** 按对象查询时间线 */
export function getChgTraceTimeline(
  objectType: ChgTraceObjectType,
  objectId: number,
): Promise<ChgTrace[]> {
  return http.get<ChgTrace[]>('/pdm/v1/change/trace/timeline', { objectType, objectId });
}

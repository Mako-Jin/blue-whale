// OA 协同 - 日程 API 层：个人/共享/公开日程、共享名单与提醒查询
//
// 后端时间字段序列化为 ISO 字符串；日期区间查询参数为 YYYY-MM-DD。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 日程可见性 */
export type OaCalendarVisibility = 'PRIVATE' | 'SHARED' | 'PUBLIC';

/** 日程来源 */
export type OaCalendarSource = 'PERSONAL' | 'MEETING';

/** 日程 */
export interface OaCalendarEntry {
  id: number;
  ownerId: number;
  ownerName?: string;
  title: string;
  description?: string;
  location?: string;
  startTime: string;
  endTime: string;
  allDay?: number;
  visibility: OaCalendarVisibility;
  remindMinutes?: number;
  source: OaCalendarSource;
  sourceId?: number;
  createTime?: string;
}

/** 共享用户 */
export interface OaCalendarShare {
  userId: number;
  userName?: string;
}

/** 日程详情 */
export interface OaCalendarDetail extends OaCalendarEntry {
  editable: boolean;
  shares?: OaCalendarShare[];
}

/** 日程查询 */
export interface OaCalendarQuery {
  current?: number;
  size?: number;
  keyword?: string;
  visibility?: OaCalendarVisibility | '';
  dateFrom?: string;
  dateTo?: string;
}

/** 日程入参 */
export interface OaCalendarInput {
  title: string;
  description?: string;
  location?: string;
  startTime: string;
  endTime: string;
  allDay?: number;
  visibility?: OaCalendarVisibility;
  remindMinutes?: number;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询当前用户可见日程 */
export function pageCalendars(params: OaCalendarQuery = {}): Promise<PageResult<OaCalendarEntry>> {
  return http.get<PageResult<OaCalendarEntry>>(`/oa/v1/calendar/page${buildQuery(params)}`);
}

/** 日程详情 */
export function getCalendar(id: number): Promise<OaCalendarDetail> {
  return http.get<OaCalendarDetail>(`/oa/v1/calendar/${id}`);
}

/** 新建日程 */
export function createCalendar(input: OaCalendarInput): Promise<OaCalendarEntry> {
  return http.post<OaCalendarEntry>('/oa/v1/calendar', input);
}

/** 编辑日程 */
export function updateCalendar(id: number, input: OaCalendarInput): Promise<OaCalendarEntry> {
  return http.put<OaCalendarEntry>(`/oa/v1/calendar/${id}`, input);
}

/** 删除日程 */
export function deleteCalendar(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/calendar/${id}`);
}

/** 替换日程共享名单 */
export function replaceCalendarShares(
  id: number,
  users: OaCalendarShare[],
): Promise<OaCalendarShare[]> {
  return http.put<OaCalendarShare[]>(`/oa/v1/calendar/${id}/shares`, { users });
}

/** 查询当前用户即将提醒的日程 */
export function listCalendarReminders(minutes = 30): Promise<OaCalendarEntry[]> {
  return http.get<OaCalendarEntry[]>(`/oa/v1/calendar/reminders${buildQuery({ minutes })}`);
}

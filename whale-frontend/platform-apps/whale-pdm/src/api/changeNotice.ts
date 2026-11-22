// PDM 变更通知（ECN）API 层：经网关代理到 whale-pdm（/pdm/change/notice/**）
import { http, type PageResult } from './client';
import type { ChgImpactObject } from './change';

/** 变更通知状态 */
export type ChgNoticeStatus = 'DRAFT' | 'NOTIFIED' | 'EFFECTIVE' | 'CLOSED' | 'CANCELLED';

/** 变更通知 */
export interface ChgNotice {
  id: string;
  noticeNo: string;
  ecoId: string;
  ecoNo?: string;
  title: string;
  content?: string;
  impactObjects: ChgImpactObject[];
  notifyTargets: number[];
  notifyChannels: string[];
  effectiveFrom: string;
  status: ChgNoticeStatus;
  notifiedTime?: string;
  effectiveTime?: string;
  closeTime?: string;
  createTime?: string;
}

/** 分页查询条件 */
export interface ChgNoticeQuery {
  current?: number;
  size?: number;
  noticeNo?: string;
  status?: ChgNoticeStatus;
  ecoId?: string;
}

/** 新建/编辑入参 */
export interface ChgNoticeInput {
  ecoId: string | number;
  title: string;
  content?: string;
  notifyTargets?: number[];
  notifyChannels?: string[];
  effectiveFrom: string;
}

/** 投递日志 */
export interface ChgNotifyLog {
  id: string;
  noticeId: string;
  eventType: string;
  title?: string;
  status: 'SUCCESS' | 'FAILED';
  retryCount?: number;
  lastError?: string;
  createTime?: string;
}

/** 分页查询变更通知 */
export function pageChgNotices(query: ChgNoticeQuery): Promise<PageResult<ChgNotice>> {
  return http.get<PageResult<ChgNotice>>(
    '/pdm/v1/change/notice/page',
    query as Record<string, unknown>,
  );
}

/** 变更通知详情 */
export function getChgNotice(id: string): Promise<ChgNotice> {
  return http.get<ChgNotice>(`/pdm/v1/change/notice/${id}`);
}

/** 创建变更通知 */
export function createChgNotice(input: ChgNoticeInput): Promise<ChgNotice> {
  return http.post<ChgNotice>('/pdm/v1/change/notice', input);
}

/** 修改草稿变更通知 */
export function updateChgNotice(id: string, input: ChgNoticeInput): Promise<ChgNotice> {
  return http.put<ChgNotice>(`/pdm/v1/change/notice/${id}`, input);
}

/** 删除草稿变更通知 */
export function deleteChgNotice(id: string): Promise<void> {
  return http.del<void>(`/pdm/v1/change/notice/${id}`);
}

/** 发送通知 */
export function notifyChgNotice(id: string): Promise<ChgNotice> {
  return http.post<ChgNotice>(`/pdm/v1/change/notice/${id}/notify`);
}

/** 生效 */
export function effectiveChgNotice(id: string): Promise<ChgNotice> {
  return http.post<ChgNotice>(`/pdm/v1/change/notice/${id}/effective`);
}

/** 关闭 */
export function closeChgNotice(id: string): Promise<ChgNotice> {
  return http.post<ChgNotice>(`/pdm/v1/change/notice/${id}/close`);
}

/** 取消 */
export function cancelChgNotice(id: string): Promise<ChgNotice> {
  return http.post<ChgNotice>(`/pdm/v1/change/notice/${id}/cancel`);
}

/** 重试失败投递 */
export function retryChgNotice(id: string): Promise<ChgNotice> {
  return http.post<ChgNotice>(`/pdm/v1/change/notice/${id}/retry-notify`);
}

/** 通知投递日志 */
export function pageChgNotifyLogs(id: string): Promise<PageResult<ChgNotifyLog>> {
  return http.get<PageResult<ChgNotifyLog>>(`/pdm/v1/change/notice/${id}/notify-logs`, { size: 100 });
}

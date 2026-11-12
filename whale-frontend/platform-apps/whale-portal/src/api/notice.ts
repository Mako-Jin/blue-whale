// notice.ts —— 消息通知数据源
//
// 优先告警记录（GET /alert/record/page，需 sys:alert:record:list），
// 其次 IM 会话（GET /im/conversations，需 sys:im:chat）；两者均无权限时由页面展示空态。

import { http } from './client';

/** 告警记录（字段对齐 backend AlertRecordVO） */
export interface AlertRecord {
  id: number;
  ruleId?: number;
  ruleCode?: string;
  alertLevel?: string;
  content?: string;
  status?: string;
  triggerTime?: string;
  recoverTime?: string;
  createTime?: string;
}

/** 后端分页结果信封 */
interface PageResult<T> {
  records: T[];
  /** 后端 PageResult.total 为字符串（全局 Long→String 口径） */
  total: string;
}

/** IM 会话中的最后一条消息摘要（字段对齐 backend MessageVO） */
export interface ConversationMessage {
  id?: string;
  content?: string;
  createTime?: string;
}

/** IM 会话（字段对齐 backend ConversationVO） */
export interface Conversation {
  id: string;
  type?: string;
  title?: string;
  unread?: number;
  lastActiveAt?: string;
  lastMessage?: ConversationMessage;
}

/** 分页查询告警记录（可按状态过滤，如 FIRING） */
export async function pageAlertRecords(
  params: { pageSize?: number; status?: string } = {},
): Promise<PageResult<AlertRecord>> {
  const search = new URLSearchParams();
  if (params.pageSize !== undefined) search.set('pageSize', String(params.pageSize));
  if (params.status) search.set('status', params.status);
  const query = search.toString();
  const page = await http.get<PageResult<AlertRecord>>(
    `/alert/record/page${query ? `?${query}` : ''}`,
  );
  return { records: page?.records ?? [], total: page?.total ?? '0' };
}

/** 查询当前用户 IM 会话列表（含未读数与最后一条消息） */
export async function listConversations(): Promise<Conversation[]> {
  const list = await http.get<Conversation[]>('/im/conversations');
  return list ?? [];
}

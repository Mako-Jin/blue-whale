// inapp.ts —— 站内信收件箱数据源（/message/inapp/**）
// 登录用户自助能力：无需权限码，后端按网关注入的 X-User-Id 只返回本人消息。

import { http } from './client';

/** 站内信收件箱条目（字段对齐后端 InAppMessageVO） */
export interface InAppMessage {
  /** 投递记录ID（后端 Long 序列化为字符串） */
  deliveryId: string;
  /** 消息ID：标记已读用 */
  messageId: string;
  title?: string;
  content?: string;
  bizType?: string;
  bizId?: string;
  deliveryStatus?: string;
  /** 1 已读 / 0 未读 */
  readFlag?: number;
  sentAt?: string;
  createTime?: string;
}

/** 后端分页结果信封 */
interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 分页查询本人站内信（readFlag 1 已读 / 0 未读 / 缺省全部） */
export async function pageInAppMessages(params: {
  current: number;
  size: number;
  readFlag?: number;
}): Promise<PageResult<InAppMessage>> {
  const search = new URLSearchParams();
  search.set('current', String(params.current));
  search.set('size', String(params.size));
  if (params.readFlag !== undefined) search.set('readFlag', String(params.readFlag));
  const page = await http.get<PageResult<InAppMessage>>(`/message/inapp/page?${search.toString()}`);
  return {
    records: page?.records ?? [],
    total: page?.total ?? 0,
    current: page?.current ?? params.current,
    size: page?.size ?? params.size,
  };
}

/** 查询本人未读站内信数量 */
export async function getInAppUnreadCount(): Promise<number> {
  const count = await http.get<number>('/message/inapp/unread-count');
  return Number(count ?? 0);
}

/** 标记本人某条站内信为已读 */
export function markInAppRead(messageId: string): Promise<void> {
  return http.post<void>(`/message/inapp/${messageId}/read`);
}

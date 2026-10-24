// IM 业务 API 层（与 whale-im 后端 VO/DTO 字段一一对应）
//
// 主键类字段后端统一输出字符串，前端不做数字转换，避免精度丢失。

import { del, get, post } from './client';

/** 会话类型：单聊 / 群聊 / 机器人 */
export type ConversationType = 'SINGLE' | 'GROUP' | 'BOT';

/** 发送者类型 */
export type SenderType = 'USER' | 'BOT' | 'SYSTEM';

/** 消息内容类型 */
export type MessageContentType = 'TEXT' | 'MARKDOWN' | 'IMAGE' | 'FILE' | 'CARD';

/** 成员角色 */
export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface MessageVO {
  id: string;
  conversationId: string;
  seq: number;
  senderId: string;
  senderType: SenderType;
  contentType: MessageContentType;
  content: string;
  clientMsgId: string;
  replyTo?: string | null;
  status: number;
  createTime: string;
}

export interface ConversationVO {
  id: string;
  type: ConversationType;
  title: string;
  ownerId: string;
  lastSeq: number;
  unread: number;
  lastActiveAt: string;
  lastMessage?: MessageVO | null;
}

export interface MemberVO {
  userId: string;
  memberType: 'USER' | 'BOT';
  role: MemberRole;
  lastReadSeq: number;
  muteUntil?: string | null;
  joinTime: string;
}

export interface MessagePageVO {
  records: MessageVO[];
  hasMore: boolean;
}

export interface WsTicketVO {
  ticket: string;
  expiresIn: number;
}

/** 确认卡片 content（CARD 消息的 JSON） */
export interface ConfirmationCardData {
  type: 'platform_op';
  confirmationId: string;
  operation: string;
  summary: string;
  permission: string;
  expiresAt: string;
  runId?: string;
}

/** 附件消息 content（IMAGE / FILE 的 JSON） */
export interface AttachmentData {
  fileId: number | string;
  name: string;
  size: number;
  contentType?: string;
}

/** 工具调用轨迹（后端原样透传，字段按实际返回兜底展示） */
export interface ToolTraceItem {
  step?: number;
  name?: string;
  arguments?: string;
  success?: boolean;
  [key: string]: unknown;
}

/** 引用条目 */
export interface CitationItem {
  sourceId?: string;
  kind?: string;
  path?: string;
  title?: string;
  [key: string]: unknown;
}

export interface AgentRunDetail {
  runId: string;
  conversationId: string;
  status: string;
  requestMessageId?: string | null;
  responseMessageId?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMs?: number | null;
  tools: ToolTraceItem[];
  citations: CitationItem[];
  confirmation?: ConfirmationCardData | null;
}

export interface CreateConversationPayload {
  type: 'SINGLE' | 'GROUP';
  title?: string;
  memberIds: number[];
}

export interface SendMessagePayload {
  clientMsgId: string;
  contentType: MessageContentType;
  content: string;
  replyTo?: number;
}

export const imApi = {
  listConversations: () => get<ConversationVO[]>('/im/conversations'),
  createConversation: (payload: CreateConversationPayload) =>
    post<ConversationVO>('/im/conversations', payload),
  openBotConversation: () => post<ConversationVO>('/im/conversations/bot'),
  listMessages: (id: string, query: { beforeSeq?: number; afterSeq?: number; limit?: number }) =>
    get<MessagePageVO>(`/im/conversations/${id}/messages`, query),
  sendMessage: (id: string, payload: SendMessagePayload) =>
    post<MessageVO>(`/im/conversations/${id}/messages`, payload),
  markRead: (id: string, seq: number) => post<void>(`/im/conversations/${id}/read`, { seq }),
  recall: (id: string, messageId: string) =>
    post<void>(`/im/conversations/${id}/messages/${messageId}/recall`),
  listMembers: (id: string) => get<MemberVO[]>(`/im/conversations/${id}/members`),
  addMembers: (id: string, memberIds: string[]) =>
    post<MemberVO[]>(`/im/conversations/${id}/members`, { memberIds: memberIds.map(Number) }),
  removeMember: (id: string, userId: string) =>
    del<void>(`/im/conversations/${id}/members/${userId}`),
  quit: (id: string) => post<void>(`/im/conversations/${id}/quit`),
  wsTicket: () => post<WsTicketVO>('/im/ws-ticket'),
  getRun: (id: string) => get<AgentRunDetail>(`/im/agent-runs/${id}`),
  confirmRun: (id: string, approved: boolean) =>
    post<Record<string, unknown>>(`/im/agent-runs/${id}/confirm`, { approved }),
};

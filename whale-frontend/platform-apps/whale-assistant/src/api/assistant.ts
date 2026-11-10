// AI 助手平台 API 封装（whale-assistant 8087，经网关 /assistant 前缀）

import { http, postStream } from './client';

/** 助手定义视图 */
export interface DefinitionVO {
  id: string;
  code: string;
  name: string;
  avatar?: string | null;
  description?: string | null;
  status: 'draft' | 'published' | 'disabled';
}

/** 会话视图 */
export interface SessionVO {
  id: string;
  assistantId: string;
  assistantName?: string | null;
  title?: string | null;
  source: string;
  channelRef?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 引用块 */
export interface CitationVO {
  sourceId: string;
  kind: string;
  path?: string | null;
  title?: string | null;
  startLine?: number | null;
  endLine?: number | null;
  snippet?: string | null;
}

/** 消息视图 */
export interface MessageVO {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content?: string | null;
  citations?: CitationVO[] | null;
  tokens: number;
  elapsedMs: number;
  degraded: boolean;
  createTime?: string | null;
}

/** SSE done 帧 */
export interface ChatDoneVO {
  sessionId: string;
  assistantId: string;
  messageId: string;
  tokens: number;
  degraded: boolean;
  elapsedMillis: number;
  citations: CitationVO[];
}

/** 已发布助手列表（会话创建选择用；无 sys:assistant:define 权限时可能 403，由调用方降级） */
export function listPublishedDefinitions(): Promise<DefinitionVO[]> {
  return http.get<DefinitionVO[]>('/assistant/definitions/page', { status: 'published', size: 50 });
}

/** 我的会话分页 */
export function pageSessions(current = 1, size = 20) {
  return http.get<{ records: SessionVO[]; total: number }>('/assistant/sessions/page', {
    current,
    size,
  });
}

/** 创建会话 */
export function createSession(assistantId: number, source = 'platform', channelRef?: string) {
  return http.post<SessionVO>('/assistant/sessions', {
    assistantId,
    source,
    channelRef,
  });
}

/** 会话历史消息 */
export function listMessages(sessionId: string): Promise<MessageVO[]> {
  return http.get<MessageVO[]>(`/assistant/sessions/${sessionId}/messages`);
}

/** 删除会话 */
export function deleteSession(sessionId: string) {
  return http.del<void>(`/assistant/sessions/${sessionId}`);
}

/** 流式问答事件 */
export interface StreamHandlers {
  onMeta?: (data: { assistantId?: string; intent?: string; degraded?: boolean }) => void;
  onDelta?: (text: string) => void;
  onDone?: (data: ChatDoneVO) => void;
  onError?: (message: string) => void;
}

/**
 * 发起流式问答（SSE 帧解析见 client.postStream）。
 *
 * @param sessionId 会话ID
 * @param question  问题
 * @param handlers  事件回调
 * @param signal    中断信号
 */
export function streamChat(
  sessionId: string,
  question: string,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return postStream(
    `/assistant/sessions/${sessionId}/stream`,
    { question },
    (event, data) => {
      let parsed: Record<string, unknown> = {};
      try {
        parsed = JSON.parse(data) as Record<string, unknown>;
      } catch {
        parsed = {};
      }
      switch (event) {
        case 'meta':
          handlers.onMeta?.(parsed as { assistantId?: string; intent?: string; degraded?: boolean });
          break;
        case 'delta':
          handlers.onDelta?.(String(parsed.text ?? ''));
          break;
        case 'done':
          handlers.onDone?.(parsed as unknown as ChatDoneVO);
          break;
        case 'error':
          handlers.onError?.(String(parsed.message ?? '问答失败'));
          break;
        default:
          break;
      }
    },
    signal,
  );
}

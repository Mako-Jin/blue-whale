// 文档 AI 助手契约层：经网关调用 whale-agent 问答 SSE，检索范围限定文档知识块
//
// 后端响应为 { code, message, data } 信封（SSE 除外），Id 一律按字符串处理。

import { streamSse } from './client';

/** 引用（回答中的 [S1]…[Sn]） */
export interface AssistantCitation {
  sourceId: string;
  kind: 'fact' | 'chunk' | string;
  path: string;
  title?: string;
  snippet?: string;
  /** 文档页面地址（文档知识块可跳转） */
  url?: string;
}

/** SSE meta 事件 */
export interface AssistantMeta {
  conversationId: string;
  intent?: string;
  degraded?: boolean;
  citations?: AssistantCitation[];
}

/** SSE done 事件 */
export interface AssistantDone {
  conversationId: string;
  messageId: string;
  degraded?: boolean;
  citations?: AssistantCitation[];
}

/** 流式回调 */
export interface DocAssistantHandlers {
  onMeta?: (meta: AssistantMeta) => void;
  onDelta?: (text: string) => void;
  onDone?: (done: AssistantDone) => void;
  onError?: (message: string) => void;
}

/**
 * 提问文档知识库（仅检索 doc_page 范围）。
 *
 * @param question 问题（建议带上页面标题以聚焦上下文）
 * @param handlers 事件回调
 * @param signal   中止信号
 */
export async function askDocAssistant(
  question: string,
  handlers: DocAssistantHandlers,
  signal?: AbortSignal,
): Promise<void> {
  await streamSse(
    '/agent/v1/chat',
    { question, scope: 'doc_page' },
    (event, data) => {
      switch (event) {
        case 'meta':
          handlers.onMeta?.(data as AssistantMeta);
          break;
        case 'delta': {
          const payload = data as { content?: string; text?: string };
          const text = payload.content ?? payload.text ?? '';
          if (text) {
            handlers.onDelta?.(text);
          }
          break;
        }
        case 'done':
          handlers.onDone?.(data as AssistantDone);
          break;
        case 'error': {
          const payload = data as { message?: string };
          handlers.onError?.(payload.message ?? 'AI 回答失败');
          break;
        }
        default:
          break;
      }
    },
    signal,
  );
}

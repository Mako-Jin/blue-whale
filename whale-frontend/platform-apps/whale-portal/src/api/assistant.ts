// assistant.ts —— AI 助手内嵌问答数据源（/assistant/**）
// 门户内嵌演示入口：embed 来源会话 + SSE 流式问答。
// 登录用户自助能力：问答权限码 sys:assistant:chat；列表接口仅返回已发布助手。

import { ApiError } from '@bluewhale/http';
import { getToken, http } from './client';

/** 已发布助手（字段对齐后端 DefinitionVO，问答视角精简展示） */
export interface AssistantDef {
  /** 后端 Long 序列化为字符串 */
  id: string;
  code: string;
  name?: string;
  avatar?: string;
  description?: string;
}

/** 会话视图（字段对齐后端 SessionVO） */
export interface AssistantSession {
  id: string;
  assistantId: string;
  title?: string;
  source?: string;
}

/** 查询已发布助手清单（问答视角） */
export function listPublishedAssistants(): Promise<AssistantDef[]> {
  return http.get<AssistantDef[]>('/assistant/v1/definitions/published');
}

/** 创建 embed 来源会话（门户内嵌入口） */
export function createEmbedSession(assistantId: string): Promise<AssistantSession> {
  return http.post<AssistantSession>('/assistant/v1/sessions', {
    assistantId: Number(assistantId),
    source: 'embed',
    channelRef: 'portal-embed',
  });
}

/** 流式问答事件回调（事件名, data JSON 串） */
export type StreamListener = (event: string, data: string) => void;

/**
 * POST 流式问答：手工解析 SSE 帧（EventSource 不支持 POST）。
 *
 * @param url      流式端点
 * @param body     请求体
 * @param onEvent  事件回调
 * @param signal   中断信号
 */
export async function postStream(
  url: string,
  body: unknown,
  onEvent: StreamListener,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken();
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
  });
  if (!response.ok || !response.body) {
    throw new ApiError(response.status, `流式问答失败：HTTP ${response.status}`);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let event = 'message';
  let data = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let index = buffer.indexOf('\n');
    while (index >= 0) {
      const line = buffer.slice(0, index).replace(/\r$/, '');
      buffer = buffer.slice(index + 1);
      if (line === '') {
        if (data) {
          onEvent(event, data);
          event = 'message';
          data = '';
        }
      } else if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        data = data ? `${data}\n${line.slice(5).trimStart()}` : line.slice(5).trimStart();
      }
      index = buffer.indexOf('\n');
    }
  }
  if (data) {
    onEvent(event, data);
  }
}

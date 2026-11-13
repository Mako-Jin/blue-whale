// AI 助手内嵌对话页（门户演示入口）
// 选择已发布助手 → embed 来源建会话 → SSE 流式问答（delta 增量渲染，done 收尾）。
// 会话保存在组件状态内；切换助手自动开新会话。

import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Empty, Input, Select, Space, Tag, Typography } from 'antd';
import {
  createEmbedSession,
  listPublishedAssistants,
  postStream,
  type AssistantDef,
} from '../api/assistant';

interface ChatItem {
  role: 'user' | 'assistant';
  content: string;
}

export default function Assistant() {
  const { message } = App.useApp();
  const [assistants, setAssistants] = useState<AssistantDef[]>([]);
  const [assistantId, setAssistantId] = useState<string | undefined>(undefined);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [items, setItems] = useState<ChatItem[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    listPublishedAssistants()
      .then((list) => {
        setAssistants(list);
        if (list.length > 0) {
          setAssistantId((current) => current ?? list[0].id);
        }
      })
      .catch(() => message.error('助手清单加载失败（whale-assistant 未就绪或无权限）'));
  }, [message]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [items]);

  /** 切换助手：重置会话与消息 */
  const handleAssistantChange = (value: string) => {
    setAssistantId(value);
    setSessionId(null);
    setItems([]);
  };

  const send = useCallback(async () => {
    const question = input.trim();
    if (!question || streaming) {
      return;
    }
    if (!assistantId) {
      message.warning('暂无已发布助手');
      return;
    }
    setInput('');
    setItems((prev) => [...prev, { role: 'user', content: question }]);
    setStreaming(true);
    try {
      let currentSessionId = sessionId;
      if (!currentSessionId) {
        const session = await createEmbedSession(assistantId);
        currentSessionId = session.id;
        setSessionId(session.id);
      }
      setItems((prev) => [...prev, { role: 'assistant', content: '' }]);
      let closed = false;
      await postStream(
        `/assistant/sessions/${currentSessionId}/stream`,
        { question },
        (event, data) => {
          if (event === 'delta') {
            try {
              const text = (JSON.parse(data) as { text?: string }).text ?? '';
              setItems((prev) => {
                const next = [...prev];
                const last = next[next.length - 1];
                if (last?.role === 'assistant') {
                  next[next.length - 1] = { ...last, content: last.content + text };
                }
                return next;
              });
            } catch {
              // 忽略无法解析的 delta 帧
            }
          } else if (event === 'error') {
            closed = true;
            try {
              const text = (JSON.parse(data) as { message?: string }).message ?? '问答失败';
              message.error(text);
            } catch {
              message.error('问答失败');
            }
          }
        },
      );
      if (!closed) {
        // done 帧（引用、token 等元信息在门户演示中暂不展示）
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '问答失败，请稍后重试');
    } finally {
      setStreaming(false);
    }
  }, [assistantId, input, message, sessionId, streaming]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 140px)' }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          style={{ width: 240 }}
          placeholder="选择助手"
          value={assistantId}
          onChange={handleAssistantChange}
          options={assistants.map((item) => ({ value: item.id, label: item.name ?? item.code }))}
          notFoundContent={
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布助手" />
          }
        />
        {sessionId ? <Tag color="green">会话已建立</Tag> : <Tag>发送首条消息时自动建会话</Tag>}
      </Space>

      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '8px 4px',
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
        }}
      >
        {items.length === 0 && (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="向 AI 助手提问，答案会流式返回"
          />
        )}
        {items.map((item, index) => (
          <div
            key={index}
            style={{
              alignSelf: item.role === 'user' ? 'flex-end' : 'flex-start',
              maxWidth: '78%',
              background: item.role === 'user' ? '#e6f4ff' : '#f5f5f5',
              borderRadius: 8,
              padding: '8px 12px',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}
          >
            <Typography.Text style={{ fontSize: 13, color: '#999' }}>
              {item.role === 'user' ? '我' : '助手'}
            </Typography.Text>
            <div>{item.content || (streaming ? '…' : '')}</div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <Space.Compact style={{ width: '100%', marginTop: 12 }}>
        <Input
          placeholder="输入问题（Enter 发送）"
          value={input}
          disabled={streaming || !assistantId}
          onChange={(event) => setInput(event.target.value)}
          onPressEnter={() => void send()}
          maxLength={2000}
        />
        <Button
          type="primary"
          loading={streaming}
          disabled={!assistantId}
          onClick={() => void send()}
        >
          发送
        </Button>
      </Space.Compact>
    </div>
  );
}

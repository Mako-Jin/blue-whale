// 会话工作台：左侧我的会话 + 右侧对话窗（SSE 流式渲染，M1 最小闭环）
//
// 管理端（助手定义 CRUD/工具授权）在 M3 进 admin；本页只做「选助手 → 建会话 → 问答」。

import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Empty, Input, List, Popconfirm, Select, Space, Tag, Typography } from 'antd';
import {
  createSession,
  deleteSession,
  listMessages,
  listPublishedDefinitions,
  pageSessions,
  streamChat,
  type CitationVO,
  type DefinitionVO,
  type SessionVO,
} from '../api/assistant';

interface ChatItem {
  role: 'user' | 'assistant';
  content: string;
  citations?: CitationVO[];
  streaming?: boolean;
}

export default function ChatWorkbenchPage() {
  const { message } = App.useApp();

  const [definitions, setDefinitions] = useState<DefinitionVO[]>([]);
  const [assistantId, setAssistantId] = useState<number | undefined>();
  const [sessions, setSessions] = useState<SessionVO[]>([]);
  const [current, setCurrent] = useState<SessionVO | null>(null);
  const [items, setItems] = useState<ChatItem[]>([]);
  const [question, setQuestion] = useState('');
  const [busy, setBusy] = useState(false);
  const messagesRef = useRef<HTMLDivElement>(null);

  const scrollToEnd = useCallback(() => {
    requestAnimationFrame(() => {
      messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight });
    });
  }, []);

  const reloadSessions = useCallback(async () => {
    try {
      const page = await pageSessions();
      setSessions(page.records ?? []);
    } catch {
      // 未登录/独立运行时静默：会话列表留空
      setSessions([]);
    }
  }, []);

  useEffect(() => {
    listPublishedDefinitions()
      .then((list) => {
        setDefinitions(list);
        if (list.length > 0) {
          setAssistantId(Number(list[0].id));
        }
      })
      .catch(() => setDefinitions([]));
    void reloadSessions();
  }, [reloadSessions]);

  const openSession = useCallback(
    async (session: SessionVO) => {
      setCurrent(session);
      setItems([]);
      try {
        const messages = await listMessages(session.id);
        setItems(
          messages
            .filter((item) => item.role !== 'system')
            .map((item) => ({
              role: item.role === 'user' ? 'user' : 'assistant',
              content: item.content ?? '',
              citations: item.citations ?? undefined,
            })),
        );
        scrollToEnd();
      } catch (error) {
        message.error(`历史消息加载失败：${String(error)}`);
      }
    },
    [message, scrollToEnd],
  );

  const handleCreate = useCallback(async () => {
    if (!assistantId) {
      message.warning('请先选择助手');
      return;
    }
    try {
      const session = await createSession(assistantId);
      setCurrent(session);
      setItems([]);
      await reloadSessions();
    } catch (error) {
      message.error(`创建会话失败：${String(error)}`);
    }
  }, [assistantId, message, reloadSessions]);

  const handleDelete = useCallback(
    async (session: SessionVO) => {
      try {
        await deleteSession(session.id);
        if (current?.id === session.id) {
          setCurrent(null);
          setItems([]);
        }
        await reloadSessions();
      } catch (error) {
        message.error(`删除会话失败：${String(error)}`);
      }
    },
    [current, message, reloadSessions],
  );

  const handleAsk = useCallback(async () => {
    const text = question.trim();
    if (!text || !current || busy) {
      return;
    }
    setBusy(true);
    setQuestion('');
    setItems((prev) => [
      ...prev,
      { role: 'user', content: text },
      { role: 'assistant', content: '', streaming: true },
    ]);
    scrollToEnd();
    try {
      await streamChat(current.id, text, {
        onDelta: (delta) => {
          setItems((prev) => {
            const next = [...prev];
            const last = next[next.length - 1];
            if (last?.role === 'assistant') {
              next[next.length - 1] = { ...last, content: last.content + delta };
            }
            return next;
          });
          scrollToEnd();
        },
        onDone: (done) => {
          setItems((prev) => {
            const next = [...prev];
            const last = next[next.length - 1];
            if (last?.role === 'assistant') {
              next[next.length - 1] = {
                ...last,
                content: last.content || '（无回答）',
                citations: done.citations,
                streaming: false,
              };
            }
            return next;
          });
          scrollToEnd();
        },
        onError: (msg) => {
          message.error(msg);
          setItems((prev) => {
            const next = [...prev];
            const last = next[next.length - 1];
            if (last?.role === 'assistant' && !last.content) {
              next[next.length - 1] = { ...last, content: `（失败：${msg}）`, streaming: false };
            } else if (last?.role === 'assistant') {
              next[next.length - 1] = { ...last, streaming: false };
            }
            return next;
          });
        },
      });
    } catch (error) {
      message.error(`流式问答失败：${String(error)}`);
    } finally {
      setBusy(false);
      setItems((prev) => {
        const next = [...prev];
        const last = next[next.length - 1];
        if (last?.role === 'assistant') {
          next[next.length - 1] = { ...last, streaming: false };
        }
        return next;
      });
    }
  }, [busy, current, message, question, scrollToEnd]);

  return (
    <div className="asst-layout">
      <aside className="asst-sider">
        <div className="asst-sider-header">
          <h2 className="asst-sider-title">AI 助手</h2>
          <Space.Compact style={{ width: '100%' }}>
            <Select
              style={{ flex: 1 }}
              placeholder="选择助手"
              value={assistantId}
              onChange={setAssistantId}
              options={definitions.map((item) => ({
                value: Number(item.id),
                label: item.name,
              }))}
              notFoundContent="暂无已发布助手"
            />
            <Button type="primary" onClick={handleCreate}>
              新会话
            </Button>
          </Space.Compact>
        </div>
        <div className="asst-sider-list">
          {sessions.length === 0 ? (
            <Empty description="暂无会话" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <List
              dataSource={sessions}
              renderItem={(session) => (
                <List.Item
                  style={{
                    cursor: 'pointer',
                    padding: '8px 12px',
                    borderRadius: 8,
                    background: current?.id === session.id ? 'rgba(22,104,220,0.08)' : undefined,
                  }}
                  onClick={() => void openSession(session)}
                  actions={[
                    <Popconfirm
                      key="del"
                      title="删除该会话？"
                      onConfirm={(event) => {
                        event?.stopPropagation();
                        void handleDelete(session);
                      }}
                      onCancel={(event) => event?.stopPropagation()}
                    >
                      <Button
                        type="text"
                        size="small"
                        danger
                        onClick={(event) => event.stopPropagation()}
                      >
                        删除
                      </Button>
                    </Popconfirm>,
                  ]}
                >
                  <Typography.Text ellipsis style={{ maxWidth: 160 }}>
                    {session.title || '新会话'}
                  </Typography.Text>
                </List.Item>
              )}
            />
          )}
        </div>
      </aside>
      <main className="asst-main">
        <div className="asst-main-header">
          <h3 className="asst-main-title">
            {current
              ? `${current.assistantName ?? '助手'} · ${current.title || '新会话'}`
              : '选择或新建会话'}
          </h3>
        </div>
        <div className="asst-messages" ref={messagesRef}>
          {items.length === 0 ? (
            <div className="asst-empty">向助手提问，回答将附带 [S1] 形式的证据引用。</div>
          ) : (
            items.map((item, index) => (
              <div
                key={index}
                className={`asst-bubble ${item.role === 'user' ? 'asst-bubble-user' : 'asst-bubble-assistant'}`}
              >
                {item.content}
                {item.streaming ? <span className="asst-cursor" /> : null}
                {item.citations && item.citations.length > 0 ? (
                  <div className="asst-citations">
                    <Space size={4} wrap>
                      {item.citations.map((citation) => (
                        <Tag key={citation.sourceId} color="blue">
                          {citation.sourceId} {citation.path ?? ''}
                        </Tag>
                      ))}
                    </Space>
                  </div>
                ) : null}
              </div>
            ))
          )}
        </div>
        <div className="asst-input-bar">
          <Input
            placeholder={current ? '输入问题，Enter 发送' : '请先在左侧选择或新建会话'}
            value={question}
            disabled={!current || busy}
            onChange={(event) => setQuestion(event.target.value)}
            onPressEnter={() => void handleAsk()}
            allowClear
          />
          <Button
            type="primary"
            loading={busy}
            disabled={!current}
            onClick={() => void handleAsk()}
          >
            发送
          </Button>
        </div>
      </main>
    </div>
  );
}

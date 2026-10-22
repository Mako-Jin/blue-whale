// 知识问答工作台：左栏会话/范围开关，中栏消息流（SSE 打字机），右栏证据面板

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { App, Button, Checkbox, Empty, Input, Popconfirm, Space, Spin, Tag, Tooltip } from 'antd';
import dayjs from 'dayjs';
import {
  deleteConversation,
  joinScope,
  listMessages,
  pageConversations,
  SCOPE_OPTIONS,
  sendFeedback,
  streamChat,
  type Citation,
  type Conversation,
  type ScopeKey,
} from '../../api/agent';
import EvidencePanel from '../../components/EvidencePanel';
import Markdown from '../../components/Markdown';

/** 界面消息模型（用户消息与助手回答共用，助手消息在流式过程中持续增长） */
interface UiMessage {
  /** 本地唯一键（流式期间尚未有服务端 ID） */
  key: string;
  /** 服务端消息 ID（落库后回填，用于评价） */
  id?: string;
  role: 'user' | 'assistant';
  content: string;
  citations: Citation[];
  intent?: string;
  degraded?: boolean;
  tokens?: number;
  streaming?: boolean;
  error?: string;
  /** 已提交的评价：1 有用 / -1 无用 */
  feedback?: 1 | -1;
}

/** 全部检索范围（默认全选，等价于后端 scope 为空） */
const ALL_SCOPES: ScopeKey[] = SCOPE_OPTIONS.map((item) => item.key);

/** 空态推荐问题 */
const SAMPLE_QUESTIONS = [
  'whale-agent 的端口是多少？',
  '系统的鉴权链路是怎么实现的？',
  'sys_user 表有哪些字段？',
  '最近一次提交改了什么？',
];

/** 判断是否为主动中止（停止按钮）导致的异常 */
function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

/**
 * 复制文本：优先 Clipboard API，不可用/被拒时回退到隐藏 textarea + execCommand。
 *
 * @param text 待复制文本
 * @returns 是否复制成功
 */
async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // 权限被拒时继续走兜底方案
    }
  }
  try {
    const area = document.createElement('textarea');
    area.value = text;
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const copied = document.execCommand('copy');
    document.body.removeChild(area);
    return copied;
  } catch {
    return false;
  }
}

/** 会话消息 → 界面消息 */
function toUiMessage(message: {
  id: string;
  role: string;
  content: string;
  citations?: Citation[];
  tokens?: number;
}): UiMessage {
  return {
    key: `s-${message.id}`,
    id: message.id,
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content ?? '',
    citations: message.citations ?? [],
    tokens: message.tokens,
  };
}

export default function AgentWorkbench() {
  const { message: toast } = App.useApp();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [conversationsLoading, setConversationsLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [scope, setScope] = useState<ScopeKey[]>(ALL_SCOPES);
  const [question, setQuestion] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [activeEvidenceKey, setActiveEvidenceKey] = useState<string | null>(null);
  const [activeCitation, setActiveCitation] = useState<string | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);

  /** 拉取会话列表 */
  const refreshConversations = useCallback(async () => {
    setConversationsLoading(true);
    try {
      const page = await pageConversations(1, 50);
      setConversations(page.records ?? []);
    } catch {
      // 列表失败不阻断问答
    } finally {
      setConversationsLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshConversations();
  }, [refreshConversations]);

  // 消息增长时滚动到底部（流式期间持续跟随）
  useEffect(() => {
    const node = listRef.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [messages]);

  /** 更新指定助手消息 */
  const patchMessage = useCallback((key: string, patch: (current: UiMessage) => UiMessage) => {
    setMessages((prev) => prev.map((item) => (item.key === key ? patch(item) : item)));
  }, []);

  /** 打开某个会话 */
  const openConversation = useCallback(async (id: string) => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    setStreaming(false);
    setConversationId(id);
    setActiveEvidenceKey(null);
    setActiveCitation(null);
    setMessagesLoading(true);
    try {
      const list = await listMessages(id);
      setMessages((list ?? []).map(toUiMessage));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '加载会话消息失败');
      setMessages([]);
    } finally {
      setMessagesLoading(false);
    }
  }, [toast]);

  /** 新建会话（清空当前上下文） */
  const startNewConversation = () => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    setStreaming(false);
    setConversationId(null);
    setMessages([]);
    setActiveEvidenceKey(null);
    setActiveCitation(null);
    setQuestion('');
  };

  /** 删除会话 */
  const handleDelete = async (id: string) => {
    try {
      await deleteConversation(id);
      toast.success('会话已删除');
      if (conversationId === id) {
        startNewConversation();
      }
      await refreshConversations();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '删除会话失败');
    }
  };

  /** 发送提问（SSE 流式） */
  const send = useCallback(async () => {
    const text = question.trim();
    if (!text || streaming) {
      return;
    }
    const stamp = Date.now();
    const userKey = `u-${stamp}`;
    const assistantKey = `a-${stamp}`;
    setMessages((prev) => [
      ...prev,
      { key: userKey, role: 'user', content: text, citations: [] },
      { key: assistantKey, role: 'assistant', content: '', citations: [], streaming: true },
    ]);
    setQuestion('');
    setStreaming(true);
    setActiveEvidenceKey(assistantKey);
    setActiveCitation(null);

    const controller = new AbortController();
    abortRef.current = controller;
    let resolvedId = conversationId;

    try {
      await streamChat(
        {
          conversationId: conversationId ?? undefined,
          question: text,
          scope: joinScope(scope),
        },
        {
          onMeta: (meta) => {
            resolvedId = meta.conversationId;
            setConversationId(meta.conversationId);
            patchMessage(assistantKey, (item) => ({
              ...item,
              citations: meta.citations ?? [],
              intent: meta.intent,
              degraded: meta.degraded,
            }));
            setActiveCitation(meta.citations?.[0]?.sourceId ?? null);
          },
          onDelta: (delta) => {
            patchMessage(assistantKey, (item) => ({ ...item, content: item.content + delta }));
          },
          onDone: (done) => {
            patchMessage(assistantKey, (item) => ({
              ...item,
              id: done.messageId,
              citations: done.citations?.length ? done.citations : item.citations,
              tokens: done.tokens,
              degraded: done.degraded,
              streaming: false,
            }));
            setActiveCitation((current) => current ?? done.citations?.[0]?.sourceId ?? null);
            void refreshConversations();
          },
          onError: (errorMessage) => {
            patchMessage(assistantKey, (item) => ({ ...item, streaming: false, error: errorMessage }));
          },
        },
        controller.signal,
      );
      // 流结束但未收到 done（后端异常/连接中断）时兜底关闭打字机
      patchMessage(assistantKey, (item) =>
        item.streaming ? { ...item, streaming: false } : item,
      );
      if (resolvedId) {
        void refreshConversations();
      }
    } catch (error) {
      if (isAbortError(error)) {
        patchMessage(assistantKey, (item) => ({ ...item, streaming: false }));
      } else {
        const errorMessage = error instanceof Error ? error.message : '问答请求失败';
        patchMessage(assistantKey, (item) => ({ ...item, streaming: false, error: errorMessage }));
      }
    } finally {
      abortRef.current = null;
      setStreaming(false);
    }
  }, [conversationId, patchMessage, question, refreshConversations, scope, streaming]);

  /** 停止生成 */
  const stop = () => {
    abortRef.current?.abort();
    abortRef.current = null;
  };

  /** 评价助手回答 */
  const rate = async (item: UiMessage, rating: 1 | -1) => {
    if (!item.id) {
      toast.warning('回答尚未落库，暂不可评价');
      return;
    }
    try {
      await sendFeedback(item.id, rating, undefined);
      patchMessage(item.key, (current) => ({ ...current, feedback: rating }));
      toast.success(rating === 1 ? '感谢反馈' : '已记录，我们会继续改进');
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '评价失败');
    }
  };

  /** 复制回答 */
  const copy = async (content: string) => {
    if (await copyText(content)) {
      toast.success('已复制');
    } else {
      toast.error('复制失败，请手动选择文本');
    }
  };

  /** 点击正文 [Sx]：定位到对应证据 */
  const focusCitation = (messageKey: string, sourceId: string) => {
    setActiveEvidenceKey(messageKey);
    setActiveCitation(sourceId);
    window.setTimeout(() => {
      document.getElementById(`evidence-${sourceId}`)?.scrollIntoView({ block: 'nearest' });
    }, 0);
  };

  /** 右栏展示的消息：优先用户选中的助手消息，否则最后一条带引用的回答 */
  const evidenceMessage = useMemo(() => {
    if (activeEvidenceKey) {
      const found = messages.find((item) => item.key === activeEvidenceKey);
      if (found && found.role === 'assistant') {
        return found;
      }
    }
    return [...messages].reverse().find((item) => item.role === 'assistant') ?? null;
  }, [activeEvidenceKey, messages]);

  const scopeIsAll = scope.length === 0 || scope.length === SCOPE_OPTIONS.length;

  return (
    <div className="agent-workbench">
      {/* 左栏：会话 + 范围开关 */}
      <aside className="agent-sidebar">
        <div className="agent-sidebar-top">
          <Button type="primary" block onClick={startNewConversation}>
            新建会话
          </Button>
        </div>
        <div className="agent-sidebar-section-title">
          <span>我的会话</span>
          {conversationsLoading && <Spin size="small" />}
        </div>
        <div className="agent-conversation-list">
          {conversations.length === 0 && !conversationsLoading ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话" />
          ) : (
            conversations.map((item) => (
              <div
                key={item.id}
                className={`agent-conversation${conversationId === item.id ? ' agent-conversation-active' : ''}`}
                onClick={() => void openConversation(item.id)}
              >
                <div className="agent-conversation-title" title={item.title}>
                  {item.title || '未命名会话'}
                </div>
                <div className="agent-conversation-meta">
                  <span>{item.updateTime ? dayjs(item.updateTime).format('MM-DD HH:mm') : ''}</span>
                  <Popconfirm
                    title="删除该会话？"
                    description="会话消息将一并删除，且不可恢复。"
                    okText="删除"
                    cancelText="取消"
                    onConfirm={() => void handleDelete(item.id)}
                  >
                    <Button
                      type="link"
                      size="small"
                      danger
                      className="agent-conversation-delete"
                      onClick={(event) => event.stopPropagation()}
                    >
                      删除
                    </Button>
                  </Popconfirm>
                </div>
              </div>
            ))
          )}
        </div>
        <div className="agent-sidebar-footer">
          <div className="agent-sidebar-section-title">
            <span>检索范围</span>
            <Tooltip title="全不选等价于全部范围">
              <Tag>默认全部</Tag>
            </Tooltip>
          </div>
          <Checkbox.Group
            className="agent-scope-group"
            value={scope}
            onChange={(values) => setScope(values as ScopeKey[])}
            options={SCOPE_OPTIONS.map((item) => ({ label: item.label, value: item.key }))}
          />
        </div>
      </aside>

      {/* 中栏：消息流 + 输入 */}
      <section className="agent-main">
        <div className="agent-message-list" ref={listRef}>
          {messagesLoading ? (
            <div className="agent-loading">
              <Spin />
            </div>
          ) : messages.length === 0 ? (
            <div className="agent-empty">
              <div className="agent-empty-title">向知识库提问</div>
              <div className="agent-empty-sub">
                覆盖平台技术栈、架构、数据表、依赖版本、运行时状态与 Git 演进；回答附证据引用。
              </div>
              <Space wrap>
                {SAMPLE_QUESTIONS.map((sample) => (
                  <Tag
                    key={sample}
                    className="agent-sample"
                    onClick={() => {
                      setQuestion(sample);
                    }}
                  >
                    {sample}
                  </Tag>
                ))}
              </Space>
            </div>
          ) : (
            messages.map((item) => (
              <div key={item.key} className={`agent-message agent-message-${item.role}`}>
                <div className="agent-message-role">{item.role === 'user' ? '我' : '知识助手'}</div>
                <div className="agent-bubble">
                  {item.role === 'user' ? (
                    <div className="agent-user-text">{item.content}</div>
                  ) : (
                    <>
                      {item.content ? (
                        <Markdown
                          content={item.content}
                          activeCitation={activeCitation}
                          onCitation={(sourceId) => focusCitation(item.key, sourceId)}
                        />
                      ) : (
                        !item.error && (
                          <div className="agent-thinking">
                            <Spin size="small" /> 正在检索证据并组织回答…
                          </div>
                        )
                      )}
                      {item.error && <div className="agent-message-error">{item.error}</div>}
                      {item.streaming && item.content && <span className="agent-caret" />}
                    </>
                  )}
                </div>
                {item.role === 'assistant' && !item.streaming && !item.error && (
                  <div className="agent-message-footer">
                    <Space size={4} wrap>
                      {item.intent && <Tag>{item.intent}</Tag>}
                      {item.degraded && <Tag color="gold">降级：检索摘要</Tag>}
                      {item.tokens != null && <span className="agent-message-meta">{item.tokens} tokens</span>}
                      {item.citations.length > 0 && (
                        <Button
                          type="link"
                          size="small"
                          onClick={() => {
                            setActiveEvidenceKey(item.key);
                            setActiveCitation(item.citations[0]?.sourceId ?? null);
                          }}
                        >
                          查看证据（{item.citations.length}）
                        </Button>
                      )}
                      <Button type="link" size="small" onClick={() => void copy(item.content)}>
                        复制
                      </Button>
                      <Button
                        type="link"
                        size="small"
                        disabled={item.feedback === 1}
                        onClick={() => void rate(item, 1)}
                      >
                        有用
                      </Button>
                      <Button
                        type="link"
                        size="small"
                        disabled={item.feedback === -1}
                        onClick={() => void rate(item, -1)}
                      >
                        无用
                      </Button>
                    </Space>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
        <div className="agent-composer">
          <Input.TextArea
            value={question}
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            autoSize={{ minRows: 2, maxRows: 6 }}
            maxLength={2000}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                void send();
              }
            }}
          />
          <div className="agent-composer-actions">
            <span className="agent-composer-hint">
              {scopeIsAll
                ? '范围：全部'
                : `范围：${scope
                    .map((key) => SCOPE_OPTIONS.find((item) => item.key === key)?.label ?? key)
                    .join(' / ')}`}
            </span>
            {streaming ? (
              <Button danger onClick={stop}>
                停止生成
              </Button>
            ) : (
              <Button type="primary" disabled={!question.trim()} onClick={() => void send()}>
                发送
              </Button>
            )}
          </div>
        </div>
      </section>

      {/* 右栏：证据面板 */}
      <aside className="agent-evidence">
        <EvidencePanel
          citations={evidenceMessage?.citations ?? []}
          activeId={activeCitation}
          footer={
            evidenceMessage?.citations.length
              ? '引用路径与行号来自索引时的真实文件，提交类事实可跳转 Gitee'
              : undefined
          }
        />
      </aside>
    </div>
  );
}

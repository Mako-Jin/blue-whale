// IM 数据状态机：会话列表、消息流、成员、机器人流式占位
//
// WS 帧统一进入 handleFrame；REST 负责历史分页、发送、已读与成员管理。

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { App } from 'antd';
import { createLogger } from '@bluewhale/whale-common-tools';
import {
  imApi,
  type ConversationVO,
  type MemberVO,
  type MessageContentType,
  type MessageVO,
  type SendMessagePayload,
} from '../api/im';
import { getCurrentUserId } from '../api/token';
import type { ImFrame } from '../ws/client';

const logger = createLogger('whale-im › data');

const PAGE_SIZE = 30;
const STREAM_REPLACE_FALLBACK_MS = 3000;

/** 机器人流式占位 */
export interface StreamEntry {
  runId: string;
  conversationId: string;
  text: string;
  intent?: string;
  degraded: boolean;
  done: boolean;
  error?: string;
  messageId?: string;
  tokens?: number;
  elapsedMillis?: number;
}

function sortConversations(list: ConversationVO[]): ConversationVO[] {
  return [...list].sort((a, b) => (b.lastActiveAt ?? '').localeCompare(a.lastActiveAt ?? ''));
}

function mergeMessages(current: MessageVO[], incoming: MessageVO[]): MessageVO[] {
  const map = new Map<string, MessageVO>();
  current.forEach((item) => map.set(item.id, item));
  incoming.forEach((item) => map.set(item.id, item));
  return [...map.values()].sort((a, b) => a.seq - b.seq);
}

export function useImData() {
  const { message } = App.useApp();
  const currentUserId = useMemo(() => getCurrentUserId(), []);

  const [conversations, setConversations] = useState<ConversationVO[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageVO[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [members, setMembers] = useState<MemberVO[]>([]);
  const [streaming, setStreaming] = useState<StreamEntry[]>([]);
  const [sending, setSending] = useState(false);
  /** 机器人回复消息ID → 运行ID（工具轨迹与反馈入口） */
  const [runByMessageId, setRunByMessageId] = useState<Record<string, string>>({});

  const activeIdRef = useRef<string | null>(null);
  const messagesRef = useRef<MessageVO[]>([]);
  const conversationsRef = useRef<ConversationVO[]>([]);
  const runByMessageIdRef = useRef<Record<string, string>>({});
  /** 兜底定时器登记表：卸载时统一清理，避免卸载后仍触发 setStreaming（审计修复） */
  const fallbackTimersRef = useRef<Set<number>>(new Set());

  useEffect(() => {
    activeIdRef.current = activeId;
  }, [activeId]);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    conversationsRef.current = conversations;
  }, [conversations]);

  useEffect(() => {
    runByMessageIdRef.current = runByMessageId;
  }, [runByMessageId]);

  // 卸载时清理所有未触发的兜底定时器
  useEffect(() => {
    const timers = fallbackTimersRef.current;
    return () => {
      timers.forEach((id) => window.clearTimeout(id));
      timers.clear();
    };
  }, []);

  /** 会话列表加载（按最后活跃时间倒序） */
  const loadConversations = useCallback(async () => {
    try {
      const list = await imApi.listConversations();
      setConversations(sortConversations(list));
    } catch (error) {
      logger.warn('会话列表加载失败', error);
      message.error('会话列表加载失败，请稍后重试');
    }
  }, [message]);

  /** 将会话未读清零（本地即时反馈） */
  const clearUnread = useCallback((id: string) => {
    setConversations((prev) =>
      prev.map((item) => (item.id === id ? { ...item, unread: 0 } : item)),
    );
  }, []);

  /** 打开会话：拉取最近一页消息、成员列表并上报已读 */
  const openConversation = useCallback(
    async (id: string) => {
      setActiveId(id);
      activeIdRef.current = id;
      setMessages([]);
      setHasMore(false);
      setMembers([]);
      setLoadingMessages(true);
      try {
        const [page, memberList] = await Promise.all([
          imApi.listMessages(id, { limit: PAGE_SIZE }),
          imApi.listMembers(id).catch(() => [] as MemberVO[]),
        ]);
        setMessages(page.records);
        setHasMore(page.hasMore);
        setMembers(memberList);
        const conversation = conversationsRef.current.find((item) => item.id === id);
        if (conversation && conversation.lastSeq > 0) {
          await imApi.markRead(id, conversation.lastSeq);
          clearUnread(id);
        }
      } catch (error) {
        logger.warn('会话详情加载失败', error);
        message.error('消息加载失败，请稍后重试');
      } finally {
        setLoadingMessages(false);
      }
    },
    [clearUnread, message],
  );

  /** 上滑加载更早消息（beforeSeq 游标） */
  const loadMore = useCallback(async () => {
    const id = activeIdRef.current;
    const oldest = messagesRef.current[0]?.seq;
    if (!id || oldest === undefined || !hasMore || loadingMessages) {
      return;
    }
    setLoadingMessages(true);
    try {
      const page = await imApi.listMessages(id, { beforeSeq: oldest, limit: PAGE_SIZE });
      setMessages((prev) => mergeMessages(page.records, prev));
      setHasMore(page.hasMore);
    } catch (error) {
      logger.warn('历史消息加载失败', error);
    } finally {
      setLoadingMessages(false);
    }
  }, [hasMore, loadingMessages]);

  /** 发送消息（clientMsgId 幂等）；成功后立即上屏 */
  const sendMessage = useCallback(
    async (
      contentType: MessageContentType,
      content: string,
      replyTo?: number,
    ): Promise<boolean> => {
      const id = activeIdRef.current;
      if (!id) {
        return false;
      }
      setSending(true);
      const payload: SendMessagePayload = {
        clientMsgId: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
        contentType,
        content,
        replyTo,
      };
      try {
        const saved = await imApi.sendMessage(id, payload);
        setMessages((prev) => mergeMessages(prev, [saved]));
        setConversations((prev) =>
          sortConversations(
            prev.map((item) =>
              item.id === id
                ? {
                    ...item,
                    lastSeq: Math.max(item.lastSeq ?? 0, saved.seq),
                    lastMessage: saved,
                    lastActiveAt: saved.createTime,
                  }
                : item,
            ),
          ),
        );
        return true;
      } catch (error) {
        logger.warn('消息发送失败', error);
        message.error('消息发送失败，请重试');
        return false;
      } finally {
        setSending(false);
      }
    },
    [message],
  );

  /** 撤回自己的消息（服务端校验发送者与超时） */
  const recallMessage = useCallback(
    async (messageId: string) => {
      const id = activeIdRef.current;
      if (!id) {
        return;
      }
      try {
        await imApi.recall(id, messageId);
        setMessages((prev) =>
          prev.map((item) => (item.id === messageId ? { ...item, status: 0 } : item)),
        );
        message.success('消息已撤回');
      } catch (error) {
        logger.warn('消息撤回失败', error);
        message.error('撤回失败，可能已超过可撤回时间');
      }
    },
    [message],
  );

  /** 重新拉取当前会话成员 */
  const reloadMembers = useCallback(async () => {
    const id = activeIdRef.current;
    if (!id) {
      return;
    }
    try {
      setMembers(await imApi.listMembers(id));
    } catch (error) {
      logger.warn('成员列表加载失败', error);
    }
  }, []);

  const addMembers = useCallback(
    async (userIds: string[]) => {
      const id = activeIdRef.current;
      if (!id || userIds.length === 0) {
        return;
      }
      try {
        const added = await imApi.addMembers(id, userIds);
        setMembers((prev) => {
          const merged = new Map(prev.map((item) => [item.userId, item]));
          added.forEach((item) => merged.set(item.userId, item));
          return [...merged.values()];
        });
        message.success(`已添加 ${added.length} 位成员`);
      } catch (error) {
        logger.warn('添加成员失败', error);
        message.error('添加成员失败：仅群主/管理员可操作');
      }
    },
    [message],
  );

  const removeMember = useCallback(
    async (userId: string) => {
      const id = activeIdRef.current;
      if (!id) {
        return;
      }
      try {
        await imApi.removeMember(id, userId);
        setMembers((prev) => prev.filter((item) => item.userId !== userId));
        message.success('已移除成员');
      } catch (error) {
        logger.warn('移除成员失败', error);
        message.error('移除成员失败：仅群主/管理员可操作');
      }
    },
    [message],
  );

  const quitConversation = useCallback(async () => {
    const id = activeIdRef.current;
    if (!id) {
      return;
    }
    try {
      await imApi.quit(id);
      setConversations((prev) => prev.filter((item) => item.id !== id));
      setActiveId(null);
      activeIdRef.current = null;
      setMessages([]);
      setMembers([]);
      message.success('已退出群聊');
    } catch (error) {
      logger.warn('退出群聊失败', error);
      message.error('退出失败：群主不可退群');
    }
  }, [message]);

  /** 创建单聊/群聊并打开 */
  const createConversation = useCallback(
    async (payload: { type: 'SINGLE' | 'GROUP'; title?: string; memberIds: number[] }) => {
      try {
        const created = await imApi.createConversation(payload);
        setConversations((prev) =>
          sortConversations([...prev.filter((c) => c.id !== created.id), created]),
        );
        await openConversation(created.id);
        return true;
      } catch (error) {
        logger.warn('创建会话失败', error);
        message.error('创建会话失败，请检查成员ID');
        return false;
      }
    },
    [message, openConversation],
  );

  /** 打开「蓝鲸助手」会话（幂等） */
  const openBotConversation = useCallback(async () => {
    try {
      const bot = await imApi.openBotConversation();
      setConversations((prev) => sortConversations([...prev.filter((c) => c.id !== bot.id), bot]));
      await openConversation(bot.id);
    } catch (error) {
      logger.warn('打开机器人会话失败', error);
      message.error('机器人会话不可用，请确认已开通权限');
    }
  }, [message, openConversation]);

  /** 单条消息上屏 + 会话摘要/未读联动 */
  const applyIncomingMessage = useCallback(
    (incoming: MessageVO) => {
      const active = activeIdRef.current;
      if (incoming.conversationId === active) {
        setMessages((prev) => mergeMessages(prev, [incoming]));
        if (incoming.senderId !== currentUserId) {
          imApi.markRead(incoming.conversationId, incoming.seq).catch((error) => {
            logger.warn('已读上报失败', error);
          });
        }
      }
      if (!conversationsRef.current.some((item) => item.id === incoming.conversationId)) {
        void loadConversations();
        return;
      }
      setConversations((prev) =>
        sortConversations(
          prev.map((item) =>
            item.id === incoming.conversationId
              ? {
                  ...item,
                  lastSeq: Math.max(item.lastSeq ?? 0, incoming.seq),
                  lastMessage: incoming,
                  lastActiveAt: incoming.createTime,
                  unread:
                    incoming.senderId === currentUserId || incoming.conversationId === active
                      ? 0
                      : (item.unread ?? 0) + 1,
                }
              : item,
          ),
        ),
      );
    },
    [currentUserId, loadConversations],
  );

  /** WS 帧入口 */
  const handleFrame = useCallback(
    (frame: ImFrame) => {
      switch (frame.type) {
        case 'message.new': {
          const incoming = frame.message as MessageVO | undefined;
          if (!incoming) {
            return;
          }
          applyIncomingMessage(incoming);
          // 真实消息到达后移除对应流式占位
          setStreaming((prev) => prev.filter((entry) => entry.messageId !== incoming.id));
          break;
        }
        case 'message.recalled': {
          const conversationId = String(frame.conversationId ?? '');
          const messageId = String(frame.messageId ?? '');
          if (conversationId === activeIdRef.current) {
            setMessages((prev) =>
              prev.map((item) => (item.id === messageId ? { ...item, status: 0 } : item)),
            );
          }
          break;
        }
        case 'agent.meta': {
          const runId = String(frame.runId ?? '');
          if (!runId) {
            return;
          }
          const entry: StreamEntry = {
            runId,
            conversationId: String(frame.conversationId ?? ''),
            text: '',
            intent: typeof frame.intent === 'string' ? frame.intent : undefined,
            degraded: Boolean(frame.degraded),
            done: false,
          };
          setStreaming((prev) =>
            prev.some((item) => item.runId === runId) ? prev : [...prev, entry],
          );
          break;
        }
        case 'agent.delta': {
          const runId = String(frame.runId ?? '');
          const text = typeof frame.text === 'string' ? frame.text : '';
          if (!runId || !text) {
            return;
          }
          setStreaming((prev) =>
            prev.map((item) => (item.runId === runId ? { ...item, text: item.text + text } : item)),
          );
          break;
        }
        case 'agent.done': {
          const runId = String(frame.runId ?? '');
          const messageId =
            frame.messageId === null || frame.messageId === undefined
              ? undefined
              : String(frame.messageId);
          if (!runId) {
            return;
          }
          setStreaming((prev) =>
            prev.map((item) =>
              item.runId === runId
                ? {
                    ...item,
                    done: true,
                    messageId,
                    tokens: typeof frame.tokens === 'number' ? frame.tokens : undefined,
                    elapsedMillis:
                      typeof frame.elapsedMillis === 'number' ? frame.elapsedMillis : undefined,
                    degraded: item.degraded || Boolean(frame.degraded),
                  }
                : item,
            ),
          );
          if (messageId) {
            setRunByMessageId((prev) => ({ ...prev, [messageId]: runId }));
            // 兜底：message.new 未到达时，3s 后移除占位并补拉
            const timer = window.setTimeout(() => {
              fallbackTimersRef.current.delete(timer);
              setStreaming((prev) => prev.filter((item) => !(item.runId === runId && item.done)));
            }, STREAM_REPLACE_FALLBACK_MS);
            fallbackTimersRef.current.add(timer);
          }
          break;
        }
        case 'agent.error': {
          const runId = String(frame.runId ?? '');
          if (!runId) {
            return;
          }
          setStreaming((prev) =>
            prev.map((item) =>
              item.runId === runId
                ? {
                    ...item,
                    done: true,
                    error: typeof frame.message === 'string' ? frame.message : '智能体执行失败',
                  }
                : item,
            ),
          );
          break;
        }
        case 'agent.confirm': {
          const runId = String(frame.runId ?? '');
          const cardMessageId =
            frame.messageId === null || frame.messageId === undefined
              ? undefined
              : String(frame.messageId);
          if (cardMessageId) {
            setRunByMessageId((prev) => ({ ...prev, [cardMessageId]: runId }));
          }
          setStreaming((prev) => prev.filter((item) => item.runId !== runId));
          break;
        }
        case 'member.changed': {
          const conversationId = String(frame.conversationId ?? '');
          if (!conversationId) {
            return;
          }
          if (frame.action === 'DISSOLVED') {
            if (conversationId === activeIdRef.current) {
              setActiveId(null);
              activeIdRef.current = null;
              setMessages([]);
              setMembers([]);
            }
            setConversations((prev) => prev.filter((item) => item.id !== conversationId));
            message.warning('该群聊已解散');
            return;
          }
          if (conversationId === activeIdRef.current) {
            void reloadMembers();
          }
          void loadConversations();
          break;
        }
        default:
          break;
      }
    },
    [applyIncomingMessage, loadConversations, message, reloadMembers],
  );

  /**
   * 历史机器人消息按 messageId 懒加载运行ID（工具轨迹与反馈入口）。
   *
   * 命中缓存直接返回；未命中调用后端定位并写回缓存，失败返回 undefined。
   */
  const resolveRunId = useCallback(async (messageId: string): Promise<string | undefined> => {
    const known = runByMessageIdRef.current[messageId];
    if (known) {
      return known;
    }
    try {
      const found = await imApi.getRunByMessage(messageId);
      if (found?.runId) {
        setRunByMessageId((prev) => ({ ...prev, [messageId]: found.runId }));
        return found.runId;
      }
    } catch (error) {
      logger.warn('运行记录定位失败', error);
    }
    return undefined;
  }, []);

  /** 重连后：刷新会话列表 + 按 lastSeq 补拉当前会话 */
  const refreshAfterReconnect = useCallback(async () => {
    await loadConversations();
    const id = activeIdRef.current;
    if (!id) {
      return;
    }
    const lastSeq = messagesRef.current[messagesRef.current.length - 1]?.seq ?? 0;
    try {
      const page = await imApi.listMessages(id, { afterSeq: lastSeq, limit: 100 });
      if (page.records.length > 0) {
        setMessages((prev) => mergeMessages(prev, page.records));
      }
    } catch (error) {
      logger.warn('断线补拉失败', error);
    }
  }, [loadConversations]);

  /** 进行中的流式占位（仅当前会话） */
  const activeStreaming = useMemo(
    () => streaming.filter((item) => item.conversationId === activeId),
    [streaming, activeId],
  );

  return {
    currentUserId,
    conversations,
    activeId,
    activeConversation: conversations.find((item) => item.id === activeId) ?? null,
    messages,
    hasMore,
    loadingMessages,
    members,
    streaming: activeStreaming,
    runByMessageId,
    sending,
    loadConversations,
    openConversation,
    loadMore,
    sendMessage,
    recallMessage,
    reloadMembers,
    addMembers,
    removeMember,
    quitConversation,
    createConversation,
    openBotConversation,
    handleFrame,
    resolveRunId,
    refreshAfterReconnect,
  };
}

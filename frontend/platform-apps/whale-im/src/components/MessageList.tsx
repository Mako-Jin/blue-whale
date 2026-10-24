// 消息流：日期分隔、引用跳转、上滑加载更早、机器人流式占位

import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Avatar, Empty, Spin, Tag, Typography } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import type { ConversationVO, MessageVO } from '../api/im';
import type { StreamEntry } from '../hooks/useImData';
import { formatDay, formatTime } from '../utils/format';
import { renderMarkdown } from '../utils/markdown';
import MessageItem from './MessageItem';

const { Text } = Typography;

interface MessageListProps {
  conversation: ConversationVO;
  messages: MessageVO[];
  streaming: StreamEntry[];
  currentUserId: string | null;
  loading: boolean;
  hasMore: boolean;
  runByMessageId: Record<string, string>;
  onResolveRunId: (messageId: string) => Promise<string | undefined>;
  onLoadMore: () => void;
  onRecall: (messageId: string) => void;
}

/** 发送者显示名：机器人/系统固定，其余按用户ID兜底 */
function senderName(message: MessageVO, conversation: ConversationVO, currentUserId: string | null): string {
  if (message.senderType === 'BOT') {
    return conversation.type === 'BOT' ? conversation.title || '蓝鲸助手' : '蓝鲸助手';
  }
  if (message.senderType === 'SYSTEM') {
    return '系统';
  }
  if (message.senderId === currentUserId) {
    return '我';
  }
  return `用户 ${message.senderId}`;
}

export default function MessageList({
  conversation,
  messages,
  streaming,
  currentUserId,
  loading,
  hasMore,
  runByMessageId,
  onResolveRunId,
  onLoadMore,
  onRecall,
}: MessageListProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastCountRef = useRef(0);
  const streamLength = streaming.reduce((total, item) => total + item.text.length, 0);

  const messageById = useMemo(() => {
    const map = new Map<string, MessageVO>();
    messages.forEach((item) => map.set(item.id, item));
    return map;
  }, [messages]);

  // 新消息/流式增量到达时滚动到底部
  useEffect(() => {
    const node = scrollRef.current;
    if (!node) {
      return;
    }
    if (messages.length !== lastCountRef.current || streamLength > 0) {
      lastCountRef.current = messages.length;
      node.scrollTop = node.scrollHeight;
    }
  }, [messages.length, streamLength]);

  const jumpTo = useCallback((messageId: string) => {
    const target = document.querySelector(`[data-message-id="${messageId}"]`);
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, []);

  const handleScroll = useCallback(() => {
    const node = scrollRef.current;
    if (!node || node.scrollTop > 40 || !hasMore) {
      return;
    }
    onLoadMore();
  }, [hasMore, onLoadMore]);

  return (
    <div className="im-messages" ref={scrollRef} onScroll={handleScroll}>
      {loading && (
        <div style={{ textAlign: 'center', padding: 8 }}>
          <Spin size="small" />
        </div>
      )}
      {hasMore && !loading && (
        <div style={{ textAlign: 'center', padding: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            上滑加载更早消息
          </Text>
        </div>
      )}
      {messages.length === 0 && streaming.length === 0 && !loading ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有消息，打个招呼吧" />
      ) : (
        messages.map((message, index) => {
          const previous = messages[index - 1];
          const day = formatDay(message.createTime);
          const showDay = !previous || formatDay(previous.createTime) !== day;
          const replied = message.replyTo ? messageById.get(message.replyTo) ?? null : null;
          return (
            <div key={message.id}>
              {showDay && <div className="im-day-divider">{day}</div>}
              <MessageItem
                message={message}
                isMe={message.senderId === currentUserId && message.senderType !== 'BOT'}
                senderName={senderName(message, conversation, currentUserId)}
                replied={replied}
                runId={runByMessageId[message.id]}
                onResolveRunId={onResolveRunId}
                onRecall={onRecall}
                onJumpTo={jumpTo}
              />
            </div>
          );
        })
      )}
      {streaming.map((entry) => (
        <div key={`stream-${entry.runId}`} className="im-message is-bot">
          <Avatar size="small" icon={<RobotOutlined />} style={{ backgroundColor: '#722ed1', flex: 'none' }} />
          <div className="im-message-body">
            <div className="im-message-meta">
              <span>蓝鲸助手</span>
              <span>{formatTime(new Date().toISOString())}</span>
              <Tag color="purple">流式回答</Tag>
              {entry.degraded && <Tag color="orange">降级</Tag>}
            </div>
            <div className={`im-bubble${entry.done ? '' : ' im-stream-cursor'}`}>
              {entry.error ? (
                <Text type="danger">{entry.error}</Text>
              ) : entry.text ? (
                renderMarkdown(entry.text)
              ) : (
                <Text type="secondary">思考中…</Text>
              )}
            </div>
            {entry.done && !entry.error && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                共 {entry.tokens ?? 0} tokens，耗时 {entry.elapsedMillis ?? 0} ms
              </Text>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

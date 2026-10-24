// 左侧会话列表：搜索、未读红点、最后消息摘要、新建与机器人入口

import { useMemo, useState } from 'react';
import { Avatar, Badge, Button, Empty, Input, Space, Typography } from 'antd';
import { PlusOutlined, RobotOutlined, WechatOutlined } from '@ant-design/icons';
import type { ConversationVO } from '../api/im';
import { formatTime, messageSummary } from '../utils/format';

const { Text } = Typography;

interface ConversationListProps {
  conversations: ConversationVO[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onOpenBot: () => void;
}

/** 会话头像：BOT 用机器人图标，其余用标题首字 */
function ConversationAvatar({ conversation }: { conversation: ConversationVO }) {
  if (conversation.type === 'BOT') {
    return <Avatar icon={<RobotOutlined />} style={{ backgroundColor: '#722ed1', flex: 'none' }} />;
  }
  if (conversation.type === 'GROUP') {
    return <Avatar icon={<WechatOutlined />} style={{ backgroundColor: '#1677ff', flex: 'none' }} />;
  }
  return <Avatar style={{ backgroundColor: '#13c2c2', flex: 'none' }}>{conversation.title?.slice(0, 1) ?? '?'}</Avatar>;
}

export default function ConversationList({
  conversations,
  activeId,
  onSelect,
  onCreate,
  onOpenBot,
}: ConversationListProps) {
  const [keyword, setKeyword] = useState('');

  const filtered = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) {
      return conversations;
    }
    return conversations.filter((item) => item.title?.toLowerCase().includes(text));
  }, [conversations, keyword]);

  return (
    <div className="im-conversations">
      <div className="im-conversations-search">
        <Input
          allowClear
          size="small"
          placeholder="搜索会话"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
        <Space size={4}>
          <Button size="small" icon={<PlusOutlined />} aria-label="新建会话" onClick={onCreate} />
          <Button size="small" icon={<RobotOutlined />} aria-label="蓝鲸助手" onClick={onOpenBot} />
        </Space>
      </div>
      <div className="im-conversations-list">
        {filtered.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话" style={{ marginTop: 32 }} />
        ) : (
          filtered.map((conversation) => (
            <div
              key={conversation.id}
              className={`im-conversation-item${conversation.id === activeId ? ' is-active' : ''}`}
              onClick={() => onSelect(conversation.id)}
              role="button"
              tabIndex={0}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  onSelect(conversation.id);
                }
              }}
            >
              <ConversationAvatar conversation={conversation} />
              <div className="im-conversation-main">
                <div className="im-conversation-title">
                  <Text ellipsis style={{ maxWidth: 150 }}>
                    {conversation.title || '未命名会话'}
                  </Text>
                  <span>{formatTime(conversation.lastActiveAt)}</span>
                </div>
                <div className="im-conversation-summary">
                  {messageSummary(conversation.lastMessage)}
                </div>
              </div>
              <Badge
                count={conversation.unread}
                size="small"
                overflowCount={99}
                style={{ flex: 'none', marginTop: 18 }}
              />
            </div>
          ))
        )}
      </div>
    </div>
  );
}

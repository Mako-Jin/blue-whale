// IM 子应用根组件：左侧会话 / 中间消息流 / 右侧成员与机器人信息

import { useEffect, useMemo, useState } from 'react';
import { Badge, Space, Tag, Typography } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { PermissionProvider } from '@bluewhale/whale-ui';
import { getPermissions, getCurrentUserId } from './api/token';
import { useImData } from './hooks/useImData';
import { useImSocket } from './hooks/useImSocket';
import ConversationList from './components/ConversationList';
import MessageList from './components/MessageList';
import Composer from './components/Composer';
import MemberPanel from './components/MemberPanel';
import NewConversationModal from './components/NewConversationModal';

const { Text } = Typography;

const TYPE_LABEL: Record<string, string> = {
  SINGLE: '单聊',
  GROUP: '群聊',
  BOT: '机器人',
};

const SOCKET_LABEL: Record<string, { color: string; text: string }> = {
  connecting: { color: 'orange', text: '连接中' },
  open: { color: 'green', text: '实时连接' },
  closed: { color: 'red', text: '已断开' },
};

export default function App() {
  const permissions = useMemo(() => getPermissions(), []);
  const currentUserId = useMemo(() => getCurrentUserId(), []);
  const im = useImData();
  const { loadConversations } = im;
  const socketState = useImSocket(im.handleFrame, im.refreshAfterReconnect);
  const [newOpen, setNewOpen] = useState(false);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  const active = im.activeConversation;
  const socket = SOCKET_LABEL[socketState] ?? SOCKET_LABEL.closed;

  return (
    <PermissionProvider permissions={permissions}>
      <div className="im-root">
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            padding: '10px 16px',
            borderBottom: '1px solid var(--bw-color-border, #f0f0f0)',
          }}
        >
          <Space size={8}>
            <RobotOutlined style={{ color: '#722ed1' }} />
            <Text strong>消息中心</Text>
            <Badge
              status={
                socketState === 'open'
                  ? 'success'
                  : socketState === 'connecting'
                    ? 'processing'
                    : 'error'
              }
            />
            <Tag color={socket.color}>{socket.text}</Tag>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {currentUserId ? `当前用户 ${currentUserId}` : '未识别登录态'}
          </Text>
        </div>

        <div className="im-body">
          <ConversationList
            conversations={im.conversations}
            activeId={im.activeId}
            onSelect={(id) => {
              void im.openConversation(id);
            }}
            onCreate={() => setNewOpen(true)}
            onOpenBot={() => {
              void im.openBotConversation();
            }}
          />

          {active ? (
            <div className="im-chat">
              <div className="im-chat-header">
                <Space size={8}>
                  <Text strong>{active.title || '未命名会话'}</Text>
                  <Tag
                    color={
                      active.type === 'BOT' ? 'purple' : active.type === 'GROUP' ? 'blue' : 'cyan'
                    }
                  >
                    {TYPE_LABEL[active.type] ?? active.type}
                  </Tag>
                </Space>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  未读 {active.unread ?? 0}
                </Text>
              </div>
              <MessageList
                conversation={active}
                messages={im.messages}
                streaming={im.streaming}
                currentUserId={currentUserId}
                loading={im.loadingMessages}
                hasMore={im.hasMore}
                runByMessageId={im.runByMessageId}
                onResolveRunId={im.resolveRunId}
                onLoadMore={() => {
                  void im.loadMore();
                }}
                onRecall={(messageId) => {
                  void im.recallMessage(messageId);
                }}
              />
              <Composer
                disabled={false}
                sending={im.sending}
                onSend={(contentType, content) => im.sendMessage(contentType, content)}
              />
            </div>
          ) : (
            <div className="im-empty">
              <Space direction="vertical" align="center">
                <RobotOutlined style={{ fontSize: 36, color: '#bfbfbf' }} />
                <Text type="secondary">
                  选择左侧会话开始聊天，或点击机器人图标向「蓝鲸助手」提问
                </Text>
              </Space>
            </div>
          )}

          {active && (
            <MemberPanel
              conversation={active}
              members={im.members}
              currentUserId={currentUserId}
              onAddMembers={im.addMembers}
              onRemoveMember={im.removeMember}
              onQuit={im.quitConversation}
            />
          )}
        </div>
      </div>

      <NewConversationModal
        open={newOpen}
        onCancel={() => setNewOpen(false)}
        onCreate={im.createConversation}
      />
    </PermissionProvider>
  );
}

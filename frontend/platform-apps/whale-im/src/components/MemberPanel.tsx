// 右侧成员/机器人信息：角色标签、添加/移除成员、退出群聊

import { useState } from 'react';
import { Avatar, Button, Card, Empty, Input, List, Popconfirm, Space, Tag, Typography } from 'antd';
import {
  DeleteOutlined,
  LogoutOutlined,
  PlusOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ConversationVO, MemberVO } from '../api/im';

const { Text } = Typography;

interface MemberPanelProps {
  conversation: ConversationVO;
  members: MemberVO[];
  currentUserId: string | null;
  onAddMembers: (userIds: string[]) => Promise<void>;
  onRemoveMember: (userId: string) => Promise<void>;
  onQuit: () => Promise<void>;
}

const ROLE_LABEL: Record<string, string> = {
  OWNER: '群主',
  ADMIN: '管理员',
  MEMBER: '成员',
};

function parseUserIds(text: string): string[] {
  return text
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter((item) => /^\d+$/.test(item));
}

export default function MemberPanel({
  conversation,
  members,
  currentUserId,
  onAddMembers,
  onRemoveMember,
  onQuit,
}: MemberPanelProps) {
  const [adding, setAdding] = useState(false);
  const [userInput, setUserInput] = useState('');

  const me = members.find((item) => item.userId === currentUserId);
  const canManage = conversation.type === 'GROUP' && (me?.role === 'OWNER' || me?.role === 'ADMIN');
  const isOwner = me?.role === 'OWNER';

  const handleAdd = async () => {
    const ids = parseUserIds(userInput);
    if (ids.length === 0) {
      return;
    }
    await onAddMembers(ids);
    setUserInput('');
    setAdding(false);
  };

  if (conversation.type === 'BOT') {
    return (
      <div className="im-members">
        <Card size="small" title="机器人信息">
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Space>
              <Avatar icon={<RobotOutlined />} style={{ backgroundColor: '#722ed1' }} />
              <div>
                <Text strong>{conversation.title || '蓝鲸助手'}</Text>
                <div>
                  <Tag color="purple">智能体</Tag>
                  <Tag>平台问答</Tag>
                </div>
              </div>
            </Space>
            <Text type="secondary" style={{ fontSize: 12 }}>
              可直接提问平台架构、技术实现与数据口径；写操作会先发送确认卡片，确认后才执行。
            </Text>
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                最近活跃：{conversation.lastActiveAt || '—'}
              </Text>
            </div>
          </Space>
        </Card>
        <Card size="small" title="成员" style={{ marginTop: 12 }}>
          <List
            size="small"
            dataSource={members}
            locale={{ emptyText: '暂无成员' }}
            renderItem={(member) => (
              <List.Item>
                <Space>
                  <Avatar size="small" icon={<RobotOutlined />} />
                  <Text>{member.memberType === 'BOT' ? '蓝鲸助手' : `用户 ${member.userId}`}</Text>
                </Space>
              </List.Item>
            )}
          />
        </Card>
      </div>
    );
  }

  return (
    <div className="im-members">
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        <Space style={{ justifyContent: 'space-between', width: '100%' }}>
          <Text strong>成员（{members.length}）</Text>
          {canManage && (
            <Button
              size="small"
              type="link"
              icon={<PlusOutlined />}
              onClick={() => setAdding((prev) => !prev)}
            >
              添加
            </Button>
          )}
        </Space>
        {adding && canManage && (
          <Space.Compact style={{ width: '100%' }}>
            <Input
              size="small"
              placeholder="用户ID，逗号分隔"
              value={userInput}
              onChange={(event) => setUserInput(event.target.value)}
            />
            <Button size="small" type="primary" onClick={() => { void handleAdd(); }}>
              确定
            </Button>
          </Space.Compact>
        )}
        {members.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无成员" />
        ) : (
          <List
            size="small"
            dataSource={members}
            renderItem={(member) => (
              <List.Item
                actions={
                  canManage && member.memberType !== 'BOT' && member.userId !== currentUserId
                    ? [
                        <Popconfirm
                          key="remove"
                          title="移除该成员？"
                          okText="移除"
                          cancelText="取消"
                          onConfirm={() => {
                            void onRemoveMember(member.userId);
                          }}
                        >
                          <Button size="small" type="text" danger icon={<DeleteOutlined />} aria-label="移除成员" />
                        </Popconfirm>,
                      ]
                    : undefined
                }
              >
                <Space>
                  <Avatar
                    size="small"
                    icon={member.memberType === 'BOT' ? <RobotOutlined /> : <UserOutlined />}
                  />
                  <div>
                    <Text>{member.userId === currentUserId ? `我（${member.userId}）` : member.userId}</Text>
                    <div>
                      <Tag color={member.role === 'OWNER' ? 'gold' : member.role === 'ADMIN' ? 'blue' : 'default'}>
                        {ROLE_LABEL[member.role] ?? member.role}
                      </Tag>
                      {member.memberType === 'BOT' && <Tag color="purple">机器人</Tag>}
                    </div>
                  </div>
                </Space>
              </List.Item>
            )}
          />
        )}
        {conversation.type === 'GROUP' && !isOwner && (
          <Popconfirm
            title="退出该群聊？"
            okText="退出"
            cancelText="取消"
            onConfirm={() => {
              void onQuit();
            }}
          >
            <Button block danger size="small" icon={<LogoutOutlined />}>
              退出群聊
            </Button>
          </Popconfirm>
        )}
      </Space>
    </div>
  );
}

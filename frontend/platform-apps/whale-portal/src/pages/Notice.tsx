// 消息通知：优先展示告警记录（sys:alert:record:list），否则展示 IM 会话未读（sys:im:chat）
// 两者均无权限时展示空态；接口异常降级为「暂无消息通知」，不阻塞页面。
import { useEffect, useState } from 'react';
import { Badge, Card, Empty, List, Spin, Tag } from 'antd';
import { hasPermission } from '../api/client';
import {
  listConversations,
  pageAlertRecords,
  type AlertRecord,
  type Conversation,
} from '../api/notice';

/** 告警级别颜色 */
const LEVEL_COLOR: Record<string, string> = {
  INFO: 'blue',
  WARN: 'orange',
  ERROR: 'red',
  CRITICAL: 'magenta',
};

/** 告警状态文案 */
const ALERT_STATUS_LABEL: Record<string, string> = {
  FIRING: '触发中',
  RECOVERED: '已恢复',
};

export default function Notice() {
  const [alerts, setAlerts] = useState<AlertRecord[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [mode, setMode] = useState<'alert' | 'im' | 'none'>('none');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (hasPermission('sys:alert:record:list')) {
      setMode('alert');
      pageAlertRecords({ pageSize: 50 })
        .then((page) => setAlerts(page.records))
        .catch(() => setAlerts([]))
        .finally(() => setLoading(false));
      return;
    }
    if (hasPermission('sys:im:chat')) {
      setMode('im');
      listConversations()
        .then(setConversations)
        .catch(() => setConversations([]))
        .finally(() => setLoading(false));
      return;
    }
    setLoading(false);
  }, []);

  if (loading) {
    return (
      <Card title="消息通知">
        <Spin />
      </Card>
    );
  }

  if (mode === 'alert') {
    return (
      <Card title="消息通知">
        <List
          dataSource={alerts}
          locale={{ emptyText: <Empty description="暂无告警通知" /> }}
          renderItem={(item) => (
            <List.Item
              extra={
                <Tag color={LEVEL_COLOR[item.alertLevel ?? ''] || 'default'}>
                  {item.alertLevel ?? '-'} · {ALERT_STATUS_LABEL[item.status ?? ''] ?? item.status}
                </Tag>
              }
            >
              <List.Item.Meta
                title={item.content ?? item.ruleCode ?? `告警 #${item.id}`}
                description={item.triggerTime ?? item.createTime ?? '-'}
              />
            </List.Item>
          )}
        />
      </Card>
    );
  }

  if (mode === 'im') {
    return (
      <Card title="消息通知">
        <List
          dataSource={conversations}
          locale={{ emptyText: <Empty description="暂无 IM 消息" /> }}
          renderItem={(item) => (
            <List.Item extra={<Badge count={item.unread ?? 0} size="small" />}>
              <List.Item.Meta
                title={item.title ?? item.id}
                description={item.lastMessage?.content ?? item.lastActiveAt ?? '暂无消息'}
              />
            </List.Item>
          )}
        />
      </Card>
    );
  }

  return (
    <Card title="消息通知">
      <Empty description="当前账号暂无消息中心读取权限" />
    </Card>
  );
}

// 消息中心：本人站内信收件箱（分页列表 + 未读/全部过滤 + 标记已读）
// 接口异常降级为空态，不阻塞页面；标记已读后广播事件供导航角标刷新。
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, List, Pagination, Radio, Space, Tag } from 'antd';
import {
  markInAppRead,
  pageInAppMessages,
  type InAppMessage,
} from '../api/inapp';

/** 标记已读后广播：App.tsx 监听以刷新导航未读角标 */
export const INAPP_READ_EVENT = 'whale-inapp-read';

const PAGE_SIZE = 10;

export default function Inbox() {
  const [readFilter, setReadFilter] = useState<'unread' | 'all'>('unread');
  const [messages, setMessages] = useState<InAppMessage[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(true);
  const [markingId, setMarkingId] = useState<string | null>(null);

  const load = useCallback(
    (page: number, filter: 'unread' | 'all') => {
      setLoading(true);
      pageInAppMessages({
        current: page,
        size: PAGE_SIZE,
        readFlag: filter === 'unread' ? 0 : undefined,
      })
        .then((result) => {
          setMessages(result.records);
          setTotal(result.total);
          setCurrent(result.current);
        })
        .catch(() => {
          setMessages([]);
          setTotal(0);
        })
        .finally(() => setLoading(false));
    },
    [],
  );

  useEffect(() => {
    load(1, readFilter);
  }, [load, readFilter]);

  const handleMarkRead = (item: InAppMessage) => {
    setMarkingId(item.messageId);
    markInAppRead(item.messageId)
      .then(() => {
        // 通知导航角标刷新（App.tsx 监听）
        window.dispatchEvent(new CustomEvent(INAPP_READ_EVENT));
        load(current, readFilter);
      })
      .catch(() => {
        // 保持原状态，不打断浏览
      })
      .finally(() => setMarkingId(null));
  };

  return (
    <Card
      title="消息中心"
      extra={
        <Radio.Group
          value={readFilter}
          optionType="button"
          buttonStyle="solid"
          options={[
            { value: 'unread', label: '未读' },
            { value: 'all', label: '全部' },
          ]}
          onChange={(event) => setReadFilter(event.target.value)}
        />
      }
    >
      <List
        loading={loading}
        dataSource={messages}
        locale={{ emptyText: <Empty description={readFilter === 'unread' ? '暂无未读消息' : '暂无消息'} /> }}
        renderItem={(item) => (
          <List.Item
            actions={
              item.readFlag === 0
                ? [
                    <Button
                      key="read"
                      type="link"
                      size="small"
                      loading={markingId === item.messageId}
                      onClick={() => handleMarkRead(item)}
                    >
                      标记已读
                    </Button>,
                  ]
                : undefined
            }
          >
            <List.Item.Meta
              title={
                <Space>
                  {item.readFlag === 0 ? <Badge unread /> : null}
                  <span>{item.title ?? `消息 #${item.messageId}`}</span>
                  {item.bizType ? <Tag>{item.bizType}</Tag> : null}
                </Space>
              }
              description={
                <Space direction="vertical" size={2}>
                  {item.content ? <span>{item.content}</span> : null}
                  <span style={{ color: 'rgba(0,0,0,0.45)' }}>{item.sentAt ?? item.createTime ?? '-'}</span>
                </Space>
              }
            />
          </List.Item>
        )}
      />
      <Pagination
        style={{ marginTop: 16, textAlign: 'right' }}
        current={current}
        pageSize={PAGE_SIZE}
        total={total}
        showSizeChanger={false}
        showTotal={(count) => `共 ${count} 条`}
        onChange={(page) => load(page, readFilter)}
      />
    </Card>
  );
}

/** 未读圆点标记 */
function Badge({ unread }: { unread: boolean }) {
  if (!unread) {
    return null;
  }
  return <Tag color="red">未读</Tag>;
}

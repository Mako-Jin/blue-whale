// 热榜页：只读快照（服务端每 30 分钟刷新）
//
// 快照语义是「这一轮大家在看什么」：排名与计数都取快照时点，不随实时变化，
// 所以这里不做轮询，只在手动刷新后重新拉取。

import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, List, Space, Spin, Tag, Typography, message } from 'antd';
import { fetchHot, refreshHot, type HotItem } from '../api/forum';
import { hasPermission } from '../api/client';

const { Text } = Typography;

interface Props {
  onOpenThread: (threadId: string) => void;
}

export default function HotPage({ onOpenThread }: Props) {
  const [items, setItems] = useState<HotItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    fetchHot(20)
      .then((data) => {
        setItems(data);
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '加载热榜失败'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = async () => {
    setRefreshing(true);
    try {
      const rows = await refreshHot();
      void message.success(`热榜已刷新（${rows} 条）`);
      load();
    } catch (err: unknown) {
      void message.error(err instanceof Error ? err.message : '刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const canRefresh = hasPermission('sys:forum:hot:refresh');

  return (
    <div className="forum-page">
      <Card
        title="鲸榜 · 近 7 日热议"
        extra={
          canRefresh ? (
            <Button loading={refreshing} onClick={refresh}>
              刷新快照
            </Button>
          ) : null
        }
        bordered={false}
      >
        {error ? <Alert type="error" showIcon message="热榜加载失败" description={error} /> : null}
        {loading ? (
          <div className="forum-loading">
            <Spin tip="正在读取热榜…" />
          </div>
        ) : (
          <List
            dataSource={items}
            locale={{ emptyText: <Empty description="近 7 天还没有上榜的帖子" /> }}
            renderItem={(item) => (
              <List.Item>
                <Space size={12} align="start" style={{ width: '100%' }}>
                  <Tag color={item.rankNo <= 3 ? 'gold' : undefined}>{item.rankNo}</Tag>
                  <div style={{ flex: 1 }}>
                    <Button type="link" style={{ padding: 0 }} onClick={() => onOpenThread(item.threadId)}>
                      {item.title}
                    </Button>
                    <div>
                      <Text type="secondary">
                        热度 {Number(item.score).toFixed(1)} · 回复 {item.replyCount} · 投花{' '}
                        {item.likeCount} · 浏览 {item.viewCount}
                      </Text>
                    </div>
                  </div>
                  <Text type="secondary">{item.snapshotTime ?? ''}</Text>
                </Space>
              </List.Item>
            )}
          />
        )}
      </Card>
    </div>
  );
}

// 我的收藏：按收藏时间倒序（服务端联查，已删/不可见帖不会出现在这里）

import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, List, Pagination, Space, Spin, Tag, Typography } from 'antd';
import { fetchMyCollections, type ThreadListItem } from '../api/forum';

const { Text } = Typography;

interface Props {
  onOpenThread: (threadId: string) => void;
}

export default function CollectionsPage({ onOpenThread }: Props) {
  const [items, setItems] = useState<ThreadListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback((page: number) => {
    setLoading(true);
    fetchMyCollections(page, 20)
      .then((result) => {
        setItems(result.records);
        setTotal(Number(result.total));
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '加载收藏失败'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(current);
  }, [current, load]);

  return (
    <div className="forum-page">
      <Card title={`我的收藏（${total}）`} bordered={false}>
        {error ? <Alert type="error" showIcon message="收藏加载失败" description={error} /> : null}
        {loading ? (
          <div className="forum-loading">
            <Spin tip="正在加载收藏…" />
          </div>
        ) : (
          <>
            <List
              dataSource={items}
              locale={{ emptyText: <Empty description="还没有收藏帖子（详情页点「藏帖」）" /> }}
              renderItem={(item) => (
                <List.Item>
                  <Space size={8} wrap>
                    {item.status === 'SOLVED' ? <Tag color="green">已解决</Tag> : null}
                    <Button type="link" style={{ padding: 0 }} onClick={() => onOpenThread(item.id)}>
                      {item.title}
                    </Button>
                    <Text type="secondary">
                      回复 {item.replyCount} · 浏览 {item.viewCount} · 投花 {item.likeCount}
                    </Text>
                  </Space>
                </List.Item>
              )}
            />
            {total > 20 ? (
              <Pagination
                style={{ marginTop: 12, textAlign: 'right' }}
                current={current}
                pageSize={20}
                total={total}
                showSizeChanger={false}
                onChange={setCurrent}
              />
            ) : null}
          </>
        )}
      </Card>
    </div>
  );
}

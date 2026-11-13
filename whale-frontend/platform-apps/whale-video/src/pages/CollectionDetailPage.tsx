// 片单详情（M4）：片单信息 + 片单内电影（按片单排序，仅展示当前可见电影）

import { App, Button, Card, Col, Empty, Result, Row, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchCollectionDetail, type MovieCollectionDetailView } from '../api/video';
import { hasPermission } from '../api/client';
import ContentCover from '../components/ContentCover';
import { formatCount, formatDateTime, formatDuration } from '../utils/videoDisplay';

export default function CollectionDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const collectionId = Number(params.id);

  const canBrowse = hasPermission('sys:video:browse');
  const [collection, setCollection] = useState<MovieCollectionDetailView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    if (!Number.isFinite(collectionId) || collectionId <= 0) {
      setError('片单不存在');
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchCollectionDetail(collectionId)
      .then((data) => {
        if (cancelled) {
          return;
        }
        setCollection(data);
        setError(null);
      })
      .catch((reason: unknown) => {
        if (cancelled) {
          return;
        }
        const text = reason instanceof Error ? reason.message : '片单加载失败';
        setError(text);
        message.error(text);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [canBrowse, collectionId, message]);

  if (!canBrowse) {
    return (
      <Result
        status="403"
        title="暂无浏览权限"
        subTitle="浏览片单需要 sys:video:browse 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        }
      />
    );
  }

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (error || !collection) {
    return (
      <Result
        status="404"
        title="片单不存在"
        subTitle={error ?? '该片单可能已下架或不可见'}
        extra={
          <Button type="primary" onClick={() => navigate('/movies')}>
            返回电影列表
          </Button>
        }
      />
    );
  }

  const items = collection.items ?? [];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Button onClick={() => navigate('/movies')}>返回电影</Button>
      </Space>

      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
          <div style={{ flex: '0 0 320px', minWidth: 240 }}>
            <ContentCover
              url={collection.coverUrl}
              title={collection.title}
              height={200}
              style={{ borderRadius: 0 }}
            />
          </div>
          <div style={{ flex: '1 1 320px', padding: 20 }}>
            <Typography.Title level={4} style={{ marginTop: 0 }}>
              {collection.title}
            </Typography.Title>
            <Space wrap size={6}>
              <Tag color={collection.publishState === 'PUBLISHED' ? 'green' : 'default'}>
                {collection.itemCount} 部电影
              </Tag>
              <Typography.Text type="secondary">
                上架时间：{formatDateTime(collection.publishedAt)}
              </Typography.Text>
            </Space>
            {collection.description ? (
              <Typography.Paragraph style={{ marginTop: 12 }}>
                {collection.description}
              </Typography.Paragraph>
            ) : null}
          </div>
        </div>
      </Card>

      <Card
        title="片单内电影"
        size="small"
        extra={<Typography.Text type="secondary">仅展示当前可见电影</Typography.Text>}
      >
        {items.length === 0 ? (
          <Empty description="该片单暂无可见电影" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Row gutter={[16, 16]}>
            {items.map((item) => (
              <Col key={item.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  size="small"
                  hoverable
                  onClick={() => navigate(`/movies/${item.id}`)}
                  styles={{ body: { padding: 0 } }}
                >
                  <ContentCover url={item.posterUrl} title={item.title} height={190} />
                  <div style={{ padding: '10px 12px' }}>
                    <Typography.Text strong ellipsis style={{ display: 'block' }}>
                      {item.title}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {[
                        item.region,
                        formatDuration(item.durationMs),
                        `${formatCount(item.playCount)} 次播放`,
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </Typography.Text>
                    <Space size={4} wrap style={{ marginTop: 6 }}>
                      {item.rating ? <Tag color="gold">{item.rating} 分</Tag> : null}
                      <Tag color={item.playable ? 'green' : 'default'}>
                        {item.playable ? '可播放' : '暂不可播'}
                      </Tag>
                    </Space>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Card>
    </Space>
  );
}

// 系列详情页（M3）：系列信息 + 已发布集数列表（按集数升序，仅展示已上架公开集）

import { App, Button, Card, Col, Empty, Result, Row, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchSeriesDetail, type SeriesDetailView } from '../api/video';
import { hasPermission } from '../api/client';
import ContentCover from '../components/ContentCover';
import { formatCount, formatDateTime, formatDuration } from '../utils/videoDisplay';

export default function SeriesDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const seriesId = Number(params.id);

  const canBrowse = hasPermission('sys:video:browse');
  const [series, setSeries] = useState<SeriesDetailView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    if (!Number.isFinite(seriesId) || seriesId <= 0) {
      setError('系列不存在');
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchSeriesDetail(seriesId)
      .then((data) => {
        if (cancelled) {
          return;
        }
        setSeries(data);
        setError(null);
      })
      .catch((reason: unknown) => {
        if (cancelled) {
          return;
        }
        const text = reason instanceof Error ? reason.message : '系列加载失败';
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
  }, [canBrowse, seriesId, message]);

  if (!canBrowse) {
    return (
      <Result
        status="403"
        title="暂无浏览权限"
        subTitle="浏览系列需要 sys:video:browse 权限，请联系租户管理员开通"
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

  if (error || !series) {
    return (
      <Result
        status="404"
        title="系列不存在"
        subTitle={error ?? '该系列可能已下架或不可见'}
        extra={
          <Button type="primary" onClick={() => navigate('/videos')}>
            返回视频列表
          </Button>
        }
      />
    );
  }

  const episodes = series.episodes ?? [];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Button onClick={() => navigate('/videos')}>返回视频</Button>
      </Space>

      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
          <div style={{ flex: '0 0 320px', minWidth: 240 }}>
            <ContentCover url={series.coverUrl} title={series.title} height={220} style={{ borderRadius: 0 }} />
          </div>
          <div style={{ flex: '1 1 320px', padding: 20 }}>
            <Space wrap align="center">
              <Typography.Title level={4} style={{ margin: 0 }}>
                {series.title}
              </Typography.Title>
              <Tag color="purple">{series.episodeCount} 集</Tag>
            </Space>
            {series.description ? (
              <Typography.Paragraph style={{ marginTop: 12 }}>{series.description}</Typography.Paragraph>
            ) : null}
            <Space wrap size={16}>
              <Typography.Text type="secondary">创作者：{series.creatorName || '-'}</Typography.Text>
              <Typography.Text type="secondary">上架时间：{formatDateTime(series.publishedAt)}</Typography.Text>
            </Space>
          </div>
        </div>
      </Card>

      <Card title="集数列表" size="small" extra={<Typography.Text type="secondary">仅展示已发布集数</Typography.Text>}>
        {episodes.length === 0 ? (
          <Empty description="该系列暂无已发布集数" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Row gutter={[16, 16]}>
            {episodes.map((episode) => (
              <Col key={episode.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  size="small"
                  hoverable
                  onClick={() => navigate(`/videos/${episode.id}`)}
                  styles={{ body: { padding: 0 } }}
                >
                  <ContentCover url={episode.coverUrl} title={episode.title} height={130} />
                  <div style={{ padding: '10px 12px' }}>
                    <Typography.Text strong ellipsis style={{ display: 'block' }}>
                      第 {episode.episodeNo || 1} 集 · {episode.title}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {formatDuration(episode.durationMs)} · {formatCount(episode.playCount)} 次播放
                    </Typography.Text>
                    <Tag style={{ marginTop: 6 }} color={episode.playable ? 'green' : 'default'}>
                      {episode.playable ? '可播放' : '暂不可播'}
                    </Tag>
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

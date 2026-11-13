// 电影详情（M4）：播放器 + 导演 / 演员 / 地区 / 语言 / 上映日期 / 评分 + 收录片单

import { Alert, App, Button, Card, Descriptions, Result, Space, Spin, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { favoriteContent, fetchMovieDetail, likeContent, type MovieDetailView } from '../api/video';
import { hasPermission } from '../api/client';
import ContentComments from '../components/ContentComments';
import ContentCover from '../components/ContentCover';
import ContentPlayer from '../player/ContentPlayer';
import { formatCount, formatDateTime, formatDuration } from '../utils/videoDisplay';

export default function MovieDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const movieId = Number(params.id);

  const [detail, setDetail] = useState<MovieDetailView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [commentsOpen, setCommentsOpen] = useState(false);

  const canLike = hasPermission('sys:video:like');
  const canFavorite = hasPermission('sys:video:favorite');
  const canComment = hasPermission('sys:video:comment:create');

  const load = useCallback(() => {
    if (!Number.isFinite(movieId) || movieId <= 0) {
      setError('电影不存在');
      setLoading(false);
      return;
    }
    setLoading(true);
    fetchMovieDetail(movieId)
      .then((data) => {
        setDetail(data);
        setError(null);
      })
      .catch((reason: unknown) =>
        setError(reason instanceof Error ? reason.message : '电影加载失败'),
      )
      .finally(() => setLoading(false));
  }, [movieId]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleLike = async () => {
    if (!detail) {
      return;
    }
    if (!canLike) {
      message.warning('暂无点赞权限');
      return;
    }
    try {
      await likeContent(detail.id, !detail.liked);
      setDetail({
        ...detail,
        liked: !detail.liked,
        likeCount: Math.max((detail.likeCount ?? 0) + (detail.liked ? -1 : 1), 0),
      });
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '点赞失败');
    }
  };

  const toggleFavorite = async () => {
    if (!detail) {
      return;
    }
    if (!canFavorite) {
      message.warning('暂无收藏权限');
      return;
    }
    try {
      await favoriteContent(detail.id, !detail.favorited);
      setDetail({
        ...detail,
        favorited: !detail.favorited,
        favoriteCount: Math.max((detail.favoriteCount ?? 0) + (detail.favorited ? -1 : 1), 0),
      });
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '收藏失败');
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (error || !detail) {
    return (
      <Result
        status="404"
        title="电影不存在"
        subTitle={error ?? '该电影可能已下架、授权到期或不可见'}
        extra={
          <Button type="primary" onClick={() => navigate('/movies')}>
            返回电影列表
          </Button>
        }
      />
    );
  }

  const collections = detail.collections ?? [];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Button onClick={() => navigate('/movies')}>返回电影</Button>
      </Space>

      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
          <div
            style={{ flex: '1 1 640px', minWidth: 320, aspectRatio: '16 / 9', background: '#000' }}
          >
            {detail.playable ? (
              <ContentPlayer contentId={detail.id} active fit="contain" />
            ) : (
              <div
                style={{
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#fff',
                }}
              >
                该电影暂不可播放（授权到期或已下架）
              </div>
            )}
          </div>
          <div style={{ flex: '0 1 360px', minWidth: 280, padding: 20 }}>
            <Typography.Title level={4} style={{ marginTop: 0 }}>
              {detail.title}
            </Typography.Title>
            {detail.originalTitle ? (
              <Typography.Text type="secondary">{detail.originalTitle}</Typography.Text>
            ) : null}
            <Space wrap size={6} style={{ marginTop: 12 }}>
              {detail.rating ? <Tag color="gold">{detail.rating} 分</Tag> : null}
              <Tag color={detail.playable ? 'green' : 'default'}>
                {detail.playable ? '可播放' : '暂不可播'}
              </Tag>
              {detail.tags?.map((tag) => (
                <Tag key={tag} color="blue">
                  {tag}
                </Tag>
              ))}
            </Space>
            {detail.description ? (
              <Typography.Paragraph style={{ marginTop: 12 }}>
                {detail.description}
              </Typography.Paragraph>
            ) : null}
            <Descriptions column={1} size="small" style={{ marginTop: 12 }}>
              <Descriptions.Item label="导演">{detail.director || '-'}</Descriptions.Item>
              <Descriptions.Item label="主演">{detail.actors || '-'}</Descriptions.Item>
              <Descriptions.Item label="地区 / 语言">
                {[detail.region, detail.language].filter(Boolean).join(' / ') || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="上映日期">{detail.releaseDate || '-'}</Descriptions.Item>
              <Descriptions.Item label="时长">
                {formatDuration(detail.durationMs)}
              </Descriptions.Item>
              <Descriptions.Item label="播放">{formatCount(detail.playCount)}</Descriptions.Item>
              <Descriptions.Item label="上架时间">
                {formatDateTime(detail.publishedAt)}
              </Descriptions.Item>
            </Descriptions>
            <Space wrap>
              <Button onClick={() => void toggleLike()}>
                {detail.liked ? '已点赞' : '点赞'} {formatCount(detail.likeCount)}
              </Button>
              <Button onClick={() => void toggleFavorite()}>
                {detail.favorited ? '已收藏' : '收藏'} {formatCount(detail.favoriteCount)}
              </Button>
              <Button
                onClick={() => {
                  if (!canComment) {
                    message.warning('暂无评论权限');
                    return;
                  }
                  setCommentsOpen(true);
                }}
              >
                评论 {formatCount(detail.commentCount)}
              </Button>
            </Space>
            {!detail.commentable ? (
              <Alert style={{ marginTop: 16 }} type="info" showIcon message="该电影暂不开放评论" />
            ) : null}
          </div>
        </div>
      </Card>

      {collections.length > 0 ? (
        <Card
          title="收录于片单"
          size="small"
          extra={<Typography.Text type="secondary">已上架片单</Typography.Text>}
        >
          <Space wrap size={10}>
            {collections.map((item) => (
              <div
                key={item.id}
                style={{
                  width: 200,
                  border: '1px solid #f0f0f0',
                  borderRadius: 8,
                  overflow: 'hidden',
                  cursor: 'pointer',
                  background: '#fff',
                }}
                onClick={() => navigate(`/collections/${item.id}`)}
              >
                <ContentCover url={item.coverUrl} title={item.title} height={100} />
                <div style={{ padding: '8px 10px' }}>
                  <Typography.Text strong ellipsis style={{ display: 'block', fontSize: 12 }}>
                    {item.title}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                    {item.itemCount} 部电影
                  </Typography.Text>
                </div>
              </div>
            ))}
          </Space>
        </Card>
      ) : null}

      <ContentComments
        contentId={detail.id}
        open={commentsOpen}
        commentable={detail.commentable}
        onClose={() => setCommentsOpen(false)}
      />
    </Space>
  );
}

// 短视频详情：播放器 + 互动（点赞 / 收藏 / 评论）+ 元信息

import { Alert, App, Button, Card, Descriptions, Result, Space, Spin, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  favoriteContent,
  fetchShortDetail,
  likeContent,
  type ContentDetailView,
} from '../api/video';
import { hasPermission } from '../api/client';
import ContentComments from '../components/ContentComments';
import ContentPlayer from '../player/ContentPlayer';
import { formatCount, formatDateTime, formatDuration } from '../utils/videoDisplay';

export default function ShortDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const contentId = Number(params.id);

  const [detail, setDetail] = useState<ContentDetailView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [commentsOpen, setCommentsOpen] = useState(false);

  const canLike = hasPermission('sys:video:like');
  const canFavorite = hasPermission('sys:video:favorite');
  const canComment = hasPermission('sys:video:comment:create');

  const load = useCallback(() => {
    if (!Number.isFinite(contentId) || contentId <= 0) {
      setError('内容不存在');
      setLoading(false);
      return;
    }
    setLoading(true);
    fetchShortDetail(contentId)
      .then((data) => {
        setDetail(data);
        setError(null);
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : '内容加载失败'))
      .finally(() => setLoading(false));
  }, [contentId]);

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
        title="内容不存在"
        subTitle={error ?? '该内容可能已下架或不可见'}
        extra={
          <Button type="primary" onClick={() => navigate('/shorts')}>
            返回短视频
          </Button>
        }
      />
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Button onClick={() => navigate('/shorts')}>返回短视频</Button>
      </Space>
      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
          <div style={{ flex: '1 1 420px', minWidth: 320, height: 480, background: '#000' }}>
            {detail.playable ? (
              <ContentPlayer contentId={detail.id} active />
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
                该内容暂不可播放
              </div>
            )}
          </div>
          <div style={{ flex: '1 1 360px', padding: 20 }}>
            <Typography.Title level={4} style={{ marginTop: 0 }}>
              {detail.title}
            </Typography.Title>
            {detail.subtitle ? <Typography.Text type="secondary">{detail.subtitle}</Typography.Text> : null}
            {detail.description ? (
              <Typography.Paragraph style={{ marginTop: 12 }}>{detail.description}</Typography.Paragraph>
            ) : null}
            {detail.tags?.length ? (
              <Space wrap style={{ marginBottom: 12 }}>
                {detail.tags.map((tag) => (
                  <Tag key={tag} color="blue">
                    {tag}
                  </Tag>
                ))}
              </Space>
            ) : null}
            <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="创作者">{detail.creatorName || '-'}</Descriptions.Item>
              <Descriptions.Item label="时长">{formatDuration(detail.durationMs)}</Descriptions.Item>
              <Descriptions.Item label="播放">{formatCount(detail.playCount)}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{formatDateTime(detail.publishedAt)}</Descriptions.Item>
            </Descriptions>
            <Space wrap>
              <Button onClick={() => void toggleLike()}>{detail.liked ? '已点赞' : '点赞'} {formatCount(detail.likeCount)}</Button>
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
              <Alert style={{ marginTop: 16 }} type="info" showIcon message="该内容暂不开放评论" />
            ) : null}
          </div>
        </div>
      </Card>
      <ContentComments
        contentId={detail.id}
        open={commentsOpen}
        commentable={detail.commentable}
        onClose={() => setCommentsOpen(false)}
      />
    </Space>
  );
}

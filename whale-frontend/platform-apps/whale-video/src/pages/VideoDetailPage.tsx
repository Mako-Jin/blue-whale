// 视频详情（M3）：播放器（断点续播 / 全屏 / 快捷键）+ 互动 + 所属系列集数

import { Alert, App, Button, Card, Descriptions, Result, Space, Spin, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  favoriteContent,
  fetchSeriesDetail,
  fetchVideoDetail,
  likeContent,
  type ContentDetailView,
  type SeriesDetailView,
} from '../api/video';
import { hasPermission } from '../api/client';
import ContentComments from '../components/ContentComments';
import ContentCover from '../components/ContentCover';
import ContentPlayer from '../player/ContentPlayer';
import { formatCount, formatDateTime, formatDuration } from '../utils/videoDisplay';

export default function VideoDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const contentId = Number(params.id);

  const [detail, setDetail] = useState<ContentDetailView | null>(null);
  const [series, setSeries] = useState<SeriesDetailView | null>(null);
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
    fetchVideoDetail(contentId)
      .then((data) => {
        setDetail(data);
        setError(null);
        if (data.seriesId && data.seriesId > 0) {
          fetchSeriesDetail(data.seriesId)
            .then(setSeries)
            .catch(() => setSeries(null));
        } else {
          setSeries(null);
        }
      })
      .catch((reason: unknown) =>
        setError(reason instanceof Error ? reason.message : '视频加载失败'),
      )
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
        title="视频不存在"
        subTitle={error ?? '该视频可能已下架或不可见'}
        extra={
          <Button type="primary" onClick={() => navigate('/videos')}>
            返回视频列表
          </Button>
        }
      />
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Button onClick={() => navigate('/videos')}>返回视频</Button>
      </Space>

      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
          <div
            style={{ flex: '1 1 560px', minWidth: 320, aspectRatio: '16 / 9', background: '#000' }}
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
                该视频暂不可播放
              </div>
            )}
          </div>
          <div style={{ flex: '1 1 320px', padding: 20 }}>
            <Typography.Title level={4} style={{ marginTop: 0 }}>
              {detail.title}
            </Typography.Title>
            {detail.subtitle ? (
              <Typography.Text type="secondary">{detail.subtitle}</Typography.Text>
            ) : null}
            {detail.description ? (
              <Typography.Paragraph style={{ marginTop: 12 }}>
                {detail.description}
              </Typography.Paragraph>
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
              <Descriptions.Item label="时长">
                {formatDuration(detail.durationMs)}
              </Descriptions.Item>
              <Descriptions.Item label="播放">{formatCount(detail.playCount)}</Descriptions.Item>
              <Descriptions.Item label="发布时间">
                {formatDateTime(detail.publishedAt)}
              </Descriptions.Item>
              {detail.seriesId && detail.seriesId > 0 ? (
                <Descriptions.Item label="所属系列">
                  <Button
                    type="link"
                    size="small"
                    style={{ padding: 0 }}
                    onClick={() => navigate(`/series/${detail.seriesId}`)}
                  >
                    {series?.title ?? `系列 #${detail.seriesId}`} · 第 {detail.episodeNo || 1} 集
                  </Button>
                </Descriptions.Item>
              ) : null}
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
              <Alert style={{ marginTop: 16 }} type="info" showIcon message="该视频暂不开放评论" />
            ) : null}
          </div>
        </div>
      </Card>

      {series && series.episodes?.length ? (
        <Card
          title={`系列：${series.title}`}
          size="small"
          extra={<Typography.Text type="secondary">共 {series.episodes.length} 集</Typography.Text>}
        >
          <Space wrap size={10}>
            {series.episodes.map((episode) => {
              const active = episode.id === detail.id;
              return (
                <div
                  key={episode.id}
                  style={{
                    width: 168,
                    border: active ? '2px solid #1677ff' : '1px solid #f0f0f0',
                    borderRadius: 8,
                    overflow: 'hidden',
                    cursor: 'pointer',
                    background: '#fff',
                  }}
                  onClick={() => navigate(`/videos/${episode.id}`)}
                >
                  <ContentCover url={episode.coverUrl} title={episode.title} height={94} />
                  <div style={{ padding: '6px 8px' }}>
                    <Typography.Text strong ellipsis style={{ display: 'block', fontSize: 12 }}>
                      第 {episode.episodeNo || 1} 集 · {episode.title}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      {formatDuration(episode.durationMs)}
                    </Typography.Text>
                  </div>
                </div>
              );
            })}
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

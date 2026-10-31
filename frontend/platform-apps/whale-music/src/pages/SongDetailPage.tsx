// 歌曲详情（用户侧公开视图）：曲目信息、标签与播放 / 收藏入口
// 播放走 /music/stream 票据代理（M2），文件 ID 不暴露给前端。

import { App, Button, Card, Descriptions, Result, Space, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  getPublicSong,
  likeSong,
  pageAlbums,
  pageArtists,
  type AlbumView,
  type ArtistView,
  type PublicSongView,
} from '../api/music';
import { formatDuration, renderTags, renderVisibility } from '../utils/musicDisplay';
import { usePlayer } from '../player/PlayerContext';
import CommentSection from '../components/CommentSection';

export default function SongDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const player = usePlayer();

  const [song, setSong] = useState<PublicSongView | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [artists, setArtists] = useState<ArtistView[]>([]);
  const [albums, setAlbums] = useState<AlbumView[]>([]);

  useEffect(() => {
    const id = Number(params.id);
    if (!Number.isFinite(id)) {
      setNotFound(true);
      setLoading(false);
      return;
    }
    setLoading(true);
    getPublicSong(id)
      .then(setSong)
      .catch((error) => {
        setNotFound(true);
        message.error(error instanceof Error ? error.message : '歌曲不存在或不可见');
      })
      .finally(() => setLoading(false));
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch(() => setArtists([]));
    pageAlbums({ current: 1, size: 200 })
      .then((result) => setAlbums(result.records))
      .catch(() => setAlbums([]));
  }, [message, params.id]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    );
  }

  if (notFound || !song) {
    return (
      <Result
        status="404"
        title="歌曲不存在或不可见"
        subTitle="自听作品仅上传者本人可见，已下架或版权下架的作品不可访问"
        extra={
          <Button type="primary" onClick={() => navigate('/library')}>
            返回曲库
          </Button>
        }
      />
    );
  }

  const artistName = artists.find((item) => item.id === song.artistId)?.name ?? '-';
  const albumTitle = albums.find((item) => item.id === song.albumId)?.title ?? '-';

  return (
    <div className="music-detail">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card
          title={
            <Space direction="vertical" size={0}>
              <Typography.Title level={4} style={{ margin: 0 }}>
                {song.title}
              </Typography.Title>
              {song.subtitle && <Typography.Text type="secondary">{song.subtitle}</Typography.Text>}
            </Space>
          }
          extra={
            <Space>
              <Button
                type="primary"
                disabled={!song.playable}
                onClick={() => player.play(song)}
              >
                播放
              </Button>
              <Button
                onClick={() => {
                  likeSong(song.id)
                    .then(() => message.success('已加入我的收藏'))
                    .catch((error) => message.error(
                      error instanceof Error ? error.message : '收藏失败'));
                }}
              >
                收藏
              </Button>
              <Button onClick={() => navigate('/library')}>返回曲库</Button>
            </Space>
          }
        >
          <Descriptions
            size="small"
            column={2}
            bordered
            items={[
              { key: 'artist', label: '歌手', children: artistName },
              { key: 'album', label: '专辑', children: albumTitle },
              { key: 'duration', label: '时长', children: formatDuration(song.durationMs) },
              { key: 'language', label: '语言', children: song.language ?? '-' },
              { key: 'visibility', label: '可见性', children: renderVisibility(song.visibility) },
              { key: 'playable', label: '可播放', children: song.playable ? '是' : '否' },
              { key: 'playCount', label: '播放次数', children: song.playCount ?? 0 },
              { key: 'likeCount', label: '收藏数', children: song.likeCount ?? 0 },
              { key: 'commentCount', label: '评论数', children: song.commentCount ?? 0 },
              {
                key: 'publishedAt',
                label: '上架时间',
                children: song.publishedAt ?? '-',
              },
              { key: 'tags', label: '标签', children: renderTags(song.tags), span: 2 },
            ]}
          />
        </Card>
        <CommentSection songId={song.id} />
      </Space>
    </div>
  );
}

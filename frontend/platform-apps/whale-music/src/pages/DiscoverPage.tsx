// 发现首页：热门歌曲 / 新歌 / 官方歌单 / 热门标签（点击歌曲直接走全局播放器）

import { App, Card, Col, Empty, List, Row, Skeleton, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getHome, type HomeView, type PlaylistView, type PublicSongView } from '../api/music';
import { formatDuration } from '../utils/musicDisplay';
import { usePlayer } from '../player/PlayerContext';

/** 歌曲榜单区块 */
function SongBoard({
  title,
  songs,
  loading,
  onPlay,
}: {
  title: string;
  songs: PublicSongView[];
  loading: boolean;
  onPlay: (song: PublicSongView) => void;
}) {
  return (
    <Card title={title} size="small" style={{ height: '100%' }}>
      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <List<PublicSongView>
          size="small"
          dataSource={songs}
          locale={{
            emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无歌曲" />,
          }}
          renderItem={(song, index) => (
            <List.Item
              className="music-board-item"
              onClick={() => onPlay(song)}
              extra={
                <Typography.Text type="secondary">
                  {formatDuration(song.durationMs)}
                </Typography.Text>
              }
            >
              <div className="music-board-row">
                <span className={index < 3 ? 'music-rank top' : 'music-rank'}>{index + 1}</span>
                <div className="music-song-title">
                  <span>{song.title}</span>
                  {song.subtitle && <span className="music-song-subtitle">{song.subtitle}</span>}
                </div>
                {!song.playable && <Tag color="default">不可播</Tag>}
              </div>
            </List.Item>
          )}
        />
      )}
    </Card>
  );
}

/** 官方歌单卡片（封面用首字占位，避免额外拉取私有文件） */
function PlaylistCard({ playlist, onOpen }: { playlist: PlaylistView; onOpen: () => void }) {
  return (
    <Card hoverable className="music-playlist-card" onClick={onOpen}>
      <div className="music-playlist-cover">{playlist.title?.slice(0, 1) || '歌'}</div>
      <div className="music-playlist-meta">
        <Typography.Text strong ellipsis>
          {playlist.title}
        </Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {playlist.songCount ?? 0} 首 · 官方歌单
        </Typography.Text>
      </div>
    </Card>
  );
}

export default function DiscoverPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const player = usePlayer();

  const [home, setHome] = useState<HomeView | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getHome()
      .then(setHome)
      .catch((error) => message.error(error instanceof Error ? error.message : '首页加载失败'))
      .finally(() => setLoading(false));
  }, [message]);

  const hotSongs = home?.hotSongs ?? [];
  const newSongs = home?.newSongs ?? [];
  const officialPlaylists = home?.officialPlaylists ?? [];
  const tags = home?.tags ?? [];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div>
        <Typography.Title level={4} style={{ margin: 0 }}>
          发现音乐
        </Typography.Title>
        <Typography.Text type="secondary">
          热门推荐、最新上架与官方歌单，点击歌曲即可播放
        </Typography.Text>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <SongBoard title="热门歌曲" songs={hotSongs} loading={loading} onPlay={player.play} />
        </Col>
        <Col xs={24} lg={12}>
          <SongBoard title="新歌上架" songs={newSongs} loading={loading} onPlay={player.play} />
        </Col>
      </Row>

      <Card title="官方歌单" size="small">
        {loading ? (
          <Skeleton active paragraph={{ rows: 3 }} />
        ) : officialPlaylists.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无官方歌单" />
        ) : (
          <Row gutter={[16, 16]}>
            {officialPlaylists.map((playlist) => (
              <Col xs={12} sm={8} md={6} xl={4} key={playlist.id}>
                <PlaylistCard
                  playlist={playlist}
                  onOpen={() => navigate(`/playlist/${playlist.id}`)}
                />
              </Col>
            ))}
          </Row>
        )}
      </Card>

      <Card title="热门标签" size="small">
        {loading ? (
          <Skeleton active paragraph={{ rows: 1 }} />
        ) : tags.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无标签" />
        ) : (
          <div className="music-tag-cloud">
            {tags.map((tag) => (
              <Tag key={tag.id} color="blue">
                {tag.name}
              </Tag>
            ))}
          </div>
        )}
      </Card>
    </Space>
  );
}

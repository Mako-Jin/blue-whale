// 我的音乐：我的收藏 / 最近播放 / 我的歌单（M4-3）

import { App, Button, Card, Empty, List, Space, Tabs, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  pageMyLikes,
  pagePlayHistory,
  pagePlaylists,
  type PlayHistoryView,
  type PlaylistView,
  type PublicSongView,
} from '../api/music';
import { formatDuration } from '../utils/musicDisplay';
import { usePlayer } from '../player/PlayerContext';

const PAGE_SIZE = 20;

export default function MyMusicPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const player = usePlayer();

  const [likes, setLikes] = useState<PublicSongView[]>([]);
  const [likesTotal, setLikesTotal] = useState(0);
  const [history, setHistory] = useState<PlayHistoryView[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [playlists, setPlaylists] = useState<PlaylistView[]>([]);
  const [playlistsTotal, setPlaylistsTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const loadLikes = useCallback(
    (page: number) => {
      setLoading(true);
      pageMyLikes({ current: page, size: PAGE_SIZE })
        .then((result) => {
          setLikes(result.records);
          setLikesTotal(result.total);
        })
        .catch((error) => message.error(error instanceof Error ? error.message : '收藏加载失败'))
        .finally(() => setLoading(false));
    },
    [message],
  );

  const loadHistory = useCallback(
    (page: number) => {
      setLoading(true);
      pagePlayHistory({ current: page, size: PAGE_SIZE })
        .then((result) => {
          setHistory(result.records);
          setHistoryTotal(result.total);
        })
        .catch((error) => message.error(error instanceof Error ? error.message : '最近播放加载失败'))
        .finally(() => setLoading(false));
    },
    [message],
  );

  const loadPlaylists = useCallback(
    (page: number) => {
      setLoading(true);
      pagePlaylists({ current: page, size: PAGE_SIZE, mine: true })
        .then((result) => {
          setPlaylists(result.records);
          setPlaylistsTotal(result.total);
        })
        .catch((error) => message.error(error instanceof Error ? error.message : '歌单加载失败'))
        .finally(() => setLoading(false));
    },
    [message],
  );

  useEffect(() => {
    loadLikes(1);
    loadHistory(1);
    loadPlaylists(1);
  }, [loadLikes, loadHistory, loadPlaylists]);

  const likeTab = (
    <List
      loading={loading}
      dataSource={likes}
      locale={{ emptyText: <Empty description="还没有收藏歌曲，去曲库逛逛吧" /> }}
      pagination={{ total: likesTotal, pageSize: PAGE_SIZE, onChange: loadLikes, hideOnSinglePage: true }}
      renderItem={(song) => (
        <List.Item
          actions={[
            <Button
              key="play"
              type="link"
              disabled={!song.playable}
              onClick={() => player.play(song)}
            >
              播放
            </Button>,
            <Button key="detail" type="link" onClick={() => navigate(`/song/${song.id}`)}>
              详情
            </Button>,
          ]}
        >
          <List.Item.Meta
            title={song.title}
            description={`时长 ${formatDuration(song.durationMs)} · 播放 ${song.playCount ?? 0} 次`}
          />
        </List.Item>
      )}
    />
  );

  const historyTab = (
    <List
      loading={loading}
      dataSource={history}
      locale={{ emptyText: <Empty description="暂无播放记录" /> }}
      pagination={{ total: historyTotal, pageSize: PAGE_SIZE, onChange: loadHistory, hideOnSinglePage: true }}
      renderItem={(item) => (
        <List.Item
          actions={[
            <Button key="detail" type="link" onClick={() => navigate(`/song/${item.songId}`)}>
              详情
            </Button>,
          ]}
        >
          <List.Item.Meta
            title={
              <Space size={8}>
                <span>{item.title ?? `歌曲 ${item.songId}`}</span>
                {!item.playable && <Tag color="red">已下架</Tag>}
              </Space>
            }
            description={`播放于 ${item.playedAt ?? '-'} · 进度 ${Math.round((item.progressMs ?? 0) / 1000)}s`}
          />
        </List.Item>
      )}
    />
  );

  const playlistTab = (
    <List
      loading={loading}
      dataSource={playlists}
      locale={{ emptyText: <Empty description="还没有歌单，去歌单页创建一个吧" /> }}
      pagination={{
        total: playlistsTotal,
        pageSize: PAGE_SIZE,
        onChange: loadPlaylists,
        hideOnSinglePage: true,
      }}
      renderItem={(playlist) => (
        <List.Item
          actions={[
            <Button key="open" type="link" onClick={() => navigate(`/playlist/${playlist.id}`)}>
              打开
            </Button>,
          ]}
        >
          <List.Item.Meta
            title={
              <Space size={8}>
                <span>{playlist.title}</span>
                {playlist.visibility === 'PRIVATE' && <Tag>私有</Tag>}
              </Space>
            }
            description={`${playlist.songCount ?? 0} 首歌曲`}
          />
        </List.Item>
      )}
    />
  );

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        我的音乐
      </Typography.Title>
      <Tabs
        defaultActiveKey="likes"
        items={[
          { key: 'likes', label: `我的收藏（${likesTotal}）`, children: likeTab },
          { key: 'history', label: `最近播放（${historyTotal}）`, children: historyTab },
          { key: 'playlists', label: `我的歌单（${playlistsTotal}）`, children: playlistTab },
        ]}
      />
    </Card>
  );
}

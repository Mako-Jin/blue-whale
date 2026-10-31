// 站内搜索：歌曲 / 歌手 / 专辑 / 歌单分组结果，含加载态与空态

import {
  App,
  Button,
  Card,
  Empty,
  Input,
  List,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  searchMusic,
  type AlbumView,
  type ArtistView,
  type PlaylistView,
  type PublicSongView,
  type SearchResultView,
  type SearchType,
} from '../api/music';
import { formatDuration } from '../utils/musicDisplay';
import { usePlayer } from '../player/PlayerContext';

const TYPE_TABS: { key: SearchType; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'SONG', label: '歌曲' },
  { key: 'ARTIST', label: '歌手' },
  { key: 'ALBUM', label: '专辑' },
  { key: 'PLAYLIST', label: '歌单' },
];

export default function SearchPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const player = usePlayer();

  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [type, setType] = useState<SearchType>('ALL');
  const [result, setResult] = useState<SearchResultView | null>(null);
  const [loading, setLoading] = useState(false);

  const runSearch = useCallback(
    (value: string, searchType: SearchType) => {
      const trimmed = value.trim();
      setKeyword(trimmed);
      if (!trimmed) {
        setResult(null);
        setLoading(false);
        return;
      }
      setLoading(true);
      searchMusic({ keyword: trimmed, type: searchType })
        .then(setResult)
        .catch((error) => message.error(error instanceof Error ? error.message : '搜索失败'))
        .finally(() => setLoading(false));
    },
    [message],
  );

  const renderSongs = (songs: PublicSongView[]) => (
    <List<PublicSongView>
      size="small"
      dataSource={songs}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无歌曲结果" /> }}
      renderItem={(song) => (
        <List.Item
          className="music-board-item"
          onClick={() => navigate(`/song/${song.id}`)}
          extra={
            <Space size={12}>
              <Typography.Text type="secondary">{formatDuration(song.durationMs)}</Typography.Text>
              <Button
                type="link"
                size="small"
                disabled={!song.playable}
                onClick={(event) => {
                  event.stopPropagation();
                  player.play(song);
                }}
              >
                播放
              </Button>
            </Space>
          }
        >
          <div className="music-song-title">
            <span>{song.title}</span>
            {song.subtitle && <span className="music-song-subtitle">{song.subtitle}</span>}
          </div>
        </List.Item>
      )}
    />
  );

  const renderArtists = (artists: ArtistView[]) => (
    <List<ArtistView>
      size="small"
      dataSource={artists}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无歌手结果" /> }}
      renderItem={(artist) => (
        <List.Item extra={<Typography.Text type="secondary">{artist.region ?? '-'}</Typography.Text>}>
          <Typography.Text>{artist.name}</Typography.Text>
        </List.Item>
      )}
    />
  );

  const renderAlbums = (albums: AlbumView[]) => (
    <List<AlbumView>
      size="small"
      dataSource={albums}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无专辑结果" /> }}
      renderItem={(album) => (
        <List.Item
          extra={<Typography.Text type="secondary">{album.releaseDate ?? '-'}</Typography.Text>}
        >
          <Typography.Text>{album.title}</Typography.Text>
        </List.Item>
      )}
    />
  );

  const renderPlaylists = (playlists: PlaylistView[]) => (
    <List<PlaylistView>
      size="small"
      dataSource={playlists}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无歌单结果" /> }}
      renderItem={(playlist) => (
        <List.Item
          className="music-board-item"
          onClick={() => navigate(`/playlist/${playlist.id}`)}
          extra={
            <Space size={8}>
              {playlist.playlistType === 'OFFICIAL' && <Tag color="blue">官方</Tag>}
              <Typography.Text type="secondary">{playlist.songCount ?? 0} 首</Typography.Text>
            </Space>
          }
        >
          <div className="music-song-title">
            <span>{playlist.title}</span>
            {playlist.description && (
              <span className="music-song-subtitle">{playlist.description}</span>
            )}
          </div>
        </List.Item>
      )}
    />
  );

  const sections = () => {
    if (!keyword) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="输入关键词开始搜索歌曲、歌手、专辑与歌单"
        />
      );
    }
    if (loading) {
      return <Skeleton active paragraph={{ rows: 8 }} />;
    }
    if (!result || result.total === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={`未找到与「${keyword}」相关的内容`}
        />
      );
    }
    if (type === 'ALL') {
      return (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card size="small" title={`歌曲（${result.songTotal}）`}>
            {renderSongs(result.songs ?? [])}
          </Card>
          <Card size="small" title={`歌手（${result.artistTotal}）`}>
            {renderArtists(result.artists ?? [])}
          </Card>
          <Card size="small" title={`专辑（${result.albumTotal}）`}>
            {renderAlbums(result.albums ?? [])}
          </Card>
          <Card size="small" title={`歌单（${result.playlistTotal}）`}>
            {renderPlaylists(result.playlists ?? [])}
          </Card>
        </Space>
      );
    }
    return (
      <Card size="small" title={`搜索结果（${result.total}）`}>
        {type === 'SONG' && renderSongs(result.songs ?? [])}
        {type === 'ARTIST' && renderArtists(result.artists ?? [])}
        {type === 'ALBUM' && renderAlbums(result.albums ?? [])}
        {type === 'PLAYLIST' && renderPlaylists(result.playlists ?? [])}
      </Card>
    );
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Input.Search
          size="large"
          allowClear
          enterButton="搜索"
          placeholder="搜索歌曲 / 歌手 / 专辑 / 歌单"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onSearch={(value) => runSearch(value, type)}
        />
        <Tabs
          activeKey={type}
          onChange={(key) => {
            const next = key as SearchType;
            setType(next);
            if (keyword) {
              runSearch(keyword, next);
            }
          }}
          items={TYPE_TABS.map((tab) => ({ key: tab.key, label: tab.label }))}
          style={{ marginTop: 8 }}
        />
      </Card>

      <Card size="small">
        <div style={{ minHeight: 240 }}>{sections()}</div>
      </Card>
    </Space>
  );
}

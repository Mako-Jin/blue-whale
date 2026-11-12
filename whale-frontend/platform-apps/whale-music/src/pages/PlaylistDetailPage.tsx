// 歌单详情：歌单信息 + 歌曲列表；所有者可加歌 / 移歌 / 排序，官方歌单只读

import {
  App,
  Button,
  Card,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Result,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  type TableProps,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getCurrentUser, hasPermission } from '../api/client';
import {
  addPlaylistSong,
  getPlaylist,
  pageArtists,
  pagePublicSongs,
  removePlaylistSong,
  reorderPlaylist,
  type ArtistView,
  type PlaylistSongView,
  type PlaylistView,
  type PublicSongView,
} from '../api/music';
import { formatDuration, renderVisibility } from '../utils/musicDisplay';
import { usePlayer } from '../player/PlayerContext';

/** 歌曲选择弹窗每页条数 */
const PICKER_PAGE_SIZE = 10;

export default function PlaylistDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const player = usePlayer();

  const playlistId = Number(params.id);

  const [playlist, setPlaylist] = useState<PlaylistView | null>(null);
  const [songs, setSongs] = useState<PlaylistSongView[]>([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [artists, setArtists] = useState<ArtistView[]>([]);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerInput, setPickerInput] = useState('');
  const [pickerKeyword, setPickerKeyword] = useState<string | undefined>(undefined);
  const [pickerPage, setPickerPage] = useState(1);
  const [pickerTotal, setPickerTotal] = useState(0);
  const [pickerSongs, setPickerSongs] = useState<PublicSongView[]>([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [addingId, setAddingId] = useState<number | undefined>(undefined);

  const loadDetail = useCallback(
    (id: number) => {
      setLoading(true);
      getPlaylist(id)
        .then((data) => {
          setPlaylist(data);
          setSongs(data.songs ?? []);
          setDirty(false);
        })
        .catch((error) => {
          setNotFound(true);
          message.error(error instanceof Error ? error.message : '歌单不存在或不可见');
        })
        .finally(() => setLoading(false));
    },
    [message],
  );

  useEffect(() => {
    if (!Number.isFinite(playlistId)) {
      setNotFound(true);
      setLoading(false);
      return;
    }
    loadDetail(playlistId);
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch(() => setArtists([]));
  }, [loadDetail, playlistId]);

  useEffect(() => {
    if (!pickerOpen) {
      return;
    }
    setPickerLoading(true);
    pagePublicSongs({ keyword: pickerKeyword, current: pickerPage, size: PICKER_PAGE_SIZE })
      .then((result) => {
        setPickerSongs(result.records ?? []);
        setPickerTotal(Number(result.total ?? 0));
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '歌曲加载失败'))
      .finally(() => setPickerLoading(false));
  }, [message, pickerKeyword, pickerOpen, pickerPage]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    );
  }

  if (notFound || !playlist) {
    return (
      <Result
        status="404"
        title="歌单不存在或不可见"
        subTitle="私有歌单仅所有者可见，官方歌单请从发现页进入"
        extra={
          <Button type="primary" onClick={() => navigate('/playlist')}>
            返回歌单广场
          </Button>
        }
      />
    );
  }

  const currentUserId = getCurrentUser()?.userId;
  const isOwner =
    playlist.playlistType === 'USER' &&
    playlist.ownerId !== undefined &&
    currentUserId !== undefined &&
    String(playlist.ownerId) === String(currentUserId) &&
    hasPermission('sys:music:playlist:own');

  const artistName = (artistId?: number) =>
    artists.find((item) => item.id === artistId)?.name ?? '-';

  const handlePlayAll = () => {
    if (songs.length > 0) {
      player.play(songs[0], songs);
    }
  };

  const handlePlay = (song: PlaylistSongView) => {
    player.play(song, songs);
  };

  const handleEnqueue = (song: PlaylistSongView) => {
    if (player.queue.some((item) => item.id === song.id)) {
      message.info('该歌曲已在播放队列中');
      return;
    }
    player.enqueue(song);
    message.success('已加入播放队列');
  };

  const handleMove = (index: number, offset: number) => {
    const target = index + offset;
    if (target < 0 || target >= songs.length) {
      return;
    }
    const next = [...songs];
    const [item] = next.splice(index, 1);
    next.splice(target, 0, item);
    setSongs(next);
    setDirty(true);
  };

  const handleSaveSort = async () => {
    setSaving(true);
    try {
      const updated = await reorderPlaylist(
        playlist.id,
        songs.map((song) => song.id),
      );
      setPlaylist(updated);
      setSongs(updated.songs ?? songs);
      setDirty(false);
      message.success('排序已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '排序保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async (songId: number) => {
    try {
      await removePlaylistSong(playlist.id, songId);
      message.success('已移出歌单');
      loadDetail(playlist.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '移出失败');
    }
  };

  const handleAdd = async (songId: number) => {
    setAddingId(songId);
    try {
      const updated = await addPlaylistSong(playlist.id, songId);
      setPlaylist(updated);
      setSongs(updated.songs ?? []);
      setDirty(false);
      message.success('已加入歌单');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加入歌单失败');
    } finally {
      setAddingId(undefined);
    }
  };

  const columns: TableProps<PlaylistSongView>['columns'] = [
    {
      title: '#',
      key: 'index',
      width: 60,
      render: (_, __, index) => index + 1,
    },
    {
      title: '歌曲',
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.subtitle && <span className="music-song-subtitle">{row.subtitle}</span>}
        </div>
      ),
    },
    {
      title: '歌手',
      dataIndex: 'artistId',
      width: 140,
      render: (value: number | undefined) => artistName(value),
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 90,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: '操作',
      key: 'action',
      width: isOwner ? 260 : 150,
      render: (_, row, index) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => handlePlay(row)}>
            播放
          </Button>
          <Button type="link" size="small" onClick={() => handleEnqueue(row)}>
            加入队列
          </Button>
          {isOwner && (
            <>
              <Button
                type="link"
                size="small"
                disabled={index === 0}
                onClick={() => handleMove(index, -1)}
              >
                上移
              </Button>
              <Button
                type="link"
                size="small"
                disabled={index === songs.length - 1}
                onClick={() => handleMove(index, 1)}
              >
                下移
              </Button>
              <Popconfirm
                title="确认将该歌曲移出歌单？"
                onConfirm={() => void handleRemove(row.id)}
              >
                <Button type="link" size="small" danger>
                  移出
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  const pickerColumns: TableProps<PublicSongView>['columns'] = [
    {
      title: '歌曲',
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.subtitle && <span className="music-song-subtitle">{row.subtitle}</span>}
        </div>
      ),
    },
    {
      title: '歌手',
      dataIndex: 'artistId',
      width: 140,
      render: (value: number | undefined) => artistName(value),
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 90,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, row) => {
        const added = songs.some((song) => song.id === row.id);
        return (
          <Button
            type="link"
            size="small"
            disabled={added}
            loading={addingId === row.id}
            onClick={() => void handleAdd(row.id)}
          >
            {added ? '已加入' : '加入'}
          </Button>
        );
      },
    },
  ];

  return (
    <>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card
          title={
            <Space direction="vertical" size={2}>
              <Space size={8}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {playlist.title}
                </Typography.Title>
                {playlist.playlistType === 'OFFICIAL' ? (
                  <Tag color="blue">官方歌单</Tag>
                ) : (
                  <Tag>用户歌单</Tag>
                )}
                {renderVisibility(playlist.visibility)}
              </Space>
              <Typography.Text type="secondary">
                {playlist.description || '暂无简介'}
              </Typography.Text>
            </Space>
          }
          extra={
            <Space wrap>
              <Button type="primary" onClick={handlePlayAll} disabled={songs.length === 0}>
                播放全部
              </Button>
              {isOwner && (
                <>
                  <Button onClick={() => setPickerOpen(true)}>添加歌曲</Button>
                  <Button
                    type="primary"
                    ghost
                    loading={saving}
                    disabled={!dirty || songs.length === 0}
                    onClick={() => void handleSaveSort()}
                  >
                    保存排序
                  </Button>
                </>
              )}
              <Button onClick={() => navigate('/playlist')}>返回歌单广场</Button>
            </Space>
          }
        >
          <Space size={24} wrap>
            <Typography.Text type="secondary">
              歌曲数：{playlist.songCount ?? songs.length}
            </Typography.Text>
            <Typography.Text type="secondary">播放次数：{playlist.playCount ?? 0}</Typography.Text>
            <Typography.Text type="secondary">
              更新时间：{playlist.updateTime ?? '-'}
            </Typography.Text>
            {!isOwner && playlist.playlistType === 'OFFICIAL' && (
              <Typography.Text type="secondary">官方歌单只读，由运营维护</Typography.Text>
            )}
          </Space>
        </Card>

        <Card title="歌单曲目" size="small">
          <Table<PlaylistSongView>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={songs}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={isOwner ? '歌单暂无歌曲，点击「添加歌曲」' : '歌单暂无歌曲'}
                />
              ),
            }}
          />
        </Card>
      </Space>

      <Modal
        open={pickerOpen}
        title="添加歌曲"
        width={760}
        footer={null}
        onCancel={() => setPickerOpen(false)}
      >
        <Input.Search
          allowClear
          enterButton="搜索"
          placeholder="歌曲标题 / 副标题"
          value={pickerInput}
          onChange={(event) => setPickerInput(event.target.value)}
          onSearch={(value) => {
            setPickerKeyword(value.trim() || undefined);
            setPickerPage(1);
          }}
          style={{ marginBottom: 12 }}
        />
        <Table<PublicSongView>
          rowKey="id"
          size="small"
          loading={pickerLoading}
          columns={pickerColumns}
          dataSource={pickerSongs}
          locale={{
            emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无歌曲" />,
          }}
          pagination={{
            current: pickerPage,
            pageSize: PICKER_PAGE_SIZE,
            total: pickerTotal,
            showSizeChanger: false,
            onChange: (page) => setPickerPage(page),
          }}
        />
      </Modal>
    </>
  );
}

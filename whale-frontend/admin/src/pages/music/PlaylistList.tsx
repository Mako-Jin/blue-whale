// 音乐运营 - 官方歌单：分页 / 新建 / 编辑 / 删除 + 曲目抽屉（加歌 / 移歌 / 排序）
// 权限码：sys:music:playlist:manage；曲目维护复用用户歌单加歌 / 移歌 / 排序接口

import {
  App,
  Button,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Upload,
  type TableProps,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import { AuthButton, FormModal, ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import { uploadFile } from '../../api/file';
import {
  addOfficialPlaylistSong,
  createOfficialPlaylist,
  deleteOfficialPlaylist,
  getPlaylist,
  pageArtists,
  pageOfficialPlaylists,
  pageSongs,
  reorderOfficialPlaylist,
  removeOfficialPlaylistSong,
  updateOfficialPlaylist,
  type ArtistView,
  type PlaylistQuery,
  type PlaylistSaveInput,
  type PlaylistSongView,
  type PlaylistView,
  type SongView,
} from '../../api/music';

interface PlaylistFormValues {
  title: string;
  description?: string;
  coverFileId?: string;
}

/** 歌曲选择弹窗每页条数 */
const PICKER_PAGE_SIZE = 10;

/** 毫秒时长 → mm:ss */
function formatDuration(durationMs?: number): string {
  if (!durationMs || durationMs <= 0) {
    return '-';
  }
  const totalSeconds = Math.round(durationMs / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

export default function PlaylistList() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<PlaylistFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<PlaylistView | null>(null);
  const [coverName, setCoverName] = useState('');
  const [uploadingCover, setUploadingCover] = useState(false);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [current, setCurrent] = useState<PlaylistView | null>(null);
  const [songs, setSongs] = useState<PlaylistSongView[]>([]);
  const [songsLoading, setSongsLoading] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [savingSort, setSavingSort] = useState(false);

  const [artists, setArtists] = useState<ArtistView[]>([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerInput, setPickerInput] = useState('');
  const [pickerKeyword, setPickerKeyword] = useState<string | undefined>(undefined);
  const [pickerPage, setPickerPage] = useState(1);
  const [pickerTotal, setPickerTotal] = useState(0);
  const [pickerSongs, setPickerSongs] = useState<SongView[]>([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [addingId, setAddingId] = useState<string | undefined>(undefined);

  useEffect(() => {
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch(() => setArtists([]));
  }, []);

  useEffect(() => {
    if (!pickerOpen) {
      return;
    }
    setPickerLoading(true);
    pageSongs({
      keyword: pickerKeyword,
      publishState: 'PUBLISHED',
      visibility: 'PUBLIC',
      current: pickerPage,
      size: PICKER_PAGE_SIZE,
    })
      .then((result) => {
        setPickerSongs(result.records ?? []);
        setPickerTotal(Number(result.total ?? 0));
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '歌曲加载失败'))
      .finally(() => setPickerLoading(false));
  }, [message, pickerKeyword, pickerOpen, pickerPage]);

  const artistName = (artistId?: string) =>
    artists.find((item) => item.id === artistId)?.name ?? '-';

  const openCreate = () => {
    setEditing(null);
    setCoverName('');
    setModalOpen(true);
  };

  const openEdit = (row: PlaylistView) => {
    setEditing(row);
    setCoverName(row.coverFileId ? `文件 ID ${row.coverFileId}` : '');
    setModalOpen(true);
  };

  const handleCoverSelect = (file: File) => {
    setUploadingCover(true);
    uploadFile(file)
      .then((uploaded) => {
        form.setFieldValue('coverFileId', uploaded.id);
        setCoverName(uploaded.originalName ?? file.name);
        message.success('封面已上传');
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '封面上传失败'))
      .finally(() => setUploadingCover(false));
  };

  const handleSave = async (values: PlaylistFormValues) => {
    const payload: PlaylistSaveInput = {
      title: values.title,
      description: values.description || undefined,
      visibility: 'PUBLIC',
      coverFileId: values.coverFileId,
    };
    try {
      if (editing) {
        await updateOfficialPlaylist(editing.id, payload);
        message.success('官方歌单已更新');
      } else {
        await createOfficialPlaylist(payload);
        message.success('官方歌单已创建');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: PlaylistView) => {
    try {
      await deleteOfficialPlaylist(row.id);
      message.success('官方歌单已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const reloadSongs = async (id: string) => {
    setSongsLoading(true);
    try {
      const detail = await getPlaylist(id);
      setCurrent(detail);
      setSongs(detail.songs ?? []);
      setDirty(false);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '歌单曲目加载失败');
    } finally {
      setSongsLoading(false);
    }
  };

  const openSongs = (row: PlaylistView) => {
    setCurrent(row);
    setSongs([]);
    setDirty(false);
    setDrawerOpen(true);
    void reloadSongs(row.id);
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
    if (!current) {
      return;
    }
    setSavingSort(true);
    try {
      const updated = await reorderOfficialPlaylist(
        current.id,
        songs.map((song) => song.id),
      );
      setCurrent(updated);
      setSongs(updated.songs ?? songs);
      setDirty(false);
      message.success('排序已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '排序保存失败');
    } finally {
      setSavingSort(false);
    }
  };

  const handleRemove = async (songId: string) => {
    if (!current) {
      return;
    }
    try {
      await removeOfficialPlaylistSong(current.id, songId);
      message.success('已移出歌单');
      await reloadSongs(current.id);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '移出失败');
    }
  };

  const handleAdd = async (songId: string) => {
    if (!current) {
      return;
    }
    setAddingId(songId);
    try {
      const updated = await addOfficialPlaylistSong(current.id, songId);
      setCurrent(updated);
      setSongs(updated.songs ?? []);
      setDirty(false);
      message.success('已加入歌单');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加入歌单失败');
    } finally {
      setAddingId(undefined);
    }
  };

  const columns: TableProps<PlaylistView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '歌单',
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.description && <span className="music-song-subtitle">{row.description}</span>}
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'playlistType',
      width: 90,
      render: () => <Tag color="blue">官方</Tag>,
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      render: () => <Tag color="blue">公开</Tag>,
    },
    {
      title: '歌曲数',
      dataIndex: 'songCount',
      width: 90,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: '播放',
      dataIndex: 'playCount',
      width: 90,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openSongs(row)}>
            曲目管理
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该官方歌单？" onConfirm={() => void handleDelete(row)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const songColumns: TableProps<PlaylistSongView>['columns'] = [
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
      width: 130,
      render: (value: string | undefined) => artistName(value),
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
      width: 200,
      render: (_, row, index) => (
        <Space size={4} wrap>
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
          <Popconfirm title="确认将该歌曲移出歌单？" onConfirm={() => void handleRemove(row.id)}>
            <Button type="link" size="small" danger>
              移出
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const pickerColumns: TableProps<SongView>['columns'] = [
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
      width: 130,
      render: (value: string | undefined) => artistName(value),
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
      <ProTable<PlaylistView, PlaylistQuery>
        ref={tableRef}
        rowKey="id"
        title="官方歌单"
        columns={columns}
        request={pageOfficialPlaylists}
        params={{ keyword }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : '官方歌单加载失败')
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键词',
                element: <Input allowClear placeholder="歌单标题 / 简介" />,
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <AuthButton perm="sys:music:playlist:manage" type="primary" onClick={openCreate}>
            新建官方歌单
          </AuthButton>
        }
      />

      <FormModal<PlaylistFormValues>
        open={modalOpen}
        title={editing ? `编辑官方歌单：${editing.title}` : '新建官方歌单'}
        form={form}
        width={600}
        initialValues={{
          title: editing?.title,
          description: editing?.description,
          coverFileId: editing?.coverFileId,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item
          name="title"
          label="歌单标题"
          rules={[{ required: true, message: '请输入歌单标题' }]}
        >
          <Input maxLength={120} showCount />
        </Form.Item>
        <Form.Item name="description" label="歌单简介">
          <Input.TextArea rows={3} maxLength={1000} showCount placeholder="可选" />
        </Form.Item>
        <Form.Item label="封面（可选）">
          <Space>
            <Upload
              showUploadList={false}
              accept="image/*"
              beforeUpload={(file) => {
                handleCoverSelect(file);
                return false;
              }}
            >
              <Button loading={uploadingCover}>
                {coverName ? '重新上传封面' : '选择封面图片'}
              </Button>
            </Upload>
            {coverName && <Typography.Text type="secondary">{coverName}</Typography.Text>}
          </Space>
        </Form.Item>
        <Form.Item name="coverFileId" hidden>
          <Input />
        </Form.Item>
        <Typography.Text type="secondary">官方歌单固定公开，创建后用户侧只读。</Typography.Text>
      </FormModal>

      <Drawer
        title={current ? `歌单曲目：${current.title}` : '歌单曲目'}
        width={760}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setDirty(false);
          tableRef.current?.reload();
        }}
        extra={
          <Space>
            <Button onClick={() => setPickerOpen(true)}>添加歌曲</Button>
            <Button
              type="primary"
              loading={savingSort}
              disabled={!dirty || songs.length === 0}
              onClick={() => void handleSaveSort()}
            >
              保存排序
            </Button>
          </Space>
        }
      >
        {songsLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin />
          </div>
        ) : (
          <Table<PlaylistSongView>
            rowKey="id"
            size="middle"
            columns={songColumns}
            dataSource={songs}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="歌单暂无歌曲，点击右上角「添加歌曲」"
                />
              ),
            }}
          />
        )}
      </Drawer>

      <Modal
        open={pickerOpen}
        title="添加歌曲（仅已上架公开歌曲）"
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
        <Table<SongView>
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

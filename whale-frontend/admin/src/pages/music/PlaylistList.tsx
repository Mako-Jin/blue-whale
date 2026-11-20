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
import { useI18n } from '../../i18n';

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
  const { t } = useI18n();
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
      .catch((error) =>
        message.error(
          error instanceof Error ? error.message : t('music.playlist.msg.songsLoadFailed'),
        ),
      )
      .finally(() => setPickerLoading(false));
  }, [message, pickerKeyword, pickerOpen, pickerPage, t]);

  const artistName = (artistId?: string) =>
    artists.find((item) => item.id === artistId)?.name ?? '-';

  const openCreate = () => {
    setEditing(null);
    setCoverName('');
    setModalOpen(true);
  };

  const openEdit = (row: PlaylistView) => {
    setEditing(row);
    setCoverName(row.coverFileId ? t('music.common.fileId', { id: row.coverFileId }) : '');
    setModalOpen(true);
  };

  const handleCoverSelect = (file: File) => {
    setUploadingCover(true);
    uploadFile(file)
      .then((uploaded) => {
        form.setFieldValue('coverFileId', uploaded.id);
        setCoverName(uploaded.originalName ?? file.name);
        message.success(t('music.common.coverUploaded'));
      })
      .catch((error) =>
        message.error(error instanceof Error ? error.message : t('music.common.coverUploadFailed')),
      )
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
        message.success(t('music.playlist.msg.updated'));
      } else {
        await createOfficialPlaylist(payload);
        message.success(t('music.playlist.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: PlaylistView) => {
    try {
      await deleteOfficialPlaylist(row.id);
      message.success(t('music.playlist.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.deleteFailed'));
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
      message.error(
        error instanceof Error ? error.message : t('music.playlist.msg.tracksLoadFailed'),
      );
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
      message.success(t('music.playlist.msg.sortSaved'));
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('music.playlist.msg.sortSaveFailed'),
      );
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
      message.success(t('music.playlist.msg.removed'));
      await reloadSongs(current.id);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.playlist.msg.removeFailed'));
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
      message.success(t('music.playlist.msg.added'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.playlist.msg.addFailed'));
    } finally {
      setAddingId(undefined);
    }
  };

  const columns: TableProps<PlaylistView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('music.playlist.col.playlist'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.description && <span className="music-song-subtitle">{row.description}</span>}
        </div>
      ),
    },
    {
      title: t('music.playlist.col.type'),
      dataIndex: 'playlistType',
      width: 90,
      render: () => <Tag color="blue">{t('music.playlist.tag.official')}</Tag>,
    },
    {
      title: t('music.playlist.col.visibility'),
      dataIndex: 'visibility',
      width: 90,
      render: () => <Tag color="blue">{t('music.playlist.tag.public')}</Tag>,
    },
    {
      title: t('music.playlist.col.songCount'),
      dataIndex: 'songCount',
      width: 90,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: t('music.playlist.col.playCount'),
      dataIndex: 'playCount',
      width: 90,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: t('music.playlist.col.updateTime'),
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('music.common.action'),
      key: 'action',
      width: 220,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openSongs(row)}>
            {t('music.playlist.action.manageSongs')}
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            {t('music.common.edit')}
          </Button>
          <Popconfirm
            title={t('music.playlist.confirm.delete')}
            onConfirm={() => void handleDelete(row)}
          >
            <Button type="link" size="small" danger>
              {t('music.common.delete')}
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
      title: t('music.playlist.track.col.song'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.subtitle && <span className="music-song-subtitle">{row.subtitle}</span>}
        </div>
      ),
    },
    {
      title: t('music.playlist.track.col.artist'),
      dataIndex: 'artistId',
      width: 130,
      render: (value: string | undefined) => artistName(value),
    },
    {
      title: t('music.playlist.track.col.duration'),
      dataIndex: 'durationMs',
      width: 90,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: t('music.common.action'),
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
            {t('music.playlist.track.action.moveUp')}
          </Button>
          <Button
            type="link"
            size="small"
            disabled={index === songs.length - 1}
            onClick={() => handleMove(index, 1)}
          >
            {t('music.playlist.track.action.moveDown')}
          </Button>
          <Popconfirm
            title={t('music.playlist.track.confirmRemove')}
            onConfirm={() => void handleRemove(row.id)}
          >
            <Button type="link" size="small" danger>
              {t('music.playlist.track.action.remove')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const pickerColumns: TableProps<SongView>['columns'] = [
    {
      title: t('music.playlist.track.col.song'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.subtitle && <span className="music-song-subtitle">{row.subtitle}</span>}
        </div>
      ),
    },
    {
      title: t('music.playlist.track.col.artist'),
      dataIndex: 'artistId',
      width: 130,
      render: (value: string | undefined) => artistName(value),
    },
    {
      title: t('music.playlist.track.col.duration'),
      dataIndex: 'durationMs',
      width: 90,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: t('music.common.action'),
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
            {added ? t('music.playlist.picker.added') : t('music.playlist.picker.add')}
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
        title={t('music.playlist.title')}
        columns={columns}
        request={pageOfficialPlaylists}
        params={{ keyword }}
        onError={(error) =>
          message.error(
            error instanceof Error ? error.message : t('music.playlist.msg.listLoadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('music.playlist.search.keyword'),
                element: (
                  <Input allowClear placeholder={t('music.playlist.search.keywordPlaceholder')} />
                ),
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <AuthButton perm="sys:music:playlist:manage" type="primary" onClick={openCreate}>
            {t('music.playlist.action.create')}
          </AuthButton>
        }
      />

      <FormModal<PlaylistFormValues>
        open={modalOpen}
        title={
          editing
            ? t('music.playlist.form.editTitle', { title: editing.title })
            : t('music.playlist.action.create')
        }
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
          label={t('music.playlist.form.title')}
          rules={[{ required: true, message: t('music.playlist.form.titleRequired') }]}
        >
          <Input maxLength={120} showCount />
        </Form.Item>
        <Form.Item name="description" label={t('music.playlist.form.description')}>
          <Input.TextArea
            rows={3}
            maxLength={1000}
            showCount
            placeholder={t('music.common.optional')}
          />
        </Form.Item>
        <Form.Item label={t('music.playlist.form.coverOptional')}>
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
                {coverName
                  ? t('music.playlist.form.reuploadCover')
                  : t('music.playlist.form.selectCover')}
              </Button>
            </Upload>
            {coverName && <Typography.Text type="secondary">{coverName}</Typography.Text>}
          </Space>
        </Form.Item>
        <Form.Item name="coverFileId" hidden>
          <Input />
        </Form.Item>
        <Typography.Text type="secondary">{t('music.playlist.form.publicHint')}</Typography.Text>
      </FormModal>

      <Drawer
        title={
          current
            ? t('music.playlist.drawer.titleWith', { title: current.title })
            : t('music.playlist.drawer.title')
        }
        width={760}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setDirty(false);
          tableRef.current?.reload();
        }}
        extra={
          <Space>
            <Button onClick={() => setPickerOpen(true)}>
              {t('music.playlist.drawer.addSong')}
            </Button>
            <Button
              type="primary"
              loading={savingSort}
              disabled={!dirty || songs.length === 0}
              onClick={() => void handleSaveSort()}
            >
              {t('music.playlist.drawer.saveSort')}
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
                  description={t('music.playlist.drawer.empty')}
                />
              ),
            }}
          />
        )}
      </Drawer>

      <Modal
        open={pickerOpen}
        title={t('music.playlist.picker.title')}
        width={760}
        footer={null}
        onCancel={() => setPickerOpen(false)}
      >
        <Input.Search
          allowClear
          enterButton={t('music.common.search')}
          placeholder={t('music.playlist.picker.placeholder')}
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
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={t('music.playlist.picker.empty')}
              />
            ),
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

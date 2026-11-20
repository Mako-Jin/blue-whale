// 音乐运营 - 曲库：分页查询 / 新增 / 编辑 / 上架 / 下架 / 版权下架 / 删除
// 权限码：sys:music:song:list / add / edit / delete / publish

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Upload,
  type TableProps,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  createSong,
  deleteSong,
  listTags,
  offlineSong,
  pageAlbums,
  pageArtists,
  pageSongs,
  publishSong,
  takedownSong,
  updateSong,
  type AlbumView,
  type ArtistView,
  type CopyrightState,
  type PublishState,
  type SongQuery,
  type SongSaveInput,
  type SongView,
  type SongVisibility,
  type TagView,
} from '../../api/music';
import { uploadFile } from '../../api/file';
import { useI18n } from '../../i18n';

interface SongFormValues {
  artistId: string;
  albumId?: string;
  title: string;
  subtitle?: string;
  language?: string;
  visibility?: SongVisibility;
  sortWeight?: number;
  tagIds?: string[];
  lyricText?: string;
}

export default function SongList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<SongFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [artistId, setArtistId] = useState<string | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [visibility, setVisibility] = useState<SongVisibility | undefined>(undefined);
  const [sortBy, setSortBy] = useState<'NEW' | 'HOT'>('NEW');

  const [artists, setArtists] = useState<ArtistView[]>([]);
  const [albums, setAlbums] = useState<AlbumView[]>([]);
  const [tags, setTags] = useState<TagView[]>([]);

  const [editing, setEditing] = useState<SongView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [audioFileId, setAudioFileId] = useState<string | undefined>(undefined);
  const [audioName, setAudioName] = useState('');
  const [durationMs, setDurationMs] = useState<number | undefined>(undefined);
  const [uploadingAudio, setUploadingAudio] = useState(false);

  const [takedownTarget, setTakedownTarget] = useState<SongView | null>(null);
  const [takedownReason, setTakedownReason] = useState('');

  const languageOptions = [
    { value: 'ZH', label: t('music.song.language.zh') },
    { value: 'EN', label: t('music.song.language.en') },
    { value: 'JA', label: t('music.song.language.ja') },
    { value: 'OTHER', label: t('music.song.language.other') },
  ];

  const visibilityOptions = [
    { value: 'PUBLIC', label: t('music.song.visibility.public') },
    { value: 'PRIVATE', label: t('music.song.visibility.private') },
  ];

  const publishStateOptions = [
    { value: 'DRAFT', label: t('music.song.state.draft') },
    { value: 'PUBLISHED', label: t('music.song.state.published') },
    { value: 'OFFLINE', label: t('music.song.state.offline') },
  ];

  const sortOptions = [
    { value: 'NEW', label: t('music.song.sort.new') },
    { value: 'HOT', label: t('music.song.sort.hot') },
  ];

  /** 发布状态标签 */
  const renderPublishState = (state?: PublishState) => {
    const map: Record<PublishState, { color: string; label: string }> = {
      DRAFT: { color: 'default', label: t('music.song.state.draft') },
      PUBLISHED: { color: 'success', label: t('music.song.state.published') },
      OFFLINE: { color: 'error', label: t('music.song.state.offline') },
    };
    if (!state) {
      return '-';
    }
    return <Tag color={map[state].color}>{map[state].label}</Tag>;
  };

  /** 版权状态标签 */
  const renderCopyrightState = (state?: CopyrightState) => {
    const map: Record<CopyrightState, { color: string; label: string }> = {
      DECLARED: { color: 'gold', label: t('music.song.copyright.declared') },
      VALID: { color: 'success', label: t('music.song.copyright.valid') },
      TAKEDOWN: { color: 'error', label: t('music.song.copyright.takedown') },
    };
    if (!state) {
      return '-';
    }
    return <Tag color={map[state].color}>{map[state].label}</Tag>;
  };

  useEffect(() => {
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch((error) =>
        message.error(error instanceof Error ? error.message : t('music.common.artistsLoadFailed')),
      );
    pageAlbums({ current: 1, size: 200 })
      .then((result) => setAlbums(result.records))
      .catch((error) =>
        message.error(
          error instanceof Error ? error.message : t('music.song.msg.albumsLoadFailed'),
        ),
      );
    listTags()
      .then(setTags)
      .catch((error) =>
        message.error(error instanceof Error ? error.message : t('music.song.msg.tagsLoadFailed')),
      );
  }, [message, t]);

  const artistName = (value?: string) => artists.find((item) => item.id === value)?.name ?? '-';

  const openCreate = () => {
    setEditing(null);
    setAudioFileId(undefined);
    setAudioName('');
    setDurationMs(undefined);
    setModalOpen(true);
  };

  const openEdit = (row: SongView) => {
    setEditing(row);
    setAudioFileId(row.audioFileId);
    setAudioName(row.audioFileId ? t('music.common.fileId', { id: row.audioFileId }) : '');
    setDurationMs(row.durationMs);
    setModalOpen(true);
  };

  const handleAudioSelect = (file: File) => {
    setUploadingAudio(true);
    uploadFile(file)
      .then((uploaded) => {
        setAudioFileId(uploaded.id);
        setAudioName(uploaded.originalName ?? file.name);
        message.success(t('music.song.msg.audioUploaded'));
      })
      .catch((error) =>
        message.error(
          error instanceof Error ? error.message : t('music.song.msg.audioUploadFailed'),
        ),
      )
      .finally(() => setUploadingAudio(false));
  };

  const handleSave = async (values: SongFormValues) => {
    if (!audioFileId) {
      message.warning(t('music.song.msg.audioRequired'));
      throw new Error(t('music.song.msg.audioMissing'));
    }
    const payload: SongSaveInput = {
      artistId: values.artistId,
      albumId: values.albumId,
      title: values.title,
      subtitle: values.subtitle || undefined,
      language: values.language || undefined,
      visibility: values.visibility,
      sortWeight: values.sortWeight,
      tagIds: values.tagIds?.length ? values.tagIds : undefined,
      lyricText: values.lyricText || undefined,
      audioFileId,
      coverFileId: editing?.coverFileId,
      durationMs,
    };
    try {
      if (editing) {
        await updateSong(editing.id, payload);
        message.success(t('music.song.msg.updated'));
      } else {
        await createSong(payload);
        message.success(t('music.song.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.saveFailed'));
      throw error;
    }
  };

  const handlePublish = async (row: SongView) => {
    try {
      await publishSong(row.id);
      message.success(t('music.song.msg.published'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.song.msg.publishFailed'));
    }
  };

  const handleOffline = async (row: SongView) => {
    try {
      await offlineSong(row.id);
      message.success(t('music.song.msg.offlined'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.song.msg.offlineFailed'));
    }
  };

  const handleDelete = async (row: SongView) => {
    try {
      await deleteSong(row.id);
      message.success(t('music.song.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.deleteFailed'));
    }
  };

  const confirmTakedown = async () => {
    if (!takedownTarget) {
      return;
    }
    if (!takedownReason.trim()) {
      message.warning(t('music.song.msg.takedownReasonRequired'));
      return;
    }
    try {
      await takedownSong(takedownTarget.id, takedownReason.trim());
      message.success(t('music.song.msg.takedownDone'));
      setTakedownTarget(null);
      setTakedownReason('');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.song.msg.takedownFailed'));
    }
  };

  const columns: TableProps<SongView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('music.song.col.song'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.subtitle && <span className="music-song-subtitle">{row.subtitle}</span>}
        </div>
      ),
    },
    {
      title: t('music.song.col.artist'),
      dataIndex: 'artistId',
      width: 130,
      render: (value: string | undefined) => artistName(value),
    },
    {
      title: t('music.song.col.visibility'),
      dataIndex: 'visibility',
      width: 90,
      render: (value: SongVisibility | undefined) =>
        value === 'PRIVATE' ? (
          <Tag color="orange">{t('music.song.visibility.private')}</Tag>
        ) : (
          <Tag color="blue">{t('music.song.visibility.public')}</Tag>
        ),
    },
    {
      title: t('music.song.col.state'),
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value),
    },
    {
      title: t('music.song.col.copyright'),
      dataIndex: 'copyrightState',
      width: 100,
      render: (value: CopyrightState | undefined) => renderCopyrightState(value),
    },
    {
      title: t('music.song.col.playCount'),
      dataIndex: 'playCount',
      width: 80,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: t('music.song.col.publishedAt'),
      dataIndex: 'publishedAt',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('music.common.action'),
      key: 'action',
      width: 260,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.publishState !== 'PUBLISHED' && has('sys:music:song:publish') && (
            <Popconfirm
              title={t('music.song.confirm.publish')}
              onConfirm={() => void handlePublish(row)}
            >
              <Button type="link" size="small">
                {t('music.song.action.publish')}
              </Button>
            </Popconfirm>
          )}
          {row.publishState === 'PUBLISHED' && has('sys:music:song:publish') && (
            <Popconfirm
              title={t('music.song.confirm.offline')}
              onConfirm={() => void handleOffline(row)}
            >
              <Button type="link" size="small" danger>
                {t('music.song.action.offline')}
              </Button>
            </Popconfirm>
          )}
          {row.copyrightState !== 'TAKEDOWN' && has('sys:music:song:publish') && (
            <Button
              type="link"
              size="small"
              danger
              onClick={() => {
                setTakedownTarget(row);
                setTakedownReason('');
              }}
            >
              {t('music.song.action.takedown')}
            </Button>
          )}
          {has('sys:music:song:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('music.common.edit')}
            </Button>
          )}
          {has('sys:music:song:delete') && (
            <Popconfirm
              title={t('music.song.confirm.delete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('music.common.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<SongView, SongQuery>
        ref={tableRef}
        rowKey="id"
        title={t('music.song.title')}
        columns={columns}
        request={pageSongs}
        params={{ keyword, artistId, publishState, visibility, sortBy }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : t('music.song.msg.listLoadFailed'))
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('music.song.search.keyword'),
                element: (
                  <Input allowClear placeholder={t('music.song.search.keywordPlaceholder')} />
                ),
              },
              {
                name: 'artistId',
                label: t('music.song.search.artist'),
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder={t('music.common.all')}
                    style={{ width: 160 }}
                    options={artists.map((artist) => ({ value: artist.id, label: artist.name }))}
                  />
                ),
              },
              {
                name: 'publishState',
                label: t('music.song.search.state'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('music.common.all')}
                    style={{ width: 120 }}
                    options={publishStateOptions}
                  />
                ),
              },
              {
                name: 'visibility',
                label: t('music.song.search.visibility'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('music.common.all')}
                    style={{ width: 120 }}
                    options={visibilityOptions}
                  />
                ),
              },
              {
                name: 'sortBy',
                label: t('music.song.search.sort'),
                element: <Select style={{ width: 130 }} options={sortOptions} defaultValue="NEW" />,
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setArtistId((values.artistId as string) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
              setVisibility((values.visibility as SongVisibility) || undefined);
              setSortBy((values.sortBy as 'NEW' | 'HOT') || 'NEW');
            }}
            onReset={() => {
              setKeyword(undefined);
              setArtistId(undefined);
              setPublishState(undefined);
              setVisibility(undefined);
              setSortBy('NEW');
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:music:song:add" type="primary" onClick={openCreate}>
            {t('music.song.action.create')}
          </AuthButton>
        }
      />

      <FormModal<SongFormValues>
        open={modalOpen}
        title={
          editing
            ? t('music.song.form.editTitle', { title: editing.title })
            : t('music.song.action.create')
        }
        form={form}
        width={640}
        initialValues={{
          artistId: editing?.artistId,
          albumId: editing?.albumId,
          title: editing?.title,
          subtitle: editing?.subtitle,
          language: editing?.language,
          visibility: editing?.visibility ?? 'PUBLIC',
          sortWeight: editing?.sortWeight,
          tagIds: editing?.tags?.map((tag) => tag.id),
          lyricText: editing?.lyricText,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item label={t('music.song.form.audio')} required>
          <Space>
            <Upload
              showUploadList={false}
              accept="audio/*"
              beforeUpload={(file) => {
                handleAudioSelect(file);
                return false;
              }}
            >
              <Button loading={uploadingAudio}>
                {audioFileId
                  ? t('music.song.form.reuploadAudio')
                  : t('music.song.form.selectAudio')}
              </Button>
            </Upload>
            {audioName && <span>{audioName}</span>}
          </Space>
        </Form.Item>
        <Form.Item
          name="title"
          label={t('music.song.form.title')}
          rules={[{ required: true, message: t('music.song.form.titleRequired') }]}
        >
          <Input maxLength={120} showCount />
        </Form.Item>
        <Form.Item name="subtitle" label={t('music.song.form.subtitle')}>
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item
          name="artistId"
          label={t('music.song.form.artist')}
          rules={[{ required: true, message: t('music.song.form.artistRequired') }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            options={artists.map((artist) => ({ value: artist.id, label: artist.name }))}
          />
        </Form.Item>
        <Form.Item name="albumId" label={t('music.song.form.album')}>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('music.common.optional')}
            options={albums.map((album) => ({ value: album.id, label: album.title }))}
          />
        </Form.Item>
        <Form.Item name="language" label={t('music.song.form.language')}>
          <Select allowClear options={languageOptions} placeholder={t('music.common.optional')} />
        </Form.Item>
        <Form.Item name="visibility" label={t('music.song.form.visibility')}>
          <Select options={visibilityOptions} />
        </Form.Item>
        <Form.Item name="tagIds" label={t('music.song.form.tags')}>
          <Select
            mode="multiple"
            allowClear
            maxCount={5}
            options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
          />
        </Form.Item>
        <Form.Item
          name="sortWeight"
          label={t('music.song.form.sortWeight')}
          extra={t('music.song.form.sortWeightExtra')}
        >
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="lyricText" label={t('music.song.form.lyric')}>
          <Input.TextArea rows={4} maxLength={5000} showCount />
        </Form.Item>
      </FormModal>

      <Modal
        open={takedownTarget !== null}
        title={
          takedownTarget
            ? t('music.song.takedown.titleWith', { title: takedownTarget.title })
            : t('music.song.takedown.title')
        }
        okText={t('music.song.takedown.confirm')}
        okButtonProps={{ danger: true }}
        onOk={() => void confirmTakedown()}
        onCancel={() => {
          setTakedownTarget(null);
          setTakedownReason('');
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>{t('music.song.takedown.description')}</span>
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            value={takedownReason}
            onChange={(event) => setTakedownReason(event.target.value)}
            placeholder={t('music.song.takedown.reasonPlaceholder')}
          />
        </Space>
      </Modal>
    </>
  );
}

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

interface SongFormValues {
  artistId: number;
  albumId?: number;
  title: string;
  subtitle?: string;
  language?: string;
  visibility?: SongVisibility;
  sortWeight?: number;
  tagIds?: number[];
  lyricText?: string;
}

const LANGUAGE_OPTIONS = [
  { value: 'ZH', label: '中文' },
  { value: 'EN', label: '英文' },
  { value: 'JA', label: '日文' },
  { value: 'OTHER', label: '其他' },
];

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: '公开' },
  { value: 'PRIVATE', label: '自听' },
];

const PUBLISH_STATE_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已上架' },
  { value: 'OFFLINE', label: '已下架' },
];

const SORT_OPTIONS = [
  { value: 'NEW', label: '最新上架' },
  { value: 'HOT', label: '最热' },
];

/** 发布状态标签 */
function renderPublishState(state?: PublishState) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: '草稿' },
    PUBLISHED: { color: 'success', label: '已上架' },
    OFFLINE: { color: 'error', label: '已下架' },
  };
  if (!state) {
    return '-';
  }
  return <Tag color={map[state].color}>{map[state].label}</Tag>;
}

/** 版权状态标签 */
function renderCopyrightState(state?: CopyrightState) {
  const map: Record<CopyrightState, { color: string; label: string }> = {
    DECLARED: { color: 'gold', label: '已声明' },
    VALID: { color: 'success', label: '有效' },
    TAKEDOWN: { color: 'error', label: '版权下架' },
  };
  if (!state) {
    return '-';
  }
  return <Tag color={map[state].color}>{map[state].label}</Tag>;
}

export default function SongList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<SongFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [artistId, setArtistId] = useState<number | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [visibility, setVisibility] = useState<SongVisibility | undefined>(undefined);
  const [sortBy, setSortBy] = useState<'NEW' | 'HOT'>('NEW');

  const [artists, setArtists] = useState<ArtistView[]>([]);
  const [albums, setAlbums] = useState<AlbumView[]>([]);
  const [tags, setTags] = useState<TagView[]>([]);

  const [editing, setEditing] = useState<SongView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [audioFileId, setAudioFileId] = useState<number | undefined>(undefined);
  const [audioName, setAudioName] = useState('');
  const [durationMs, setDurationMs] = useState<number | undefined>(undefined);
  const [uploadingAudio, setUploadingAudio] = useState(false);

  const [takedownTarget, setTakedownTarget] = useState<SongView | null>(null);
  const [takedownReason, setTakedownReason] = useState('');

  useEffect(() => {
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch((error) => message.error(error instanceof Error ? error.message : '歌手加载失败'));
    pageAlbums({ current: 1, size: 200 })
      .then((result) => setAlbums(result.records))
      .catch((error) => message.error(error instanceof Error ? error.message : '专辑加载失败'));
    listTags()
      .then(setTags)
      .catch((error) => message.error(error instanceof Error ? error.message : '标签加载失败'));
  }, [message]);

  const artistName = (value?: number) => artists.find((item) => item.id === value)?.name ?? '-';

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
    setAudioName(row.audioFileId ? `文件 ID ${row.audioFileId}` : '');
    setDurationMs(row.durationMs);
    setModalOpen(true);
  };

  const handleAudioSelect = (file: File) => {
    setUploadingAudio(true);
    uploadFile(file)
      .then((uploaded) => {
        setAudioFileId(uploaded.id);
        setAudioName(uploaded.originalName ?? file.name);
        message.success('音频已上传');
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '音频上传失败'))
      .finally(() => setUploadingAudio(false));
  };

  const handleSave = async (values: SongFormValues) => {
    if (!audioFileId) {
      message.warning('请先上传音频文件');
      throw new Error('缺少音频文件');
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
        message.success('歌曲已更新');
      } else {
        await createSong(payload);
        message.success('歌曲已新增（草稿），上架后用户可见');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handlePublish = async (row: SongView) => {
    try {
      await publishSong(row.id);
      message.success('歌曲已上架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上架失败');
    }
  };

  const handleOffline = async (row: SongView) => {
    try {
      await offlineSong(row.id);
      message.success('歌曲已下架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下架失败');
    }
  };

  const handleDelete = async (row: SongView) => {
    try {
      await deleteSong(row.id);
      message.success('歌曲已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const confirmTakedown = async () => {
    if (!takedownTarget) {
      return;
    }
    if (!takedownReason.trim()) {
      message.warning('请填写版权下架原因');
      return;
    }
    try {
      await takedownSong(takedownTarget.id, takedownReason.trim());
      message.success('已完成版权下架');
      setTakedownTarget(null);
      setTakedownReason('');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '版权下架失败');
    }
  };

  const columns: TableProps<SongView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
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
      render: (value: number | undefined) => artistName(value),
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      render: (value: SongVisibility | undefined) =>
        value === 'PRIVATE' ? <Tag color="orange">自听</Tag> : <Tag color="blue">公开</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value),
    },
    {
      title: '版权',
      dataIndex: 'copyrightState',
      width: 100,
      render: (value: CopyrightState | undefined) => renderCopyrightState(value),
    },
    {
      title: '播放',
      dataIndex: 'playCount',
      width: 80,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: '上架时间',
      dataIndex: 'publishedAt',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.publishState !== 'PUBLISHED' && has('sys:music:song:publish') && (
            <Popconfirm title="确认上架该歌曲？" onConfirm={() => void handlePublish(row)}>
              <Button type="link" size="small">
                上架
              </Button>
            </Popconfirm>
          )}
          {row.publishState === 'PUBLISHED' && has('sys:music:song:publish') && (
            <Popconfirm title="确认下架该歌曲？" onConfirm={() => void handleOffline(row)}>
              <Button type="link" size="small" danger>
                下架
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
              版权下架
            </Button>
          )}
          {has('sys:music:song:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:music:song:delete') && (
            <Popconfirm title="确认删除该歌曲？" onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
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
        title="曲库"
        columns={columns}
        request={pageSongs}
        params={{ keyword, artistId, publishState, visibility, sortBy }}
        onError={(error) => message.error(error instanceof Error ? error.message : '歌曲列表加载失败')}
        search={
          <SearchForm
            items={[
              { name: 'keyword', label: '关键词', element: <Input allowClear placeholder="标题 / 副标题" /> },
              {
                name: 'artistId',
                label: '歌手',
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="全部"
                    style={{ width: 160 }}
                    options={artists.map((artist) => ({ value: artist.id, label: artist.name }))}
                  />
                ),
              },
              {
                name: 'publishState',
                label: '状态',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={PUBLISH_STATE_OPTIONS} />
                ),
              },
              {
                name: 'visibility',
                label: '可见性',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={VISIBILITY_OPTIONS} />
                ),
              },
              {
                name: 'sortBy',
                label: '排序',
                element: <Select style={{ width: 130 }} options={SORT_OPTIONS} defaultValue="NEW" />,
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setArtistId((values.artistId as number) || undefined);
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
            新增歌曲
          </AuthButton>
        }
      />

      <FormModal<SongFormValues>
        open={modalOpen}
        title={editing ? `编辑歌曲：${editing.title}` : '新增歌曲'}
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
        <Form.Item label="音频文件（必填）" required>
          <Space>
            <Upload
              showUploadList={false}
              accept="audio/*"
              beforeUpload={(file) => {
                handleAudioSelect(file);
                return false;
              }}
            >
              <Button loading={uploadingAudio}>{audioFileId ? '重新上传音频' : '选择音频文件'}</Button>
            </Upload>
            {audioName && <span>{audioName}</span>}
          </Space>
        </Form.Item>
        <Form.Item name="title" label="歌曲标题" rules={[{ required: true, message: '请输入歌曲标题' }]}>
          <Input maxLength={120} showCount />
        </Form.Item>
        <Form.Item name="subtitle" label="副标题">
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="artistId" label="歌手" rules={[{ required: true, message: '请选择歌手' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            options={artists.map((artist) => ({ value: artist.id, label: artist.name }))}
          />
        </Form.Item>
        <Form.Item name="albumId" label="专辑">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="可选"
            options={albums.map((album) => ({ value: album.id, label: album.title }))}
          />
        </Form.Item>
        <Form.Item name="language" label="语言">
          <Select allowClear options={LANGUAGE_OPTIONS} placeholder="可选" />
        </Form.Item>
        <Form.Item name="visibility" label="可见性">
          <Select options={VISIBILITY_OPTIONS} />
        </Form.Item>
        <Form.Item name="tagIds" label="标签">
          <Select
            mode="multiple"
            allowClear
            maxCount={5}
            options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
          />
        </Form.Item>
        <Form.Item name="sortWeight" label="排序权重" extra="数值越大越靠前">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="lyricText" label="歌词（纯文本）">
          <Input.TextArea rows={4} maxLength={5000} showCount />
        </Form.Item>
      </FormModal>

      <Modal
        open={takedownTarget !== null}
        title={takedownTarget ? `版权下架：${takedownTarget.title}` : '版权下架'}
        okText="确认下架"
        okButtonProps={{ danger: true }}
        onOk={() => void confirmTakedown()}
        onCancel={() => {
          setTakedownTarget(null);
          setTakedownReason('');
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>版权下架将强制下线并拒绝播放，请填写原因（留痕可追溯）。</span>
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            value={takedownReason}
            onChange={(event) => setTakedownReason(event.target.value)}
            placeholder="如：权利人投诉 / 授权到期"
          />
        </Space>
      </Modal>
    </>
  );
}

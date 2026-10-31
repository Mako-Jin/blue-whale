// 我的作品：公开 / 自听筛选、编辑与删除（含自听作品，列表返回运营视图便于回填）

import { App, Button, Form, Input, Popconfirm, Select, Space, Tag, type TableProps } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  FormModal,
  ProTable,
  SearchForm,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  deleteMySong,
  listTags,
  pageAlbums,
  pageArtists,
  pageMySongs,
  updateMySong,
  type AlbumView,
  type ArtistView,
  type SongQuery,
  type SongView,
  type SongVisibility,
  type TagView,
} from '../api/music';
import { formatDuration, renderPublishState, renderTags, renderVisibility } from '../utils/musicDisplay';

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

interface EditFormValues {
  title: string;
  subtitle?: string;
  artistId: number;
  albumId?: number;
  language?: string;
  visibility: SongVisibility;
  tagIds?: number[];
  lyricText?: string;
}

export default function MySongsPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const tableRef = useRef<ProTableRef>(null);
  const [editForm] = Form.useForm<EditFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [visibility, setVisibility] = useState<SongVisibility | undefined>(undefined);

  const [artists, setArtists] = useState<ArtistView[]>([]);
  const [albums, setAlbums] = useState<AlbumView[]>([]);
  const [tags, setTags] = useState<TagView[]>([]);

  const [editing, setEditing] = useState<SongView | null>(null);
  const [editOpen, setEditOpen] = useState(false);

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

  const artistName = (artistId?: number) =>
    artists.find((item) => item.id === artistId)?.name ?? '-';

  const openEdit = (row: SongView) => {
    setEditing(row);
    setEditOpen(true);
  };

  const handleSave = async (values: EditFormValues) => {
    if (!editing) {
      return;
    }
    if (!editing.audioFileId) {
      message.error('该作品缺少音频文件标识，无法编辑，请联系管理员');
      return;
    }
    try {
      await updateMySong(editing.id, {
        artistId: values.artistId,
        albumId: values.albumId,
        title: values.title,
        subtitle: values.subtitle || undefined,
        language: values.language || undefined,
        visibility: values.visibility,
        tagIds: values.tagIds?.length ? values.tagIds : undefined,
        lyricText: values.lyricText || undefined,
        audioFileId: editing.audioFileId,
        coverFileId: editing.coverFileId,
        durationMs: editing.durationMs,
      });
      message.success('作品已更新');
      setEditOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新失败');
      throw error;
    }
  };

  const handleDelete = async (row: SongView) => {
    try {
      await deleteMySong(row.id);
      message.success('作品已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<SongView>['columns'] = [
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
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      render: (value: SongVisibility | undefined) => renderVisibility(value),
    },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value: SongView['publishState']) => renderPublishState(value),
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 90,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: '播放',
      dataIndex: 'playCount',
      width: 80,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: '标签',
      dataIndex: 'tags',
      render: (value: TagView[] | undefined) => renderTags(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4}>
          {row.publishState === 'PUBLISHED' && row.visibility === 'PUBLIC' && (
            <a onClick={() => navigate(`/song/${row.id}`)}>查看详情</a>
          )}
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该作品？" onConfirm={() => void handleDelete(row)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const watchedArtistId = Form.useWatch('artistId', editForm);
  const albumOptions = albums
    .filter((album) =>
      (watchedArtistId ?? editing?.artistId)
        ? album.artistId === (watchedArtistId ?? editing?.artistId)
        : true,
    )
    .map((album) => ({ value: album.id, label: album.title }));

  return (
    <>
      <ProTable<SongView, SongQuery>
        ref={tableRef}
        rowKey="id"
        title="我的作品"
        columns={columns}
        request={pageMySongs}
        params={{ keyword, visibility }}
        onError={(error) => message.error(error instanceof Error ? error.message : '作品加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键词',
                element: <Input allowClear placeholder="歌曲标题 / 副标题" />,
              },
              {
                name: 'visibility',
                label: '可见性',
                element: (
                  <Select
                    allowClear
                    placeholder="全部"
                    style={{ width: 120 }}
                    options={VISIBILITY_OPTIONS}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setVisibility((values.visibility as SongVisibility) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setVisibility(undefined);
            }}
          />
        }
        toolbar={
          <Button type="primary" onClick={() => navigate('/upload')}>
            上传新作品
          </Button>
        }
      />

      <FormModal<EditFormValues>
        open={editOpen}
        title={editing ? `编辑作品：${editing.title}` : '编辑作品'}
        form={editForm}
        width={640}
        initialValues={{
          title: editing?.title,
          subtitle: editing?.subtitle,
          artistId: editing?.artistId,
          albumId: editing?.albumId,
          language: editing?.language,
          visibility: editing?.visibility ?? 'PUBLIC',
          tagIds: editing?.tags?.map((tag) => tag.id),
          lyricText: editing?.lyricText,
        }}
        onOk={handleSave}
        onCancel={() => setEditOpen(false)}
      >
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
          <Select allowClear options={albumOptions} placeholder="可选" />
        </Form.Item>
        <Form.Item name="language" label="语言">
          <Select allowClear options={LANGUAGE_OPTIONS} placeholder="可选" />
        </Form.Item>
        <Form.Item
          name="visibility"
          label="可见性"
          rules={[{ required: true, message: '请选择可见性' }]}
        >
          <Select
            options={[
              { value: 'PUBLIC', label: '公开（租户内可见）' },
              { value: 'PRIVATE', label: '仅自听（仅本人可见）' },
            ]}
          />
        </Form.Item>
        <Form.Item name="tagIds" label="标签">
          <Select
            mode="multiple"
            allowClear
            maxCount={5}
            options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
          />
        </Form.Item>
        <Form.Item name="lyricText" label="歌词（纯文本）">
          <Input.TextArea rows={4} maxLength={5000} showCount />
        </Form.Item>
        {editing?.visibility === 'PRIVATE' && (
          <Tag color="orange">自听作品切换为公开后，其他用户方可检索与播放</Tag>
        )}
      </FormModal>
    </>
  );
}

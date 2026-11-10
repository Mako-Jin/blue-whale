// 音乐运营 - 专辑管理：分页 / 新增 / 编辑 / 上架 / 下架 / 删除
// 权限码：sys:music:album:list / add / edit / delete / publish

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
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
  createAlbum,
  deleteAlbum,
  offlineAlbum,
  pageAlbums,
  pageArtists,
  publishAlbum,
  updateAlbum,
  type AlbumQuery,
  type AlbumSaveInput,
  type AlbumView,
  type ArtistView,
  type PublishState,
} from '../../api/music';
import { uploadFile } from '../../api/file';

interface AlbumFormValues {
  artistId: number;
  title: string;
  coverFileId: number;
  releaseDate?: string;
  albumType?: string;
  intro?: string;
  sortNo?: number;
}

const ALBUM_TYPE_OPTIONS = [
  { value: 'SINGLE', label: '单曲' },
  { value: 'EP', label: 'EP' },
  { value: 'ALBUM', label: '专辑' },
  { value: 'COMPILATION', label: '合辑' },
];

const PUBLISH_STATE_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已上架' },
  { value: 'OFFLINE', label: '已下架' },
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

export default function AlbumList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<AlbumFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [artists, setArtists] = useState<ArtistView[]>([]);

  const [editing, setEditing] = useState<AlbumView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [coverName, setCoverName] = useState('');
  const [uploadingCover, setUploadingCover] = useState(false);

  useEffect(() => {
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch((error) => message.error(error instanceof Error ? error.message : '歌手加载失败'));
  }, [message]);

  const artistName = (artistId?: number) =>
    artists.find((item) => item.id === artistId)?.name ?? '-';

  const openCreate = () => {
    setEditing(null);
    setCoverName('');
    setModalOpen(true);
  };

  const openEdit = (row: AlbumView) => {
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

  const handleSave = async (values: AlbumFormValues) => {
    const payload: AlbumSaveInput = {
      artistId: values.artistId,
      title: values.title,
      coverFileId: values.coverFileId,
      releaseDate: values.releaseDate || undefined,
      albumType: values.albumType,
      intro: values.intro || undefined,
      sortNo: values.sortNo,
    };
    try {
      if (editing) {
        await updateAlbum(editing.id, payload);
        message.success('专辑已更新');
      } else {
        await createAlbum(payload);
        message.success('专辑已新增');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handlePublish = async (row: AlbumView) => {
    try {
      await publishAlbum(row.id);
      message.success('专辑已上架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上架失败');
    }
  };

  const handleOffline = async (row: AlbumView) => {
    try {
      await offlineAlbum(row.id);
      message.success('专辑已下架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下架失败');
    }
  };

  const handleDelete = async (row: AlbumView) => {
    try {
      await deleteAlbum(row.id);
      message.success('专辑已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<AlbumView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '标题', dataIndex: 'title' },
    {
      title: '歌手',
      dataIndex: 'artistId',
      width: 140,
      render: (value: number | undefined) => artistName(value),
    },
    {
      title: '发行日期',
      dataIndex: 'releaseDate',
      width: 120,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '类型',
      dataIndex: 'albumType',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.publishState !== 'PUBLISHED' && has('sys:music:album:publish') && (
            <Popconfirm title="确认上架该专辑？" onConfirm={() => void handlePublish(row)}>
              <Button type="link" size="small">
                上架
              </Button>
            </Popconfirm>
          )}
          {row.publishState === 'PUBLISHED' && has('sys:music:album:publish') && (
            <Popconfirm title="确认下架该专辑？" onConfirm={() => void handleOffline(row)}>
              <Button type="link" size="small" danger>
                下架
              </Button>
            </Popconfirm>
          )}
          {has('sys:music:album:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:music:album:delete') && (
            <Popconfirm title="确认删除该专辑？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<AlbumView, AlbumQuery>
        ref={tableRef}
        rowKey="id"
        title="专辑"
        columns={columns}
        request={pageAlbums}
        params={{ keyword, publishState }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : '专辑列表加载失败')
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '标题',
                element: <Input allowClear placeholder="专辑标题" />,
              },
              {
                name: 'publishState',
                label: '状态',
                element: (
                  <Select
                    allowClear
                    placeholder="全部"
                    style={{ width: 120 }}
                    options={PUBLISH_STATE_OPTIONS}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setPublishState(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:music:album:add" type="primary" onClick={openCreate}>
            新增专辑
          </AuthButton>
        }
      />

      <FormModal<AlbumFormValues>
        open={modalOpen}
        title={editing ? `编辑专辑：${editing.title}` : '新增专辑'}
        form={form}
        width={640}
        initialValues={{
          artistId: editing?.artistId,
          title: editing?.title,
          coverFileId: editing?.coverFileId,
          releaseDate: editing?.releaseDate,
          albumType: editing?.albumType ?? 'ALBUM',
          intro: editing?.intro,
          sortNo: editing?.sortNo,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item name="artistId" label="歌手" rules={[{ required: true, message: '请选择歌手' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            options={artists.map((artist) => ({ value: artist.id, label: artist.name }))}
          />
        </Form.Item>
        <Form.Item
          name="title"
          label="专辑标题"
          rules={[{ required: true, message: '请输入专辑标题' }]}
        >
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item label="封面（必填）" required>
          <Space direction="vertical">
            <Space>
              <Upload
                showUploadList={false}
                accept="image/*"
                beforeUpload={(file) => {
                  handleCoverSelect(file);
                  return false;
                }}
              >
                <Button loading={uploadingCover}>选择封面图片</Button>
              </Upload>
              {coverName && <span>{coverName}</span>}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item
          name="coverFileId"
          hidden
          rules={[{ required: true, message: '请上传专辑封面' }]}
        >
          <InputNumber />
        </Form.Item>
        <Form.Item name="releaseDate" label="发行日期" extra="格式 YYYY-MM-DD">
          <Input placeholder="如 2026-10-31" />
        </Form.Item>
        <Form.Item name="albumType" label="专辑类型">
          <Select options={ALBUM_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="intro" label="简介">
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
        <Form.Item name="sortNo" label="排序">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}

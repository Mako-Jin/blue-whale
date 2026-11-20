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
import { useI18n } from '../../i18n';

interface AlbumFormValues {
  artistId: string;
  title: string;
  coverFileId: string;
  releaseDate?: string;
  albumType?: string;
  intro?: string;
  sortNo?: number;
}

export default function AlbumList() {
  const { message } = App.useApp();
  const { t } = useI18n();
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

  const albumTypeOptions = [
    { value: 'SINGLE', label: t('music.album.type.single') },
    { value: 'EP', label: t('music.album.type.ep') },
    { value: 'ALBUM', label: t('music.album.type.album') },
    { value: 'COMPILATION', label: t('music.album.type.compilation') },
  ];

  const publishStateOptions = [
    { value: 'DRAFT', label: t('music.album.state.draft') },
    { value: 'PUBLISHED', label: t('music.album.state.published') },
    { value: 'OFFLINE', label: t('music.album.state.offline') },
  ];

  /** 发布状态标签 */
  const renderPublishState = (state?: PublishState) => {
    const map: Record<PublishState, { color: string; label: string }> = {
      DRAFT: { color: 'default', label: t('music.album.state.draft') },
      PUBLISHED: { color: 'success', label: t('music.album.state.published') },
      OFFLINE: { color: 'error', label: t('music.album.state.offline') },
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
  }, [message, t]);

  const artistName = (artistId?: string) =>
    artists.find((item) => item.id === artistId)?.name ?? '-';

  const openCreate = () => {
    setEditing(null);
    setCoverName('');
    setModalOpen(true);
  };

  const openEdit = (row: AlbumView) => {
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
        message.success(t('music.album.msg.updated'));
      } else {
        await createAlbum(payload);
        message.success(t('music.album.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.saveFailed'));
      throw error;
    }
  };

  const handlePublish = async (row: AlbumView) => {
    try {
      await publishAlbum(row.id);
      message.success(t('music.album.msg.published'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.album.msg.publishFailed'));
    }
  };

  const handleOffline = async (row: AlbumView) => {
    try {
      await offlineAlbum(row.id);
      message.success(t('music.album.msg.offlined'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.album.msg.offlineFailed'));
    }
  };

  const handleDelete = async (row: AlbumView) => {
    try {
      await deleteAlbum(row.id);
      message.success(t('music.album.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.deleteFailed'));
    }
  };

  const columns: TableProps<AlbumView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('music.album.col.title'), dataIndex: 'title' },
    {
      title: t('music.album.col.artist'),
      dataIndex: 'artistId',
      width: 140,
      render: (value: string | undefined) => artistName(value),
    },
    {
      title: t('music.album.col.releaseDate'),
      dataIndex: 'releaseDate',
      width: 120,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('music.album.col.type'),
      dataIndex: 'albumType',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('music.album.col.state'),
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value),
    },
    {
      title: t('music.common.action'),
      key: 'action',
      width: 220,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.publishState !== 'PUBLISHED' && has('sys:music:album:publish') && (
            <Popconfirm
              title={t('music.album.confirm.publish')}
              onConfirm={() => void handlePublish(row)}
            >
              <Button type="link" size="small">
                {t('music.album.action.publish')}
              </Button>
            </Popconfirm>
          )}
          {row.publishState === 'PUBLISHED' && has('sys:music:album:publish') && (
            <Popconfirm
              title={t('music.album.confirm.offline')}
              onConfirm={() => void handleOffline(row)}
            >
              <Button type="link" size="small" danger>
                {t('music.album.action.offline')}
              </Button>
            </Popconfirm>
          )}
          {has('sys:music:album:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('music.common.edit')}
            </Button>
          )}
          {has('sys:music:album:delete') && (
            <Popconfirm
              title={t('music.album.confirm.delete')}
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
      <ProTable<AlbumView, AlbumQuery>
        ref={tableRef}
        rowKey="id"
        title={t('music.album.title')}
        columns={columns}
        request={pageAlbums}
        params={{ keyword, publishState }}
        onError={(error) =>
          message.error(
            error instanceof Error ? error.message : t('music.album.msg.listLoadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('music.album.search.title'),
                element: (
                  <Input allowClear placeholder={t('music.album.search.titlePlaceholder')} />
                ),
              },
              {
                name: 'publishState',
                label: t('music.album.search.state'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('music.common.all')}
                    style={{ width: 120 }}
                    options={publishStateOptions}
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
            {t('music.album.action.create')}
          </AuthButton>
        }
      />

      <FormModal<AlbumFormValues>
        open={modalOpen}
        title={
          editing
            ? t('music.album.form.editTitle', { title: editing.title })
            : t('music.album.action.create')
        }
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
        <Form.Item
          name="artistId"
          label={t('music.album.form.artist')}
          rules={[{ required: true, message: t('music.album.form.artistRequired') }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            options={artists.map((artist) => ({ value: artist.id, label: artist.name }))}
          />
        </Form.Item>
        <Form.Item
          name="title"
          label={t('music.album.form.title')}
          rules={[{ required: true, message: t('music.album.form.titleRequired') }]}
        >
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item label={t('music.album.form.cover')} required>
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
                <Button loading={uploadingCover}>{t('music.album.form.selectCover')}</Button>
              </Upload>
              {coverName && <span>{coverName}</span>}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item
          name="coverFileId"
          hidden
          rules={[{ required: true, message: t('music.album.form.coverRequired') }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="releaseDate"
          label={t('music.album.form.releaseDate')}
          extra={t('music.album.form.releaseDateExtra')}
        >
          <Input placeholder={t('music.album.form.releaseDatePlaceholder')} />
        </Form.Item>
        <Form.Item name="albumType" label={t('music.album.form.type')}>
          <Select options={albumTypeOptions} />
        </Form.Item>
        <Form.Item name="intro" label={t('music.album.form.intro')}>
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
        <Form.Item name="sortNo" label={t('music.album.form.sort')}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}

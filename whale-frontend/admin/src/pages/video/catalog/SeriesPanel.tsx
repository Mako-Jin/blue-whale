// 视频运营 - 系列管理（M3）：分页 / 新增 / 编辑 / 上架 / 下架 / 删除（分类只读筛选）
// 权限码：sys:video:series:manage（分类 / 系列查询走 sys:video:browse + series:manage）

import {
  App,
  Button,
  Form,
  Image,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
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
  createSeries,
  deleteSeries,
  listVideoCategories,
  offlineSeries,
  pageSeries,
  publishSeries,
  updateSeries,
  type CategoryView,
  type PublishState,
  type SeriesQuery,
  type SeriesSaveInput,
  type SeriesView,
} from '../../../api/video';
import { uploadFile } from '../../../api/file';
import { useI18n } from '../../../i18n';

type Translate = ReturnType<typeof useI18n>['t'];

interface SeriesFormValues {
  title: string;
  description?: string;
  categoryId?: string;
  creatorId?: string;
  sortNo?: number;
}

/** 发布状态选项（系列无待审核） */
const buildPublishStateOptions = (t: Translate) => [
  { value: 'DRAFT', label: t('video.catalog.series.state.draft') },
  { value: 'PUBLISHED', label: t('video.catalog.series.state.published') },
  { value: 'OFFLINE', label: t('video.catalog.series.state.offline') },
];

/** 发布状态标签 */
function renderPublishState(state: PublishState | undefined, t: Translate) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: t('video.catalog.series.state.draft') },
    PENDING_REVIEW: { color: 'gold', label: t('video.catalog.series.state.pendingReview') },
    PUBLISHED: { color: 'success', label: t('video.catalog.series.state.published') },
    OFFLINE: { color: 'error', label: t('video.catalog.series.state.offline') },
  };
  return state ? <Tag color={map[state].color}>{map[state].label}</Tag> : '-';
}

export default function SeriesPanel() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const publishStateOptions = buildPublishStateOptions(t);
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<SeriesFormValues>();

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined);

  const [editing, setEditing] = useState<SeriesView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [coverFileId, setCoverFileId] = useState<string | undefined>(undefined);
  const [coverName, setCoverName] = useState('');
  const [uploadingCover, setUploadingCover] = useState(false);

  useEffect(() => {
    listVideoCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    setCoverFileId(undefined);
    setCoverName('');
    setModalOpen(true);
  };

  const openEdit = (row: SeriesView) => {
    setEditing(row);
    setCoverFileId(undefined);
    setCoverName('');
    setModalOpen(true);
  };

  const handleCoverSelect = (file: File) => {
    setUploadingCover(true);
    uploadFile(file)
      .then((uploaded) => {
        setCoverFileId(uploaded.id);
        setCoverName(uploaded.originalName ?? file.name);
        message.success(t('video.catalog.series.msg.coverUploaded'));
      })
      .catch((error) =>
        message.error(
          error instanceof Error ? error.message : t('video.catalog.series.msg.coverUploadFailed'),
        ),
      )
      .finally(() => setUploadingCover(false));
  };

  const handleSave = async (values: SeriesFormValues) => {
    const payload: SeriesSaveInput = {
      title: values.title,
      description: values.description || undefined,
      categoryId: values.categoryId,
      creatorId: values.creatorId,
      sortNo: values.sortNo,
    };
    if (coverFileId !== undefined) {
      payload.coverFileId = coverFileId;
    }
    try {
      if (editing) {
        await updateSeries(editing.id, payload);
        message.success(t('video.catalog.series.msg.updated'));
      } else {
        await createSeries(payload);
        message.success(t('video.catalog.series.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.series.msg.saveFailed'),
      );
      throw error;
    }
  };

  const handlePublish = async (row: SeriesView) => {
    try {
      await publishSeries(row.id);
      message.success(t('video.catalog.series.msg.published'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.series.msg.publishFailed'),
      );
    }
  };

  const handleOffline = async (row: SeriesView) => {
    try {
      await offlineSeries(row.id);
      message.success(t('video.catalog.series.msg.offlined'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.series.msg.offlineFailed'),
      );
    }
  };

  const handleDelete = async (row: SeriesView) => {
    try {
      await deleteSeries(row.id);
      message.success(t('video.catalog.series.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.series.msg.deleteFailed'),
      );
    }
  };

  const categoryName = (value?: string) =>
    categories.find((item) => item.id === value)?.name ?? '-';

  const columns: TableProps<SeriesView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('video.catalog.series.col.cover'),
      dataIndex: 'coverUrl',
      width: 100,
      render: (value?: string) =>
        value ? (
          <Image
            src={value}
            alt={t('video.catalog.series.alt.cover')}
            width={72}
            height={40}
            style={{ objectFit: 'cover' }}
          />
        ) : (
          '-'
        ),
    },
    {
      title: t('video.catalog.series.col.series'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.description ? (
            <Typography.Text type="secondary" ellipsis style={{ maxWidth: 320 }}>
              {row.description}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: t('video.catalog.series.col.category'),
      dataIndex: 'categoryId',
      width: 120,
      render: (value: string | undefined) => categoryName(value),
    },
    {
      title: t('video.catalog.series.col.creator'),
      dataIndex: 'creatorName',
      width: 140,
      render: (value: string | undefined, row) =>
        value || (row.creatorId && row.creatorId !== '0' ? `#${row.creatorId}` : '-'),
    },
    { title: t('video.catalog.series.col.episodeCount'), dataIndex: 'episodeCount', width: 80 },
    { title: t('video.catalog.series.col.sortNo'), dataIndex: 'sortNo', width: 80 },
    {
      title: t('video.catalog.series.col.status'),
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value, t),
    },
    {
      title: t('video.catalog.series.col.publishedAt'),
      dataIndex: 'publishedAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('video.catalog.series.col.action'),
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={4} wrap>
          {has('sys:video:series:manage') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('video.catalog.series.action.edit')}
            </Button>
          ) : null}
          {row.publishState !== 'PUBLISHED' && has('sys:video:series:manage') ? (
            <Popconfirm
              title={t('video.catalog.series.confirm.publish')}
              onConfirm={() => void handlePublish(row)}
            >
              <Button type="link" size="small">
                {t('video.catalog.series.action.publish')}
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:series:manage') ? (
            <Popconfirm
              title={t('video.catalog.series.confirm.offline')}
              onConfirm={() => void handleOffline(row)}
            >
              <Button type="link" size="small" danger>
                {t('video.catalog.series.action.offline')}
              </Button>
            </Popconfirm>
          ) : null}
          {has('sys:video:series:manage') ? (
            <Popconfirm
              title={t('video.catalog.series.confirm.delete')}
              description={
                row.episodeCount > 0
                  ? t('video.catalog.series.confirm.deleteWithEpisodes')
                  : undefined
              }
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('video.catalog.series.action.delete')}
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<SeriesView, SeriesQuery>
        ref={tableRef}
        rowKey="id"
        title={t('video.catalog.series.list.title')}
        columns={columns}
        request={pageSeries}
        params={{ keyword, publishState, categoryId }}
        onError={(error) =>
          message.error(
            error instanceof Error ? error.message : t('video.catalog.series.list.loadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('video.catalog.series.search.keyword'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('video.catalog.series.search.keywordPlaceholder')}
                  />
                ),
              },
              {
                name: 'publishState',
                label: t('video.catalog.series.search.status'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.catalog.series.search.all')}
                    style={{ width: 120 }}
                    options={publishStateOptions}
                  />
                ),
              },
              {
                name: 'categoryId',
                label: t('video.catalog.series.search.category'),
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder={t('video.catalog.series.search.all')}
                    style={{ width: 160 }}
                    options={categories.map((category) => ({
                      value: category.id,
                      label: category.name,
                    }))}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
              setCategoryId((values.categoryId as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setPublishState(undefined);
              setCategoryId(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:video:series:manage" type="primary" onClick={openCreate}>
            {t('video.catalog.series.action.create')}
          </AuthButton>
        }
      />

      <FormModal<SeriesFormValues>
        open={modalOpen}
        title={
          editing
            ? t('video.catalog.series.form.editTitle', { title: editing.title })
            : t('video.catalog.series.form.createTitle')
        }
        form={form}
        width={640}
        initialValues={{
          title: editing?.title,
          description: editing?.description,
          categoryId: editing?.categoryId,
          creatorId: editing?.creatorId,
          sortNo: editing?.sortNo,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item label={t('video.catalog.series.form.cover')}>
          <Space direction="vertical" size={6}>
            {coverFileId === '0' ? (
              <Typography.Text type="secondary">
                {t('video.catalog.series.form.coverCleared')}
              </Typography.Text>
            ) : coverFileId ? (
              <Typography.Text type="secondary">
                {t('video.catalog.series.form.coverUploaded', {
                  name: coverName || t('video.catalog.series.form.fileId', { id: coverFileId }),
                })}
              </Typography.Text>
            ) : editing?.coverUrl ? (
              <Image
                src={editing.coverUrl}
                width={160}
                height={90}
                style={{ objectFit: 'cover' }}
                alt={t('video.catalog.series.alt.currentCover')}
              />
            ) : (
              <Typography.Text type="secondary">
                {t('video.catalog.series.form.coverEmpty')}
              </Typography.Text>
            )}
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
                  {coverFileId !== '0' && (coverFileId || editing?.coverUrl)
                    ? t('video.catalog.series.form.coverReupload')
                    : t('video.catalog.series.form.coverUpload')}
                </Button>
              </Upload>
              {editing?.coverUrl || (coverFileId && coverFileId !== '0') ? (
                <Button
                  danger
                  onClick={() => {
                    setCoverFileId('0');
                    setCoverName('');
                  }}
                >
                  {t('video.catalog.series.form.coverRemove')}
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item
          name="title"
          label={t('video.catalog.series.form.title')}
          rules={[{ required: true, message: t('video.catalog.series.form.titleRequired') }]}
        >
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label={t('video.catalog.series.form.description')}>
          <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Form.Item
          name="categoryId"
          label={t('video.catalog.series.form.category')}
          extra={t('video.catalog.series.form.categoryExtra')}
        >
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('video.catalog.series.form.categoryPlaceholder')}
            options={categories
              .filter(
                (category) =>
                  category.contentTypeScope === 'ALL' || category.contentTypeScope === 'VIDEO',
              )
              .map((category) => ({ value: category.id, label: category.name }))}
          />
        </Form.Item>
        <Form.Item
          name="creatorId"
          label={t('video.catalog.series.form.creatorId')}
          extra={t('video.catalog.series.form.creatorIdExtra')}
        >
          <Input placeholder={t('video.catalog.series.form.creatorIdPlaceholder')} />
        </Form.Item>
        <Form.Item
          name="sortNo"
          label={t('video.catalog.series.form.sortNo')}
          extra={t('video.catalog.series.form.sortNoExtra')}
        >
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}

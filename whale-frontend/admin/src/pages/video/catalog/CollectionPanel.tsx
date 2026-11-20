// 视频运营 - 片单管理（M4）：分页 / 新增 / 编辑 / 上架 / 下架 / 删除 + 片单内电影加片与移除
// 权限码：sys:video:content:list / add / edit / delete + sys:video:movie:manage（片单维护与加片）

import {
  App,
  Button,
  Drawer,
  Empty,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Upload,
  type TableProps,
} from 'antd';
import { useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  addCollectionItem,
  createCollection,
  deleteCollection,
  getCollection,
  offlineCollection,
  pageCollections,
  pageMovies,
  publishCollection,
  removeCollectionItem,
  updateCollection,
  type CollectionQuery,
  type CollectionSaveInput,
  type MovieAdminView,
  type MovieCollectionAdminDetailView,
  type MovieCollectionAdminView,
  type MoviePublicView,
  type PublishState,
} from '../../../api/video';
import { uploadFile } from '../../../api/file';
import { useI18n } from '../../../i18n';

type Translate = ReturnType<typeof useI18n>['t'];

interface CollectionFormValues {
  title: string;
  description?: string;
  sortNo?: number;
}

/** 发布状态选项（片单无待审核） */
const buildPublishStateOptions = (t: Translate) => [
  { value: 'DRAFT', label: t('video.catalog.collection.state.draft') },
  { value: 'PUBLISHED', label: t('video.catalog.collection.state.published') },
  { value: 'OFFLINE', label: t('video.catalog.collection.state.offline') },
];

/** 选片弹窗每页条数 */
const PICKER_PAGE_SIZE = 10;

/** 发布状态标签 */
function renderPublishState(state: PublishState | undefined, t: Translate) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: t('video.catalog.collection.state.draft') },
    PENDING_REVIEW: {
      color: 'gold',
      label: t('video.catalog.collection.state.pendingReview'),
    },
    PUBLISHED: { color: 'success', label: t('video.catalog.collection.state.published') },
    OFFLINE: { color: 'error', label: t('video.catalog.collection.state.offline') },
  };
  return state ? <Tag color={map[state].color}>{map[state].label}</Tag> : '-';
}

/** 时长毫秒 → mm:ss */
function formatDuration(value?: number): string {
  if (!value || value <= 0) {
    return '-';
  }
  const totalSeconds = Math.floor(value / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

export default function CollectionPanel() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const publishStateOptions = buildPublishStateOptions(t);
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<CollectionFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MovieCollectionAdminView | null>(null);
  const [coverFileId, setCoverFileId] = useState<string | undefined>(undefined);
  const [coverName, setCoverName] = useState('');
  const [uploadingCover, setUploadingCover] = useState(false);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [current, setCurrent] = useState<MovieCollectionAdminView | null>(null);
  const [items, setItems] = useState<MoviePublicView[]>([]);
  const [itemsLoading, setItemsLoading] = useState(false);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerInput, setPickerInput] = useState('');
  const [pickerKeyword, setPickerKeyword] = useState<string | undefined>(undefined);
  const [pickerPage, setPickerPage] = useState(1);
  const [pickerTotal, setPickerTotal] = useState(0);
  const [pickerMovies, setPickerMovies] = useState<MovieAdminView[]>([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [addingId, setAddingId] = useState<string | undefined>(undefined);

  const openCreate = () => {
    setEditing(null);
    setCoverFileId(undefined);
    setCoverName('');
    setModalOpen(true);
  };

  const openEdit = (row: MovieCollectionAdminView) => {
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
        message.success(t('video.catalog.collection.msg.coverUploaded'));
      })
      .catch((error) =>
        message.error(
          error instanceof Error
            ? error.message
            : t('video.catalog.collection.msg.coverUploadFailed'),
        ),
      )
      .finally(() => setUploadingCover(false));
  };

  const handleSave = async (values: CollectionFormValues) => {
    const payload: CollectionSaveInput = {
      title: values.title,
      description: values.description || undefined,
      sortNo: values.sortNo,
    };
    if (coverFileId !== undefined) {
      payload.coverFileId = coverFileId;
    }
    try {
      if (editing) {
        await updateCollection(editing.id, payload);
        message.success(t('video.catalog.collection.msg.updated'));
      } else {
        await createCollection(payload);
        message.success(t('video.catalog.collection.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.collection.msg.saveFailed'),
      );
      throw error;
    }
  };

  const handlePublish = async (row: MovieCollectionAdminView) => {
    try {
      await publishCollection(row.id);
      message.success(t('video.catalog.collection.msg.published'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.collection.msg.publishFailed'),
      );
    }
  };

  const handleOffline = async (row: MovieCollectionAdminView) => {
    try {
      await offlineCollection(row.id);
      message.success(t('video.catalog.collection.msg.offlined'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.collection.msg.offlineFailed'),
      );
    }
  };

  const handleDelete = async (row: MovieCollectionAdminView) => {
    try {
      await deleteCollection(row.id);
      message.success(t('video.catalog.collection.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.collection.msg.deleteFailed'),
      );
    }
  };

  const reloadItems = async (id: string) => {
    setItemsLoading(true);
    try {
      const detail: MovieCollectionAdminDetailView = await getCollection(id);
      setCurrent(detail);
      setItems(detail.items ?? []);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : t('video.catalog.collection.msg.itemsLoadFailed'),
      );
    } finally {
      setItemsLoading(false);
    }
  };

  const openItems = (row: MovieCollectionAdminView) => {
    setCurrent(row);
    setItems([]);
    setDrawerOpen(true);
    void reloadItems(row.id);
  };

  const loadPicker = async (page: number) => {
    setPickerLoading(true);
    try {
      const result = await pageMovies({
        keyword: pickerKeyword,
        publishState: 'PUBLISHED',
        sortBy: 'LATEST',
        current: page,
        size: PICKER_PAGE_SIZE,
      });
      setPickerMovies(result.records ?? []);
      setPickerTotal(Number(result.total ?? 0));
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : t('video.catalog.collection.msg.moviesLoadFailed'),
      );
    } finally {
      setPickerLoading(false);
    }
  };

  const openPicker = () => {
    setPickerOpen(true);
    setPickerInput('');
    setPickerKeyword(undefined);
    setPickerPage(1);
    void loadPicker(1);
  };

  const handleAdd = async (contentId: string) => {
    if (!current) {
      return;
    }
    setAddingId(contentId);
    try {
      const detail = await addCollectionItem(current.id, contentId);
      setCurrent(detail);
      setItems(detail.items ?? []);
      message.success(t('video.catalog.collection.msg.added'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.collection.msg.addFailed'),
      );
    } finally {
      setAddingId(undefined);
    }
  };

  const handleRemove = async (contentId: string) => {
    if (!current) {
      return;
    }
    try {
      const detail = await removeCollectionItem(current.id, contentId);
      setCurrent(detail);
      setItems(detail.items ?? []);
      message.success(t('video.catalog.collection.msg.removed'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.collection.msg.removeFailed'),
      );
    }
  };

  const columns: TableProps<MovieCollectionAdminView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('video.catalog.collection.col.cover'),
      dataIndex: 'coverUrl',
      width: 100,
      render: (value?: string) =>
        value ? (
          <Image
            src={value}
            alt={t('video.catalog.collection.alt.cover')}
            width={72}
            height={40}
            style={{ objectFit: 'cover' }}
          />
        ) : (
          '-'
        ),
    },
    {
      title: t('video.catalog.collection.col.collection'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.description ? (
            <Typography.Text type="secondary" ellipsis style={{ maxWidth: 360 }}>
              {row.description}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    { title: t('video.catalog.collection.col.itemCount'), dataIndex: 'itemCount', width: 90 },
    { title: t('video.catalog.collection.col.sortNo'), dataIndex: 'sortNo', width: 80 },
    {
      title: t('video.catalog.collection.col.status'),
      dataIndex: 'publishState',
      width: 100,
      render: (value?: PublishState) => renderPublishState(value, t),
    },
    {
      title: t('video.catalog.collection.col.publishedAt'),
      dataIndex: 'publishedAt',
      width: 170,
      render: (value?: string) => value ?? '-',
    },
    {
      title: t('video.catalog.collection.col.action'),
      key: 'action',
      width: 280,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openItems(row)}>
            {t('video.catalog.collection.action.manageMovies')}
          </Button>
          {has('sys:video:content:edit') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('video.catalog.collection.action.edit')}
            </Button>
          ) : null}
          {row.publishState !== 'PUBLISHED' && has('sys:video:movie:manage') ? (
            <Popconfirm
              title={t('video.catalog.collection.confirm.publish')}
              onConfirm={() => void handlePublish(row)}
            >
              <Button type="link" size="small">
                {t('video.catalog.collection.action.publish')}
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:movie:manage') ? (
            <Popconfirm
              title={t('video.catalog.collection.confirm.offline')}
              onConfirm={() => void handleOffline(row)}
            >
              <Button type="link" size="small" danger>
                {t('video.catalog.collection.action.offline')}
              </Button>
            </Popconfirm>
          ) : null}
          {has('sys:video:content:delete') ? (
            <Popconfirm
              title={t('video.catalog.collection.confirm.delete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('video.catalog.collection.action.delete')}
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const itemColumns: TableProps<MoviePublicView>['columns'] = [
    {
      title: '#',
      key: 'index',
      width: 60,
      render: (_, __, index) => index + 1,
    },
    {
      title: t('video.catalog.collection.item.col.poster'),
      dataIndex: 'posterUrl',
      width: 90,
      render: (value?: string) =>
        value ? (
          <Image
            src={value}
            alt={t('video.catalog.collection.alt.poster')}
            width={48}
            height={66}
            style={{ objectFit: 'cover' }}
          />
        ) : (
          '-'
        ),
    },
    {
      title: t('video.catalog.collection.item.col.movie'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.originalTitle ? (
            <Typography.Text type="secondary">{row.originalTitle}</Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: t('video.catalog.collection.item.col.duration'),
      dataIndex: 'durationMs',
      width: 90,
      render: (value?: number) => formatDuration(value),
    },
    {
      title: t('video.catalog.collection.item.col.rating'),
      dataIndex: 'rating',
      width: 80,
      render: (value?: number) => ((value ?? 0) > 0 ? value : '-'),
    },
    {
      title: t('video.catalog.collection.item.col.playable'),
      dataIndex: 'playable',
      width: 80,
      render: (value?: boolean) =>
        value ? (
          <Tag color="green">{t('video.catalog.collection.tag.playable')}</Tag>
        ) : (
          <Tag color="default">{t('video.catalog.collection.tag.notPlayable')}</Tag>
        ),
    },
    {
      title: t('video.catalog.collection.item.col.action'),
      key: 'action',
      width: 90,
      render: (_, row) => (
        <Popconfirm
          title={t('video.catalog.collection.confirm.removeItem')}
          onConfirm={() => void handleRemove(row.id)}
        >
          <Button type="link" size="small" danger>
            {t('video.catalog.collection.action.remove')}
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const pickerColumns: TableProps<MovieAdminView>['columns'] = [
    {
      title: t('video.catalog.collection.item.col.movie'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.originalTitle ? (
            <Typography.Text type="secondary">{row.originalTitle}</Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: t('video.catalog.collection.item.col.duration'),
      dataIndex: 'durationMs',
      width: 90,
      render: (value?: number) => formatDuration(value),
    },
    {
      title: t('video.catalog.collection.col.status'),
      dataIndex: 'publishState',
      width: 100,
      render: (value?: PublishState) => renderPublishState(value, t),
    },
    {
      title: t('video.catalog.collection.item.col.action'),
      key: 'action',
      width: 100,
      render: (_, row) => {
        const added = items.some((item) => item.id === row.id);
        return (
          <Button
            type="link"
            size="small"
            disabled={added}
            loading={addingId === row.id}
            onClick={() => void handleAdd(row.id)}
          >
            {added
              ? t('video.catalog.collection.action.added')
              : t('video.catalog.collection.action.add')}
          </Button>
        );
      },
    },
  ];

  return (
    <>
      <ProTable<MovieCollectionAdminView, CollectionQuery>
        ref={tableRef}
        rowKey="id"
        title={t('video.catalog.collection.list.title')}
        columns={columns}
        request={pageCollections}
        params={{ keyword, publishState }}
        onError={(error) =>
          message.error(
            error instanceof Error
              ? error.message
              : t('video.catalog.collection.list.loadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('video.catalog.collection.search.keyword'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('video.catalog.collection.search.keywordPlaceholder')}
                  />
                ),
              },
              {
                name: 'publishState',
                label: t('video.catalog.collection.search.status'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.catalog.collection.search.all')}
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
          <AuthButton perm="sys:video:content:add" type="primary" onClick={openCreate}>
            {t('video.catalog.collection.action.create')}
          </AuthButton>
        }
      />

      <FormModal<CollectionFormValues>
        open={modalOpen}
        title={
          editing
            ? t('video.catalog.collection.form.editTitle', { title: editing.title })
            : t('video.catalog.collection.form.createTitle')
        }
        form={form}
        width={640}
        initialValues={{
          title: editing?.title,
          description: editing?.description,
          sortNo: editing?.sortNo,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item label={t('video.catalog.collection.form.cover')}>
          <Space direction="vertical" size={6}>
            {coverFileId === '0' ? (
              <Typography.Text type="secondary">
                {t('video.catalog.collection.form.coverCleared')}
              </Typography.Text>
            ) : coverFileId ? (
              <Typography.Text type="secondary">
                {t('video.catalog.collection.form.coverUploaded', {
                  name:
                    coverName ||
                    t('video.catalog.collection.form.fileId', { id: coverFileId }),
                })}
              </Typography.Text>
            ) : editing?.coverUrl ? (
              <Image
                src={editing.coverUrl}
                width={160}
                height={90}
                style={{ objectFit: 'cover' }}
                alt={t('video.catalog.collection.alt.currentCover')}
              />
            ) : (
              <Typography.Text type="secondary">
                {t('video.catalog.collection.form.coverEmpty')}
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
                    ? t('video.catalog.collection.form.coverReupload')
                    : t('video.catalog.collection.form.coverUpload')}
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
                  {t('video.catalog.collection.form.coverRemove')}
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item
          name="title"
          label={t('video.catalog.collection.form.title')}
          rules={[
            { required: true, message: t('video.catalog.collection.form.titleRequired') },
          ]}
        >
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label={t('video.catalog.collection.form.description')}>
          <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Form.Item
          name="sortNo"
          label={t('video.catalog.collection.form.sortNo')}
          extra={t('video.catalog.collection.form.sortNoExtra')}
        >
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>

      <Drawer
        title={
          current
            ? t('video.catalog.collection.drawer.titleWithName', { title: current.title })
            : t('video.catalog.collection.drawer.title')
        }
        width={820}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setItems([]);
        }}
        extra={
          <Space>
            <Typography.Text type="secondary">
              {t('video.catalog.collection.drawer.count', {
                count: current?.itemCount ?? items.length,
              })}
            </Typography.Text>
            {has('sys:video:movie:manage') ? (
              <Button type="primary" onClick={openPicker}>
                {t('video.catalog.collection.drawer.add')}
              </Button>
            ) : null}
          </Space>
        }
      >
        {itemsLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin />
          </div>
        ) : (
          <Table<MoviePublicView>
            rowKey="id"
            size="middle"
            columns={itemColumns}
            dataSource={items}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={t('video.catalog.collection.drawer.empty')}
                />
              ),
            }}
          />
        )}
      </Drawer>

      <Modal
        open={pickerOpen}
        title={t('video.catalog.collection.picker.title')}
        width={760}
        footer={null}
        onCancel={() => setPickerOpen(false)}
      >
        <Input.Search
          allowClear
          enterButton={t('video.catalog.collection.picker.search')}
          placeholder={t('video.catalog.collection.picker.searchPlaceholder')}
          value={pickerInput}
          onChange={(event) => setPickerInput(event.target.value)}
          onSearch={(value) => {
            setPickerKeyword(value.trim() || undefined);
            setPickerPage(1);
            void loadPicker(1);
          }}
          style={{ marginBottom: 12 }}
        />
        <Table<MovieAdminView>
          rowKey="id"
          size="small"
          loading={pickerLoading}
          columns={pickerColumns}
          dataSource={pickerMovies}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={t('video.catalog.collection.picker.empty')}
              />
            ),
          }}
          pagination={{
            current: pickerPage,
            pageSize: PICKER_PAGE_SIZE,
            total: pickerTotal,
            showSizeChanger: false,
            onChange: (page) => {
              setPickerPage(page);
              void loadPicker(page);
            },
          }}
        />
      </Modal>
    </>
  );
}

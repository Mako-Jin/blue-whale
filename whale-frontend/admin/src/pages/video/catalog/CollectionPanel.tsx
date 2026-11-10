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

interface CollectionFormValues {
  title: string;
  description?: string;
  sortNo?: number;
}

const PUBLISH_STATE_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已上架' },
  { value: 'OFFLINE', label: '已下架' },
];

/** 选片弹窗每页条数 */
const PICKER_PAGE_SIZE = 10;

/** 发布状态标签 */
function renderPublishState(state?: PublishState) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: '草稿' },
    PENDING_REVIEW: { color: 'gold', label: '待审核' },
    PUBLISHED: { color: 'success', label: '已上架' },
    OFFLINE: { color: 'error', label: '已下架' },
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
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<CollectionFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MovieCollectionAdminView | null>(null);
  const [coverFileId, setCoverFileId] = useState<number | undefined>(undefined);
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
  const [addingId, setAddingId] = useState<number | undefined>(undefined);

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
        message.success('封面已上传');
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '封面上传失败'))
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
        message.success('片单已更新');
      } else {
        await createCollection(payload);
        message.success('片单已新增（草稿），上架后用户可见');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handlePublish = async (row: MovieCollectionAdminView) => {
    try {
      await publishCollection(row.id);
      message.success('片单已上架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上架失败');
    }
  };

  const handleOffline = async (row: MovieCollectionAdminView) => {
    try {
      await offlineCollection(row.id);
      message.success('片单已下架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下架失败');
    }
  };

  const handleDelete = async (row: MovieCollectionAdminView) => {
    try {
      await deleteCollection(row.id);
      message.success('片单已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const reloadItems = async (id: number) => {
    setItemsLoading(true);
    try {
      const detail: MovieCollectionAdminDetailView = await getCollection(id);
      setCurrent(detail);
      setItems(detail.items ?? []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '片单内电影加载失败');
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
      setPickerTotal(result.total ?? 0);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '电影加载失败');
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

  const handleAdd = async (contentId: number) => {
    if (!current) {
      return;
    }
    setAddingId(contentId);
    try {
      const detail = await addCollectionItem(current.id, contentId);
      setCurrent(detail);
      setItems(detail.items ?? []);
      message.success('已加入片单');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加入片单失败');
    } finally {
      setAddingId(undefined);
    }
  };

  const handleRemove = async (contentId: number) => {
    if (!current) {
      return;
    }
    try {
      const detail = await removeCollectionItem(current.id, contentId);
      setCurrent(detail);
      setItems(detail.items ?? []);
      message.success('已移出片单');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '移出失败');
    }
  };

  const columns: TableProps<MovieCollectionAdminView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '封面',
      dataIndex: 'coverUrl',
      width: 100,
      render: (value?: string) =>
        value ? <Image src={value} alt="封面" width={72} height={40} style={{ objectFit: 'cover' }} /> : '-',
    },
    {
      title: '片单',
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
    { title: '影片数', dataIndex: 'itemCount', width: 90 },
    { title: '排序', dataIndex: 'sortNo', width: 80 },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value?: PublishState) => renderPublishState(value),
    },
    {
      title: '上架时间',
      dataIndex: 'publishedAt',
      width: 170,
      render: (value?: string) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openItems(row)}>
            影片管理
          </Button>
          {has('sys:video:content:edit') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          ) : null}
          {row.publishState !== 'PUBLISHED' && has('sys:video:movie:manage') ? (
            <Popconfirm title="确认上架该片单？" onConfirm={() => void handlePublish(row)}>
              <Button type="link" size="small">
                上架
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:movie:manage') ? (
            <Popconfirm title="确认下架该片单？" onConfirm={() => void handleOffline(row)}>
              <Button type="link" size="small" danger>
                下架
              </Button>
            </Popconfirm>
          ) : null}
          {has('sys:video:content:delete') ? (
            <Popconfirm title="确认删除该片单？" onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
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
      title: '海报',
      dataIndex: 'posterUrl',
      width: 90,
      render: (value?: string) =>
        value ? <Image src={value} alt="海报" width={48} height={66} style={{ objectFit: 'cover' }} /> : '-',
    },
    {
      title: '电影',
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.originalTitle ? <Typography.Text type="secondary">{row.originalTitle}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 90,
      render: (value?: number) => formatDuration(value),
    },
    {
      title: '评分',
      dataIndex: 'rating',
      width: 80,
      render: (value?: number) => (value ?? 0) > 0 ? value : '-',
    },
    {
      title: '可播',
      dataIndex: 'playable',
      width: 80,
      render: (value?: boolean) =>
        value ? <Tag color="green">可播</Tag> : <Tag color="default">不可播</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, row) => (
        <Popconfirm title="确认将该电影移出片单？" onConfirm={() => void handleRemove(row.id)}>
          <Button type="link" size="small" danger>
            移出
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const pickerColumns: TableProps<MovieAdminView>['columns'] = [
    {
      title: '电影',
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.originalTitle ? <Typography.Text type="secondary">{row.originalTitle}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 90,
      render: (value?: number) => formatDuration(value),
    },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value?: PublishState) => renderPublishState(value),
    },
    {
      title: '操作',
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
            {added ? '已加入' : '加入'}
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
        title="片单管理"
        columns={columns}
        request={pageCollections}
        params={{ keyword, publishState }}
        onError={(error) => message.error(error instanceof Error ? error.message : '片单列表加载失败')}
        search={
          <SearchForm
            items={[
              { name: 'keyword', label: '关键词', element: <Input allowClear placeholder="片单标题" /> },
              {
                name: 'publishState',
                label: '状态',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={PUBLISH_STATE_OPTIONS} />
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
            新增片单
          </AuthButton>
        }
      />

      <FormModal<CollectionFormValues>
        open={modalOpen}
        title={editing ? `编辑片单：${editing.title}` : '新增片单'}
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
        <Form.Item label="封面">
          <Space direction="vertical" size={6}>
            {coverFileId === 0 ? (
              <Typography.Text type="secondary">已清除封面（保存后生效）</Typography.Text>
            ) : coverFileId ? (
              <Typography.Text type="secondary">已上传封面：{coverName || `文件 ID ${coverFileId}`}</Typography.Text>
            ) : editing?.coverUrl ? (
              <Image src={editing.coverUrl} width={160} height={90} style={{ objectFit: 'cover' }} alt="当前封面" />
            ) : (
              <Typography.Text type="secondary">暂无封面</Typography.Text>
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
                  {coverFileId !== 0 && (coverFileId || editing?.coverUrl) ? '重新上传封面' : '上传封面'}
                </Button>
              </Upload>
              {editing?.coverUrl || coverFileId ? (
                <Button
                  danger
                  onClick={() => {
                    setCoverFileId(0);
                    setCoverName('');
                  }}
                >
                  移除封面
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item name="title" label="片单标题" rules={[{ required: true, message: '请输入片单标题' }]}>
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label="片单简介">
          <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Form.Item name="sortNo" label="运营排序" extra="数值越小越靠前">
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>

      <Drawer
        title={current ? `片单影片：${current.title}` : '片单影片'}
        width={820}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setItems([]);
        }}
        extra={
          <Space>
            <Typography.Text type="secondary">共 {current?.itemCount ?? items.length} 部（上限 200）</Typography.Text>
            {has('sys:video:movie:manage') ? (
              <Button type="primary" onClick={openPicker}>
                添加电影
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
                  description="片单暂无电影，点击右上角「添加电影」"
                />
              ),
            }}
          />
        )}
      </Drawer>

      <Modal
        open={pickerOpen}
        title="添加电影（仅已发布且可播电影）"
        width={760}
        footer={null}
        onCancel={() => setPickerOpen(false)}
      >
        <Input.Search
          allowClear
          enterButton="搜索"
          placeholder="电影标题 / 原始片名"
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
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布电影" /> }}
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

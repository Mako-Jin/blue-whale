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

interface SeriesFormValues {
  title: string;
  description?: string;
  categoryId?: number;
  creatorId?: number;
  sortNo?: number;
}

const PUBLISH_STATE_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已上架' },
  { value: 'OFFLINE', label: '已下架' },
];

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

export default function SeriesPanel() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<SeriesFormValues>();

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);

  const [editing, setEditing] = useState<SeriesView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [coverFileId, setCoverFileId] = useState<number | undefined>(undefined);
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
        message.success('封面已上传');
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '封面上传失败'))
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
        message.success('系列已更新');
      } else {
        await createSeries(payload);
        message.success('系列已新增（草稿），上架后用户可见');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handlePublish = async (row: SeriesView) => {
    try {
      await publishSeries(row.id);
      message.success('系列已上架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上架失败');
    }
  };

  const handleOffline = async (row: SeriesView) => {
    try {
      await offlineSeries(row.id);
      message.success('系列已下架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下架失败');
    }
  };

  const handleDelete = async (row: SeriesView) => {
    try {
      await deleteSeries(row.id);
      message.success('系列已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败（存在已发布集数时需先移除集数）');
    }
  };

  const categoryName = (value?: number) => categories.find((item) => item.id === value)?.name ?? '-';

  const columns: TableProps<SeriesView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '封面',
      dataIndex: 'coverUrl',
      width: 100,
      render: (value?: string) =>
        value ? <Image src={value} alt="封面" width={72} height={40} style={{ objectFit: 'cover' }} /> : '-',
    },
    {
      title: '系列',
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
      title: '分类',
      dataIndex: 'categoryId',
      width: 120,
      render: (value: number | undefined) => categoryName(value),
    },
    {
      title: '创作者',
      dataIndex: 'creatorName',
      width: 140,
      render: (value: string | undefined, row) => value || (row.creatorId ? `#${row.creatorId}` : '-'),
    },
    { title: '集数', dataIndex: 'episodeCount', width: 80 },
    { title: '排序', dataIndex: 'sortNo', width: 80 },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value),
    },
    {
      title: '上架时间',
      dataIndex: 'publishedAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={4} wrap>
          {has('sys:video:series:manage') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          ) : null}
          {row.publishState !== 'PUBLISHED' && has('sys:video:series:manage') ? (
            <Popconfirm title="确认上架该系列？" onConfirm={() => void handlePublish(row)}>
              <Button type="link" size="small">
                上架
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:series:manage') ? (
            <Popconfirm title="确认下架该系列？" onConfirm={() => void handleOffline(row)}>
              <Button type="link" size="small" danger>
                下架
              </Button>
            </Popconfirm>
          ) : null}
          {has('sys:video:series:manage') ? (
            <Popconfirm
              title="确认删除该系列？"
              description={row.episodeCount > 0 ? '该系列仍有集数，删除会被后端拒绝' : undefined}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                删除
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
        title="系列管理"
        columns={columns}
        request={pageSeries}
        params={{ keyword, publishState, categoryId }}
        onError={(error) => message.error(error instanceof Error ? error.message : '系列列表加载失败')}
        search={
          <SearchForm
            items={[
              { name: 'keyword', label: '关键词', element: <Input allowClear placeholder="系列标题" /> },
              {
                name: 'publishState',
                label: '状态',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={PUBLISH_STATE_OPTIONS} />
                ),
              },
              {
                name: 'categoryId',
                label: '分类',
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="全部"
                    style={{ width: 160 }}
                    options={categories.map((category) => ({ value: category.id, label: category.name }))}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
              setCategoryId((values.categoryId as number) || undefined);
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
            新增系列
          </AuthButton>
        }
      />

      <FormModal<SeriesFormValues>
        open={modalOpen}
        title={editing ? `编辑系列：${editing.title}` : '新增系列'}
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
        <Form.Item name="title" label="系列标题" rules={[{ required: true, message: '请输入系列标题' }]}>
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label="系列简介">
          <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Form.Item name="categoryId" label="分类" extra="仅可选适用于视频的分类">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="选择分类"
            options={categories
              .filter((category) => category.contentTypeScope === 'ALL' || category.contentTypeScope === 'VIDEO')
              .map((category) => ({ value: category.id, label: category.name }))}
          />
        </Form.Item>
        <Form.Item name="creatorId" label="创作者 ID" extra="可选，填写后用户侧展示创作者昵称">
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="sortNo" label="运营排序" extra="数值越小越靠前">
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}

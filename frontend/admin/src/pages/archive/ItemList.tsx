// 档案检索页：统计概览 + 分页检索 + 详情抽屉 + 整理/确认归档/销毁/下载
// 操作按钮按 sys:archive:item:* 权限码渲染

import {
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AuthButton,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  archiveStats,
  categoryTree,
  confirmItem,
  destroyItem,
  downloadItemFile,
  editItem,
  fetchItemPreviewUrl,
  isPreviewableFile,
  itemDetail,
  pageItems,
  previewKindOf,
  type ArchiveCategoryVO,
  type ArchiveFileVO,
  type ArchiveItemDetailVO,
  type ArchiveItemEditRequest,
  type ArchiveItemVO,
  type ArchivePreviewKind,
  type ArchiveStatsVO,
} from '../../api/archive';
import {
  FILE_ROLE_LABELS,
  ITEM_STATUS_COLORS,
  ITEM_STATUS_OPTIONS,
  SECURITY_COLORS,
  SECURITY_OPTIONS,
  flattenCategories,
  formatDateTime,
  itemStatusLabel,
  securityLabel,
  type CategoryOption,
} from './helpers';

/** 条目查询条件 */
type ItemQuery = {
  keyword?: string;
  categoryId?: string;
  year?: number;
  securityLevel?: number;
  itemStatus?: number;
};

/** 整理表单值 */
interface ItemEditValues {
  categoryId: string;
  title?: string;
  securityLevel?: number;
  retentionYears?: number;
  summary?: string;
  keywords?: string;
}

/** 文件大小格式化 */
function formatSize(size?: number): string {
  if (size === undefined || size === null) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 音视频在线预览的大小上限（超过建议下载后播放，避免浏览器整块载入内存） */
const PREVIEW_SIZE_LIMIT = 200 * 1024 * 1024;

export default function ArchiveItemList({
  defaultItemStatus,
}: {
  /** 默认状态筛选：待整理页传 0，检索页不传 */
  defaultItemStatus?: number;
}) {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<ItemEditValues>();
  const [query, setQuery] = useState<ItemQuery>(
    defaultItemStatus === undefined ? {} : { itemStatus: defaultItemStatus },
  );
  const [stats, setStats] = useState<ArchiveStatsVO | null>(null);
  const [categories, setCategories] = useState<ArchiveCategoryVO[]>([]);
  const [detail, setDetail] = useState<ArchiveItemDetailVO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<ArchiveItemVO | null>(null);
  const [saving, setSaving] = useState(false);
  const [preview, setPreview] = useState<{
    kind: ArchivePreviewKind;
    fileName: string;
    url: string;
  } | null>(null);

  useEffect(() => {
    if (!preview) {
      return undefined;
    }
    return () => URL.revokeObjectURL(preview.url);
  }, [preview]);

  const loadStats = useMemo(
    () => async () => {
      try {
        setStats(await archiveStats());
      } catch (error) {
        message.error(error instanceof Error ? error.message : '统计加载失败');
      }
    },
    [message],
  );

  useEffect(() => {
    void loadStats();
    categoryTree()
      .then(setCategories)
      .catch((error: unknown) => {
        message.error(error instanceof Error ? error.message : '门类加载失败');
      });
  }, [loadStats, message]);

  const categoryOptions = useMemo<CategoryOption[]>(
    () => flattenCategories(categories),
    [categories],
  );

  const refresh = () => {
    tableRef.current?.reload();
    void loadStats();
  };

  const openDetail = async (row: ArchiveItemVO) => {
    setDrawerOpen(true);
    setDetailLoading(true);
    try {
      setDetail(await itemDetail(row.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '详情加载失败');
      setDrawerOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const openEdit = (row: ArchiveItemVO) => {
    setEditing(row);
    form.setFieldsValue({
      categoryId: row.categoryId ?? '',
      title: row.title,
      securityLevel: row.securityLevel,
      retentionYears: row.retentionYears,
      summary: row.summary,
      keywords: row.keywords,
    });
  };

  const handleSave = async () => {
    if (!editing) {
      return;
    }
    const values = await form.validateFields();
    const payload: ArchiveItemEditRequest = {
      categoryId: values.categoryId,
      title: values.title,
      securityLevel: values.securityLevel,
      retentionYears: values.retentionYears,
      summary: values.summary,
      keywords: values.keywords,
    };
    setSaving(true);
    try {
      await editItem(editing.id, payload);
      message.success('条目已整理');
      setEditing(null);
      refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '整理失败');
    } finally {
      setSaving(false);
    }
  };

  const handleConfirm = async (row: ArchiveItemVO) => {
    try {
      const result = await confirmItem(row.id);
      message.success(`已归档，档号 ${result.archiveNo ?? '-'}`);
      refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '确认归档失败');
    }
  };

  const handleDestroy = async (row: ArchiveItemVO) => {
    try {
      await destroyItem(row.id);
      message.success('条目已销毁登记');
      refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '销毁失败');
    }
  };

  const handleDownload = async (file: ArchiveFileVO) => {
    if (!detail) {
      return;
    }
    try {
      await downloadItemFile(detail.item.id, file);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下载失败');
    }
  };

  const handlePreview = async (file: ArchiveFileVO) => {
    if (!detail) {
      return;
    }
    const kind = previewKindOf(file);
    if (!kind) {
      message.warning('该文件类型不支持在线预览，请下载后查看');
      return;
    }
    if ((kind === 'video' || kind === 'audio') && (file.fileSize ?? 0) > PREVIEW_SIZE_LIMIT) {
      message.warning('文件超过 200MB，建议下载后播放');
      return;
    }
    const hide = message.loading('预览加载中...', 0);
    try {
      const url = await fetchItemPreviewUrl(detail.item.id, file);
      setPreview({ kind, fileName: file.originalName ?? '档案文件', url });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '预览失败');
    } finally {
      hide();
    }
  };

  const columns: TableProps<ArchiveItemVO>['columns'] = [
    {
      title: '档号',
      dataIndex: 'archiveNo',
      width: 190,
      render: (value?: string) => value ?? '（待分配）',
    },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '门类',
      dataIndex: 'categoryName',
      width: 140,
      render: (value?: string) => value ?? '-',
    },
    { title: '年度', dataIndex: 'year', width: 80 },
    {
      title: '密级',
      dataIndex: 'securityLevel',
      width: 90,
      render: (value?: number) => (
        <Tag color={SECURITY_COLORS[value ?? 2]}>{securityLabel(value)}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'itemStatus',
      width: 100,
      render: (value?: number) => (
        <Tag color={ITEM_STATUS_COLORS[value ?? 0]}>{itemStatusLabel(value)}</Tag>
      ),
    },
    {
      title: '办结时间',
      dataIndex: 'finishedAt',
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_, row) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openDetail(row)}>
            详情
          </Button>
          {row.itemStatus === 0 && (
            <>
              <AuthButton
                perm="sys:archive:item:edit"
                type="link"
                size="small"
                onClick={() => openEdit(row)}
              >
                整理
              </AuthButton>
              <AuthButton
                perm="sys:archive:item:archive"
                type="link"
                size="small"
                onClick={() => void handleConfirm(row)}
              >
                确认归档
              </AuthButton>
            </>
          )}
          {row.itemStatus === 1 && has('sys:archive:item:destroy') && (
            <Popconfirm
              title="确认销毁登记？操作不可撤销"
              onConfirm={() => void handleDestroy(row)}
            >
              <Button type="link" size="small" danger>
                销毁
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const fileColumns: TableProps<ArchiveFileVO>['columns'] = [
    {
      title: '角色',
      dataIndex: 'fileRole',
      width: 90,
      render: (value?: string) => FILE_ROLE_LABELS[value ?? ''] ?? value ?? '-',
    },
    { title: '文件名', dataIndex: 'originalName', ellipsis: true },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 110,
      render: (value?: number) => formatSize(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, file) => (
        <Space size={4}>
          {isPreviewableFile(file) && (
            <AuthButton
              perm="sys:archive:item:download"
              type="link"
              size="small"
              onClick={() => void handlePreview(file)}
            >
              预览
            </AuthButton>
          )}
          <AuthButton
            perm="sys:archive:item:download"
            type="link"
            size="small"
            onClick={() => void handleDownload(file)}
          >
            下载
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="条目总数" value={stats?.total ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="待整理" value={stats?.pending ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="已归档" value={stats?.archived ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="已销毁" value={stats?.destroyed ?? 0} />
          </Card>
        </Col>
      </Row>
      <ProTable<ArchiveItemVO, ItemQuery>
        ref={tableRef}
        rowKey="id"
        title="档案检索"
        columns={columns}
        request={pageItems}
        params={{ ...query }}
        onError={(error) => message.error(error instanceof Error ? error.message : '档案加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键词',
                element: <Input allowClear placeholder="档号/标题/摘要/关键词" />,
              },
              {
                name: 'categoryId',
                label: '门类',
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="全部门类"
                    options={categoryOptions}
                  />
                ),
              },
              {
                name: 'itemStatus',
                label: '状态',
                element: <Select allowClear placeholder="全部" options={ITEM_STATUS_OPTIONS} />,
              },
              {
                name: 'securityLevel',
                label: '密级',
                element: <Select allowClear placeholder="全部" options={SECURITY_OPTIONS} />,
              },
              {
                name: 'year',
                label: '年度',
                element: <InputNumber min={1900} max={9999} style={{ width: '100%' }} />,
              },
            ]}
            onSearch={(values) =>
              setQuery({
                keyword: (values.keyword as string) || undefined,
                categoryId: (values.categoryId as string) || undefined,
                itemStatus: values.itemStatus as number | undefined,
                securityLevel: values.securityLevel as number | undefined,
                year: values.year as number | undefined,
              })
            }
            onReset={() => setQuery({})}
          />
        }
        toolbar={<Button onClick={refresh}>刷新</Button>}
      />
      <Drawer
        title="档案详情"
        width={720}
        open={drawerOpen}
        loading={detailLoading}
        onClose={() => setDrawerOpen(false)}
      >
        {detail && (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="档号">
                {detail.item.archiveNo ?? '（待分配）'}
              </Descriptions.Item>
              <Descriptions.Item label="标题">{detail.item.title ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="门类">{detail.item.categoryName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="门类路径">
                {detail.item.categoryPath ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="年度">{detail.item.year ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="密级">
                {securityLabel(detail.item.securityLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="保管期限">
                {detail.item.retentionYears ? `${detail.item.retentionYears} 年` : '长期'}
              </Descriptions.Item>
              <Descriptions.Item label="到期时间">
                {formatDateTime(detail.item.expireAt)}
              </Descriptions.Item>
              <Descriptions.Item label="来源定义">
                {detail.item.definitionCode ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="业务单号">
                {detail.item.businessKey ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="发起人">{detail.item.initiator ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="来源状态">
                {detail.sourceStatus === undefined || detail.sourceStatus === null
                  ? '不可达'
                  : detail.sourceStatus}
              </Descriptions.Item>
              <Descriptions.Item label="办结时间">
                {formatDateTime(detail.item.finishedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="归档时间">
                {formatDateTime(detail.item.archivedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="快照摘要" span={2}>
                {detail.item.snapshotHash ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="摘要" span={2}>
                {detail.item.summary ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="关键词" span={2}>
                {detail.item.keywords ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Card size="small" title="文件清单" style={{ marginTop: 16 }}>
              <Table<ArchiveFileVO>
                rowKey="id"
                size="small"
                columns={fileColumns}
                dataSource={detail.files}
                pagination={false}
              />
            </Card>
          </>
        )}
      </Drawer>
      <Modal
        title="整理档案条目"
        open={Boolean(editing)}
        confirmLoading={saving}
        onOk={() => void handleSave()}
        onCancel={() => setEditing(null)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="categoryId"
            label="归档门类"
            rules={[{ required: true, message: '请选择门类' }]}
          >
            <Select options={categoryOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="title" label="档案标题">
            <Input />
          </Form.Item>
          <Form.Item name="securityLevel" label="密级">
            <Select options={SECURITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="retentionYears" label="保管期限（年，留空表示长期）">
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="summary" label="摘要">
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item name="keywords" label="关键词（逗号分隔）">
            <Input maxLength={512} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={`预览：${preview?.fileName ?? ''}`}
        open={Boolean(preview)}
        footer={null}
        width={preview?.kind === 'video' ? 880 : 720}
        destroyOnClose
        onCancel={() => setPreview(null)}
      >
        {preview?.kind === 'image' && (
          <img src={preview.url} alt={preview.fileName} style={{ width: '100%' }} />
        )}
        {preview?.kind === 'video' && <video src={preview.url} controls style={{ width: '100%' }} />}
        {preview?.kind === 'audio' && <audio src={preview.url} controls style={{ width: '100%' }} />}
        {preview && (preview.kind === 'pdf' || preview.kind === 'text') && (
          <iframe
            src={preview.url}
            title={preview.fileName}
            style={{ width: '100%', height: 520, border: 0 }}
          />
        )}
      </Modal>
    </>
  );
}

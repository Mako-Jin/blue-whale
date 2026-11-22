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
import { useI18n } from '../../i18n';
import {
  ITEM_STATUS_COLORS,
  SECURITY_COLORS,
  fileRoleLabel,
  flattenCategories,
  formatDateTime,
  itemStatusLabel,
  itemStatusOptions,
  securityLabel,
  securityOptions,
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
  const { t } = useI18n();
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
        message.error(error instanceof Error ? error.message : t('archive.item.msg.statsFailed'));
      }
    },
    [message, t],
  );

  useEffect(() => {
    void loadStats();
    categoryTree()
      .then(setCategories)
      .catch((error: unknown) => {
        message.error(
          error instanceof Error ? error.message : t('archive.common.categoryLoadFailed'),
        );
      });
  }, [loadStats, message, t]);

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
      message.error(error instanceof Error ? error.message : t('archive.item.msg.detailFailed'));
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
      message.success(t('archive.item.msg.edited'));
      setEditing(null);
      refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.item.msg.editFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleConfirm = async (row: ArchiveItemVO) => {
    try {
      const result = await confirmItem(row.id);
      message.success(t('archive.item.msg.confirmed', { archiveNo: result.archiveNo ?? '-' }));
      refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.item.msg.confirmFailed'));
    }
  };

  const handleDestroy = async (row: ArchiveItemVO) => {
    try {
      await destroyItem(row.id);
      message.success(t('archive.item.msg.destroyed'));
      refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.item.msg.destroyFailed'));
    }
  };

  const handleDownload = async (file: ArchiveFileVO) => {
    if (!detail) {
      return;
    }
    try {
      await downloadItemFile(detail.item.id, file);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.item.msg.downloadFailed'));
    }
  };

  const handlePreview = async (file: ArchiveFileVO) => {
    if (!detail) {
      return;
    }
    const kind = previewKindOf(file);
    if (!kind) {
      message.warning(t('archive.item.msg.previewUnsupported'));
      return;
    }
    if ((kind === 'video' || kind === 'audio') && (file.fileSize ?? 0) > PREVIEW_SIZE_LIMIT) {
      message.warning(t('archive.item.msg.previewTooLarge'));
      return;
    }
    const hide = message.loading(t('archive.item.msg.previewLoading'), 0);
    try {
      const url = await fetchItemPreviewUrl(detail.item.id, file);
      setPreview({
        kind,
        fileName: file.originalName ?? t('archive.item.msg.defaultFileName'),
        url,
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.item.msg.previewFailed'));
    } finally {
      hide();
    }
  };

  const columns: TableProps<ArchiveItemVO>['columns'] = [
    {
      title: t('archive.item.col.archiveNo'),
      dataIndex: 'archiveNo',
      width: 190,
      render: (value?: string) => value ?? t('archive.item.pendingNo'),
    },
    { title: t('archive.item.col.title'), dataIndex: 'title', ellipsis: true },
    {
      title: t('archive.common.category'),
      dataIndex: 'categoryName',
      width: 140,
      render: (value?: string) => value ?? '-',
    },
    { title: t('archive.common.year'), dataIndex: 'year', width: 80 },
    {
      title: t('archive.common.securityLevel'),
      dataIndex: 'securityLevel',
      width: 90,
      render: (value?: number) => (
        <Tag color={SECURITY_COLORS[value ?? 2]}>{securityLabel(value, t)}</Tag>
      ),
    },
    {
      title: t('archive.common.status'),
      dataIndex: 'itemStatus',
      width: 100,
      render: (value?: number) => (
        <Tag color={ITEM_STATUS_COLORS[value ?? 0]}>{itemStatusLabel(value, t)}</Tag>
      ),
    },
    {
      title: t('archive.item.col.finishedAt'),
      dataIndex: 'finishedAt',
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: t('archive.common.action'),
      key: 'action',
      width: 250,
      render: (_, row) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openDetail(row)}>
            {t('archive.item.action.detail')}
          </Button>
          {row.itemStatus === 0 && (
            <>
              <AuthButton
                perm="sys:archive:item:edit"
                type="link"
                size="small"
                onClick={() => openEdit(row)}
              >
                {t('archive.item.action.edit')}
              </AuthButton>
              <AuthButton
                perm="sys:archive:item:archive"
                type="link"
                size="small"
                onClick={() => void handleConfirm(row)}
              >
                {t('archive.item.action.confirm')}
              </AuthButton>
            </>
          )}
          {row.itemStatus === 1 && has('sys:archive:item:destroy') && (
            <Popconfirm
              title={t('archive.item.confirm.destroy')}
              onConfirm={() => void handleDestroy(row)}
            >
              <Button type="link" size="small" danger>
                {t('archive.item.action.destroy')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const fileColumns: TableProps<ArchiveFileVO>['columns'] = [
    {
      title: t('archive.item.col.fileRole'),
      dataIndex: 'fileRole',
      width: 90,
      render: (value?: string) => fileRoleLabel(value, t),
    },
    { title: t('archive.item.col.fileName'), dataIndex: 'originalName', ellipsis: true },
    {
      title: t('archive.item.col.fileSize'),
      dataIndex: 'fileSize',
      width: 110,
      render: (value?: number) => formatSize(value),
    },
    {
      title: t('archive.common.action'),
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
              {t('archive.item.action.preview')}
            </AuthButton>
          )}
          <AuthButton
            perm="sys:archive:item:download"
            type="link"
            size="small"
            onClick={() => void handleDownload(file)}
          >
            {t('archive.item.action.download')}
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
            <Statistic title={t('archive.item.stats.total')} value={stats?.total ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title={t('archive.itemStatus.pending')} value={stats?.pending ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title={t('archive.itemStatus.archived')} value={stats?.archived ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title={t('archive.itemStatus.destroyed')} value={stats?.destroyed ?? 0} />
          </Card>
        </Col>
      </Row>
      <ProTable<ArchiveItemVO, ItemQuery>
        ref={tableRef}
        rowKey="id"
        title={t('archive.item.title')}
        columns={columns}
        request={pageItems}
        params={{ ...query }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : t('archive.item.msg.loadFailed'))
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('archive.item.search.keyword'),
                element: (
                  <Input allowClear placeholder={t('archive.item.search.keywordPlaceholder')} />
                ),
              },
              {
                name: 'categoryId',
                label: t('archive.common.category'),
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder={t('archive.item.search.allCategories')}
                    options={categoryOptions}
                  />
                ),
              },
              {
                name: 'itemStatus',
                label: t('archive.common.status'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('archive.common.all')}
                    options={itemStatusOptions(t)}
                  />
                ),
              },
              {
                name: 'securityLevel',
                label: t('archive.common.securityLevel'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('archive.common.all')}
                    options={securityOptions(t)}
                  />
                ),
              },
              {
                name: 'year',
                label: t('archive.common.year'),
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
        toolbar={<Button onClick={refresh}>{t('archive.common.refresh')}</Button>}
      />
      <Drawer
        title={t('archive.item.detail.title')}
        width={720}
        open={drawerOpen}
        loading={detailLoading}
        onClose={() => setDrawerOpen(false)}
      >
        {detail && (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label={t('archive.item.col.archiveNo')}>
                {detail.item.archiveNo ?? t('archive.item.pendingNo')}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.col.title')}>
                {detail.item.title ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.common.category')}>
                {detail.item.categoryName ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.categoryPath')}>
                {detail.item.categoryPath ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.common.year')}>
                {detail.item.year ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.common.securityLevel')}>
                {securityLabel(detail.item.securityLevel, t)}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.common.retention')}>
                {detail.item.retentionYears
                  ? t('archive.common.years', { years: detail.item.retentionYears })
                  : t('archive.common.longTerm')}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.expireAt')}>
                {formatDateTime(detail.item.expireAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.definitionCode')}>
                {detail.item.definitionCode ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.businessKey')}>
                {detail.item.businessKey ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.initiator')}>
                {detail.item.initiator ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.sourceStatus')}>
                {detail.sourceStatus === undefined || detail.sourceStatus === null
                  ? t('archive.item.detail.unreachable')
                  : detail.sourceStatus}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.col.finishedAt')}>
                {formatDateTime(detail.item.finishedAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.archivedAt')}>
                {formatDateTime(detail.item.archivedAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.detail.snapshotHash')} span={2}>
                {detail.item.snapshotHash ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.summary')} span={2}>
                {detail.item.summary ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('archive.item.keyword')} span={2}>
                {detail.item.keywords ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Card size="small" title={t('archive.item.detail.files')} style={{ marginTop: 16 }}>
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
        title={t('archive.item.edit.title')}
        open={Boolean(editing)}
        confirmLoading={saving}
        onOk={() => void handleSave()}
        onCancel={() => setEditing(null)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="categoryId"
            label={t('archive.item.form.categoryId')}
            rules={[{ required: true, message: t('archive.item.form.categoryRequired') }]}
          >
            <Select options={categoryOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="title" label={t('archive.item.form.title')}>
            <Input />
          </Form.Item>
          <Form.Item name="securityLevel" label={t('archive.common.securityLevel')}>
            <Select options={securityOptions(t)} />
          </Form.Item>
          <Form.Item name="retentionYears" label={t('archive.common.retentionYears')}>
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="summary" label={t('archive.item.summary')}>
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item name="keywords" label={t('archive.item.form.keywords')}>
            <Input maxLength={512} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={t('archive.item.preview.title', { fileName: preview?.fileName ?? '' })}
        open={Boolean(preview)}
        footer={null}
        width={preview?.kind === 'video' ? 880 : 720}
        destroyOnClose
        onCancel={() => setPreview(null)}
      >
        {preview?.kind === 'image' && (
          <img src={preview.url} alt={preview.fileName} style={{ width: '100%' }} />
        )}
        {preview?.kind === 'video' && (
          <video src={preview.url} controls style={{ width: '100%' }} />
        )}
        {preview?.kind === 'audio' && (
          <audio src={preview.url} controls style={{ width: '100%' }} />
        )}
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

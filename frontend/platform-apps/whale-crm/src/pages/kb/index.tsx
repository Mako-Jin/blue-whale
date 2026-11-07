// 知识库引用：FAQ 元数据 / whale-doc 版本引用 / 命中计数（正文不复制）
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { hasPermission } from '../../utils/permission';
import {
  createKbRef,
  deleteKbRef,
  hitKbRef,
  pageKbRefs,
  updateKbRef,
  type KbPageQuery,
  type KbRefInput,
  type KbRefView,
} from '../../api/service';
import {
  KB_STATUS_COLOR,
  KB_STATUS_LABEL,
  KB_STATUS_OPTIONS,
  KB_VISIBLE_SCOPE_LABEL,
  KB_VISIBLE_SCOPE_OPTIONS,
} from '../../utils/dict';

/** 筛选条件 */
interface KbFilterValues {
  keyword?: string;
  category?: string;
  visibleScope?: string;
  status?: string;
}

/** 知识引用表单值 */
interface KbFormValues {
  title: string;
  category?: string;
  productTags?: string;
  faultTags?: string;
  visibleScope?: string;
  docId?: number | null;
  docVersion?: number | null;
  status?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

interface KbFormModalProps {
  open: boolean;
  record: KbRefView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 知识引用新建 / 编辑弹窗 */
function KbFormModal({ open, record, onCancel, onSaved }: KbFormModalProps) {
  const [form] = Form.useForm<KbFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    form.resetFields();
    if (record) {
      form.setFieldsValue({
        title: record.title,
        category: record.category ?? undefined,
        productTags: record.productTags ?? undefined,
        faultTags: record.faultTags ?? undefined,
        visibleScope: record.visibleScope ?? 'INTERNAL',
        docId: record.docId ?? undefined,
        docVersion: record.docVersion ?? undefined,
        status: record.status ?? 'ENABLED',
        ownerId: record.ownerId ?? undefined,
        deptId: record.deptId ?? undefined,
      });
    } else {
      form.setFieldsValue({ visibleScope: 'INTERNAL', status: 'ENABLED' });
    }
  }, [open, record, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: KbRefInput = {
        title: values.title,
        category: values.category || undefined,
        productTags: values.productTags || undefined,
        faultTags: values.faultTags || undefined,
        visibleScope: values.visibleScope,
        docId: values.docId ?? null,
        docVersion: values.docVersion ?? null,
        status: values.status,
        ownerId: values.ownerId ?? null,
        deptId: values.deptId ?? null,
      };
      if (record) {
        await updateKbRef(record.id, payload);
        message.success('知识引用已更新');
      } else {
        await createKbRef(payload);
        message.success('知识引用已创建');
      }
      onSaved();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={record ? `编辑知识引用：${record.title}` : '新建知识引用'}
      width={820}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={() => void handleOk()}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item name="title" label="FAQ 标题" rules={[{ required: true, message: '请输入 FAQ 标题' }]}>
          <Input maxLength={255} showCount />
        </Form.Item>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="category" label="分类" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="visibleScope" label="可见范围" style={{ flex: 1 }}>
            <Select options={KB_VISIBLE_SCOPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="status" label="状态" style={{ flex: 1 }}>
            <Select options={KB_STATUS_OPTIONS} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="productTags" label="产品标签（逗号分隔）" style={{ flex: 1 }}>
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="faultTags" label="故障标签（逗号分隔）" style={{ flex: 1 }}>
            <Input maxLength={255} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="docId" label="whale-doc 文档 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="docVersion" label="文档版本（缺省取当前版本）" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deptId" label="归属部门 ID" style={{ flex: 1 }}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
      </Form>
    </Modal>
  );
}

/** 知识库页 */
export default function KbPage() {
  const [filterForm] = Form.useForm<KbFilterValues>();
  const [rows, setRows] = useState<KbRefView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<KbPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<KbRefView | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageKbRefs({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: KbFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      category: values.category || undefined,
      visibleScope: values.visibleScope || undefined,
      status: values.status || undefined,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleDelete = async (row: KbRefView) => {
    try {
      await deleteKbRef(row.id);
      message.success('知识引用已删除');
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleHit = async (row: KbRefView) => {
    try {
      const updated = await hitKbRef(row.id);
      setRows((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      message.success('引用命中已记录');
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<KbRefView>['columns'] = [
    { title: 'FAQ 标题', dataIndex: 'title', width: 260, ellipsis: true },
    { title: '分类', dataIndex: 'category', width: 120, render: (value?: string | null) => value || '-' },
    {
      title: '标签',
      dataIndex: 'productTags',
      width: 220,
      render: (_: unknown, row) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{row.productTags || '-'}</Typography.Text>
          <Typography.Text type="secondary">{row.faultTags || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '可见范围',
      dataIndex: 'visibleScope',
      width: 110,
      render: (value?: string | null) => KB_VISIBLE_SCOPE_LABEL[value ?? ''] ?? value ?? '-',
    },
    {
      title: 'whale-doc 引用',
      dataIndex: 'docId',
      width: 170,
      render: (_: unknown, row) => (
        <Space size={4} wrap>
          <Typography.Text>{row.docId ? `#${row.docId}` : '未关联'}</Typography.Text>
          {row.docVersion ? <Tag>v{row.docVersion}</Tag> : null}
          {row.docId ? (
            <Tag color={row.docAvailable ? 'green' : 'red'}>{row.docAvailable ? '可用' : '不可用'}</Tag>
          ) : null}
        </Space>
      ),
    },
    {
      title: '命中',
      dataIndex: 'hitCount',
      width: 90,
      render: (value?: number | null) => value ?? 0,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value?: string | null) => (
        <Tag color={KB_STATUS_COLOR[value ?? '']}>{KB_STATUS_LABEL[value ?? ''] ?? value ?? '-'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 220,
      render: (_, row) => (
        <Space size={0} wrap>
          {hasPermission('sys:crm:kb:manage') && (
            <Button
              size="small"
              type="link"
              onClick={() => {
                setEditing(row);
                setFormOpen(true);
              }}
            >
              编辑
            </Button>
          )}
          {hasPermission('sys:crm:kb:manage') && (
            <Button size="small" type="link" onClick={() => void handleHit(row)}>
              引用 +1
            </Button>
          )}
          {hasPermission('sys:crm:kb:manage') && (
            <Popconfirm title="删除知识引用" description="仅删除 CRM 元数据，不删除 whale-doc 正文" onConfirm={() => void handleDelete(row)}>
              <Button size="small" type="link" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="知识库"
      extra={
        hasPermission('sys:crm:kb:manage') && (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            新建知识引用
          </Button>
        )
      }
    >
      <Form form={filterForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="keyword">
          <Input placeholder="标题 / 产品标签 / 故障标签" allowClear style={{ width: 240 }} />
        </Form.Item>
        <Form.Item name="category">
          <Input placeholder="分类" allowClear style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="visibleScope">
          <Select placeholder="可见范围" allowClear style={{ width: 140 }} options={KB_VISIBLE_SCOPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="status">
          <Select placeholder="状态" allowClear style={{ width: 120 }} options={KB_STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
      <Table<KbRefView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1400 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        正文归 whale-doc：保存时调用文档内网接口校验并记录可用版本；不可用时降级标记，不复制正文。
      </Typography.Text>

      <KbFormModal
        open={formOpen}
        record={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          void loadData();
        }}
      />
    </Card>
  );
}

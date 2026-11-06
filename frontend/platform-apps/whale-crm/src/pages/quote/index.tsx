// 销售报价：筛选分页、新建编辑（含明细行）、提交审批、改价新版本、详情查看
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
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
  message,
} from 'antd';
import type { FormListFieldData, TableProps } from 'antd';
import {
  createQuote,
  getQuote,
  pageQuotes,
  reviseQuote,
  submitQuote,
  updateQuote,
  type QuoteLineInput,
  type QuoteLineView,
  type QuotePageQuery,
  type QuoteSaveInput,
  type QuoteView,
} from '../../api/crm';
import { hasPermission } from '../../utils/permission';
import {
  QUOTE_STATUS_COLOR,
  QUOTE_STATUS_LABEL,
  QUOTE_STATUS_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface QuoteFilterValues {
  keyword?: string;
  opportunityId?: number | null;
  customerId?: number | null;
  status?: string;
  ownerId?: number | null;
}

/** 报价行表单值 */
interface QuoteLineFormValues {
  productName: string;
  specification?: string;
  unit?: string;
  quantity: number;
  unitPrice: number;
  discountRate?: number | null;
  taxRate?: number | null;
  remark?: string;
}

/** 报价表单值 */
interface QuoteFormValues {
  opportunityId: number;
  title: string;
  currency?: string;
  validUntil?: string;
  discountAmount?: number | null;
  remark?: string;
  lines: QuoteLineFormValues[];
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

/**
 * 格式化金额。
 *
 * @param value 后端金额
 * @returns 千分位金额文本
 */
function formatAmount(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isNaN(num)
    ? String(value)
    : num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

interface QuoteFormModalProps {
  open: boolean;
  initial: QuoteView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 报价新建 / 编辑弹窗（含明细行编辑） */
function QuoteFormModal({ open, initial, onCancel, onSaved }: QuoteFormModalProps) {
  const [form] = Form.useForm<QuoteFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        opportunityId: initial.opportunityId ?? undefined,
        title: initial.title,
        currency: initial.currency ?? 'CNY',
        validUntil: initial.validUntil ?? undefined,
        discountAmount:
          initial.discountAmount === null || initial.discountAmount === undefined
            ? null
            : Number(initial.discountAmount),
        remark: initial.remark ?? undefined,
        lines: (initial.lines ?? []).map((line) => ({
          productName: line.productName ?? '',
          specification: line.specification ?? undefined,
          unit: line.unit ?? undefined,
          quantity: line.quantity === null || line.quantity === undefined ? 1 : Number(line.quantity),
          unitPrice: line.unitPrice === null || line.unitPrice === undefined ? 0 : Number(line.unitPrice),
          discountRate:
            line.discountRate === null || line.discountRate === undefined
              ? 0
              : Number(line.discountRate),
          taxRate: line.taxRate === null || line.taxRate === undefined ? 13 : Number(line.taxRate),
          remark: line.remark ?? undefined,
        })),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        currency: 'CNY',
        discountAmount: 0,
        lines: [{ productName: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 13 }],
      });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      if (!values.lines || values.lines.length === 0) {
        message.error('请至少添加一行报价明细');
        return;
      }
      const lines: QuoteLineInput[] = values.lines.map((line) => ({
        productName: line.productName,
        specification: line.specification,
        unit: line.unit,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        discountRate: line.discountRate ?? 0,
        taxRate: line.taxRate ?? 13,
        remark: line.remark,
      }));
      const payload: QuoteSaveInput = {
        opportunityId: values.opportunityId,
        title: values.title,
        currency: values.currency,
        validUntil: values.validUntil || undefined,
        discountAmount: values.discountAmount ?? null,
        remark: values.remark,
        lines,
      };
      if (initial) {
        const result = await updateQuote(initial.id, payload);
        message.success(`报价已更新：${result.quoteNo ?? ''}`);
      } else {
        const result = await createQuote(payload);
        message.success(`报价已创建：${result.quoteNo ?? ''}`);
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
      title={initial ? `编辑报价：${initial.quoteNo ?? initial.title}` : '新建报价'}
      width={1080}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item
            name="opportunityId"
            label="关联商机 ID"
            rules={[{ required: true, message: '请输入关联商机 ID' }]}
            style={{ flex: 1 }}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} disabled={Boolean(initial)} />
          </Form.Item>
          <Form.Item
            name="title"
            label="报价标题"
            rules={[{ required: true, message: '请输入报价标题' }]}
            style={{ flex: 2 }}
          >
            <Input maxLength={100} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="currency" label="币种" style={{ flex: 1 }}>
            <Select
              options={[
                { value: 'CNY', label: 'CNY 人民币' },
                { value: 'USD', label: 'USD 美元' },
                { value: 'EUR', label: 'EUR 欧元' },
              ]}
            />
          </Form.Item>
          <Form.Item name="validUntil" label="有效期至" style={{ flex: 1 }}>
            <Input placeholder="yyyy-MM-dd" />
          </Form.Item>
          <Form.Item name="discountAmount" label="整单优惠金额" style={{ flex: 1 }}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
        <Typography.Text strong>报价明细</Typography.Text>
        <Form.List name="lines">
          {(fields, { add, remove }) => (
            <>
              <Table<FormListFieldData>
                style={{ marginTop: 8 }}
                rowKey="key"
                size="small"
                dataSource={fields}
                pagination={false}
                scroll={{ x: 960 }}
                columns={[
                  {
                    title: '产品名称',
                    width: 180,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'productName']}
                        rules={[{ required: true, message: '请输入产品名称' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Input maxLength={100} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '规格',
                    width: 120,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'specification']} style={{ marginBottom: 0 }}>
                        <Input maxLength={50} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '单位',
                    width: 80,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'unit']} style={{ marginBottom: 0 }}>
                        <Input maxLength={20} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '数量',
                    width: 100,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'quantity']}
                        rules={[{ required: true, message: '请输入数量' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '单价',
                    width: 110,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'unitPrice']}
                        rules={[{ required: true, message: '请输入单价' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '折扣率(%)',
                    width: 100,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'discountRate']} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} max={100} precision={2} style={{ width: '100%' }} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '税率(%)',
                    width: 100,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'taxRate']} style={{ marginBottom: 0 }}>
                        <InputNumber min={0} max={100} precision={2} style={{ width: '100%' }} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '备注',
                    width: 140,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'remark']} style={{ marginBottom: 0 }}>
                        <Input maxLength={100} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '操作',
                    width: 60,
                    fixed: 'right',
                    render: (_, field) => (
                      <Button size="small" type="link" danger onClick={() => remove(field.name)}>
                        删除
                      </Button>
                    ),
                  },
                ]}
              />
              <Button
                type="dashed"
                block
                style={{ marginTop: 8 }}
                onClick={() =>
                  add({ productName: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 13 })
                }
              >
                新增明细
              </Button>
            </>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}

interface QuoteDetailDrawerProps {
  quote: QuoteView | null;
  onClose: () => void;
}

/** 报价详情抽屉 */
function QuoteDetailDrawer({ quote, onClose }: QuoteDetailDrawerProps) {
  const [detail, setDetail] = useState<QuoteView | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!quote) {
      setDetail(null);
      return;
    }
    let active = true;
    setLoading(true);
    getQuote(quote.id)
      .then((data) => {
        if (active) {
          setDetail(data);
        }
      })
      .catch((error: unknown) => {
        message.error(errorText(error));
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [quote]);

  const current = detail ?? quote;
  const lineColumns: TableProps<QuoteLineView>['columns'] = [
    { title: '行号', dataIndex: 'lineNo', width: 70, render: (value?: number | null) => value ?? '-' },
    { title: '产品名称', dataIndex: 'productName', width: 160, render: (value?: string | null) => value || '-' },
    {
      title: '规格',
      dataIndex: 'specification',
      width: 110,
      render: (value?: string | null) => value || '-',
    },
    { title: '单位', dataIndex: 'unit', width: 70, render: (value?: string | null) => value || '-' },
    { title: '数量', dataIndex: 'quantity', width: 90, align: 'right', render: (value?: number | string | null) => value ?? '-' },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      width: 110,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '折扣率',
      dataIndex: 'discountRate',
      width: 90,
      align: 'right',
      render: (value?: number | string | null) =>
        value === null || value === undefined ? '-' : `${value}%`,
    },
    {
      title: '税率',
      dataIndex: 'taxRate',
      width: 80,
      align: 'right',
      render: (value?: number | string | null) =>
        value === null || value === undefined ? '-' : `${value}%`,
    },
    {
      title: '行金额',
      dataIndex: 'lineAmount',
      width: 130,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '备注', dataIndex: 'remark', width: 140, render: (value?: string | null) => value || '-' },
  ];

  return (
    <Drawer
      open={Boolean(quote)}
      title={current ? `报价详情：${current.quoteNo ?? current.title}` : '报价详情'}
      width={1000}
      onClose={onClose}
    >
      {current && (
        <Spin spinning={loading}>
          <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="报价编号">{current.quoteNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="版本">v{current.version ?? 1}</Descriptions.Item>
            <Descriptions.Item label="标题">{current.title}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={QUOTE_STATUS_COLOR[current.status ?? '']}>
                {QUOTE_STATUS_LABEL[current.status ?? ''] ?? current.status ?? '-'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="关联商机">{current.opportunityId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="客户 ID">{current.customerId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="币种">{current.currency ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="有效期至">{current.validUntil ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="未税金额">{formatAmount(current.subtotalAmount)}</Descriptions.Item>
            <Descriptions.Item label="税额">{formatAmount(current.taxAmount)}</Descriptions.Item>
            <Descriptions.Item label="整单优惠">{formatAmount(current.discountAmount)}</Descriptions.Item>
            <Descriptions.Item label="价税合计">{formatAmount(current.totalAmount)}</Descriptions.Item>
            <Descriptions.Item label="提交时间">{formatDateTime(current.submittedAt)}</Descriptions.Item>
            <Descriptions.Item label="审批时间">{formatDateTime(current.approvedAt)}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {current.remark || '-'}
            </Descriptions.Item>
          </Descriptions>
          <Table<QuoteLineView>
            rowKey="id"
            size="small"
            columns={lineColumns}
            dataSource={current.lines ?? []}
            pagination={false}
            scroll={{ x: 1050 }}
          />
        </Spin>
      )}
    </Drawer>
  );
}

/** 销售报价页 */
export default function QuotePage() {
  const [form] = Form.useForm<QuoteFilterValues>();
  const [rows, setRows] = useState<QuoteView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<QuotePageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<QuoteView | null>(null);
  const [detailQuote, setDetailQuote] = useState<QuoteView | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageQuotes({ ...filters, current: page.current, size: page.size });
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

  const handleSearch = (values: QuoteFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      opportunityId: values.opportunityId ?? null,
      customerId: values.customerId ?? null,
      status: values.status || undefined,
      ownerId: values.ownerId ?? null,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    form.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleEdit = async (row: QuoteView) => {
    try {
      const detail = await getQuote(row.id);
      setEditing(detail);
      setFormOpen(true);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleSubmit = async (row: QuoteView) => {
    try {
      const result = await submitQuote(row.id);
      message.success(`报价已提交审批：${result.quoteNo ?? ''}`);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleRevise = async (row: QuoteView) => {
    try {
      const result = await reviseQuote(row.id);
      message.success(`已生成新版本：${result.quoteNo ?? ''} v${result.version ?? 1}`);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<QuoteView>['columns'] = [
    { title: '报价编号', dataIndex: 'quoteNo', width: 170, render: (value?: string | null) => value || '-' },
    { title: '标题', dataIndex: 'title', width: 180, ellipsis: true },
    { title: '版本', dataIndex: 'version', width: 70, render: (value?: number | null) => `v${value ?? 1}` },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string | null) => (
        <Tag color={QUOTE_STATUS_COLOR[value ?? '']}>
          {QUOTE_STATUS_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '客户 ID', dataIndex: 'customerId', width: 110, render: (value?: number | null) => value ?? '-' },
    {
      title: '商机 ID',
      dataIndex: 'opportunityId',
      width: 110,
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '价税合计',
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '币种', dataIndex: 'currency', width: 80, render: (value?: string | null) => value || '-' },
    { title: '有效期至', dataIndex: 'validUntil', width: 120, render: (value?: string | null) => value || '-' },
    { title: '负责人', dataIndex: 'ownerId', width: 100, render: (value?: number | null) => value ?? '-' },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 240,
      render: (_, row) => (
        <Space size={0} wrap>
          <Button size="small" type="link" onClick={() => setDetailQuote(row)}>
            详情
          </Button>
          {hasPermission('sys:crm:quote:update') && row.status === 'DRAFT' && (
            <Button size="small" type="link" onClick={() => void handleEdit(row)}>
              编辑
            </Button>
          )}
          {hasPermission('sys:crm:quote:submit') && row.status === 'DRAFT' && (
            <Popconfirm
              title="提交报价审批"
              description="提交后将进入审批流程，报价不可再编辑。"
              okText="提交"
              cancelText="取消"
              onConfirm={() => void handleSubmit(row)}
            >
              <Button size="small" type="link">
                提交
              </Button>
            </Popconfirm>
          )}
          {hasPermission('sys:crm:quote:revise') &&
            (row.status === 'APPROVED' || row.status === 'REJECTED') && (
              <Popconfirm
                title="生成新版本报价"
                description="将基于当前报价生成新版本草稿，旧版本保留。"
                okText="生成"
                cancelText="取消"
                onConfirm={() => void handleRevise(row)}
              >
                <Button size="small" type="link">
                  修订
                </Button>
              </Popconfirm>
            )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="销售报价"
      extra={
        hasPermission('sys:crm:quote:create') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            新建报价
          </Button>
        ) : null
      }
    >
      <Form form={form} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="keyword">
          <Input placeholder="报价编号 / 标题" allowClear style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="opportunityId">
          <InputNumber min={1} precision={0} placeholder="商机 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select placeholder="报价状态" allowClear style={{ width: 130 }} options={QUOTE_STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="ownerId">
          <InputNumber min={1} precision={0} placeholder="负责人 ID" style={{ width: 130 }} />
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
      <Table<QuoteView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1600 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <QuoteFormModal
        open={formOpen}
        initial={editing}
        onCancel={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onSaved={() => {
          setFormOpen(false);
          setEditing(null);
          void loadData();
        }}
      />
      <QuoteDetailDrawer quote={detailQuote} onClose={() => setDetailQuote(null)} />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        仅草稿报价可编辑与提交；已通过或已驳回报价可修订生成新版本草稿。
      </Typography.Text>
    </Card>
  );
}

// 发票与应收：开票申请全流程（草稿 / 审批 / 开票 / 寄送 / 红冲 / 取消）与应收快照对账
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
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { FormListFieldData, TableProps } from 'antd';
import {
  cancelInvoice,
  createInvoice,
  getInvoice,
  issueInvoice,
  pageInvoices,
  redFlushInvoice,
  sendInvoice,
  submitInvoice,
  updateInvoice,
  type InvoiceItemInput,
  type InvoicePageQuery,
  type InvoiceSaveInput,
  type InvoiceView,
} from '../../api/commerce';
import { hasPermission } from '../../utils/permission';
import {
  INVOICE_REQUEST_TYPE_LABEL,
  INVOICE_STATUS_COLOR,
  INVOICE_STATUS_LABEL,
  INVOICE_STATUS_OPTIONS,
  INVOICE_TITLE_TYPE_LABEL,
  INVOICE_TYPE_LABEL,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface InvoiceFilterValues {
  keyword?: string;
  customerId?: number | null;
  contractId?: number | null;
  status?: string;
  requestType?: string;
}

/** 申请行表单值 */
interface InvoiceItemFormValues {
  itemName?: string;
  specification?: string;
  quantity: number;
  unitPrice: number;
  taxRate?: number;
  remark?: string;
}

/** 申请表单值 */
interface InvoiceFormValues {
  sourceType: 'CONTRACT' | 'ORDER';
  sourceId?: number | null;
  currency?: string;
  amount: number;
  invoiceType?: string;
  titleType?: string;
  invoiceTitle?: string;
  taxNo?: string;
  remark?: string;
  items: InvoiceItemFormValues[];
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

interface InvoiceFormModalProps {
  open: boolean;
  initial: InvoiceView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 开票申请新建 / 编辑弹窗 */
function InvoiceFormModal({ open, initial, onCancel, onSaved }: InvoiceFormModalProps) {
  const [form] = Form.useForm<InvoiceFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        sourceType: (initial.sourceType as 'CONTRACT' | 'ORDER') ?? 'CONTRACT',
        sourceId: initial.sourceId ?? undefined,
        currency: initial.currency ?? 'CNY',
        amount: Number(initial.amount ?? 0),
        invoiceType: initial.invoiceType ?? undefined,
        titleType: initial.titleType ?? undefined,
        invoiceTitle: initial.invoiceTitle ?? undefined,
        taxNo: initial.taxNo ?? undefined,
        remark: initial.remark ?? undefined,
        items: (initial.items ?? []).map((item) => ({
          itemName: item.itemName ?? undefined,
          specification: item.specification ?? undefined,
          quantity: Number(item.quantity ?? 1),
          unitPrice: Number(item.unitPrice ?? 0),
          taxRate: item.taxRate === null || item.taxRate === undefined ? undefined : Number(item.taxRate),
          remark: item.remark ?? undefined,
        })),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        sourceType: 'CONTRACT',
        currency: 'CNY',
        amount: 0,
        items: [],
      });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const items: InvoiceItemInput[] = (values.items ?? []).map((item) => ({
        itemName: item.itemName || undefined,
        specification: item.specification || undefined,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        taxRate: item.taxRate,
        remark: item.remark,
      }));
      const payload: InvoiceSaveInput = {
        sourceType: values.sourceType,
        sourceId: values.sourceId ?? null,
        currency: values.currency,
        amount: values.amount,
        invoiceType: values.invoiceType,
        titleType: values.titleType,
        invoiceTitle: values.invoiceTitle,
        taxNo: values.taxNo,
        remark: values.remark,
        items,
      };
      if (initial) {
        await updateInvoice(initial.id, payload);
        message.success('开票申请已更新');
      } else {
        const created = await createInvoice(payload);
        message.success(`开票申请已创建：${created.invoiceNo}`);
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
      title={initial ? `编辑开票申请：${initial.invoiceNo}` : '新建开票申请'}
      width={1000}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="sourceType" label="来源类型" style={{ flex: 1 }}>
            <Select
              disabled={Boolean(initial)}
              options={[
                { value: 'CONTRACT', label: '合同台账' },
                { value: 'ORDER', label: '商务订单' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="sourceId"
            label="来源单据 ID"
            rules={[{ required: true, message: '请输入来源单据 ID' }]}
            style={{ flex: 1 }}
          >
            <InputNumber min={1} precision={0} disabled={Boolean(initial)} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="amount"
            label="申请金额"
            rules={[{ required: true, message: '请输入申请金额' }]}
            style={{ flex: 1 }}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="currency" label="币种" style={{ flex: 1 }}>
            <Select
              options={[
                { value: 'CNY', label: 'CNY 人民币' },
                { value: 'USD', label: 'USD 美元' },
                { value: 'EUR', label: 'EUR 欧元' },
              ]}
            />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="invoiceType" label="发票类型" style={{ flex: 1 }}>
            <Select
              allowClear
              options={[
                { value: 'VAT_SPECIAL', label: '增值税专用发票' },
                { value: 'VAT_NORMAL', label: '增值税普通发票' },
                { value: 'ELECTRONIC', label: '电子发票' },
              ]}
            />
          </Form.Item>
          <Form.Item name="titleType" label="抬头类型" style={{ flex: 1 }}>
            <Select
              allowClear
              options={[
                { value: 'COMPANY', label: '单位' },
                { value: 'PERSONAL', label: '个人' },
              ]}
            />
          </Form.Item>
          <Form.Item name="invoiceTitle" label="发票抬头" style={{ flex: 1 }}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="taxNo" label="纳税人识别号" style={{ flex: 1 }}>
            <Input maxLength={30} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
        <Typography.Text strong>申请明细（可选，填写时合计须等于申请金额）</Typography.Text>
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <>
              <Table<FormListFieldData>
                style={{ marginTop: 8 }}
                rowKey="key"
                size="small"
                dataSource={fields}
                pagination={false}
                scroll={{ x: 820 }}
                columns={[
                  {
                    title: '项目名称',
                    width: 180,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'itemName']} style={{ marginBottom: 0 }}>
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
                    width: 150,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'remark']} style={{ marginBottom: 0 }}>
                        <Input maxLength={100} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '操作',
                    width: 60,
                    render: (_, field) => (
                      <Button size="small" type="link" danger onClick={() => remove(field.name)}>
                        删除
                      </Button>
                    ),
                  },
                ]}
              />
              <Button type="dashed" block style={{ marginTop: 8 }} onClick={() => add({ quantity: 1, unitPrice: 0 })}>
                新增明细
              </Button>
            </>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}

interface InvoiceDetailDrawerProps {
  invoiceId: number | null;
  onClose: () => void;
}

/** 开票申请详情抽屉 */
function InvoiceDetailDrawer({ invoiceId, onClose }: InvoiceDetailDrawerProps) {
  const [detail, setDetail] = useState<InvoiceView | null>(null);

  useEffect(() => {
    if (!invoiceId) {
      setDetail(null);
      return;
    }
    let active = true;
    getInvoice(invoiceId)
      .then((data) => {
        if (active) {
          setDetail(data);
        }
      })
      .catch((error: unknown) => {
        message.error(errorText(error));
      });
    return () => {
      active = false;
    };
  }, [invoiceId]);

  return (
    <Drawer
      open={Boolean(invoiceId)}
      title={detail ? `开票申请详情：${detail.invoiceNo}` : '开票申请详情'}
      width={760}
      onClose={onClose}
    >
      {detail && (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="申请单号">{detail.invoiceNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={INVOICE_STATUS_COLOR[detail.status as string]}>
                {INVOICE_STATUS_LABEL[detail.status as string] ?? detail.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="申请类型">
              {INVOICE_REQUEST_TYPE_LABEL[detail.requestType ?? ''] ?? detail.requestType ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="原申请 ID">{detail.originInvoiceId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="客户">{detail.customerName || detail.customerCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="合同编号">{detail.contractNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="申请金额">{formatAmount(detail.amount)}</Descriptions.Item>
            <Descriptions.Item label="可开票余额">{formatAmount(detail.availableAmount)}</Descriptions.Item>
            <Descriptions.Item label="发票类型">
              {INVOICE_TYPE_LABEL[detail.invoiceType ?? ''] ?? detail.invoiceType ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="抬头类型">
              {INVOICE_TITLE_TYPE_LABEL[detail.titleType ?? ''] ?? detail.titleType ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="发票抬头">{detail.invoiceTitle || '-'}</Descriptions.Item>
            <Descriptions.Item label="纳税人识别号">{detail.taxNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="发票代码">{detail.invoiceCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="发票号码">{detail.invoiceNumber || '-'}</Descriptions.Item>
            <Descriptions.Item label="实际开票金额">{formatAmount(detail.issuedAmount)}</Descriptions.Item>
            <Descriptions.Item label="开票时间">{formatDateTime(detail.issuedAt)}</Descriptions.Item>
            <Descriptions.Item label="快递公司">{detail.courier || '-'}</Descriptions.Item>
            <Descriptions.Item label="运单号">{detail.trackingNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="寄送时间">{formatDateTime(detail.sentAt)}</Descriptions.Item>
            <Descriptions.Item label="红冲原因">{detail.redFlushReason || '-'}</Descriptions.Item>
            <Descriptions.Item label="红冲时间">{formatDateTime(detail.redFlushedAt)}</Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {detail.remark || '-'}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Text strong style={{ display: 'block', margin: '16px 0 8px' }}>
            申请明细
          </Typography.Text>
          <Table
            rowKey={(item) => String(item.id ?? item.lineNo ?? 0)}
            size="small"
            pagination={false}
            dataSource={detail.items ?? []}
            columns={[
              { title: '行号', dataIndex: 'lineNo', width: 60 },
              { title: '项目名称', dataIndex: 'itemName', width: 160, render: (value) => value || '-' },
              { title: '规格', dataIndex: 'specification', width: 120, render: (value) => value || '-' },
              {
                title: '数量',
                dataIndex: 'quantity',
                width: 90,
                align: 'right',
                render: (value) => value ?? '-',
              },
              {
                title: '单价',
                dataIndex: 'unitPrice',
                width: 110,
                align: 'right',
                render: (value) => formatAmount(value),
              },
              {
                title: '税率(%)',
                dataIndex: 'taxRate',
                width: 90,
                align: 'right',
                render: (value) => value ?? '-',
              },
              {
                title: '行金额',
                dataIndex: 'amount',
                width: 120,
                align: 'right',
                render: (value) => formatAmount(value),
              },
            ]}
          />
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            开票审批由 whale-workflow 负责；开票事实登记在 CRM，应收与总账以 ERP 为准。
          </Typography.Text>
        </>
      )}
    </Drawer>
  );
}

/** 发票与应收页 */
export default function InvoicePage() {
  const [filterForm] = Form.useForm<InvoiceFilterValues>();
  const [rows, setRows] = useState<InvoiceView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<InvoicePageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [editing, setEditing] = useState<InvoiceView | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [actionTarget, setActionTarget] = useState<InvoiceView | null>(null);
  const [actionType, setActionType] = useState<'issue' | 'send' | 'redFlush' | null>(null);
  const [issueForm] = Form.useForm<{ invoiceCode?: string; invoiceNumber?: string; issuedAmount: number; taxAmount?: number }>();
  const [sendForm] = Form.useForm<{ courier: string; trackingNo: string }>();
  const [redFlushForm] = Form.useForm<{ amount: number; reason: string }>();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageInvoices({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: InvoiceFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      customerId: values.customerId ?? null,
      contractId: values.contractId ?? null,
      status: values.status || undefined,
      requestType: values.requestType || undefined,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const runAction = async (action: () => Promise<InvoiceView>, successText: string) => {
    try {
      await action();
      message.success(successText);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const openAction = (row: InvoiceView, type: 'issue' | 'send' | 'redFlush') => {
    setActionTarget(row);
    setActionType(type);
    if (type === 'issue') {
      issueForm.setFieldsValue({
        invoiceCode: row.invoiceCode ?? undefined,
        invoiceNumber: row.invoiceNumber ?? undefined,
        issuedAmount: Number(row.amount ?? 0),
        taxAmount: row.taxAmount === null || row.taxAmount === undefined ? undefined : Number(row.taxAmount),
      });
    } else if (type === 'send') {
      sendForm.setFieldsValue({ courier: row.courier ?? '', trackingNo: row.trackingNo ?? '' });
    } else {
      redFlushForm.setFieldsValue({ amount: Number(row.issuedAmount ?? row.amount ?? 0), reason: '' });
    }
  };

  const closeAction = () => {
    setActionTarget(null);
    setActionType(null);
  };

  const handleIssue = async () => {
    if (!actionTarget) {
      return;
    }
    const values = await issueForm.validateFields();
    await runAction(() => issueInvoice(actionTarget.id, values), '已登记开票记录');
    closeAction();
  };

  const handleSend = async () => {
    if (!actionTarget) {
      return;
    }
    const values = await sendForm.validateFields();
    await runAction(() => sendInvoice(actionTarget.id, values), '已登记发票寄送');
    closeAction();
  };

  const handleRedFlush = async () => {
    if (!actionTarget) {
      return;
    }
    const values = await redFlushForm.validateFields();
    await runAction(() => redFlushInvoice(actionTarget.id, values), '红冲单已生成');
    closeAction();
  };

  const columns: TableProps<InvoiceView>['columns'] = [
    { title: '申请单号', dataIndex: 'invoiceNo', width: 190, fixed: 'left' },
    {
      title: '类型',
      dataIndex: 'requestType',
      width: 100,
      render: (value?: string | null) => (
        <Tag color={value === 'RED_FLUSH' ? 'purple' : 'blue'}>
          {INVOICE_REQUEST_TYPE_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '客户', dataIndex: 'customerName', width: 170, render: (value?: string | null) => value || '-' },
    { title: '客户编码', dataIndex: 'customerCode', width: 130, render: (value?: string | null) => value || '-' },
    {
      title: '申请金额',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string | null) => (
        <Tag color={INVOICE_STATUS_COLOR[value ?? '']}>
          {INVOICE_STATUS_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '发票号码', dataIndex: 'invoiceNumber', width: 150, render: (value?: string | null) => value || '-' },
    {
      title: '开票时间',
      dataIndex: 'issuedAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
    { title: '运单号', dataIndex: 'trackingNo', width: 150, render: (value?: string | null) => value || '-' },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 260,
      render: (_, row) => (
        <Space size={0}>
          <Button size="small" type="link" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 'DRAFT' && hasPermission('sys:crm:invoice:create') && (
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
          {(row.status === 'DRAFT' || row.status === 'REJECTED') && hasPermission('sys:crm:invoice:submit') && (
            <Popconfirm
              title="提交审批"
              description="提交后进入审批流程，草稿不可再编辑。"
              onConfirm={() => void runAction(() => submitInvoice(row.id), '已提交审批')}
            >
              <Button size="small" type="link">
                提交
              </Button>
            </Popconfirm>
          )}
          {row.status === 'APPROVED' && hasPermission('sys:crm:invoice:issue') && (
            <Button size="small" type="link" onClick={() => openAction(row, 'issue')}>
              开票
            </Button>
          )}
          {row.status === 'ISSUED' && hasPermission('sys:crm:invoice:send') && (
            <Button size="small" type="link" onClick={() => openAction(row, 'send')}>
              寄送
            </Button>
          )}
          {(row.status === 'ISSUED' || row.status === 'SENT') && hasPermission('sys:crm:invoice:red-flush') && (
            <Button size="small" type="link" danger onClick={() => openAction(row, 'redFlush')}>
              红冲
            </Button>
          )}
          {(row.status === 'DRAFT' || row.status === 'REJECTED') && hasPermission('sys:crm:invoice:create') && (
            <Popconfirm
              title="取消申请"
              description="取消后不可恢复。"
              onConfirm={() => void runAction(() => cancelInvoice(row.id), '申请已取消')}
            >
              <Button size="small" type="link" danger>
                取消
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="发票与应收"
      extra={
        hasPermission('sys:crm:invoice:create') && (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            新建开票申请
          </Button>
        )
      }
    >
      <Form form={filterForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="keyword">
          <Input placeholder="申请单号 / 发票号码" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 120 }} />
        </Form.Item>
        <Form.Item name="contractId">
          <InputNumber min={1} precision={0} placeholder="合同台账 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select placeholder="状态" allowClear style={{ width: 140 }} options={INVOICE_STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="requestType">
          <Select
            placeholder="类型"
            allowClear
            style={{ width: 130 }}
            options={[
              { value: 'NORMAL', label: '正常开票' },
              { value: 'RED_FLUSH', label: '红冲' },
            ]}
          />
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
      <Table<InvoiceView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1500 }}
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
        开票审批由 workflow 负责；开票事实登记在 CRM，应收余额与账龄以 ERP 为准。
      </Typography.Text>

      <InvoiceFormModal
        open={formOpen}
        initial={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          void loadData();
        }}
      />
      <InvoiceDetailDrawer invoiceId={detailId} onClose={() => setDetailId(null)} />

      <Modal
        open={actionType === 'issue'}
        title="登记开票记录"
        okText="确定"
        cancelText="取消"
        onOk={() => void handleIssue()}
        onCancel={closeAction}
        destroyOnClose
      >
        <Form form={issueForm} layout="vertical" preserve={false}>
          <Form.Item name="invoiceCode" label="发票代码">
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item name="invoiceNumber" label="发票号码" rules={[{ required: true, message: '请输入发票号码' }]}>
            <Input maxLength={30} />
          </Form.Item>
          <Form.Item name="issuedAmount" label="实际开票金额" rules={[{ required: true, message: '请输入开票金额' }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="taxAmount" label="税额">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={actionType === 'send'}
        title="登记发票寄送"
        okText="确定"
        cancelText="取消"
        onOk={() => void handleSend()}
        onCancel={closeAction}
        destroyOnClose
      >
        <Form form={sendForm} layout="vertical" preserve={false}>
          <Form.Item name="courier" label="快递公司" rules={[{ required: true, message: '请输入快递公司' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="trackingNo" label="运单号" rules={[{ required: true, message: '请输入运单号' }]}>
            <Input maxLength={50} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={actionType === 'redFlush'}
        title="发票红冲"
        okText="确定"
        cancelText="取消"
        onOk={() => void handleRedFlush()}
        onCancel={closeAction}
        destroyOnClose
      >
        <Form form={redFlushForm} layout="vertical" preserve={false}>
          <Form.Item name="amount" label="红冲金额" rules={[{ required: true, message: '请输入红冲金额' }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reason" label="红冲原因" rules={[{ required: true, message: '请输入红冲原因' }]}>
            <Input.TextArea rows={2} maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

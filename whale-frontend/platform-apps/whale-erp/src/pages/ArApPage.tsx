// ERP 应收应付页：应收台账 / 应付台账 / 账龄分析
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TabsProps, TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createPayable,
  createReceivable,
  pagePayables,
  pageReceivables,
  payableAging,
  provisionBadDebt,
  receivableAging,
  settlePayable,
  settleReceivable,
  type AgingRow,
  type Payable,
  type Receivable,
  type SettleInput,
} from '../api/finance';
import { pageCustomers, type ErpCustomer } from '../api/masterdata';
import { listErpSuppliers, type ErpSupplier } from '../api/erp';
import { hasPermission } from '../utils/permission';

/** 台账状态文案 */
const STATUS_LABEL: Record<string, string> = {
  OPEN: '未结',
  PARTIALLY_SETTLED: '部分结清',
  SETTLED: '已结清',
  WRITTEN_OFF: '坏账核销',
};

/** 状态颜色 */
function ledgerColor(status: string): string {
  if (status === 'SETTLED') return 'green';
  if (status === 'WRITTEN_OFF') return 'red';
  if (status === 'PARTIALLY_SETTLED') return 'orange';
  return 'blue';
}

/** 金额格式化 */
function money(value: number | undefined): string {
  return Number(value ?? 0).toFixed(2);
}

/** 核销目标 */
type SettleTarget =
  | { kind: 'AR'; record: Receivable }
  | { kind: 'AP'; record: Payable }
  | { kind: 'BAD'; record: Receivable };

/** 应收单表单值 */
interface ReceivableFormValues {
  customerId: number;
  docDate: Dayjs;
  dueDate: Dayjs;
  amount: number;
  remark?: string;
}

/** 应付单表单值 */
interface PayableFormValues {
  supplierId: number;
  docDate: Dayjs;
  dueDate: Dayjs;
  amount: number;
  remark?: string;
}

/** 应收页签 */
function ReceivableTab() {
  const [data, setData] = useState<Receivable[]>([]);
  const [loading, setLoading] = useState(false);
  const [version, setVersion] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [customers, setCustomers] = useState<ErpCustomer[]>([]);
  const [target, setTarget] = useState<SettleTarget | null>(null);
  const [form] = Form.useForm<ReceivableFormValues>();
  const [settleForm] = Form.useForm<SettleInput>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageReceivables({ size: 50, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    const page = await pageCustomers({ size: 100 });
    setCustomers(page.records);
    form.resetFields();
    form.setFieldsValue({ docDate: dayjs(), dueDate: dayjs().add(30, 'day') });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createReceivable({
      customerId: values.customerId,
      docDate: values.docDate.format('YYYY-MM-DD'),
      dueDate: values.dueDate.format('YYYY-MM-DD'),
      amount: values.amount,
      remark: values.remark,
    });
    message.success('应收单已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handleSettle = async () => {
    if (!target) return;
    const values = await settleForm.validateFields().catch(() => null);
    if (!values) return;
    if (target.kind === 'AR') {
      await settleReceivable(target.record.id, values);
    } else if (target.kind === 'AP') {
      await settlePayable(target.record.id, values);
    } else {
      await provisionBadDebt(target.record.id, values);
    }
    message.success('操作成功');
    setTarget(null);
    setVersion((value) => value + 1);
  };

  useEffect(() => {
    if (target) {
      settleForm.resetFields();
      settleForm.setFieldsValue({
        amount: target.record.remainingAmount,
        settleType: target.kind === 'BAD' ? 'OFFSET' : 'BANK',
      });
    }
  }, [target, settleForm]);

  const columns: TableProps<Receivable>['columns'] = [
    { title: '应收单号', dataIndex: 'docNo', key: 'docNo', width: 160 },
    { title: '客户', dataIndex: 'customerName', key: 'customerName' },
    { title: '单据日期', dataIndex: 'docDate', key: 'docDate', width: 110 },
    { title: '到期日', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
    { title: '应收金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
    {
      title: '已核销',
      dataIndex: 'settledAmount',
      key: 'settledAmount',
      width: 120,
      render: money,
    },
    { title: '坏账', dataIndex: 'badDebtAmount', key: 'badDebtAmount', width: 110, render: money },
    {
      title: '未结余额',
      dataIndex: 'remainingAmount',
      key: 'remainingAmount',
      width: 120,
      render: money,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value) => (
        <Tag color={ledgerColor(String(value))}>{STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_, record) => (
        <Space size="small">
          {record.status !== 'SETTLED' &&
          record.status !== 'WRITTEN_OFF' &&
          hasPermission('sys:erp:receivable:settle') ? (
            <Button type="link" size="small" onClick={() => setTarget({ kind: 'AR', record })}>
              核销
            </Button>
          ) : null}
          {record.status !== 'SETTLED' &&
          record.status !== 'WRITTEN_OFF' &&
          hasPermission('sys:erp:receivable:bad-debt') ? (
            <Button
              type="link"
              size="small"
              danger
              onClick={() => setTarget({ kind: 'BAD', record })}
            >
              坏账
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Input.Search
          allowClear
          placeholder="单号 / 客户"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:erp:receivable:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增应收单
          </Button>
        ) : null}
      </Space>
      <Table<Receivable> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增应收单"
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="customerId"
            label="客户"
            rules={[{ required: true, message: '请选择客户' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={customers.map((item) => ({
                value: item.id,
                label: `${item.code} ${item.name}`,
              }))}
            />
          </Form.Item>
          <Space size="large">
            <Form.Item name="docDate" label="单据日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="dueDate" label="到期日" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.Item
            name="amount"
            label="应收金额"
            rules={[{ required: true, message: '请输入金额' }]}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={target !== null}
        title={target?.kind === 'BAD' ? '坏账计提' : '应收核销'}
        onOk={handleSettle}
        onCancel={() => setTarget(null)}
        destroyOnHidden
      >
        <Form form={settleForm} layout="vertical">
          <Form.Item name="amount" label="金额" rules={[{ required: true, message: '请输入金额' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="settleType" label="方式">
            <Select
              disabled={target?.kind === 'BAD'}
              options={[
                { value: 'BANK', label: '银行' },
                { value: 'CASH', label: '现金' },
                { value: 'OFFSET', label: '冲抵' },
              ]}
            />
          </Form.Item>
          <Form.Item name="referenceNo" label="流水号">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 应付页签 */
function PayableTab() {
  const [data, setData] = useState<Payable[]>([]);
  const [loading, setLoading] = useState(false);
  const [version, setVersion] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [suppliers, setSuppliers] = useState<ErpSupplier[]>([]);
  const [target, setTarget] = useState<Payable | null>(null);
  const [form] = Form.useForm<PayableFormValues>();
  const [settleForm] = Form.useForm<SettleInput>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePayables({ size: 50, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    setSuppliers(await listErpSuppliers());
    form.resetFields();
    form.setFieldsValue({ docDate: dayjs(), dueDate: dayjs().add(30, 'day') });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const supplier = suppliers.find((item) => Number(item.id) === values.supplierId);
    await createPayable({
      supplierId: values.supplierId,
      supplierName: supplier?.name,
      docDate: values.docDate.format('YYYY-MM-DD'),
      dueDate: values.dueDate.format('YYYY-MM-DD'),
      amount: values.amount,
      remark: values.remark,
    });
    message.success('应付单已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handleSettle = async () => {
    if (!target) return;
    const values = await settleForm.validateFields().catch(() => null);
    if (!values) return;
    await settlePayable(target.id, values);
    message.success('付款核销成功');
    setTarget(null);
    setVersion((value) => value + 1);
  };

  useEffect(() => {
    if (target) {
      settleForm.resetFields();
      settleForm.setFieldsValue({ amount: target.remainingAmount, settleType: 'BANK' });
    }
  }, [target, settleForm]);

  const columns: TableProps<Payable>['columns'] = [
    { title: '应付单号', dataIndex: 'docNo', key: 'docNo', width: 160 },
    { title: '供应商', dataIndex: 'supplierName', key: 'supplierName' },
    { title: '单据日期', dataIndex: 'docDate', key: 'docDate', width: 110 },
    { title: '到期日', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
    { title: '应付金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
    {
      title: '已核销',
      dataIndex: 'settledAmount',
      key: 'settledAmount',
      width: 120,
      render: money,
    },
    {
      title: '未结余额',
      dataIndex: 'remainingAmount',
      key: 'remainingAmount',
      width: 120,
      render: money,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value) => (
        <Tag color={ledgerColor(String(value))}>{STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) =>
        record.status !== 'SETTLED' && hasPermission('sys:erp:payable:settle') ? (
          <Button type="link" size="small" onClick={() => setTarget(record)}>
            付款核销
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Input.Search
          allowClear
          placeholder="单号 / 供应商"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:erp:payable:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增应付单
          </Button>
        ) : null}
      </Space>
      <Table<Payable> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增应付单"
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="supplierId"
            label="供应商"
            rules={[{ required: true, message: '请选择供应商' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={suppliers.map((item) => ({
                value: Number(item.id),
                label: `${item.code} ${item.name}`,
              }))}
            />
          </Form.Item>
          <Space size="large">
            <Form.Item name="docDate" label="单据日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="dueDate" label="到期日" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.Item
            name="amount"
            label="应付金额"
            rules={[{ required: true, message: '请输入金额' }]}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={target !== null}
        title="应付付款核销"
        onOk={handleSettle}
        onCancel={() => setTarget(null)}
        destroyOnHidden
      >
        <Form form={settleForm} layout="vertical">
          <Form.Item
            name="amount"
            label="付款金额"
            rules={[{ required: true, message: '请输入金额' }]}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="settleType" label="方式">
            <Select
              options={[
                { value: 'BANK', label: '银行' },
                { value: 'CASH', label: '现金' },
                { value: 'OFFSET', label: '冲抵' },
              ]}
            />
          </Form.Item>
          <Form.Item name="referenceNo" label="流水号">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 账龄页签 */
function AgingTab() {
  const [dimension, setDimension] = useState<'AR' | 'AP'>('AR');
  const [data, setData] = useState<AgingRow[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(dimension === 'AR' ? await receivableAging() : await payableAging());
    } finally {
      setLoading(false);
    }
  }, [dimension]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<AgingRow>['columns'] = [
    { title: '往来单位', dataIndex: 'partyName', key: 'partyName' },
    { title: '未结余额', dataIndex: 'total', key: 'total', width: 120, render: money },
    { title: '未到期', dataIndex: 'notDue', key: 'notDue', width: 110, render: money },
    { title: '1–30 天', dataIndex: 'overdue1To30', key: 'overdue1To30', width: 110, render: money },
    {
      title: '31–60 天',
      dataIndex: 'overdue31To60',
      key: 'overdue31To60',
      width: 110,
      render: money,
    },
    {
      title: '61–90 天',
      dataIndex: 'overdue61To90',
      key: 'overdue61To90',
      width: 110,
      render: money,
    },
    {
      title: '90 天以上',
      dataIndex: 'overdueOver90',
      key: 'overdueOver90',
      width: 120,
      render: money,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Select
        value={dimension}
        style={{ width: 160 }}
        onChange={(value) => setDimension(value)}
        options={[
          { value: 'AR', label: '应收账龄' },
          { value: 'AP', label: '应付账龄' },
        ]}
      />
      <Table<AgingRow> rowKey="partyId" loading={loading} columns={columns} dataSource={data} />
    </Space>
  );
}

/** 应收应付页 */
export default function ArApPage() {
  const items: TabsProps['items'] = [
    { key: 'receivable', label: '应收台账', children: <ReceivableTab /> },
    { key: 'payable', label: '应付台账', children: <PayableTab /> },
    { key: 'aging', label: '账龄分析', children: <AgingTab /> },
  ];

  return <Tabs items={items} />;
}

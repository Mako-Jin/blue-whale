// ERP 主数据页：科目 / 会计期间 / 客户 / 仓库四个页签
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TabsProps, TableProps } from 'antd';
import {
  closePeriod,
  pageAccounts,
  pageCustomers,
  pagePeriods,
  pageWarehouses,
  reopenPeriod,
  saveAccount,
  saveCustomer,
  saveWarehouse,
  type ErpAccount,
  type ErpAccountInput,
  type ErpCustomer,
  type ErpCustomerInput,
  type ErpPeriod,
  type ErpWarehouse,
  type ErpWarehouseInput,
} from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { getCreditUsage, type CreditUsageView } from '../api/scm';

/** 金额展示（空值显示占位） */
function amount(value?: number | null): string {
  return value == null ? '-' : Number(value).toFixed(2);
}

/** 科目类型文案 */
const ACCOUNT_TYPE_LABEL: Record<string, string> = {
  ASSET: '资产',
  LIABILITY: '负债',
  EQUITY: '权益',
  REVENUE: '收入',
  EXPENSE: '费用',
};

/** 余额方向文案 */
const DIRECTION_LABEL: Record<string, string> = {
  DEBIT: '借方',
  CREDIT: '贷方',
};

/** 状态标签 */
function StatusTag({ status }: { status: string }) {
  const enabled = status === 'ENABLED' || status === 'OPEN';
  return (
    <Tag color={enabled ? 'green' : 'default'}>
      {status === 'OPEN' ? '未关账' : status === 'CLOSED' ? '已关账' : enabled ? '启用' : '禁用'}
    </Tag>
  );
}

/** 科目页签 */
function AccountTab() {
  const [data, setData] = useState<ErpAccount[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ErpAccount | null>(null);
  const [version, setVersion] = useState(0);
  const [form] = Form.useForm<Omit<ErpAccountInput, 'id'>>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageAccounts({ size: 100, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue(editing);
    } else {
      form.resetFields();
    }
  }, [open, editing, form]);

  const handleSave = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await saveAccount({ ...values, id: editing?.id });
    message.success('保存成功');
    setOpen(false);
    setVersion((value) => value + 1);
  };

  const columns: TableProps<ErpAccount>['columns'] = [
    { title: '科目编码', dataIndex: 'code', key: 'code', width: 110 },
    { title: '科目名称', dataIndex: 'name', key: 'name' },
    {
      title: '类型',
      dataIndex: 'accountType',
      key: 'accountType',
      width: 90,
      render: (value) => ACCOUNT_TYPE_LABEL[value as string] ?? value,
    },
    {
      title: '方向',
      dataIndex: 'direction',
      key: 'direction',
      width: 80,
      render: (value) => DIRECTION_LABEL[value as string] ?? value,
    },
    {
      title: '末级',
      dataIndex: 'leaf',
      key: 'leaf',
      width: 70,
      render: (value) => (value ? '是' : '否'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => <StatusTag status={String(value)} />,
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        hasPermission('sys:erp:account:edit') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              setOpen(true);
            }}
          >
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Input.Search
          allowClear
          placeholder="按编码或名称搜索"
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:erp:account:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增科目
          </Button>
        ) : null}
      </Space>
      <Table<ErpAccount> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={open}
        title={editing ? '编辑会计科目' : '新增会计科目'}
        onOk={handleSave}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ leaf: true, status: 'ENABLED' }}>
          <Form.Item name="code" label="科目编码" rules={[{ required: true, message: '请输入科目编码' }]}>
            <Input placeholder="如 1001" maxLength={32} />
          </Form.Item>
          <Form.Item name="name" label="科目名称" rules={[{ required: true, message: '请输入科目名称' }]}>
            <Input placeholder="如 库存现金" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="accountType"
            label="科目类型"
            rules={[{ required: true, message: '请选择科目类型' }]}
          >
            <Select
              options={Object.entries(ACCOUNT_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item
            name="direction"
            label="余额方向"
            rules={[{ required: true, message: '请选择余额方向' }]}
          >
            <Select
              options={Object.entries(DIRECTION_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="parentCode" label="上级科目编码">
            <Input placeholder="选填" maxLength={32} />
          </Form.Item>
          <Form.Item name="leaf" label="是否末级科目" rules={[{ required: true }]}>
            <Select
              options={[
                { value: true, label: '是（可记账）' },
                { value: false, label: '否（汇总科目）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '禁用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 会计期间页签 */
function PeriodTab() {
  const [data, setData] = useState<ErpPeriod[]>([]);
  const [loading, setLoading] = useState(false);
  const [version, setVersion] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePeriods({ size: 100 });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, version]);

  const handleClose = async (id: number) => {
    await closePeriod(id);
    message.success('关账成功');
    setVersion((value) => value + 1);
  };

  const handleReopen = async (id: number) => {
    await reopenPeriod(id);
    message.success('反结账成功');
    setVersion((value) => value + 1);
  };

  const columns: TableProps<ErpPeriod>['columns'] = [
    { title: '期间', dataIndex: 'periodCode', key: 'periodCode', width: 100 },
    { title: '开始日期', dataIndex: 'startDate', key: 'startDate', width: 120 },
    { title: '结束日期', dataIndex: 'endDate', key: 'endDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => <StatusTag status={String(value)} />,
    },
    {
      title: '关账时间',
      dataIndex: 'closedAt',
      key: 'closedAt',
      width: 180,
      render: (value) => (value ? String(value).replace('T', ' ') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space size="small">
          {record.status === 'OPEN' && hasPermission('sys:erp:period:close') ? (
            <Button type="link" size="small" onClick={() => handleClose(record.id)}>
              关账
            </Button>
          ) : null}
          {record.status === 'CLOSED' && hasPermission('sys:erp:period:reopen') ? (
            <Button type="link" size="small" danger onClick={() => handleReopen(record.id)}>
              反结账
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return <Table<ErpPeriod> rowKey="id" loading={loading} columns={columns} dataSource={data} />;
}

/** 客户页签 */
function CustomerTab() {
  const [data, setData] = useState<ErpCustomer[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ErpCustomer | null>(null);
  const [version, setVersion] = useState(0);
  const [creditCustomer, setCreditCustomer] = useState<ErpCustomer | null>(null);
  const [creditUsage, setCreditUsage] = useState<CreditUsageView | null>(null);
  const [creditLoading, setCreditLoading] = useState(false);
  const [form] = Form.useForm<Omit<ErpCustomerInput, 'id'>>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageCustomers({ size: 100, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue(editing);
    } else {
      form.resetFields();
    }
  }, [open, editing, form]);

  const handleSave = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await saveCustomer({ ...values, id: editing?.id });
    message.success('保存成功');
    setOpen(false);
    setVersion((value) => value + 1);
  };

  const openCredit = async (customer: ErpCustomer) => {
    setCreditCustomer(customer);
    setCreditUsage(null);
    setCreditLoading(true);
    try {
      setCreditUsage(await getCreditUsage(customer.id));
    } finally {
      setCreditLoading(false);
    }
  };

  const columns: TableProps<ErpCustomer>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 110 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '简称', dataIndex: 'shortName', key: 'shortName', width: 120 },
    { title: '联系人', dataIndex: 'contactName', key: 'contactName', width: 100 },
    { title: '联系电话', dataIndex: 'contactPhone', key: 'contactPhone', width: 130 },
    {
      title: '信用额度',
      dataIndex: 'creditLimit',
      key: 'creditLimit',
      width: 120,
      render: (value) => Number(value ?? 0).toFixed(2),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => <StatusTag status={String(value)} />,
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          {hasPermission('sys:erp:customer:edit') ? (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditing(record);
                setOpen(true);
              }}
            >
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:erp:credit-usage:view') ? (
            <Button type="link" size="small" onClick={() => void openCredit(record)}>
              信用
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
          placeholder="按编码或名称搜索"
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:erp:customer:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增客户
          </Button>
        ) : null}
      </Space>
      <Table<ErpCustomer> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={open}
        title={editing ? '编辑客户' : '新增客户'}
        onOk={handleSave}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ status: 'ENABLED', creditLimit: 0 }}>
          <Form.Item name="code" label="客户编码" rules={[{ required: true, message: '请输入客户编码' }]}>
            <Input placeholder="如 CUS-001" maxLength={32} />
          </Form.Item>
          <Form.Item name="name" label="客户名称" rules={[{ required: true, message: '请输入客户名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="shortName" label="客户简称">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="taxNo" label="纳税人识别号">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="contactName" label="联系人">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="contactPhone" label="联系电话">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="creditLimit" label="信用额度">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '禁用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={Boolean(creditCustomer)}
        title={`信用占用 - ${creditCustomer?.name ?? ''}`}
        footer={null}
        width={560}
        onCancel={() => setCreditCustomer(null)}
        destroyOnHidden
      >
        <Spin spinning={creditLoading}>
          {creditUsage ? (
            <>
              {creditUsage.exceeded ? (
                <Tag color="red" style={{ marginBottom: 12 }}>
                  已超额
                </Tag>
              ) : null}
              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  { key: 'customer', label: '客户', children: creditUsage.customerName },
                  {
                    key: 'limit',
                    label: '信用额度',
                    children: creditUsage.unlimited ? '不限' : amount(creditUsage.creditLimit),
                  },
                  { key: 'outstanding', label: '未结应收', children: amount(creditUsage.outstanding) },
                  { key: 'reserved', label: '在途预占', children: amount(creditUsage.reserved) },
                  { key: 'occupied', label: '已占用', children: amount(creditUsage.occupied) },
                  { key: 'available', label: '可用额度', children: amount(creditUsage.available) },
                ]}
              />
            </>
          ) : null}
        </Spin>
      </Modal>
    </Space>
  );
}

/** 仓库页签 */
function WarehouseTab() {
  const [data, setData] = useState<ErpWarehouse[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ErpWarehouse | null>(null);
  const [version, setVersion] = useState(0);
  const [form] = Form.useForm<Omit<ErpWarehouseInput, 'id'>>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageWarehouses({ size: 100, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue(editing);
    } else {
      form.resetFields();
    }
  }, [open, editing, form]);

  const handleSave = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await saveWarehouse({ ...values, id: editing?.id });
    message.success('保存成功');
    setOpen(false);
    setVersion((value) => value + 1);
  };

  const columns: TableProps<ErpWarehouse>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 120 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '负责人', dataIndex: 'manager', key: 'manager', width: 110 },
    { title: '地址', dataIndex: 'address', key: 'address' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => <StatusTag status={String(value)} />,
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        hasPermission('sys:erp:warehouse:edit') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              setOpen(true);
            }}
          >
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Input.Search
          allowClear
          placeholder="按编码或名称搜索"
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:erp:warehouse:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增仓库
          </Button>
        ) : null}
      </Space>
      <Table<ErpWarehouse> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={open}
        title={editing ? '编辑仓库' : '新增仓库'}
        onOk={handleSave}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ status: 'ENABLED' }}>
          <Form.Item name="code" label="仓库编码" rules={[{ required: true, message: '请输入仓库编码' }]}>
            <Input placeholder="如 WH-001" maxLength={32} />
          </Form.Item>
          <Form.Item name="name" label="仓库名称" rules={[{ required: true, message: '请输入仓库名称' }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="manager" label="负责人">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="address" label="地址">
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '禁用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 主数据页 */
export default function MasterDataPage() {
  const items: TabsProps['items'] = [
    { key: 'account', label: '会计科目', children: <AccountTab /> },
    { key: 'period', label: '会计期间', children: <PeriodTab /> },
    { key: 'customer', label: '客户', children: <CustomerTab /> },
    { key: 'warehouse', label: '仓库', children: <WarehouseTab /> },
  ];

  return <Tabs items={items} />;
}

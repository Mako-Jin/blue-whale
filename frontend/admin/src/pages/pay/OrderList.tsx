// 支付单管理页 - 列表（分页 / 新增 / 支付 / 取消 / 退款）+ 详情抽屉（流水 / 对账）
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏
// 权限码：sys:pay:list(70) / add(71) / pay(72) / cancel(73) / refund(244) / reconcile(245)

import {
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
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
  cancelPayOrder,
  createPayOrder,
  listPayTransactions,
  pagePayOrders,
  payPayOrder,
  reconcilePayOrder,
  refundPayOrder,
  type PayOrderInput,
  type PayOrderStatus,
  type PayOrderVO,
  type PayReconcileVO,
  type PayTransactionVO,
} from '../../api/pay';

interface OrderFormValues {
  orderNo: string;
  businessKey?: string;
  amount: number;
  currency: string;
  channel?: string;
  remark?: string;
}

type OrderQuery = { keyword?: string; status?: string; channel?: string };

const STATUS_OPTIONS = [
  { value: 'CREATED', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' },
];

const CHANNEL_OPTIONS = [
  { value: 'ALIPAY', label: '支付宝' },
  { value: 'WECHAT', label: '微信支付' },
  { value: 'BALANCE', label: '余额' },
];

/** 状态 → 标签颜色/文案 */
function renderStatus(status: PayOrderStatus | undefined) {
  const map: Record<string, { color: string; label: string }> = {
    CREATED: { color: 'gold', label: '待支付' },
    PAID: { color: 'green', label: '已支付' },
    CANCELLED: { color: 'default', label: '已取消' },
    REFUNDED: { color: 'purple', label: '已退款' },
  };
  if (!status) return '-';
  const item = map[status] ?? { color: 'default', label: status };
  return <Tag color={item.color}>{item.label}</Tag>;
}

function renderTxStatus(status: string | undefined, errorMessage?: string) {
  if (status === 'SUCCESS') {
    return <Tag color="success">成功</Tag>;
  }
  if (status === 'FAILED') {
    return (
      <Tag color="error" title={errorMessage}>
        失败
      </Tag>
    );
  }
  return <Tag>{status ?? '-'}</Tag>;
}

export default function OrderList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [channel, setChannel] = useState<string | undefined>(undefined);

  const [creatorOpen, setCreatorOpen] = useState(false);
  const [creatorForm] = Form.useForm<OrderFormValues>();

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRecord, setDetailRecord] = useState<PayOrderVO | null>(null);
  const [transactions, setTransactions] = useState<PayTransactionVO[]>([]);
  const [txLoading, setTxLoading] = useState(false);
  const [reconcile, setReconcile] = useState<PayReconcileVO | null>(null);
  const [reconcileLoading, setReconcileLoading] = useState(false);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const refreshList = () => tableRef.current?.reload();

  const loadTransactions = (order: PayOrderVO) => {
    setTxLoading(true);
    listPayTransactions(order.id)
      .then(setTransactions)
      .catch((error) => showError(error, '流水加载失败'))
      .finally(() => setTxLoading(false));
  };

  const openDetail = (record: PayOrderVO) => {
    setDetailRecord(record);
    setTransactions([]);
    setReconcile(null);
    setDetailOpen(true);
    loadTransactions(record);
  };

  const handlePay = async (row: PayOrderVO) => {
    try {
      await payPayOrder(row.id);
      message.success('支付成功');
      refreshList();
    } catch (error) {
      showError(error, '支付失败');
    }
  };

  const handleCancel = async (row: PayOrderVO) => {
    try {
      await cancelPayOrder(row.id);
      message.success('支付单已取消');
      refreshList();
    } catch (error) {
      showError(error, '取消失败');
    }
  };

  const handleRefund = async (row: PayOrderVO) => {
    try {
      await refundPayOrder(row.id);
      message.success('退款成功');
      refreshList();
    } catch (error) {
      showError(error, '退款失败');
    }
  };

  const handleReconcile = () => {
    if (!detailRecord) {
      return;
    }
    setReconcileLoading(true);
    reconcilePayOrder(detailRecord.id)
      .then(setReconcile)
      .catch((error) => showError(error, '对账失败'))
      .finally(() => setReconcileLoading(false));
  };

  const handleCreate = async (values: OrderFormValues) => {
    const payload: PayOrderInput = {
      orderNo: values.orderNo,
      businessKey: values.businessKey || undefined,
      amount: values.amount.toFixed(2),
      currency: values.currency,
      channel: values.channel || undefined,
      remark: values.remark || undefined,
    };
    try {
      await createPayOrder(payload);
      message.success('支付单已创建');
      setCreatorOpen(false);
      refreshList();
    } catch (error) {
      showError(error, '创建失败');
      throw error;
    }
  };

  const columns: TableProps<PayOrderVO>['columns'] = [
    { title: '支付单号', dataIndex: 'orderNo', width: 200 },
    {
      title: '业务标识',
      dataIndex: 'businessKey',
      width: 160,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: '金额', dataIndex: 'amount', width: 110, align: 'right' },
    { title: '币种', dataIndex: 'currency', width: 80 },
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: PayOrderStatus | undefined) => renderStatus(value),
    },
    {
      title: '支付时间',
      dataIndex: 'payTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '已退款',
      dataIndex: 'refundAmount',
      width: 110,
      align: 'right',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openDetail(row)}>
            详情
          </Button>
          {row.status === 'CREATED' && has('sys:pay:pay') && (
            <Popconfirm title="确认对该支付单执行支付？" onConfirm={() => void handlePay(row)}>
              <Button type="link" size="small">
                支付
              </Button>
            </Popconfirm>
          )}
          {row.status === 'CREATED' && has('sys:pay:cancel') && (
            <Popconfirm title="确认取消该支付单？" onConfirm={() => void handleCancel(row)}>
              <Button type="link" size="small">
                取消
              </Button>
            </Popconfirm>
          )}
          {row.status === 'PAID' && has('sys:pay:refund') && (
            <Popconfirm
              title="确认退款？"
              description="当前为全额退款，退款后状态变为已退款"
              onConfirm={() => void handleRefund(row)}
            >
              <Button type="link" size="small" danger>
                退款
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const txColumns: TableProps<PayTransactionVO>['columns'] = [
    { title: '流水ID', dataIndex: 'id', width: 90 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (value: string | undefined) =>
        value === 'PAY' ? <Tag color="blue">支付</Tag> : value === 'REFUND' ? <Tag color="purple">退款</Tag> : (value ?? '-'),
    },
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: '金额', dataIndex: 'amount', width: 110, align: 'right' },
    { title: '币种', dataIndex: 'currency', width: 80 },
    {
      title: '渠道流水号',
      dataIndex: 'providerTradeNo',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string | undefined, row) => renderTxStatus(value, row.errorMessage),
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
  ];

  return (
    <>
      <ProTable<PayOrderVO, OrderQuery>
        ref={tableRef}
        rowKey="id"
        title="支付单"
        columns={columns}
        request={pagePayOrders}
        params={{ keyword, status, channel }}
        onError={(error) => showError(error, '支付单列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="支付单号 / 业务标识" />,
              },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select allowClear options={STATUS_OPTIONS} placeholder="全部" style={{ width: 140 }} />
                ),
              },
              {
                name: 'channel',
                label: '渠道',
                element: (
                  <Select allowClear options={CHANNEL_OPTIONS} placeholder="全部" style={{ width: 140 }} />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setStatus((values.status as string) || undefined);
              setChannel((values.channel as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setStatus(undefined);
              setChannel(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:pay:add" type="primary" onClick={() => setCreatorOpen(true)}>
            新增支付单
          </AuthButton>
        }
      />

      <FormModal<OrderFormValues>
        open={creatorOpen}
        title="新增支付单"
        form={creatorForm}
        initialValues={{ currency: 'CNY', channel: 'ALIPAY' }}
        onOk={handleCreate}
        onCancel={() => setCreatorOpen(false)}
      >
        <Form.Item
          name="orderNo"
          label="支付单号"
          rules={[{ required: true, message: '请输入支付单号' }]}
          extra="业务唯一，创建后不可修改"
        >
          <Input placeholder="如 PAY20261028001" />
        </Form.Item>
        <Form.Item name="businessKey" label="业务标识">
          <Input placeholder="外部订单号 / 业务键" />
        </Form.Item>
        <Form.Item
          name="amount"
          label="支付金额"
          rules={[{ required: true, message: '请输入支付金额' }]}
        >
          <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="currency"
          label="币种"
          rules={[{ required: true, message: '请输入币种' }]}
        >
          <Input placeholder="默认 CNY" />
        </Form.Item>
        <Form.Item name="channel" label="支付渠道">
          <Select allowClear options={CHANNEL_OPTIONS} placeholder="默认 ALIPAY" />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} />
        </Form.Item>
      </FormModal>

      <Drawer
        title={detailRecord ? `支付单详情：${detailRecord.orderNo}` : '支付单详情'}
        width={720}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {detailRecord && (
          <>
            <Descriptions
              size="small"
              column={2}
              bordered
              items={[
                { key: 'orderNo', label: '支付单号', children: detailRecord.orderNo },
                {
                  key: 'businessKey',
                  label: '业务标识',
                  children: detailRecord.businessKey ?? '-',
                },
                {
                  key: 'amount',
                  label: '金额',
                  children: `${detailRecord.amount} ${detailRecord.currency}`,
                },
                { key: 'status', label: '状态', children: renderStatus(detailRecord.status) },
                {
                  key: 'channel',
                  label: '渠道',
                  children: detailRecord.channel ?? '-',
                },
                {
                  key: 'channelTradeNo',
                  label: '渠道流水号',
                  children: detailRecord.channelTradeNo ?? '-',
                },
                { key: 'payTime', label: '支付时间', children: detailRecord.payTime ?? '-' },
                {
                  key: 'refundAmount',
                  label: '已退款',
                  children: detailRecord.refundAmount ?? '-',
                },
                { key: 'refundTime', label: '退款时间', children: detailRecord.refundTime ?? '-' },
                { key: 'createTime', label: '创建时间', children: detailRecord.createTime ?? '-' },
                {
                  key: 'remark',
                  label: '备注',
                  children: detailRecord.remark ?? '-',
                  span: 2,
                },
              ]}
            />
            <Tabs
              style={{ marginTop: 16 }}
              items={[
                {
                  key: 'transactions',
                  label: '支付/退款流水',
                  children: (
                    <Table<PayTransactionVO>
                      rowKey="id"
                      size="small"
                      columns={txColumns}
                      dataSource={transactions}
                      loading={txLoading}
                      pagination={false}
                    />
                  ),
                },
                {
                  key: 'reconcile',
                  label: '对账',
                  children: (
                    <Space direction="vertical" style={{ width: '100%' }}>
                      {has('sys:pay:reconcile') && (
                        <Button type="primary" loading={reconcileLoading} onClick={handleReconcile}>
                          发起对账
                        </Button>
                      )}
                      {reconcile && (
                        <Descriptions
                          size="small"
                          column={1}
                          bordered
                          items={[
                            { key: 'local', label: '本地状态', children: reconcile.localStatus },
                            {
                              key: 'channel',
                              label: '渠道状态',
                              children: reconcile.channelStatus,
                            },
                            {
                              key: 'consistent',
                              label: '一致性',
                              children: reconcile.consistent ? (
                                <Tag color="success">一致</Tag>
                              ) : (
                                <Tag color="error">不一致</Tag>
                              ),
                            },
                            {
                              key: 'providerTradeNo',
                              label: '渠道流水号',
                              children: reconcile.providerTradeNo ?? '-',
                            },
                            {
                              key: 'checkedAt',
                              label: '对账时间',
                              children: reconcile.checkedAt ?? '-',
                            },
                          ]}
                        />
                      )}
                    </Space>
                  ),
                },
              ]}
            />
          </>
        )}
      </Drawer>
    </>
  );
}

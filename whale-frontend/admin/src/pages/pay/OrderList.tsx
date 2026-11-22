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
import { useI18n } from '../../i18n';

interface OrderFormValues {
  orderNo: string;
  businessKey?: string;
  amount: number;
  currency: string;
  channel?: string;
  remark?: string;
}

type OrderQuery = { keyword?: string; status?: string; channel?: string };

export default function OrderList() {
  const { message } = App.useApp();
  const { t } = useI18n();
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

  const statusOptions = [
    { value: 'CREATED', label: t('pay.status.created') },
    { value: 'PAID', label: t('pay.status.paid') },
    { value: 'CANCELLED', label: t('pay.status.cancelled') },
    { value: 'REFUNDED', label: t('pay.status.refunded') },
  ];

  const channelOptions = [
    { value: 'ALIPAY', label: t('pay.channel.alipay') },
    { value: 'WECHAT', label: t('pay.channel.wechat') },
    { value: 'BALANCE', label: t('pay.channel.balance') },
  ];

  /** 状态 → 标签颜色/文案 */
  const renderStatus = (value: PayOrderStatus | undefined) => {
    const map: Record<string, { color: string; label: string }> = {
      CREATED: { color: 'gold', label: t('pay.status.created') },
      PAID: { color: 'green', label: t('pay.status.paid') },
      CANCELLED: { color: 'default', label: t('pay.status.cancelled') },
      REFUNDED: { color: 'purple', label: t('pay.status.refunded') },
    };
    if (!value) return '-';
    const item = map[value] ?? { color: 'default', label: value };
    return <Tag color={item.color}>{item.label}</Tag>;
  };

  const renderTxStatus = (value: string | undefined, errorMessage?: string) => {
    if (value === 'SUCCESS') {
      return <Tag color="success">{t('pay.txStatus.success')}</Tag>;
    }
    if (value === 'FAILED') {
      return (
        <Tag color="error" title={errorMessage}>
          {t('pay.txStatus.failed')}
        </Tag>
      );
    }
    return <Tag>{value ?? '-'}</Tag>;
  };

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const refreshList = () => tableRef.current?.reload();

  const loadTransactions = (order: PayOrderVO) => {
    setTxLoading(true);
    listPayTransactions(order.id)
      .then(setTransactions)
      .catch((error) => showError(error, t('pay.msg.transactionsLoadFailed')))
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
      message.success(t('pay.msg.paid'));
      refreshList();
    } catch (error) {
      showError(error, t('pay.msg.payFailed'));
    }
  };

  const handleCancel = async (row: PayOrderVO) => {
    try {
      await cancelPayOrder(row.id);
      message.success(t('pay.msg.cancelled'));
      refreshList();
    } catch (error) {
      showError(error, t('pay.msg.cancelFailed'));
    }
  };

  const handleRefund = async (row: PayOrderVO) => {
    try {
      await refundPayOrder(row.id);
      message.success(t('pay.msg.refunded'));
      refreshList();
    } catch (error) {
      showError(error, t('pay.msg.refundFailed'));
    }
  };

  const handleReconcile = () => {
    if (!detailRecord) {
      return;
    }
    setReconcileLoading(true);
    reconcilePayOrder(detailRecord.id)
      .then(setReconcile)
      .catch((error) => showError(error, t('pay.msg.reconcileFailed')))
      .finally(() => setReconcileLoading(false));
  };

  const handleCreate = async (values: OrderFormValues) => {
    const payload: PayOrderInput = {
      orderNo: values.orderNo,
      businessKey: values.businessKey || undefined,
      amount: values.amount,
      currency: values.currency,
      channel: values.channel || undefined,
      remark: values.remark || undefined,
    };
    try {
      await createPayOrder(payload);
      message.success(t('pay.msg.created'));
      setCreatorOpen(false);
      refreshList();
    } catch (error) {
      showError(error, t('pay.msg.createFailed'));
      throw error;
    }
  };

  const columns: TableProps<PayOrderVO>['columns'] = [
    { title: t('pay.col.orderNo'), dataIndex: 'orderNo', width: 200 },
    {
      title: t('pay.col.businessKey'),
      dataIndex: 'businessKey',
      width: 160,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: t('pay.col.amount'), dataIndex: 'amount', width: 110, align: 'right' },
    { title: t('pay.col.currency'), dataIndex: 'currency', width: 80 },
    {
      title: t('pay.col.channel'),
      dataIndex: 'channel',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('pay.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: PayOrderStatus | undefined) => renderStatus(value),
    },
    {
      title: t('pay.col.payTime'),
      dataIndex: 'payTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('pay.col.refundAmount'),
      dataIndex: 'refundAmount',
      width: 110,
      align: 'right',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('pay.col.createTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('pay.col.action'),
      key: 'action',
      width: 260,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openDetail(row)}>
            {t('pay.action.detail')}
          </Button>
          {row.status === 'CREATED' && has('sys:pay:pay') && (
            <Popconfirm title={t('pay.confirm.pay')} onConfirm={() => void handlePay(row)}>
              <Button type="link" size="small">
                {t('pay.action.pay')}
              </Button>
            </Popconfirm>
          )}
          {row.status === 'CREATED' && has('sys:pay:cancel') && (
            <Popconfirm title={t('pay.confirm.cancel')} onConfirm={() => void handleCancel(row)}>
              <Button type="link" size="small">
                {t('pay.action.cancel')}
              </Button>
            </Popconfirm>
          )}
          {row.status === 'PAID' && has('sys:pay:refund') && (
            <Popconfirm
              title={t('pay.confirm.refund')}
              description={t('pay.confirm.refundDesc')}
              onConfirm={() => void handleRefund(row)}
            >
              <Button type="link" size="small" danger>
                {t('pay.action.refund')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const txColumns: TableProps<PayTransactionVO>['columns'] = [
    { title: t('pay.tx.col.id'), dataIndex: 'id', width: 90 },
    {
      title: t('pay.tx.col.type'),
      dataIndex: 'type',
      width: 90,
      render: (value: string | undefined) =>
        value === 'PAY' ? (
          <Tag color="blue">{t('pay.tx.type.pay')}</Tag>
        ) : value === 'REFUND' ? (
          <Tag color="purple">{t('pay.tx.type.refund')}</Tag>
        ) : (
          (value ?? '-')
        ),
    },
    {
      title: t('pay.tx.col.channel'),
      dataIndex: 'channel',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: t('pay.tx.col.amount'), dataIndex: 'amount', width: 110, align: 'right' },
    { title: t('pay.tx.col.currency'), dataIndex: 'currency', width: 80 },
    {
      title: t('pay.tx.col.providerTradeNo'),
      dataIndex: 'providerTradeNo',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('pay.tx.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: string | undefined, row) => renderTxStatus(value, row.errorMessage),
    },
    {
      title: t('pay.tx.col.time'),
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
        title={t('pay.list.title')}
        columns={columns}
        request={pagePayOrders}
        params={{ keyword, status, channel }}
        onError={(error) => showError(error, t('pay.msg.listLoadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('pay.search.keyword'),
                element: <Input allowClear placeholder={t('pay.search.keywordPlaceholder')} />,
              },
              {
                name: 'status',
                label: t('pay.search.status'),
                element: (
                  <Select
                    allowClear
                    options={statusOptions}
                    placeholder={t('pay.search.all')}
                    style={{ width: 140 }}
                  />
                ),
              },
              {
                name: 'channel',
                label: t('pay.search.channel'),
                element: (
                  <Select
                    allowClear
                    options={channelOptions}
                    placeholder={t('pay.search.all')}
                    style={{ width: 140 }}
                  />
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
            {t('pay.action.create')}
          </AuthButton>
        }
      />

      <FormModal<OrderFormValues>
        open={creatorOpen}
        title={t('pay.modal.createTitle')}
        form={creatorForm}
        initialValues={{ currency: 'CNY', channel: 'ALIPAY' }}
        onOk={handleCreate}
        onCancel={() => setCreatorOpen(false)}
      >
        <Form.Item
          name="orderNo"
          label={t('pay.field.orderNo')}
          rules={[{ required: true, message: t('pay.field.orderNoRequired') }]}
          extra={t('pay.field.orderNoExtra')}
        >
          <Input placeholder={t('pay.field.orderNoPlaceholder')} />
        </Form.Item>
        <Form.Item name="businessKey" label={t('pay.field.businessKey')}>
          <Input placeholder={t('pay.field.businessKeyPlaceholder')} />
        </Form.Item>
        <Form.Item
          name="amount"
          label={t('pay.field.amount')}
          rules={[{ required: true, message: t('pay.field.amountRequired') }]}
        >
          <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="currency"
          label={t('pay.field.currency')}
          rules={[{ required: true, message: t('pay.field.currencyRequired') }]}
        >
          <Input placeholder={t('pay.field.currencyPlaceholder')} />
        </Form.Item>
        <Form.Item name="channel" label={t('pay.field.channel')}>
          <Select
            allowClear
            options={channelOptions}
            placeholder={t('pay.field.channelPlaceholder')}
          />
        </Form.Item>
        <Form.Item name="remark" label={t('pay.field.remark')}>
          <Input.TextArea rows={2} />
        </Form.Item>
      </FormModal>

      <Drawer
        title={
          detailRecord
            ? t('pay.detail.titleWithNo', { orderNo: detailRecord.orderNo })
            : t('pay.detail.title')
        }
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
                {
                  key: 'orderNo',
                  label: t('pay.col.orderNo'),
                  children: detailRecord.orderNo,
                },
                {
                  key: 'businessKey',
                  label: t('pay.col.businessKey'),
                  children: detailRecord.businessKey ?? '-',
                },
                {
                  key: 'amount',
                  label: t('pay.detail.amount'),
                  children: `${detailRecord.amount} ${detailRecord.currency}`,
                },
                {
                  key: 'status',
                  label: t('pay.col.status'),
                  children: renderStatus(detailRecord.status),
                },
                {
                  key: 'channel',
                  label: t('pay.col.channel'),
                  children: detailRecord.channel ?? '-',
                },
                {
                  key: 'channelTradeNo',
                  label: t('pay.detail.channelTradeNo'),
                  children: detailRecord.channelTradeNo ?? '-',
                },
                {
                  key: 'payTime',
                  label: t('pay.detail.payTime'),
                  children: detailRecord.payTime ?? '-',
                },
                {
                  key: 'refundAmount',
                  label: t('pay.detail.refundAmount'),
                  children: detailRecord.refundAmount ?? '-',
                },
                {
                  key: 'refundTime',
                  label: t('pay.detail.refundTime'),
                  children: detailRecord.refundTime ?? '-',
                },
                {
                  key: 'createTime',
                  label: t('pay.detail.createTime'),
                  children: detailRecord.createTime ?? '-',
                },
                {
                  key: 'remark',
                  label: t('pay.detail.remark'),
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
                  label: t('pay.tab.transactions'),
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
                  label: t('pay.tab.reconcile'),
                  children: (
                    <Space direction="vertical" style={{ width: '100%' }}>
                      {has('sys:pay:reconcile') && (
                        <Button type="primary" loading={reconcileLoading} onClick={handleReconcile}>
                          {t('pay.action.reconcile')}
                        </Button>
                      )}
                      {reconcile && (
                        <Descriptions
                          size="small"
                          column={1}
                          bordered
                          items={[
                            {
                              key: 'local',
                              label: t('pay.reconcile.localStatus'),
                              children: reconcile.localStatus,
                            },
                            {
                              key: 'channel',
                              label: t('pay.reconcile.channelStatus'),
                              children: reconcile.channelStatus,
                            },
                            {
                              key: 'consistent',
                              label: t('pay.reconcile.consistency'),
                              children: reconcile.consistent ? (
                                <Tag color="success">{t('pay.reconcile.consistent')}</Tag>
                              ) : (
                                <Tag color="error">{t('pay.reconcile.inconsistent')}</Tag>
                              ),
                            },
                            {
                              key: 'providerTradeNo',
                              label: t('pay.reconcile.providerTradeNo'),
                              children: reconcile.providerTradeNo ?? '-',
                            },
                            {
                              key: 'checkedAt',
                              label: t('pay.reconcile.checkedAt'),
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

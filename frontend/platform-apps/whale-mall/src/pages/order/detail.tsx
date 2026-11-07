// 订单详情：单据信息、金额构成、行明细与状态动作
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Input,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import {
  cancelOrder,
  completeOrder,
  getOrder,
  payOrder,
  refundOrder,
  shipOrder,
  type OrderLineView,
  type OrderView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  MODE_LABEL,
  ORDER_STATUS_COLOR,
  ORDER_STATUS_LABEL,
  PRICE_TYPE_LABEL,
  SETTLEMENT_LABEL,
  formatDateTime,
  formatMoney,
  formatSpec,
} from '../../utils/dict';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '加载失败';
}

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<OrderView | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [refundOpen, setRefundOpen] = useState(false);
  const [refundReason, setRefundReason] = useState('');

  const canManage = hasPermission('sys:mall:order:manage');
  const canCancel = hasPermission('sys:mall:order:cancel');
  const canRefund = hasPermission('sys:mall:order:refund');

  /** 加载详情 */
  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setLoading(true);
    try {
      setOrder(await getOrder(Number(id)));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 取消 */
  const submitCancel = async () => {
    if (!order) {
      return;
    }
    setBusy(true);
    try {
      await cancelOrder(order.id, cancelReason.trim() || undefined);
      message.success('订单已取消');
      setCancelOpen(false);
      setCancelReason('');
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 发起支付（MOCK 即时成功，随后 whale-pay 回调推进订单状态） */
  const submitPay = async () => {
    if (!order) {
      return;
    }
    setBusy(true);
    try {
      const payNo = await payOrder(order.id);
      message.success(`已发起支付：${payNo}，支付结果稍后自动刷新`);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 整单退款 */
  const submitRefund = async () => {
    if (!order) {
      return;
    }
    setBusy(true);
    try {
      await refundOrder(order.id, refundReason.trim() || undefined);
      message.success('已发起整单退款，等待支付服务回调确认');
      setRefundOpen(false);
      setRefundReason('');
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 发货 / 完成 */
  const run = async (action: 'ship' | 'complete') => {
    if (!order) {
      return;
    }
    try {
      if (action === 'ship') {
        await shipOrder(order.id);
        message.success('已发货');
      } else {
        await completeOrder(order.id);
        message.success('订单已完成');
      }
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<OrderLineView>['columns'] = [
    { title: '商品', dataIndex: 'spuName' },
    { title: 'SKU', dataIndex: 'skuCode', width: 180 },
    {
      title: '规格',
      dataIndex: 'specJson',
      width: 160,
      render: (value?: string) => formatSpec(value),
    },
    {
      title: '价目',
      dataIndex: 'priceType',
      width: 110,
      render: (value: string) => PRICE_TYPE_LABEL[value] ?? value,
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      width: 140,
      render: (value: number, row) =>
        formatMoney(value, row.priceType === 'POINTS' ? 'POINTS' : 'CASH'),
    },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    {
      title: '小计',
      dataIndex: 'amount',
      width: 140,
      render: (value: number, row) =>
        formatMoney(value, row.priceType === 'POINTS' ? 'POINTS' : 'CASH'),
    },
  ];

  return (
    <Card
      title={`订单详情${order ? ` · ${order.orderNo}` : ''}`}
      extra={
        <Space>
          <Button onClick={() => navigate('/order')}>返回列表</Button>
          <Button onClick={() => void load()}>刷新</Button>
          {order?.status === 'PENDING_PAYMENT' && (
            <Popconfirm title="确认发起支付？" onConfirm={() => void submitPay()}>
              <Button type="primary">去支付</Button>
            </Popconfirm>
          )}
          {canRefund && order?.status === 'PAID' && (
            <Button danger onClick={() => setRefundOpen(true)}>
              整单退款
            </Button>
          )}
          {canCancel && order?.status === 'PENDING_PAYMENT' && (
            <Button danger onClick={() => setCancelOpen(true)}>
              取消订单
            </Button>
          )}
          {canManage && order?.status === 'PAID' && (
            <Popconfirm title="确认发货？" onConfirm={() => run('ship')}>
              <Button type="primary">发货</Button>
            </Popconfirm>
          )}
          {order?.status === 'SHIPPED' && (
            <Popconfirm title="确认收货？" onConfirm={() => run('complete')}>
              <Button type="primary">确认收货</Button>
            </Popconfirm>
          )}
        </Space>
      }
    >
      <Spin spinning={loading}>
        {order ? (
          <>
            <Descriptions column={3} size="small" bordered>
              <Descriptions.Item label="订单号">{order.orderNo}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={ORDER_STATUS_COLOR[order.status]}>
                  {ORDER_STATUS_LABEL[order.status] ?? order.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="应付金额">
                <Typography.Text strong>
                  {formatMoney(order.totalAmount, order.currencyType)}
                </Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="渠道">
                {order.channelCode || order.channelId}
              </Descriptions.Item>
              <Descriptions.Item label="形态">
                {MODE_LABEL[order.mode] ?? order.mode}
              </Descriptions.Item>
              <Descriptions.Item label="结算方式">
                {SETTLEMENT_LABEL[order.settlementType] ?? order.settlementType}
              </Descriptions.Item>
              <Descriptions.Item label="买家">{order.buyerName || order.buyerUserId}</Descriptions.Item>
              <Descriptions.Item label="件数">{order.totalQuantity}</Descriptions.Item>
              <Descriptions.Item label="支付单号">{order.payNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="下单时间">{formatDateTime(order.createTime)}</Descriptions.Item>
              <Descriptions.Item label="支付时间">{formatDateTime(order.payTime)}</Descriptions.Item>
              <Descriptions.Item label="发货时间">{formatDateTime(order.shipTime)}</Descriptions.Item>
              <Descriptions.Item label="完成时间">
                {formatDateTime(order.completeTime)}
              </Descriptions.Item>
              <Descriptions.Item label="取消时间">
                {formatDateTime(order.cancelTime)}
              </Descriptions.Item>
              <Descriptions.Item label="取消原因">{order.cancelReason || '-'}</Descriptions.Item>
              <Descriptions.Item label="备注" span={3}>
                {order.remark || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Typography.Title level={5} style={{ marginTop: 20 }}>
              商品明细
            </Typography.Title>
            <Table<OrderLineView>
              rowKey={(row) => `${row.skuId}-${row.quantity}`}
              size="small"
              columns={columns}
              dataSource={order.items ?? []}
              pagination={false}
            />
          </>
        ) : (
          <Typography.Text type="secondary">暂无数据</Typography.Text>
        )}
      </Spin>

      <Modal
        title="取消订单"
        open={cancelOpen}
        confirmLoading={busy}
        onCancel={() => setCancelOpen(false)}
        onOk={submitCancel}
      >
        <Typography.Paragraph type="secondary">
          只有待支付订单可以取消；已支付订单请走整单退款（运营权限）。
        </Typography.Paragraph>
        <Input.TextArea
          rows={2}
          maxLength={255}
          placeholder="取消原因（可选）"
          value={cancelReason}
          onChange={(event) => setCancelReason(event.target.value)}
        />
      </Modal>

      <Modal
        title="整单退款"
        open={refundOpen}
        confirmLoading={busy}
        onCancel={() => setRefundOpen(false)}
        onOk={submitRefund}
      >
        <Typography.Paragraph type="secondary">
          订单将进入「退款中」，退款成功由支付服务回调确认；退款中不可发货、不可再次退款。
        </Typography.Paragraph>
        <Input.TextArea
          rows={2}
          maxLength={255}
          placeholder="退款原因（可选）"
          value={refundReason}
          onChange={(event) => setRefundReason(event.target.value)}
        />
      </Modal>
    </Card>
  );
}

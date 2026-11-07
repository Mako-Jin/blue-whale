// 订单详情：单据信息、金额构成、行明细与状态动作
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
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
import type { TableProps } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import {
  applyAfterSale,
  cancelOrder,
  completeOrder,
  getOrder,
  payOrder,
  refundOrder,
  createShipment,
  listShipmentsByOrder,
  type OrderLineView,
  type OrderView,
  type ShipmentView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  AFTER_SALE_REASON_OPTIONS,
  AFTER_SALE_TYPE_OPTIONS,
  CARRIER_OPTIONS,
  MODE_LABEL,
  ORDER_STATUS_COLOR,
  ORDER_STATUS_LABEL,
  PRICE_TYPE_LABEL,
  SETTLEMENT_LABEL,
  SHIPMENT_STATUS_COLOR,
  SHIPMENT_STATUS_LABEL,
  formatDateTime,
  formatShipProgress,
  formatMoney,
  formatSpec,
} from '../../utils/dict';

/** 可发起售后的订单状态（已收款才谈得上退款） */
const AFTER_SALE_ALLOWED_STATUS = ['PAID', 'SHIPPED', 'COMPLETED'];

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '加载失败';
}

/**
 * 本次退数量对应的可退金额（与后端同一口径）。
 *
 * <p>基数用行实付额（小计 − 分摊优惠 + 分摊运费）而不是快照价：
 * 一张 8 折券的订单按原价退，等于把两成优惠折现送给买家。</p>
 *
 * <p>分次退同一行时最后一次取剩余，不再按比例算一遍——
 * 逐次四舍五入的合计常常差一分钱，对账时这一分钱最难查。前端拿不到
 * 已退金额，这里按「实付 × 已退占比」估算，最终数字以售后单为准。</p>
 *
 * @param row 订单行
 * @param quantity 本次退数量
 * @returns 预估可退金额
 */
function refundableShare(row: OrderLineView, quantity: number): number {
  const total = row.quantity ?? 0;
  if (total <= 0 || quantity <= 0) {
    return 0;
  }
  const net = row.netAmount ?? row.amount ?? 0;
  const refunded = row.refundedQuantity ?? 0;
  const raw =
    refunded + quantity >= total ? Math.max(0, net - (net * refunded) / total) : (net * quantity) / total;
  return Math.round(raw * 100) / 100;
}

/**
 * 行实付额（老订单没有分摊字段时回落到小计）。
 *
 * @param row 订单行
 * @returns 实付金额
 */
function netOf(row: OrderLineView): number {
  return row.netAmount ?? row.amount ?? 0;
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
  const [afterSaleOpen, setAfterSaleOpen] = useState(false);
  const [afterSaleType, setAfterSaleType] = useState('REFUND_ONLY');
  const [afterSaleReason, setAfterSaleReason] = useState('质量问题');
  const [afterSaleDescription, setAfterSaleDescription] = useState('');
  const [afterSaleQty, setAfterSaleQty] = useState<Record<number, number>>({});
  const [shipments, setShipments] = useState<ShipmentView[]>([]);
  const [shipOpen, setShipOpen] = useState(false);
  const [carrierCode, setCarrierCode] = useState('SF');
  const [carrierName, setCarrierName] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const [shipRemark, setShipRemark] = useState('');
  const [shipQty, setShipQty] = useState<Record<number, number>>({});

  const canManage = hasPermission('sys:mall:order:manage');
  const canCancel = hasPermission('sys:mall:order:cancel');
  const canRefund = hasPermission('sys:mall:order:refund');
  const canApplyAfterSale = hasPermission('sys:mall:aftersale:apply');

  /** 还有可发数量、且订单处于已支付/已发货时才允许继续发货（拆包） */
  const canShipNow =
    (order?.status === 'PAID' || order?.status === 'SHIPPED') &&
    (order?.items ?? []).some((row) => (row.shippableQuantity ?? 0) > 0);

  /** 加载详情 */
  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setLoading(true);
    try {
      const data = await getOrder(Number(id));
      setOrder(data);
      // 发货单单独查：列表视图不带明细，详情页要看到每个包裹发了哪几件
      setShipments(await listShipmentsByOrder(data.orderNo).catch(() => []));
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

  /**
   * 打开售后弹窗：默认按每行可退数量预填。
   *
   * <p>预填成 0 会让用户每次都要手动选，预填满额又容易误退整单，
   * 折中为「预填 0、点行才计数」，提交时只收大于 0 的行。</p>
   */
  const openAfterSale = () => {
    setAfterSaleQty({});
    setAfterSaleType('REFUND_ONLY');
    setAfterSaleReason('质量问题');
    setAfterSaleDescription('');
    setAfterSaleOpen(true);
  };

  /** 提交售后申请（金额由后端按行快照价计算） */
  const submitAfterSale = async () => {
    if (!order) {
      return;
    }
    const lines = (order.items ?? [])
      .filter((row) => typeof row.id === 'number' && (afterSaleQty[row.id] ?? 0) > 0)
      .map((row) => ({ orderItemId: row.id as number, quantity: afterSaleQty[row.id as number] }));
    if (lines.length === 0) {
      message.warning('请填写要退的商品数量');
      return;
    }
    setBusy(true);
    try {
      await applyAfterSale({
        orderId: order.id,
        type: afterSaleType,
        reason: afterSaleReason,
        description: afterSaleDescription.trim() || undefined,
        lines,
      });
      message.success('售后申请已提交，等待运营审核');
      setAfterSaleOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 本次申请退款金额预估（与后端同一算法：行实付额 × 数量占比） */
  const afterSalePreview = (order?.items ?? []).reduce((sum, row) => {
    const qty = typeof row.id === 'number' ? afterSaleQty[row.id] ?? 0 : 0;
    return sum + refundableShare(row, qty);
  }, 0);

  /** 确认收货（买家动作；发货由发货单承载） */
  const runComplete = async () => {
    if (!order) {
      return;
    }
    try {
      await completeOrder(order.id);
      message.success('订单已完成');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 打开发货弹窗：默认按每行可发数量预填，运营只改要拆出去的 */
  const openShip = () => {
    const preset: Record<number, number> = {};
    (order?.items ?? []).forEach((row) => {
      if (typeof row.id === 'number') {
        preset[row.id] = row.shippableQuantity ?? 0;
      }
    });
    setShipQty(preset);
    setCarrierCode('SF');
    setCarrierName('');
    setTrackingNo('');
    setShipRemark('');
    setShipOpen(true);
  };

  /** 提交发货：一次发货对应一个运单号，一个订单可发多次 */
  const submitShip = async () => {
    if (!order) {
      return;
    }
    const items = (order.items ?? [])
      .filter((row) => typeof row.id === 'number' && (shipQty[row.id] ?? 0) > 0)
      .map((row) => ({ orderItemId: row.id as number, quantity: shipQty[row.id as number] }));
    if (items.length === 0) {
      message.warning('请填写本次要发的数量');
      return;
    }
    if (!trackingNo.trim()) {
      message.warning('请填写运单号');
      return;
    }
    setBusy(true);
    try {
      await createShipment({
        orderId: order.id,
        carrierCode,
        carrierName: carrierName.trim() || undefined,
        trackingNo: trackingNo.trim(),
        remark: shipRemark.trim() || undefined,
        items,
      });
      message.success('已发货');
      setShipOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
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
      title: '已发',
      dataIndex: 'shippedQuantity',
      width: 80,
      render: (value?: number) => value ?? 0,
    },
    {
      title: '可发',
      dataIndex: 'shippableQuantity',
      width: 80,
      render: (value: number | undefined, row) => value ?? row.quantity,
    },
    {
      title: '已退',
      dataIndex: 'refundedQuantity',
      width: 80,
      render: (value?: number) => value ?? 0,
    },
    {
      title: '可退',
      dataIndex: 'refundableQuantity',
      width: 80,
      render: (value: number | undefined, row) =>
        value === undefined ? (row.quantity ?? 0) - (row.refundedQuantity ?? 0) : value,
    },
    {
      title: '小计',
      dataIndex: 'amount',
      width: 140,
      render: (value: number, row) =>
        formatMoney(value, row.priceType === 'POINTS' ? 'POINTS' : 'CASH'),
    },
    {
      title: '实付',
      dataIndex: 'netAmount',
      width: 160,
      render: (_: unknown, row) => {
        const discount = row.shareDiscount ?? 0;
        const freight = row.shareFreight ?? 0;
        return (
          <Space direction="vertical" size={0}>
            <Typography.Text>
              {formatMoney(netOf(row), row.priceType === 'POINTS' ? 'POINTS' : 'CASH')}
            </Typography.Text>
            {discount !== 0 || freight !== 0 ? (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                优惠 -{formatMoney(discount)} / 运费 +{formatMoney(freight)}
              </Typography.Text>
            ) : null}
          </Space>
        );
      },
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
          {canRefund && AFTER_SALE_ALLOWED_STATUS.includes(order?.status ?? '') && (
            <Button danger onClick={() => setRefundOpen(true)}>
              整单退款
            </Button>
          )}
          {canApplyAfterSale && AFTER_SALE_ALLOWED_STATUS.includes(order?.status ?? '') && (
            <Button onClick={openAfterSale}>申请售后</Button>
          )}
          <Button onClick={() => navigate('/aftersale')}>售后单</Button>
          {canCancel && order?.status === 'PENDING_PAYMENT' && (
            <Button danger onClick={() => setCancelOpen(true)}>
              取消订单
            </Button>
          )}
          {canManage && canShipNow && (
            <Button type="primary" onClick={openShip}>
              发货
            </Button>
          )}
          {order?.status === 'SHIPPED' && (
            <Popconfirm title="确认收货？" onConfirm={() => void runComplete()}>
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
              金额构成
            </Typography.Title>
            <Descriptions column={4} size="small" bordered>
              <Descriptions.Item label="商品总额">
                {formatMoney(order.goodsAmount ?? order.totalAmount, order.currencyType)}
              </Descriptions.Item>
              <Descriptions.Item label="优惠">
                {order.discountAmount ? (
                  <Typography.Text type="danger">
                    -{formatMoney(order.discountAmount, order.currencyType)}
                    {order.couponCode ? `（${order.couponCode}）` : ''}
                  </Typography.Text>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="运费">
                {order.freightAmount
                  ? formatMoney(order.freightAmount, order.currencyType)
                  : '免运费'}
              </Descriptions.Item>
              <Descriptions.Item label="应付">
                <Typography.Text strong>
                  {formatMoney(order.totalAmount, order.currencyType)}
                </Typography.Text>
              </Descriptions.Item>
            </Descriptions>

            <Typography.Title level={5} style={{ marginTop: 20 }}>
              物流信息
              <Typography.Text type="secondary" style={{ marginLeft: 12, fontSize: 13 }}>
                {formatShipProgress(order.shippedQuantity, order.totalQuantity)}
              </Typography.Text>
            </Typography.Title>
            <Table<ShipmentView>
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={shipments}
              locale={{
                emptyText: (
                  <Typography.Text type="secondary">
                    还没有发货单；运营点上方「发货」建单，一个订单可拆成多个包裹。
                  </Typography.Text>
                ),
              }}
              columns={[
                { title: '发货单号', dataIndex: 'shipmentNo', width: 190 },
                { title: '承运商', dataIndex: 'carrierName', width: 130 },
                { title: '运单号', dataIndex: 'trackingNo', width: 180 },
                {
                  title: '件数',
                  dataIndex: 'totalQuantity',
                  width: 80,
                  render: (value: number) => `${value} 件`,
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (value: string) => (
                    <Tag color={SHIPMENT_STATUS_COLOR[value]}>
                      {SHIPMENT_STATUS_LABEL[value] ?? value}
                    </Tag>
                  ),
                },
                {
                  title: '发货时间',
                  dataIndex: 'shippedTime',
                  width: 170,
                  render: (value?: string) => formatDateTime(value),
                },
                {
                  title: '签收时间',
                  dataIndex: 'deliveredTime',
                  width: 170,
                  render: (value?: string) => formatDateTime(value),
                },
              ]}
            />

            <Typography.Title level={5} style={{ marginTop: 20 }}>
              商品明细
            </Typography.Title>
            <Table<OrderLineView>
              rowKey={(row) => String(row.id ?? `${row.skuId}-${row.quantity}`)}
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

      <Modal
        title="申请售后"
        open={afterSaleOpen}
        confirmLoading={busy}
        onCancel={() => setAfterSaleOpen(false)}
        onOk={submitAfterSale}
        width={640}
      >
        <Typography.Paragraph type="secondary">
          退款金额由后端按「行实付额」计算（小计减去分摊优惠、加上分摊运费），提交后需运营审核，通过后原路退回。
        </Typography.Paragraph>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space>
            <Typography.Text>售后类型</Typography.Text>
            <Select
              style={{ width: 140 }}
              value={afterSaleType}
              options={AFTER_SALE_TYPE_OPTIONS}
              onChange={setAfterSaleType}
            />
            <Typography.Text>售后原因</Typography.Text>
            <Select
              style={{ width: 180 }}
              value={afterSaleReason}
              options={AFTER_SALE_REASON_OPTIONS}
              onChange={setAfterSaleReason}
            />
          </Space>
          <Input.TextArea
            rows={2}
            maxLength={500}
            placeholder="问题描述（可选）"
            value={afterSaleDescription}
            onChange={(event) => setAfterSaleDescription(event.target.value)}
          />
          <Table<OrderLineView>
            rowKey={(row) => String(row.id ?? `${row.skuId}-${row.quantity}`)}
            size="small"
            pagination={false}
            dataSource={order?.items ?? []}
            columns={[
              { title: '商品', dataIndex: 'spuName' },
              {
                title: '实付金额',
                width: 120,
                render: (_: unknown, row) => formatMoney(netOf(row)),
              },
              {
                title: '可退',
                width: 80,
                render: (_: unknown, row) =>
                  row.refundableQuantity ?? (row.quantity ?? 0) - (row.refundedQuantity ?? 0),
              },
              {
                title: '本次退数量',
                width: 140,
                render: (_: unknown, row) => {
                  const refundable = row.refundableQuantity ?? (row.quantity ?? 0) - (row.refundedQuantity ?? 0);
                  if (typeof row.id !== 'number' || refundable <= 0) {
                    return <Typography.Text type="secondary">不可退</Typography.Text>;
                  }
                  return (
                    <InputNumber
                      min={0}
                      max={refundable}
                      precision={0}
                      value={afterSaleQty[row.id] ?? 0}
                      onChange={(value) =>
                        setAfterSaleQty((prev) => ({ ...prev, [row.id as number]: Number(value ?? 0) }))
                      }
                    />
                  );
                },
              },
              {
                title: '本次退款',
                width: 110,
                render: (_: unknown, row) => {
                  const qty = typeof row.id === 'number' ? afterSaleQty[row.id] ?? 0 : 0;
                  return formatMoney(refundableShare(row, qty));
                },
              },
            ]}
          />
          <Typography.Text>
            预计退款：<Typography.Text strong>{formatMoney(afterSalePreview)}</Typography.Text>
          </Typography.Text>
        </Space>
      </Modal>

      <Modal
        title="发货"
        open={shipOpen}
        width={720}
        confirmLoading={busy}
        okText="确认发货"
        onCancel={() => setShipOpen(false)}
        onOk={submitShip}
      >
        <Typography.Paragraph type="secondary">
          一张发货单对应一个运单号。要拆成两个包裹就发两次；默认按每行可发数量预填，
          只把这次要发的行留下数量即可。
        </Typography.Paragraph>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space wrap>
            <Typography.Text>承运商</Typography.Text>
            <Select
              style={{ width: 160 }}
              value={carrierCode}
              options={CARRIER_OPTIONS}
              onChange={setCarrierCode}
            />
            {carrierCode === 'OTHER' ? (
              <Input
                style={{ width: 180 }}
                maxLength={64}
                placeholder="承运商名称"
                value={carrierName}
                onChange={(event) => setCarrierName(event.target.value)}
              />
            ) : null}
            <Typography.Text>运单号</Typography.Text>
            <Input
              style={{ width: 220 }}
              maxLength={64}
              placeholder="必填；自送也填内部单号"
              value={trackingNo}
              onChange={(event) => setTrackingNo(event.target.value)}
            />
          </Space>
          <Input
            maxLength={255}
            placeholder="备注（可选）"
            value={shipRemark}
            onChange={(event) => setShipRemark(event.target.value)}
          />
          <Table<OrderLineView>
            rowKey={(row) => String(row.id ?? `${row.skuId}-${row.quantity}`)}
            size="small"
            pagination={false}
            dataSource={order?.items ?? []}
            columns={[
              { title: '商品', dataIndex: 'spuName' },
              {
                title: '已发',
                dataIndex: 'shippedQuantity',
                width: 80,
                render: (value?: number) => value ?? 0,
              },
              {
                title: '可发',
                width: 80,
                render: (_: unknown, row) => row.shippableQuantity ?? row.quantity,
              },
              {
                title: '本次发货',
                width: 140,
                render: (_: unknown, row) => {
                  const shippable = row.shippableQuantity ?? row.quantity;
                  if (typeof row.id !== 'number' || shippable <= 0) {
                    return <Typography.Text type="secondary">不可发</Typography.Text>;
                  }
                  return (
                    <InputNumber
                      min={0}
                      max={shippable}
                      precision={0}
                      value={shipQty[row.id] ?? 0}
                      onChange={(value) =>
                        setShipQty((prev) => ({ ...prev, [row.id as number]: Number(value ?? 0) }))
                      }
                    />
                  );
                },
              },
            ]}
          />
        </Space>
      </Modal>
    </Card>
  );
}

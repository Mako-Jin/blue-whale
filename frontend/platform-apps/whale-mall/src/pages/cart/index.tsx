// 购物车：按渠道隔离的车内条目，改量 / 删除 / 选中、结算预览与提交
//
// 结算前必须先 preview：预览与下单之间库存可能被别人买走，预览能看到"当前这一版"金额，
// 下单时后端会再校验一次并可能拒绝——不是前端算错了。
// 幂等键在同一次结算流程内固定，用户连点两次只会得到一张单。
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  addCartItem,
  createOrder,
  fetchCart,
  getProduct,
  pageChannels,
  pageProducts,
  previewOrder,
  removeCartItem,
  selectCartItems,
  updateCartQuantity,
  type CartItemView,
  type CartView,
  type ChannelView,
  type OrderPreviewView,
  type PriceView,
  type SkuView,
  type SpuView,
} from '../../api/mall';
import {
  MODE_LABEL,
  PRICE_TYPE_LABEL,
  formatMoney,
  formatSpec,
} from '../../utils/dict';

/** 加购表单值 */
interface AddFormValues {
  spuId: number;
  skuId: number;
  quantity: number;
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

/**
 * 生成幂等键（同一次结算流程复用）。
 *
 * @returns 幂等键
 */
function newIdempotencyKey(): string {
  const uuid = globalThis.crypto?.randomUUID?.();
  return uuid ?? `mall-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
}

/**
 * 取 SKU 在指定渠道下的成交价（与后端解析规则一致：优先形态价，经销回落零售）。
 *
 * @param sku SKU
 * @param mode 渠道形态
 * @returns 价目；缺失时 undefined
 */
function pickPrice(sku: SkuView, mode: string): PriceView | undefined {
  const preferred = mode === 'WELFARE' ? 'POINTS' : mode === 'B2B' ? 'B2B' : 'RETAIL';
  const hit = sku.prices?.find((item) => item.priceType === preferred);
  if (hit) {
    return hit;
  }
  return mode === 'B2B' ? sku.prices?.find((item) => item.priceType === 'RETAIL') : undefined;
}

export default function CartPage() {
  const navigate = useNavigate();
  const [channels, setChannels] = useState<ChannelView[]>([]);
  const [channelId, setChannelId] = useState<number | undefined>(undefined);
  const [cart, setCart] = useState<CartView | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<OrderPreviewView | null>(null);
  const [checkoutItemIds, setCheckoutItemIds] = useState<number[]>([]);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [products, setProducts] = useState<SpuView[]>([]);
  const [skus, setSkus] = useState<SkuView[]>([]);
  const [addForm] = Form.useForm<AddFormValues>();
  const [selectedSpuId, setSelectedSpuId] = useState<number | undefined>(undefined);

  const channel = useMemo(
    () => channels.find((item) => item.id === channelId),
    [channels, channelId],
  );

  /** 渠道列表（只列启用渠道：停业渠道无法加购） */
  useEffect(() => {
    pageChannels({ status: 'ACTIVE', current: 1, size: 100 })
      .then((page) => {
        const list = page.records ?? [];
        setChannels(list);
        if (list.length > 0) {
          setChannelId((prev) => prev ?? list[0].id);
        }
      })
      .catch((error: unknown) => message.error(errorText(error)));
  }, []);

  /** 拉取购物车 */
  const load = useCallback(async () => {
    if (!channelId) {
      setCart(null);
      return;
    }
    setLoading(true);
    try {
      setCart(await fetchCart(channelId));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [channelId]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 改数量 */
  const changeQuantity = async (row: CartItemView, quantity: number) => {
    if (!channelId || quantity < 1 || quantity === row.quantity) {
      return;
    }
    setBusy(true);
    try {
      setCart(await updateCartQuantity(row.id, quantity));
    } catch (error) {
      message.error(errorText(error));
      await load();
    } finally {
      setBusy(false);
    }
  };

  /** 删除行 */
  const removeRow = async (row: CartItemView) => {
    setBusy(true);
    try {
      setCart(await removeCartItem(row.id));
      message.success('已移出购物车');
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 切换选中 */
  const toggleSelected = async (row: CartItemView, selected: boolean) => {
    if (!channelId) {
      return;
    }
    setBusy(true);
    try {
      setCart(await selectCartItems({ channelId, itemIds: [row.id], selected }));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 打开结算预览 */
  const openPreview = async () => {
    if (!channelId) {
      return;
    }
    const itemIds = (cart?.items ?? []).filter((item) => item.selected).map((item) => item.id);
    if (itemIds.length === 0) {
      message.warning('请先勾选要结算的商品');
      return;
    }
    setPreviewLoading(true);
    try {
      const data = await previewOrder({ channelId, itemIds });
      setPreview(data);
      // 下单接口的 itemIds 是**购物车行 ID**（后端据此取数量与 SKU），不是 SKU ID
      setCheckoutItemIds(itemIds);
      setIdempotencyKey(newIdempotencyKey());
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setPreviewLoading(false);
    }
  };

  /** 提交订单 */
  const submitOrder = async () => {
    if (!channelId || !preview) {
      return;
    }
    setBusy(true);
    try {
      const order = await createOrder(
        {
          channelId,
          itemIds: checkoutItemIds,
          remark: '',
        },
        idempotencyKey,
      );
      message.success(`下单成功：${order.orderNo}`);
      setPreview(null);
      setCheckoutItemIds([]);
      navigate(`/order/${order.id}`);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 打开加购弹窗（商品从商城商品中心取，SKU 按商品详情展开） */
  const openAdd = async () => {
    setAddOpen(true);
    setSkus([]);
    setSelectedSpuId(undefined);
    addForm.setFieldsValue({ quantity: 1 });
    try {
      const page = await pageProducts({ current: 1, size: 100, status: 'ON_SALE' });
      setProducts(page.records ?? []);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 选商品后加载 SKU */
  const loadSkus = async (spuId: number) => {
    setSelectedSpuId(spuId);
    addForm.setFieldValue('skuId', undefined);
    try {
      const detail = await getProduct(spuId);
      setSkus(detail.skus ?? []);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 加购提交 */
  const submitAdd = async () => {
    if (!channelId) {
      return;
    }
    const values = await addForm.validateFields();
    setBusy(true);
    try {
      setCart(await addCartItem({ channelId, skuId: values.skuId, quantity: values.quantity }));
      message.success('已加入购物车');
      setAddOpen(false);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  const currencyType = cart?.currencyType ?? channel?.currencyType ?? 'CASH';

  const columns: TableProps<CartItemView>['columns'] = [
    {
      title: '',
      dataIndex: 'selected',
      width: 50,
      render: (_, row) => (
        <input
          type="checkbox"
          aria-label="选择"
          checked={row.selected}
          disabled={!row.available || busy}
          onChange={(event) => void toggleSelected(row, event.target.checked)}
        />
      ),
    },
    { title: '商品', dataIndex: 'spuName' },
    {
      title: '规格',
      dataIndex: 'specJson',
      width: 160,
      render: (value?: string) => formatSpec(value),
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      width: 160,
      render: (value: number, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{formatMoney(value, currencyType)}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {PRICE_TYPE_LABEL[row.priceType] ?? row.priceType}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 120,
      render: (value: number, row) => (
        <InputNumber
          min={1}
          max={999}
          value={value}
          disabled={busy}
          onChange={(next) => next && void changeQuantity(row, Number(next))}
        />
      ),
    },
    {
      title: '小计',
      dataIndex: 'amount',
      width: 140,
      render: (value: number) => formatMoney(value, currencyType),
    },
    {
      title: '可售情况',
      width: 160,
      render: (_, row) =>
        row.available ? (
          <Tag color="green">可售 {row.availableQty ?? 0}</Tag>
        ) : (
          <Tag color="red">{row.unavailableReason ?? '不可售'}</Tag>
        ),
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <Button type="link" size="small" danger disabled={busy} onClick={() => void removeRow(row)}>
          删除
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="购物车"
      extra={
        <Space wrap>
          <Select
            style={{ width: 240 }}
            placeholder="选择商城渠道"
            value={channelId}
            options={channels.map((item) => ({
              value: item.id,
              label: `${item.channelName}（${MODE_LABEL[item.mode] ?? item.mode}）`,
            }))}
            onChange={(value: number) => setChannelId(value)}
          />
          <Button onClick={() => void openAdd()} disabled={!channelId}>
            加入购物车
          </Button>
          <Button type="primary" loading={previewLoading} disabled={!channelId} onClick={openPreview}>
            去结算
          </Button>
        </Space>
      }
    >
      {channels.length === 0 ? (
        <Empty description="还没有启用的商城渠道，请先在「渠道管理」里建一个" />
      ) : (
        <>
          <Table<CartItemView>
            rowKey="id"
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={cart?.items ?? []}
            pagination={false}
            locale={{ emptyText: <Empty description="购物车是空的" /> }}
          />
          <Space style={{ marginTop: 16 }} size="large">
            <Typography.Text>共 {cart?.totalQuantity ?? 0} 件</Typography.Text>
            <Typography.Text>已选 {cart?.selectedQuantity ?? 0} 件</Typography.Text>
            <Typography.Text strong>
              已选金额：{formatMoney(cart?.selectedAmount ?? 0, currencyType)}
            </Typography.Text>
          </Space>
        </>
      )}

      <Modal
        title="结算预览"
        open={preview !== null}
        width={720}
        confirmLoading={busy}
        okText="提交订单"
        onCancel={() => setPreview(null)}
        onOk={submitOrder}
      >
        {preview ? (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message={`${MODE_LABEL[preview.mode] ?? preview.mode} · ${
                preview.settlementType
              } · 共 ${preview.totalQuantity} 件`}
            />
            <Descriptions column={1} size="small" bordered>
              {preview.lines.map((line) => (
                <Descriptions.Item
                  key={line.skuId}
                  label={`${line.spuName} ${formatSpec(line.specJson)}`}
                >
                  {formatMoney(line.unitPrice, preview.currencyType)} × {line.quantity} ={' '}
                  {formatMoney(line.amount, preview.currencyType)}
                </Descriptions.Item>
              ))}
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              应付：{formatMoney(preview.totalAmount, preview.currencyType)}
            </Typography.Title>
          </>
        ) : null}
      </Modal>

      <Modal
        title="加入购物车"
        open={addOpen}
        confirmLoading={busy}
        onCancel={() => setAddOpen(false)}
        onOk={submitAdd}
        destroyOnClose
      >
        <Form form={addForm} layout="vertical" preserve={false}>
          <Form.Item label="商品" name="spuId" rules={[{ required: true, message: '请选择商品' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="在售商品"
              options={products.map((item) => ({ value: item.id, label: item.spuName }))}
              onChange={(value: number) => void loadSkus(value)}
            />
          </Form.Item>
          <Form.Item label="规格" name="skuId" rules={[{ required: true, message: '请选择规格' }]}>
            <Select
              placeholder={selectedSpuId ? '选择规格' : '请先选择商品'}
              disabled={!selectedSpuId}
              options={skus.map((sku) => {
                const price = pickPrice(sku, channel?.mode ?? 'RETAIL');
                return {
                  value: sku.id,
                  label: `${formatSpec(sku.specJson)} ${
                    price ? formatMoney(price.price, currencyType) : '未配置价格'
                  }`,
                  disabled: !price,
                };
              })}
            />
          </Form.Item>
          <Form.Item
            label="数量"
            name="quantity"
            rules={[{ required: true, message: '请填写数量' }]}
          >
            <InputNumber min={1} max={999} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

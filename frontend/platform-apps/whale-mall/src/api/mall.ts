// 商城子应用接口层：与 whale-mall 控制器逐字段对齐（/mall/**，不剥离前缀）
//
// 分四块：元信息 / 商品中心（类目·商品·库存）/ 渠道 / 交易（购物车·订单）。
// 价格字段后端为 BigDecimal，JSON 序列化为数字；统一用 Money 类型（number）承载，
// 展示前经 utils/dict.ts 的 formatMoney 处理，避免页面各自 toFixed。

import { http, type PageResult, request } from './client';

/** 金额（后端 BigDecimal，序列化为 number） */
export type Money = number;

/** 商城元信息（枚举字典的唯一来源：前端不硬编码形态/结算/价目取值） */
export interface MallMeta {
  service: string;
  version: string;
  modes: string[];
  settlementTypes: string[];
  currencyTypes: string[];
  priceTypes: string[];
  orderStatuses: string[];
}

/** 读取商城元信息 */
export function fetchMeta(): Promise<MallMeta> {
  return http.get<MallMeta>('/mall/meta');
}

// ---------------------------------------------------------------------------
// 类目
// ---------------------------------------------------------------------------

/** 类目节点（树形响应） */
export interface CategoryNode {
  id: number;
  parentId: number;
  categoryName: string;
  categoryLevel: number;
  sortNo: number;
  status: string;
  children?: CategoryNode[];
}

/** 类目保存入参 */
export interface CategorySavePayload {
  parentId?: number | null;
  categoryName: string;
  sortNo?: number | null;
  status?: string;
  remark?: string;
}

/** 类目树（keyword / status 可选） */
export function fetchCategoryTree(query?: {
  keyword?: string;
  status?: string;
}): Promise<CategoryNode[]> {
  return http.get<CategoryNode[]>('/mall/category/tree', query);
}

/** 新建类目 */
export function createCategory(body: CategorySavePayload): Promise<CategoryNode> {
  return http.post<CategoryNode>('/mall/category', body);
}

/** 编辑类目 */
export function updateCategory(id: number, body: CategorySavePayload): Promise<CategoryNode> {
  return http.put<CategoryNode>(`/mall/category/${id}`, body);
}

/** 删除类目（有子类目或在用商品时后端拒绝） */
export function removeCategory(id: number): Promise<void> {
  return http.del<void>(`/mall/category/${id}`);
}

// ---------------------------------------------------------------------------
// 商品（SPU + SKU + 价目）
// ---------------------------------------------------------------------------

/** 价目 */
export interface PriceView {
  priceType: string;
  price: Money;
}

/** SKU 视图 */
export interface SkuView {
  id: number;
  skuCode: string;
  specJson?: string;
  imageUrl?: string;
  status: string;
  sortNo: number;
  availableQty?: number;
  reservedQty?: number;
  prices: PriceView[];
}

/** 商品列表视图 */
export interface SpuView {
  id: number;
  spuCode: string;
  spuName: string;
  categoryId: number;
  categoryName?: string;
  brand?: string;
  mainImage?: string;
  status: string;
  sortNo: number;
  updateTime?: string;
}

/** 商品详情视图 */
export interface SpuDetailView extends SpuView {
  images: string[];
  detail?: string;
  remark?: string;
  skus: SkuView[];
}

/** SKU 保存入参 */
export interface SkuPayload {
  id?: number | null;
  skuCode: string;
  specJson?: string;
  imageUrl?: string;
  status?: string;
  sortNo?: number | null;
  prices: PriceView[];
}

/** 商品保存入参 */
export interface SpuSavePayload {
  spuCode: string;
  spuName: string;
  categoryId: number;
  brand?: string;
  mainImage?: string;
  images?: string[];
  detail?: string;
  sortNo?: number | null;
  remark?: string;
  skus: SkuPayload[];
}

/** 商品分页查询 */
export interface ProductQuery {
  [key: string]: unknown;
  keyword?: string;
  categoryId?: number | null;
  status?: string;
  current?: number;
  size?: number;
}

/** 商品分页 */
export function pageProducts(query: ProductQuery): Promise<PageResult<SpuView>> {
  return http.get<PageResult<SpuView>>('/mall/product/page', query);
}

/** 商品详情（含 SKU 与库存） */
export function getProduct(id: number): Promise<SpuDetailView> {
  return http.get<SpuDetailView>(`/mall/product/${id}`);
}

/** 新建商品（返回新商品 ID） */
export function createProduct(body: SpuSavePayload): Promise<number> {
  return http.post<number>('/mall/product', body);
}

/** 编辑商品 */
export function updateProduct(id: number, body: SpuSavePayload): Promise<void> {
  return http.put<void>(`/mall/product/${id}`, body);
}

/** 上下架（只接受 ON_SALE / OFF_SALE） */
export function changeProductStatus(id: number, status: string): Promise<void> {
  return http.put<void>(`/mall/product/${id}/status`, undefined, { status });
}

/** 删除商品（在售不可删） */
export function removeProduct(id: number): Promise<void> {
  return http.del<void>(`/mall/product/${id}`);
}

// ---------------------------------------------------------------------------
// 库存
// ---------------------------------------------------------------------------

/** 库存视图 */
export interface StockView {
  skuId: number;
  skuCode: string;
  spuId: number;
  spuName: string;
  specJson?: string;
  spuStatus?: string;
  availableQty: number;
  reservedQty: number;
  totalQty: number;
  updateTime?: string;
}

/** 库存分页（跨商品按 SKU 维度） */
export function pageStocks(query: {
  keyword?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<StockView>> {
  return http.get<PageResult<StockView>>('/mall/stock/page', query);
}

/** 调整库存（delta 为增量，可负；返回调整后的可售数量） */
export function adjustStock(skuId: number, delta: number, remark?: string): Promise<number> {
  return http.put<number>(`/mall/stock/${skuId}`, { delta, remark });
}

// ---------------------------------------------------------------------------
// 渠道（形态的唯一载体：mode + settlementType 决定这一单按哪种价目计价）
// ---------------------------------------------------------------------------

/** 渠道视图 */
export interface ChannelView {
  id: number;
  channelCode: string;
  channelName: string;
  mode: string;
  settlementType: string;
  currencyType: string;
  status: string;
  remark?: string;
  createTime?: string;
}

/** 渠道保存入参 */
export interface ChannelSavePayload {
  channelCode: string;
  channelName: string;
  mode: string;
  settlementType: string;
  remark?: string;
}

/** 渠道分页 */
export function pageChannels(query: {
  keyword?: string;
  mode?: string;
  status?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<ChannelView>> {
  return http.get<PageResult<ChannelView>>('/mall/channel/page', query);
}

/** 新建渠道 */
export function createChannel(body: ChannelSavePayload): Promise<ChannelView> {
  return http.post<ChannelView>('/mall/channel', body);
}

/** 编辑渠道 */
export function updateChannel(id: number, body: ChannelSavePayload): Promise<ChannelView> {
  return http.put<ChannelView>(`/mall/channel/${id}`, body);
}

/** 渠道启停 */
export function changeChannelStatus(id: number, status: string): Promise<void> {
  return http.put<void>(`/mall/channel/${id}/status`, undefined, { status });
}

// ---------------------------------------------------------------------------
// 购物车
// ---------------------------------------------------------------------------

/** 购物车行 */
export interface CartItemView {
  id: number;
  skuId: number;
  skuCode: string;
  spuId: number;
  spuName: string;
  specJson?: string;
  imageUrl?: string;
  unitPrice: Money;
  priceType: string;
  quantity: number;
  amount: Money;
  selected: boolean;
  availableQty?: number;
  available: boolean;
  unavailableReason?: string;
}

/** 购物车视图 */
export interface CartView {
  channelId: number;
  channelName: string;
  currencyType: string;
  items: CartItemView[];
  totalQuantity: number;
  selectedQuantity: number;
  selectedAmount: Money;
}

/** 查看购物车（按渠道隔离：同一 SKU 在两个渠道是两行） */
export function fetchCart(channelId: number): Promise<CartView> {
  return http.get<CartView>('/mall/cart', { channelId });
}

/** 加入购物车（同 SKU 数量累加） */
export function addCartItem(body: {
  channelId: number;
  skuId: number;
  quantity: number;
}): Promise<CartView> {
  return http.post<CartView>('/mall/cart/item', body);
}

/** 修改数量（<= 0 由后端拒绝，前端不要静默拦截） */
export function updateCartQuantity(itemId: number, quantity: number): Promise<CartView> {
  return http.put<CartView>(`/mall/cart/item/${itemId}`, undefined, { quantity });
}

/** 删除购物车行 */
export function removeCartItem(itemId: number): Promise<CartView> {
  return http.del<CartView>(`/mall/cart/item/${itemId}`);
}

/** 批量选中 / 取消选中（itemIds 为空表示整车） */
export function selectCartItems(body: {
  channelId: number;
  itemIds?: number[];
  selected?: boolean;
}): Promise<CartView> {
  return http.put<CartView>('/mall/cart/select', body);
}

// ---------------------------------------------------------------------------
// 订单
// ---------------------------------------------------------------------------

/** 订单行 */
export interface OrderLineView {
  skuId: number;
  spuId: number;
  spuName: string;
  skuCode: string;
  specJson?: string;
  imageUrl?: string;
  priceType: string;
  unitPrice: Money;
  quantity: number;
  amount: Money;
}

/** 结算预览 */
export interface OrderPreviewView {
  channelId: number;
  mode: string;
  settlementType: string;
  currencyType: string;
  lines: OrderLineView[];
  totalAmount: Money;
  totalQuantity: number;
}

/** 订单视图 */
export interface OrderView {
  id: number;
  orderNo: string;
  channelId: number;
  channelCode?: string;
  mode: string;
  settlementType: string;
  currencyType: string;
  buyerUserId: number;
  buyerName?: string;
  status: string;
  totalAmount: Money;
  totalQuantity: number;
  itemCount: number;
  payNo?: string;
  payTime?: string;
  shipTime?: string;
  completeTime?: string;
  cancelTime?: string;
  cancelReason?: string;
  remark?: string;
  createTime?: string;
  items: OrderLineView[];
}

/** 结算预览（只算不算，不落库、不锁库存） */
export function previewOrder(body: {
  channelId: number;
  itemIds: number[];
}): Promise<OrderPreviewView> {
  return http.post<OrderPreviewView>('/mall/order/preview', body);
}

/**
 * 提交订单。
 *
 * <p>`idempotencyKey` 必传：后端把它落唯一键 `uk_mall_order_idem`，
 * 重复提交返回**已有订单**而不是报错——用户连点两次只应得到一张单。</p>
 *
 * @param body 下单入参
 * @param idempotencyKey 幂等键（同一次结算流程内保持同一个值）
 * @returns 订单视图
 */
export function createOrder(
  body: { channelId: number; itemIds: number[]; remark?: string },
  idempotencyKey: string,
): Promise<OrderView> {
  return request<OrderView>('/mall/order', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

/** 我的订单分页 */
export function pageMyOrders(query: {
  status?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<OrderView>> {
  return http.get<PageResult<OrderView>>('/mall/order/my', query);
}

/** 全部订单分页（后台口径，需 ORDER_MANAGE 之外的 ORDER_LIST 即可读） */
export function pageOrders(query: {
  status?: string;
  keyword?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<OrderView>> {
  return http.get<PageResult<OrderView>>('/mall/order/page', query);
}

/** 订单详情 */
export function getOrder(id: number): Promise<OrderView> {
  return http.get<OrderView>(`/mall/order/${id}`);
}

/** 取消订单（只对未支付生效） */
export function cancelOrder(id: number, reason?: string): Promise<void> {
  return http.post<void>(`/mall/order/${id}/cancel`, undefined, { reason });
}

/** 发货（PAID → SHIPPED） */
export function shipOrder(id: number): Promise<void> {
  return http.post<void>(`/mall/order/${id}/ship`);
}

/** 确认收货（SHIPPED → COMPLETED） */
export function completeOrder(id: number): Promise<void> {
  return http.post<void>(`/mall/order/${id}/complete`);
}

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
  /** 绑定的运费模板 ID（空 = 不收运费，积分商城恒为空） */
  freightTemplateId?: number;
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
  /** 运费模板 ID（可空；积分商城会被后端强制清空） */
  freightTemplateId?: number;
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
  /** 订单明细 ID（申请售后时定位被退的行） */
  id?: number;
  skuId: number;
  spuId: number;
  spuName: string;
  skuCode: string;
  specJson?: string;
  imageUrl?: string;
  priceType: string;
  unitPrice: Money;
  quantity: number;
  /** 已退数量 */
  refundedQuantity?: number;
  /** 当前可退数量（售后申请上限） */
  refundableQuantity?: number;
  /** 小计（优惠与运费分摊前） */
  amount: Money;
  /** 分摊优惠金额 */
  shareDiscount?: Money;
  /** 分摊运费 */
  shareFreight?: Money;
  /** 本行实付金额（售后可退上限） */
  netAmount?: Money;
}

/** 结算预览 */
export interface OrderPreviewView {
  channelId: number;
  mode: string;
  settlementType: string;
  currencyType: string;
  lines: OrderLineView[];
  /** 商品总额（优惠与运费前） */
  goodsAmount: Money;
  /** 优惠金额 */
  discountAmount: Money;
  /** 运费 */
  freightAmount: Money;
  /** 应付金额 */
  totalAmount: Money;
  totalQuantity: number;
  /** 本次选中的用户券 ID */
  couponId?: number;
  /** 本次选中的券名称 */
  couponName?: string;
  /** 当前订单可用的券（含不可用及原因） */
  availableCoupons?: CouponUserView[];
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
  /** 应付金额 */
  totalAmount: Money;
  /** 商品总额 */
  goodsAmount?: Money;
  /** 优惠金额 */
  discountAmount?: Money;
  /** 运费 */
  freightAmount?: Money;
  /** 使用的券编码 */
  couponCode?: string;
  totalQuantity: number;
  itemCount: number;
  payNo?: string;
  payTime?: string;
  refundAmount?: Money;
  refundTime?: string;
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
  couponId?: number;
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
  body: { channelId: number; itemIds: number[]; remark?: string; couponId?: number },
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

/** 买家发起支付：创建支付单并调起支付，返回支付单号（成功后 whale-pay 异步回调推进状态） */
export function payOrder(id: number): Promise<string> {
  return http.post<string>(`/mall/order/${id}/pay`);
}

/** 整单退款（运营；PAID → REFUNDING，退款成功由 whale-pay 回调终态） */
export function refundOrder(id: number, reason?: string): Promise<void> {
  return http.post<void>(`/mall/order/${id}/refund`, undefined, { reason });
}

/** 发货（PAID → SHIPPED） */
export function shipOrder(id: number): Promise<void> {
  return http.post<void>(`/mall/order/${id}/ship`);
}

/** 确认收货（SHIPPED → COMPLETED） */
export function completeOrder(id: number): Promise<void> {
  return http.post<void>(`/mall/order/${id}/complete`);
}

// ---------------------------------------------------------------------------
// 售后
// ---------------------------------------------------------------------------

/** 售后行 */
export interface AfterSaleLineView {
  id: number;
  orderItemId: number;
  spuId: number;
  skuId: number;
  spuName: string;
  skuCode: string;
  unitPrice: Money;
  /** 本次退数量 */
  quantity: number;
  /** 本次退金额 */
  amount: Money;
}

/** 售后单 */
export interface AfterSaleView {
  id: number;
  afterSaleNo: string;
  orderId: number;
  orderNo: string;
  userId: number;
  /** REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 */
  type: string;
  /** CREATED / APPROVED / REFUNDED / REJECTED / CANCELLED */
  status: string;
  refundAmount: Money;
  reason?: string;
  description?: string;
  images?: string;
  auditUserId?: number;
  auditUserName?: string;
  auditTime?: string;
  auditRemark?: string;
  refundNo?: string;
  refundTime?: string;
  returnLogisticsNo?: string;
  createTime?: string;
  items: AfterSaleLineView[];
}

/** 申请售后（金额由后端按订单行快照价计算，前端只传行与数量） */
export function applyAfterSale(body: {
  orderId: number;
  type?: string;
  reason?: string;
  description?: string;
  images?: string;
  returnLogisticsNo?: string;
  lines: Array<{ orderItemId: number; quantity: number }>;
}): Promise<number> {
  return http.post<number>('/mall/after-sale', body);
}

/** 我的售后分页 */
export function pageMyAfterSale(query: {
  status?: string;
  keyword?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<AfterSaleView>> {
  return http.get<PageResult<AfterSaleView>>('/mall/after-sale/page', query);
}

/** 全部售后分页（运营） */
export function pageAfterSaleAdmin(query: {
  status?: string;
  keyword?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<AfterSaleView>> {
  return http.get<PageResult<AfterSaleView>>('/mall/after-sale/admin/page', query);
}

/** 售后详情 */
export function getAfterSale(id: number): Promise<AfterSaleView> {
  return http.get<AfterSaleView>(`/mall/after-sale/${id}`);
}

/** 撤销售后（仅待审核，买家本人或运营） */
export function cancelAfterSale(id: number): Promise<void> {
  return http.post<void>(`/mall/after-sale/${id}/cancel`);
}

/** 审核通过并发起退款 */
export function approveAfterSale(id: number, remark?: string): Promise<void> {
  return http.post<void>(`/mall/after-sale/${id}/approve`, undefined, { remark });
}

/** 驳回售后 */
export function rejectAfterSale(id: number, remark?: string): Promise<void> {
  return http.post<void>(`/mall/after-sale/${id}/reject`, undefined, { remark });
}

// ---------------------------------------------------------------------------
// 运费模板
// ---------------------------------------------------------------------------

/** 运费模板 */
export interface FreightTemplateView {
  id: number;
  templateName: string;
  /** FREE 全免 / FIXED 固定 / THRESHOLD 满额包邮 */
  chargeType: string;
  /** 固定运费金额 */
  baseAmount?: Money;
  /** 包邮门槛 */
  freeThreshold?: Money;
  status: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 运费模板保存入参 */
export interface FreightTemplateSaveBody {
  templateName: string;
  chargeType: string;
  baseAmount?: number;
  freeThreshold?: number;
  status?: string;
  remark?: string;
}

/** 运费模板分页 */
export function pageFreightTemplate(query: {
  keyword?: string;
  chargeType?: string;
  status?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<FreightTemplateView>> {
  return http.get<PageResult<FreightTemplateView>>('/mall/freight-template/page', query);
}

/** 运费模板详情 */
export function getFreightTemplate(id: number): Promise<FreightTemplateView> {
  return http.get<FreightTemplateView>(`/mall/freight-template/${id}`);
}

/** 新建运费模板 */
export function createFreightTemplate(body: FreightTemplateSaveBody): Promise<FreightTemplateView> {
  return http.post<FreightTemplateView>('/mall/freight-template', body);
}

/** 编辑运费模板 */
export function updateFreightTemplate(
  id: number,
  body: FreightTemplateSaveBody,
): Promise<FreightTemplateView> {
  return http.put<FreightTemplateView>(`/mall/freight-template/${id}`, body);
}

/** 启停运费模板 */
export function changeFreightTemplateStatus(id: number, status: string): Promise<void> {
  return http.post<void>(`/mall/freight-template/${id}/status`, undefined, { status });
}

// ---------------------------------------------------------------------------
// 优惠券
// ---------------------------------------------------------------------------

/** 券模板 */
export interface CouponView {
  id: number;
  couponCode: string;
  couponName: string;
  /** FULL_REDUCTION 满减 / DISCOUNT 折扣 */
  couponType: string;
  thresholdAmount?: Money;
  faceAmount?: Money;
  discountRate?: number;
  maxDiscountAmount?: Money;
  channelId?: number;
  totalQty?: number;
  issuedQty?: number;
  perUserLimit?: number;
  validFrom?: string;
  validTo?: string;
  status: string;
  remark?: string;
  createTime?: string;
}

/** 券模板保存入参 */
export interface CouponSaveBody {
  couponCode: string;
  couponName: string;
  couponType: string;
  thresholdAmount?: number;
  faceAmount?: number;
  discountRate?: number;
  maxDiscountAmount?: number;
  channelId?: number;
  totalQty?: number;
  perUserLimit?: number;
  /** 后端按 yyyy-MM-dd HH:mm:ss 解析 */
  validFrom: string;
  validTo: string;
  status?: string;
  remark?: string;
}

/** 我的券 / 下单可用券 */
export interface CouponUserView {
  /** 用户券 ID（下单时传它，不是模板 ID） */
  id: number;
  couponId: number;
  couponCode: string;
  couponName: string;
  couponType: string;
  thresholdAmount?: Money;
  faceAmount?: Money;
  discountRate?: number;
  maxDiscountAmount?: Money;
  channelId?: number;
  validFrom?: string;
  validTo?: string;
  status: string;
  orderNo?: string;
  usedTime?: string;
  /** 本单可减金额（仅下单可用券接口返回） */
  discountAmount?: Money;
  /** USABLE / USED / EXPIRED / CHANNEL_MISMATCH / THRESHOLD_NOT_MET */
  usability?: string;
  /** 不可用原因（给买家看） */
  usabilityLabel?: string;
}

/** 券模板分页 */
export function pageCoupon(query: {
  keyword?: string;
  couponType?: string;
  status?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<CouponView>> {
  return http.get<PageResult<CouponView>>('/mall/coupon/page', query);
}

/** 新建券模板 */
export function createCoupon(body: CouponSaveBody): Promise<CouponView> {
  return http.post<CouponView>('/mall/coupon', body);
}

/** 编辑券模板（不影响已领出的券） */
export function updateCoupon(id: number, body: CouponSaveBody): Promise<CouponView> {
  return http.put<CouponView>(`/mall/coupon/${id}`, body);
}

/** 启停券模板 */
export function changeCouponStatus(id: number, status: string): Promise<void> {
  return http.post<void>(`/mall/coupon/${id}/status`, undefined, { status });
}

/** 领取优惠券 */
export function claimCoupon(id: number): Promise<CouponUserView> {
  return http.post<CouponUserView>(`/mall/coupon/${id}/claim`);
}

/** 我的券分页 */
export function pageMyCoupons(query: {
  status?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<CouponUserView>> {
  return http.get<PageResult<CouponUserView>>('/mall/coupon/mine', query);
}

/** 当前订单可用的券（含不可用及原因） */
export function listAvailableCoupons(query: {
  channelId: number;
  amount?: number;
}): Promise<CouponUserView[]> {
  return http.get<CouponUserView[]>('/mall/coupon/available', query);
}

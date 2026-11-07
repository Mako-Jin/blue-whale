// 商城子应用文案 / 字典 / 金额与时间格式化
//
// 枚举取值以 `/mall/meta` 为准（后端 MallMode / SettlementType / PriceType 等），
// 这里只做「中文文案 + 颜色」映射，不在前端重定义业务取值。

/** 渠道形态文案（决定这一单按哪种价目计价） */
export const MODE_LABEL: Record<string, string> = {
  RETAIL: '零售',
  B2B: '经销订货',
  WELFARE: '员工福利',
};

/** 渠道形态标签色 */
export const MODE_COLOR: Record<string, string> = {
  RETAIL: 'blue',
  B2B: 'gold',
  WELFARE: 'purple',
};

/** 结算方式文案 */
export const SETTLEMENT_LABEL: Record<string, string> = {
  ONLINE: '在线支付',
  CREDIT: '账期挂账',
  POINTS: '积分结算',
};

/** 币种文案 */
export const CURRENCY_LABEL: Record<string, string> = {
  CASH: '人民币',
  POINTS: '积分',
};

/** 价目类型文案 */
export const PRICE_TYPE_LABEL: Record<string, string> = {
  RETAIL: '零售价',
  B2B: '经销价',
  POINTS: '积分价',
};

/** 商品状态下拉选项 */
export const SPU_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ON_SALE', label: '在售' },
  { value: 'OFF_SALE', label: '已下架' },
];

/** 商品状态文案 */
export const SPU_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ON_SALE: '在售',
  OFF_SALE: '已下架',
};

/** 商品状态标签色 */
export const SPU_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ON_SALE: 'green',
  OFF_SALE: 'orange',
};

/** 启用停用状态下拉选项（类目 / 渠道 / SKU 共用） */
export const ENABLE_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '启用' },
  { value: 'DISABLED', label: '停用' },
];

/** 启用停用文案 */
export const ENABLE_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
};

/** 启用停用标签色 */
export const ENABLE_STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  DISABLED: 'red',
};

/** 订单状态下拉选项 */
export const ORDER_STATUS_OPTIONS = [
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDING', label: '退款中' },
  { value: 'REFUNDED', label: '已退款' },
];

/** 订单状态文案 */
export const ORDER_STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '待发货',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
};

/** 订单状态标签色 */
export const ORDER_STATUS_COLOR: Record<string, string> = {
  PENDING_PAYMENT: 'gold',
  PAID: 'blue',
  SHIPPED: 'cyan',
  COMPLETED: 'green',
  CANCELLED: 'default',
  REFUNDING: 'orange',
  REFUNDED: 'purple',
};

/** 售后类型下拉选项 */
export const AFTER_SALE_TYPE_OPTIONS = [
  { value: 'REFUND_ONLY', label: '仅退款' },
  { value: 'RETURN_REFUND', label: '退货退款' },
];

/** 售后类型文案 */
export const AFTER_SALE_TYPE_LABEL: Record<string, string> = {
  REFUND_ONLY: '仅退款',
  RETURN_REFUND: '退货退款',
};

/** 售后状态下拉选项 */
export const AFTER_SALE_STATUS_OPTIONS = [
  { value: 'CREATED', label: '待审核' },
  { value: 'APPROVED', label: '退款中' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已撤销' },
];

/** 售后状态文案 */
export const AFTER_SALE_STATUS_LABEL: Record<string, string> = {
  CREATED: '待审核',
  APPROVED: '退款中',
  REFUNDED: '已退款',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
};

/** 售后状态标签色 */
export const AFTER_SALE_STATUS_COLOR: Record<string, string> = {
  CREATED: 'gold',
  APPROVED: 'orange',
  REFUNDED: 'purple',
  REJECTED: 'red',
  CANCELLED: 'default',
};

/** 售后原因下拉选项（申请时选择） */
export const AFTER_SALE_REASON_OPTIONS = [
  { value: '质量问题', label: '质量问题' },
  { value: '发错货', label: '发错货' },
  { value: '少件/漏发', label: '少件/漏发' },
  { value: '商品与描述不符', label: '商品与描述不符' },
  { value: '不想要了', label: '不想要了' },
  { value: '其他', label: '其他' },
];

/**
 * 金额格式化：按币种决定符号与小数位。
 *
 * <p>积分价后端强制整数，展示成 `120 积分` 而不是 `¥120.00`——
 * 把积分显示成人民币是这类系统最容易被截图吐槽的错误。</p>
 *
 * @param amount 金额
 * @param currencyType 币种（CASH / POINTS）
 * @returns 带符号的金额文本
 */
export function formatMoney(amount?: number | null, currencyType = 'CASH'): string {
  if (amount === undefined || amount === null) {
    return '-';
  }
  if (currencyType === 'POINTS') {
    return `${Math.round(Number(amount))} 积分`;
  }
  return `¥${Number(amount).toFixed(2)}`;
}

/** 格式化后端 LocalDateTime（兼容 ISO 与 yyyy-MM-dd HH:mm:ss） */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

/** 规格 JSON 转展示文本（`{"颜色":"黑"}` → `颜色: 黑`），解析失败原样返回 */
export function formatSpec(specJson?: string | null): string {
  if (!specJson) {
    return '-';
  }
  try {
    const parsed = JSON.parse(specJson) as Record<string, unknown>;
    const entries = Object.entries(parsed);
    if (entries.length === 0) {
      return '-';
    }
    return entries.map(([key, value]) => `${key}: ${String(value)}`).join(' / ');
  } catch {
    return specJson;
  }
}

/** 运费计费方式 */
export const FREIGHT_CHARGE_TYPE_LABEL: Record<string, string> = {
  FREE: '全免运费',
  FIXED: '固定运费',
  THRESHOLD: '满额包邮',
};

/** 优惠券类型 */
export const COUPON_TYPE_LABEL: Record<string, string> = {
  FULL_REDUCTION: '满减券',
  DISCOUNT: '折扣券',
};

/** 券模板状态 */
export const COUPON_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
};

/** 用户券状态 */
export const COUPON_USER_STATUS_LABEL: Record<string, string> = {
  UNUSED: '未使用',
  USED: '已使用',
  EXPIRED: '已过期',
};

/** 用户券状态配色 */
export const COUPON_USER_STATUS_COLOR: Record<string, string> = {
  UNUSED: 'green',
  USED: 'default',
  EXPIRED: 'red',
};

/**
 * 券面额的展示文案。
 *
 * <p>折扣率在库里存的是 0.85 这种小数，直接展示给买家会得到一个看不懂的数字，
 * 统一折算成「8.5 折」。</p>
 */
export function formatCouponValue(coupon: {
  couponType: string;
  faceAmount?: number;
  discountRate?: number;
  maxDiscountAmount?: number;
  currencyType?: string;
}): string {
  if (coupon.couponType === 'DISCOUNT') {
    const rate = coupon.discountRate ?? 1;
    const discount = `${(rate * 10).toFixed(1).replace(/\.0$/, '')} 折`;
    return coupon.maxDiscountAmount
      ? `${discount}（最高减 ${formatMoney(coupon.maxDiscountAmount, coupon.currencyType)}）`
      : discount;
  }
  return formatMoney(coupon.faceAmount ?? 0, coupon.currencyType);
}

package com.bluewhale.mall.order.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 商城订单。
 *
 * <p>状态流转全部收在这个对象里（{@link #pay} / {@link #cancel} / {@link #ship} /
 * {@link #complete}）：如果让应用服务直接 {@code setStatus}，状态机就退化成一句口头约定，
 * 半年后一定有人从「已完成」倒退到「待支付」。</p>
 *
 * <p>渠道形态、结算方式、币种三项都<b>落快照</b>：渠道日后改配置，历史订单的金额口径
 * 不能跟着变，否则一笔 100 积分的旧单会突然变成 100 元。</p>
 *
 * @author BlueWhale
 */
@Data
public class MallOrder {

    /** 订单号最大长度 */
    public static final int ORDER_NO_MAX_LENGTH = 32;

    /** 幂等键最大长度 */
    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 最小应付金额：优惠最多把应付打到 0.01，支付侧 amount 必须大于 0 */
    public static final BigDecimal MIN_PAYABLE_AMOUNT = new BigDecimal("0.01");

    /** 主键 */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 渠道 ID */
    private Long channelId;

    /** 渠道编码（快照） */
    private String channelCode;

    /** 形态（快照） */
    private MallMode mode;

    /** 结算方式（快照） */
    private SettlementType settlementType;

    /** 金额口径（快照） */
    private CurrencyType currencyType;

    /** 买家用户 ID */
    private Long buyerUserId;

    /** 买家名称（快照） */
    private String buyerName;

    /** 状态 */
    private OrderStatus status;

    /** 订单总额（应付 = 商品总额 - 优惠 + 运费） */
    private BigDecimal totalAmount;

    /** 商品总额（各行小计之和，不含优惠与运费；活动/会员价已体现在成交单价中） */
    private BigDecimal goodsAmount;

    /** 原价合计（Σ原单价×数量，未含活动与会员让利；对账口径） */
    private BigDecimal listAmount;

    /** 活动与会员价让利合计（list_amount - goods_amount，对账口径） */
    private BigDecimal activityDiscount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 使用的用户券 ID（主券快照：单券订单即该券；多券订单为首张券） */
    private Long couponUserId;

    /** 使用的券编码（主券快照，含义同 couponUserId） */
    private String couponCode;

    /** 商品总件数 */
    private Integer totalQuantity;

    /** 明细行数 */
    private Integer itemCount;

    /** 幂等键 */
    private String idempotencyKey;

    /** 支付单号 */
    private String payNo;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 累计退款金额 */
    private BigDecimal refundAmount;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 发货时间 */
    private LocalDateTime shipTime;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 取消原因 */
    private String cancelReason;

    /** 待支付超时时间 */
    private LocalDateTime expireTime;

    /** 买家备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细 */
    private List<OrderItem> items = new ArrayList<>();

    /** 本单用券明细（一单最多 2 张；单券订单与老数据只有首张一条） */
    private List<OrderCoupon> coupons = new ArrayList<>();

    /**
     * 按明细重算金额与件数（无优惠、无运费）。
     *
     * <p>金额只认明细：让前端传总额、或者让订单自己记一个数，
     * 早晚会出现「总额 100、明细加起来 98」的对不上账。</p>
     */
    public void recalculate() {
        recalculate(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 按明细重算金额构成，并把优惠与运费分摊到行。
     *
     * <p>应付 = 商品总额 - 优惠 + 运费。优惠封顶在「商品总额 - 0.01」，
     * 不是贪小便宜：whale-pay 侧 {@code amount > 0} 是硬约束，
     * 应付算成 0 会让支付单直接创建失败，而用户看到的只是一个莫名其妙的错误。</p>
     *
     * <p>{@code total_amount} 的含义从此是<b>应付</b>：支付单金额、退款额度、
     * 前端展示继续读它，不需要任何迁移改值——无券无运费时它与商品总额相等。</p>
     *
     * @param discount 优惠金额（可为空）
     * @param freight  运费（可为空）
     */
    public void recalculate(BigDecimal discount, BigDecimal freight) {
        BigDecimal goods = computeLineAmounts();
        BigDecimal appliedDiscount = capDiscount(discount, goods);
        BigDecimal appliedFreight = normalizeFreight(freight);
        allocate(appliedDiscount, goods, OrderItem::setShareDiscount);
        allocate(appliedFreight, goods, OrderItem::setShareFreight);
        this.goodsAmount = goods;
        this.discountAmount = appliedDiscount;
        this.freightAmount = appliedFreight;
        this.totalAmount = goods.subtract(appliedDiscount).add(appliedFreight);
    }

    /**
     * 按多张券叠加重算金额构成，并把各券优惠按适用范围分摊到行。
     *
     * <p>口径四条，缺一不可：</p>
     * <ol>
     * <li>门槛按各自适用范围判定：全场/渠道券的分母是整单商品小计，
     * 品类券的分母是命中类目商品行小计——其他商品凑单不能替品类券抬门槛；</li>
     * <li>优惠只摊到命中行：品类券的优惠如果摊给全部行，「退掉未命中商品行」
     * 时该退多少就永远算不清了；</li>
     * <li>合计优惠封顶在「商品总额 - 0.01」：超出时按比例缩减，
     * 尾差挂在最后一张券上，保证 Σ各券优惠 = discount_amount 这个恒等式成立；</li>
     * <li>各券优惠累加写入行分摊（shareDiscount），而不是覆盖——
     * 两张券同时命中一行时，行分摊是两笔之和。</li>
     * </ol>
     *
     * @param coupons 本单用券明细（每张必须带计价规则）
     * @param freight 运费（可为空）
     */
    public void recalculateWithCoupons(List<OrderCoupon> coupons, BigDecimal freight) {
        BigDecimal goods = computeLineAmounts();
        BigDecimal appliedFreight = normalizeFreight(freight);
        // 1. 逐券按适用范围算门槛分母与优惠
        BigDecimal rawTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (OrderCoupon coupon : coupons) {
            if (coupon.getRule() == null) {
                throw MallErrorCode.exception(MallErrorCode.ORDER_AMOUNT_INVALID,
                        "用券明细缺少计价规则：" + coupon.getCouponCode());
            }
            BigDecimal matched = matchedGoods(coupon, goods);
            if (!coupon.getRule().meetsThreshold(matched)) {
                throw MallErrorCode.exception(MallErrorCode.COUPON_THRESHOLD_NOT_MET,
                        "订单未达到优惠券使用门槛：" + coupon.getCouponName());
            }
            BigDecimal discount = coupon.getRule().discountFor(matched);
            coupon.setDiscountAmount(discount);
            rawTotal = rawTotal.add(discount);
        }
        // 2. 合计封顶：超出按比例缩减，前 n-1 张向下取整、末张吃尾差
        BigDecimal cap = goods.subtract(MIN_PAYABLE_AMOUNT);
        if (rawTotal.compareTo(cap) > 0) {
            shrinkToCap(coupons, rawTotal, cap);
        }
        // 3. 分摊：品类券只摊命中行，其余摊全部行；运费仍按全部行分摊
        resetShareDiscount();
        for (OrderCoupon coupon : coupons) {
            allocateCoupon(coupon, goods);
        }
        allocate(appliedFreight, goods, OrderItem::setShareFreight);
        BigDecimal totalDiscount = coupons.stream()
                .map(coupon -> coupon.getDiscountAmount() == null
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : coupon.getDiscountAmount())
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        this.goodsAmount = goods;
        this.discountAmount = totalDiscount;
        this.freightAmount = appliedFreight;
        this.totalAmount = goods.subtract(totalDiscount).add(appliedFreight);
        this.totalQuantity = totalQuantity();
        this.itemCount = items.size();
        this.coupons = coupons;
    }

    /**
     * 计算各明细行小计并汇总商品总额（叠加计价与单券计价共用的行级口径）。
     *
     * @return 商品总额
     */
    private BigDecimal computeLineAmounts() {
        BigDecimal goods = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal list = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int quantity = 0;
        for (OrderItem item : items) {
            if (item.getUnitPrice() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw MallErrorCode.exception(MallErrorCode.ORDER_AMOUNT_INVALID, "订单明细价格或数量不合法");
            }
            BigDecimal amount = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setAmount(amount);
            goods = goods.add(amount);
            // 原价快照缺失（老数据/未覆盖）按成交价计，让 list_amount 恒等 goods_amount
            BigDecimal listPrice = item.getListPrice() == null ? item.getUnitPrice() : item.getListPrice();
            list = list.add(listPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP));
            quantity += item.getQuantity();
        }
        if (goods.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_AMOUNT_INVALID, "订单金额必须大于 0");
        }
        this.totalQuantity = quantity;
        this.itemCount = items.size();
        this.listAmount = list;
        this.activityDiscount = list.subtract(goods).setScale(2, RoundingMode.HALF_UP);
        return goods;
    }

    /**
     * 订单重量合计（kg，单件重量快照 × 数量；行重量缺失按 0 计）。
     *
     * @return 重量合计，两位小数
     */
    public BigDecimal totalWeightKg() {
        BigDecimal weight = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        for (OrderItem item : items) {
            if (item.getWeightKg() == null || item.getQuantity() == null) {
                continue;
            }
            weight = weight.add(item.getWeightKg()
                    .multiply(BigDecimal.valueOf(item.getQuantity())))
                    .setScale(3, RoundingMode.HALF_UP);
        }
        return weight;
    }

    /**
     * 本单商品总件数。
     *
     * @return 件数之和
     */
    private int totalQuantity() {
        int quantity = 0;
        for (OrderItem item : items) {
            quantity += item.getQuantity() == null ? 0 : item.getQuantity();
        }
        return quantity;
    }

    /**
     * 计算一张券适用范围内的商品小计（门槛分母与优惠基数）。
     *
     * @param coupon 用券明细
     * @param goods  整单商品小计
     * @return 适用范围商品小计
     */
    private BigDecimal matchedGoods(OrderCoupon coupon, BigDecimal goods) {
        if (!coupon.isSkuScoped() && !coupon.isCategoryScoped()) {
            return goods;
        }
        List<OrderItem> targets = couponTargets(coupon, goods);
        BigDecimal matched = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (OrderItem item : targets) {
            matched = matched.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
        }
        return matched;
    }

    /**
     * 求一张券的命中行（商品券按 SKU、品类券按类目、全场/渠道券为全部行）。
     *
     * @param coupon 用券明细
     * @param goods  整单商品小计（全场/渠道券命中全部行）
     * @return 命中行列表
     */
    private List<OrderItem> couponTargets(OrderCoupon coupon, BigDecimal goods) {
        List<OrderItem> targets = new ArrayList<>();
        if (coupon.isSkuScoped()) {
            Set<Long> scoped = coupon.skuIdSet();
            for (OrderItem item : items) {
                if (item.getSkuId() != null && scoped.contains(item.getSkuId())) {
                    targets.add(item);
                }
            }
            return targets;
        }
        if (coupon.isCategoryScoped()) {
            Set<Long> scoped = coupon.categoryIdSet();
            for (OrderItem item : items) {
                if (item.getCategoryId() != null && scoped.contains(item.getCategoryId())) {
                    targets.add(item);
                }
            }
            return targets;
        }
        targets.addAll(items);
        return targets;
    }

    /**
     * 求一张券的分摊基数（命中行小计之和；全场/渠道券为整单商品小计）。
     *
     * @param coupon  用券明细
     * @param targets 命中行
     * @param goods   整单商品小计
     * @return 分摊基数
     */
    private BigDecimal matchedBase(OrderCoupon coupon, List<OrderItem> targets, BigDecimal goods) {
        if (!coupon.isSkuScoped() && !coupon.isCategoryScoped()) {
            return goods;
        }
        BigDecimal base = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (OrderItem item : targets) {
            base = base.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
        }
        return base;
    }

    /**
     * 多券合计优惠超出封顶时按比例缩减。
     *
     * <p>前 n-1 张按「单券优惠 × 缩减系数」向下取整，最后一张拿
     * 「封顶 - 前面之和」：分摊误差全部落在一张券上，
     * 明细合计才始终等于订单优惠额。</p>
     *
     * @param coupons  用券明细
     * @param rawTotal 原始合计优惠
     * @param cap      封顶金额
     */
    private void shrinkToCap(List<OrderCoupon> coupons, BigDecimal rawTotal, BigDecimal cap) {
        BigDecimal scaled = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int index = 0; index < coupons.size(); index++) {
            OrderCoupon coupon = coupons.get(index);
            if (index == coupons.size() - 1) {
                coupon.setDiscountAmount(cap.subtract(scaled));
            } else {
                BigDecimal shrunk = coupon.getDiscountAmount()
                        .multiply(cap)
                        .divide(rawTotal, 2, RoundingMode.DOWN);
                coupon.setDiscountAmount(shrunk);
                scaled = scaled.add(shrunk);
            }
        }
    }

    /**
     * 清空各行分摊优惠（叠加计价从零开始累加）。
     */
    private void resetShareDiscount() {
        for (OrderItem item : items) {
            item.setShareDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * 把一张券的优惠分摊到命中行。
     *
     * <p>品类券的分摊基数是命中行小计之和、只写命中行；全场/渠道券的基数是
     * 整单商品小计、写全部行。行内金额前 n-1 行向下取整、末行吃尾差，
     * 保证 Σ行分摊 = 该券优惠额；命中行为空（老数据无类目快照）时按未命中处理，
     * 不抛错——宁可少摊也不能摊错。</p>
     *
     * @param coupon 用券明细（已带最终生效优惠额）
     * @param goods  整单商品小计（全场/渠道券的分摊基数）
     */
    private void allocateCoupon(OrderCoupon coupon, BigDecimal goods) {
        BigDecimal discount = coupon.getDiscountAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : coupon.getDiscountAmount();
        if (discount.signum() <= 0) {
            return;
        }
        List<OrderItem> targets = couponTargets(coupon, goods);
        BigDecimal base = matchedBase(coupon, targets, goods);
        if (targets.isEmpty() || base.signum() <= 0) {
            return;
        }
        BigDecimal remaining = discount;
        for (int index = 0; index < targets.size(); index++) {
            OrderItem item = targets.get(index);
            BigDecimal share;
            if (index == targets.size() - 1) {
                share = remaining;
            } else {
                BigDecimal lineBase = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
                share = discount.multiply(lineBase).divide(base, 2, RoundingMode.DOWN);
                if (share.compareTo(remaining) > 0) {
                    share = remaining;
                }
            }
            remaining = remaining.subtract(share);
            BigDecimal current = item.getShareDiscount() == null
                    ? BigDecimal.ZERO : item.getShareDiscount();
            item.setShareDiscount(current.add(share));
        }
    }

    /**
     * 把优惠金额按行小计比例分摊到每一行。
     *
     * <p>前 n-1 行按比例向下取整，末行吃掉全部尾差。用「向下取整 + 末行兜底」
     * 而不是逐行四舍五入：后者会让分摊合计与订单优惠差几分钱，
     * 于是 {@code total = goods - discount + freight} 这个恒等式在部分订单上不成立，
     * 对账时会变成一桩查不出原因的悬案。</p>
     *
     * @param total  待分摊总额
     * @param goods  商品总额（分摊基数）
     * @param setter 写值动作
     */
    private void allocate(BigDecimal total, BigDecimal goods, BiConsumer<OrderItem, BigDecimal> setter) {
        BigDecimal remaining = total;
        for (int index = 0; index < items.size(); index++) {
            OrderItem item = items.get(index);
            BigDecimal share;
            if (index == items.size() - 1) {
                share = remaining;
            } else {
                BigDecimal base = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
                share = total.multiply(base).divide(goods, 2, RoundingMode.DOWN);
                if (share.compareTo(remaining) > 0) {
                    share = remaining;
                }
            }
            remaining = remaining.subtract(share);
            setter.accept(item, share);
        }
    }

    /**
     * 优惠金额封顶：不低于 0，且应付至少留 0.01。
     *
     * @param discount 优惠金额
     * @param goods    商品总额
     * @return 实际生效的优惠金额
     */
    private BigDecimal capDiscount(BigDecimal discount, BigDecimal goods) {
        BigDecimal value = discount == null
                ? BigDecimal.ZERO : discount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal cap = goods.subtract(MIN_PAYABLE_AMOUNT);
        return value.compareTo(cap) > 0 ? cap : value;
    }

    /**
     * 运费规整：非空、非负、两位小数。
     *
     * @param freight 运费
     * @return 实际生效的运费
     */
    private BigDecimal normalizeFreight(BigDecimal freight) {
        if (freight == null || freight.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return freight.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 支付（幂等由调用方保证「已支付直接返回」）。
     *
     * @param payNo 支付单号
     * @param paidAt 支付时间
     */
    public void pay(String payNo, LocalDateTime paidAt) {
        if (!status.canPay()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "订单当前状态（" + status + "）不允许支付");
        }
        if (payNo == null || payNo.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.PAY_CALLBACK_INVALID, "支付单号不能为空");
        }
        this.payNo = payNo.trim();
        this.payTime = paidAt == null ? LocalDateTime.now() : paidAt;
        this.status = OrderStatus.PAID;
    }

    /**
     * 取消。
     *
     * @param reason 取消原因
     * @param cancelledAt 取消时间
     */
    public void cancel(String reason, LocalDateTime cancelledAt) {
        if (!status.canCancel()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "订单当前状态（" + status + "）不允许取消；已支付订单请走退款流程");
        }
        this.cancelReason = reason == null ? null : reason.trim();
        this.cancelTime = cancelledAt == null ? LocalDateTime.now() : cancelledAt;
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 发货。
     *
     * <p>幂等：已是 {@code SHIPPED} 时直接返回 {@code false}，<b>不重写 ship_time</b>。
     * 一个订单拆成多个包裹分批发货，第二张发货单会再次走到这里；重写时间会让
     * 「订单发货时间」变成最后一个包裹的发出时间，与买家看到的第一个包裹对不上。</p>
     *
     * <p>其余状态一律拒绝：待支付没收到钱不能发、退款中不能发（钱正在退）、
     * 已完成不能再补发（否则库存与物流记录都会对不上）。</p>
     *
     * @param shippedAt 发货时间
     * @return 本次是否发生状态变更
     */
    public boolean ship(LocalDateTime shippedAt) {
        if (status == OrderStatus.SHIPPED) {
            return false;
        }
        if (!status.canShip()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "订单当前状态（" + status + "）不允许发货");
        }
        this.shipTime = shippedAt == null ? LocalDateTime.now() : shippedAt;
        this.status = OrderStatus.SHIPPED;
        return true;
    }

    /**
     * 发货单全部作废后退回待发货。
     *
     * <p>不退回去的话订单显示「已发货」却一张有效发货单都没有，运营再也发不出去
     * （状态看着像发过了），买家也查不到任何物流。</p>
     */
    public void unShip() {
        if (status != OrderStatus.SHIPPED) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "只有已发货订单才能在发货单全部作废后退回待发货");
        }
        this.status = OrderStatus.PAID;
        this.shipTime = null;
    }

    /**
     * 是否所有行都已发满。
     *
     * <p>签收要驱动完成，前提是货真的全发出去了：只签收了一个包裹、
     * 还有一行没发，此时把订单置为已完成，剩下的货就永远发不出去了
     * （{@code COMPLETED} 不能再发货）。</p>
     *
     * @return 全部行发满返回 true
     */
    public boolean fullyShipped() {
        if (items == null || items.isEmpty()) {
            return false;
        }
        return items.stream().allMatch(item -> item.shippableQuantity() == 0);
    }

    /**
     * 已发总件数。
     *
     * @return 已发件数之和
     */
    public int shippedQuantity() {
        if (items == null) {
            return 0;
        }
        return items.stream()
                .mapToInt(item -> item.getShippedQuantity() == null ? 0 : item.getShippedQuantity())
                .sum();
    }

    /**
     * 完成。
     *
     * @param completedAt 完成时间
     */
    public void complete(LocalDateTime completedAt) {
        if (!status.canComplete()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "订单当前状态（" + status + "）不允许完成");
        }
        this.completeTime = completedAt == null ? LocalDateTime.now() : completedAt;
        this.status = OrderStatus.COMPLETED;
    }

    /**
     * 是否为指定用户的订单。
     *
     * @param userId 用户 ID
     * @return 是该用户的订单返回 true
     */
    public boolean belongsTo(Long userId) {
        return userId != null && userId.equals(buyerUserId);
    }

    /**
     * 申请整单退款：已收款状态 → REFUNDING。
     *
     * <p>REFUNDING 期间不可发货（{@code canShip} 只认 PAID），防「退款中的订单被发出去」。
     * 实际退款金额以 whale-pay 回调为准。</p>
     *
     * <p>结单不在这里做：退款回调要同时结售后单、累计订单退款额、判定是否退满，
     * 三步必须在一个事务里原子完成，放在领域模型上做会把它拆成两次写。
     * 因此由售后仓储的 {@code applyRefund} 一条 SQL 完成累计与状态判定。</p>
     */
    public void requestRefund() {
        if (!status.canRefund()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "订单当前状态（" + status + "）不允许退款");
        }
        this.status = OrderStatus.REFUNDING;
    }
}

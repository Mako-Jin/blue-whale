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

    /** 商品总额（各行小计之和，不含优惠与运费） */
    private BigDecimal goodsAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 使用的用户券 ID（快照） */
    private Long couponUserId;

    /** 使用的券编码（快照） */
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
        BigDecimal goods = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
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
            quantity += item.getQuantity();
        }
        if (goods.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_AMOUNT_INVALID, "订单金额必须大于 0");
        }
        BigDecimal appliedDiscount = capDiscount(discount, goods);
        BigDecimal appliedFreight = normalizeFreight(freight);
        allocate(appliedDiscount, goods, OrderItem::setShareDiscount);
        allocate(appliedFreight, goods, OrderItem::setShareFreight);
        this.goodsAmount = goods;
        this.discountAmount = appliedDiscount;
        this.freightAmount = appliedFreight;
        this.totalAmount = goods.subtract(appliedDiscount).add(appliedFreight);
        this.totalQuantity = quantity;
        this.itemCount = items.size();
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
     * @param shippedAt 发货时间
     */
    public void ship(LocalDateTime shippedAt) {
        if (!status.canShip()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "订单当前状态（" + status + "）不允许发货");
        }
        this.shipTime = shippedAt == null ? LocalDateTime.now() : shippedAt;
        this.status = OrderStatus.SHIPPED;
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

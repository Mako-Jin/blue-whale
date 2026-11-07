package com.bluewhale.mall.order.domain.model;

import java.util.Locale;

/**
 * 订单状态。
 *
 * <pre>
 * PENDING_PAYMENT ──pay──> PAID ──ship──> SHIPPED ──complete──> COMPLETED
 *        │                  │                │                    │
 *        └──cancel──> CANCELLED              └────refund──────────┘
 *                           └──refund──> REFUNDING ──refund-callback──> REFUNDED
 * </pre>
 *
 * <p>只允许这几条边。{@code PAID} 不能取消：钱已收，取消等于退款，
 * 而退款是带审批与流水记录的独立流程，塞进「取消」会让这个动作有时退钱有时不退。</p>
 *
 * <p>{@code REFUNDING} 的语义收窄为「整单退款进行中」（部分退款的订单保持原状态），
 * 期间禁止发货与再次整单退款，防并发双退。部分退款的并发由行级额度条件更新兜住。</p>
 *
 * @author BlueWhale
 */
public enum OrderStatus {

    /** 待支付 */
    PENDING_PAYMENT,

    /** 已支付（待发货） */
    PAID,

    /** 已发货 */
    SHIPPED,

    /** 已完成 */
    COMPLETED,

    /** 已取消 */
    CANCELLED,

    /** 退款中（已向 whale-pay 发起整单退款，待回调确认） */
    REFUNDING,

    /** 已退款（终态） */
    REFUNDED;

    /**
     * 是否可支付。
     *
     * @return 待支付返回 true
     */
    public boolean canPay() {
        return this == PENDING_PAYMENT;
    }

    /**
     * 是否可取消。
     *
     * @return 待支付返回 true
     */
    public boolean canCancel() {
        return this == PENDING_PAYMENT;
    }

    /**
     * 是否可发货。
     *
     * <p>已发货也返回 true：一个订单可以拆成多个包裹分批发出去，第二张发货单
     * 到来时订单已经是 {@code SHIPPED}。真正的幂等判定在
     * {@code MallOrder.ship} 里（已是 SHIPPED 就不再改时间）。</p>
     *
     * @return 已支付或已发货返回 true
     */
    public boolean canShip() {
        return this == PAID || this == SHIPPED;
    }

    /**
     * 是否已发货（含部分发货）。
     *
     * @return 已发货返回 true
     */
    public boolean shipped() {
        return this == SHIPPED;
    }

    /**
     * 是否可完成。
     *
     * @return 已发货返回 true
     */
    public boolean canComplete() {
        return this == SHIPPED;
    }

    /**
     * 是否可申请退款 / 售后。
     *
     * <p>已支付、已发货、已完成都可以：货发出去了买家才发现问题是最常见的售后场景，
     * 只放开「已支付」等于把退货全部堵死。已取消与待支付不可退——这两者没收到钱，
     * 走退款只会把钱凭空送出去。</p>
     *
     * @return 已支付、已发货或已完成返回 true
     */
    public boolean canRefund() {
        return this == PAID || this == SHIPPED || this == COMPLETED;
    }

    /**
     * 是否为终态。
     *
     * @return 已完成、已取消或已退款返回 true
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED;
    }

    /**
     * 解析状态文本。
     *
     * @param value 文本
     * @return 状态；无法识别时返回 null（由调用方判定为非法入参）
     */
    public static OrderStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

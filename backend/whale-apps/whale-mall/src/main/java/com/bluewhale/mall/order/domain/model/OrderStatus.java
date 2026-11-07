package com.bluewhale.mall.order.domain.model;

import java.util.Locale;

/**
 * 订单状态。
 *
 * <pre>
 * PENDING_PAYMENT ──pay──> PAID ──ship──> SHIPPED ──complete──> COMPLETED
 *        │                  │
 *        └──cancel──> CANCELLED    └──refund──> REFUNDING ──refund-callback──> REFUNDED
 * </pre>
 *
 * <p>只允许这六条边。{@code PAID} 不能取消：钱已收，取消等于退款，
 * 而退款是带审批与流水记录的独立流程，塞进「取消」会让这个动作有时退钱有时不退。
 * {@code SHIPPED} 不能取消：货在路上，逆向走售后（M1 后续切片）。
 * {@code REFUNDING} 期间禁止发货与再次申请退款，防并发双退。</p>
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
     * @return 已支付返回 true
     */
    public boolean canShip() {
        return this == PAID;
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
     * 是否可申请整单退款。
     *
     * @return 已支付返回 true
     */
    public boolean canRefund() {
        return this == PAID;
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

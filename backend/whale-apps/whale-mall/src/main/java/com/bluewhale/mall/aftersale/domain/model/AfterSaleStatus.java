package com.bluewhale.mall.aftersale.domain.model;

import java.util.Locale;

/**
 * 售后单状态。
 *
 * <pre>
 * CREATED ──approve──> APPROVED ──refund-callback──> REFUNDED
 *    │
 *    ├──reject──> REJECTED
 *    └──cancel──> CANCELLED
 * </pre>
 *
 * <p>{@code APPROVED} 就是「已审核通过、退款执行中」，不再单设 REFUNDING：
 * 退款的真实进度由支付侧 {@code pay_refund.status} 表达，售后单再加一层中间态
 * 只会让两个状态机对不齐——回调到达时到底该改哪一个，很容易写错。</p>
 *
 * <p>只有 {@code CREATED} 可以撤销和审核。已通过的售后单不能撤销：
 * 钱已经在路上，撤回只能靠再发起一笔反向流程，静默把它置成「已撤销」会让账面凭空多出钱。</p>
 *
 * @author BlueWhale
 */
public enum AfterSaleStatus {

    /** 待审核 */
    CREATED,

    /** 审核通过（退款执行中） */
    APPROVED,

    /** 已退款 */
    REFUNDED,

    /** 已驳回 */
    REJECTED,

    /** 已撤销 */
    CANCELLED;

    /**
     * 是否可审核（通过或驳回）。
     *
     * @return 待审核返回 true
     */
    public boolean canAudit() {
        return this == CREATED;
    }

    /**
     * 是否可撤销。
     *
     * @return 待审核返回 true
     */
    public boolean canCancel() {
        return this == CREATED;
    }

    /**
     * 是否为终态。
     *
     * @return 已退款、已驳回或已撤销返回 true
     */
    public boolean isFinal() {
        return this == REFUNDED || this == REJECTED || this == CANCELLED;
    }

    /**
     * 解析状态文本。
     *
     * @param value 文本
     * @return 状态；无法识别时返回 null（由调用方判定为非法入参）
     */
    public static AfterSaleStatus parse(String value) {
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

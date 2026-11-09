package com.bluewhale.mall.aftersale.domain.model;

import java.util.Locale;

/**
 * 售后单状态。
 *
 * <pre>
 * 仅退款 REFUND_ONLY:
 *   CREATED ──approve──> APPROVED ──refund-callback──> REFUNDED
 * 退货退款 RETURN_REFUND:
 *   CREATED ──approve──> WAIT_RETURN ──fill-return-logistics──> WAIT_RETURN
 *                        WAIT_RETURN ──confirm-return──> APPROVED ──refund-callback──> REFUNDED
 * 换货 EXCHANGE:
 *   CREATED ──approve──> WAIT_RETURN ──confirm-return──> EXCHANGING
 *                        ──exchange-shipment──> EXCHANGED
 * 旁支：reject（CREATED|WAIT_RETURN）→ REJECTED；cancel / 超时自动关闭（仅 CREATED，
 *       换货在 WAIT_RETURN 也可撤销）→ CANCELLED
 * </pre>
 *
 * <p>{@code APPROVED} 就是「已审核通过、退款执行中」，不再单设 REFUNDING：
 * 退款的真实进度由支付侧 {@code pay_refund.status} 表达，售后单再加一层中间态
 * 只会让两个状态机对不齐——回调到达时到底该改哪一个，很容易写错。</p>
 *
 * <p>{@code WAIT_RETURN} 是退货类（退货退款 / 换货）审核通过与最终结单之间的等待位：
 * 钱和货只能先动一个，买家还没把货寄回来就发起退款，等于凭空多退一份。
 * 存量兼容：上线前 RETURN_REFUND 已 APPROVED 的在途单仍按原路径由回调结单。</p>
 *
 * @author BlueWhale
 */
public enum AfterSaleStatus {

    /** 待审核 */
    CREATED,

    /** 待退货（退货类审核通过，等买家寄回商品） */
    WAIT_RETURN,

    /** 审核通过（退款执行中） */
    APPROVED,

    /** 已退款 */
    REFUNDED,

    /** 换货补发中（已收到退货，待运营建补发发货单） */
    EXCHANGING,

    /** 已换货（终态） */
    EXCHANGED,

    /** 已驳回 */
    REJECTED,

    /** 已撤销 */
    CANCELLED;

    /**
     * 是否可审核（通过或驳回）。
     *
     * @return 待审核或待退货返回 true（待退货只允许驳回）
     */
    public boolean canAudit() {
        return this == CREATED || this == WAIT_RETURN;
    }

    /**
     * 是否可撤销。
     *
     * <p>只认待审核；换货单在待退货阶段是否可撤由聚合根结合类型判断
     * （换货不占退款额度，撤了不留悬账）。</p>
     *
     * @return 待审核返回 true
     */
    public boolean canCancel() {
        return this == CREATED;
    }

    /**
     * 是否可回填退货物流。
     *
     * @return 待退货返回 true
     */
    public boolean canFillReturnLogistics() {
        return this == WAIT_RETURN;
    }

    /**
     * 是否为终态。
     *
     * @return 已退款、已换货、已驳回或已撤销返回 true
     */
    public boolean isFinal() {
        return this == REFUNDED || this == EXCHANGED || this == REJECTED || this == CANCELLED;
    }

    /**
     * 是否属于换货进行中状态（换货查重用：同一行存在这些状态的换货单则拒绝再换）。
     *
     * @return 待审核、待退货或换货补发中返回 true
     */
    public boolean isActiveExchangeState() {
        return this == CREATED || this == WAIT_RETURN || this == EXCHANGING;
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

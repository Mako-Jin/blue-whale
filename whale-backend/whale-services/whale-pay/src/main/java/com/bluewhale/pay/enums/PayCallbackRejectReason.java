package com.bluewhale.pay.enums;

/**
 * 渠道回调拒绝原因（落 {@code pay_channel_callback_log.reject_reason}）。
 *
 * <p>设计定稿列了前四项；{@link #REFUND_NOT_FOUND} 与 {@link #EVENT_UNSUPPORTED}
 * 是退款定位与未知事件留痕所需的补充，均只写日志、不推进资金状态。</p>
 *
 * @author BlueWhale
 */
public enum PayCallbackRejectReason {

    /** 验签或报文解析失败 */
    SIGN_INVALID("SIGN_INVALID"),

    /** 渠道金额/币种与本地不符 */
    AMOUNT_MISMATCH("AMOUNT_MISMATCH"),

    /** 支付单不存在或跨租户重复无法定位 */
    ORDER_NOT_FOUND("ORDER_NOT_FOUND"),

    /** 退款单不存在或不属于该支付单 */
    REFUND_NOT_FOUND("REFUND_NOT_FOUND"),

    /** 本地状态不允许该事件推进 */
    STATUS_CONFLICT("STATUS_CONFLICT"),

    /** 渠道事件不在支持范围内 */
    EVENT_UNSUPPORTED("EVENT_UNSUPPORTED");

    private final String code;

    /**
     * 构造拒绝原因。
     *
     * @param code 原因码
     */
    PayCallbackRejectReason(String code) {
        this.code = code;
    }

    /**
     * 原因码。
     *
     * @return 原因码
     */
    public String code() {
        return code;
    }
}

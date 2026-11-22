package com.bluewhale.pay.enums;

/**
 * 支付单关单原因（{@code pay_order.close_reason} 的唯一取值来源）。
 *
 * <p>只有 {@code CANCELLED} 状态的支付单会带关单原因，用于区分「人工取消」、
 * 「超时自动关单」与「渠道侧关单」三类资金动作，便于对账与运营归因。</p>
 *
 * @author BlueWhale
 */
public enum PayCloseReason {

    /** 超过支付有效期未支付，由定时任务自动关单 */
    TIMEOUT("TIMEOUT"),

    /** 人工取消 */
    MANUAL("MANUAL"),

    /** 渠道回调关单 */
    CHANNEL_CLOSED("CHANNEL_CLOSED");

    private final String code;

    /**
     * 构造关单原因。
     *
     * @param code 落库字符串
     */
    PayCloseReason(String code) {
        this.code = code;
    }

    /**
     * 落库字符串。
     *
     * @return 关单原因
     */
    public String code() {
        return code;
    }
}

package com.bluewhale.pay.enums;

/**
 * 业务通知事件。
 *
 * @author BlueWhale
 */
public enum PayNotifyEvent {

    /** 支付成功 */
    PAID("PAID"),

    /** 退款成功 */
    REFUNDED("REFUNDED");

    private final String code;

    /**
     * 构造事件。
     *
     * @param code 事件码
     */
    PayNotifyEvent(String code) {
        this.code = code;
    }

    /**
     * 事件码
     *
     * @return 事件码
     */
    public String code() {
        return code;
    }
}

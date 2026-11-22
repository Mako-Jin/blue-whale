package com.bluewhale.pay.enums;

import java.util.Locale;

/**
 * 渠道回调事件。
 *
 * <p>只覆盖当前资金闭环需要的事件：支付成功、退款成功、关单；
 * 渠道返回未知事件时归一为 {@link #UNKNOWN}，由回调服务拒绝并留痕。</p>
 *
 * @author BlueWhale
 */
public enum PayCallbackEvent {

    /** 支付成功 */
    PAID("PAID"),

    /** 退款成功 */
    REFUNDED("REFUNDED"),

    /** 渠道关单 */
    CLOSED("CLOSED"),

    /** 未知事件 */
    UNKNOWN("UNKNOWN");

    private final String code;

    /**
     * 构造事件。
     *
     * @param code 事件码
     */
    PayCallbackEvent(String code) {
        this.code = code;
    }

    /**
     * 事件码。
     *
     * @return 事件码
     */
    public String code() {
        return code;
    }

    /**
     * 按事件码解析，忽略大小写；空值与未知值返回 {@link #UNKNOWN}。
     *
     * @param code 事件码
     * @return 事件枚举
     */
    public static PayCallbackEvent fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (PayCallbackEvent event : values()) {
            if (event.code.equals(normalized)) {
                return event;
            }
        }
        return UNKNOWN;
    }
}

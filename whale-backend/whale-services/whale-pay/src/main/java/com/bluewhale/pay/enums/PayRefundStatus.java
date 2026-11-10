package com.bluewhale.pay.enums;

/**
 * 退款单状态。
 *
 * @author BlueWhale
 */
public enum PayRefundStatus {

    /** 已创建（渠道处理中） */
    CREATED("CREATED"),

    /** 退款成功 */
    SUCCESS("SUCCESS"),

    /** 退款失败 */
    FAILED("FAILED");

    private final String code;

    /**
     * 构造状态。
     *
     * @param code 状态码
     */
    PayRefundStatus(String code) {
        this.code = code;
    }

    /**
     * 状态码
     *
     * @return 状态码
     */
    public String code() {
        return code;
    }
}

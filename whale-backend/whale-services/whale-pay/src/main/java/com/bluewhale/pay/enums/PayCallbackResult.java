package com.bluewhale.pay.enums;

/**
 * 渠道回调处理结果（落 {@code pay_channel_callback_log.result}）。
 *
 * @author BlueWhale
 */
public enum PayCallbackResult {

    /** 本次完成状态推进或幂等确认 */
    PROCESSED("PROCESSED"),

    /** 重发回调，已有终态记录，未重复推进 */
    DUPLICATE("DUPLICATE"),

    /** 拒绝（验签失败 / 金额不符 / 单号不存在 / 状态冲突等） */
    REJECTED("REJECTED"),

    /** 处理异常（初始占位或未预期异常），可被渠道重发重新处理 */
    FAILED("FAILED");

    private final String code;

    /**
     * 构造结果。
     *
     * @param code 结果码
     */
    PayCallbackResult(String code) {
        this.code = code;
    }

    /**
     * 结果码。
     *
     * @return 结果码
     */
    public String code() {
        return code;
    }
}

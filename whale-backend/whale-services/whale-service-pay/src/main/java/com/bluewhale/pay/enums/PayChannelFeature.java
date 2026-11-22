package com.bluewhale.pay.enums;

/**
 * 支付渠道能力声明。
 *
 * <p>适配器通过 {@link com.bluewhale.pay.channel.PayChannelAdapter#features()} 声明自身能力，
 * 业务侧据此决定「能否部分退款」「能否主动关单」「回调是否异步」等行为，
 * 避免为每个渠道在服务里写 {@code if (channel == ...)} 分支。</p>
 *
 * @author BlueWhale
 */
public enum PayChannelFeature {

    /** 支持部分退款：单笔退款金额可小于剩余可退金额 */
    PARTIAL_REFUND("PARTIAL_REFUND"),

    /** 支持退款结果查询：退款受理后可主动查询最终状态 */
    REFUND_QUERY("REFUND_QUERY"),

    /** 支付结果以异步回调为准（同步返回仅代表受理） */
    ASYNC_CALLBACK("ASYNC_CALLBACK"),

    /** 支持渠道主动关单 */
    CLOSE("CLOSE");

    private final String code;

    /**
     * 构造能力。
     *
     * @param code 能力编码
     */
    PayChannelFeature(String code) {
        this.code = code;
    }

    /**
     * 能力编码（返回给前端）。
     *
     * @return 能力编码
     */
    public String code() {
        return code;
    }
}

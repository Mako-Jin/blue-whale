package com.bluewhale.pay.channel;

/**
 * 渠道动作结果。
 *
 * <p>渠道明确失败时不返回本对象，适配器直接抛异常；因此这里只需表达
 * 「即时成功」与「已受理」两种可落账状态。状态查询走
 * {@link PayChannelAdapter#queryStatus}。</p>
 *
 * @param providerTradeNo 渠道流水号（受理中可能为空）
 * @param status          渠道结果状态
 * @author BlueWhale
 */
public record PayChannelResult(String providerTradeNo, PayChannelStatus status) {

    /**
     * 规范化：状态缺省视为即时成功，兼容既有适配器只回传流水号的写法。
     */
    public PayChannelResult {
        status = status == null ? PayChannelStatus.SUCCESS : status;
    }

    /**
     * 兼容构造：只有流水号时视为即时成功。
     *
     * @param providerTradeNo 渠道流水号
     */
    public PayChannelResult(String providerTradeNo) {
        this(providerTradeNo, PayChannelStatus.SUCCESS);
    }

    /**
     * 受理中：渠道已接单，结果待回调确认。
     *
     * @param providerTradeNo 渠道流水号（可能为空）
     * @return 受理中的渠道结果
     */
    public static PayChannelResult accepted(String providerTradeNo) {
        return new PayChannelResult(providerTradeNo, PayChannelStatus.ACCEPTED);
    }

    /**
     * 是否即时成功。
     *
     * @return true 表示可立即落账
     */
    public boolean isSuccess() {
        return PayChannelStatus.SUCCESS == status;
    }
}

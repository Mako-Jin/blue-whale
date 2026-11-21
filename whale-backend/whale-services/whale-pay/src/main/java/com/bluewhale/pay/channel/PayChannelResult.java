package com.bluewhale.pay.channel;

/**
 * 渠道动作结果。
 *
 * <p>支付/退款方法正常返回即代表渠道成功，状态查询走
 * {@link PayChannelAdapter#queryStatus}，因此这里只保留渠道流水号。</p>
 *
 * @param providerTradeNo 渠道流水号
 * @author BlueWhale
 */
public record PayChannelResult(String providerTradeNo) {
}

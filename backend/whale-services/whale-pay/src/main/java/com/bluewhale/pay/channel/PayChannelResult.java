package com.bluewhale.pay.channel;

/**
 * 渠道动作结果。
 *
 * @param providerTradeNo 渠道流水号
 * @param channelStatus   渠道侧状态
 * @author BlueWhale
 */
public record PayChannelResult(String providerTradeNo, String channelStatus) {
}

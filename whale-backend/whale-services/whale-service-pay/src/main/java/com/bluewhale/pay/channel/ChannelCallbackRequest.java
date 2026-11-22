package com.bluewhale.pay.channel;

import com.bluewhale.pay.enums.PayCallbackEvent;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 渠道回调解析结果（与具体渠道报文解耦）。
 *
 * <p>由 {@link PayChannelAdapter#parseCallback(String, Map)} 产出：适配器负责验签与
 * 报文解析，失败直接抛业务异常；本对象只承载后续状态推进需要的最小字段。
 * {@code refundNo} 是商户退款单号，退款回调用于定位 {@code pay_refund}，支付回调为空。</p>
 *
 * @param channel        渠道编码
 * @param channelTradeNo 渠道流水号（幂等键组成部分）
 * @param outTradeNo     商户支付单号（= {@code pay_order.order_no}）
 * @param refundNo       商户退款单号（REFUNDED 事件使用，可为空）
 * @param event          回调事件
 * @param amount         渠道金额
 * @param currency       渠道币种
 * @param rawBody        原始报文（已截断，用于留痕）
 * @param headers        原始请求头（只读）
 * @author BlueWhale
 */
public record ChannelCallbackRequest(
        String channel,
        String channelTradeNo,
        String outTradeNo,
        String refundNo,
        PayCallbackEvent event,
        BigDecimal amount,
        String currency,
        String rawBody,
        Map<String, String> headers
) {
}

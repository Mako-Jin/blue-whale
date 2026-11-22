package com.bluewhale.pay.channel;

import java.math.BigDecimal;

/**
 * 渠道账单条目（批量对账取数）。
 *
 * <p>只承载对账比对所需的最小字段：商户单号用于与本地 {@code pay_order.order_no} 对齐，
 * 渠道流水号与金额用于核对，状态预留给渠道返回的非支付成功账单行。</p>
 *
 * @param orderNo         商户支付单号（对齐 {@code pay_order.order_no}）
 * @param channelTradeNo  渠道流水号
 * @param amount          渠道金额
 * @param currency        渠道币种
 * @param status          渠道状态（缺省按已支付处理）
 * @author BlueWhale
 */
public record ChannelBillItem(String orderNo, String channelTradeNo,
                              BigDecimal amount, String currency, String status) {
}

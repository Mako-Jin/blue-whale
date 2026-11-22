package com.bluewhale.pay.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单视图（与支付服务内网返回字段对齐，独立于服务端 VO）。
 *
 * @param id            支付单 ID
 * @param orderNo       支付单号
 * @param businessKey   业务单号
 * @param amount        金额
 * @param currency      币种
 * @param channel       渠道编码
 * @param status        状态：CREATED / PAYING / PAID / CANCELLED / REFUNDED
 * @param payTime       支付时间
 * @param channelTradeNo 渠道流水号
 * @param refundAmount  累计退款额
 * @param refundTime    退款完成时间
 * @param remark        备注
 * @param createTime    创建时间
 * @param updateTime    更新时间
 * @author BlueWhale
 */
public record PayOrderView(Long id, String orderNo, String businessKey, BigDecimal amount,
                           String currency, String channel, String status, LocalDateTime payTime,
                           String channelTradeNo, BigDecimal refundAmount, LocalDateTime refundTime,
                           String remark, LocalDateTime createTime, LocalDateTime updateTime) {
}

package com.bluewhale.pay.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单视图（与支付服务内网返回字段对齐，独立于服务端 VO）。
 *
 * @param id             退款单 ID
 * @param refundNo       退款单号
 * @param payOrderId     支付单 ID
 * @param payOrderNo     支付单号
 * @param amount         退款金额
 * @param reason         退款原因
 * @param status         状态：CREATED / SUCCESS / FAILED
 * @param channelRefundNo 渠道退款流水号
 * @param refundTime     退款时间
 * @param remark         备注
 * @param createTime     创建时间
 * @author BlueWhale
 */
public record PayRefundView(Long id, String refundNo, Long payOrderId, String payOrderNo,
                            BigDecimal amount, String reason, String status, String channelRefundNo,
                            LocalDateTime refundTime, String remark, LocalDateTime createTime) {
}

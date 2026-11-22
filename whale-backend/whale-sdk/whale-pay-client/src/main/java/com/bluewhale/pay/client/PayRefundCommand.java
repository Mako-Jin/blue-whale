package com.bluewhale.pay.client;

import java.math.BigDecimal;

/**
 * 退款命令。
 *
 * <p>{@code refundNo} 是幂等键：未传时 SDK 生成并在重试间复用，
 * 跨进程重试仍建议由调用方持久化后传入，避免服务重启后生成新号。</p>
 *
 * @param payOrderNo 支付单号（必填）
 * @param refundNo   业务退款请求号（幂等键，可空）
 * @param amount     退款金额（必填）
 * @param reason     退款原因（可空）
 * @author BlueWhale
 */
public record PayRefundCommand(String payOrderNo, String refundNo, BigDecimal amount, String reason) {
}

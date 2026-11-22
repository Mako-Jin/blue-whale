package com.bluewhale.pay.client;

import java.math.BigDecimal;

/**
 * 创建支付单命令。
 *
 * <p>{@code orderNo} 与 {@code businessKey} 至少传一个：都传时以 {@code orderNo} 为准；
 * 只传 {@code businessKey} 时由 SDK 按 {@code bizType + "-" + businessKey} 生成，
 * 同一业务单重试生成同一个单号，服务端按单号幂等。</p>
 *
 * @param orderNo    支付单号（租户内唯一，可空）
 * @param bizType    业务方类型（如 MALL_ORDER，必填）
 * @param businessKey 业务单号（用于生成 orderNo，可空）
 * @param amount     金额（必填，两位小数）
 * @param currency   币种（可空，服务端默认 CNY）
 * @param channel    渠道编码（可空，服务端默认 MOCK）
 * @param notifyUrl  支付结果通知地址（必填）
 * @param remark     备注（可空）
 * @author BlueWhale
 */
public record PayCreateCommand(String orderNo, String bizType, String businessKey,
                               BigDecimal amount, String currency, String channel,
                               String notifyUrl, String remark) {
}

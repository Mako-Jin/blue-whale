package com.bluewhale.mail.support;

/**
 * 单个收件人的退信明细。
 *
 * @param recipient 被退回的收件人地址（Final-Recipient）
 * @param action DSN Action：failed / delayed / delivered
 * @param statusCode RFC 3463 增强状态码，如 5.1.1
 * @param diagnostic 远端诊断信息（原始 SMTP 应答）
 * @param remoteMta 对端服务器（Remote-MTA）
 * @author BlueWhale
 */
public record BounceRecipient(String recipient, String action, String statusCode,
                              String diagnostic, String remoteMta) {
}

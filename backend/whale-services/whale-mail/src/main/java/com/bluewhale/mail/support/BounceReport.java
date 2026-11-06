package com.bluewhale.mail.support;

import java.util.List;

/**
 * 退信回执（DSN，RFC 3464）解析结果。
 *
 * @param originalMessageId 被退回原邮件的 RFC822 Message-ID（可能为空）
 * @param reportingMta 生成回执的 MTA（判断是哪一跳退回的关键）
 * @param recipients 逐个收件人的退信明细
 * @author BlueWhale
 */
public record BounceReport(String originalMessageId, String reportingMta, List<BounceRecipient> recipients) {

    /**
     * 是否解析出了有效退信明细
     *
     * @return 无收件人明细返回 {@code true}
     */
    public boolean isEmpty() {
        return recipients == null || recipients.isEmpty();
    }
}

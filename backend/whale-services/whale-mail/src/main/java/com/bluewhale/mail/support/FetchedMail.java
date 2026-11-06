package com.bluewhale.mail.support;

import java.time.LocalDateTime;

/**
 * IMAP 拉取到的单封邮件快照（不含正文）。
 *
 * @param uid IMAP UID
 * @param messageId RFC822 Message-ID
 * @param subject 主题
 * @param fromAddr 发件人地址
 * @param fromName 发件人显示名
 * @param toAddrs 收件人
 * @param ccAddrs 抄送
 * @param sentTime 发信时间
 * @param sizeBytes 大小
 * @param seen 已读
 * @param flagged 星标
 * @author BlueWhale
 */
public record FetchedMail(long uid,
                          String messageId,
                          String subject,
                          String fromAddr,
                          String fromName,
                          String toAddrs,
                          String ccAddrs,
                          LocalDateTime sentTime,
                          long sizeBytes,
                          boolean seen,
                          boolean flagged) {
}

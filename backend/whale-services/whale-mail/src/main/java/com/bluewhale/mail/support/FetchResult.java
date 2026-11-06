package com.bluewhale.mail.support;

import java.util.List;

/**
 * 单次 IMAP 拉取结果。
 *
 * @param mails 邮件快照（按 UID 升序）
 * @param uidValidity 服务器返回的 UIDVALIDITY；0 表示无法获取
 * @author BlueWhale
 */
public record FetchResult(List<FetchedMail> mails, long uidValidity) {

    /**
     * 空结果
     *
     * @return 空结果
     */
    public static FetchResult empty() {
        return new FetchResult(List.of(), 0L);
    }
}

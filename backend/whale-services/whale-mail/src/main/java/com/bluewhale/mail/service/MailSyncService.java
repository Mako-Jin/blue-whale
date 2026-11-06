package com.bluewhale.mail.service;

/**
 * 收信同步服务（IMAP → 本地索引）。
 *
 * @author BlueWhale
 */
public interface MailSyncService {

    /**
     * 同步指定账号
     *
     * @param accountId 账号ID
     * @param userId 当前用户ID
     * @return 本次新增的邮件数
     */
    int syncAccount(Long accountId, Long userId);

    /**
     * 同步全部启用同步的账号（定时任务入口，无用户上下文）
     *
     * @return 本次新增的邮件总数
     */
    int syncAll();
}

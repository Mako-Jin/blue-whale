package com.bluewhale.mail.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱账号视图。
 *
 * <p>刻意不包含 {@code secretRef}：凭据引用指向具体配置键，泄露后配合配置中心读权限即可取密码。</p>
 *
 * @author BlueWhale
 */
@Data
public class MailAccountVO {

    /** 账号ID */
    private Long id;

    /** 邮箱地址 */
    private String email;

    /** 发件显示名 */
    private String displayName;

    /** 邮箱域名 */
    private String domain;

    /** 账号类型 */
    private String accountType;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 是否参与定时同步 */
    private Integer syncEnabled;

    /** IMAP 主机 */
    private String imapHost;

    /** IMAP 端口 */
    private Integer imapPort;

    /** SMTP 主机 */
    private String smtpHost;

    /** SMTP 端口 */
    private Integer smtpPort;

    /** 配额（字节） */
    private Long quotaBytes;

    /** 已用（字节） */
    private Long usedBytes;

    /** 最近同步时间 */
    private LocalDateTime lastSyncTime;
}

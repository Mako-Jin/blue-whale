package com.bluewhale.mail.enums;

/**
 * 邮箱文件夹类型：平台稳定编码 + 默认 IMAP 名称。
 *
 * <p>IMAP 名称在不同服务商上不一致（如 {@code Sent} / {@code Sent Items} / {@code INBOX.Sent}），
 * 因此 {@code imapName} 仅作为新建账号时的默认值，实际以服务器列表探测结果为准并落库覆盖。</p>
 *
 * @author BlueWhale
 */
public enum MailFolderType {

    /** 收件箱 */
    INBOX("INBOX", "收件箱", "INBOX", 0),

    /** 草稿箱 */
    DRAFT("DRAFT", "草稿箱", "Drafts", 10),

    /** 已发送 */
    SENT("SENT", "已发送", "Sent", 20),

    /** 垃圾邮件 */
    JUNK("JUNK", "垃圾邮件", "Junk", 30),

    /** 已删除 */
    TRASH("TRASH", "已删除", "Trash", 40),

    /** 自定义文件夹 */
    CUSTOM("CUSTOM", "自定义", null, 90);

    /** 平台稳定编码 */
    private final String code;

    /** 默认展示名 */
    private final String displayName;

    /** 默认 IMAP 名称（CUSTOM 无默认值） */
    private final String defaultImapName;

    /** 默认排序号 */
    private final int sortNo;

    /**
     * 构造器
     *
     * @param code 平台稳定编码
     * @param displayName 默认展示名
     * @param defaultImapName 默认 IMAP 名称
     * @param sortNo 默认排序号
     */
    MailFolderType(String code, String displayName, String defaultImapName, int sortNo) {
        this.code = code;
        this.displayName = displayName;
        this.defaultImapName = defaultImapName;
        this.sortNo = sortNo;
    }

    /**
     * 获取平台稳定编码
     *
     * @return 编码
     */
    public String code() {
        return code;
    }

    /**
     * 获取默认展示名
     *
     * @return 展示名
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 获取默认 IMAP 名称
     *
     * @return IMAP 名称；CUSTOM 返回 {@code null}
     */
    public String defaultImapName() {
        return defaultImapName;
    }

    /**
     * 获取默认排序号
     *
     * @return 排序号
     */
    public int sortNo() {
        return sortNo;
    }

    /**
     * 是否为系统文件夹（不可删除、不参与改名）
     *
     * @return 系统文件夹返回 {@code true}
     */
    public boolean isSystem() {
        return this != CUSTOM;
    }
}

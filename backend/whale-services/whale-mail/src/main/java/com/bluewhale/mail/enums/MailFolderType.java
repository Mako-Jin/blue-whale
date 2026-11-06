package com.bluewhale.mail.enums;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

    /** 服务器命名别名表：层级末段（小写）→ 系统类型 */
    private static final Map<String, MailFolderType> ALIASES = buildAliases();

    /** 平台稳定编码 */
    private final String code;

    /** 默认展示名 */
    private final String displayName;

    /** 默认 IMAP 名称（CUSTOM 无默认值） */
    private final String defaultImapName;

    /** 默认排序号 */
    private final int sortNo;

    /**
     * 构建服务器命名别名表
     *
     * <p>只登记真正等价的叫法：Dovecot 用 {@code Sent}、Exchange 用 {@code Sent Items}、
     * 中文客户端写 {@code 已发送}，三者语义一致；而 {@code Archive}、{@code 归档} 之类的
     * 归档目录各家用得随意，不认作系统文件夹，交给自定义逻辑处理。</p>
     *
     * @return 不可变别名表
     */
    private static Map<String, MailFolderType> buildAliases() {
        Map<String, MailFolderType> map = new HashMap<>();
        register(map, INBOX, "inbox");
        register(map, DRAFT, "drafts", "draft", "草稿", "草稿箱");
        register(map, SENT, "sent", "sent items", "sent messages", "sent mail", "已发送", "已发送邮件", "已送出");
        register(map, JUNK, "junk", "spam", "junk e-mail", "junk email", "bulk mail", "垃圾邮件", "垃圾箱");
        register(map, TRASH, "trash", "deleted", "deleted items", "deleted messages", "已删除", "已删除邮件", "废纸篓", "垃圾桶");
        return Collections.unmodifiableMap(map);
    }

    /**
     * 登记一组别名
     *
     * @param map 目标表
     * @param type 系统类型
     * @param names 别名（小写）
     */
    private static void register(Map<String, MailFolderType> map, MailFolderType type, String... names) {
        for (String name : names) {
            map.put(name, type);
        }
    }

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

    /**
     * 按服务器上的 IMAP 名称识别系统文件夹。
     *
     * <p>同一语义在各服务商上命名不同（{@code Sent} / {@code Sent Items} / {@code 已发送}），
     * 写死一个默认名会导致「已发送」永远同步不到，所以以探测结果为准。识别只看层级末段：
     * {@code INBOX.Sent} 与 {@code Sent} 都认作已发送，而 {@code Work.SentArchive} 不会误判。</p>
     *
     * <p>返回 {@code null} 表示这是自定义文件夹，由调用方决定落库方式。</p>
     *
     * @param imapName IMAP 原始名称
     * @param delimiter 层级分隔符，可为 {@code null}
     * @return 识别出的系统类型；非系统文件夹返回 {@code null}
     */
    public static MailFolderType resolveByImapName(String imapName, String delimiter) {
        String leaf = leafOf(imapName, delimiter);
        return leaf.isEmpty() ? null : ALIASES.get(leaf);
    }

    /**
     * 判断 IMAP 名称是否位于顶层（无层级前缀）
     *
     * <p>系统文件夹认领时顶层优先：用户完全可能在自己建的 {@code Archive.Sent} 归档目录里
     * 放一个同名子文件夹，顶层那个才更可能是真系统文件夹。</p>
     *
     * @param imapName IMAP 原始名称
     * @param delimiter 层级分隔符，可为 {@code null}
     * @return 顶层返回 {@code true}
     */
    public static boolean isTopLevel(String imapName, String delimiter) {
        if (imapName == null || imapName.isEmpty()) {
            return false;
        }
        String sep = delimiter == null || delimiter.isEmpty() ? "/" : delimiter;
        return imapName.indexOf(sep) < 0;
    }

    /**
     * 取 IMAP 名称的层级末段并归一化
     *
     * @param imapName IMAP 原始名称
     * @param delimiter 层级分隔符，可为 {@code null}
     * @return 小写末段；名称为空时返回空串
     */
    private static String leafOf(String imapName, String delimiter) {
        if (imapName == null || imapName.isBlank()) {
            return "";
        }
        String sep = delimiter == null || delimiter.isEmpty() ? "/" : delimiter;
        int index = imapName.lastIndexOf(sep);
        String leaf = index >= 0 ? imapName.substring(index + sep.length()) : imapName;
        return leaf.trim().toLowerCase(Locale.ROOT);
    }
}


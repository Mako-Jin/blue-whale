package com.bluewhale.mail.support;

import java.util.List;

/**
 * 邮件正文快照。
 *
 * @param text 纯文本正文
 * @param html HTML 正文
 * @param hasAttachment 是否含附件
 * @author BlueWhale
 */
public record MailBody(String text, String html, boolean hasAttachment) {

    /**
     * 空正文（回源失败或邮件本身无正文时使用）
     */
    public static final MailBody EMPTY = new MailBody(null, null, false);

    /**
     * 是否有可展示内容
     *
     * @return 存在任一正文时返回 {@code true}
     */
    public boolean hasContent() {
        return (text != null && !text.isBlank()) || (html != null && !html.isBlank());
    }

    /**
     * 从纯文本推导摘要
     *
     * @param maxLength 最大字符数
     * @return 摘要；无正文返回 {@code null}
     */
    public String preview(int maxLength) {
        String source = text != null && !text.isBlank() ? text : stripHtml(html);
        if (source == null) {
            return null;
        }
        String normalized = source.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    /**
     * 粗略去标签（仅用于摘要，不用于渲染）
     *
     * @param html HTML 文本
     * @return 去标签文本
     */
    private static String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ");
    }

    /**
     * 是否为空
     *
     * @return 无内容返回 {@code true}
     */
    public boolean isEmpty() {
        return !hasContent();
    }

    /**
     * 合并附件标记
     *
     * @param attachments 附件标记
     * @return 新的正文快照
     */
    public MailBody withAttachment(boolean attachments) {
        return new MailBody(text, html, attachments);
    }

    /**
     * 正文文本列表（便于批量处理）
     *
     * @return 非空正文列表
     */
    public List<String> contents() {
        return List.of(text == null ? "" : text, html == null ? "" : html);
    }
}

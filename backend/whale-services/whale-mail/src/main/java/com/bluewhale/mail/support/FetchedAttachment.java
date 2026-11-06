package com.bluewhale.mail.support;

/**
 * IMAP 取到的单个附件快照。
 *
 * <p>{@code content} 为空表示超出单文件上限被跳过：元信息仍要保留并入库，
 * 否则用户在附件列表里看不到它，会误以为邮件本身不完整。</p>
 *
 * @param fileName 文件名
 * @param contentType MIME 类型
 * @param contentId Content-ID（内联图片用）
 * @param inline 是否内联资源
 * @param sizeBytes 原始字节数
 * @param content 附件字节；超出上限时为 {@code null}
 * @author BlueWhale
 */
public record FetchedAttachment(String fileName,
                                String contentType,
                                String contentId,
                                boolean inline,
                                long sizeBytes,
                                byte[] content) {

    /**
     * 是否已取到字节
     *
     * @return 有字节内容时返回 {@code true}
     */
    public boolean hasContent() {
        return content != null && content.length > 0;
    }

    /**
     * 实际字节数（取到字节时以真实长度为准）
     *
     * @return 字节数
     */
    public long effectiveSize() {
        return content == null ? sizeBytes : content.length;
    }
}

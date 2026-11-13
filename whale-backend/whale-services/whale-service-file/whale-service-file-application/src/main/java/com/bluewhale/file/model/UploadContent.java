package com.bluewhale.file.model;

/**
 * 上传内容（应用层中立类型）：屏蔽 Web 上传对象，仅暴露原始文件名、MIME 类型与字节内容。
 *
 * <p>由接口层将 {@code MultipartFile} 适配为本类型后传入应用层，避免应用层反向依赖
 * Spring Web 类型。</p>
 *
 * @param originalName 原始文件名，可为 {@code null}
 * @param contentType MIME 类型，可为 {@code null}
 * @param content 文件内容字节，可为 {@code null}
 * @author BlueWhale
 */
public record UploadContent(String originalName, String contentType, byte[] content) {

    /**
     * 内容字节数
     *
     * @return 字节数；内容为 {@code null} 时返回 0
     */
    public long size() {
        return content == null ? 0L : content.length;
    }

    /**
     * 内容是否为空
     *
     * @return 内容为 {@code null} 或长度为 0 时返回 {@code true}
     */
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}

package com.bluewhale.file.domain.storage;

/**
 * 分片上传会话元数据：初始化时登记，完成合并时回传给应用层作为文件元数据来源。
 *
 * @param fileName 原始文件名
 * @param contentType MIME 类型，可为 {@code null}
 * @param size 文件总字节数
 * @author BlueWhale
 */
public record ChunkUploadMetadata(String fileName, String contentType, long size) {
}

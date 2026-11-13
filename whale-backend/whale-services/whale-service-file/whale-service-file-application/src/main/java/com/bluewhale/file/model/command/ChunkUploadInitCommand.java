package com.bluewhale.file.model.command;

import com.bluewhale.file.model.UploadLimits;

/**
 * 分片上传初始化命令：创建或续期上传会话，并登记本次生效的上传上限。
 *
 * @param fileName 原始文件名
 * @param contentType MIME 类型，可为 {@code null}
 * @param size 文件总字节数
 * @param bucket 目标桶名称；为空时取配置的默认桶
 * @param uploadId 续传会话 ID，可为 {@code null}（新建会话）
 * @param limits 本次上传生效的上限与配额；为 {@code null} 时使用全局默认值
 * @author BlueWhale
 */
public record ChunkUploadInitCommand(String fileName, String contentType, Long size, String bucket,
                                     String uploadId, UploadLimits limits) {
}

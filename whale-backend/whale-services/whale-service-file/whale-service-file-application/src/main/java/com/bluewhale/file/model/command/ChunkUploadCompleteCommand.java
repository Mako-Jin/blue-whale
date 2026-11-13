package com.bluewhale.file.model.command;

import com.bluewhale.file.model.UploadLimits;

/**
 * 分片上传完成命令：校验分片完整性后合并落存储并登记元数据。
 *
 * @param uploadId 上传会话 ID
 * @param fileName 原始文件名
 * @param size 文件总字节数
 * @param bucket 目标桶名称；为空时取配置的默认桶
 * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
 * @param limits 完成阶段当前生效的上限与配额；为 {@code null} 时使用全局默认值
 * @author BlueWhale
 */
public record ChunkUploadCompleteCommand(String uploadId, String fileName, Long size, String bucket,
                                         Long uploaderId, UploadLimits limits) {
}

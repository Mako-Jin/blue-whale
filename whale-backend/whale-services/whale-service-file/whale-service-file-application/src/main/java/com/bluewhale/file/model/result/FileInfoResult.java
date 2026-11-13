package com.bluewhale.file.model.result;

import java.time.LocalDateTime;

/**
 * 文件元数据结果。
 *
 * @param id 文件主键
 * @param fileName 存储文件名
 * @param originalName 原始文件名
 * @param bucket 桶名称
 * @param objectKey 对象键
 * @param fileSize 文件字节数
 * @param contentType MIME 类型
 * @param md5 文件 MD5
 * @param uploaderId 上传用户 ID；系统上传为 {@code null}
 * @param createTime 创建时间
 * @author BlueWhale
 */
public record FileInfoResult(Long id, String fileName, String originalName, String bucket,
                             String objectKey, Long fileSize, String contentType, String md5,
                             Long uploaderId, LocalDateTime createTime) {
}

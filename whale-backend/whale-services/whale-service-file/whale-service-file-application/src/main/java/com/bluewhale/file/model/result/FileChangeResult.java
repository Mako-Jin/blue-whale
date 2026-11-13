package com.bluewhale.file.model.result;

import java.time.LocalDateTime;

/**
 * 文件变更结果（内网变更流）：含状态、逻辑删除标记与更新时间。
 *
 * @param id 文件主键
 * @param fileName 存储文件名
 * @param originalName 原始文件名
 * @param bucket 桶名称
 * @param objectKey 对象键
 * @param fileSize 文件字节数
 * @param contentType MIME 类型
 * @param md5 文件 MD5
 * @param status 文件状态码（1 正常、2 归档锁定、3 禁用）
 * @param uploaderId 上传用户 ID；系统上传为 {@code null}
 * @param deleted 逻辑删除标记（0 未删除、1 已删除）
 * @param createTime 创建时间
 * @param updateTime 更新时间（变更流游标）
 * @author BlueWhale
 */
public record FileChangeResult(Long id, String fileName, String originalName, String bucket,
                               String objectKey, Long fileSize, String contentType, String md5,
                               Integer status, Long uploaderId, Integer deleted,
                               LocalDateTime createTime, LocalDateTime updateTime) {
}

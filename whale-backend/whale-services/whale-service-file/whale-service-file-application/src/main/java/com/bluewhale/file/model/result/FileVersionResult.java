package com.bluewhale.file.model.result;

import java.time.LocalDateTime;

/**
 * 文件版本结果。
 *
 * @param id 版本行主键
 * @param parentId 版本组根文件 ID；根版本为 {@code null}
 * @param version 版本号
 * @param latest 是否为版本组内当前最新版本（已删除版本恒为 {@code false}）
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
public record FileVersionResult(Long id, Long parentId, int version, boolean latest, String fileName,
                                String originalName, String bucket, String objectKey, Long fileSize,
                                String contentType, String md5, Long uploaderId, LocalDateTime createTime) {
}

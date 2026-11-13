package com.bluewhale.file.model.result;

/**
 * 上传结果：新登记的文件元数据。
 *
 * @param id 文件主键
 * @param fileName 存储文件名
 * @param originalName 原始文件名
 * @param bucket 桶名称
 * @param objectKey 对象键
 * @param fileSize 文件字节数
 * @param contentType MIME 类型
 * @param md5 文件 MD5
 * @author BlueWhale
 */
public record FileUploadResult(Long id, String fileName, String originalName, String bucket,
                               String objectKey, Long fileSize, String contentType, String md5) {
}

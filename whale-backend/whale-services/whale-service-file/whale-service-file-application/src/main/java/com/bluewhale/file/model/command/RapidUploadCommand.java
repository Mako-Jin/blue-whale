package com.bluewhale.file.model.command;

import com.bluewhale.file.model.UploadLimits;

/**
 * 秒传命令：按 MD5 与大小匹配已有物理对象，命中时仅登记新元数据。
 *
 * @param fileName 原始文件名
 * @param md5 文件 MD5（32 位十六进制，大小写不敏感）
 * @param size 文件字节数
 * @param contentType MIME 类型，可为 {@code null}
 * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
 * @param limits 本次上传生效的上限与配额；为 {@code null} 时使用全局默认值
 * @author BlueWhale
 */
public record RapidUploadCommand(String fileName, String md5, Long size, String contentType,
                                 Long uploaderId, UploadLimits limits) {
}

package com.bluewhale.file.model.command;

import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;

/**
 * 普通上传命令：写入存储并登记文件元数据。
 *
 * @param content 上传内容（必填）
 * @param bucket 目标桶名称；为空时取配置的默认桶
 * @param uploaderId 上传用户 ID；内网系统调用可为 {@code null}
 * @param limits 本次上传生效的上限与配额；为 {@code null} 时使用全局默认值
 * @author BlueWhale
 */
public record UploadFileCommand(UploadContent content, String bucket, Long uploaderId, UploadLimits limits) {
}

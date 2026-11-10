package com.bluewhale.oa.common.domain.model;

/**
 * 文件元数据快照（来自 whale-file）。
 *
 * <p>附件归属校验与附件引用登记共用本快照，避免同一文件在一次请求内被回查两次：
 * 校验放行后调用方直接取用这里的文件名 / 大小 / MIME，不采信前端传入值。</p>
 *
 * @param fileId       文件ID
 * @param originalName 原始文件名
 * @param fileSize     字节数，缺失时为 {@code null}
 * @param contentType  MIME 类型，缺失时为 {@code null}
 * @param uploaderId   上传人用户ID，缺失时为 {@code null}
 * @param deleted      逻辑删除标记（0 未删除）
 * @param status       文件状态（1 正常 / 0 禁用 / 2 归档锁定）
 * @author BlueWhale
 */
public record FileMetadata(Long fileId, String originalName, Long fileSize, String contentType,
                           Long uploaderId, int deleted, int status) {
}

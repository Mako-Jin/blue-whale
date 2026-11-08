package com.bluewhale.oa.common.domain.model;

/**
 * 文件元数据快照（来自 whale-file）。
 *
 * @param fileId       文件ID
 * @param originalName 原始文件名
 * @param uploaderId   上传人用户ID，缺失时为 {@code null}
 * @param deleted      逻辑删除标记（0 未删除）
 * @param status       文件状态（1 正常）
 * @author BlueWhale
 */
public record FileMetadata(Long fileId, String originalName, Long uploaderId, int deleted, int status) {
}

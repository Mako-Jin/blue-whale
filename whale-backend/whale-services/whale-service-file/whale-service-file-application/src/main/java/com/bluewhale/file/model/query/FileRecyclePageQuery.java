package com.bluewhale.file.model.query;

/**
 * 回收站分页查询（应用层）。
 *
 * @param originalName 原始文件名模糊条件，可为 {@code null}
 * @param bucket 桶名称等值条件，可为 {@code null}
 * @param requesterId 调用者用户 ID
 * @param canManage 是否持有文件管理权限（可查看全租户回收站）
 * @param current 页码（从 1 开始）
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileRecyclePageQuery(String originalName, String bucket, Long requesterId, boolean canManage,
                                   long current, long size) {
}

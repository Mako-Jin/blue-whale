package com.bluewhale.file.model.query;

import java.time.LocalDateTime;

/**
 * 文件分页查询：查询条件与调用者可见范围。
 *
 * @param current 当前页码（从 1 开始）
 * @param size 每页条数（1..200，越界自动收敛）
 * @param originalName 原始文件名模糊条件，可为 {@code null}
 * @param bucket 桶名称等值条件，可为 {@code null}
 * @param createTimeStart 创建时间起（含），可为 {@code null}
 * @param createTimeEnd 创建时间止（含），可为 {@code null}
 * @param requesterId 调用者用户 ID
 * @param canManage 是否持有文件管理权限（可查看全租户文件）
 * @author BlueWhale
 */
public record FilePageQuery(long current, long size, String originalName, String bucket,
                            LocalDateTime createTimeStart, LocalDateTime createTimeEnd,
                            Long requesterId, boolean canManage) {
}

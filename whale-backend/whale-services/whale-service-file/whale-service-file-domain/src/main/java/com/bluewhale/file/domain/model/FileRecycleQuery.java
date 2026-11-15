package com.bluewhale.file.domain.model;

/**
 * 回收站分页查询条件（领域层查询模型）。
 *
 * <p>只查询已逻辑删除的文件（{@code deleted = 1}）；排序固定为
 * {@code update_time DESC, id DESC}。当前表未单独记录删除时间，
 * 列表中的更新时间仅代表最后一次业务更新，不代表删除时刻。</p>
 *
 * @param originalName 原始文件名（模糊匹配），可为 {@code null}
 * @param bucket 对象存储桶名称（精确匹配），可为 {@code null}
 * @param uploaderId 上传人ID（精确匹配），为 {@code null} 时不限（管理视角）
 * @param current 页码（从 1 开始）
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileRecycleQuery(String originalName, String bucket, Long uploaderId, long current, long size) {
}

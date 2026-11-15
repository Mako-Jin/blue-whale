package com.bluewhale.file.domain.share.model;

/**
 * 分享链接分页查询条件。
 *
 * @param creatorId 创建人过滤条件；为 {@code null} 时不按创建人过滤（管理视角）
 * @param current 当前页码
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileShareQuery(Long creatorId, long current, long size) {
}

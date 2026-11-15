package com.bluewhale.file.model.query;

/**
 * 分享链接分页查询。
 *
 * @param requesterId 调用者用户 ID
 * @param canManage 是否持有文件管理权限（管理视角可见全租户分享）
 * @param current 当前页码
 * @param size 每页条数
 * @author BlueWhale
 */
public record FileSharePageQuery(Long requesterId, boolean canManage, long current, long size) {
}

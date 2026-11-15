package com.bluewhale.file.model.command;

/**
 * 创建分享命令。
 *
 * @param fileId 被分享的文件ID
 * @param expireHours 有效小时数；为 {@code null} 时取配置默认值
 * @param maxDownloads 下载次数上限；{@code null} 或 {@code <=0} 表示不限
 * @param password 明文分享密码；为空表示无密码
 * @param requesterId 创建人用户 ID
 * @param canManage 是否持有文件管理权限
 * @author BlueWhale
 */
public record CreateShareCommand(Long fileId, Integer expireHours, Integer maxDownloads, String password,
                                 Long requesterId, boolean canManage) {
}

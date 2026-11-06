package com.bluewhale.auth.infrastructure.mail;

/**
 * 代开邮箱命令（发往 whale-mail 内网接口）。
 *
 * <p>不带口令：口令由 whale-mail 生成并加密存储，调用方拿到也用不上，
 * 让它过一遍网络只是多一个泄漏面。</p>
 *
 * @param tenantId 租户 ID
 * @param userId 平台用户 ID
 * @param username 用户名（生成邮箱地址用）
 * @param displayName 显示名
 * @author BlueWhale
 */
public record MailProvisionCommand(Long tenantId, Long userId, String username, String displayName) {
}

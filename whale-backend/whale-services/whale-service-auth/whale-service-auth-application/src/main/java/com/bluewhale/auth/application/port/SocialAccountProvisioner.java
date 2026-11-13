package com.bluewhale.auth.application.port;

/**
 * 三方账号开通端口（权限中心扩展 M2）：租户自助注册开关开启时自动建号。
 *
 * <p>由基础设施适配器实现：随机临时口令（用户不可知、不可用，登录全走三方）、
 * {@code register_source=SOCIAL}、注册时间，并建立 {@code sys_user_tenant} 成员关系。
 * 用户名冲突时由实现侧追加随机后缀重试（租户内唯一约束兜底）。</p>
 *
 * @author BlueWhale
 */
public interface SocialAccountProvisioner {

    /**
     * 在指定租户内开通三方来源账号
     *
     * @param tenantId 租户 ID
     * @param username 初始用户名（冲突时实现侧自动处理）
     * @param nickname 昵称（可空）
     * @param avatar 头像 URL（可空）
     * @param email 邮箱（可空）
     * @return 新建用户 ID
     */
    Long provision(Long tenantId, String username, String nickname, String avatar, String email);
}

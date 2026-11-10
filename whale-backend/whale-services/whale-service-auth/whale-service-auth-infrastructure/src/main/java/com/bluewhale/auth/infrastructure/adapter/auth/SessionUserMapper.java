package com.bluewhale.auth.infrastructure.adapter.auth;

import com.bluewhale.auth.application.auth.AuthenticatedUser;
import com.bluewhale.auth.security.LoginUser;

/**
 * 登录用户适配器：把 Spring Security 的 {@link LoginUser} 转换为应用层快照。
 *
 * @author BlueWhale
 */
final class SessionUserMapper {

    /**
     * 私有构造器：工具类禁止实例化
     */
    private SessionUserMapper() {
    }

    /**
     * 转换为应用层用户快照
     *
     * @param loginUser Spring Security 登录用户
     * @return 应用层用户快照
     */
    static AuthenticatedUser toAuthenticatedUser(LoginUser loginUser) {
        return new AuthenticatedUser(loginUser.getTenantId(), loginUser.getUserId(), loginUser.getUsername(),
                loginUser.getNickname(), loginUser.getRoles(), loginUser.isEnabled(),
                loginUser.isMustChangePassword(), loginUser.getPasswordUpdatedTime());
    }
}

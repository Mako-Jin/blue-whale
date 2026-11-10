package com.bluewhale.auth.application.model.port;

import com.bluewhale.auth.application.model.AuthenticatedUser;

/**
 * 当前登录用户提供者：从运行时认证上下文读取身份，应用层不依赖 Spring Security。
 *
 * @author BlueWhale
 */
public interface CurrentUserProvider {

    /**
     * 获取当前登录用户
     *
     * @return 当前登录用户
     */
    AuthenticatedUser current();
}

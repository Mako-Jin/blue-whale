package com.bluewhale.auth.infrastructure.adapter.auth;

import com.bluewhale.auth.application.auth.AuthenticatedUser;
import com.bluewhale.auth.application.auth.port.CurrentUserProvider;
import com.bluewhale.auth.security.LoginUser;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前用户提供者：从 Spring Security 上下文读取已认证用户。
 *
 * @author BlueWhale
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public AuthenticatedUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return SessionUserMapper.toAuthenticatedUser(loginUser);
    }
}

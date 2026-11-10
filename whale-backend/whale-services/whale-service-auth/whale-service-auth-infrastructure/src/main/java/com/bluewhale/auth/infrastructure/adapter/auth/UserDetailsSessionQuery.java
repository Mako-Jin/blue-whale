package com.bluewhale.auth.infrastructure.adapter.auth;

import com.bluewhale.auth.application.auth.AuthenticatedUser;
import com.bluewhale.auth.application.auth.port.UserSessionQuery;
import com.bluewhale.auth.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 用户会话查询适配器：复用 {@link UserDetailsServiceImpl#loadUserById} 跨租户加载并装配角色。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class UserDetailsSessionQuery implements UserSessionQuery {

    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Optional<AuthenticatedUser> loadUserById(Long userId, Long tenantId) {
        try {
            return Optional.of(SessionUserMapper.toAuthenticatedUser(
                    userDetailsService.loadUserById(userId, tenantId)));
        } catch (UsernameNotFoundException e) {
            return Optional.empty();
        }
    }
}

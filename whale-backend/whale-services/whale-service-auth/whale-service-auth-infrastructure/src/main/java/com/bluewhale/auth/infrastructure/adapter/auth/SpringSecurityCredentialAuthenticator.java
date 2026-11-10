package com.bluewhale.auth.infrastructure.adapter.auth;

import com.bluewhale.auth.application.auth.AuthenticatedUser;
import com.bluewhale.auth.application.auth.port.CredentialAuthenticator;
import com.bluewhale.auth.infrastructure.security.LoginTenantContext;
import com.bluewhale.auth.infrastructure.security.LoginUser;
import com.bluewhale.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * 凭据认证适配器：基于 Spring Security {@link AuthenticationManager} + UserDetailsService + BCrypt。
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringSecurityCredentialAuthenticator implements CredentialAuthenticator {

    private final AuthenticationManager authenticationManager;

    @Override
    public AuthenticatedUser authenticate(Long tenantId, String username, String rawPassword) {
        Authentication authentication;
        // UserDetailsService 只接收用户名，需在认证前绑定租户，确保按（租户, 用户名）定位账号
        LoginTenantContext.set(tenantId);
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, rawPassword));
        } catch (BadCredentialsException e) {
            throw new BizException(401, "用户名或密码错误");
        } catch (DisabledException e) {
            throw new BizException(401, "账号已停用");
        } catch (AuthenticationException e) {
            log.warn("认证失败: tenantId={}, username={}, reason={}", tenantId, username, e.getMessage());
            throw new BizException(401, "认证失败");
        } finally {
            LoginTenantContext.clear();
        }
        return SessionUserMapper.toAuthenticatedUser((LoginUser) authentication.getPrincipal());
    }
}

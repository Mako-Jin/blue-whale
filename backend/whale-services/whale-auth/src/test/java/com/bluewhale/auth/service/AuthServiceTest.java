package com.bluewhale.auth.service;

import com.bluewhale.auth.dto.RefreshRequest;
import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.security.LoginUser;
import com.bluewhale.auth.security.TokenBlacklist;
import com.bluewhale.auth.security.UserDetailsServiceImpl;
import com.bluewhale.auth.vo.LoginResponse;
import com.bluewhale.common.core.exception.BizException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService} 单元测试：登出双令牌失效（REQ-SEC-004）、refreshToken 失效校验（T5.6）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ACCESS_TOKEN = "access-token-original";
    private static final String REFRESH_TOKEN = "refresh-token-original";
    private static final String BEARER_ACCESS = "Bearer " + ACCESS_TOKEN;
    private static final String USERNAME = "alice";
    private static final long USER_ID = 1251323419503984640L;
    private static final long ACCESS_EXPIRE_SECONDS = 7200L;
    private static final int UNAUTHORIZED_CODE = 401;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private TokenBlacklist tokenBlacklist;

    @InjectMocks
    private AuthService authService;

    private static RefreshRequest refreshRequest() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(REFRESH_TOKEN);
        return request;
    }

    /** 构造已完成签名校验的 refreshToken 载荷 */
    private Claims refreshClaims() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        return claims;
    }

    @Test
    @DisplayName("refresh：refreshToken 命中登出黑名单时返回 401，且不再查库")
    void refreshShouldRejectBlacklistedRefreshToken() {
        Claims claims = refreshClaims();
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(refreshRequest()));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("refresh：accessToken 冒充 refreshToken 时返回 401")
    void refreshShouldRejectNonRefreshToken() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(refreshRequest()));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(tokenBlacklist);
    }

    @Test
    @DisplayName("refresh：未失效的 refreshToken 正常换发新令牌")
    void refreshShouldIssueNewTokensWhenNotBlacklisted() {
        Claims claims = refreshClaims();
        when(claims.getSubject()).thenReturn(USERNAME);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(new LoginUser(USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        when(tokenProvider.generateAccessToken(USER_ID, USERNAME, List.of("admin"))).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(USER_ID, USERNAME)).thenReturn("new-refresh");
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        LoginResponse response = authService.refresh(refreshRequest());

        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
    }

    @Test
    @DisplayName("logout：accessToken 与 refreshToken 一并写入黑名单")
    void logoutShouldBlacklistBothTokens() {
        authService.logout(BEARER_ACCESS, REFRESH_TOKEN);

        verify(tokenBlacklist).add(ACCESS_TOKEN);
        verify(tokenBlacklist).add(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("logout：未携带 refreshToken 时仅失效 accessToken")
    void logoutShouldBlacklistOnlyAccessTokenWhenRefreshAbsent() {
        authService.logout(BEARER_ACCESS, null);

        verify(tokenBlacklist, times(1)).add(anyString());
        verify(tokenBlacklist).add(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("logout：Authorization 头格式不符时静默跳过，不写黑名单")
    void logoutShouldSkipWhenAuthorizationMalformed() {
        authService.logout("Basic abc", null);

        verifyNoInteractions(tokenBlacklist);
    }
}

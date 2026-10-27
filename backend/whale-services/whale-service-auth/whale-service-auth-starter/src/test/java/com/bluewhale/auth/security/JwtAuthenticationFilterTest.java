package com.bluewhale.auth.security;

import com.bluewhale.auth.application.auth.port.TokenBlacklist;
import com.bluewhale.auth.application.auth.port.UserAccessRevocationPort;
import com.bluewhale.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthenticationFilter} 单元测试（AUTH-23）：认证写入与各类未认证降级路径。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private TokenBlacklist tokenBlacklist;

    @Mock
    private TenantMembershipRevocationStore membershipRevocationStore;

    @Mock
    private UserAccessRevocationPort userAccessRevocationStore;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Claims claims;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void givenBearer(String token) {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(JwtTokenProvider.BEARER_PREFIX + token);
    }

    private void givenAccessClaims(String subject) {
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class)).thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);
        lenient().when(claims.getSubject()).thenReturn(subject);
        // 黑名单分支在租户解析前即返回，使用 lenient 避免严格桩校验误报
        lenient().when(claims.get(JwtTokenProvider.CLAIM_TENANT_ID)).thenReturn(1L);
        lenient().when(claims.get(JwtTokenProvider.CLAIM_USER_ID)).thenReturn(1L);
    }

    @Test
    @DisplayName("无 Authorization 头：不认证，链路继续")
    void shouldSkipWhenNoHeader() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("有效 accessToken + 启用用户：写入 SecurityContext")
    void shouldAuthenticateValidAccessToken() throws Exception {
        givenBearer("a.b.c");
        when(tokenProvider.parse("a.b.c")).thenReturn(claims);
        givenAccessClaims("tester");
        when(userDetailsService.loadUserById(1L, 1L))
                .thenReturn(new LoginUser(1L, 1L, "tester", "pwd", "T", List.of("super_admin"), true));

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("refreshToken：不写入认证")
    void shouldRejectRefreshToken() throws Exception {
        givenBearer("r.r.r");
        when(tokenProvider.parse("r.r.r")).thenReturn(claims);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token 命中登出黑名单：不写入认证")
    void shouldRejectBlacklistedToken() throws Exception {
        givenBearer("b.b.b");
        when(tokenProvider.parse("b.b.b")).thenReturn(claims);
        givenAccessClaims("tester");
        when(tokenBlacklist.contains("b.b.b")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token 签发于租户成员解绑之前：不写入认证")
    void shouldRejectRevokedMembershipToken() throws Exception {
        givenBearer("v.v.v");
        when(tokenProvider.parse("v.v.v")).thenReturn(claims);
        givenAccessClaims("tester");
        when(tokenBlacklist.contains("v.v.v")).thenReturn(false);
        when(membershipRevocationStore.isRevoked(1L, 1L, new java.util.Date(0L))).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserById(any(), any());
    }

    @Test
    @DisplayName("用户已停用/删除/改密：撤销前签发的令牌不写入认证")
    void shouldRejectUserAccessRevokedToken() throws Exception {
        givenBearer("v.v.v");
        when(tokenProvider.parse("v.v.v")).thenReturn(claims);
        givenAccessClaims("tester");
        when(tokenBlacklist.contains("v.v.v")).thenReturn(false);
        when(userAccessRevocationStore.isRevoked(1L, new java.util.Date(0L))).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserById(any(), any());
    }

    @Test
    @DisplayName("用户已停用：不写入认证")
    void shouldRejectDisabledUser() throws Exception {
        givenBearer("d.d.d");
        when(tokenProvider.parse("d.d.d")).thenReturn(claims);
        givenAccessClaims("tester");
        when(tokenBlacklist.contains("d.d.d")).thenReturn(false);
        when(userDetailsService.loadUserById(1L, 1L))
                .thenReturn(new LoginUser(1L, 1L, "tester", "pwd", "T", List.of(), false));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token 对应用户不存在：不写入认证（吞掉 UsernameNotFoundException）")
    void shouldRejectWhenUserMissing() throws Exception {
        givenBearer("m.m.m");
        when(tokenProvider.parse("m.m.m")).thenReturn(claims);
        givenAccessClaims("ghost");
        when(tokenBlacklist.contains("m.m.m")).thenReturn(false);
        when(userDetailsService.loadUserById(1L, 1L))
                .thenThrow(new UsernameNotFoundException("用户不存在: ghost"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("accessToken 缺少 tenantId claim：不写入认证")
    void shouldRejectTokenWithoutTenantClaim() throws Exception {
        givenBearer("t.t.t");
        when(tokenProvider.parse("t.t.t")).thenReturn(claims);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);
        when(claims.getSubject()).thenReturn("tester");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("accessToken 缺少 userId claim：不写入认证")
    void shouldRejectTokenWithoutUserClaim() throws Exception {
        givenBearer("u.u.u");
        when(tokenProvider.parse("u.u.u")).thenReturn(claims);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);
        when(claims.getSubject()).thenReturn("tester");
        when(claims.get(JwtTokenProvider.CLAIM_TENANT_ID)).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

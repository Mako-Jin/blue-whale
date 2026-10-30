package com.bluewhale.auth.service;

import com.bluewhale.auth.application.menu.MenuAppService;
import com.bluewhale.auth.application.permission.PermissionAppService;
import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.tenant.TenantMembershipAppService;
import com.bluewhale.auth.config.RefreshRotationProperties;
import com.bluewhale.auth.dto.SwitchTenantRequest;
import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.security.LoginUser;
import com.bluewhale.auth.security.PermissionInterceptor;
import com.bluewhale.auth.security.RefreshTokenRotationStore;
import com.bluewhale.auth.security.TokenBlacklist;
import com.bluewhale.auth.security.UserAccessRevocationStore;
import com.bluewhale.auth.security.UserDetailsServiceImpl;
import com.bluewhale.auth.vo.AuthMeResponse;
import com.bluewhale.auth.vo.AuthTokenIssue;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.auth.vo.MyTenantVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService} 单元测试：登出双令牌失效（REQ-SEC-004）、refreshToken 失效校验（T5.6）、
 * refreshToken 一次性轮换与复用检测（AUTH-39）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ACCESS_TOKEN = "access-token-original";
    private static final String REFRESH_TOKEN = "refresh-token-original";
    private static final String BEARER_ACCESS = "Bearer " + ACCESS_TOKEN;
    private static final String USERNAME = "alice";
    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1251323419503984640L;
    private static final long ACCESS_EXPIRE_SECONDS = 7200L;
    private static final long REFRESH_EXPIRE_SECONDS = 86400L;
    private static final long REMAINING_SECONDS = 3600L;
    private static final int UNAUTHORIZED_CODE = 401;
    private static final String FAMILY_ID = "family-uuid-0001";
    private static final String JTI = "jti-uuid-0001";

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

    @Mock
    private RoleAppService roleAppService;

    @Mock
    private MenuAppService menuAppService;

    @Mock
    private PermissionAppService permissionAppService;

    @Mock
    private RefreshTokenRotationStore refreshRotationStore;

    @Mock
    private RefreshRotationProperties refreshRotationProperties;

    @Mock
    private TenantAppService tenantAppService;

    @Mock
    private TenantMembershipAppService tenantMembershipAppService;

    @Mock
    private UserAccessRevocationStore userAccessRevocationStore;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 把已认证用户放入 SecurityContext，供 {@link AuthService#me()} 读取 */
    private static void authenticate(LoginUser loginUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    /** 构造已完成签名校验的 refreshToken 载荷 */
    private Claims refreshClaims() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        // 部分用例在租户解析前即失败，使用 lenient 避免严格桩校验误报
        lenient().when(claims.get(JwtTokenProvider.CLAIM_TENANT_ID)).thenReturn(TENANT_ID);
        lenient().when(claims.get(JwtTokenProvider.CLAIM_USER_ID)).thenReturn(USER_ID);
        return claims;
    }

    private static SwitchTenantRequest switchRequest(long tenantId) {
        SwitchTenantRequest request = new SwitchTenantRequest();
        request.setTenantId(tenantId);
        return request;
    }

    @Test
    @DisplayName("refresh：refreshToken 命中登出黑名单时返回 401，且不再查库")
    void refreshShouldRejectBlacklistedRefreshToken() {
        Claims claims = refreshClaims();
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

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

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(tokenBlacklist);
    }

    @Test
    @DisplayName("refresh：未失效的 refreshToken 正常换发新令牌")
    void refreshShouldIssueNewTokensWhenNotBlacklisted() {
        Claims claims = refreshClaims();
        lenient().when(claims.getSubject()).thenReturn(USERNAME);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userDetailsService.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        when(tokenProvider.generateAccessToken(TENANT_ID, USER_ID, USERNAME, List.of("admin")))
                .thenReturn("new-access");
        // 轮换默认关闭：familyId 原样透传（claim 未携带即 null）
        when(tokenProvider.generateRefreshToken(TENANT_ID, USER_ID, USERNAME, null)).thenReturn("new-refresh");
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        AuthTokenIssue issue = authService.refresh(REFRESH_TOKEN);

        assertEquals("new-access", issue.getResponse().getAccessToken());
        assertEquals("new-refresh", issue.getRefreshToken());
        verifyNoInteractions(refreshRotationStore);
    }

    @Test
    @DisplayName("refresh：refreshToken 在用户级撤销前签发时返回 401，不换发新令牌")
    void refreshShouldRejectRefreshTokenIssuedBeforeUserRevocation() {
        Claims claims = refreshClaims();
        Date issuedAt = new Date(1_800_000_000_000L);
        when(claims.getIssuedAt()).thenReturn(issuedAt);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userDetailsService.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        when(userAccessRevocationStore.isRevoked(USER_ID, issuedAt)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verify(tokenProvider, never()).generateAccessToken(anyLong(), anyLong(), anyString(), any());
        verify(refreshRotationStore, never()).markUsed(anyString(), anyLong());
    }

    @Test
    @DisplayName("refresh：用户级撤销后签发的 refreshToken 正常换发新令牌")
    void refreshShouldIssueTokensWhenRefreshTokenIssuedAfterUserRevocation() {
        Claims claims = refreshClaims();
        Date issuedAt = new Date(1_800_000_000_000L);
        lenient().when(claims.getSubject()).thenReturn(USERNAME);
        when(claims.getIssuedAt()).thenReturn(issuedAt);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userDetailsService.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        when(userAccessRevocationStore.isRevoked(USER_ID, issuedAt)).thenReturn(false);
        when(tokenProvider.generateAccessToken(TENANT_ID, USER_ID, USERNAME, List.of("admin")))
                .thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(TENANT_ID, USER_ID, USERNAME, null)).thenReturn("new-refresh");
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        AuthTokenIssue issue = authService.refresh(REFRESH_TOKEN);

        assertEquals("new-access", issue.getResponse().getAccessToken());
        assertEquals("new-refresh", issue.getRefreshToken());
        verify(userAccessRevocationStore).isRevoked(USER_ID, issuedAt);
    }

    @Test
    @DisplayName("refresh：轮换开启时原子消费旧 refreshToken（入黑名单）并沿用 familyId 换发新令牌")
    void refreshShouldConsumeTokenWhenRotationEnabled() {
        Claims claims = refreshClaims();
        lenient().when(claims.getSubject()).thenReturn(USERNAME);
        when(claims.getId()).thenReturn(JTI);
        when(claims.get(JwtTokenProvider.CLAIM_FAMILY_ID, String.class)).thenReturn(FAMILY_ID);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(refreshRotationProperties.isEnabled()).thenReturn(true);
        when(refreshRotationStore.isFamilyRevoked(FAMILY_ID)).thenReturn(false);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userDetailsService.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        when(tokenProvider.getRemainingValiditySeconds(REFRESH_TOKEN)).thenReturn(REMAINING_SECONDS);
        when(refreshRotationStore.markUsed(JTI, REMAINING_SECONDS)).thenReturn(true);
        when(tokenProvider.generateAccessToken(TENANT_ID, USER_ID, USERNAME, List.of("admin")))
                .thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(TENANT_ID, USER_ID, USERNAME, FAMILY_ID)).thenReturn("new-refresh");
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        AuthTokenIssue issue = authService.refresh(REFRESH_TOKEN);

        assertEquals("new-access", issue.getResponse().getAccessToken());
        assertEquals("new-refresh", issue.getRefreshToken());
        verify(tokenBlacklist).add(REFRESH_TOKEN);
        verify(refreshRotationStore, never()).revokeFamily(anyString(), anyLong());
    }

    @Test
    @DisplayName("refresh：复用检测命中（旧 refreshToken 二次使用）返回 401 并整族吊销 + 降级告警")
    void refreshShouldRevokeFamilyWhenReuseDetected() {
        Claims claims = refreshClaims();
        lenient().when(claims.getSubject()).thenReturn(USERNAME);
        when(claims.getId()).thenReturn(JTI);
        when(claims.get(JwtTokenProvider.CLAIM_FAMILY_ID, String.class)).thenReturn(FAMILY_ID);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(refreshRotationProperties.isEnabled()).thenReturn(true);
        when(refreshRotationStore.isFamilyRevoked(FAMILY_ID)).thenReturn(false);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userDetailsService.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        when(tokenProvider.getRemainingValiditySeconds(REFRESH_TOKEN)).thenReturn(REMAINING_SECONDS);
        when(refreshRotationStore.markUsed(JTI, REMAINING_SECONDS)).thenReturn(false);
        when(tokenProvider.getRefreshExpireSeconds()).thenReturn(REFRESH_EXPIRE_SECONDS);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verify(refreshRotationStore).revokeFamily(FAMILY_ID, REFRESH_EXPIRE_SECONDS);
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("refresh：轮换开启但令牌缺少 familyId（旧令牌）返回 401")
    void refreshShouldRejectTokenWithoutFamilyWhenRotationEnabled() {
        Claims claims = refreshClaims();
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(refreshRotationProperties.isEnabled()).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(refreshRotationStore, tokenBlacklist);
    }

    @Test
    @DisplayName("refresh：轮换开启但 family 已被吊销返回 401")
    void refreshShouldRejectRevokedFamily() {
        Claims claims = refreshClaims();
        when(claims.get(JwtTokenProvider.CLAIM_FAMILY_ID, String.class)).thenReturn(FAMILY_ID);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);
        when(refreshRotationProperties.isEnabled()).thenReturn(true);
        when(refreshRotationStore.isFamilyRevoked(FAMILY_ID)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verify(tokenBlacklist, never()).add(anyString());
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

    @Test
    @DisplayName("me：内置超管授予全量权限码与全部启用可见菜单")
    void meShouldGrantAllForSuperAdmin() {
        LoginUser superAdmin = new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Admin",
                List.of(PermissionInterceptor.SUPER_ADMIN), true);
        authenticate(superAdmin);
        when(permissionAppService.listAllCodes()).thenReturn(List.of("sys:user:list", "sys:role:list"));
        when(menuAppService.treeByPermissionCodes(null)).thenReturn(List.of(new MenuVO()));

        AuthMeResponse response = authService.me();

        assertEquals(List.of("sys:user:list", "sys:role:list"), response.getPermissions());
        assertEquals(1, response.getMenus().size());
        verifyNoInteractions(roleAppService);
    }

    @Test
    @DisplayName("me：普通用户按角色授权返回权限码并据此过滤菜单树")
    void meShouldReturnGrantedPermissionsForNormalUser() {
        authenticate(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        when(roleAppService.listPermissionCodesByUserId(USER_ID)).thenReturn(List.of("sys:user:list"));
        when(menuAppService.treeByPermissionCodes(Set.of("sys:user:list"))).thenReturn(List.of(new MenuVO()));

        AuthMeResponse response = authService.me();

        assertEquals(List.of("sys:user:list"), response.getPermissions());
        assertEquals(TENANT_ID, response.getTenantId());
        assertEquals(USER_ID, response.getUserId());
        assertEquals(List.of("admin"), response.getRoles());
        verifyNoInteractions(permissionAppService);
    }

    @Test
    @DisplayName("me：未认证上下文返回 401")
    void meShouldRejectAnonymous() {
        BizException ex = assertThrows(BizException.class, () -> authService.me());

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
    }

    @Test
    @DisplayName("refresh：令牌缺少 userId claim 时返回 401")
    void refreshShouldRejectTokenWithoutUserClaim() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        lenient().when(claims.get(JwtTokenProvider.CLAIM_TENANT_ID)).thenReturn(TENANT_ID);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(userDetailsService, tokenBlacklist);
    }

    @Test
    @DisplayName("listTenants：按当前登录用户与当前租户返回可进入租户列表")
    void listTenantsShouldDelegateToMembershipService() {
        authenticate(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        MyTenantVO current = new MyTenantVO();
        current.setTenantId(TENANT_ID);
        current.setCurrent(true);
        when(tenantMembershipAppService.listMyTenants(USER_ID, TENANT_ID)).thenReturn(List.of(current));

        List<MyTenantVO> tenants = authService.listTenants();

        assertEquals(1, tenants.size());
        assertEquals(TENANT_ID, tenants.get(0).getTenantId());
    }

    @Test
    @DisplayName("switchTenant：校验关系后按同一 userId 签发目标租户令牌，并失效旧 access/refresh")
    void switchTenantShouldIssueTokensForTargetTenant() {
        authenticate(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        long targetTenantId = 2L;
        when(userDetailsService.loadUserById(USER_ID, targetTenantId)).thenReturn(
                new LoginUser(targetTenantId, USER_ID, USERNAME, "pwd", "Alice", List.of("tenant_admin"), true));
        when(tokenProvider.generateAccessToken(targetTenantId, USER_ID, USERNAME, List.of("tenant_admin")))
                .thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(
                org.mockito.ArgumentMatchers.eq(targetTenantId),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(USERNAME),
                anyString())).thenReturn("new-refresh");
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        AuthTokenIssue issue = authService.switchTenant(BEARER_ACCESS, switchRequest(targetTenantId), REFRESH_TOKEN);

        assertEquals(targetTenantId, issue.getResponse().getTenantId());
        assertEquals("new-access", issue.getResponse().getAccessToken());
        assertEquals("new-refresh", issue.getRefreshToken());
        verify(tenantMembershipAppService).requireSwitchable(USER_ID, targetTenantId);
        verify(tokenBlacklist).add(ACCESS_TOKEN);
        verify(tokenBlacklist).add(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("switchTenant：无权进入目标租户时返回 403，不签发令牌")
    void switchTenantShouldRejectNonMember() {
        authenticate(new LoginUser(TENANT_ID, USER_ID, USERNAME, "pwd", "Alice", List.of("admin"), true));
        long targetTenantId = 2L;
        doThrow(new BizException(403, "无权进入该租户"))
                .when(tenantMembershipAppService).requireSwitchable(USER_ID, targetTenantId);

        BizException ex = assertThrows(BizException.class,
                () -> authService.switchTenant(BEARER_ACCESS, switchRequest(targetTenantId), REFRESH_TOKEN));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(userDetailsService, tokenBlacklist);
    }

    @Test
    @DisplayName("refresh：令牌缺少 tenantId claim 时返回 401")
    void refreshShouldRejectTokenWithoutTenantClaim() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        when(tokenProvider.parse(REFRESH_TOKEN)).thenReturn(claims);

        BizException ex = assertThrows(BizException.class, () -> authService.refresh(REFRESH_TOKEN));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(userDetailsService, tokenBlacklist);
    }
}

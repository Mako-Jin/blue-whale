package com.bluewhale.auth.application.auth;

import com.bluewhale.auth.application.auth.command.LogoutCommand;
import com.bluewhale.auth.application.auth.command.RefreshCommand;
import com.bluewhale.auth.application.auth.command.SwitchTenantCommand;
import com.bluewhale.auth.application.auth.port.CredentialAuthenticator;
import com.bluewhale.auth.application.auth.port.CurrentUserProvider;
import com.bluewhale.auth.application.auth.port.RefreshTokenRotationStore;
import com.bluewhale.auth.application.auth.port.TokenBlacklist;
import com.bluewhale.auth.application.auth.port.TokenIssuer;
import com.bluewhale.auth.application.auth.port.UserAccessRevocationPort;
import com.bluewhale.auth.application.auth.port.UserSessionQuery;
import com.bluewhale.auth.application.auth.query.CurrentUserQuery;
import com.bluewhale.auth.application.auth.query.TenantListQuery;
import com.bluewhale.auth.application.auth.result.CurrentUserResult;
import com.bluewhale.auth.application.auth.result.LoginResult;
import com.bluewhale.auth.application.auth.result.TenantResult;
import com.bluewhale.auth.application.menu.MenuAppService;
import com.bluewhale.auth.application.permission.PermissionAppService;
import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.tenant.TenantMembershipAppService;
import com.bluewhale.auth.application.user.password.PasswordPolicyService;
import com.bluewhale.auth.application.user.password.TenantPasswordPolicyService;
import com.bluewhale.auth.application.user.port.PasswordHasher;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.application.vo.MenuVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SessionAppService} 单元测试：登出双令牌失效（REQ-SEC-004）、refreshToken 失效校验（T5.6）、
 * refreshToken 一次性轮换与复用检测（AUTH-39）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SessionAppServiceTest {

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
    private CredentialAuthenticator credentialAuthenticator;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private UserSessionQuery userSessionQuery;

    @Mock
    private UserRepository userRepository;

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
    private UserAccessRevocationPort userAccessRevocationPort;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TenantPasswordPolicyService tenantPasswordPolicyService;

    @InjectMocks
    private SessionAppService sessionAppService;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }


    /** 构造已完成签名校验的 refreshToken 载荷 */
    private static TokenPayload refreshPayload(Long tenantId, Long userId) {
        return new TokenPayload(USERNAME, tenantId, userId, TokenPayload.TYPE_REFRESH, null, null, 0L);
    }

    @Test
    @DisplayName("refresh：refreshToken 命中登出黑名单时返回 401，且不再查库")
    void refreshShouldRejectBlacklistedRefreshToken() {
        when(tokenIssuer.parse(REFRESH_TOKEN)).thenReturn(refreshPayload(TENANT_ID, USER_ID));
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(userSessionQuery);
    }

    @Test
    @DisplayName("refresh：accessToken 冒充 refreshToken 时返回 401")
    void refreshShouldRejectNonRefreshToken() {
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, TENANT_ID, USER_ID, "access", null, null, 0L));

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(tokenBlacklist);
    }

    @Test
    @DisplayName("refresh：未失效的 refreshToken 正常换发新令牌")
    void refreshShouldIssueNewTokensWhenNotBlacklisted() {
        when(tokenIssuer.parse(REFRESH_TOKEN)).thenReturn(refreshPayload(TENANT_ID, USER_ID));
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userSessionQuery.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false)));
        when(tokenIssuer.issueAccessToken(TENANT_ID, USER_ID, USERNAME, List.of("admin"), false))
                .thenReturn("new-access");
        // 轮换默认关闭：familyId 原样透传（载荷未携带即 null）
        when(tokenIssuer.issueRefreshToken(TENANT_ID, USER_ID, USERNAME, null)).thenReturn("new-refresh");
        when(tokenIssuer.accessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        LoginResult issue = sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN));

        assertEquals("new-access", issue.accessToken());
        assertEquals("new-refresh", issue.refreshToken());
        verifyNoInteractions(refreshRotationStore);
    }

    @Test
    @DisplayName("refresh：refreshToken 在用户级撤销前签发时返回 401，不换发新令牌")
    void refreshShouldRejectRefreshTokenIssuedBeforeUserRevocation() {
        long issuedAtMillis = 1_800_000_000_000L;
        Date issuedAt = new Date(issuedAtMillis);
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, TENANT_ID, USER_ID, TokenPayload.TYPE_REFRESH, null, null, issuedAtMillis));
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userSessionQuery.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false)));
        when(userAccessRevocationPort.isRevoked(USER_ID, issuedAt)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verify(tokenIssuer, never()).issueAccessToken(anyLong(), anyLong(), anyString(), any(), anyBoolean());
        verify(refreshRotationStore, never()).markUsed(anyString(), anyLong());
    }

    @Test
    @DisplayName("refresh：用户级撤销后签发的 refreshToken 正常换发新令牌")
    void refreshShouldIssueTokensWhenRefreshTokenIssuedAfterUserRevocation() {
        long issuedAtMillis = 1_800_000_000_000L;
        Date issuedAt = new Date(issuedAtMillis);
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, TENANT_ID, USER_ID, TokenPayload.TYPE_REFRESH, null, null, issuedAtMillis));
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userSessionQuery.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false)));
        when(userAccessRevocationPort.isRevoked(USER_ID, issuedAt)).thenReturn(false);
        when(tokenIssuer.issueAccessToken(TENANT_ID, USER_ID, USERNAME, List.of("admin"), false))
                .thenReturn("new-access");
        when(tokenIssuer.issueRefreshToken(TENANT_ID, USER_ID, USERNAME, null)).thenReturn("new-refresh");
        when(tokenIssuer.accessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        LoginResult issue = sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN));

        assertEquals("new-access", issue.accessToken());
        assertEquals("new-refresh", issue.refreshToken());
        verify(userAccessRevocationPort).isRevoked(USER_ID, issuedAt);
    }

    @Test
    @DisplayName("refresh：轮换开启时原子消费旧 refreshToken（入黑名单）并沿用 familyId 换发新令牌")
    void refreshShouldConsumeTokenWhenRotationEnabled() {
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, TENANT_ID, USER_ID, TokenPayload.TYPE_REFRESH, FAMILY_ID, JTI, 0L));
        when(refreshRotationProperties.isEnabled()).thenReturn(true);
        when(refreshRotationStore.isFamilyRevoked(FAMILY_ID)).thenReturn(false);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userSessionQuery.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false)));
        when(tokenIssuer.remainingValiditySeconds(REFRESH_TOKEN)).thenReturn(REMAINING_SECONDS);
        when(refreshRotationStore.markUsed(JTI, REMAINING_SECONDS)).thenReturn(true);
        when(tokenIssuer.issueAccessToken(TENANT_ID, USER_ID, USERNAME, List.of("admin"), false))
                .thenReturn("new-access");
        when(tokenIssuer.issueRefreshToken(TENANT_ID, USER_ID, USERNAME, FAMILY_ID)).thenReturn("new-refresh");
        when(tokenIssuer.accessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        LoginResult issue = sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN));

        assertEquals("new-access", issue.accessToken());
        assertEquals("new-refresh", issue.refreshToken());
        verify(tokenBlacklist).add(REFRESH_TOKEN);
        verify(refreshRotationStore, never()).revokeFamily(anyString(), anyLong());
    }

    @Test
    @DisplayName("refresh：复用检测命中（旧 refreshToken 二次使用）返回 401 并整族吊销 + 降级告警")
    void refreshShouldRevokeFamilyWhenReuseDetected() {
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, TENANT_ID, USER_ID, TokenPayload.TYPE_REFRESH, FAMILY_ID, JTI, 0L));
        when(refreshRotationProperties.isEnabled()).thenReturn(true);
        when(refreshRotationStore.isFamilyRevoked(FAMILY_ID)).thenReturn(false);
        when(tokenBlacklist.contains(REFRESH_TOKEN)).thenReturn(false);
        when(userSessionQuery.loadUserById(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false)));
        when(tokenIssuer.remainingValiditySeconds(REFRESH_TOKEN)).thenReturn(REMAINING_SECONDS);
        when(refreshRotationStore.markUsed(JTI, REMAINING_SECONDS)).thenReturn(false);
        when(tokenIssuer.refreshExpireSeconds()).thenReturn(REFRESH_EXPIRE_SECONDS);

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verify(refreshRotationStore).revokeFamily(FAMILY_ID, REFRESH_EXPIRE_SECONDS);
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("refresh：轮换开启但令牌缺少 familyId（旧令牌）返回 401")
    void refreshShouldRejectTokenWithoutFamilyWhenRotationEnabled() {
        when(tokenIssuer.parse(REFRESH_TOKEN)).thenReturn(refreshPayload(TENANT_ID, USER_ID));
        when(refreshRotationProperties.isEnabled()).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(refreshRotationStore, tokenBlacklist);
    }

    @Test
    @DisplayName("refresh：轮换开启但 family 已被吊销返回 401")
    void refreshShouldRejectRevokedFamily() {
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, TENANT_ID, USER_ID, TokenPayload.TYPE_REFRESH, FAMILY_ID, null, 0L));
        when(refreshRotationProperties.isEnabled()).thenReturn(true);
        when(refreshRotationStore.isFamilyRevoked(FAMILY_ID)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verify(tokenBlacklist, never()).add(anyString());
    }

    @Test
    @DisplayName("logout：accessToken 与 refreshToken 一并写入黑名单")
    void logoutShouldBlacklistBothTokens() {
        sessionAppService.logout(new LogoutCommand(BEARER_ACCESS, REFRESH_TOKEN));

        verify(tokenBlacklist).add(ACCESS_TOKEN);
        verify(tokenBlacklist).add(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("logout：未携带 refreshToken 时仅失效 accessToken")
    void logoutShouldBlacklistOnlyAccessTokenWhenRefreshAbsent() {
        sessionAppService.logout(new LogoutCommand(BEARER_ACCESS, null));

        verify(tokenBlacklist, times(1)).add(anyString());
        verify(tokenBlacklist).add(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("logout：Authorization 头格式不符时静默跳过，不写黑名单")
    void logoutShouldSkipWhenAuthorizationMalformed() {
        sessionAppService.logout(new LogoutCommand("Basic abc", null));

        verifyNoInteractions(tokenBlacklist);
    }

    @Test
    @DisplayName("me：内置超管授予全量权限码与全部启用可见菜单")
    void meShouldGrantAllForSuperAdmin() {
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Admin",
                List.of(AuthRoleCodes.SUPER_ADMIN), true, false));
        when(permissionAppService.listAllCodes()).thenReturn(List.of("sys:user:list", "sys:role:list"));
        when(menuAppService.treeByPermissionCodes(null)).thenReturn(List.of(new MenuVO()));

        CurrentUserResult response = sessionAppService.me(new CurrentUserQuery());

        assertEquals(List.of("sys:user:list", "sys:role:list"), response.permissions());
        assertEquals(1, response.menus().size());
        verifyNoInteractions(roleAppService);
    }

    @Test
    @DisplayName("me：普通用户按角色授权返回权限码并据此过滤菜单树")
    void meShouldReturnGrantedPermissionsForNormalUser() {
        when(currentUserProvider.current()).thenReturn(
                new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false));
        when(roleAppService.listPermissionCodesByUserId(USER_ID)).thenReturn(List.of("sys:user:list"));
        when(menuAppService.treeByPermissionCodes(Set.of("sys:user:list"))).thenReturn(List.of(new MenuVO()));

        CurrentUserResult response = sessionAppService.me(new CurrentUserQuery());

        assertEquals(List.of("sys:user:list"), response.permissions());
        assertEquals(TENANT_ID, response.tenantId());
        assertEquals(USER_ID, response.userId());
        assertEquals(List.of("admin"), response.roles());
        verifyNoInteractions(permissionAppService);
    }

    @Test
    @DisplayName("me：未认证上下文返回 401")
    void meShouldRejectAnonymous() {
        when(currentUserProvider.current()).thenThrow(new BizException(UNAUTHORIZED_CODE, "未认证"));

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.me(new CurrentUserQuery()));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
    }

    @Test
    @DisplayName("refresh：令牌缺少 userId claim 时返回 401")
    void refreshShouldRejectTokenWithoutUserClaim() {
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, TENANT_ID, null, TokenPayload.TYPE_REFRESH, null, null, 0L));

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(userSessionQuery, tokenBlacklist);
    }

    @Test
    @DisplayName("listTenants：按当前登录用户与当前租户返回可进入租户列表")
    void listTenantsShouldDelegateToMembershipService() {
        when(currentUserProvider.current()).thenReturn(
                new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false));
        TenantResult current = new TenantResult(TENANT_ID, "default", "默认租户", true);
        when(tenantMembershipAppService.listMyTenants(USER_ID, TENANT_ID)).thenReturn(List.of(current));

        List<TenantResult> tenants = sessionAppService.listTenants(new TenantListQuery());

        assertEquals(1, tenants.size());
        assertEquals(TENANT_ID, tenants.get(0).tenantId());
    }

    @Test
    @DisplayName("switchTenant：校验关系后按同一 userId 签发目标租户令牌，并失效旧 access/refresh")
    void switchTenantShouldIssueTokensForTargetTenant() {
        when(currentUserProvider.current()).thenReturn(
                new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false));
        long targetTenantId = 2L;
        when(userSessionQuery.loadUserById(USER_ID, targetTenantId)).thenReturn(
                Optional.of(new AuthenticatedUser(targetTenantId, USER_ID, USERNAME, "Alice", List.of("tenant_admin"), true, false)));
        when(tokenIssuer.issueAccessToken(targetTenantId, USER_ID, USERNAME, List.of("tenant_admin"), false))
                .thenReturn("new-access");
        when(tokenIssuer.issueRefreshToken(
                org.mockito.ArgumentMatchers.eq(targetTenantId),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(USERNAME),
                anyString())).thenReturn("new-refresh");
        when(tokenIssuer.accessExpireSeconds()).thenReturn(ACCESS_EXPIRE_SECONDS);

        LoginResult issue = sessionAppService.switchTenant(new SwitchTenantCommand(BEARER_ACCESS, targetTenantId, REFRESH_TOKEN));

        assertEquals(targetTenantId, issue.tenantId());
        assertEquals("new-access", issue.accessToken());
        assertEquals("new-refresh", issue.refreshToken());
        verify(tenantMembershipAppService).requireSwitchable(USER_ID, targetTenantId);
        verify(tokenBlacklist).add(ACCESS_TOKEN);
        verify(tokenBlacklist).add(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("switchTenant：无权进入目标租户时返回 403，不签发令牌")
    void switchTenantShouldRejectNonMember() {
        when(currentUserProvider.current()).thenReturn(
                new AuthenticatedUser(TENANT_ID, USER_ID, USERNAME, "Alice", List.of("admin"), true, false));
        long targetTenantId = 2L;
        doThrow(new BizException(403, "无权进入该租户"))
                .when(tenantMembershipAppService).requireSwitchable(USER_ID, targetTenantId);

        BizException ex = assertThrows(BizException.class,
                () -> sessionAppService.switchTenant(new SwitchTenantCommand(BEARER_ACCESS, targetTenantId, REFRESH_TOKEN)));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(userSessionQuery, tokenBlacklist);
    }

    @Test
    @DisplayName("refresh：令牌缺少 tenantId claim 时返回 401")
    void refreshShouldRejectTokenWithoutTenantClaim() {
        when(tokenIssuer.parse(REFRESH_TOKEN))
                .thenReturn(new TokenPayload(USERNAME, null, USER_ID, TokenPayload.TYPE_REFRESH, null, null, 0L));

        BizException ex = assertThrows(BizException.class, () -> sessionAppService.refresh(new RefreshCommand(REFRESH_TOKEN)));

        assertEquals(UNAUTHORIZED_CODE, ex.getCode());
        verifyNoInteractions(userSessionQuery, tokenBlacklist);
    }
}

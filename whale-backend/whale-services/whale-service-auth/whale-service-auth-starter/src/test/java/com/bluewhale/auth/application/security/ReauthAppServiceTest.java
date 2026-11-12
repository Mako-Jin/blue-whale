package com.bluewhale.auth.application.security;

import com.bluewhale.auth.application.model.AuthenticatedUser;
import com.bluewhale.auth.application.port.CredentialAuthenticator;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.ReauthTokenStore;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.model.result.ReauthResult;
import com.bluewhale.auth.application.service.LoginLockService;
import com.bluewhale.auth.application.service.ReauthAppService;
import com.bluewhale.auth.application.service.impl.ReauthAppServiceImpl;

/**
 * {@link ReauthAppService} 单元测试（Wave 6.22 / 6.27）
 *
 * <p>重点覆盖「二次认证与登录共用失败计数」：锁定期内即使口令正确也拒绝，
 * 口令错误时计入同一计数键。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("二次认证")
class ReauthAppServiceTest {

    /** 测试用租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试用用户 */
    private static final Long USER_ID = 2L;

    /** 测试用用户名 */
    private static final String USERNAME = "alice";

    /** 测试用明文口令 */
    private static final String RAW_PASSWORD = "secret1";

    /** 测试用凭证 */
    private static final String TOKEN = "reauth-token";

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private CredentialAuthenticator credentialAuthenticator;

    @Mock
    private ReauthTokenStore reauthTokenStore;

    @Mock
    private LoginLockService loginLockService;

    private ReauthAppService newService() {
        return new ReauthAppServiceImpl(currentUserProvider, credentialAuthenticator,
                reauthTokenStore, loginLockService);
    }

    private static AuthenticatedUser user(Long userId) {
        return new AuthenticatedUser(TENANT_ID, userId, USERNAME, "Alice", Set.of("admin"), true, false);
    }

    @Test
    @DisplayName("已锁定：直接拒绝且不再校验口令（锁定不能被 step-up 绕过）")
    void lockedUserIsRejectedBeforeAuthenticate() {
        when(currentUserProvider.current()).thenReturn(user(USER_ID));
        doThrow(new BizException(LoginLockService.LOCKED_CODE, "账号已锁定，请 5 分钟后再试"))
                .when(loginLockService).ensureUnlocked(TENANT_ID, USERNAME);

        BizException ex = assertThrows(BizException.class, () -> newService().reauthenticate(RAW_PASSWORD));

        assertEquals(LoginLockService.LOCKED_CODE, ex.getCode());
        verifyNoInteractions(credentialAuthenticator, reauthTokenStore);
    }

    @Test
    @DisplayName("口令错误：计入登录失败次数并原样抛出 401")
    void wrongPasswordCountsFailure() {
        when(currentUserProvider.current()).thenReturn(user(USER_ID));
        when(credentialAuthenticator.authenticate(TENANT_ID, USERNAME, RAW_PASSWORD))
                .thenThrow(new InvalidCredentialException("用户名或密码错误"));

        BizException ex = assertThrows(InvalidCredentialException.class,
                () -> newService().reauthenticate(RAW_PASSWORD));

        assertEquals(InvalidCredentialException.CODE, ex.getCode());
        verify(loginLockService).recordFailure(TENANT_ID, USERNAME);
        verify(loginLockService, never()).clear(anyLong(), anyString());
        verifyNoInteractions(reauthTokenStore);
    }

    @Test
    @DisplayName("口令正确：清除失败计数并发放凭证")
    void successClearsFailureAndIssuesToken() {
        when(currentUserProvider.current()).thenReturn(user(USER_ID));
        when(credentialAuthenticator.authenticate(TENANT_ID, USERNAME, RAW_PASSWORD)).thenReturn(user(USER_ID));
        when(reauthTokenStore.issue(TENANT_ID, USER_ID)).thenReturn(TOKEN);
        when(reauthTokenStore.ttlSeconds()).thenReturn(300L);

        ReauthResult result = newService().reauthenticate(RAW_PASSWORD);

        assertEquals(TOKEN, result.token());
        assertEquals(300L, result.expiresInSeconds());
        verify(loginLockService).clear(TENANT_ID, USERNAME);
    }

    @Test
    @DisplayName("认证结果与当前登录身份不一致：拒绝发放凭证")
    void mismatchedIdentityIsRejected() {
        when(currentUserProvider.current()).thenReturn(user(USER_ID));
        when(credentialAuthenticator.authenticate(TENANT_ID, USERNAME, RAW_PASSWORD)).thenReturn(user(99L));

        BizException ex = assertThrows(BizException.class, () -> newService().reauthenticate(RAW_PASSWORD));

        assertEquals(401, ex.getCode());
        verifyNoInteractions(reauthTokenStore);
    }
}

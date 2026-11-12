package com.bluewhale.auth.application.security;

import com.bluewhale.auth.application.port.LoginAttemptStore;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bluewhale.auth.application.service.LoginLockService;
import com.bluewhale.auth.application.service.impl.LoginLockServiceImpl;

/**
 * {@link LoginLockService} 单元测试（Wave 6.27）
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("登录失败锁定服务")
class LoginLockServiceTest {

    /** 测试用租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试用用户名 */
    private static final String USERNAME = "alice";

    @Mock
    private LoginAttemptStore loginAttemptStore;

    @Test
    @DisplayName("未锁定：放行且不抛异常")
    void ensureUnlockedPassesWhenNotLocked() {
        when(loginAttemptStore.lockRemainingSeconds(TENANT_ID, USERNAME)).thenReturn(0L);

        assertDoesNotThrow(() -> new LoginLockServiceImpl(loginAttemptStore).ensureUnlocked(TENANT_ID, USERNAME));
    }

    @Test
    @DisplayName("已锁定：抛 42301，剩余秒数向上取整为分钟")
    void ensureUnlockedThrowsWithRoundedMinutes() {
        when(loginAttemptStore.lockRemainingSeconds(TENANT_ID, USERNAME)).thenReturn(301L);

        BizException ex = assertThrows(BizException.class,
                () -> new LoginLockServiceImpl(loginAttemptStore).ensureUnlocked(TENANT_ID, USERNAME));

        assertEquals(LoginLockService.LOCKED_CODE, ex.getCode());
        assertEquals("账号已锁定，请 6 分钟后再试", ex.getMessage());
    }

    @Test
    @DisplayName("已锁定：不足 1 分钟也提示 1 分钟（不出现「请 0 分钟后再试」）")
    void ensureUnlockedRoundsUpToOneMinute() {
        when(loginAttemptStore.lockRemainingSeconds(TENANT_ID, USERNAME)).thenReturn(5L);

        BizException ex = assertThrows(BizException.class,
                () -> new LoginLockServiceImpl(loginAttemptStore).ensureUnlocked(TENANT_ID, USERNAME));

        assertEquals("账号已锁定，请 1 分钟后再试", ex.getMessage());
    }

    @Test
    @DisplayName("记录失败：未达上限不抛异常，只写入计数")
    void recordFailureBelowThresholdDoesNotThrow() {
        when(loginAttemptStore.lockRemainingSeconds(TENANT_ID, USERNAME)).thenReturn(0L);

        assertDoesNotThrow(() -> new LoginLockServiceImpl(loginAttemptStore).recordFailure(TENANT_ID, USERNAME));
        verify(loginAttemptStore).recordFailure(TENANT_ID, USERNAME);
    }

    @Test
    @DisplayName("记录失败：本次失败触达上限时直接抛锁定异常")
    void recordFailureThrowsWhenThresholdReached() {
        when(loginAttemptStore.lockRemainingSeconds(TENANT_ID, USERNAME)).thenReturn(900L);

        BizException ex = assertThrows(BizException.class,
                () -> new LoginLockServiceImpl(loginAttemptStore).recordFailure(TENANT_ID, USERNAME));

        assertEquals(LoginLockService.LOCKED_CODE, ex.getCode());
        assertEquals("账号已锁定，请 15 分钟后再试", ex.getMessage());
    }

    @Test
    @DisplayName("清除：委托存储删除计数")
    void clearDelegatesToStore() {
        new LoginLockServiceImpl(loginAttemptStore).clear(TENANT_ID, USERNAME);
        verify(loginAttemptStore).clear(TENANT_ID, USERNAME);
    }
}

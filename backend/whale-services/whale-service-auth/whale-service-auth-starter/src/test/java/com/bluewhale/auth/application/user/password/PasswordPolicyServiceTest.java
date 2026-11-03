package com.bluewhale.auth.application.user.password;

import com.bluewhale.auth.application.user.port.PasswordHasher;
import com.bluewhale.auth.domain.user.repository.PasswordHistoryRepository;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PasswordPolicyService} 单元测试（REQ-SEC-021 / REQ-SEC-023）。
 *
 * <p>覆盖强度校验委托、当前口令/历史口令复用拦截、历史记录写入与裁剪。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 2L;
    private static final String CURRENT_CIPHER = "$2a$10$currentciphervalue";
    private static final String HISTORY_CIPHER = "$2a$10$historyciphervalue";
    private static final String NEW_CIPHER = "$2a$10$newencodedciphervalue";
    private static final String STRONG_PASSWORD = "Str0ng@Passw0rd";

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    private PasswordPolicyProperties properties;

    private PasswordPolicyService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordPolicyProperties();
        service = new PasswordPolicyService(properties, passwordHasher, passwordHistoryRepository);
    }

    @Test
    @DisplayName("validate：按默认策略校验强度")
    void shouldDelegateStrengthValidation() {
        assertThrows(BizException.class, () -> service.validate("weak", "alice_01"));
    }

    @Test
    @DisplayName("ensureNotReused：新口令与当前口令相同则拒绝，且不查询历史")
    void shouldRejectCurrentPassword() {
        when(passwordHasher.matches(STRONG_PASSWORD, CURRENT_CIPHER)).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureNotReused(TENANT_ID, USER_ID, CURRENT_CIPHER, STRONG_PASSWORD));

        assertTrue(ex.getMessage().startsWith("新口令不得与当前口令相同"));
        verify(passwordHistoryRepository, never()).listRecent(TENANT_ID, USER_ID, 5);
    }

    @Test
    @DisplayName("ensureNotReused：命中最近历史口令则拒绝")
    void shouldRejectRecentHistoryPassword() {
        when(passwordHasher.matches(STRONG_PASSWORD, CURRENT_CIPHER)).thenReturn(false);
        when(passwordHistoryRepository.listRecent(TENANT_ID, USER_ID, 5)).thenReturn(List.of(HISTORY_CIPHER));
        when(passwordHasher.matches(STRONG_PASSWORD, HISTORY_CIPHER)).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureNotReused(TENANT_ID, USER_ID, CURRENT_CIPHER, STRONG_PASSWORD));

        assertTrue(ex.getMessage().contains("最近 5 次使用过的口令重复"));
    }

    @Test
    @DisplayName("ensureNotReused：历史未命中则通过")
    void shouldAcceptPasswordNotInHistory() {
        when(passwordHasher.matches(STRONG_PASSWORD, CURRENT_CIPHER)).thenReturn(false);
        when(passwordHistoryRepository.listRecent(TENANT_ID, USER_ID, 5)).thenReturn(List.of(HISTORY_CIPHER));
        when(passwordHasher.matches(STRONG_PASSWORD, HISTORY_CIPHER)).thenReturn(false);

        service.ensureNotReused(TENANT_ID, USER_ID, CURRENT_CIPHER, STRONG_PASSWORD);

        verify(passwordHasher).matches(STRONG_PASSWORD, HISTORY_CIPHER);
    }

    @Test
    @DisplayName("ensureNotReused：history-size=0 时跳过历史查询")
    void shouldSkipHistoryWhenHistorySizeZero() {
        properties.setHistorySize(0);
        when(passwordHasher.matches(STRONG_PASSWORD, CURRENT_CIPHER)).thenReturn(false);

        service.ensureNotReused(TENANT_ID, USER_ID, CURRENT_CIPHER, STRONG_PASSWORD);

        verifyNoInteractions(passwordHistoryRepository);
    }

    @Test
    @DisplayName("recordHistory：先追加再按策略裁剪")
    void shouldAppendAndTrimHistory() {
        service.recordHistory(TENANT_ID, USER_ID, NEW_CIPHER);

        verify(passwordHistoryRepository).append(TENANT_ID, USER_ID, NEW_CIPHER);
        verify(passwordHistoryRepository).trim(TENANT_ID, USER_ID, 5);
    }

    @Test
    @DisplayName("策略读数：encode / 强制改密 / 有效期 / 最小长度 / 提醒天数")
    void shouldExposePolicyValues() {
        when(passwordHasher.encode(STRONG_PASSWORD)).thenReturn(NEW_CIPHER);

        assertTrue(service.encode(STRONG_PASSWORD).contains(NEW_CIPHER));
        assertTrue(service.isForceChangeOnCreate());
        assertTrue(service.maxAgeDays() > 0);
        assertTrue(service.minLength() > 0);
        assertTrue(service.warnDays() >= 0);
    }
}

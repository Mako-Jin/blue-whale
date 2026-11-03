package com.bluewhale.auth.infrastructure.password;

import com.bluewhale.auth.application.user.password.TenantPasswordPolicy;
import com.bluewhale.auth.application.user.password.TenantPasswordPolicyService;
import com.bluewhale.auth.application.user.password.port.PasswordExpiryQuery;
import com.bluewhale.auth.application.user.password.port.PasswordExpiryQuery.PasswordExpiryCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PasswordExpiryNoticeJob} 单元测试（REQ-SEC-023）：多租户口令到期提醒。
 *
 * <p>覆盖最短提醒窗口一次性扫描、按候选账号所属租户策略二次筛选、提醒时间回写与异常隔离。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PasswordExpiryNoticeJobTest {

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final long TENANT_UNKNOWN = 3L;
    private static final long USER_A = 11L;
    private static final long USER_B = 21L;
    private static final int MAX_AGE_A = 90;
    private static final int WARN_A = 7;
    private static final int MAX_AGE_B = 30;
    private static final int WARN_B = 5;
    /** 租户 A 提醒窗口：90 - 7 = 83 天；租户 B：30 - 5 = 25 天（更短） */
    private static final int MIN_WINDOW_DAYS = 25;

    @Mock
    private PasswordExpiryQuery passwordExpiryQuery;

    @Mock
    private TenantPasswordPolicyService tenantPasswordPolicyService;

    @InjectMocks
    private PasswordExpiryNoticeJob job;

    /**
     * 构造多租户生效策略（A：90/7，B：30/5）
     *
     * @return 租户 ID → 生效策略
     */
    private static Map<Long, TenantPasswordPolicy> policies() {
        return Map.of(
                TENANT_A, new TenantPasswordPolicy(TENANT_A, MAX_AGE_A, WARN_A),
                TENANT_B, new TenantPasswordPolicy(TENANT_B, MAX_AGE_B, WARN_B));
    }

    /**
     * 构造候选账号
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param updatedDaysAgo 距今天数的改密时间
     * @return 候选账号
     */
    private static PasswordExpiryCandidate candidate(long tenantId, long userId, long updatedDaysAgo) {
        return new PasswordExpiryCandidate(tenantId, userId, "user" + userId,
                LocalDateTime.now().minusDays(updatedDaysAgo));
    }

    @Test
    @DisplayName("全部租户均未启用有效期：直接返回，不扫描账号")
    void shouldSkipWhenNoTenantEnforcesExpiry() {
        when(tenantPasswordPolicyService.effectivePolicies())
                .thenReturn(Map.of(TENANT_A, new TenantPasswordPolicy(TENANT_A, 0, 0)));

        job.notifyExpiringPasswords();

        verifyNoInteractions(passwordExpiryQuery);
    }

    @Test
    @DisplayName("按最短提醒窗口一次性扫描，再按候选账号所属租户策略二次筛选")
    void shouldFilterByTenantWindow() {
        when(tenantPasswordPolicyService.effectivePolicies()).thenReturn(policies());
        when(passwordExpiryQuery.findCandidates(any())).thenReturn(List.of(
                candidate(TENANT_A, USER_A, MAX_AGE_A - WARN_A - 10),
                candidate(TENANT_B, USER_B, MAX_AGE_B - WARN_B + 3)));

        job.notifyExpiringPasswords();

        ArgumentCaptor<LocalDateTime> remindFrom = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(passwordExpiryQuery).findCandidates(remindFrom.capture());
        assertTrue(remindFrom.getValue().isBefore(LocalDateTime.now().minusDays(MIN_WINDOW_DAYS - 1)));
        // 租户 A 账号尚未进入 83 天窗口，不提醒；租户 B 账号已进入 25 天窗口，提醒并回写时间
        verify(passwordExpiryQuery, never()).markNoticed(eq(USER_A), any());
        verify(passwordExpiryQuery).markNoticed(eq(USER_B), any());
    }

    @Test
    @DisplayName("已过期账号同样告警并回写提醒时间")
    void shouldNotifyOverdueAccount() {
        when(tenantPasswordPolicyService.effectivePolicies()).thenReturn(policies());
        when(passwordExpiryQuery.findCandidates(any()))
                .thenReturn(List.of(candidate(TENANT_B, USER_B, MAX_AGE_B + 5)));

        job.notifyExpiringPasswords();

        verify(passwordExpiryQuery).markNoticed(eq(USER_B), any());
    }

    @Test
    @DisplayName("候选账号所属租户无策略或改密时间为空：跳过且不回写提醒时间")
    void shouldSkipCandidateWithoutPolicyOrUpdatedTime() {
        when(tenantPasswordPolicyService.effectivePolicies()).thenReturn(policies());
        when(passwordExpiryQuery.findCandidates(any())).thenReturn(List.of(
                candidate(TENANT_UNKNOWN, USER_A, MAX_AGE_B),
                new PasswordExpiryCandidate(TENANT_B, USER_B, "user21", null)));

        job.notifyExpiringPasswords();

        verify(passwordExpiryQuery, never()).markNoticed(any(), any());
    }

    @Test
    @DisplayName("无候选账号：不触发提醒回写")
    void shouldSkipWhenNoCandidates() {
        when(tenantPasswordPolicyService.effectivePolicies()).thenReturn(policies());
        when(passwordExpiryQuery.findCandidates(any())).thenReturn(List.of());

        job.notifyExpiringPasswords();

        verify(passwordExpiryQuery, never()).markNoticed(any(), any());
    }

    @Test
    @DisplayName("任务异常不向调度线程抛出")
    void shouldSwallowException() {
        when(tenantPasswordPolicyService.effectivePolicies())
                .thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(job::notifyExpiringPasswords);
    }
}

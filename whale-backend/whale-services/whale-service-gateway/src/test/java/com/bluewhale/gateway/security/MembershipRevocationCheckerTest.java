package com.bluewhale.gateway.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TenantMembershipRevocationKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link MembershipRevocationChecker} 单元测试：撤销时间比较、键约定、Redis 降级。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MembershipRevocationCheckerTest {

    private static final long TENANT_ID = 2L;
    private static final long USER_ID = 1251323419503984640L;
    private static final long ISSUED_AT_SECONDS = 1_800_000_000L;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private MembershipRevocationChecker checker;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    private static Date issuedAt() {
        return new Date(ISSUED_AT_SECONDS * 1000L);
    }

    @Test
    @DisplayName("isRevoked：令牌签发时间不晚于撤销时间时返回 true，且键与 auth 侧约定一致")
    void shouldReturnTrueWhenTokenIssuedBeforeRevocation() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(TenantMembershipRevocationKeys
                .revocationKey(TENANT_ID, USER_ID)))
                .thenReturn(Mono.just(String.valueOf(ISSUED_AT_SECONDS)));

        assertTrue(checker.isRevoked(String.valueOf(TENANT_ID), String.valueOf(USER_ID), issuedAt()).block());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(keyCaptor.capture());
        assertEquals(TenantMembershipRevocationKeys.revocationKey(TENANT_ID, USER_ID),
                keyCaptor.getValue(), "网关使用的键必须与 whale-service-auth 写入侧一致");
    }

    @Test
    @DisplayName("isRevoked：解绑后同一秒签发的新令牌（毫秒级 iatMs）返回 false")
    void shouldReturnFalseWhenTokenIssuedAfterMillisecondRevocation() {
        long revokedAtMillis = ISSUED_AT_SECONDS * 1000L + 100L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenReturn(Mono.just(String.valueOf(revokedAtMillis)));

        assertFalse(checker.isRevoked(String.valueOf(TENANT_ID), String.valueOf(USER_ID),
                new Date(revokedAtMillis + 1L)).block());
    }

    @Test
    @DisplayName("isRevoked：撤销时间早于签发时间（重新绑定后新令牌）返回 false")
    void shouldReturnFalseWhenTokenIssuedAfterRevocation() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenReturn(Mono.just(String.valueOf(ISSUED_AT_SECONDS - 10L)));

        assertFalse(checker.isRevoked(String.valueOf(TENANT_ID), String.valueOf(USER_ID), issuedAt()).block());
    }

    @Test
    @DisplayName("isRevoked：撤销键不存在时返回 false")
    void shouldReturnFalseWhenKeyAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        assertFalse(checker.isRevoked(String.valueOf(TENANT_ID), String.valueOf(USER_ID), issuedAt()).block());
    }

    @Test
    @DisplayName("isRevoked：Redis 不可用时按未撤销降级放行并自增告警计数")
    void shouldReturnFalseAndRaiseAlarmWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        assertFalse(checker.isRevoked(String.valueOf(TENANT_ID), String.valueOf(USER_ID), issuedAt()).block());
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("isRevoked：非法租户/用户/签发时间返回 false，且不访问 Redis")
    void shouldReturnFalseWhenClaimsInvalid() {
        assertFalse(checker.isRevoked("bad", String.valueOf(USER_ID), issuedAt()).block());
        assertFalse(checker.isRevoked(String.valueOf(TENANT_ID), "bad", issuedAt()).block());
        assertFalse(checker.isRevoked(String.valueOf(TENANT_ID), String.valueOf(USER_ID), null).block());
        verifyNoInteractions(redisTemplate);
    }
}

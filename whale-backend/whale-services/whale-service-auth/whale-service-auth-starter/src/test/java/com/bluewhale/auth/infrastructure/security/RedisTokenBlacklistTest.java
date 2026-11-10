package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.infrastructure.jwt.JwtTokenProvider;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RedisTokenBlacklist} 单元测试：正常写入、TTL 边界、Redis 降级、命中判断（REQ-QA-002 / T5.19）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.signature";
    private static final String KEY_PREFIX = "auth:token:blacklist:";
    private static final String PLACEHOLDER_VALUE = "1";
    private static final long VALID_TTL_SECONDS = 600L;
    private static final String SHA256_HEX_PATTERN = "[0-9a-f]{64}";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private RedisTokenBlacklist blacklist;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    @Test
    @DisplayName("add：有效期内 token 以 SHA-256 摘要为键写入，TTL 取剩余有效期")
    void addShouldWriteDigestKeyWithRemainingTtl() {
        when(tokenProvider.getRemainingValiditySeconds(TOKEN)).thenReturn(VALID_TTL_SECONDS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        blacklist.add(TOKEN);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq(PLACEHOLDER_VALUE),
                eq(VALID_TTL_SECONDS), eq(TimeUnit.SECONDS));

        String key = keyCaptor.getValue();
        assertTrue(key.startsWith(KEY_PREFIX), "键应以固定前缀开头");
        assertTrue(key.substring(KEY_PREFIX.length()).matches(SHA256_HEX_PATTERN),
                "键后缀应为 64 位十六进制 SHA-256 摘要（不落 token 明文）");
    }

    @Test
    @DisplayName("add：TTL 小于最小阈值（已过期 token）时跳过写入")
    void addShouldSkipWriteWhenTtlBelowMinimum() {
        when(tokenProvider.getRemainingValiditySeconds(TOKEN)).thenReturn(0L);

        blacklist.add(TOKEN);

        verifyNoInteractions(redisTemplate, valueOperations);
    }

    @Test
    @DisplayName("add：Redis 不可用时仅告警不抛异常（可用性优先降级）")
    void addShouldNotThrowWhenRedisUnavailable() {
        when(tokenProvider.getRemainingValiditySeconds(TOKEN)).thenReturn(VALID_TTL_SECONDS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertDoesNotThrow(() -> blacklist.add(TOKEN));
    }

    @Test
    @DisplayName("contains：键存在返回 true")
    void containsShouldReturnTrueWhenKeyExists() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.TRUE);

        assertTrue(blacklist.contains(TOKEN));
    }

    @Test
    @DisplayName("contains：键不存在或返回值缺失时返回 false")
    void containsShouldReturnFalseWhenKeyAbsent() {
        when(redisTemplate.hasKey(anyString())).thenReturn(null);

        assertFalse(blacklist.contains(TOKEN));
    }

    @Test
    @DisplayName("contains：Redis 不可用时按未命中处理，不抛异常")
    void containsShouldReturnFalseWhenRedisUnavailable() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertFalse(blacklist.contains(TOKEN));
    }

    @Test
    @DisplayName("contains：Redis 不可用时降级告警计数自增（AUTH-05）")
    void containsShouldRaiseDegradationAlarmWhenRedisUnavailable() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertFalse(blacklist.contains(TOKEN));
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("键稳定性：同一 token 摘要一致，不同 token 键不同")
    void keysShouldBeStableForSameTokenAndDistinctForDifferentTokens() {
        when(tokenProvider.getRemainingValiditySeconds(anyString())).thenReturn(VALID_TTL_SECONDS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        blacklist.add(TOKEN);
        blacklist.add(TOKEN);
        blacklist.add(TOKEN + "-other");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(3)).set(keyCaptor.capture(), eq(PLACEHOLDER_VALUE),
                eq(VALID_TTL_SECONDS), eq(TimeUnit.SECONDS));

        List<String> keys = keyCaptor.getAllValues();
        assertEquals(keys.get(0), keys.get(1), "同一 token 生成的键应完全一致");
        assertNotEquals(keys.get(0), keys.get(2), "不同 token 生成的键应不同");
    }
}

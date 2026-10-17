package com.bluewhale.gateway.security;

import com.bluewhale.security.token.TokenBlacklistKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TokenBlacklistChecker} 单元测试：命中判定、键约定一致性、Redis 降级（REQ-SEC-003 / T5.7）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistCheckerTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.signature";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @InjectMocks
    private TokenBlacklistChecker checker;

    @Test
    @DisplayName("isBlacklisted：键存在返回 true，且键前缀与 auth 侧约定一致")
    void shouldReturnTrueWhenKeyExists() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(Boolean.TRUE));

        assertTrue(checker.isBlacklisted(TOKEN).block());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).hasKey(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().startsWith(TokenBlacklistKeys.KEY_PREFIX),
                "网关使用的键必须与 whale-auth 写入侧前缀一致");
    }

    @Test
    @DisplayName("isBlacklisted：键不存在（值为 false）返回 false")
    void shouldReturnFalseWhenKeyAbsent() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(Boolean.FALSE));

        assertFalse(checker.isBlacklisted(TOKEN).block());
    }

    @Test
    @DisplayName("isBlacklisted：结果为空（无发射）时按未命中处理")
    void shouldReturnFalseWhenResultEmpty() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.empty());

        assertFalse(checker.isBlacklisted(TOKEN).block());
    }

    @Test
    @DisplayName("isBlacklisted：Redis 不可用时按未命中降级放行，不抛异常")
    void shouldReturnFalseWhenRedisUnavailable() {
        when(redisTemplate.hasKey(anyString()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        assertFalse(checker.isBlacklisted(TOKEN).block());
    }
}

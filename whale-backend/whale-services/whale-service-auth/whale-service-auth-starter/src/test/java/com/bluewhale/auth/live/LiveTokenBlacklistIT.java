package com.bluewhale.auth.live;

import com.bluewhale.auth.infrastructure.config.JwtProperties;
import com.bluewhale.auth.infrastructure.jwt.JwtKeyMaterial;
import com.bluewhale.auth.infrastructure.jwt.JwtTokenProvider;
import com.bluewhale.auth.infrastructure.security.RedisTokenBlacklist;
import com.bluewhale.security.token.TokenBlacklistKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 令牌黑名单的实时集成测试（真实 Redis 5.x @ 127.0.0.1:6379）。
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\backend}）：</p>
 * <pre>
 * mvn -B -pl whale-services/whale-service-auth/whale-service-auth-starter -am test -Dwhale.live.it=true -Dtest=LiveTokenBlacklistIT -DfailIfNoTests=false
 * </pre>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LiveTokenBlacklistIT {

    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;
    private static final long SIXTY_SECONDS = 60L;
    private static final long TTL_TOLERANCE = 10L;
    private static final int SHA256_HEX_LENGTH = 64;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static JwtTokenProvider tokenProvider;
    private static RedisTokenBlacklist blacklist;

    @BeforeAll
    static void setUpClass() {
        connectionFactory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // accessToken 有效期 60s → 黑名单键 TTL ≈ 60s（与任务口径一致）
        JwtProperties properties = new JwtProperties();
        properties.setExpireMinutes(1);
        properties.setRefreshExpireMinutes(1);
        tokenProvider = new JwtTokenProvider(properties, new JwtKeyMaterial(properties));
        blacklist = new RedisTokenBlacklist(redisTemplate, tokenProvider);
    }

    @AfterAll
    static void tearDownClass() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("有效期内 token：写入摘要键（TTL>0），contains=true，键前缀/摘要长度符合约定")
    void shouldWriteDigestKeyWithPositiveTtlAndHit() {
        String token = tokenProvider.generateAccessToken(1L, 1L, "admin", List.of("super_admin"), false);
        long remaining = tokenProvider.getRemainingValiditySeconds(token);
        assertThat(remaining).isBetween(SIXTY_SECONDS - TTL_TOLERANCE, SIXTY_SECONDS);

        blacklist.add(token);

        assertThat(blacklist.contains(token)).isTrue();

        String key = TokenBlacklistKeys.blacklistKey(token);
        assertThat(key).startsWith(TokenBlacklistKeys.KEY_PREFIX);
        assertThat(key.substring(TokenBlacklistKeys.KEY_PREFIX.length())).hasSize(SHA256_HEX_LENGTH)
                .matches("[0-9a-f]{64}");
        // 键名不含 token 明文，避免凭据落入 Redis keyspace
        assertThat(key).doesNotContain(token);
        assertThat(redisTemplate.hasKey(key)).isTrue();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("1");
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(SIXTY_SECONDS);
    }

    @Test
    @DisplayName("跨实例共享：另建一个黑名单实例（同一 Redis）同样命中")
    void shouldShareBlacklistAcrossInstances() {
        String token = tokenProvider.generateRefreshToken(1L, 1L, "admin", null);
        blacklist.add(token);

        StringRedisTemplate otherTemplate = new StringRedisTemplate(connectionFactory);
        RedisTokenBlacklist otherInstance = new RedisTokenBlacklist(otherTemplate, tokenProvider);

        assertThat(otherInstance.contains(token)).isTrue();
    }

    @Test
    @DisplayName("TTL 已过期/剩余<1s 的 token 不写入黑名单")
    void shouldSkipWriteWhenTokenExpired() {
        JwtProperties expiredProperties = new JwtProperties();
        expiredProperties.setExpireMinutes(0);
        expiredProperties.setRefreshExpireMinutes(0);
        JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProperties,
                new JwtKeyMaterial(expiredProperties));
        String expiredToken = expiredProvider.generateAccessToken(1L, 1L, "admin", List.of(), false);

        assertThat(tokenProvider.getRemainingValiditySeconds(expiredToken)).isLessThan(1L);

        blacklist.add(expiredToken);

        String key = TokenBlacklistKeys.blacklistKey(expiredToken);
        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertThat(blacklist.contains(expiredToken)).isFalse();
    }
}

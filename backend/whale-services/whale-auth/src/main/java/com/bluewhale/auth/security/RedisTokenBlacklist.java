package com.bluewhale.auth.security;

import com.bluewhale.auth.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的令牌黑名单实现。
 * <p>键结构：{@code auth:token:blacklist:<sha256(token)>}，值为固定占位符，
 * 仅依赖键是否存在判断命中；使用摘要而非 token 原文作为键名，避免明文凭据落入 Redis keyspace。</p>
 * <p>降级策略：Redis 不可用时 {@link #add} 仅告警不抛异常，{@link #contains} 返回未命中，
 * 保证鉴权链路整体可用（与 whale-i18n-sdk 的 Redis 降级策略一致）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklist implements TokenBlacklist {

    /** 黑名单键前缀，便于运维按前缀批量查看/清理 */
    private static final String KEY_PREFIX = "auth:token:blacklist:";

    /** 键值占位符：命中与否只看键是否存在 */
    private static final String PLACEHOLDER_VALUE = "1";

    /** 最小写入 TTL（秒），避免出现 TTL<=0 导致键永不过期 */
    private static final long MIN_TTL_SECONDS = 1L;

    /** 摘要算法：SHA-256 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    @Override
    public void add(String token) {
        long ttlSeconds = tokenProvider.getRemainingValiditySeconds(token);
        if (ttlSeconds < MIN_TTL_SECONDS) {
            // token 已过期，本身不再可用，无需占用黑名单空间
            return;
        }
        try {
            redisTemplate.opsForValue().set(buildKey(token), PLACEHOLDER_VALUE, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("令牌写入黑名单失败（Redis 不可用），本次登出退化为客户端丢弃 token: {}", e.getMessage());
        }
    }

    @Override
    public boolean contains(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(token)));
        } catch (RuntimeException e) {
            log.warn("令牌黑名单查询失败（Redis 不可用），按未命中处理: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 构造黑名单键：token 经 SHA-256 摘要后十六进制编码
     */
    private static String buildKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // JDK 标准实现必然支持 SHA-256，此处为不可达分支
            throw new IllegalStateException(DIGEST_ALGORITHM + " 算法不可用", e);
        }
    }
}

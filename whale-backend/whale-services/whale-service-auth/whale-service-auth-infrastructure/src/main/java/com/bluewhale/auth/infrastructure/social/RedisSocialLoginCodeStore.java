package com.bluewhale.auth.infrastructure.social;

import com.bluewhale.auth.application.model.social.SocialLoginCodeRecord;
import com.bluewhale.auth.application.port.SocialLoginCodeStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 一次性登录码 Redis 存储（权限中心扩展 M2）。
 *
 * <p>消费用 {@code delete} 返回值保证一次性语义：并发换码只有一方删除成功，
 * 其余一律拒绝（防重放）。序列化用 Jackson JSON，TTL 到期自动失效。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSocialLoginCodeStore implements SocialLoginCodeStore {

    /** key 前缀 */
    private static final String KEY_PREFIX = "auth:social:login-code:";
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 保存登录码（带 TTL）
     *
     * @param loginCode 登录码
     * @param record 载荷
     * @param ttlSeconds 有效期（秒）
     */
    @Override
    public void save(String loginCode, SocialLoginCodeRecord record, long ttlSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + loginCode,
                    objectMapper.writeValueAsString(record), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw new IllegalStateException("登录码写入 Redis 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 一次性消费登录码（原子删除防重放）
     *
     * @param loginCode 登录码
     * @return 载荷；不存在、已过期或已被消费返回空
     */
    @Override
    public Optional<SocialLoginCodeRecord> consume(String loginCode) {
        String key = KEY_PREFIX + loginCode;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        Boolean deleted = stringRedisTemplate.delete(key);
        if (!Boolean.TRUE.equals(deleted)) {
            log.warn("登录码并发消费冲突（疑似重放）");
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SocialLoginCodeRecord.class));
        } catch (Exception e) {
            throw new IllegalStateException("登录码载荷反序列化失败: " + e.getMessage(), e);
        }
    }
}

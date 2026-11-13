package com.bluewhale.auth.infrastructure.social;

import com.bluewhale.auth.application.model.social.SocialStateRecord;
import com.bluewhale.auth.application.port.SocialStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * 三方授权 state Redis 存储（权限中心扩展 M2）。
 *
 * <p>回调裁决「先 load 校验渠道一致性、再一次性 consume」的两段式语义由此支撑：
 * {@code consume} 用 {@code delete} 返回值保证原子性，重复回调第二个到达者失败。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSocialStateStore implements SocialStateStore {

    /** key 前缀 */
    private static final String KEY_PREFIX = "auth:social:state:";
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 保存 state 载荷（带 TTL）
     *
     * @param state state 随机串
     * @param record state 业务载荷
     * @param ttlSeconds 有效期（秒）
     */
    @Override
    public void save(String state, SocialStateRecord record, long ttlSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + state,
                    objectMapper.writeValueAsString(record), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw new IllegalStateException("state 写入 Redis 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取 state 载荷（不消费）
     *
     * @param state state 随机串
     * @return state 载荷；不存在或已过期返回空
     */
    @Override
    public Optional<SocialStateRecord> load(String state) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + state);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SocialStateRecord.class));
        } catch (Exception e) {
            throw new IllegalStateException("state 载荷反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 一次性消费 state（原子删除，重复回调第二个到达者失败）
     *
     * @param state state 随机串
     * @return 消费成功返回 {@code true}
     */
    @Override
    public boolean consume(String state) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(KEY_PREFIX + state));
    }
}

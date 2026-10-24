package com.bluewhale.im.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 在线状态服务。
 *
 * <p>在线状态由「连接 + 心跳」共同维护：Redis 键 {@code whale:im:presence:} 前缀加用户ID，
 * TTL 默认 90 秒，连接建立、心跳与消息帧到达时续期；最后一个连接断开时立即删除。
 * 该状态仅用于展示，离线不阻塞消息入库。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class PresenceService {

    /** 在线状态键前缀（REQ-ARCH-004 命名：whale:域:用途:标识） */
    private static final String PRESENCE_KEY_PREFIX = "whale:im:presence:";

    /** 在线标记值 */
    private static final String ONLINE_VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    private final long presenceTtlSeconds;

    /**
     * 构造在线状态服务。
     *
     * @param redisTemplate     Redis 客户端
     * @param presenceTtlSeconds 在线状态 TTL（秒）
     */
    public PresenceService(StringRedisTemplate redisTemplate,
                           @Value("${whale.im.ws.presence-ttl-seconds:90}") long presenceTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.presenceTtlSeconds = presenceTtlSeconds;
    }

    /**
     * 标记在线并续期。
     *
     * @param userId 用户ID
     */
    public void touch(Long userId) {
        try {
            redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userId, ONLINE_VALUE,
                    Duration.ofSeconds(presenceTtlSeconds));
        } catch (RuntimeException e) {
            log.warn("在线状态续期失败: userId={}", userId, e);
        }
    }

    /**
     * 标记离线（最后一个连接断开时调用）。
     *
     * @param userId 用户ID
     */
    public void offline(Long userId) {
        try {
            redisTemplate.delete(PRESENCE_KEY_PREFIX + userId);
        } catch (RuntimeException e) {
            log.warn("在线状态清除失败: userId={}", userId, e);
        }
    }
}

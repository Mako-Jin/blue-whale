package com.bluewhale.video.infrastructure.redis;

import com.bluewhale.video.domain.model.PlayTicket;
import com.bluewhale.video.domain.repository.PlayTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 播放票据 Redis 实现。
 *
 * <p>key 约定 {@code whale:video:play:ticket:{ticket}}，值为
 * {@code tenantId|userId|contentId}，TTL 默认 300 秒；每次流式请求都实时读取，
 * 过期即不可用。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RedisPlayTicketRepository implements PlayTicketRepository {

    /** 票据缓存 key 前缀 */
    private static final String TICKET_KEY_PREFIX = "whale:video:play:ticket:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void issue(PlayTicket ticket, int ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key(ticket.getTicket()), ticket.toValue(),
                Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<PlayTicket> find(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        String value = stringRedisTemplate.opsForValue().get(key(ticket));
        return Optional.ofNullable(PlayTicket.parse(ticket, value));
    }

    @Override
    public void revoke(String ticket) {
        if (ticket != null && !ticket.isBlank()) {
            stringRedisTemplate.delete(key(ticket));
        }
    }

    /**
     * 拼装票据缓存 key。
     *
     * @param ticket 票据令牌
     * @return Redis key
     */
    private String key(String ticket) {
        return TICKET_KEY_PREFIX + ticket;
    }
}

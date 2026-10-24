package com.bluewhale.im.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.im.vo.WsTicketVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * WebSocket 一次性凭证服务。
 *
 * <p>安全红线（规划 §5.4）：浏览器 WS 无法携带 {@code Authorization} 头，禁止把 JWT 放进 URL；
 * 已登录用户先经标准 JWT 调用 {@code POST /im/ws-ticket} 换取随机 ticket，服务端写入
 * Redis 键 {@code whale:im:ticket:} 前缀加随机串（默认 60 秒、绑定 userId），握手时原子取删，保证一次性。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class WsTicketService {

    /** ticket 键前缀（REQ-ARCH-004 命名：whale:域:用途:标识） */
    private static final String TICKET_KEY_PREFIX = "whale:im:ticket:";

    private final StringRedisTemplate redisTemplate;

    private final long ticketTtlSeconds;

    /**
     * 构造 ticket 服务。
     *
     * @param redisTemplate    Redis 客户端
     * @param ticketTtlSeconds ticket 有效期（秒）
     */
    public WsTicketService(StringRedisTemplate redisTemplate,
                           @Value("${whale.im.ws.ticket-ttl-seconds:60}") long ticketTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    /**
     * 签发一次性 ticket 并绑定当前用户。
     *
     * <p>Redis 不可用时失败关闭（不签发不落盘），避免绕过一次性凭证直接建立通道。</p>
     *
     * @param userId 当前用户ID
     * @return ticket 视图对象
     */
    public WsTicketVO issue(Long userId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket, String.valueOf(userId),
                    Duration.ofSeconds(ticketTtlSeconds));
        } catch (RuntimeException e) {
            log.error("WS ticket 写入失败: userId={}", userId, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "即时通讯通道暂不可用，请稍后重试");
        }
        return WsTicketVO.builder().ticket(ticket).expiresIn(ticketTtlSeconds).build();
    }

    /**
     * 消费 ticket（原子取删，保证一次性）。
     *
     * @param ticket 客户端提交的 ticket
     * @return 绑定的用户ID；缺失、已使用、已过期或 Redis 异常时返回 {@code null}
     */
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().getAndDelete(TICKET_KEY_PREFIX + ticket);
            return value == null ? null : Long.valueOf(value);
        } catch (RuntimeException e) {
            log.warn("WS ticket 消费失败: {}", e.getMessage());
            return null;
        }
    }
}

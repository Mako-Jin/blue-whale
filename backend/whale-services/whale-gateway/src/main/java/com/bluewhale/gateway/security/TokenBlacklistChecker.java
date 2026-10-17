package com.bluewhale.gateway.security;

import com.bluewhale.security.token.TokenBlacklistKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 网关侧令牌黑名单校验（REQ-SEC-003）。
 * <p>使用响应式 Redis（WebFlux 栈不可用阻塞式客户端），键由 {@link TokenBlacklistKeys} 与
 * whale-auth 写入侧共用，保证登出写入与网关校验指向同一键。</p>
 * <p>降级策略：Redis 不可用时仅告警并按「未命中」放行，避免黑名单存储故障阻断全站流量
 * （与 whale-auth 侧「可用性优先」取舍一致）。生产需对 Redis 可用性监控告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TokenBlacklistChecker {

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 构造校验器，注入响应式 Redis 模板
     *
     * @param redisTemplate 响应式字符串 Redis 模板
     */
    public TokenBlacklistChecker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断令牌是否已在登出黑名单中
     *
     * @param token 待校验的 accessToken 原文
     * @return 命中返回 {@code true}；Redis 不可用时返回 {@code false}（降级放行）
     */
    public Mono<Boolean> isBlacklisted(String token) {
        return redisTemplate.hasKey(TokenBlacklistKeys.blacklistKey(token))
                .defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(e -> {
                    log.warn("网关查询令牌黑名单失败（Redis 不可用），按未命中放行: {}", e.getMessage());
                    return Mono.just(Boolean.FALSE);
                });
    }
}

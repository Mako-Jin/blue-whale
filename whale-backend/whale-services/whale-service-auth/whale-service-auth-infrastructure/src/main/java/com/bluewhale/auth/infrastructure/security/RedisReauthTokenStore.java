package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.port.ReauthTokenStore;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 step-up 凭证存储（Wave 6.22）
 *
 * <p>键 {@code whale:auth:reauth:{tenantId}:{userId}}，值为当前有效凭证；
 * 同用户重新验密会覆盖旧凭证。TTL 由 {@code whale.auth.reauth.ttl-seconds} 控制（默认 300s）。</p>
 *
 * <p><b>fail-closed</b>：Redis 不可用时发放直接失败（400/500 语义的 {@code SYSTEM_ERROR}），
 * 校验返回 {@code false}——危险操作宁拒绝不放行；两处降级均经
 * {@link SecurityDegradationAlerter} 输出告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class RedisReauthTokenStore implements ReauthTokenStore {

    /** 键前缀 */
    private static final String KEY_PREFIX = "whale:auth:reauth:";

    /** 最小 TTL（秒） */
    private static final long MIN_TTL_SECONDS = 1L;

    /** 降级告警场景：发放凭证 */
    private static final String SCENE_ISSUE = "auth:reauth:issue";

    /** 降级告警场景：校验凭证 */
    private static final String SCENE_MATCH = "auth:reauth:match";

    /** 降级说明：发放 */
    private static final String DETAIL_ISSUE = "step-up 凭证写入失败（Redis 不可用），本次二次认证不可用（fail-closed）";

    /** 降级说明：校验 */
    private static final String DETAIL_MATCH = "step-up 凭证校验失败（Redis 不可用），按无效处理（fail-closed）";

    /** Redis 字符串模板 */
    private final StringRedisTemplate redisTemplate;

    /** 凭证有效期（秒） */
    private final long ttlSeconds;

    /**
     * 构造凭证存储
     *
     * @param redisTemplate Redis 字符串模板
     * @param ttlSeconds 凭证有效期（秒），默认 300
     */
    public RedisReauthTokenStore(StringRedisTemplate redisTemplate,
                                 @Value("${whale.auth.reauth.ttl-seconds:300}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = Math.max(MIN_TTL_SECONDS, ttlSeconds);
    }

    @Override
    public String issue(Long tenantId, Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(key(tenantId, userId), token, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("step-up 凭证写入失败（Redis 不可用）: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_ISSUE, DETAIL_ISSUE, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        return token;
    }

    @Override
    public boolean matches(Long tenantId, Long userId, String token) {
        try {
            String expected = redisTemplate.opsForValue().get(key(tenantId, userId));
            if (expected == null || token == null) {
                return false;
            }
            // 恒时比较，避免按字节差异泄露凭证前缀
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            log.warn("step-up 凭证校验失败（Redis 不可用），按无效处理: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_MATCH, DETAIL_MATCH, e);
            return false;
        }
    }

    @Override
    public long ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * 拼装存储键
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return Redis 键
     */
    private String key(Long tenantId, Long userId) {
        return KEY_PREFIX + tenantId + ":" + userId;
    }
}

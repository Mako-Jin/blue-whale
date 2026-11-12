package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.port.RefreshTokenRotationStore;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TokenBlacklistKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 refreshToken 轮换状态存储（AUTH-39 / ADR-001）。
 *
 * <p>键结构统一由 {@link TokenBlacklistKeys} 构造：
 * {@code whale:auth:refresh:used:<jti>}（已消费）与
 * {@code whale:auth:refresh:family:<familyId>}（family 吊销）；值为固定占位符，
 * 仅以键是否存在判定命中。</p>
 *
 * <p><b>fail-closed</b>（ADR-001 R2）：Redis 不可用时 {@link #markUsed} 返回 {@code false}、
 * {@link #isFamilyRevoked} 返回 {@code true}，即保守拒绝换发；两处降级分支均 {@code warn} 并
 * 经 {@link SecurityDegradationAlerter} 输出 ERROR 级告警与进程内计数。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenRotationStore implements RefreshTokenRotationStore {

    /** 键值占位符：命中与否只看键是否存在 */
    private static final String PLACEHOLDER_VALUE = "1";

    /** 最小写入 TTL（秒），避免出现 TTL<=0 导致键永不过期 */
    private static final long MIN_TTL_SECONDS = 1L;

    /** 降级告警场景标识：标记已消费 */
    private static final String SCENE_MARK_USED = "auth:refresh-rotation:mark-used";

    /** 降级告警场景标识：查询 family 吊销 */
    private static final String SCENE_FAMILY_REVOKED = "auth:refresh-rotation:family-revoked";

    /** 降级告警场景标识：吊销 family */
    private static final String SCENE_REVOKE_FAMILY = "auth:refresh-rotation:revoke-family";

    /** 标记已消费降级说明 */
    private static final String DETAIL_MARK_USED = "refresh 已消费标记写入失败（Redis 不可用），按已消费处理拒绝换发（fail-closed）";

    /** 查询 family 吊销降级说明 */
    private static final String DETAIL_FAMILY_REVOKED = "refresh family 吊销状态查询失败（Redis 不可用），按已吊销处理拒绝换发（fail-closed）";

    /** 吊销 family 降级说明 */
    private static final String DETAIL_REVOKE_FAMILY = "refresh family 吊销标记写入失败（Redis 不可用），该 family 已被拒绝换发";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean markUsed(String jti, long ttlSeconds) {
        if (ttlSeconds < MIN_TTL_SECONDS) {
            // 令牌已过期，本身不可再用；保守按「已消费」处理
            return false;
        }
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(TokenBlacklistKeys.refreshUsedKey(jti), PLACEHOLDER_VALUE, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            log.warn("refresh 已消费标记写入失败（Redis 不可用），按已消费处理: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_MARK_USED, DETAIL_MARK_USED, e);
            return false;
        }
    }

    @Override
    public boolean isFamilyRevoked(String familyId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(TokenBlacklistKeys.refreshFamilyKey(familyId)));
        } catch (RuntimeException e) {
            log.warn("refresh family 吊销状态查询失败（Redis 不可用），按已吊销处理: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_FAMILY_REVOKED, DETAIL_FAMILY_REVOKED, e);
            return true;
        }
    }

    @Override
    public void revokeFamily(String familyId, long ttlSeconds) {
        if (ttlSeconds < MIN_TTL_SECONDS) {
            log.warn("refresh family 吊销 TTL 非正，跳过写入: familyId={}", familyId);
            return;
        }
        try {
            redisTemplate.opsForValue().set(TokenBlacklistKeys.refreshFamilyKey(familyId),
                    PLACEHOLDER_VALUE, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("refresh family 吊销标记写入失败（Redis 不可用）: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_REVOKE_FAMILY, DETAIL_REVOKE_FAMILY, e);
        }
    }
}

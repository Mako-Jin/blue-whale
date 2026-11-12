package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.port.LoginAttemptStore;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的登录失败计数（Wave 6.27）
 *
 * <p>键 {@code whale:auth:login-fail:{tenantId}:{username}}，值为窗口内失败次数。
 * 首次失败时设置 {@code whale.auth.lockout.window-seconds}（默认 900s）过期时间，
 * 计数达到 {@code whale.auth.lockout.max-attempts}（默认 5）即视为锁定，
 * 窗口到期自动解锁（不需要人工解封）。</p>
 *
 * <p><b>fail-open</b>：Redis 不可用时按「未锁定、不计数」处理并输出降级告警——
 * 与 step-up 凭证（fail-closed）刻意相反：锁定计数是「附加防线」，
 * 存储故障时若 fail-closed，整体登录会在 Redis 抖动期间全站不可用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class RedisLoginAttemptStore implements LoginAttemptStore {

    /** 键前缀 */
    private static final String KEY_PREFIX = "whale:auth:login-fail:";

    /** 计数下限（防止配置成 0 导致一登录即锁） */
    private static final int MIN_ATTEMPTS = 1;

    /** 窗口下限（秒） */
    private static final long MIN_WINDOW_SECONDS = 1L;

    /** 降级告警场景：记录失败 */
    private static final String SCENE_RECORD = "auth:login-attempt:record";

    /** 降级告警场景：查询锁定状态 */
    private static final String SCENE_LOCKED = "auth:login-attempt:locked";

    /** 降级告警场景：清除计数 */
    private static final String SCENE_CLEAR = "auth:login-attempt:clear";

    /** 降级说明：记录失败 */
    private static final String DETAIL_RECORD = "登录失败计数写入失败（Redis 不可用），本次失败不计数（fail-open）";

    /** 降级说明：查询锁定状态 */
    private static final String DETAIL_LOCKED = "登录失败计数读取失败（Redis 不可用），按未锁定放行（fail-open）";

    /** 降级说明：清除计数 */
    private static final String DETAIL_CLEAR = "登录失败计数清除失败（Redis 不可用），旧计数由窗口自然过期（fail-open）";

    /** Redis 字符串模板 */
    private final StringRedisTemplate redisTemplate;

    /** 触发锁定的失败次数上限 */
    private final int maxAttempts;

    /** 计数窗口 / 锁定最长持续时间（秒） */
    private final long windowSeconds;

    /**
     * 构造登录失败计数存储
     *
     * @param redisTemplate Redis 字符串模板
     * @param maxAttempts 触发锁定的失败次数上限，默认 5
     * @param windowSeconds 计数窗口时长（秒），默认 900
     */
    public RedisLoginAttemptStore(StringRedisTemplate redisTemplate,
                                  @Value("${whale.auth.lockout.max-attempts:5}") int maxAttempts,
                                  @Value("${whale.auth.lockout.window-seconds:900}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = Math.max(MIN_ATTEMPTS, maxAttempts);
        this.windowSeconds = Math.max(MIN_WINDOW_SECONDS, windowSeconds);
    }

    @Override
    public long lockRemainingSeconds(Long tenantId, String username) {
        String key = key(tenantId, username);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return 0L;
            }
            if (parseCount(raw) < maxAttempts) {
                return 0L;
            }
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            // TTL 缺失（未设置过期时间）时按整窗口兜底，绝不返回「永久锁定」
            return ttl == null || ttl <= 0L ? windowSeconds : ttl;
        } catch (RuntimeException e) {
            log.warn("登录失败计数读取失败（Redis 不可用），按未锁定放行: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_LOCKED, DETAIL_LOCKED, e);
            return 0L;
        }
    }

    @Override
    public int recordFailure(Long tenantId, String username) {
        String key = key(tenantId, username);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return 0;
            }
            if (count == 1L) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }
            return count.intValue();
        } catch (RuntimeException e) {
            log.warn("登录失败计数写入失败（Redis 不可用），本次失败不计数: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_RECORD, DETAIL_RECORD, e);
            return 0;
        }
    }

    @Override
    public void clear(Long tenantId, String username) {
        try {
            redisTemplate.delete(key(tenantId, username));
        } catch (RuntimeException e) {
            log.warn("登录失败计数清除失败（Redis 不可用），由窗口自然过期: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_CLEAR, DETAIL_CLEAR, e);
        }
    }

    /**
     * 解析计数值（脏数据按 0 处理，避免格式异常把账号永久判为锁定）
     *
     * @param raw Redis 中的原始字符串
     * @return 计数值
     */
    private int parseCount(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("登录失败计数格式异常，按 0 处理: value={}", raw);
            return 0;
        }
    }

    /**
     * 拼装计数键（用户名统一小写，避免大小写变体绕过同一账号的计数）
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return Redis 键
     */
    private String key(Long tenantId, String username) {
        return KEY_PREFIX + tenantId + ":" + username.toLowerCase(Locale.ROOT);
    }
}

package com.bluewhale.i18n;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Locale;

/**
 * 国际化缓存清理助手
 *
 * <p>数据库文案变更后调用 {@link #evict(String, String)} 清理对应 Redis 缓存，
 * 使新文案立即可见（无需等待 TTL 过期）。
 *
 * @author BlueWhale
 */
@Slf4j
public class I18nCacheEvictHelper {

    /**
     * Redis 异常告警限流间隔（毫秒）
     */
    private static final long REDIS_WARN_INTERVAL_MS = 60_000L;

    private final WhaleI18nProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * Redis 异常日志限流
     */
    private volatile long lastRedisWarnAt = 0L;

    /**
     * 构造缓存清理助手
     *
     * @param properties    i18n 配置属性
     * @param redisTemplate Redis 操作模板，可为 null（未启用缓存）
     */
    public I18nCacheEvictHelper(WhaleI18nProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 清理指定 code + locale 的缓存 key
     *
     * @param code   消息编码
     * @param locale 语言环境字符串，如 zh_CN、en_US
     */
    public void evict(String code, String locale) {
        evictInternal(code, locale);
    }

    /**
     * 清理指定 code + locale 的缓存 key
     */
    public void evict(String code, Locale locale) {
        evictInternal(code, locale != null ? locale.toString() : Locale.getDefault().toString());
    }

    /**
     * 内部清理指定 code + locale 的缓存 key
     *
     * @param code   消息编码
     * @param locale 语言环境字符串
     */
    private void evictInternal(String code, String locale) {
        if (redisTemplate == null) {
            log.debug("[whale-i18n] 未启用 Redis 缓存，跳过清理。code={}, locale={}", code, locale);
            return;
        }
        String cacheKey = WhaleMessageSource.buildCacheKey(properties.getCachePrefix(), locale, code);
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.debug("[whale-i18n] 清理缓存 key={}，deleted={}", cacheKey, deleted);
        } catch (Exception ex) {
            long now = System.currentTimeMillis();
            if (now - lastRedisWarnAt >= REDIS_WARN_INTERVAL_MS) {
                lastRedisWarnAt = now;
                log.warn("[whale-i18n] Redis 不可用（清理缓存失败）：{}", ex.getMessage());
            }
        }
    }
}

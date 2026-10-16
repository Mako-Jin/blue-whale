package com.bluewhale.i18n;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.MessageSourceSupport;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 三级回退国际化 MessageSource：数据库 > 属性文件 > 默认值
 * <ol>
 *     <li>优先查数据库（经 {@link com.bluewhale.i18n.spi.I18nDatabaseProvider}，结果走 Redis 缓存）</li>
 *     <li>未命中 → 委托属性文件 MessageSource（basename 可配，默认 classpath:i18n/messages）</li>
 *     <li>仍未命中 → 返回调用方默认值；连默认值都没有 → 返回 code 本身（永不抛 NoSuchMessageException）</li>
 * </ol>
 * Redis 不可用时自动降级直查数据库；无 Redis / 无数据源环境均可优雅退化。
 *
 * @author BlueWhale
 */
@Slf4j
public class WhaleMessageSource extends MessageSourceSupport implements MessageSource {

    /**
     * 数据库与属性文件均未命中时的空值哨兵（防缓存穿透）
     */
    static final String NEGATIVE_SENTINEL = "";

    /**
     * 空值哨兵 TTL（秒），短于正常缓存，便于误缓存后尽快恢复
     */
    private static final long NEGATIVE_TTL_SECONDS = 60;

    /**
     * Redis 异常告警限流间隔（毫秒）
     */
    private static final long REDIS_WARN_INTERVAL_MS = 60_000L;

    private final WhaleI18nProperties properties;
    private final MessageSource propertiesMessageSource;
    private final com.bluewhale.i18n.spi.I18nDatabaseProvider databaseProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * Redis 异常日志限流（同一时间窗口只打一条 warn）
     */
    private final AtomicLong lastRedisWarnAt = new AtomicLong(0L);

    public WhaleMessageSource(WhaleI18nProperties properties,
                              MessageSource propertiesMessageSource,
                              com.bluewhale.i18n.spi.I18nDatabaseProvider databaseProvider,
                              StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.propertiesMessageSource = propertiesMessageSource;
        this.databaseProvider = databaseProvider;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 构造 Redis 缓存 key：{cachePrefix}:{locale}:{code}
     */
    public static String buildCacheKey(String cachePrefix, String locale, String code) {
        return cachePrefix + ":" + locale + ":" + code;
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        Locale localeToUse = locale != null ? locale : Locale.getDefault();
        String resolved = resolveCode(code, args, localeToUse);
        if (resolved != null) {
            return resolved;
        }
        // 三级回退：默认值；连默认值都没有 → 返回 code 本身，保证展示层永不空
        return defaultMessage != null ? defaultMessage : code;
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        // 保持 MessageSource 语义但永不抛异常：未命中返回 code 本身
        return getMessage(code, args, null, locale);
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        Locale localeToUse = locale != null ? locale : Locale.getDefault();
        String[] codes = resolvable.getCodes();
        if (codes != null) {
            for (String code : codes) {
                String resolved = resolveCode(code, resolvable.getArguments(), localeToUse);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        String defaultMessage = resolvable.getDefaultMessage();
        if (defaultMessage != null) {
            return defaultMessage;
        }
        return (codes != null && codes.length > 0) ? codes[0] : "";
    }

    /**
     * 按优先级解析：Redis 缓存 → 数据库 → 属性文件
     *
     * @return 命中的消息文本；三级均未命中返回 null
     */
    private String resolveCode(String code, Object[] args, Locale locale) {
        String localeKey = locale.toString();
        String cacheKey = buildCacheKey(properties.getCachePrefix(), localeKey, code);

        // 第一级：数据库（经 Redis 缓存）
        String cached = getFromCache(cacheKey);
        if (cached != null) {
            // 空哨兵：数据库与属性文件均已确认未命中，直接走默认值
            return cached.isEmpty() ? null : renderMessage(cached, args, locale);
        }
        String dbText = lookupDatabase(code, localeKey);
        if (dbText != null) {
            putInCache(cacheKey, dbText, Duration.ofSeconds(properties.getCacheTtlSeconds()));
            return renderMessage(dbText, args, locale);
        }

        // 第二级：属性文件
        String propText = lookupProperties(code, args, locale);
        if (propText != null) {
            return propText;
        }

        // 第三级：均未命中，缓存空值哨兵防止穿透
        putInCache(cacheKey, NEGATIVE_SENTINEL, Duration.ofSeconds(NEGATIVE_TTL_SECONDS));
        return null;
    }

    /**
     * 使用 MessageFormat 渲染 {0} 参数占位
     */
    private String renderMessage(String message, Object[] args, Locale locale) {
        return formatMessage(message, args, locale);
    }

    private String lookupDatabase(String code, String localeKey) {
        if (databaseProvider == null) {
            return null;
        }
        try {
            return databaseProvider.findMessage(code, localeKey);
        } catch (Exception ex) {
            log.warn("[whale-i18n] 数据库查询异常，回退属性文件。code={}, locale={}", code, localeKey, ex);
            return null;
        }
    }

    private String lookupProperties(String code, Object[] args, Locale locale) {
        if (propertiesMessageSource == null) {
            return null;
        }
        try {
            return propertiesMessageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException ex) {
            return null;
        }
    }

    private String getFromCache(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception ex) {
            warnRedisDown("读取缓存", ex);
            return null;
        }
    }

    private void putInCache(String cacheKey, String value, Duration ttl) {
        if (redisTemplate == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, value, ttl);
        } catch (Exception ex) {
            warnRedisDown("写入缓存", ex);
        }
    }

    /**
     * Redis 不可用时降级并限流打印 warn（默认每分钟最多一条）
     */
    private void warnRedisDown(String action, Exception ex) {
        long now = System.currentTimeMillis();
        long last = lastRedisWarnAt.get();
        if (now - last >= REDIS_WARN_INTERVAL_MS && lastRedisWarnAt.compareAndSet(last, now)) {
            log.warn("[whale-i18n] Redis 不可用（{}），已降级为直查数据库：{}", action, ex.getMessage());
        }
    }

    /**
     * 空值哨兵 TTL（秒），供 {@link I18nCacheEvictHelper} 等使用
     */
    static long negativeTtlSeconds() {
        return NEGATIVE_TTL_SECONDS;
    }
}

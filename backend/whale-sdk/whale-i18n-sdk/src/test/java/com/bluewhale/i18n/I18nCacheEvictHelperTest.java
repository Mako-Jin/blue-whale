package com.bluewhale.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * {@link I18nCacheEvictHelper} 单元测试（内存版 Redis fake，不依赖真实 Redis）
 *
 * @author BlueWhale
 */
class I18nCacheEvictHelperTest {

    @Test
    @DisplayName("evict(code, locale) 清除缓存后，MessageSource 重新查库拿到新文案")
    void evictClearsCachedMessage() {
        InMemoryI18nDatabaseProvider dbProvider = new InMemoryI18nDatabaseProvider();
        dbProvider.put("db.msg", "zh_CN", "旧文案");

        Map<String, String> store = new HashMap<>();
        StringRedisTemplate redis = workingRedis(store);
        WhaleI18nProperties properties = new WhaleI18nProperties();
        I18nCacheEvictHelper evictHelper = new I18nCacheEvictHelper(properties, redis);
        WhaleMessageSource source = newMessageSource(dbProvider, redis);

        // 1. 首次读取，旧文案写入缓存
        assertEquals("旧文案", source.getMessage("db.msg", null, "默认值", I18nCacheEvictHelperTest.zhCn()));

        // 2. 数据库更新文案，但缓存未清 → 仍读到旧值
        dbProvider.put("db.msg", "zh_CN", "新文案");
        assertEquals("旧文案", source.getMessage("db.msg", null, "默认值", I18nCacheEvictHelperTest.zhCn()));

        // 3. 清缓存
        evictHelper.evict("db.msg", "zh_CN");
        String key = WhaleMessageSource.buildCacheKey(properties.getCachePrefix(), "zh_CN", "db.msg");
        assertNull(store.get(key));

        // 4. 再次读取 → 重新查库拿到新文案并重新缓存
        assertEquals("新文案", source.getMessage("db.msg", null, "默认值", I18nCacheEvictHelperTest.zhCn()));
        assertEquals("新文案", store.get(key));
    }

    @Test
    @DisplayName("未启用 Redis（null）时 evict 不抛异常、直接跳过")
    void evictWithoutRedisIsNoOp() {
        I18nCacheEvictHelper helper = new I18nCacheEvictHelper(new WhaleI18nProperties(), null);
        // 不抛异常即为通过
        helper.evict("db.msg", "zh_CN");
    }

    private static Locale zhCn() {
        return Locale.SIMPLIFIED_CHINESE;
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate workingRedis(Map<String, String> store) {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.time.Duration.class));
        doAnswer(inv -> store.remove(inv.getArgument(0, String.class)) != null)
                .when(redis).delete(anyString());
        return redis;
    }

    private WhaleMessageSource newMessageSource(InMemoryI18nDatabaseProvider provider, StringRedisTemplate redis) {
        WhaleI18nProperties properties = new WhaleI18nProperties();
        ReloadablePropertiesDelegate delegate = new ReloadablePropertiesDelegate(properties);
        return new WhaleMessageSource(properties, delegate.messageSource(), provider, redis);
    }

    /**
     * 局部辅助：构造与自动装配一致的属性文件 delegate
     */
    private static class ReloadablePropertiesDelegate {
        private final WhaleI18nProperties properties;

        ReloadablePropertiesDelegate(WhaleI18nProperties properties) {
            this.properties = properties;
        }

        org.springframework.context.support.ReloadableResourceBundleMessageSource messageSource() {
            org.springframework.context.support.ReloadableResourceBundleMessageSource messageSource =
                    new org.springframework.context.support.ReloadableResourceBundleMessageSource();
            messageSource.setBasename(properties.getBasename());
            messageSource.setDefaultEncoding("UTF-8");
            return messageSource;
        }
    }
}

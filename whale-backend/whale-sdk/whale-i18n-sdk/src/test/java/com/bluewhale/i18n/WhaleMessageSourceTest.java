package com.bluewhale.i18n;

import com.bluewhale.i18n.spi.I18nDatabaseProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 三级回退（数据库 > 属性文件 > 默认值）+ Redis 缓存/降级 单元测试
 * 不依赖真实 DB / Redis：InMemory Provider + Mockito fake Redis
 *
 * @author BlueWhale
 */
class WhaleMessageSourceTest {

    private static final Locale ZH = Locale.SIMPLIFIED_CHINESE;   // toString() = zh_CN
    private static final Locale EN = Locale.ENGLISH;              // toString() = en

    private InMemoryI18nDatabaseProvider dbProvider;

    @BeforeEach
    void setUp() {
        dbProvider = new InMemoryI18nDatabaseProvider();
        dbProvider.put("db.hello", "zh_CN", "你好，数据库");
        dbProvider.put("db.welcome", "zh_CN", "欢迎你，{0}！");
    }

    // ------------------ 构造辅助 ------------------

    private WhaleMessageSource newMessageSource(I18nDatabaseProvider provider, StringRedisTemplate redis) {
        WhaleI18nProperties properties = new WhaleI18nProperties();
        properties.setBasename("classpath:i18n/messages");
        ReloadableResourceBundleMessageSource delegate = new ReloadableResourceBundleMessageSource();
        delegate.setBasename(properties.getBasename());
        delegate.setDefaultEncoding("UTF-8");
        return new WhaleMessageSource(properties, delegate, provider, redis);
    }

    /**
     * 可正常工作的内存版 Redis fake（读写同一 store）
     */
    @SuppressWarnings("unchecked")
    private StringRedisTemplate workingRedis(java.util.Map<String, String> store) {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        doAnswer(inv -> store.remove(inv.getArgument(0, String.class)) != null)
                .when(redis).delete(anyString());
        return redis;
    }

    /**
     * Redis 断连 fake：任何操作抛连接异常
     */
    private StringRedisTemplate brokenRedis() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        return redis;
    }

    // ------------------ 第一级：数据库 ------------------

    @Test
    @DisplayName("DB 命中：返回数据库文案")
    void dbHit() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        assertEquals("你好，数据库", source.getMessage("db.hello", null, "默认值", ZH));
    }

    @Test
    @DisplayName("DB 命中 + {0} 参数渲染（MessageFormat）")
    void dbHitWithArgsRendered() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        assertEquals("欢迎你，张三！", source.getMessage("db.welcome", new Object[]{"张三"}, "默认值", ZH));
    }

    // ------------------ 第二级：属性文件 ------------------

    @Test
    @DisplayName("DB 未命中 → 属性文件命中（zh_CN / en）")
    void dbMissFallsBackToProperties() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        assertEquals("属性文件级消息（zh_CN）", source.getMessage("prop.only", null, "默认值", ZH));
        assertEquals("Property-file-only message (en)", source.getMessage("prop.only", null, "默认值", EN));
    }

    @Test
    @DisplayName("属性文件命中 + {0} 参数渲染")
    void propertiesHitWithArgsRendered() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        assertEquals("你好，小明，欢迎回来！", source.getMessage("prop.arg", new Object[]{"小明"}, null, ZH));
    }

    @Test
    @DisplayName("无 Provider / 无 Redis：优雅退化，属性文件与默认值仍可用")
    void noProviderNoRedisGraceful() {
        WhaleMessageSource source = newMessageSource(null, null);
        assertEquals("属性文件级消息（zh_CN）", source.getMessage("prop.only", null, null, ZH));
        assertEquals("默认文案", source.getMessage("no.where", null, "默认文案", ZH));
        assertEquals("no.where", source.getMessage("no.where", null, ZH));
    }

    // ------------------ 第三级：默认值 / code ------------------

    @Test
    @DisplayName("DB 与属性文件均未命中 → 返回默认值")
    void bothMissReturnsDefault() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        assertEquals("默认文案", source.getMessage("no.where", null, "默认文案", ZH));
    }

    @Test
    @DisplayName("全部未命中且无默认值 → 返回 code 本身，不抛 NoSuchMessageException")
    void allMissReturnsCode() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        String result = assertDoesNotThrow(() -> source.getMessage("no.where", null, ZH));
        assertEquals("no.where", result);
    }

    // ------------------ MessageSourceResolvable ------------------

    @Test
    @DisplayName("Resolvable 重载：按 codes 顺序解析，DB 命中优先")
    void resolvableDbHit() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        DefaultMessageSourceResolvable resolvable =
                new DefaultMessageSourceResolvable(new String[]{"miss.one", "db.hello"}, "兜底默认");
        assertEquals("你好，数据库", source.getMessage(resolvable, ZH));
    }

    @Test
    @DisplayName("Resolvable 重载：均未命中 → resolvable 默认值 → 首个 code")
    void resolvableFallback() {
        WhaleMessageSource source = newMessageSource(dbProvider, null);
        DefaultMessageSourceResolvable withDefault =
                new DefaultMessageSourceResolvable(new String[]{"miss.one", "miss.two"}, "兜底默认");
        assertEquals("兜底默认", source.getMessage(withDefault, ZH));

        DefaultMessageSourceResolvable noDefault =
                new DefaultMessageSourceResolvable(new String[]{"miss.one", "miss.two"});
        assertEquals("miss.one", source.getMessage(noDefault, ZH));
    }

    // ------------------ Redis 缓存与降级 ------------------

    @Test
    @DisplayName("Redis 断连：降级直查 DB，不抛异常")
    void redisDownDegradesToDb() {
        WhaleMessageSource source = newMessageSource(dbProvider, brokenRedis());
        assertEquals("你好，数据库", source.getMessage("db.hello", null, "默认值", ZH));
        // 全未命中时同样优雅返回默认值
        assertEquals("默认文案", source.getMessage("no.where", null, "默认文案", ZH));
    }

    @Test
    @DisplayName("Redis 可用：DB 未命中且属性文件未命中时写入空值哨兵（防穿透），再次解析直接走默认值")
    void negativeCacheSentinel() {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        WhaleMessageSource source = newMessageSource(dbProvider, workingRedis(store));

        assertEquals("默认文案1", source.getMessage("no.where", null, "默认文案1", ZH));
        String key = WhaleMessageSource.buildCacheKey("whale:i18n", "zh_CN", "no.where");
        assertEquals("", store.get(key), "空值哨兵应写入缓存");

        // 第二次调用命中空哨兵，跳过 DB/属性文件解析，直接返回新的默认值
        assertEquals("默认文案2", source.getMessage("no.where", null, "默认文案2", ZH));
    }

    @Test
    @DisplayName("Redis 可用：DB 命中结果写入缓存，再次读取走缓存")
    void positiveCacheStoresDbResult() {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        WhaleMessageSource source = newMessageSource(dbProvider, workingRedis(store));

        assertEquals("你好，数据库", source.getMessage("db.hello", null, "默认值", ZH));
        String key = WhaleMessageSource.buildCacheKey("whale:i18n", "zh_CN", "db.hello");
        assertEquals("你好，数据库", store.get(key));
    }
}

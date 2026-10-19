package com.bluewhale.common.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 缓存一致性自动配置。
 * <p>仅在「Spring Data Redis 在类路径 + 容器中存在 {@code StringRedisTemplate}」时生效，
 * 未引入 Redis 的服务不受影响（依赖为 {@code optional}）。</p>
 * <p>必须以 name 形式声明 {@code @AutoConfigureAfter}（目标为 Spring Boot 4 的
 * {@code org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration}）：
 * 自动配置按「Order → Before/After 拓扑 → 类名字母序」排序，本类位于 {@code com.bluewhale.*}，
 * 字母序天然先于 {@code org.springframework.boot.*} 的 Redis 自动配置；若不显式声明顺序，
 * {@code @ConditionalOnBean(StringRedisTemplate.class)} 会在 Redis 尚未注册模板 Bean 时
 * 提前判定为 false，导致 {@link CacheConsistencyTemplate} 装配失败。
 * 使用 name 而非 class 引用：starter 对 data-redis 为 optional 依赖，避免编译期耦合。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    /**
     * 注册缓存一致性模板
     *
     * @param redisTemplate Redis 字符串操作模板
     * @param properties 缓存配置属性
     * @return 缓存一致性模板实例
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public CacheConsistencyTemplate cacheConsistencyTemplate(StringRedisTemplate redisTemplate,
                                                             CacheProperties properties) {
        return new CacheConsistencyTemplate(redisTemplate, properties);
    }
}

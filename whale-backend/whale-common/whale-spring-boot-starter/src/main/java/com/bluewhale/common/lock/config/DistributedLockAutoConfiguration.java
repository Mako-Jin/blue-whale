package com.bluewhale.common.lock.config;

import com.bluewhale.common.lock.aop.DistributedLockAspect;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 分布式锁自动配置。
 * <p>仅在「Redisson 在类路径（使用方显式引入 {@code redisson-spring-boot-starter}）+ AOP 可用」时生效，
 * 未引入 Redisson 的服务不受影响（Redisson 依赖为 {@code optional}）。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnClass({RedissonClient.class, Aspect.class})
public class DistributedLockAutoConfiguration {

    /**
     * 注册分布式锁切面
     *
     * @param redissonClient Redisson 客户端
     * @return 分布式锁切面实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedLockAspect distributedLockAspect(RedissonClient redissonClient) {
        return new DistributedLockAspect(redissonClient);
    }
}

package com.bluewhale.mall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2 兼容配置。
 *
 * <p>Spring Boot 4 默认使用 Jackson 3（{@code tools.jackson.*}），不再自动注册
 * {@code com.fasterxml.jackson.databind.ObjectMapper} Bean。本服务仍有个别组件按 Jackson 2 类型
 * 构造注入，这里补一个最小实例，避免启动期因缺少依赖而失败。</p>
 *
 * <p>待这些组件迁移到 Jackson 3 后可移除本配置。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class Jackson2CompatConfig {

    /**
     * 提供 Jackson 2 的 ObjectMapper，仅在容器中不存在同类型 Bean 时生效。
     *
     * @return Jackson 2 ObjectMapper 实例
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper();
    }
}

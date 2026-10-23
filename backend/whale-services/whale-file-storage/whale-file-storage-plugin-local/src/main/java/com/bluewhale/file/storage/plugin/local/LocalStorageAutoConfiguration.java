package com.bluewhale.file.storage.plugin.local;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 本地存储插件自动配置。
 *
 * <p>本地插件始终装配，作为无对象存储环境下的兜底实现；
 * 是否启用由服务侧 {@code whale.file.storage.type} 决定。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@EnableConfigurationProperties(LocalStorageProperties.class)
public class LocalStorageAutoConfiguration {

    /**
     * 注册本地存储插件 Bean。
     *
     * @param properties 本地存储配置
     * @return 本地存储插件
     */
    @Bean
    @ConditionalOnMissingBean(LocalStoragePlugin.class)
    public LocalStoragePlugin localStoragePlugin(LocalStorageProperties properties) {
        return new LocalStoragePlugin(properties);
    }
}

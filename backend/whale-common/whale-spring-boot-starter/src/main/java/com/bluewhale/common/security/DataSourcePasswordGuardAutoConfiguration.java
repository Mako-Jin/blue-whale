package com.bluewhale.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 数据源密码启动守卫自动配置（P1-2）。
 *
 * <p>仅在配置了 {@code spring.datasource.url} 的服务中生效；无数据源的服务（如网关类应用）
 * 不会注册守卫，也不会误判启动失败。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class DataSourcePasswordGuardAutoConfiguration {

    /**
     * 注册数据源密码启动守卫
     *
     * @param environment 应用环境
     * @return 数据源密码启动守卫
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSourcePasswordGuard dataSourcePasswordGuard(Environment environment) {
        return new DataSourcePasswordGuard(environment);
    }
}

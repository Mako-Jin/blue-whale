package com.bluewhale.common.internaltoken.config;

import com.bluewhale.common.internaltoken.InternalTokenFilter;
import com.bluewhale.common.internaltoken.InternalTokenGuard;
import com.bluewhale.common.internaltoken.InternalTokenProperties;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * 内网令牌自动配置（V-02 纵深防御）。
 *
 * <p>仅在 Servlet Web 应用中生效：注册最高优先级的 {@link InternalTokenFilter}
 * （早于 Spring Security 过滤链，内网路径无令牌直接 403 而非 401）、启动护栏
 * {@link InternalTokenGuard}，并把 Spring 绑定配置同步给出站工厂
 * {@link InternalTokenSupport}（各服务出站内网调用自动携带令牌）。</p>
 *
 * <p>WebFlux 应用（如 whale-gateway）不加载本配置；网关侧由 internal-deny 全局过滤器
 * 与 {@code RemoveRequestHeader=X-Internal-Token} 默认过滤器负责。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InternalTokenProperties.class)
public class InternalTokenAutoConfiguration {

    /**
     * 内网令牌过滤器注册：最高优先级，早于 Spring Security
     *
     * @param properties 内网令牌配置
     * @return 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilterRegistration(
            InternalTokenProperties properties) {
        InternalTokenSupport.init(properties);
        FilterRegistrationBean<InternalTokenFilter> registration =
                new FilterRegistrationBean<>(new InternalTokenFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * 启动护栏：非 dev 环境令牌未配置时拒绝启动
     *
     * @param properties  内网令牌配置
     * @param environment Spring 环境
     * @return 启动护栏 Bean
     */
    @Bean
    public InternalTokenGuard internalTokenGuard(InternalTokenProperties properties, Environment environment) {
        return new InternalTokenGuard(properties, environment);
    }
}

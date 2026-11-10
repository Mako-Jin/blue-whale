package com.bluewhale.common.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 租户上下文自动配置（Wave 5.54 Phase 2）。
 *
 * <p>仅 Servlet Web 服务生效（网关为 WebFlux，不引入本 starter）；
 * 过滤器以最高优先级注册，保证早于 Spring Security 过滤器链与业务过滤器执行。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TenantContextAutoConfiguration {

    /**
     * 注册租户上下文过滤器：最高优先级，覆盖全部路径
     *
     * @return 过滤器注册信息
     */
    @Bean
    @ConditionalOnMissingBean(name = "tenantContextFilterRegistration")
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration() {
        FilterRegistrationBean<TenantContextFilter> registration =
                new FilterRegistrationBean<>(new TenantContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("tenantContextFilter");
        return registration;
    }
}

package com.bluewhale.common.context;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 请求上下文自动配置：仅 Servlet Web 服务生效，注册高优先级过滤器。
 *
 * <p>过滤器排序紧随租户上下文过滤器（{@link Ordered#HIGHEST_PRECEDENCE} + 10），
 * 保证业务代码、审计切面与日志组件在进入业务逻辑前即可读到调用者身份。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestContextAutoConfiguration {

    /**
     * 注册请求上下文过滤器：覆盖全部路径
     *
     * @return 过滤器注册信息
     */
    @Bean
    @ConditionalOnMissingBean(name = "requestContextFilterRegistration")
    public FilterRegistrationBean<RequestContextFilter> requestContextFilterRegistration() {
        FilterRegistrationBean<RequestContextFilter> registration =
                new FilterRegistrationBean<>(new RequestContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        // 避免与 Spring Boot 内建的 requestContextFilter 同名：Boot 4 / Tomcat 11 对重复过滤器名直接报错
        registration.setName("requestContextFilterRegistration");
        return registration;
    }
}

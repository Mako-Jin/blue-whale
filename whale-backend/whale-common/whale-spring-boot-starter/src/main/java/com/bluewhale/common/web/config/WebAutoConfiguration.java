package com.bluewhale.common.web.config;

import com.bluewhale.common.i18n.CommonMessageResolver;
import com.bluewhale.common.i18n.LocaleContextFilter;
import com.bluewhale.common.web.elapsed.RestApiElapsedAspect;
import com.bluewhale.common.web.elapsed.RestApiElapsedProperties;
import com.bluewhale.common.web.filter.ResponseHeaderFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Web 公共能力自动配置：安全响应头过滤器与可选的接口耗时日志切面。
 *
 * <p>仅 Servlet Web 应用生效。接口耗时日志默认关闭，需显式配置
 * {@code whale.web.api.elapsed.enabled=true} 开启。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(RestApiElapsedProperties.class)
public class WebAutoConfiguration {

    /**
     * 公共错误文案解析器：资源包随 starter 分发，未接入 i18n SDK 的服务也可按 Locale 解析
     *
     * @return 公共文案解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CommonMessageResolver commonMessageResolver() {
        return new CommonMessageResolver();
    }

    /**
     * 注册语言上下文过滤器：先于身份上下文过滤器执行，覆盖全部路径
     *
     * @return 过滤器注册信息
     */
    @Bean
    @ConditionalOnMissingBean(name = "localeContextFilterRegistration")
    public FilterRegistrationBean<LocaleContextFilter> localeContextFilterRegistration() {
        FilterRegistrationBean<LocaleContextFilter> registration =
                new FilterRegistrationBean<>(new LocaleContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.addUrlPatterns("/*");
        registration.setName("localeContextFilterRegistration");
        return registration;
    }

    /**
     * 注册安全响应头过滤器：覆盖全部路径
     *
     * @return 过滤器注册信息
     */
    @Bean
    @ConditionalOnMissingBean(name = "responseHeaderFilterRegistration")
    public FilterRegistrationBean<ResponseHeaderFilter> responseHeaderFilterRegistration() {
        FilterRegistrationBean<ResponseHeaderFilter> registration =
                new FilterRegistrationBean<>(new ResponseHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        registration.setName("responseHeaderFilter");
        return registration;
    }

    /**
     * 注册接口耗时日志切面（仅当配置开启时）
     *
     * @param properties 耗时配置
     * @return 耗时切面
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = RestApiElapsedProperties.PREFIX, name = "enabled", havingValue = "true")
    public RestApiElapsedAspect restApiElapsedAspect(RestApiElapsedProperties properties) {
        return new RestApiElapsedAspect(properties);
    }
}

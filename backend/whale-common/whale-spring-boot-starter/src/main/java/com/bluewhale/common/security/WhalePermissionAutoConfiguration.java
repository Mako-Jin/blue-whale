package com.bluewhale.common.security;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 权限码头判定自动配置（AUTH-06）。
 *
 * <p>生效条件：Servlet Web 应用 + 类路径存在 {@link RequirePermission} 注解 +
 * {@code whale.permission.header.enabled=true}（默认 {@code false}）。<b>仅显式开启的服务</b>才注册
 * {@link RequirePermissionInterceptor}，避免在 whale-auth 内与本服务 {@code PermissionInterceptor}
 * 重复判定（本波仅 whale-audit 开启）。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequirePermission.class)
@ConditionalOnProperty(prefix = "whale.permission.header", name = "enabled", havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(PermissionHeaderProperties.class)
public class WhalePermissionAutoConfiguration {

    /** 权限码头拦截器 Bean 名 */
    private static final String INTERCEPTOR_BEAN_NAME = "requirePermissionInterceptor";
    /** WebMvcConfigurer Bean 名 */
    private static final String CONFIGURER_BEAN_NAME = "whalePermissionWebMvcConfigurer";

    /**
     * 权限码头判定拦截器
     *
     * @param properties 权限码头判定配置
     * @return 权限码头判定拦截器
     */
    @Bean(name = INTERCEPTOR_BEAN_NAME)
    @ConditionalOnMissingBean
    public RequirePermissionInterceptor requirePermissionInterceptor(PermissionHeaderProperties properties) {
        return new RequirePermissionInterceptor(properties);
    }

    /**
     * 把权限码头判定拦截器注册到全部路径（{@code /**}）
     *
     * <p>Bean 名限定（而非按类型）的 {@code @ConditionalOnMissingBean}：避免应用自带
     * {@code WebMvcConfigurer} 时按类型命中而跳过本装配，导致受保护接口漏判（fail-open）。</p>
     *
     * @param requirePermissionInterceptor 权限码头判定拦截器
     * @return WebMvc 配置器
     */
    @Bean(name = CONFIGURER_BEAN_NAME)
    @ConditionalOnMissingBean(name = CONFIGURER_BEAN_NAME)
    public WebMvcConfigurer whalePermissionWebMvcConfigurer(RequirePermissionInterceptor requirePermissionInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(requirePermissionInterceptor).addPathPatterns("/**");
            }
        };
    }
}

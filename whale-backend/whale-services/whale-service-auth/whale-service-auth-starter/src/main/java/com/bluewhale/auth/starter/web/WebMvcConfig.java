package com.bluewhale.auth.starter.web;

import com.bluewhale.auth.infrastructure.security.PermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 拦截器注册（Wave 6.22 从 SecurityConfig 拆出）
 *
 * <p>拆出的原因：{@link ReauthInterceptor} → {@code ReauthAppService} →
 * {@code CredentialAuthenticator} → {@code AuthenticationManager}（由 SecurityConfig 提供），
 * 若注册仍留在 SecurityConfig 会形成 Bean 环（Boot 4 默认禁止循环引用）。
 * 本类只做注册，不参与安全过滤链装配。</p>
 *
 * <p>注册顺序即执行顺序：权限校验先于二次认证（先判「能不能做」，再判「是否本人确认」）。</p>
 *
 * @author BlueWhale
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** @RequirePermission 权限拦截器 */
    private final PermissionInterceptor permissionInterceptor;

    /** @RequireReauth 二次认证拦截器 */
    private final ReauthInterceptor reauthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/**");
        registry.addInterceptor(reauthInterceptor).addPathPatterns("/**");
    }
}

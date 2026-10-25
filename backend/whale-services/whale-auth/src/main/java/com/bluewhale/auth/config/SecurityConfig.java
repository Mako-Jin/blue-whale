package com.bluewhale.auth.config;

import com.bluewhale.auth.security.JwtAuthenticationFilter;
import com.bluewhale.auth.security.PermissionInterceptor;
import com.bluewhale.auth.security.RestAccessDeniedHandler;
import com.bluewhale.auth.security.RestAuthenticationEntryPoint;
import com.bluewhale.auth.security.TenantContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Security 配置（Security 7 / Boot 4，无 WebSecurityConfigurerAdapter）
 * <p>无状态（STATELESS）、关闭 CSRF、401/403 返回 Result JSON。</p>
 *
 * @author BlueWhale
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final PermissionInterceptor permissionInterceptor;

    /**
     * 安全过滤链：无状态会话、放行登录/刷新/登出与文档端点，其余接口一律要求认证，
     * 并在用户名密码过滤器之前接入 JWT 过滤器。
     *
     * @param http Spring Security 的 HTTP 安全构建器
     * @return 构建完成的过滤链
     * @throws Exception 构建过滤链失败时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout",
                                "/actuator/**", "/v3/api-docs/**", "/doc.html", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(tenantContextFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码编码器：采用 BCrypt 算法
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器：由 Spring Security 的认证配置统一提供
     *
     * @param configuration 认证配置
     * @return 认证管理器
     * @throws Exception 获取认证管理器失败时抛出
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 注册 {@link PermissionInterceptor}：拦截 @RequirePermission 注解方法
     *
     * @return 注册权限拦截器的 WebMvc 配置
     */
    @Bean
    public WebMvcConfigurer permissionInterceptorConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(permissionInterceptor).addPathPatterns("/**");
            }
        };
    }
}

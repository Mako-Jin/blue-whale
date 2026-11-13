package com.bluewhale.auth.infrastructure.config;

import com.bluewhale.auth.infrastructure.security.JwtAuthenticationFilter;
import com.bluewhale.auth.infrastructure.security.RestAccessDeniedHandler;
import com.bluewhale.auth.infrastructure.security.RestAuthenticationEntryPoint;
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

    /** BCrypt 强度（REQ-SEC-020：默认 10 提升到 12；旧密文由登录成功后的 upgradeEncoding 自动升级） */
    private static final int BCRYPT_STRENGTH = 12;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

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
                        .requestMatchers("/auth/v1/login", "/auth/v1/refresh", "/auth/v1/logout",
                                // 登录页品牌解析：登录前必须可访问，仅返回品牌渲染字段
                                "/brand/v1/resolve",
                                // 三方登录（权限中心扩展 M2）：授权起始/渠道回调为浏览器跳转链路，
                                // 一次性登录码换令牌为无 JWT 的短时效凭证交换，均免登录；
                                // 绑定/解绑/绑定列表不在白名单内，仍须 JWT。
                                "/social/v1/*/authorize", "/social/v1/*/callback", "/social/v1/login",
                                "/tenant/v1/internal/**", "/user/v1/internal/**", "/dept/v1/internal/**",
                                // 网关白名单下发（网关定时拉取）：内网令牌由 InternalTokenFilter 校验，
                                // 外部访问由网关 internal-deny 统一 403
                                "/auth/v1/internal/**",
                                // 调度中心触发端点与执行器注册/心跳（starter 的 JobTriggerController、
                                // JobExecutorRegistrar 都走 /internal/**）。外部访问由网关 internal-deny
                                // 拦截，服务侧由 InternalTokenGuard 校验令牌（生产必须配置 WHALE_INTERNAL_TOKEN）。
                                "/internal/**",
                                "/actuator/**", "/v3/api-docs/**", "/doc.html", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码编码器：采用 BCrypt 算法，强度 12
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
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
}

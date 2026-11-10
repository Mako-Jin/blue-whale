package com.bluewhale.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Duration;
import java.util.List;

/**
 * 网关跨域配置（P1-2）。
 *
 * <p>WebFlux 栈使用 {@link CorsWebFilter}，在网关全局过滤器之前完成 OPTIONS 预检并直接返回，
 * 因此预检请求不会因缺少 JWT 而被 {@code JwtAuthGlobalFilter} 拦截。</p>
 *
 * <p>来源白名单来自 {@code whale.gateway.cors.allowed-origins}：dev 默认放行本机任意端口，
 * 生产通过环境变量覆盖为具体域名；未命中白名单的来源预检返回 403。</p>
 *
 * @author BlueWhale
 */
@Configuration
@RequiredArgsConstructor
public class GatewayCorsConfig {

    /** CORS 预检结果缓存时长 */
    private static final Duration MAX_AGE = Duration.ofHours(1);

    /** 链路追踪响应头，允许浏览器端读取 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final WhaleGatewayProperties properties;

    /**
     * 注册跨域过滤器
     *
     * @return 网关跨域过滤器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter gatewayCorsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(TRACE_ID_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }
}

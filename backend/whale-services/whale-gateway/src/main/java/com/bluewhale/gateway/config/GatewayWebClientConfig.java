package com.bluewhale.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 网关 WebClient 配置（AUTH-06，Wave 5.10）。
 *
 * <p>仅用于权限码头注入过滤器的回落调用：当 Redis 权限码缓存未命中时，网关需以原请求
 * Authorization 访问 {@code lb://whale-auth/auth/me} 拉取当前用户权限码。此处提供一个带
 * {@link LoadBalanced} 负载均衡能力的 {@link WebClient.Builder}，其 {@code lb://} 服务名由
 * spring-cloud-loadbalancer 的 {@code ReactorLoadBalancerExchangeFilterFunction} 解析为实例地址。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class GatewayWebClientConfig {

    /**
     * 负载均衡 WebClient 构造器：供权限码头过滤器回落调用 {@code lb://whale-auth} 使用。
     *
     * <p>显式命名 Bean 并以 {@code @Qualifier} 注入，避免与 Spring Boot 默认的非负载均衡
     * {@code WebClient.Builder} 按类型注入产生歧义。</p>
     *
     * @return 带 {@code @LoadBalanced} 能力的 WebClient 构造器
     */
    @Bean(name = "loadBalancedWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}

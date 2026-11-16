package com.bluewhale.gateway.config;

import com.bluewhale.gateway.trace.TraceparentExchangeFilter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 网关 WebClient 配置（AUTH-06，Wave 5.10）。
 *
 * <p>用于网关发起的全部内网调用：权限码头回落 {@code /auth/me}、白名单/黑名单拉取、
 * 路由覆盖拉取。提供带 {@link LoadBalanced} 能力的 {@link WebClient.Builder}，其 {@code lb://}
 * 服务名由 spring-cloud-loadbalancer 解析为实例地址。</p>
 *
 * <p>同时挂载两件事：</p>
 * <ol>
 *   <li>Micrometer {@link ObservationRegistry}：为每次内网调用产生 client span；</li>
 *   <li>{@link TraceparentExchangeFilter}：显式写入 W3C {@code traceparent}，确保下游能接着网关的 span。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Configuration
public class GatewayWebClientConfig {

    /**
     * 负载均衡 WebClient 构造器：供内网调用使用。
     *
     * <p>显式命名 Bean 并以 {@code @Qualifier} 注入，避免与 Spring Boot 默认的非负载均衡
     * {@code WebClient.Builder} 按类型注入产生歧义。</p>
     *
     * @param observationRegistryProvider 观测注册表提供者（未启用追踪时为空）
     * @param tracerProvider              追踪器提供者（未启用追踪时为空）
     * @return 带 {@code @LoadBalanced} 能力、观测与 traceparent 注入的构造器
     */
    @Bean(name = "loadBalancedWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(
            ObjectProvider<ObservationRegistry> observationRegistryProvider,
            ObjectProvider<Tracer> tracerProvider) {
        WebClient.Builder builder = WebClient.builder();
        observationRegistryProvider.ifAvailable(builder::observationRegistry);
        return builder.filter(TraceparentExchangeFilter.create(tracerProvider));
    }
}

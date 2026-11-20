package com.bluewhale.overview;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 概览 SDK 自动装配。
 *
 * <p>接入方只需：① 实现 {@link OverviewProvider} 并注册为 Bean；
 * ② 配 {@code whale.overview.base-path}（例如 {@code /erp/overview}）；③ 在 Nacos / 网关放行该路径。
 * 缓存、超时、降级、指标都在这里统一提供，服务侧不要重复实现。</p>
 *
 * <p>没有 Micrometer 的服务不会因为缺这个类起不来：指标挂在一个
 * {@link ConditionalOnClass} 的嵌套配置里，缺类时整块跳过，
 * {@link OverviewService} 拿到的是空实现。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "whale.overview", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(OverviewProperties.class)
public class WhaleOverviewAutoConfiguration {

    /**
     * 概览装载服务
     *
     * @param providers 全部 provider（由各服务注册，可空）
     * @param properties 配置
     * @param listeners 结果监听器（有 Micrometer 时由嵌套配置提供，否则为空）
     * @return 装载服务
     */
    @Bean
    @ConditionalOnMissingBean
    public OverviewService overviewService(List<OverviewProvider> providers,
                                           OverviewProperties properties,
                                           ObjectProvider<OverviewOutcomeListener> listeners) {
        OverviewOutcomeListener listener = listeners.getIfAvailable(() -> (service, outcome) -> {
            // 无指标环境（未引入 actuator）：静默跳过，日志已在 OverviewService 里按 WARN 打过
        });
        return new OverviewService(providers, properties, listener);
    }

    /**
     * 概览控制器
     *
     * @param overviewService 装载服务
     * @return 控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public OverviewController overviewController(OverviewService overviewService) {
        return new OverviewController(overviewService);
    }

    /**
     * Micrometer 指标装配（仅在存在 {@link MeterRegistry} 时生效）
     *
     * @author BlueWhale
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    static class MetricsConfiguration {

        /**
         * 把装载结果记成计数器 {@code overview.load{service,outcome}}
         *
         * @param registry 指标注册表
         * @return 结果监听器
         */
        @Bean
        @ConditionalOnMissingBean
        OverviewOutcomeListener micrometerOutcomeListener(MeterRegistry registry) {
            return (service, outcome) -> Counter.builder("overview.load")
                    .description("概览装载结果（live/stale/degraded）")
                    .tag("service", service)
                    .tag("outcome", outcome)
                    .register(registry)
                    .increment();
        }
    }
}

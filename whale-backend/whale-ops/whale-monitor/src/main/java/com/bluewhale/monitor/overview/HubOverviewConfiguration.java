package com.bluewhale.monitor.overview;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 数枢殿总控的 Bean 注册：只负责把 {@link HubOverviewProperties} 绑进容器。
 *
 * <p>其余组件（聚合器 / provider / 控制器）都是 {@code @Component}，
 * 条件装配写在各自类上（{@code whale.overview.aggregate.enabled}），
 * 这里不重复写条件 —— 两处条件一旦不一致，就会出现"配置关了但 Bean 还在"。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HubOverviewProperties.class)
public class HubOverviewConfiguration {
}

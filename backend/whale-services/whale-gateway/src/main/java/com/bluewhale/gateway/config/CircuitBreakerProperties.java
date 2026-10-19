package com.bluewhale.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关熔断降级配置属性（T4）。
 *
 * <p>{@code whale.gateway.circuit-breaker.*}：{@code enabled} 控制熔断降级是否生效；
 * {@code timeoutMillis} 为单次下游调用超时（毫秒，超时即视为失败并降级）；
 * {@code failureRateThreshold} 为触发熔断的失败率阈值（百分比）；
 * {@code slidingWindowSize} 为统计失败率的滑动窗口大小（最近 N 次调用）；
 * {@code waitDurationInOpenStateMillis} 为熔断打开后的等待时长（毫秒，期满进入半开探测）；
 * {@code applyToRoutes} 为启用熔断的路由 id 列表，仅命中的路由参与熔断，其余路由直连放行。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway.circuit-breaker")
public class CircuitBreakerProperties {

    /** 是否启用熔断降级，默认开启；关闭后所有路由直连放行 */
    private boolean enabled = true;

    /** 单次下游调用超时（毫秒），超时按失败计入熔断统计并降级，默认 3000 */
    private int timeoutMillis = 3000;

    /** 滑动窗口内失败率达到该百分比即打开熔断，默认 50 */
    private int failureRateThreshold = 50;

    /** 失败率统计的滑动窗口大小（最近 N 次调用），默认 20 */
    private int slidingWindowSize = 20;

    /** 熔断打开后的等待时长（毫秒），期满后放行一次半开探测，默认 10000 */
    private int waitDurationInOpenStateMillis = 10000;

    /** 启用熔断降级的路由 id 列表，默认覆盖认证/任务/文件/审计四类关键路由 */
    private List<String> applyToRoutes = new ArrayList<>(List.of("whale-auth", "whale-job", "whale-file", "whale-audit"));
}

package com.bluewhale.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CircuitBreakerProperties} 单元测试（T4）：默认值与配置绑定。
 *
 * @author BlueWhale
 */
class CircuitBreakerPropertiesTest {

    /** 绑定用属性前缀 */
    private static final String PREFIX = "whale.gateway.circuit-breaker";

    @Test
    @DisplayName("默认值：启用 + 3s 超时 + 50% 失败率 + 窗口 20 + 10s 等待 + 四类关键路由")
    void shouldProvideExpectedDefaults() {
        CircuitBreakerProperties properties = new CircuitBreakerProperties();

        assertTrue(properties.isEnabled(), "默认应启用熔断");
        assertEquals(3000, properties.getTimeoutMillis());
        assertEquals(50, properties.getFailureRateThreshold());
        assertEquals(20, properties.getSlidingWindowSize());
        assertEquals(10000, properties.getWaitDurationInOpenStateMillis());
        assertEquals(List.of("whale-service-auth", "whale-job", "whale-file", "whale-audit"), properties.getApplyToRoutes());
    }

    @Test
    @DisplayName("绑定：外部属性覆盖默认值（含 apply-to-routes 列表）")
    void shouldBindFromProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(PREFIX + ".enabled", "false")
                .withProperty(PREFIX + ".timeout-millis", "1500")
                .withProperty(PREFIX + ".failure-rate-threshold", "80")
                .withProperty(PREFIX + ".sliding-window-size", "5")
                .withProperty(PREFIX + ".wait-duration-in-open-state-millis", "2000")
                .withProperty(PREFIX + ".apply-to-routes[0]", "whale-service-auth")
                .withProperty(PREFIX + ".apply-to-routes[1]", "whale-pay");

        CircuitBreakerProperties properties = Binder.get(environment)
                .bind(PREFIX, Bindable.of(CircuitBreakerProperties.class))
                .get();

        assertTrue(!properties.isEnabled(), "绑定 enabled=false 应生效");
        assertEquals(1500, properties.getTimeoutMillis());
        assertEquals(80, properties.getFailureRateThreshold());
        assertEquals(5, properties.getSlidingWindowSize());
        assertEquals(2000, properties.getWaitDurationInOpenStateMillis());
        assertEquals(List.of("whale-service-auth", "whale-pay"), properties.getApplyToRoutes());
    }
}

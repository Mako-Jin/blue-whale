package com.bluewhale.alert.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AlertWebhookTokenHealthIndicator} 健康状态判定单元测试。
 *
 * @author BlueWhale
 */
class AlertWebhookTokenHealthIndicatorTest {

    @Test
    @DisplayName("默认令牌 + prod profile 时报告 DOWN")
    void shouldReportDownForDefaultTokenInProd() {
        AlertNotifyProperties properties = new AlertNotifyProperties();
        properties.setWebhookToken(AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AlertWebhookTokenHealthIndicator indicator =
                new AlertWebhookTokenHealthIndicator(properties, environment);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    @DisplayName("默认令牌 + 未激活 profile 时报告 DOWN")
    void shouldReportDownForDefaultTokenWithoutProfile() {
        AlertNotifyProperties properties = new AlertNotifyProperties();
        properties.setWebhookToken(AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN);
        AlertWebhookTokenHealthIndicator indicator =
                new AlertWebhookTokenHealthIndicator(properties, new MockEnvironment());

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    @DisplayName("默认令牌 + dev profile 时保持 UP")
    void shouldReportUpForDefaultTokenInDev() {
        AlertNotifyProperties properties = new AlertNotifyProperties();
        properties.setWebhookToken(AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        AlertWebhookTokenHealthIndicator indicator =
                new AlertWebhookTokenHealthIndicator(properties, environment);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    @DisplayName("显式配置令牌后始终 UP")
    void shouldReportUpForConfiguredToken() {
        AlertNotifyProperties properties = new AlertNotifyProperties();
        properties.setWebhookToken("random-production-token");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AlertWebhookTokenHealthIndicator indicator =
                new AlertWebhookTokenHealthIndicator(properties, environment);

        assertEquals(Status.UP, indicator.health().getStatus());
    }
}

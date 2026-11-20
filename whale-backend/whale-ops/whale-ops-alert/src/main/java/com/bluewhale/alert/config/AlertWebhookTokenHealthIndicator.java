package com.bluewhale.alert.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 告警模块安全健康检查。
 *
 * <p>暴露 {@code /actuator/health} 时标记入站令牌是否仍为开发默认值，
 * 便于监控系统在部署配置缺失时直接告警。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AlertWebhookTokenHealthIndicator implements HealthIndicator {

    private final AlertNotifyProperties properties;

    private final Environment environment;

    @Override
    public Health health() {
        boolean defaultToken = AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN
                .equals(properties.getWebhookToken());
        List<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .toList();
        boolean risky = defaultToken && (profiles.isEmpty() || profiles.contains("prod"));
        Health.Builder builder = risky ? Health.down() : Health.up();
        return builder
                .withDetail("webhookToken", defaultToken ? "default" : "configured")
                .withDetail("profiles", profiles)
                .build();
    }
}

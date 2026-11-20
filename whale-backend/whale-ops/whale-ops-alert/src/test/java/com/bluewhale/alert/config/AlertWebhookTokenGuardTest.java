package com.bluewhale.alert.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AlertWebhookTokenGuard} 启动守卫测试。
 *
 * @author BlueWhale
 */
class AlertWebhookTokenGuardTest {

    private static final String CUSTOM_TOKEN = "8f4c1a2e-9b77-4b3c-9a51-random";

    @Test
    @DisplayName("守卫：非默认令牌在任何 profile 下都放行")
    void shouldAllowCustomTokenAnyProfile() {
        AlertNotifyProperties properties = properties(CUSTOM_TOKEN);

        assertDoesNotThrow(() -> new AlertWebhookTokenGuard(properties, env()).afterPropertiesSet());
        assertDoesNotThrow(() -> new AlertWebhookTokenGuard(properties, env("prod")).afterPropertiesSet());
    }

    @Test
    @DisplayName("守卫：默认令牌 + 无 profile（生产漏配）拒绝启动")
    void shouldRejectDefaultTokenWithoutProfile() {
        AlertNotifyProperties properties = properties(AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN);

        assertThrows(IllegalStateException.class,
                () -> new AlertWebhookTokenGuard(properties, env()).afterPropertiesSet());
    }

    @Test
    @DisplayName("守卫：默认令牌 + prod 拒绝启动")
    void shouldRejectDefaultTokenInProd() {
        AlertNotifyProperties properties = properties(AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN);

        assertThrows(IllegalStateException.class,
                () -> new AlertWebhookTokenGuard(properties, env("prod")).afterPropertiesSet());
    }

    @Test
    @DisplayName("守卫：默认令牌 + dev 与 prod 并存时按生产处理")
    void shouldRejectDefaultTokenWhenProdMixedWithDev() {
        AlertNotifyProperties properties = properties(AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN);

        assertThrows(IllegalStateException.class,
                () -> new AlertWebhookTokenGuard(properties, env("dev", "prod")).afterPropertiesSet());
    }

    @Test
    @DisplayName("守卫：默认令牌 + 本地开发 profile 放行")
    void shouldAllowDefaultTokenInLocalProfile() {
        AlertNotifyProperties properties = properties(AlertWebhookTokenGuard.DEV_DEFAULT_TOKEN);

        assertDoesNotThrow(() -> new AlertWebhookTokenGuard(properties, env("dev")).afterPropertiesSet());
    }

    /**
     * 构造通知配置
     *
     * @param token 入站令牌
     * @return 通知配置
     */
    private static AlertNotifyProperties properties(String token) {
        AlertNotifyProperties properties = new AlertNotifyProperties();
        properties.setWebhookToken(token);
        return properties;
    }

    /**
     * 构造激活指定 profile 的环境
     *
     * @param profiles 激活的 profile
     * @return 模拟环境
     */
    private static MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }
}

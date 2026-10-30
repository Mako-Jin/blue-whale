package com.bluewhale.common.internaltoken;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link InternalTokenGuard} 单元测试（V-02）：非 dev 未配置令牌拒绝启动、
 * dev 告警放行、显式关闭放行、已配置令牌放行。
 *
 * @author BlueWhale
 */
class InternalTokenGuardTest {

    @Test
    @DisplayName("guard：非 dev 环境令牌为空时拒绝启动")
    void shouldFailFastWhenTokenBlankInNonDevProfile() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setToken("");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：dev 环境令牌为空时仅告警不抛异常")
    void shouldWarnOnlyWhenTokenBlankInDevProfile() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setToken("");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertDoesNotThrow(() -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：显式关闭校验时不校验令牌")
    void shouldSkipWhenDisabled() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setEnabled(false);
        properties.setToken("");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：已配置令牌时放行")
    void shouldPassWhenTokenConfigured() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setToken("secret-token");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }
}

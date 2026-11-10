package com.bluewhale.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link GatewaySecretPlaceholderChecker} 单元测试（AUTH-01）：dev 放行与非 dev 拒绝。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class GatewaySecretPlaceholderCheckerTest {

    /** 自定义的强随机密钥样例，用于非 dev 允许场景 */
    private static final String CUSTOM_SECRET = "a-strong-random-secret-value-min-32-bytes";

    @Mock
    private WhaleGatewayProperties whaleGatewayProperties;

    private MockEnvironment environment;

    private GatewaySecretPlaceholderChecker checker;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        checker = new GatewaySecretPlaceholderChecker(whaleGatewayProperties, environment);
    }

    @Test
    @DisplayName("非 dev（prod）+ 占位密钥：拒绝启动")
    void shouldRejectPlaceholderOutsideDev() {
        environment.setActiveProfiles("prod");
        when(whaleGatewayProperties.getJwt()).thenReturn(new WhaleGatewayProperties.Jwt());

        assertThrows(IllegalStateException.class, () -> checker.afterPropertiesSet());
    }

    @Test
    @DisplayName("dev + 占位密钥：允许启动，不抛异常")
    void shouldAllowPlaceholderInDev() {
        environment.setActiveProfiles("dev");

        assertDoesNotThrow(() -> checker.afterPropertiesSet());
    }

    @Test
    @DisplayName("非 dev + 自定义密钥：允许启动")
    void shouldAllowCustomSecretOutsideDev() {
        environment.setActiveProfiles("prod");
        WhaleGatewayProperties.Jwt jwt = new WhaleGatewayProperties.Jwt();
        jwt.setSecret(CUSTOM_SECRET);
        when(whaleGatewayProperties.getJwt()).thenReturn(jwt);

        assertDoesNotThrow(() -> checker.afterPropertiesSet());
    }
}

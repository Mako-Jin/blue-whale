package com.bluewhale.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link SecretPlaceholderChecker} 单元测试（AUTH-23，REQ-SEC-005 / N-03）：dev 放行与非 dev 拒绝
 * （JWT 占位密钥 + Cookie Secure 未开启）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SecretPlaceholderCheckerTest {

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private AuthCookieProperties authCookieProperties;

    @Mock
    private Environment environment;

    private SecretPlaceholderChecker checker;

    @BeforeEach
    void setUp() {
        checker = new SecretPlaceholderChecker(jwtProperties, authCookieProperties, environment);
    }

    @Test
    @DisplayName("dev profile：允许占位密钥与 Secure 关闭，不抛异常")
    void shouldAllowPlaceholderInDev() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        assertDoesNotThrow(() -> checker.afterPropertiesSet());
    }

    @Test
    @DisplayName("非 dev + 占位密钥：拒绝启动")
    void shouldRejectPlaceholderOutsideDev() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(jwtProperties.getSecret()).thenReturn(JwtProperties.DEFAULT_PLACEHOLDER_SECRET);

        assertThrows(IllegalStateException.class, () -> checker.afterPropertiesSet());
    }

    @Test
    @DisplayName("非 dev + 自定义密钥：允许启动")
    void shouldAllowCustomSecretOutsideDev() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(jwtProperties.getSecret()).thenReturn("a-strong-random-secret-value-min-32-bytes");
        when(authCookieProperties.isSecure()).thenReturn(true);

        assertDoesNotThrow(() -> checker.afterPropertiesSet());
    }

    @Test
    @DisplayName("非 dev + 自定义密钥但 Cookie Secure 关闭：拒绝启动")
    void shouldRejectInsecureCookieOutsideDev() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(jwtProperties.getSecret()).thenReturn("a-strong-random-secret-value-min-32-bytes");
        when(authCookieProperties.isSecure()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> checker.afterPropertiesSet());
    }
}

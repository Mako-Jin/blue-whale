package com.bluewhale.security.degradation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SecurityDegradationPolicy} 单元测试：默认可用性优先、可切换安全优先、可重置。
 *
 * @author BlueWhale
 */
class SecurityDegradationPolicyTest {

    @AfterEach
    void reset() {
        SecurityDegradationPolicy.reset();
    }

    @Test
    @DisplayName("默认关闭：Redis 不可读时按可用性优先放行")
    void shouldDefaultToAvailabilityFirst() {
        assertFalse(SecurityDegradationPolicy.failClosed());
        assertFalse(SecurityDegradationPolicy.failClosedOr(false));
    }

    @Test
    @DisplayName("开启后：Redis 不可读时按安全优先拒绝")
    void shouldFailClosedWhenConfigured() {
        SecurityDegradationPolicy.configure(true);

        assertTrue(SecurityDegradationPolicy.failClosed());
        assertTrue(SecurityDegradationPolicy.failClosedOr(false));
        assertTrue(SecurityDegradationPolicy.failClosedOr(true));
    }

    @Test
    @DisplayName("重置后恢复默认")
    void shouldResetToDefault() {
        SecurityDegradationPolicy.configure(true);
        SecurityDegradationPolicy.reset();

        assertFalse(SecurityDegradationPolicy.failClosed());
    }
}

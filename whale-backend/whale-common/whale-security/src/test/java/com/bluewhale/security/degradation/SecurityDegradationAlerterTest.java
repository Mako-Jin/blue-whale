package com.bluewhale.security.degradation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SecurityDegradationAlerter} 单元测试：告警计数自增、复位与非空约束（AUTH-05）。
 *
 * @author BlueWhale
 */
class SecurityDegradationAlerterTest {

    private static final String SCENE = "auth:token-blacklist:contains";
    private static final String DETAIL = "令牌黑名单查询失败（Redis 不可用），按未命中处理";
    private static final long EXPECTED_ONE = 1L;
    private static final long EXPECTED_ZERO = 0L;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    @Test
    @DisplayName("alarm：调用一次使 alarmCount 自增 1")
    void alarmShouldIncrementCount() {
        SecurityDegradationAlerter.alarm(SCENE, DETAIL, new RuntimeException("connection refused"));

        assertEquals(EXPECTED_ONE, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("alarm：cause 为 null 时不抛异常且计数自增")
    void alarmShouldTolerateNullCause() {
        assertDoesNotThrow(() -> SecurityDegradationAlerter.alarm(SCENE, DETAIL, null));

        assertEquals(EXPECTED_ONE, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("resetAlarmCount：复位后计数归零")
    void resetShouldZeroCount() {
        SecurityDegradationAlerter.alarm(SCENE, DETAIL, null);

        SecurityDegradationAlerter.resetAlarmCount();

        assertEquals(EXPECTED_ZERO, SecurityDegradationAlerter.alarmCount());
    }
}

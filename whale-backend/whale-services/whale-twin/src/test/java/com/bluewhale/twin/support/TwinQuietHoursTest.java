package com.bluewhale.twin.support;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静默时段单测：格式归一、同日/跨零点窗口、边界与错误配置的 fail-open。
 *
 * @author BlueWhale
 */
class TwinQuietHoursTest {

    @Test
    void normalizesSingleDigitHour() {
        assertEquals("09:05", TwinQuietHours.normalize("9:05"));
        assertEquals("22:00", TwinQuietHours.normalize(" 22:00 "));
    }

    @Test
    void rejectsIllegalTime() {
        assertNull(TwinQuietHours.normalize(null));
        assertNull(TwinQuietHours.normalize(""));
        assertNull(TwinQuietHours.normalize("24:00"));
        assertNull(TwinQuietHours.normalize("22:60"));
        assertNull(TwinQuietHours.normalize("2200"));
    }

    @Test
    void sameDayWindowIsHalfOpen() {
        // [09:00, 18:00)
        assertTrue(TwinQuietHours.active("09:00", "18:00", LocalTime.of(9, 0)));
        assertTrue(TwinQuietHours.active("09:00", "18:00", LocalTime.of(17, 59)));
        assertFalse(TwinQuietHours.active("09:00", "18:00", LocalTime.of(18, 0)));
        assertFalse(TwinQuietHours.active("09:00", "18:00", LocalTime.of(8, 59)));
    }

    @Test
    void crossMidnightWindowCoversBothSides() {
        // 22:00 → 次日 08:00
        assertTrue(TwinQuietHours.active("22:00", "08:00", LocalTime.of(23, 30)));
        assertTrue(TwinQuietHours.active("22:00", "08:00", LocalTime.of(0, 30)));
        assertTrue(TwinQuietHours.active("22:00", "08:00", LocalTime.of(7, 59)));
        assertFalse(TwinQuietHours.active("22:00", "08:00", LocalTime.of(8, 0)));
        assertFalse(TwinQuietHours.active("22:00", "08:00", LocalTime.of(12, 0)));
    }

    @Test
    void missingOrIllegalWindowIsInactive() {
        assertFalse(TwinQuietHours.active(null, "08:00", LocalTime.NOON));
        assertFalse(TwinQuietHours.active("22:00", null, LocalTime.NOON));
        assertFalse(TwinQuietHours.active("bogus", "08:00", LocalTime.of(23, 0)));
        // 起止相同 = 零长度窗口，等价未配置（避免「24 小时静默」误配）
        assertFalse(TwinQuietHours.active("22:00", "22:00", LocalTime.of(22, 0)));
    }

    @Test
    void detectsWindowOverlap() {
        assertTrue(TwinQuietHours.overlaps("22:00", "08:00", "23:00", "07:00"));
        assertTrue(TwinQuietHours.overlaps("09:00", "18:00", "17:00", "19:00"));
        assertFalse(TwinQuietHours.overlaps("09:00", "12:00", "13:00", "18:00"));
        // 跨零点窗口与白天窗口不重叠
        assertFalse(TwinQuietHours.overlaps("22:00", "08:00", "09:00", "18:00"));
    }
}

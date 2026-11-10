package com.bluewhale.tools.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DateUtils} 单元测试
 *
 * @author BlueWhale
 */
class DateUtilsTest {

    /**
     * 日期格式化与解析互逆
     */
    @Test
    @DisplayName("日期格式 yyyy-MM-dd 往返一致")
    void shouldFormatAndParseDate() {
        LocalDate date = LocalDate.of(2026, 10, 24);
        assertEquals("2026-10-24", DateUtils.formatDate(date));
        assertEquals(date, DateUtils.parseDate("2026-10-24"));
    }

    /**
     * 日期时间格式化与解析互逆
     */
    @Test
    @DisplayName("日期时间格式 yyyy-MM-dd HH:mm:ss 往返一致")
    void shouldFormatAndParseDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 10, 24, 9, 5, 7);
        assertEquals("2026-10-24 09:05:07", DateUtils.formatDateTime(dateTime));
        assertEquals(dateTime, DateUtils.parseDateTime("2026-10-24 09:05:07"));
    }

    /**
     * 自定义格式
     */
    @Test
    @DisplayName("自定义格式 yyyyMMdd HHmmss 可格式化与解析")
    void shouldFormatAndParseWithCustomPattern() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 10, 24, 9, 5, 7);
        assertEquals("20261024 090507", DateUtils.format(dateTime, "yyyyMMdd HHmmss"));
        assertEquals(dateTime, DateUtils.parse("20261024 090507", "yyyyMMdd HHmmss"));
    }

    /**
     * 起止时刻
     */
    @Test
    @DisplayName("startOfDay / endOfDay 为当日首末时刻")
    void shouldResolveDayBoundary() {
        LocalDate date = LocalDate.of(2026, 10, 24);
        assertEquals(LocalDateTime.of(2026, 10, 24, 0, 0, 0), DateUtils.startOfDay(date));
        assertEquals(LocalDateTime.of(2026, 10, 24, 23, 59, 59), DateUtils.endOfDay(date));
    }

    /**
     * now / today 返回非空
     */
    @Test
    @DisplayName("now / today 返回当前时间")
    void shouldReturnCurrentTime() {
        assertNotNull(DateUtils.now());
        assertNotNull(DateUtils.today());
    }

    /**
     * 空白文本或空模式被拒绝
     */
    @Test
    @DisplayName("空白输入抛出 IllegalArgumentException")
    void shouldRejectBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> DateUtils.parseDate(" "));
        assertThrows(IllegalArgumentException.class, () -> DateUtils.parseDateTime(null));
        assertThrows(IllegalArgumentException.class,
                () -> DateUtils.format(LocalDateTime.now(), " "));
        assertThrows(NullPointerException.class, () -> DateUtils.formatDate(null));
    }
}

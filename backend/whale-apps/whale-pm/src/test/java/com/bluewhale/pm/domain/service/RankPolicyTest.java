package com.bluewhale.pm.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link RankPolicy} 排序值策略单元测试。
 *
 * @author BlueWhale
 */
class RankPolicyTest {

    @Test
    @DisplayName("initial：返回 12 位定长首值")
    void shouldReturnInitialRank() {
        assertEquals("000000001000", RankPolicy.initial());
        assertEquals(1000L, RankPolicy.parse(RankPolicy.initial()));
    }

    @Test
    @DisplayName("parse：空值与非数字按 0 处理")
    void shouldParseIllegalRankAsZero() {
        assertEquals(0L, RankPolicy.parse(null));
        assertEquals(0L, RankPolicy.parse(" "));
        assertEquals(0L, RankPolicy.parse("TMP123"));
    }

    @Test
    @DisplayName("nextAfter：空值追加到 1000，已有值追加 1000")
    void shouldAppendAfterMaxRank() {
        assertEquals("000000001000", RankPolicy.nextAfter(null));
        assertEquals("000000003000", RankPolicy.nextAfter("000000002000"));
    }

    @Test
    @DisplayName("between：无上界时下界加步长")
    void shouldAppendWhenUpperMissing() {
        assertEquals("000000001000", RankPolicy.between(null, null));
        assertEquals("000000002500", RankPolicy.between(1500L, null));
    }

    @Test
    @DisplayName("between：取上下界中值")
    void shouldReturnMiddleRank() {
        assertEquals("000000001500", RankPolicy.between(1000L, 2000L));
        assertEquals("000000001001", RankPolicy.between(1000L, 1003L));
    }

    @Test
    @DisplayName("between：无空间时返回 null")
    void shouldReturnNullWhenNoGap() {
        assertNull(RankPolicy.between(1000L, 1001L));
        assertNull(RankPolicy.between(2000L, 1000L));
    }

    @Test
    @DisplayName("format：左补零至 12 位")
    void shouldFormatWithLeadingZeros() {
        assertEquals("000000000001", RankPolicy.format(1L));
        assertEquals("000000000999", RankPolicy.format(999L));
    }
}

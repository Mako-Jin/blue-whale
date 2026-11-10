package com.bluewhale.music.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 歌曲标题值对象与版权状态规则单元测试。
 *
 * @author BlueWhale
 */
class SongTitleTest {

    /**
     * 正常标题应去除首尾空白。
     */
    @Test
    void shouldTrimTitle() {
        assertEquals("晴天", SongTitle.of("  晴天  ").value());
    }

    /**
     * 空标题与纯空白标题应被拒绝。
     */
    @Test
    void shouldRejectBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> SongTitle.of(null));
        assertThrows(IllegalArgumentException.class, () -> SongTitle.of("   "));
    }

    /**
     * 超过 120 字符的标题应被拒绝。
     */
    @Test
    void shouldRejectTooLongTitle() {
        String tooLong = "a".repeat(121);
        assertThrows(IllegalArgumentException.class, () -> SongTitle.of(tooLong));
    }

    /**
     * 版权下架状态不得播放，其余状态允许播放。
     */
    @Test
    void shouldRejectPlayWhenTakenDown() {
        assertFalse(CopyrightState.TAKEDOWN.canPlay());
        assertTrue(CopyrightState.DECLARED.canPlay());
        assertTrue(CopyrightState.VALID.canPlay());
    }
}

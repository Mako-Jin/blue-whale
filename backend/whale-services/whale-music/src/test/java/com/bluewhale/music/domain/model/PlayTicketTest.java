package com.bluewhale.music.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 播放票据序列化与三要素绑定测试。
 *
 * @author BlueWhale
 */
class PlayTicketTest {

    /**
     * 序列化与反序列化保持三要素一致。
     */
    @Test
    void shouldRoundTripValue() {
        PlayTicket ticket = new PlayTicket();
        ticket.setTenantId(1L);
        ticket.setUserId(7L);
        ticket.setSongId(9L);
        assertEquals("1|7|9", ticket.toValue());
        PlayTicket parsed = PlayTicket.parse("abc", "1|7|9");
        assertEquals("abc", parsed.getTicket());
        assertEquals(1L, parsed.getTenantId());
        assertEquals(7L, parsed.getUserId());
        assertEquals(9L, parsed.getSongId());
    }

    /**
     * 非法存储值反序列化为 {@code null}。
     */
    @Test
    void shouldRejectMalformedValue() {
        assertNull(PlayTicket.parse("abc", "1|7"));
        assertNull(PlayTicket.parse("abc", "1|x|9"));
        assertNull(PlayTicket.parse("abc", null));
    }

    /**
     * 三要素完全一致才可用于当前请求。
     */
    @Test
    void shouldMatchOnlySameTriple() {
        PlayTicket ticket = new PlayTicket();
        ticket.setTenantId(1L);
        ticket.setUserId(7L);
        ticket.setSongId(9L);
        assertTrue(ticket.matches(1L, 7L, 9L));
        assertFalse(ticket.matches(1L, 8L, 9L));
        assertFalse(ticket.matches(1L, 7L, 10L));
        assertFalse(ticket.matches(2L, 7L, 9L));
    }
}

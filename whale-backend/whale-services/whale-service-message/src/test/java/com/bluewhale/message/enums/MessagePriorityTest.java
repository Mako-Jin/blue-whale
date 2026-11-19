package com.bluewhale.message.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MessagePriority} 优先级解析单元测试。
 *
 * @author BlueWhale
 */
class MessagePriorityTest {

    @Test
    @DisplayName("按名称解析优先级并忽略大小写与空白")
    void shouldParseNameIgnoringCase() {
        assertEquals(MessagePriority.HIGH, MessagePriority.of(" high "));
        assertEquals(MessagePriority.LOW, MessagePriority.of("LOW"));
        assertEquals(MessagePriority.NORMAL, MessagePriority.of("Normal"));
    }

    @Test
    @DisplayName("空值与未知值回退普通优先级")
    void shouldFallbackToNormal() {
        assertEquals(MessagePriority.NORMAL, MessagePriority.of((String) null));
        assertEquals(MessagePriority.NORMAL, MessagePriority.of(" "));
        assertEquals(MessagePriority.NORMAL, MessagePriority.of("URGENT"));
        assertEquals(MessagePriority.NORMAL, MessagePriority.of((Integer) null));
        assertEquals(MessagePriority.NORMAL, MessagePriority.of(99));
    }

    @Test
    @DisplayName("按数值解析优先级且高优先级数值更大")
    void shouldParseCode() {
        assertEquals(MessagePriority.HIGH, MessagePriority.of(30));
        assertEquals(MessagePriority.NORMAL, MessagePriority.of(20));
        assertEquals(MessagePriority.LOW, MessagePriority.of(10));
        assertTrue(MessagePriority.HIGH.getCode() > MessagePriority.NORMAL.getCode());
        assertTrue(MessagePriority.NORMAL.getCode() > MessagePriority.LOW.getCode());
    }
}

package com.bluewhale.im.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseEventParser} 单元测试：事件块解析、注释与多行 data。
 *
 * @author BlueWhale
 */
class SseEventParserTest {

    @Test
    @DisplayName("event + data 在空行处产出完整事件")
    void shouldParseEventBlock() {
        SseEventParser parser = new SseEventParser();

        assertTrue(parser.accept("event:meta").isEmpty());
        assertTrue(parser.accept("data:{\"intent\":\"ARCH\"}").isEmpty());

        Optional<SseEventParser.SseEvent> event = parser.accept("");
        assertTrue(event.isPresent());
        assertEquals("meta", event.get().name());
        assertEquals("{\"intent\":\"ARCH\"}", event.get().data());
    }

    @Test
    @DisplayName("注释行被忽略，无事件名时默认 message")
    void shouldIgnoreCommentsAndUseDefaultName() {
        SseEventParser parser = new SseEventParser();
        parser.accept(": keep-alive");
        parser.accept("data:hello");

        Optional<SseEventParser.SseEvent> event = parser.flush();
        assertTrue(event.isPresent());
        assertEquals("message", event.get().name());
        assertEquals("hello", event.get().data());
    }

    @Test
    @DisplayName("多行 data 以换行拼接")
    void shouldJoinMultiLineData() {
        SseEventParser parser = new SseEventParser();
        parser.accept("event:delta");
        parser.accept("data:line1");
        parser.accept("data:line2");

        Optional<SseEventParser.SseEvent> event = parser.accept("");
        assertTrue(event.isPresent());
        assertEquals("line1\nline2", event.get().data());
    }

    @Test
    @DisplayName("无待发内容时 flush 返回空")
    void shouldReturnEmptyWhenNothingPending() {
        assertTrue(new SseEventParser().flush().isEmpty());
    }
}

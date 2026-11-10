package com.bluewhale.im.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentStreamClient.LinePump} 单元测试：顺序读取、空闲超时与读取异常上抛。
 *
 * @author BlueWhale
 */
class AgentStreamClientTest {

    /** 空闲超时（毫秒），取值足够小以保证用例快速失败 */
    private static final long IDLE_TIMEOUT_MILLIS = 150L;

    /** 模拟上游沉默时长（秒），远大于空闲超时 */
    private static final long SILENCE_SECONDS = 3L;

    @Test
    @DisplayName("逐行按序返回，流结束后返回空")
    void shouldReadAllLinesThenEnd() throws Exception {
        List<String> lines = new ArrayList<>(List.of("event:meta", "data:{}", ""));
        try (AgentStreamClient.LinePump pump = new AgentStreamClient.LinePump(streamOf(lines.iterator()),
                IDLE_TIMEOUT_MILLIS)) {
            for (String expected : lines) {
                Optional<String> actual = pump.next();
                assertTrue(actual.isPresent());
                assertEquals(expected, actual.get());
            }
            assertTrue(pump.next().isEmpty());
        }
    }

    @Test
    @DisplayName("上游沉默超过空闲阈值时抛出读超时")
    void shouldTimeoutWhenUpstreamIdles() {
        Iterator<String> slow = new Iterator<>() {

            private int index;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public String next() {
                if (index++ == 0) {
                    return "data:first";
                }
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(SILENCE_SECONDS));
                return "data:late";
            }
        };
        try (AgentStreamClient.LinePump pump = new AgentStreamClient.LinePump(streamOf(slow),
                IDLE_TIMEOUT_MILLIS)) {
            assertThrows(SocketTimeoutException.class, () -> {
                while (pump.next().isPresent()) {
                    // 持续读取直到超时
                }
            });
        }
    }

    @Test
    @DisplayName("读取线程异常在流结束时以 IOException 上抛")
    void shouldSurfaceReadFailureAsIoException() {
        Iterator<String> failing = new Iterator<>() {

            private int index;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public String next() {
                if (index++ == 0) {
                    return "data:one";
                }
                throw new IllegalStateException("boom");
            }
        };
        try (AgentStreamClient.LinePump pump = new AgentStreamClient.LinePump(streamOf(failing),
                IDLE_TIMEOUT_MILLIS)) {
            assertThrows(IOException.class, () -> {
                while (pump.next().isPresent()) {
                    // 第二行触发读取异常
                }
            });
        }
    }

    /**
     * 把迭代器包装为顺序流（模拟 HttpClient 的逐行响应）。
     *
     * @param iterator 行迭代器
     * @return 行流
     */
    private static Stream<String> streamOf(Iterator<String> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }
}

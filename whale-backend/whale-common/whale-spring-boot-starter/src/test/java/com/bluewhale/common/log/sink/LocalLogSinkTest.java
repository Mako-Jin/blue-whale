package com.bluewhale.common.log.sink;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link LocalLogSink} 单元测试（REQ-SEC-006）：异步投递与线程池拒绝时的降级。
 *
 * @author BlueWhale
 */
class LocalLogSinkTest {

    @Test
    @DisplayName("submit：任务被投递到线程池执行")
    void shouldSubmitTaskToExecutor() {
        AtomicBoolean executed = new AtomicBoolean(false);
        LocalLogSink sink = new LocalLogSink(Runnable::run);

        sink.submit(() -> executed.set(true), "API");

        assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("submit：线程池拒绝时只告警，不向业务链路冒泡")
    void shouldSwallowRejectedExecution() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("queue full");
        };
        LocalLogSink sink = new LocalLogSink(rejecting);
        AtomicBoolean executed = new AtomicBoolean(false);

        assertThatCode(() -> sink.submit(() -> executed.set(true), "OUTBOUND")).doesNotThrowAnyException();
        assertThat(executed).isFalse();
    }
}

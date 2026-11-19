package com.bluewhale.message.dispatch;

import com.bluewhale.message.config.MessageSendProperties;
import com.bluewhale.message.enums.MessagePriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PriorityMessageDispatchExecutor} 调度顺序与回压单元测试。
 *
 * @author BlueWhale
 */
class PriorityMessageDispatchExecutorTest {

    /** 等待超时（秒） */
    private static final long TIMEOUT_SECONDS = 5L;

    @Test
    @DisplayName("工作线程空闲后高优先级任务先出队")
    void shouldRunHigherPriorityFirst() throws InterruptedException {
        MessageSendProperties properties = new MessageSendProperties();
        properties.setPoolSize(1);
        properties.setQueueCapacity(10);
        PriorityMessageDispatchExecutor executor = new PriorityMessageDispatchExecutor(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        try {
            executor.submit(MessagePriority.HIGH, () -> {
                started.countDown();
                await(release);
                order.add("blocker");
            });
            assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "占位任务未启动");
            executor.submit(MessagePriority.LOW, () -> order.add("low"));
            executor.submit(MessagePriority.HIGH, () -> order.add("high"));
            release.countDown();
            assertTrue(awaitSize(order, 3), "任务未在超时内全部执行: " + order);
        } finally {
            release.countDown();
            executor.destroy();
        }
        assertEquals(List.of("blocker", "high", "low"), order);
    }

    @Test
    @DisplayName("队列满时回压到提交线程，不丢任务")
    void shouldRunInlineWhenQueueFull() throws InterruptedException {
        MessageSendProperties properties = new MessageSendProperties();
        properties.setPoolSize(1);
        properties.setQueueCapacity(0);
        PriorityMessageDispatchExecutor executor = new PriorityMessageDispatchExecutor(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean inline = new AtomicBoolean();
        try {
            executor.submit(MessagePriority.NORMAL, () -> {
                started.countDown();
                await(release);
            });
            assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "占位任务未启动");
            executor.submit(MessagePriority.NORMAL, () -> inline.set(true));
            assertTrue(inline.get(), "队列满时任务未回压到提交线程执行");
        } finally {
            release.countDown();
            executor.destroy();
        }
    }

    /**
     * 无限等待闩锁释放（中断时恢复中断标记）。
     *
     * @param latch 闩锁
     */
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 等待列表达到目标元素个数。
     *
     * @param values 目标列表
     * @param size   期望元素个数
     * @return 超时前达到目标个数返回 {@code true}
     * @throws InterruptedException 等待被中断
     */
    private static boolean awaitSize(List<String> values, int size) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (values.size() >= size) {
                return true;
            }
            Thread.sleep(10L);
        }
        return values.size() >= size;
    }
}

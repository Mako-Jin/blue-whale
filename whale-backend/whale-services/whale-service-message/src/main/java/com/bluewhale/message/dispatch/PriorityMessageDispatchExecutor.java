package com.bluewhale.message.dispatch;

import com.bluewhale.message.config.MessageSendProperties;
import com.bluewhale.message.enums.MessagePriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于有界优先级队列的异步投递调度器。
 *
 * <p>{@link PriorityBlockingQueue} 本身无界，这里用 {@link Semaphore} 按
 * {@code poolSize + queueCapacity} 计数做回压：许可耗尽时直接在提交线程执行任务
 * （等价于 {@code CallerRunsPolicy}），既不丢消息也不无限堆积。</p>
 *
 * <p>同一优先级内按提交顺序（序号）先进先出，避免高优先级任务饿死低优先级任务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class PriorityMessageDispatchExecutor implements MessageDispatchExecutor, DisposableBean {

    /** 关闭时等待存量任务完成的时长（秒） */
    private static final long AWAIT_TERMINATION_SECONDS = 30L;

    /** 线程名序号 */
    private static final AtomicInteger THREAD_INDEX = new AtomicInteger();

    private final ThreadPoolExecutor executor;

    /** 可用许可：线程数与队列容量之和 */
    private final Semaphore capacity;

    /** 提交序号：同优先级内保证先进先出 */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 按投递配置构建调度器。
     *
     * @param properties 投递配置（线程数、队列容量）
     */
    public PriorityMessageDispatchExecutor(MessageSendProperties properties) {
        int poolSize = Math.max(1, properties.getPoolSize());
        int queueCapacity = Math.max(0, properties.getQueueCapacity());
        this.capacity = new Semaphore(poolSize + queueCapacity);
        this.executor = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "message-dispatch-" + THREAD_INDEX.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public void submit(MessagePriority priority, Runnable task) {
        MessagePriority effective = priority == null ? MessagePriority.NORMAL : priority;
        if (!capacity.tryAcquire()) {
            log.warn("异步投递队列已满，回压到提交线程: priority={}", effective);
            runInline(effective, task);
            return;
        }
        try {
            executor.execute(new PriorityTask(effective, sequence.incrementAndGet(), task, capacity));
        } catch (RejectedExecutionException e) {
            capacity.release();
            runInline(effective, task);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(AWAIT_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 在提交线程直接执行任务（队列满或调度器已关闭时）。
     *
     * @param priority 优先级
     * @param task     投递任务
     */
    private static void runInline(MessagePriority priority, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("异步投递回压执行失败: priority={}, reason={}", priority, e.getMessage(), e);
        }
    }

    /**
     * 可排序的投递任务：优先级高的先出队，同优先级按提交序号先进先出。
     *
     * <p>任务结束后释放许可，实现“在途 + 排队”总量受限。</p>
     */
    private static final class PriorityTask implements Runnable, Comparable<PriorityTask> {

        /** 任务优先级 */
        private final MessagePriority priority;

        /** 提交序号 */
        private final long sequence;

        /** 实际投递任务 */
        private final Runnable delegate;

        /** 许可计数器 */
        private final Semaphore capacity;

        /**
         * 构造可排序任务。
         *
         * @param priority 优先级
         * @param sequence 提交序号
         * @param delegate 实际投递任务
         * @param capacity 许可计数器
         */
        private PriorityTask(MessagePriority priority, long sequence, Runnable delegate, Semaphore capacity) {
            this.priority = priority;
            this.sequence = sequence;
            this.delegate = delegate;
            this.capacity = capacity;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                capacity.release();
            }
        }

        @Override
        public int compareTo(PriorityTask other) {
            int byPriority = Integer.compare(other.priority.getCode(), priority.getCode());
            return byPriority == 0 ? Long.compare(sequence, other.sequence) : byPriority;
        }
    }
}

package com.bluewhale.common.jobschedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RemoteJobRegistry} 在途执行计数与停机等待单元测试。
 *
 * <p>停机时先注销、再等在途任务结束，是避免执行流水永远停在「执行中」的关键；
 * 这里验证计数在执行期间可见、执行结束后归零，且等待能在任务完成后返回。</p>
 *
 * @author BlueWhale
 */
class RemoteJobRegistryTest {

    private final RemoteJobRegistry registry = new RemoteJobRegistry();

    @Test
    @DisplayName("在途计数：执行期间为 1，结束后归零")
    void shouldTrackRunningCount() throws Exception {
        SampleJobs jobs = new SampleJobs(registry);
        registry.register("demo.probe", jobs, SampleJobs.class.getMethod("probe"));

        registry.invoke("demo.probe");

        assertEquals(1, jobs.getObservedInside());
        assertEquals(0, registry.runningCount());
    }

    @Test
    @DisplayName("停机等待：在途任务结束后 waitIdle 返回成功")
    void shouldAwaitRunningJobs() throws Exception {
        SampleJobs jobs = new SampleJobs(registry);
        registry.register("demo.slow", jobs, SampleJobs.class.getMethod("slow"));
        Thread worker = new Thread(() -> registry.invoke("demo.slow"));
        worker.start();
        long deadline = System.currentTimeMillis() + 2000;
        while (registry.runningCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, registry.runningCount());

        assertTrue(registry.awaitIdle(3000));
        worker.join(1000);
        assertEquals(0, registry.runningCount());
    }

    /**
     * 测试用任务方法宿主
     */
    static class SampleJobs {

        private final RemoteJobRegistry registry;

        private final AtomicInteger observedInside = new AtomicInteger();

        SampleJobs(RemoteJobRegistry registry) {
            this.registry = registry;
        }

        /**
         * 记录执行期间的计数
         *
         * @return 固定结果
         */
        public String probe() {
            observedInside.set(registry.runningCount());
            return "ok";
        }

        /**
         * 模拟慢任务
         *
         * @return 固定结果
         * @throws InterruptedException 被中断
         */
        public String slow() throws InterruptedException {
            Thread.sleep(300);
            return "ok";
        }

        int getObservedInside() {
            return observedInside.get();
        }
    }
}

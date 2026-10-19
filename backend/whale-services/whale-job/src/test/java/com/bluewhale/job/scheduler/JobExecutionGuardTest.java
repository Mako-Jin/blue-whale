package com.bluewhale.job.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JobExecutionGuard} 实例内互斥与「待补跑一次」标记单元测试。
 *
 * @author BlueWhale
 */
class JobExecutionGuardTest {

    private final JobExecutionGuard guard = new JobExecutionGuard();

    @Test
    @DisplayName("tryEnter：首次进入成功，同一任务重复进入失败")
    void shouldRejectDuplicateEnter() {
        assertTrue(guard.tryEnter(1L));
        assertFalse(guard.tryEnter(1L));
    }

    @Test
    @DisplayName("exit：退出后可再次进入")
    void shouldAllowReenterAfterExit() {
        guard.tryEnter(1L);
        guard.exit(1L);

        assertTrue(guard.tryEnter(1L));
    }

    @Test
    @DisplayName("isRunning：进入后为真，退出后为假")
    void shouldReportRunningState() {
        assertFalse(guard.isRunning(9L));

        guard.tryEnter(9L);
        assertTrue(guard.isRunning(9L));

        guard.exit(9L);
        assertFalse(guard.isRunning(9L));
    }

    @Test
    @DisplayName("consumePendingOnce：无标记返回 false")
    void shouldReturnFalseWhenNoPending() {
        assertFalse(guard.consumePendingOnce(2L));
    }

    @Test
    @DisplayName("consumePendingOnce：标记后只能消费一次")
    void shouldConsumePendingOnlyOnce() {
        guard.markPendingOnce(2L);

        assertTrue(guard.consumePendingOnce(2L));
        assertFalse(guard.consumePendingOnce(2L));
    }

    @Test
    @DisplayName("不同任务之间的互斥与标记互不影响")
    void shouldIsolateDifferentJobs() {
        assertTrue(guard.tryEnter(1L));
        assertTrue(guard.tryEnter(2L));

        guard.exit(1L);

        assertTrue(guard.isRunning(2L));
        assertFalse(guard.isRunning(1L));
    }
}

package com.bluewhale.job.health;

import com.bluewhale.job.scheduler.JobScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 调度健康项测试
 *
 * @author BlueWhale
 */
class JobSchedulerHealthIndicatorTest {

    @Test
    @DisplayName("线程池未就绪时健康项为 DOWN")
    void shouldReportDownWhenSchedulerNotRunning() {
        JobScheduler scheduler = mock(JobScheduler.class);
        when(scheduler.isSchedulerRunning()).thenReturn(false);

        Health health = new JobSchedulerHealthIndicator(scheduler).health();

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    @DisplayName("线程池就绪时健康项为 UP 并携带调度水位")
    void shouldReportUpWithPoolDetails() {
        JobScheduler scheduler = mock(JobScheduler.class);
        when(scheduler.isSchedulerRunning()).thenReturn(true);
        when(scheduler.scheduledJobCount()).thenReturn(3);
        when(scheduler.poolSize()).thenReturn(8);
        when(scheduler.activeThreadCount()).thenReturn(2);
        when(scheduler.queueSize()).thenReturn(1);

        Health health = new JobSchedulerHealthIndicator(scheduler).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(3, health.getDetails().get("scheduledJobs"));
        assertEquals(8, health.getDetails().get("poolSize"));
        assertEquals(2, health.getDetails().get("activeThreads"));
        assertEquals(1, health.getDetails().get("queueSize"));
    }
}

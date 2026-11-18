package com.bluewhale.job.health;

import com.bluewhale.job.scheduler.JobScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 调度引擎健康项：暴露线程池是否可用以及当前调度水位。
 *
 * <p>指标（Prometheus）回答「跑了多少、多慢」，健康项回答「调度还能不能工作」。
 * 线程池未初始化或已关闭时返回 {@code DOWN}，运维可据此直接摘流量或重启。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class JobSchedulerHealthIndicator implements HealthIndicator {

    private final JobScheduler jobScheduler;

    /**
     * 计算调度健康状态
     *
     * @return 健康状态与线程池水位明细
     */
    @Override
    public Health health() {
        if (!jobScheduler.isSchedulerRunning()) {
            return Health.down().withDetail("reason", "调度线程池未初始化或已关闭").build();
        }
        return Health.up()
                .withDetail("scheduledJobs", jobScheduler.scheduledJobCount())
                .withDetail("poolSize", jobScheduler.poolSize())
                .withDetail("activeThreads", jobScheduler.activeThreadCount())
                .withDetail("queueSize", jobScheduler.queueSize())
                .build();
    }
}

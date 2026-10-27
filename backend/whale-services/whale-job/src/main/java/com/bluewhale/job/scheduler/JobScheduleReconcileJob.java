package com.bluewhale.job.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务调度断连重对齐（F-02）。
 *
 * <p>多实例通过 Redis Pub/Sub 广播调度变更；断连期间的消息会丢失，且 Redis Pub/Sub 不保证补发。
 * 本任务周期性按数据库运行中任务全量重对齐本地 {@link JobScheduler} 的注册状态，
 * 单轮异常只记录日志，不影响后续调度。</p>
 *
 * <p>默认开启，可通过 {@code whale.job.schedule-reconcile.enabled=false} 关闭。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.job.schedule-reconcile", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class JobScheduleReconcileJob {

    private final JobScheduler jobScheduler;

    /**
     * 周期重对齐本地调度：默认首次延迟 60 秒，之后每 5 分钟一次
     */
    @Scheduled(fixedDelayString = "${whale.job.schedule-reconcile.interval-ms:300000}",
            initialDelayString = "${whale.job.schedule-reconcile.initial-delay-ms:60000}")
    public void reconcile() {
        try {
            jobScheduler.reconcileWithDatabase();
        } catch (Exception e) {
            log.warn("定时任务调度重对齐失败，等待下一周期重试: reason={}", e.getMessage(), e);
        }
    }
}

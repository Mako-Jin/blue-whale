package com.bluewhale.video.application.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 观看历史保留期清理任务：每日 04:40 删除超过保留天数（默认 180 天）的观看记录。
 *
 * <p>保留天数由 {@code whale.video.history-retention-days} 控制；任务按批删除并限制
 * 单次总批数，避免一次性大事务。后台任务无租户上下文，租户插件不加条件，可覆盖全部
 * 租户；多实例同时执行时删除幂等，批间重复执行只会返回 0 条，不产生副作用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchHistoryCleanupScheduler {

    /** 单批删除条数：控制单条 SQL 的锁范围与耗时 */
    private static final int BATCH_LIMIT = 500;

    /** 单次任务最多执行的批数（默认最多清理 10000 条，超出次日继续） */
    private static final int MAX_BATCHES = 20;

    private final WatchHistoryRepository watchHistoryRepository;

    private final VideoProperties videoProperties;

    /**
     * 清理超过保留期的观看历史。
     *
     * <p>整轮失败不再吞掉：调度中心按「业务码非 0 / 异常」判定失败并决定是否重试与告警，
     * 吞异常会让执行流水长期显示成功，清理静默停摆。进程内模式由注册器兜底捕获，
     * 不会中断调度线程。</p>
     *
     * @return 执行摘要（删除条数与批数）
     */
    @ScheduledJob(code = "video.watch-history.cleanup", cron = "0 40 4 * * ?", timeoutSeconds = 1800)
    public String cleanup() {
        int retentionDays = Math.max(videoProperties.getHistoryRetentionDays(), 1);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = 0;
        int batches = 0;
        try {
            while (batches < MAX_BATCHES) {
                int batchDeleted = watchHistoryRepository.deleteWatchedBefore(cutoff, BATCH_LIMIT);
                deleted += batchDeleted;
                batches++;
                if (batchDeleted < BATCH_LIMIT) {
                    break;
                }
            }
        } catch (RuntimeException ex) {
            log.error("观看历史清理失败: retentionDays={}, cutoff={}, deleted={}",
                    retentionDays, cutoff, deleted, ex);
            throw ex;
        }
        log.info("观看历史清理完成: retentionDays={}, cutoff={}, batches={}, deleted={}",
                retentionDays, cutoff, batches, deleted);
        return "deleted=" + deleted + ", batches=" + batches;
    }
}

package com.bluewhale.alert.job;

import com.bluewhale.alert.config.AlertRetentionProperties;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 告警记录保留策略任务。
 *
 * <p>每天凌晨清理超过保留期的已恢复 / 已静默记录。运行在无租户上下文的调度线程中，
 * 租户插件按约定跳过条件拼装，因此可跨租户清理。单次只删一批，避免大事务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRecordRetentionJob {

    private final AlertRecordMapper alertRecordMapper;

    private final AlertRetentionProperties retentionProperties;

    /**
     * 按保留天数清理历史告警记录。
     */
    @Scheduled(cron = "${whale.alert.retention.cron:0 30 3 * * ?}")
    public void purgeExpiredRecords() {
        if (!retentionProperties.isEnabled()) {
            return;
        }
        int days = Math.max(1, retentionProperties.getDays());
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        int limit = Math.max(1, retentionProperties.getBatchSize());
        try {
            int purged = alertRecordMapper.purgeBefore(threshold, limit);
            log.info("告警记录清理完成: threshold={}, days={}, purged={}", threshold, days, purged);
        } catch (RuntimeException e) {
            log.error("告警记录清理失败: threshold={}, reason={}", threshold, e.getMessage(), e);
        }
    }
}

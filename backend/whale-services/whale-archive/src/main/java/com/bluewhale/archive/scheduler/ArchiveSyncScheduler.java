package com.bluewhale.archive.scheduler;

import com.bluewhale.archive.service.ArchiveSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 档案同步定时任务。
 *
 * <p>仅当 {@code whale.archive.sync.enabled=true} 时注册；单轮异常只记录日志，
 * 不影响后续调度（失败来源由台账重试机制兜底）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.archive.sync", name = "enabled", havingValue = "true")
public class ArchiveSyncScheduler {

    private final ArchiveSyncService archiveSyncService;

    /**
     * 按配置表达式执行一轮同步。
     */
    @Scheduled(cron = "${whale.archive.sync.cron:0 */5 * * * *}")
    public void sync() {
        try {
            archiveSyncService.runOnce();
        } catch (Exception e) {
            log.warn("定时档案同步失败: {}", e.getMessage());
        }
    }
}

package com.bluewhale.file.schedule;

import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.lock.ScheduledTaskLock;
import com.bluewhale.file.service.FileInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 存量历史版本定时清理任务。
 *
 * <p>批次 18 的版本保留上限只在新版本产生时生效；本任务按 cron 扫描全部文件，清理存量文件中
 * 超出上限的最旧版本。默认关闭，需要显式开启；无租户上下文，可跨租户清理，且不发布领域事件。</p>
 *
 * <p>多实例并发：通过 {@link ScheduledTaskLock} 保证同一时刻只有一个实例执行；
 * 清理按版本行物理删除，重复执行不会误删根版本；单个版本失败保留待下轮重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionCleanupTask {

    /** 任务锁名称 */
    private static final String LOCK_NAME = "file:version-cleanup";

    /** 任务锁租约：全量游标扫描耗时较长，租约留足余量 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(60);

    private final FileInfoService fileInfoService;

    private final FileProperties fileProperties;

    private final ScheduledTaskLock scheduledTaskLock;

    /**
     * 按配置周期清理存量超限版本；开关关闭或保留上限非正时直接返回。
     */
    @Scheduled(cron = "${whale.file.version.cleanup-cron:0 30 4 * * ?}")
    public void cleanup() {
        FileProperties.Version config = fileProperties.getVersion();
        if (!config.isCleanupEnabled() || config.getMaxVersions() <= 0) {
            return;
        }
        Optional<ScheduledTaskLock.Handle> handle = scheduledTaskLock.tryAcquire(LOCK_NAME, LOCK_TTL);
        if (handle.isEmpty()) {
            log.info("存量历史版本清理已由其他实例执行，跳过本轮");
            return;
        }
        try (ScheduledTaskLock.Handle lock = handle.get()) {
            int pruned = fileInfoService.pruneExcessVersions();
            if (pruned > 0) {
                log.info("存量历史版本定时清理完成: pruned={}", pruned);
            }
        }
    }
}

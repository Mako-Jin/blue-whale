package com.bluewhale.file.schedule;

import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.lock.ScheduledTaskLock;
import com.bluewhale.file.domain.model.FileRecycleItem;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.metrics.FileMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 回收站到期物理清理任务。
 *
 * <p>按 {@code whale.file.recycle.retention-days} 清理超过保留期的逻辑删除记录：
 * 先按记录落盘介质删除物理对象（对象不存在视为成功），再物理删除元数据行。
 * 单条失败仅告警并保留记录，等待下一轮重试；无租户上下文，可跨租户清理。</p>
 *
 * <p>多实例并发：通过 {@link ScheduledTaskLock} 保证同一时刻只有一个实例执行；
 * 另外对象删除幂等、元数据删除条件为 {@code deleted = 1}，即使租约到期后重叠执行也不会误删
 * 已恢复的文件。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecyclePurgeTask {

    /** 任务锁名称 */
    private static final String LOCK_NAME = "file:recycle-purge";

    /** 任务锁租约：按批上限估算，超时说明任务异常，允许其他实例接手 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** 单次执行最多处理的批次数，避免历史积压时单轮任务长时间占用资源 */
    private static final int MAX_ROUNDS = 10;

    private final FileAssetRepository fileAssetRepository;

    private final FileStorage fileStorage;

    private final FileProperties fileProperties;

    private final FileMetrics fileMetrics;

    private final ScheduledTaskLock scheduledTaskLock;

    /**
     * 按配置周期清理到期回收站记录；开关关闭或保留天数非正时直接返回。
     */
    @Scheduled(cron = "${whale.file.recycle.purge-cron:0 30 3 * * ?}")
    public void purgeExpired() {
        FileProperties.Recycle config = fileProperties.getRecycle();
        if (!config.isPurgeEnabled()) {
            return;
        }
        if (config.getRetentionDays() <= 0) {
            // 非正数表示无限期保留，避免误配为 0 时把整个回收站清空
            return;
        }
        Optional<ScheduledTaskLock.Handle> handle = scheduledTaskLock.tryAcquire(LOCK_NAME, LOCK_TTL);
        if (handle.isEmpty()) {
            log.info("回收站到期清理已由其他实例执行，跳过本轮");
            return;
        }
        try (ScheduledTaskLock.Handle lock = handle.get()) {
            purgeExpired(config);
        }
    }

    /**
     * 分批清理到期记录
     *
     * @param config 回收站配置
     */
    private void purgeExpired(FileProperties.Recycle config) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(config.getRetentionDays());
        int batchSize = config.getPurgeBatchSize();
        int purged = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<FileRecycleItem> batch = fileAssetRepository.findExpiredRecycle(cutoff, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            int purgedInRound = 0;
            for (FileRecycleItem item : batch) {
                if (purgeOne(item)) {
                    purgedInRound++;
                }
            }
            purged += purgedInRound;
            // 本轮没有任何进展（全部失败）或未取满一批，立即退出，避免同一批失败记录被反复扫描 10 轮
            if (purgedInRound == 0 || batch.size() < batchSize) {
                break;
            }
        }
        if (purged > 0) {
            log.info("回收站到期清理完成: cutoff={}, purged={}", cutoff, purged);
        }
    }

    /**
     * 清理单条回收站记录
     *
     * @param item 回收站投影
     * @return 是否实际清理了元数据行
     */
    private boolean purgeOne(FileRecycleItem item) {
        try {
            fileStorage.delete(item.bucket(), item.objectKey(), item.storageType());
            boolean removed = fileAssetRepository.purgeById(item.id());
            fileMetrics.recordPurge(true);
            if (!removed) {
                log.info("回收站记录在清理期间被恢复或已清理，跳过: id={}", item.id());
            }
            return removed;
        } catch (RuntimeException e) {
            fileMetrics.recordPurge(false);
            log.warn("回收站到期清理失败，保留记录待下轮重试: id={}, bucket={}, objectKey={}",
                    item.id(), item.bucket(), item.objectKey(), e);
            return false;
        }
    }
}

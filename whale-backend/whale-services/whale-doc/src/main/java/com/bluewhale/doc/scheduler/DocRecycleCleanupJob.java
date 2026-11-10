package com.bluewhale.doc.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.doc.application.service.DocRecycleService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.persistence.query.DocTrashRootRow;
import com.bluewhale.doc.support.DocMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站到期自动清理任务（M2）。
 *
 * <p>按 {@code whale.doc.recycle-retention-days} 计算到期阈值，跨租户分批查找回收站根节点并
 * 物理清除。每个子树在独立事务中处理，单条失败不影响其余条目。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.doc", name = "recycle-cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
public class DocRecycleCleanupJob {

    /** 单次任务最大轮次，防止异常数据导致长时间占用 */
    private static final int MAX_ROUNDS = 20;

    private final DocRecycleService recycleService;

    private final DocProperties docProperties;

    private final DocMetrics docMetrics;

    /**
     * 按 Cron 清理到期回收站子树。
     *
     * @return 执行摘要（清除的回收站子树数量）
     */
    @ScheduledJob(code = "doc.recycle.cleanup",
            cron = "${whale.doc.recycle-cleanup-cron:0 30 3 * * *}",
            timeoutSeconds = 1800)
    public String cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(docProperties.getRecycleRetentionDays());
        int removed = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<DocTrashRootRow> roots = recycleService.expiredRoots(cutoff,
                    docProperties.getRecycleCleanupBatchSize());
            if (roots.isEmpty()) {
                break;
            }
            removed += purgeBatch(roots);
        }
        docMetrics.recordRecyclePurged(removed);
        log.info("回收站到期清理完成: removed={}, cutoff={}", removed, cutoff);
        return "removed=" + removed;
    }

    /**
     * 逐个清除一批到期回收站子树。
     *
     * @param roots 到期根节点
     * @return 成功清除数量
     */
    private int purgeBatch(List<DocTrashRootRow> roots) {
        int removed = 0;
        for (DocTrashRootRow root : roots) {
            try {
                recycleService.purgeExpired(root.getTenantId(), root.getPageId());
                removed++;
            } catch (RuntimeException ex) {
                log.warn("回收站到期清理失败: tenant={}, root={}", root.getTenantId(), root.getPageId(), ex);
            }
        }
        return removed;
    }
}

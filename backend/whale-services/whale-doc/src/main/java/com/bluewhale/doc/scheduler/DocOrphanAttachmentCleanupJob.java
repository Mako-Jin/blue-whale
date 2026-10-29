package com.bluewhale.doc.scheduler;

import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAttachmentMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocOrphanAttachmentRow;
import com.bluewhale.doc.support.DocMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 孤儿附件引用清理任务（M5）。
 *
 * <p>页面被物理清除后若仍残留 {@code doc_page_attachment} 行，会形成无主引用。
 * 任务跨租户分批查找并物理删除这些行；whale-file 中的文件字节由文件服务生命周期
 * 策略处理，本任务不调用文件服务删除接口。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.doc", name = "orphan-cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
public class DocOrphanAttachmentCleanupJob {

    /** 单次任务最大轮次，防止异常数据导致长时间占用 */
    private static final int MAX_ROUNDS = 20;

    private final DocPageAttachmentMapper attachmentMapper;

    private final DocProperties docProperties;

    private final DocMetrics docMetrics;

    /**
     * 按 Cron 清理无主附件引用。
     *
     * @return 本轮删除的附件引用行数
     */
    @Scheduled(cron = "${whale.doc.orphan-cleanup-cron:0 30 4 * * *}")
    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(docProperties.getOrphanAttachmentRetentionHours());
        int removed = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<DocOrphanAttachmentRow> rows = attachmentMapper.selectOrphanAttachments(cutoff,
                    docProperties.getOrphanCleanupBatchSize());
            if (rows.isEmpty()) {
                break;
            }
            int deleted = deleteBatch(rows);
            if (deleted == 0) {
                log.warn("孤儿附件引用本轮未能删除任何行，停止后续轮次: rows={}", rows.size());
                break;
            }
            removed += deleted;
        }
        docMetrics.recordOrphanRemoved(removed);
        log.info("孤儿附件引用清理完成: removed={}, cutoff={}", removed, cutoff);
        return removed;
    }

    /**
     * 按租户分组删除一批无主附件引用。
     *
     * @param rows 无主附件引用行
     * @return 成功删除行数
     */
    private int deleteBatch(List<DocOrphanAttachmentRow> rows) {
        Map<Long, List<Long>> byTenant = new LinkedHashMap<>();
        for (DocOrphanAttachmentRow row : rows) {
            byTenant.computeIfAbsent(row.getTenantId(), key -> new ArrayList<>())
                    .add(row.getAttachmentId());
        }
        int removed = 0;
        for (Map.Entry<Long, List<Long>> entry : byTenant.entrySet()) {
            try {
                removed += attachmentMapper.deleteOrphanByIds(entry.getKey(), entry.getValue());
            } catch (RuntimeException ex) {
                docMetrics.recordOrphanFailed();
                log.warn("孤儿附件引用清理失败: tenant={}, rows={}", entry.getKey(),
                        entry.getValue().size(), ex);
            }
        }
        return removed;
    }
}

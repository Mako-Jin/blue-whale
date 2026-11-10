package com.bluewhale.audit.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.audit.retention.entity.AuditArchiveWatermark;
import com.bluewhale.audit.retention.mapper.AuditArchiveWatermarkMapper;
import com.bluewhale.audit.support.AuditArchiveClient;
import com.bluewhale.audit.support.AuditCsvWriter;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@code audit_log} 归档任务（AUTH-09）。
 *
 * <p>由 {@link LogRetentionScheduler} 在 {@code whale.audit.retention.archive-enabled=true} 时
 * 随保留期 cron 触发：查询 {@code create_time < 当前时间 - 保留天数} 的记录，导出为 CSV
 * 并上传 whale-file 内网接口归档。</p>
 *
 * <p><b>边界（合规数据安全优先）：本任务只做「导出归档 + 记录归档结果」，
 * 绝不对 {@code audit_log} 做物理删除或逻辑删除</b>——该表为 append-only，写入后不允许修改/删除。
 * 既有 {@link LogRetentionCleaner} 的保留期清理也仍只覆盖 {@code api_log}/{@code outbound_log}/
 * {@code frontend_log}/{@code behavior_log} 四张日志表，同样不覆盖 {@code audit_log}。</p>
 *
 * <p>单次归档异常（查询超限、CSV 生成、上传失败）均仅告警，不冒泡、不打断调度线程。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogArchiver {

    /** 上传结果中对象存储键字段名（与 whale-file FileUploadVO 对齐） */
    private static final String FIELD_OBJECT_KEY = "objectKey";

    /** 归档水位固定主键（单行表） */
    private static final long WATERMARK_ID = 1L;

    private final AuditLogMapper auditLogMapper;

    private final AuditArchiveWatermarkMapper watermarkMapper;

    private final AuditArchiveClient archiveClient;

    private final AuditExportProperties exportProperties;

    /**
     * 归档 {@code audit_log} 中早于保留期的记录：导出 CSV 并上传，绝不删除
     *
     * <p>按 {@code audit_archive_watermark} 水位增量续查：上传成功后才推进水位，
     * 因此同一记录只导出一轮，失败时下轮重试同一区间。</p>
     *
     * @param retentionDays 保留天数；小于 0 时按 0 处理
     */
    public void archive(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(retentionDays, 0));
        AuditArchiveWatermark watermark = loadWatermark();
        List<AuditLogRecord> records = queryExpired(threshold, watermark);
        if (records.isEmpty()) {
            log.info("审计日志归档跳过：过期区间无待归档记录, 阈值={}, 水位={}",
                    threshold, watermark == null ? "无" : watermark.getLastCreateTime() + "/" + watermark.getLastId());
            return;
        }
        try {
            byte[] csv = AuditCsvWriter.toCsvBytes(records);
            Map<String, Object> uploaded = archiveClient.upload(
                    csv, AuditCsvWriter.archiveFileName(), exportProperties.getBucket());
            log.info("审计日志归档完成（未删除任何记录）: 行数={}, 阈值={}, objectKey={}",
                    records.size(), threshold, uploaded.get(FIELD_OBJECT_KEY));
            advanceWatermark(watermark, records.get(records.size() - 1));
        } catch (Exception e) {
            log.warn("审计日志归档失败（受控失败，不影响后续清理与调度）: 阈值={}, reason={}",
                    threshold, e.getMessage(), e);
        }
    }

    /**
     * 读取归档水位
     *
     * @return 水位；尚未归档过返回 {@code null}
     */
    private AuditArchiveWatermark loadWatermark() {
        try {
            return watermarkMapper.selectById(WATERMARK_ID);
        } catch (Exception e) {
            log.warn("审计日志归档水位读取失败，本轮按全量过期区间归档: reason={}", e.getMessage());
            return null;
        }
    }

    /**
     * 查询待归档记录：{@code create_time < 阈值} 且严格晚于水位，
     * 按 {@code create_time, id} 升序，最多取上限行以避免单次归档过大
     *
     * @param threshold 过期阈值（不含）
     * @param watermark 归档水位（可空）
     * @return 待归档记录；超上限时已截断为上限行
     */
    private List<AuditLogRecord> queryExpired(LocalDateTime threshold, AuditArchiveWatermark watermark) {
        int maxRows = exportProperties.getMaxRows();
        LambdaQueryWrapper<AuditLogRecord> wrapper = new LambdaQueryWrapper<AuditLogRecord>()
                .lt(AuditLogRecord::getCreateTime, threshold);
        if (watermark != null && watermark.getLastCreateTime() != null) {
            LocalDateTime lastTime = watermark.getLastCreateTime();
            Long lastId = watermark.getLastId() == null ? 0L : watermark.getLastId();
            wrapper.and(w -> w.gt(AuditLogRecord::getCreateTime, lastTime)
                    .or(q -> q.eq(AuditLogRecord::getCreateTime, lastTime)
                            .gt(AuditLogRecord::getId, lastId)));
        }
        wrapper.orderByAsc(AuditLogRecord::getCreateTime)
                .orderByAsc(AuditLogRecord::getId)
                .last("LIMIT " + (maxRows + 1L));
        List<AuditLogRecord> records = auditLogMapper.selectList(wrapper);
        if (records.size() > maxRows) {
            log.warn("审计日志归档行数超上限，本次仅取前 {} 行（原命中 {} 行）", maxRows, records.size());
            return records.subList(0, maxRows);
        }
        return records;
    }

    /**
     * 上传成功后推进归档水位（写失败仅告警，下轮可能重复导出该批）
     *
     * @param current 当前水位（可空）
     * @param last    本轮最后一条已归档记录
     */
    private void advanceWatermark(AuditArchiveWatermark current, AuditLogRecord last) {
        try {
            AuditArchiveWatermark next = new AuditArchiveWatermark();
            next.setId(WATERMARK_ID);
            next.setLastCreateTime(last.getCreateTime());
            next.setLastId(last.getId());
            next.setUpdateTime(LocalDateTime.now());
            if (current == null) {
                watermarkMapper.insert(next);
            } else {
                watermarkMapper.updateById(next);
            }
        } catch (Exception e) {
            log.warn("审计日志归档水位推进失败（下轮可能重复导出本批）: reason={}", e.getMessage(), e);
        }
    }
}

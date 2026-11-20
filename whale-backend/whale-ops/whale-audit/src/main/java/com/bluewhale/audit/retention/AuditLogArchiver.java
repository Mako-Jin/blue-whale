package com.bluewhale.audit.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.audit.retention.entity.AuditArchiveWatermark;
import com.bluewhale.audit.retention.mapper.AuditArchiveWatermarkMapper;
import com.bluewhale.audit.support.AuditArchiveClient;
import com.bluewhale.audit.support.AuditArchiveDigest;
import com.bluewhale.audit.support.AuditCsvWriter;
import com.bluewhale.audit.support.AuditMetrics;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.lock.annotation.DistributedLock;
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
 * <p>每批在 CSV 之外再上传一份 {@code .sha256} 校验清单（内容哈希、跨批链值、区间、行数、
 * 可选 HMAC 签名），并把同一组凭证写进 {@code audit_archive_watermark}——
 * 归档物因此「自证未被改、序列未被插入或删除」（见 {@link AuditArchiveDigest}）。</p>
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

    /** 单批最小行数（配置非法时兜底，避免 0/负数导致死循环或不推进） */
    private static final int MIN_BATCH_ROWS = 1;

    private final AuditLogMapper auditLogMapper;

    private final AuditArchiveWatermarkMapper watermarkMapper;

    private final AuditArchiveClient archiveClient;

    private final AuditExportProperties exportProperties;

    private final AuditMetrics auditMetrics;

    /**
     * 归档 {@code audit_log} 中早于保留期的记录：导出 CSV 并上传，绝不删除
     *
     * <p>按 {@code audit_archive_watermark} 水位增量续查，<b>分页流式</b>处理：
     * 每批最多 {@code whale.audit.export.batch-rows} 行，独立生成 CSV、独立上传、上传成功后
     * 立即推进水位，单批失败只回退该批且本轮终止，避免一次性加载 {@code max-rows} 行造成内存峰值。</p>
     *
     * <p>多实例部署时通过 Redisson 分布式锁互斥：抢锁失败的实例直接跳过本轮，
     * 避免同一水位区间被重复导出上传；锁租期与调度超时（1800s）对齐，不启用看门狗续期。</p>
     *
     * @param retentionDays 保留天数；小于 0 时按 0 处理
     */
    @DistributedLock(biz = "audit:archive", waitTime = 0, leaseTime = 1800)
    public void archive(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(retentionDays, 0));
        int batchRows = Math.max(exportProperties.getBatchRows(), MIN_BATCH_ROWS);
        int maxRows = Math.max(exportProperties.getMaxRows(), batchRows);
        AuditArchiveWatermark watermark = loadWatermark();
        int archivedRows = 0;
        int batchNo = 0;
        while (archivedRows < maxRows) {
            int limit = Math.min(batchRows, maxRows - archivedRows);
            List<AuditLogRecord> records = queryExpired(threshold, watermark, limit);
            if (records.isEmpty()) {
                break;
            }
            batchNo++;
            try {
                byte[] csv = AuditCsvWriter.toCsvBytes(records);
                String csvFileName = AuditCsvWriter.archiveFileName();
                String batchSha256 = AuditArchiveDigest.sha256Hex(csv);
                String chainSha256 = AuditArchiveDigest.chainSha256(
                        watermark == null ? null : watermark.getChainSha256(), batchSha256);
                Map<String, Object> uploaded = archiveClient.upload(
                        csv, csvFileName, exportProperties.getBucket());
                String objectKey = stringOf(uploaded.get(FIELD_OBJECT_KEY));
                // 清单紧随 CSV 上传：CSV 上的内容哈希 + 跨批链值 + 可选 HMAC 签名，任何一步失败都按本批失败处理
                String manifestFileName = AuditArchiveDigest.manifestFileName(csvFileName);
                Map<String, Object> manifestUploaded = archiveClient.upload(
                        AuditArchiveDigest.manifestBytes(objectKey, csvFileName, batchSha256, chainSha256,
                                records, exportProperties.getSignKey()),
                        manifestFileName, exportProperties.getBucket());
                watermark = advanceWatermark(watermark, records, batchSha256, chainSha256,
                        objectKey, stringOf(manifestUploaded.get(FIELD_OBJECT_KEY)));
                archivedRows += records.size();
                auditMetrics.recordArchiveBatch(true, records.size());
                log.info("审计日志归档批次完成（未删除任何记录）: 批次={}, 本批行数={}, 累计行数={}, objectKey={}, sha256={}, chain={}",
                        batchNo, records.size(), archivedRows, objectKey, batchSha256, chainSha256);
            } catch (Exception e) {
                auditMetrics.recordArchiveBatch(false, 0);
                log.warn("审计日志归档失败（受控失败，终止本轮，已上传批次的水位不回退）: 批次={}, 阈值={}, reason={}",
                        batchNo, threshold, e.getMessage(), e);
                return;
            }
            if (records.size() < limit) {
                break;
            }
        }
        if (archivedRows == 0) {
            log.info("审计日志归档跳过：过期区间无待归档记录, 阈值={}, 水位={}",
                    threshold, watermark == null ? "无" : watermark.getLastCreateTime() + "/" + watermark.getLastId());
            return;
        }
        log.info("审计日志归档结束: 批次={}, 总行数={}, 阈值={}", batchNo, archivedRows, threshold);
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
     * 按 {@code create_time, id} 升序，最多取本批行数以避免单批归档过大
     *
     * @param threshold 过期阈值（不含）
     * @param watermark 归档水位（可空）
     * @param limit     本批最大行数（正数）
     * @return 待归档记录
     */
    private List<AuditLogRecord> queryExpired(LocalDateTime threshold, AuditArchiveWatermark watermark, int limit) {
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
                .last("LIMIT " + limit);
        return auditLogMapper.selectList(wrapper);
    }

    /**
     * 上传成功后推进归档水位（写失败仅告警，下轮可能重复导出该批）
     *
     * <p>除位置水位外，同时落本批完整性凭证：CSV 的 SHA-256、链式哈希、CSV 与清单的 objectKey、
     * 本批行数与累计行数。凭证与位置同一条记录写入——避免出现「哈希落库了但水位没推进」
     * 或反之导致链值与实际归档内容错位。</p>
     *
     * @param current     当前水位（可空）
     * @param records     本批已归档记录（非空）
     * @param batchSha256 本批 CSV 的 SHA-256
     * @param chainSha256 本批链值
     * @param objectKey   本批 CSV 对象键（可为 {@code null}）
     * @param manifestKey 本批清单对象键（可为 {@code null}）
     * @return 下一批续查使用的水位（即使落库失败也返回内存水位，避免同轮重复导出本批）
     */
    private AuditArchiveWatermark advanceWatermark(AuditArchiveWatermark current, List<AuditLogRecord> records,
                                                   String batchSha256, String chainSha256,
                                                   String objectKey, String manifestKey) {
        AuditLogRecord last = records.get(records.size() - 1);
        long previousTotal = current == null || current.getArchivedTotal() == null
                ? 0L : current.getArchivedTotal();
        AuditArchiveWatermark next = new AuditArchiveWatermark();
        next.setId(WATERMARK_ID);
        next.setLastCreateTime(last.getCreateTime());
        next.setLastId(last.getId());
        next.setUpdateTime(LocalDateTime.now());
        next.setLastBatchSha256(batchSha256);
        next.setChainSha256(chainSha256);
        next.setLastObjectKey(objectKey);
        next.setLastManifestKey(manifestKey);
        next.setLastBatchRows(records.size());
        next.setArchivedTotal(previousTotal + records.size());
        try {
            if (current == null) {
                watermarkMapper.insert(next);
            } else {
                watermarkMapper.updateById(next);
            }
        } catch (Exception e) {
            log.warn("审计日志归档水位推进失败（下轮可能重复导出本批）: reason={}", e.getMessage(), e);
        }
        return next;
    }

    /**
     * 对象存储返回值的空值安全字符串化
     *
     * @param value 原始值
     * @return 字符串；{@code null} 返回 {@code null}（列允许为空，不写 "null" 字面量）
     */
    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

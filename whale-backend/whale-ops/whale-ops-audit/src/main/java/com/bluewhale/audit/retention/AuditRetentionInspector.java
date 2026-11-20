package com.bluewhale.audit.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.audit.support.AuditMetrics;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 审计日志留存期巡检（等保 2.0 8.1.4.3 a)：审计记录留存不少于 6 个月）。
 *
 * <p>归档任务只导出不删除，理论上 {@code audit_log} 会一直增长，留存天然达标；
 * 但真出问题的时候恰恰是「有人手工清过表」或「库被重建」——这类事故不会报错，
 * 只能靠「最早一条记录是多久以前」这个客观量来发现。因此每轮保留期任务顺带算一次
 * {@code audit_log} 最早记录的距今天数：</p>
 * <ul>
 *   <li>指标 {@code audit.log.oldest.days}（Gauge）：供 Prometheus 告警
 *       「低于 180 即留存不足 6 个月」；</li>
 *   <li>低于阈值时打 WARN，日志本身即可作为巡检证据；</li>
 *   <li>表为空时记 {@code -1}（无数据，不算违规也不算达标），避免把新环境误判成事故。</li>
 * </ul>
 *
 * <p>只读：一条 {@code ORDER BY create_time LIMIT 1} 的查询，命中 {@code create_time} 索引，
 * 不扫描全表。异常一律受控收敛为「本次未取到」，不打断保留期调度。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionInspector {

    /** 阈值非法（≤0）时使用的兜底天数：等保三级的 6 个月，按 180 天计 */
    private static final int FALLBACK_MINIMUM_DAYS = 180;

    /** 无数据时的指标值（区分「表为空」与「留存不足」，告警规则不得把 -1 当违规） */
    public static final long UNKNOWN_DAYS = -1L;

    private final AuditLogMapper auditLogMapper;

    private final AuditMetrics auditMetrics;

    /**
     * 巡检留存期：计算最早审计记录距今天数，写指标并在低于阈值时告警
     *
     * @param minimumDays 要求的最少留存天数（≤0 时按 180 处理）
     * @return 最早记录距今天数；{@code audit_log} 为空或查询失败时返回 {@link #UNKNOWN_DAYS}
     */
    public long inspect(int minimumDays) {
        int threshold = minimumDays > 0 ? minimumDays : FALLBACK_MINIMUM_DAYS;
        LocalDateTime oldest = queryOldestCreateTime();
        if (oldest == null) {
            auditMetrics.recordAuditOldestDays(UNKNOWN_DAYS);
            log.info("审计留存巡检：audit_log 无有效记录（最早时间为空），本次不判定留存是否达标");
            return UNKNOWN_DAYS;
        }
        long days = ChronoUnit.DAYS.between(oldest, LocalDateTime.now());
        auditMetrics.recordAuditOldestDays(days);
        if (days < threshold) {
            log.warn("审计留存不足: audit_log 最早记录={}, 距今仅 {} 天，要求不少于 {} 天"
                            + "（等保 2.0 8.1.4.3 a：审计记录留存不少于 6 个月），请核查是否发生过非预期清理或库重建",
                    oldest, days, threshold);
        } else {
            log.info("审计留存达标: audit_log 最早记录={}, 距今 {} 天，要求不少于 {} 天", oldest, days, threshold);
        }
        return days;
    }

    /**
     * 查询 {@code audit_log} 最早一条记录的 {@code create_time}
     *
     * <p>刻意不写 {@code .select(AuditLogRecord::getCreateTime)}：Lambda 列名是<b>立即</b>解析的，
     * 在无 Spring 上下文的单测里会因 MyBatis-Plus 表信息未初始化而抛
     * {@code can not find lambda cache}；而 {@code orderByAsc} 是延迟解析，不触发该问题。
     * 只取一行，多读几列不影响性能。</p>
     *
     * @return 最早创建时间；表为空或查询异常时返回 {@code null}
     */
    private LocalDateTime queryOldestCreateTime() {
        try {
            List<AuditLogRecord> rows = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogRecord>()
                    .orderByAsc(AuditLogRecord::getCreateTime)
                    .last("LIMIT 1"));
            return rows == null || rows.isEmpty() ? null : rows.get(0).getCreateTime();
        } catch (Exception e) {
            log.warn("审计留存巡检查询失败（本次不判定）: reason={}", e.getMessage(), e);
            return null;
        }
    }
}

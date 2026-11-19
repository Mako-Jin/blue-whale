package com.bluewhale.audit.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.audit.config.RetentionProperties;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.jobschedule.ScheduledJob;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 归档积压指标（{@code audit.archive.pending}）。
 *
 * <p>Gauge 的值由定时任务刷新（默认每 5 分钟一次 count 查询，走 {@code idx_create_time}），
 * 不在 Prometheus 每次抓取时直接查库，避免 15s 一次的 count 拖慢数据库。
 * 积压持续增长说明单轮归档追不上写入速度，应调大 {@code whale.audit.export.max-rows}
 * 或缩短触发间隔。</p>
 *
 * <p>仅在归档开启（{@code whale.audit.retention.archive-enabled=true}，默认 true）时装配。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "whale.audit.retention", name = "archive-enabled",
        havingValue = "true", matchIfMissing = true)
public class AuditArchiveBacklogGauge {

    /** 尚未完成首轮刷新时的占位值（Prometheus 中表现为 -1，可据此判断指标未初始化） */
    private static final long UNINITIALIZED = -1L;

    private final AuditLogMapper auditLogMapper;

    private final RetentionProperties retentionProperties;

    private final AtomicLong pending = new AtomicLong(UNINITIALIZED);

    /**
     * 注册积压 Gauge 并保存查询依赖
     *
     * @param auditLogMapper      审计日志 Mapper
     * @param retentionProperties 保留期配置（提供过期阈值天数）
     * @param meterRegistry       指标注册表
     */
    public AuditArchiveBacklogGauge(AuditLogMapper auditLogMapper,
                                    RetentionProperties retentionProperties,
                                    MeterRegistry meterRegistry) {
        this.auditLogMapper = auditLogMapper;
        this.retentionProperties = retentionProperties;
        Gauge.builder("audit.archive.pending", pending, AtomicLong::doubleValue)
                .description("早于保留期阈值但尚未归档的 audit_log 行数（每 5 分钟刷新）")
                .register(meterRegistry);
    }

    /**
     * 刷新归档积压值
     *
     * @return 执行摘要（当前积压行数）
     */
    @ScheduledJob(code = "audit.archive.backlog",
            cron = "${whale.audit.retention.backlog-cron:0 */5 * * * ?}",
            timeoutSeconds = 120)
    public String refresh() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusDays(Math.max(retentionProperties.getDays(), 0));
        try {
            Long count = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLogRecord>()
                    .lt(AuditLogRecord::getCreateTime, threshold));
            long value = count == null ? 0L : count;
            pending.set(value);
            log.debug("审计归档积压刷新: threshold={}, pending={}", threshold, value);
            return "pending=" + value;
        } catch (Exception e) {
            log.warn("审计归档积压刷新失败（沿用上次值）: reason={}", e.getMessage(), e);
            return "failed";
        }
    }
}

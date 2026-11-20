package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.RetentionProperties;
import com.bluewhale.common.jobschedule.ScheduledJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 日志保留期定时任务调度器（REQ-SEC-006 / AUTH-09）。
 *
 * <p>{@code whale.audit.retention.enabled=true} 时按 {@code whale.audit.retention.cron}
 * 触发 {@link LogRetentionCleaner} 清理 4 张日志表；默认关闭（不清理，避免误删）。</p>
 *
 * <p>{@code whale.audit.retention.archive-enabled=true}（<b>默认开启</b>）时触发
 * {@link AuditLogArchiver} 导出 {@code audit_log} 过期记录并归档（AUTH-09）。
 * 归档<b>只导出上传、绝不删除 {@code audit_log}</b>；清理器仍只覆盖 4 张日志表。</p>
 *
 * <p>两个开关相互独立：任一开启即装配本调度器，各自按开关决定是否执行。
 * 因此默认配置下仅执行归档、不执行日志清理。</p>
 *
 * <p><b>两者作用域不相交</b>（清理只删 4 张日志表、归档只读 {@code audit_log}），
 * 执行顺序不影响正确性，切勿为「先清理后归档」之类的假设调整顺序。</p>
 *
 * <p>两项之后固定执行一次 {@link AuditRetentionInspector}：只读、不删，负责把「{@code audit_log}
 * 最早记录距今多少天」暴露成指标 {@code audit.log.oldest.days}，供留存不足 6 个月的告警使用。</p>
 *
 * <p>整个方法包在 {@link AuditJobHeartbeatRecorder} 里：开始写一次、成功/失败各写一次，
 * 让 {@code audit.job.stale.days} 能回答「这个作业多久没成功跑过了」。作业被停用、
 * cron 写错、容器反复重启导致从未调度——这三种情况不会让任何业务报错，
 * 只能靠心跳发现（等保 2.0 8.1.4.3 b）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${whale.audit.retention.enabled:false} or ${whale.audit.retention.archive-enabled:true}")
public class LogRetentionScheduler {

    /** 作业编码：与 {@link AuditJobStaleGauge#JOB_LOG_RETENTION} 一致，改动需同步指标标签 */
    private static final String JOB_CODE = AuditJobStaleGauge.JOB_LOG_RETENTION;

    private final LogRetentionCleaner cleaner;

    private final AuditLogArchiver archiver;

    private final AuditRetentionInspector retentionInspector;

    private final AuditJobHeartbeatRecorder heartbeatRecorder;

    private final RetentionProperties properties;

    /**
     * 按配置 cron 触发：清理开关开启时清理 4 张日志表，归档开关开启时导出 {@code audit_log} 过期记录，
     * 最后做一次留存期巡检（指标 + 告警，只读不删）
     *
     * <p>巡检<b>不受两个开关控制</b>：它不删任何数据，只是把「最早记录距今多久」暴露成指标。
     * 若挂在开关下，关掉清理的合规环境反而失去留存证据，与等保 8.1.4.3 a 的取证意图相反。</p>
     *
     * <p>异常原样抛出（调度中心据此记失败），但抛出前先把失败写进心跳——否则「一直失败」
     * 和「一直没跑」在监控上同样是「指标不更新」。</p>
     *
     * @return 执行摘要（实际生效的开关、保留天数、最早审计记录距今天数）
     */
    @ScheduledJob(code = "audit.log.retention",
            cron = "${whale.audit.retention.cron}",
            timeoutSeconds = 1800)
    public String cleanExpiredLogs() {
        log.info("触发日志保留期任务: 清理={}, 保留天数={}, 归档={}",
                properties.isEnabled(), properties.getDays(), properties.isArchiveEnabled());
        heartbeatRecorder.recordStart(JOB_CODE);
        try {
            if (properties.isEnabled()) {
                cleaner.cleanExpired(properties.getDays());
            }
            if (properties.isArchiveEnabled()) {
                archiver.archive(properties.getDays());
            }
            long oldestDays = retentionInspector.inspect(properties.getMinimumDays());
            String summary = "clean=" + properties.isEnabled() + ", archive=" + properties.isArchiveEnabled()
                    + ", days=" + properties.getDays() + ", oldestAuditDays=" + oldestDays;
            heartbeatRecorder.recordSuccess(JOB_CODE, summary);
            return summary;
        } catch (RuntimeException e) {
            heartbeatRecorder.recordFailure(JOB_CODE, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }
}

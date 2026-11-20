package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.RetentionProperties;
import com.bluewhale.audit.retention.mapper.AuditJobHeartbeatMapper;
import com.bluewhale.audit.retention.po.AuditJobHeartbeatPo;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审计作业陈旧度指标（{@code audit.job.stale.days}），关闭「作业停了没人知道」这个盲区。
 *
 * <p>留存不达标可以靠 {@code audit.log.oldest.days} 发现，但「调度压根没触发」在监控上
 * 表现为<b>指标不更新</b>——和健康状态完全同形。本类从 {@code audit_job_heartbeat}
 * 读 {@code last_success_at}，暴露「距上次成功多少天」，把「没跑」变成可告警的正数：</p>
 *
 * <ul>
 *   <li>指标：{@code audit.job.stale.days{job="audit.log.retention"}}；</li>
 *   <li>{@code -1} 表示从未成功过（新环境或一直失败），告警规则须排除该值，改用
 *       「连续 2 天 still -1」或人工确认；</li>
 *   <li>告警口径：{@code > whale.audit.retention.stale-alert-days}（默认 2）；
 *       同时打 WARN 作为巡检证据。</li>
 * </ul>
 *
 * <p><b>为什么在抓取时查库，而 {@code AuditArchiveBacklogGauge} 不这么干</b>：那边是
 * {@code count(*)} 全表扫描，15s 一次会拖库；这里是主键单行命中，代价可忽略。另加 30 秒
 * 本地缓存，把抓取频率与查询次数解耦。Gauge 回调内部全量 catch，绝不把异常抛给
 * {@code /actuator/prometheus}（抛出去会让整个抓取 500，把监控自己变成故障源）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AuditJobStaleGauge {

    /** 日志保留期 / 归档 / 留存巡检作业编码（{@link LogRetentionScheduler} 的 {@code @ScheduledJob}） */
    public static final String JOB_LOG_RETENTION = "audit.log.retention";

    /**
     * 受监控的作业编码。
     *
     * <p>新增作业心跳时在这里加一行，构造期会为每个编码注册一个带 {@code job} 标签的 Gauge。
     * 不做「扫表发现新作业再补注册」：动态注册与去重容易出错，而这条清单本来就该在代码里显式可见。</p>
     */
    private static final List<String> MONITORED_JOBS = List.of(JOB_LOG_RETENTION);

    /** 从未成功过（无记录 / 查询失败 / 无成功时间）时的指标值 */
    public static final long NEVER_SUCCEEDED = -1L;

    /** 本地缓存有效期：抓取间隔通常 15s，30s 可省掉一半查询且不影响告警时效 */
    private static final long CACHE_TTL_MILLIS = 30_000L;

    /** 阈值非法（≤0）时的兜底天数 */
    private static final int FALLBACK_STALE_DAYS = 2;

    private final AuditJobHeartbeatMapper heartbeatMapper;

    private final RetentionProperties retentionProperties;

    /** 每个作业编码的快照（值 + 取数时刻 + 是否已告警），用于缓存与告警去重 */
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * 注册各作业的陈旧度 Gauge
     *
     * @param heartbeatMapper     心跳 Mapper
     * @param retentionProperties 保留期配置（提供陈旧告警阈值）
     * @param meterRegistry       指标注册表
     */
    public AuditJobStaleGauge(AuditJobHeartbeatMapper heartbeatMapper,
                              RetentionProperties retentionProperties,
                              MeterRegistry meterRegistry) {
        this.heartbeatMapper = heartbeatMapper;
        this.retentionProperties = retentionProperties;
        for (String jobCode : MONITORED_JOBS) {
            // 用「持有本对象引用 + 函数」的重载，而不是 Supplier lambda：
            // Gauge 对 lambda 持弱引用，被回收后指标会静默变成 NaN
            Gauge.builder("audit.job.stale.days", this, gauge -> gauge.staleDays(jobCode))
                    .tag("job", jobCode)
                    .description("距该作业上次成功执行的天数，超过阈值表示调度未跑或一直失败（-1=从未成功）")
                    .register(meterRegistry);
        }
    }

    /**
     * 取指定作业的陈旧天数（带 30 秒缓存）
     *
     * @param jobCode 任务编码
     * @return 距上次成功的天数；从未成功或查询失败返回 {@link #NEVER_SUCCEEDED}
     */
    public long staleDays(String jobCode) {
        long now = System.currentTimeMillis();
        Snapshot snapshot = snapshots.get(jobCode);
        if (snapshot != null && now - snapshot.fetchedAtMillis() < CACHE_TTL_MILLIS) {
            return snapshot.value();
        }
        long value = queryStaleDays(jobCode);
        boolean alerted = snapshot != null && snapshot.alerted();
        refreshSnapshot(jobCode, value, now, alerted);
        return value;
    }

    /**
     * 清缓存（供单测与手工排障使用：想让下一次抓取立即重新查库）
     *
     * @param jobCode 任务编码
     */
    public void invalidate(String jobCode) {
        snapshots.remove(jobCode);
    }

    /**
     * 查库算陈旧天数
     *
     * @param jobCode 任务编码
     * @return 距上次成功的天数；无记录 / 无成功时间 / 查询异常返回 {@link #NEVER_SUCCEEDED}
     */
    private long queryStaleDays(String jobCode) {
        try {
            AuditJobHeartbeatPo heartbeat = heartbeatMapper.selectById(jobCode);
            if (heartbeat == null || heartbeat.getLastSuccessAt() == null) {
                return NEVER_SUCCEEDED;
            }
            long days = ChronoUnit.DAYS.between(heartbeat.getLastSuccessAt(), LocalDateTime.now());
            // 时钟回拨可能算出负数：当作刚成功处理，不制造假告警
            return Math.max(days, 0L);
        } catch (Exception e) {
            log.warn("审计作业心跳查询失败（本次按未知处理）: job={}, reason={}", jobCode, e.getMessage());
            return NEVER_SUCCEEDED;
        }
    }

    /**
     * 写缓存并按阈值告警（同一段告警只打一次，避免抓取频率放大日志）
     *
     * @param jobCode 任务编码
     * @param value   陈旧天数
     * @param now     取数时刻（毫秒）
     * @param alerted 此前是否已告警
     */
    private void refreshSnapshot(String jobCode, long value, long now, boolean alerted) {
        int threshold = retentionProperties.getStaleAlertDays() > 0
                ? retentionProperties.getStaleAlertDays() : FALLBACK_STALE_DAYS;
        boolean stale = value > threshold;
        if (stale && !alerted) {
            log.warn("审计作业已 {} 天未成功执行（阈值 {} 天）: job={}，"
                            + "请核查调度是否被停用、cron 是否写错、最近一次执行是否持续失败"
                            + "（等保 2.0 8.1.4.3 b：须保证审计记录持续产生并留存）",
                    value, threshold, jobCode);
        } else if (!stale && alerted) {
            log.info("审计作业已恢复: job={}, 距上次成功 {} 天（阈值 {} 天）", jobCode, value, threshold);
        }
        snapshots.put(jobCode, new Snapshot(value, now, stale));
    }

    /**
     * 指标快照
     *
     * @param value           陈旧天数
     * @param fetchedAtMillis 取数时刻（毫秒）
     * @param alerted         当前是否处于已告警状态（用于告警去重与恢复通知）
     */
    private record Snapshot(long value, long fetchedAtMillis, boolean alerted) {
    }
}

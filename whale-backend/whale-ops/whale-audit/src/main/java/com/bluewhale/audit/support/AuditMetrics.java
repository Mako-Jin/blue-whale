package com.bluewhale.audit.support;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 审计关键路径指标（Micrometer / Actuator Prometheus）。
 *
 * <p>覆盖三条对外可见的链路，指标名以 {@code audit.} 为前缀，直接暴露在
 * {@code /actuator/prometheus}，供 Prometheus 告警与 Grafana 面板使用：</p>
 * <ul>
 *   <li>{@code audit.export.duration}（Tag {@code result=success|failure}）：导出任务耗时；</li>
 *   <li>{@code audit.export.rows}：导出成功时的行数分布；</li>
 *   <li>{@code audit.archive.batches}（Tag {@code result=success|failure}）：归档批次计数；</li>
 *   <li>{@code audit.archive.rows}：归档成功批次的行数分布；</li>
 *   <li>{@code audit.mq.consume}（Tag {@code result=success|duplicate|rejected}）：MQ 消费结果计数。</li>
 * </ul>
 *
 * <p>指标写入只做内存累加，不涉及 IO；异常已被各调用点受控收敛，本类不再吞异常。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AuditMetrics {

    /** 结果标签名 */
    private static final String TAG_RESULT = "result";

    /** 成功结果值 */
    public static final String RESULT_SUCCESS = "success";

    /** 失败结果值 */
    public static final String RESULT_FAILURE = "failure";

    /** 重复消息（幂等跳过）结果值 */
    public static final String RESULT_DUPLICATE = "duplicate";

    /** 毒消息（拒绝入队）结果值 */
    public static final String RESULT_REJECTED = "rejected";

    private final MeterRegistry meterRegistry;

    /**
     * 最近一次留存期巡检得到的「最早审计记录距今天数」，{@code -1} 表示尚无结果。
     *
     * <p>Gauge 只持引用不持值，所以必须由本类长期持有该 {@link AtomicLong} 并在巡检时更新，
     * 否则每次读数都取自一个临时对象，指标会永远停在注册时的快照。</p>
     */
    private final AtomicLong oldestAuditDays = new AtomicLong(-1L);

    /**
     * 记录一次导出任务结果
     *
     * @param success       是否成功
     * @param rows          导出行数（失败时传 0）
     * @param elapsedNanos  耗时（纳秒）
     */
    public void recordExport(boolean success, int rows, long elapsedNanos) {
        Timer.builder("audit.export.duration")
                .tag(TAG_RESULT, success ? RESULT_SUCCESS : RESULT_FAILURE)
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
        if (success) {
            DistributionSummary.builder("audit.export.rows").register(meterRegistry).record(rows);
        }
    }

    /**
     * 记录一个归档批次结果
     *
     * @param success 该批是否上传成功
     * @param rows    该批行数（失败时传 0）
     */
    public void recordArchiveBatch(boolean success, int rows) {
        meterRegistry.counter("audit.archive.batches",
                TAG_RESULT, success ? RESULT_SUCCESS : RESULT_FAILURE).increment();
        if (success && rows > 0) {
            DistributionSummary.builder("audit.archive.rows").register(meterRegistry).record(rows);
        }
    }

    /**
     * 记录一次 MQ 消费结果
     *
     * @param result {@link #RESULT_SUCCESS} / {@link #RESULT_DUPLICATE} / {@link #RESULT_REJECTED}
     */
    public void recordMqConsume(String result) {
        meterRegistry.counter("audit.mq.consume", TAG_RESULT, result).increment();
    }

    /**
     * 记录 {@code audit_log} 最早记录距今天数（留存期巡检结果）
     *
     * <p>以 Gauge 暴露，告警口径「{@code audit.log.oldest.days < 180}」即留存不足 6 个月；
     * 值为 {@code -1} 表示表为空或查询失败，属「无数据」，告警规则须排除该值。</p>
     *
     * @param days 距今天数；{@code -1} 表示未知
     */
    public void recordAuditOldestDays(long days) {
        oldestAuditDays.set(days);
        // 同一 name 重复 register 会复用已存在的 meter，Gauge 持有 AtomicLong 引用，后续只更新值
        Gauge.builder("audit.log.oldest.days", oldestAuditDays, AtomicLong::doubleValue)
                .description("audit_log 最早一条记录距今天数，低于 180 表示留存不足 6 个月（-1=无数据）")
                .register(meterRegistry);
    }
}

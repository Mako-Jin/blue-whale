package com.bluewhale.audit.support;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

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
}

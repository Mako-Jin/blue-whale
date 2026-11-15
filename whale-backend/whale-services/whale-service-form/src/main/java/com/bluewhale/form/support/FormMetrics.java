package com.bluewhale.form.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 表单业务指标。
 *
 * <p>只登记「HTTP 指标看不出来」的业务动作：发布结果与耗时、DDL 审计失败、
 * 导出行数、筛选查询耗时、双写不一致条数。指标在构造时一次性注册并缓存句柄，
 * 避免每次上报都按名称与标签查表；Micrometer 的计量器本身线程安全，可并发调用。</p>
 *
 * <p>Prometheus 指标名：{@code form_publish_total}（{@code result=success|failure}）、
 * {@code form_publish_duration_seconds}、{@code form_publish_failure_total}、
 * {@code form_ddl_audit_failure_total}、{@code form_export_rows}、
 * {@code form_filter_query_duration} 与 {@code form_data_dual_write_mismatch_total}。</p>
 *
 * @author BlueWhale
 */
@Component
public class FormMetrics {

    /** 发布结果标签名 */
    private static final String TAG_RESULT = "result";

    /** 发布结果标签值：成功 */
    private static final String RESULT_SUCCESS = "success";

    /** 发布结果标签值：失败 */
    private static final String RESULT_FAILURE = "failure";

    /** 发布次数（按结果分标签） */
    private final Counter publishSuccessCounter;

    /** 发布次数（按结果分标签） */
    private final Counter publishFailureCounter;

    /** 发布耗时 */
    private final Timer publishTimer;

    /** 发布失败次数（专供告警规则，不受标签影响） */
    private final Counter publishFailedAlertCounter;

    /** DDL 审计记录失败次数 */
    private final Counter ddlAuditFailureCounter;

    /** 导出行数分布 */
    private final DistributionSummary exportRowsSummary;

    /** 筛选查询耗时 */
    private final Timer filterQueryTimer;

    /** 双写不一致条数 */
    private final Counter dualWriteMismatchCounter;

    /**
     * 注册并缓存表单业务指标。
     *
     * @param registry Micrometer 注册中心
     */
    public FormMetrics(MeterRegistry registry) {
        this.publishSuccessCounter = registry.counter("form_publish_total", TAG_RESULT, RESULT_SUCCESS);
        this.publishFailureCounter = registry.counter("form_publish_total", TAG_RESULT, RESULT_FAILURE);
        this.publishTimer = registry.timer("form_publish_duration_seconds");
        this.publishFailedAlertCounter = registry.counter("form_publish_failure_total");
        this.ddlAuditFailureCounter = registry.counter("form_ddl_audit_failure_total");
        this.exportRowsSummary = registry.summary("form_export_rows");
        this.filterQueryTimer = registry.timer("form_filter_query_duration");
        this.dualWriteMismatchCounter = registry.counter("form_data_dual_write_mismatch_total");
    }

    /**
     * 记录一次发布成功：{@code result=success} 计数 +1，并记录发布耗时。
     *
     * @param durationNanos 发布耗时（纳秒）
     */
    public void publishSucceeded(long durationNanos) {
        publishSuccessCounter.increment();
        publishTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录一次发布失败：{@code result=failure} 计数与发布失败告警计数各 +1。
     */
    public void publishFailed() {
        publishFailureCounter.increment();
        publishFailedAlertCounter.increment();
    }

    /**
     * 记录一次 DDL 审计记录失败。
     */
    public void ddlAuditFailed() {
        ddlAuditFailureCounter.increment();
    }

    /**
     * 记录一次导出的数据行数。
     *
     * @param rows 导出行数
     */
    public void exportRows(int rows) {
        exportRowsSummary.record(rows);
    }

    /**
     * 记录一次筛选查询耗时。
     *
     * @param durationNanos 查询耗时（纳秒）
     */
    public void filterQuery(long durationNanos) {
        filterQueryTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录双写不一致条数（{@code count<=0} 时忽略，避免空转产生噪声）。
     *
     * @param count 不一致条数
     */
    public void dualWriteMismatch(int count) {
        if (count <= 0) {
            return;
        }
        dualWriteMismatchCounter.increment(count);
    }
}

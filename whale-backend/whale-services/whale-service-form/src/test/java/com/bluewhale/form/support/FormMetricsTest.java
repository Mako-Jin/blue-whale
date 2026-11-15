package com.bluewhale.form.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link FormMetrics} 业务指标登记与上报单元测试。
 *
 * @author BlueWhale
 */
class FormMetricsTest {

    /** 测试用注册中心 */
    private SimpleMeterRegistry registry;

    /** 被测指标组件 */
    private FormMetrics formMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        formMetrics = new FormMetrics(registry);
    }

    /**
     * 读取指定计数器当前值（不存在时返回 0）
     *
     * @param name 指标名
     * @param tags 标签（键值交替）
     * @return 计数
     */
    private double counter(String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0D : counter.count();
    }

    @Test
    @DisplayName("publishSucceeded：success 计数 +1 且记录一次发布耗时")
    void shouldRecordPublishSuccess() {
        formMetrics.publishSucceeded(2_000_000L);

        assertEquals(1D, counter("form_publish_total", "result", "success"));
        Timer timer = registry.find("form_publish_duration_seconds").timer();
        assertNotNull(timer);
        assertEquals(1L, timer.count());
        assertEquals(0.002D, timer.totalTime(TimeUnit.SECONDS), 1e-9);
    }

    @Test
    @DisplayName("publishFailed：failure 计数与发布失败告警计数各 +1")
    void shouldRecordPublishFailure() {
        formMetrics.publishFailed();

        assertEquals(1D, counter("form_publish_total", "result", "failure"));
        assertEquals(1D, counter("form_publish_failure_total"));
    }

    @Test
    @DisplayName("dualWriteMismatch：正数累加，非正数忽略")
    void shouldRecordDualWriteMismatch() {
        formMetrics.dualWriteMismatch(3);

        assertEquals(3D, counter("form_data_dual_write_mismatch_total"));

        formMetrics.dualWriteMismatch(0);
        formMetrics.dualWriteMismatch(-1);

        assertEquals(3D, counter("form_data_dual_write_mismatch_total"));
    }

    @Test
    @DisplayName("ddlAuditFailed：审计失败计数 +1")
    void shouldRecordDdlAuditFailure() {
        formMetrics.ddlAuditFailed();

        assertEquals(1D, counter("form_ddl_audit_failure_total"));
    }

    @Test
    @DisplayName("exportRows：记录一次导出行数分布")
    void shouldRecordExportRows() {
        formMetrics.exportRows(5);

        DistributionSummary summary = registry.find("form_export_rows").summary();
        assertNotNull(summary);
        assertEquals(1L, summary.count());
        assertEquals(5D, summary.totalAmount(), 1e-9);
    }

    @Test
    @DisplayName("filterQuery：记录一次筛选查询耗时")
    void shouldRecordFilterQuery() {
        formMetrics.filterQuery(1_500_000L);

        Timer timer = registry.find("form_filter_query_duration").timer();
        assertNotNull(timer);
        assertEquals(1L, timer.count());
        assertEquals(0.0015D, timer.totalTime(TimeUnit.SECONDS), 1e-9);
    }
}

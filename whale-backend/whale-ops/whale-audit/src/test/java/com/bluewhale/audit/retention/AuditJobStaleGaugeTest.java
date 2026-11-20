package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.RetentionProperties;
import com.bluewhale.audit.retention.entity.AuditJobHeartbeat;
import com.bluewhale.audit.retention.mapper.AuditJobHeartbeatMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 审计作业陈旧度指标单测。
 *
 * <p>这个指标的全部价值在于「作业没跑」能被 Prometheus 看出来，所以断言必须钉住
 * 三个值：{@code -1}（从未成功）、正数天数、{@code 0}（刚成功）。
 * 值错一位，告警要么永不触发，要么天天误报。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuditJobStaleGaugeTest {

    /** 指标名（与 AuditJobStaleGauge 保持一致） */
    private static final String METRIC_NAME = "audit.job.stale.days";

    /** 测试用任务编码 */
    private static final String JOB_CODE = AuditJobStaleGauge.JOB_LOG_RETENTION;

    @Mock
    private AuditJobHeartbeatMapper heartbeatMapper;

    private SimpleMeterRegistry meterRegistry;

    private AuditJobStaleGauge gauge;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        RetentionProperties properties = new RetentionProperties();
        properties.setStaleAlertDays(2);
        gauge = new AuditJobStaleGauge(heartbeatMapper, properties, meterRegistry);
    }

    @Test
    @DisplayName("从未成功（无心跳行）：指标为 -1，不能当成「刚跑过」")
    void shouldReportNeverSucceededWhenNoHeartbeat() {
        doReturn(null).when(heartbeatMapper).selectById(anyString());

        assertEquals(AuditJobStaleGauge.NEVER_SUCCEEDED, gauge.staleDays(JOB_CODE));
        assertEquals(-1.0, metricValue(), 1e-9);
    }

    @Test
    @DisplayName("从未成功（有行但 last_success_at 为空，即一直失败）：指标为 -1")
    void shouldReportNeverSucceededWhenSuccessTimeAbsent() {
        AuditJobHeartbeat heartbeat = new AuditJobHeartbeat();
        heartbeat.setJobCode(JOB_CODE);
        heartbeat.setFailCount(7L);
        doReturn(heartbeat).when(heartbeatMapper).selectById(anyString());

        assertEquals(AuditJobStaleGauge.NEVER_SUCCEEDED, gauge.staleDays(JOB_CODE));
    }

    @Test
    @DisplayName("3 天前成功：指标为 3，超过默认阈值 2")
    void shouldReportStaleDaysFromLastSuccess() {
        doReturn(heartbeatWithSuccess(LocalDateTime.now().minusDays(3)))
                .when(heartbeatMapper).selectById(anyString());

        assertEquals(3L, gauge.staleDays(JOB_CODE));
        assertEquals(3.0, metricValue(), 1e-9);
    }

    @Test
    @DisplayName("刚刚成功：指标为 0，不得因为毫秒差算成 1 天")
    void shouldReportZeroWhenJustSucceeded() {
        doReturn(heartbeatWithSuccess(LocalDateTime.now()))
                .when(heartbeatMapper).selectById(anyString());

        assertEquals(0L, gauge.staleDays(JOB_CODE));
    }

    @Test
    @DisplayName("成功时间在未来（时钟回拨）：按 0 处理，不制造负值假告警")
    void shouldClampFutureSuccessTimeToZero() {
        doReturn(heartbeatWithSuccess(LocalDateTime.now().plusHours(5)))
                .when(heartbeatMapper).selectById(anyString());

        assertEquals(0L, gauge.staleDays(JOB_CODE));
    }

    @Test
    @DisplayName("查询异常：按 -1 处理不抛出，避免 /actuator/prometheus 整个抓取 500")
    void shouldSwallowQueryFailure() {
        doThrow(new IllegalStateException("db down")).when(heartbeatMapper).selectById(anyString());

        assertEquals(AuditJobStaleGauge.NEVER_SUCCEEDED, gauge.staleDays(JOB_CODE));
        assertEquals(-1.0, metricValue(), 1e-9);
    }

    @Test
    @DisplayName("30 秒缓存生效：连续取两次只查一次库")
    void shouldCacheWithinTtl() {
        doReturn(heartbeatWithSuccess(LocalDateTime.now().minusDays(1)))
                .when(heartbeatMapper).selectById(anyString());

        gauge.staleDays(JOB_CODE);
        gauge.staleDays(JOB_CODE);

        verify(heartbeatMapper, times(1)).selectById(JOB_CODE);
    }

    @Test
    @DisplayName("invalidate 后下一次取值重新查库")
    void shouldQueryAgainAfterInvalidate() {
        doReturn(heartbeatWithSuccess(LocalDateTime.now().minusDays(1)))
                .when(heartbeatMapper).selectById(anyString());

        gauge.staleDays(JOB_CODE);
        gauge.invalidate(JOB_CODE);
        gauge.staleDays(JOB_CODE);

        verify(heartbeatMapper, times(2)).selectById(JOB_CODE);
    }

    /**
     * 构造带成功时间的心跳行
     *
     * @param successAt 成功时间
     * @return 心跳实体
     */
    private AuditJobHeartbeat heartbeatWithSuccess(LocalDateTime successAt) {
        AuditJobHeartbeat heartbeat = new AuditJobHeartbeat();
        heartbeat.setJobCode(JOB_CODE);
        heartbeat.setLastSuccessAt(successAt);
        return heartbeat;
    }

    /**
     * 读取注册表里的指标当前值
     *
     * @return 指标值
     */
    private double metricValue() {
        return meterRegistry.get(METRIC_NAME).tag("job", JOB_CODE).gauge().value();
    }
}

package com.bluewhale.audit.retention;

import com.bluewhale.audit.support.AuditMetrics;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * 审计留存期巡检单测（等保 2.0 8.1.4.3 a）。
 *
 * <p>断言重点不是「有没有打印日志」，而是<b>指标值</b>——告警规则读的就是
 * {@code audit.log.oldest.days}，指标错了告警就是摆设。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuditRetentionInspectorTest {

    /** 指标名（与 AuditMetrics 保持一致） */
    private static final String METRIC_OLDEST_DAYS = "audit.log.oldest.days";

    /** 等保要求的留存阈值 */
    private static final int MINIMUM_DAYS = 180;

    @Mock
    private AuditLogMapper auditLogMapper;

    private SimpleMeterRegistry meterRegistry;

    private AuditRetentionInspector inspector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        inspector = new AuditRetentionInspector(auditLogMapper, new AuditMetrics(meterRegistry));
    }

    @Test
    @DisplayName("留存达标：最早记录在 200 天前，指标等于 200 且不判定不足")
    void shouldReportOldestDaysWhenRetentionSufficient() {
        doReturn(List.of(recordWithCreateTime(LocalDateTime.now().minusDays(200))))
                .when(auditLogMapper).selectList(any());

        long days = inspector.inspect(MINIMUM_DAYS);

        assertEquals(200L, days);
        assertEquals(200.0, oldestDaysMetric(), 0.001);
    }

    @Test
    @DisplayName("留存不足：最早记录仅 10 天前，返回值与指标均反映 10（告警据此触发）")
    void shouldReportShortRetention() {
        doReturn(List.of(recordWithCreateTime(LocalDateTime.now().minusDays(10))))
                .when(auditLogMapper).selectList(any());

        long days = inspector.inspect(MINIMUM_DAYS);

        assertEquals(10L, days);
        assertEquals(10.0, oldestDaysMetric(), 0.001);
    }

    @Test
    @DisplayName("表为空：记 -1（无数据），不得当成留存不足，也不抛异常")
    void shouldReportUnknownWhenAuditLogEmpty() {
        doReturn(List.of()).when(auditLogMapper).selectList(any());

        long days = inspector.inspect(MINIMUM_DAYS);

        assertEquals(AuditRetentionInspector.UNKNOWN_DAYS, days);
        assertEquals(-1.0, oldestDaysMetric(), 0.001);
    }

    @Test
    @DisplayName("查询异常：受控收敛为 -1，不打断保留期调度")
    void shouldSwallowQueryFailure() {
        doThrow(new RuntimeException("db down")).when(auditLogMapper).selectList(any());

        long days = assertDoesNotThrow(() -> inspector.inspect(MINIMUM_DAYS));

        assertEquals(AuditRetentionInspector.UNKNOWN_DAYS, days);
        assertEquals(-1.0, oldestDaysMetric(), 0.001);
    }

    @Test
    @DisplayName("阈值非法（≤0）：按 180 兜底，不影响指标实际值")
    void shouldFallbackToDefaultThresholdWhenInvalid() {
        doReturn(List.of(recordWithCreateTime(LocalDateTime.now().minusDays(100))))
                .when(auditLogMapper).selectList(any());

        long days = inspector.inspect(0);

        assertEquals(100L, days);
        assertEquals(100.0, oldestDaysMetric(), 0.001);
    }

    /**
     * 读取指标当前值
     *
     * @return 指标值
     */
    private double oldestDaysMetric() {
        return meterRegistry.get(METRIC_OLDEST_DAYS).gauge().value();
    }

    /**
     * 构造仅带 {@code create_time} 的审计记录（巡检只读该列）
     *
     * @param createTime 创建时间
     * @return 审计记录
     */
    private static AuditLogRecord recordWithCreateTime(LocalDateTime createTime) {
        AuditLogRecord record = new AuditLogRecord();
        record.setId(1L);
        record.setCreateTime(createTime);
        return record;
    }
}

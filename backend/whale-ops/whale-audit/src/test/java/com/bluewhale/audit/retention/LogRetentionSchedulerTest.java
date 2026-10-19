package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.RetentionProperties;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import com.bluewhale.common.log.mapper.BehaviorLogMapper;
import com.bluewhale.common.log.mapper.FrontendLogMapper;
import com.bluewhale.common.log.mapper.OutboundLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 日志保留期清理单测（REQ-SEC-006 / checklist 8.2）：4 表清理、单表失败隔离、调度器按配置天数委派、默认关闭。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class LogRetentionSchedulerTest {

    /** 测试用保留天数 */
    private static final int RETENTION_DAYS = 30;

    /** 配置覆盖后的保留天数 */
    private static final int CONFIGURED_DAYS = 7;

    @Mock
    private ApiLogMapper apiLogMapper;

    @Mock
    private OutboundLogMapper outboundLogMapper;

    @Mock
    private FrontendLogMapper frontendLogMapper;

    @Mock
    private BehaviorLogMapper behaviorLogMapper;

    @Mock
    private LogRetentionCleaner cleaner;

    @Mock
    private AuditLogArchiver archiver;

    private LogRetentionCleaner retentionCleaner;

    @BeforeEach
    void setUp() {
        retentionCleaner = new LogRetentionCleaner(
                apiLogMapper, outboundLogMapper, frontendLogMapper, behaviorLogMapper);
    }

    @Test
    @DisplayName("cleanExpired：对 api_log/outbound_log/frontend_log/behavior_log 四表各执行一次删除")
    void shouldCleanFourTables() {
        when(apiLogMapper.delete(any())).thenReturn(1);
        when(outboundLogMapper.delete(any())).thenReturn(1);
        when(frontendLogMapper.delete(any())).thenReturn(1);
        when(behaviorLogMapper.delete(any())).thenReturn(1);

        retentionCleaner.cleanExpired(RETENTION_DAYS);

        verify(apiLogMapper).delete(any());
        verify(outboundLogMapper).delete(any());
        verify(frontendLogMapper).delete(any());
        verify(behaviorLogMapper).delete(any());
    }

    @Test
    @DisplayName("cleanExpired：单表删除异常仅告警，其余表仍继续清理且不抛异常")
    void shouldIsolateSingleTableFailure() {
        when(apiLogMapper.delete(any())).thenThrow(new RuntimeException("db down"));
        when(outboundLogMapper.delete(any())).thenReturn(0);
        when(frontendLogMapper.delete(any())).thenReturn(0);
        when(behaviorLogMapper.delete(any())).thenReturn(0);

        assertDoesNotThrow(() -> retentionCleaner.cleanExpired(RETENTION_DAYS));

        verify(outboundLogMapper).delete(any());
        verify(frontendLogMapper).delete(any());
        verify(behaviorLogMapper).delete(any());
    }

    @Test
    @DisplayName("cleanExpired：保留天数小于 0 时按 0 处理，仍执行四表清理")
    void shouldTreatNegativeDaysAsZero() {
        when(apiLogMapper.delete(any())).thenReturn(0);
        when(outboundLogMapper.delete(any())).thenReturn(0);
        when(frontendLogMapper.delete(any())).thenReturn(0);
        when(behaviorLogMapper.delete(any())).thenReturn(0);

        assertDoesNotThrow(() -> retentionCleaner.cleanExpired(-1));

        verify(apiLogMapper).delete(any());
    }

    @Test
    @DisplayName("Scheduler：按配置的保留天数委派清理器")
    void shouldDelegateConfiguredDaysToCleaner() {
        RetentionProperties properties = new RetentionProperties();
        properties.setDays(CONFIGURED_DAYS);
        LogRetentionScheduler scheduler = new LogRetentionScheduler(cleaner, archiver, properties);

        scheduler.cleanExpiredLogs();

        verify(cleaner).cleanExpired(CONFIGURED_DAYS);
    }

    @Test
    @DisplayName("Scheduler：archive-enabled=false 时不触发归档（默认关闭）")
    void shouldSkipArchiveWhenDisabled() {
        RetentionProperties properties = new RetentionProperties();
        LogRetentionScheduler scheduler = new LogRetentionScheduler(cleaner, archiver, properties);

        scheduler.cleanExpiredLogs();

        assertFalse(properties.isArchiveEnabled(), "归档默认必须关闭");
        verify(archiver, never()).archive(anyInt());
    }

    @Test
    @DisplayName("Scheduler：archive-enabled=true 时按保留天数触发归档导出")
    void shouldTriggerArchiveWhenEnabled() {
        RetentionProperties properties = new RetentionProperties();
        properties.setArchiveEnabled(true);
        properties.setDays(CONFIGURED_DAYS);
        LogRetentionScheduler scheduler = new LogRetentionScheduler(cleaner, archiver, properties);

        scheduler.cleanExpiredLogs();

        verify(cleaner).cleanExpired(CONFIGURED_DAYS);
        verify(archiver).archive(CONFIGURED_DAYS);
    }

    @Test
    @DisplayName("RetentionProperties：默认关闭清理，默认保留 30 天")
    void shouldDefaultDisabledWithThirtyDays() {
        RetentionProperties properties = new RetentionProperties();

        assertFalse(properties.isEnabled(), "默认必须关闭，避免误删");
        assertEquals(RETENTION_DAYS, properties.getDays());
        verifyNoInteractions(apiLogMapper);
    }
}

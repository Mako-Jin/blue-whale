package com.bluewhale.pay.service.impl;

import com.bluewhale.pay.config.PayArchiveProperties;
import com.bluewhale.pay.mapper.PayArchiveMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayArchiveServiceImpl} 单元测试：分批搬迁、只归档成功通知、
 * 单阶段失败不阻塞另一阶段、单轮批次上限。
 *
 * @author BlueWhale
 */
class PayArchiveServiceImplTest {

    private final PayArchiveMapper payArchiveMapper = mock(PayArchiveMapper.class);

    private final PayArchiveProperties properties = new PayArchiveProperties();

    private final PayArchiveServiceImpl service =
            new PayArchiveServiceImpl(payArchiveMapper, properties);

    @BeforeEach
    void setUp() {
        properties.setBatchSize(2);
        properties.setMaxBatchesPerRun(3);
    }

    @Test
    @DisplayName("按批搬迁直至不足一批")
    void shouldArchiveInBatches() {
        when(payArchiveMapper.archiveTransactions(any(), eq(2))).thenReturn(2, 2, 1);
        when(payArchiveMapper.deleteArchivedTransactions(any(), eq(2))).thenReturn(2, 2, 1);
        when(payArchiveMapper.archiveNotifyTasks(any(), eq(2))).thenReturn(0);
        when(payArchiveMapper.deleteArchivedNotifyTasks(any(), eq(2))).thenReturn(0);

        assertEquals(5, service.archive());

        verify(payArchiveMapper, times(3)).archiveTransactions(any(), eq(2));
        verify(payArchiveMapper, times(3)).deleteArchivedTransactions(any(), eq(2));
        verify(payArchiveMapper, times(1)).archiveNotifyTasks(any(), eq(2));
    }

    @Test
    @DisplayName("通知归档截止时间按保留天数计算")
    void shouldUseNotifyRetentionCutoff() {
        when(payArchiveMapper.archiveTransactions(any(), eq(2))).thenReturn(0);
        when(payArchiveMapper.deleteArchivedTransactions(any(), eq(2))).thenReturn(0);
        when(payArchiveMapper.archiveNotifyTasks(any(), eq(2))).thenReturn(1);
        when(payArchiveMapper.deleteArchivedNotifyTasks(any(), eq(2))).thenReturn(1);

        assertEquals(1, service.archive());

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(payArchiveMapper).archiveNotifyTasks(cutoff.capture(), eq(2));
        long days = Duration.between(cutoff.getValue(), LocalDateTime.now()).toDays();
        assertEquals(30, days);
    }

    @Test
    @DisplayName("流水归档失败不影响通知归档")
    void shouldContinueNotifyWhenTransactionArchiveFails() {
        when(payArchiveMapper.archiveTransactions(any(), eq(2)))
                .thenThrow(new IllegalStateException("数据库超时"));
        when(payArchiveMapper.archiveNotifyTasks(any(), eq(2))).thenReturn(1);
        when(payArchiveMapper.deleteArchivedNotifyTasks(any(), eq(2))).thenReturn(1);

        assertEquals(1, service.archive());

        verify(payArchiveMapper).archiveNotifyTasks(any(), eq(2));
        verify(payArchiveMapper, times(1)).deleteArchivedNotifyTasks(any(), eq(2));
    }

    @Test
    @DisplayName("单轮批次上限生效，避免长时间占用数据库")
    void shouldStopAtMaxBatchesPerRun() {
        when(payArchiveMapper.archiveTransactions(any(), eq(2))).thenReturn(2);
        when(payArchiveMapper.deleteArchivedTransactions(any(), eq(2))).thenReturn(2);
        when(payArchiveMapper.archiveNotifyTasks(any(), eq(2))).thenReturn(0);
        when(payArchiveMapper.deleteArchivedNotifyTasks(any(), eq(2))).thenReturn(0);

        assertEquals(6, service.archive());

        verify(payArchiveMapper, times(3)).archiveTransactions(any(), eq(2));
    }
}

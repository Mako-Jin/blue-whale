package com.bluewhale.doc.scheduler;

import com.bluewhale.doc.application.service.DocRecycleService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.persistence.query.DocTrashRootRow;
import com.bluewhale.doc.support.DocMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocRecycleCleanupJob} 轮次、单条失败隔离与指标记录断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocRecycleCleanupJobTest {

    @Mock
    private DocRecycleService recycleService;

    @Mock
    private DocMetrics docMetrics;

    private final DocProperties properties = new DocProperties();

    private DocRecycleCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new DocRecycleCleanupJob(recycleService, properties, docMetrics);
    }

    private static DocTrashRootRow row(Long tenantId, Long pageId) {
        DocTrashRootRow row = new DocTrashRootRow();
        row.setTenantId(tenantId);
        row.setPageId(pageId);
        return row;
    }

    @Test
    void cleanupShouldPurgeUntilNoRows() {
        when(recycleService.expiredRoots(any(), anyInt()))
                .thenReturn(List.of(row(1L, 5L), row(2L, 6L)), List.of());

        job.cleanup();

        verify(recycleService).purgeExpired(1L, 5L);
        verify(recycleService).purgeExpired(2L, 6L);
        verify(recycleService, times(2)).expiredRoots(any(), anyInt());
        verify(docMetrics).recordRecyclePurged(2);
    }

    @Test
    void cleanupShouldRecordZeroWhenNothingExpired() {
        when(recycleService.expiredRoots(any(), anyInt())).thenReturn(List.of());

        job.cleanup();

        verify(docMetrics).recordRecyclePurged(0);
        verify(recycleService, times(1)).expiredRoots(any(), anyInt());
        verify(recycleService, org.mockito.Mockito.never()).purgeExpired(any(), any());
    }

    @Test
    void cleanupShouldContinueAfterSingleFailure() {
        when(recycleService.expiredRoots(any(), anyInt())).thenReturn(List.of(row(1L, 5L), row(2L, 6L)), List.of());
        doThrow(new IllegalStateException("boom")).when(recycleService).purgeExpired(1L, 5L);

        job.cleanup();

        verify(recycleService).purgeExpired(2L, 6L);
        verify(docMetrics).recordRecyclePurged(1);
    }

    @Test
    void cleanupShouldStopAtMaxRounds() {
        when(recycleService.expiredRoots(any(), anyInt())).thenReturn(List.of(row(1L, 5L)));

        job.cleanup();

        verify(recycleService, times(20)).expiredRoots(any(), anyInt());
        verify(recycleService, times(20)).purgeExpired(1L, 5L);
        verify(docMetrics).recordRecyclePurged(20);
    }

    @Test
    void cleanupShouldUseConfiguredBatchSizeAndRetention() {
        properties.setRecycleCleanupBatchSize(7);
        properties.setRecycleRetentionDays(3);
        when(recycleService.expiredRoots(any(), anyInt())).thenReturn(List.of());

        job.cleanup();

        verify(recycleService).expiredRoots(any(), org.mockito.ArgumentMatchers.eq(7));
    }
}

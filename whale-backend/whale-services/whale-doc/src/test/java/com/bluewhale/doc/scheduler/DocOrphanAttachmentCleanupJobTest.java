package com.bluewhale.doc.scheduler;

import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAttachmentMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocOrphanAttachmentRow;
import com.bluewhale.doc.support.DocMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocOrphanAttachmentCleanupJob} 按租户分组删除、失败隔离与提前停止断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocOrphanAttachmentCleanupJobTest {

    @Mock
    private DocPageAttachmentMapper attachmentMapper;

    @Mock
    private DocMetrics docMetrics;

    private final DocProperties properties = new DocProperties();

    private DocOrphanAttachmentCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new DocOrphanAttachmentCleanupJob(attachmentMapper, properties, docMetrics);
    }

    private static DocOrphanAttachmentRow row(Long tenantId, Long attachmentId) {
        DocOrphanAttachmentRow row = new DocOrphanAttachmentRow();
        row.setTenantId(tenantId);
        row.setAttachmentId(attachmentId);
        row.setPageId(1L);
        return row;
    }

    @Test
    void cleanupShouldGroupByTenantAndSumDeletedRows() {
        when(attachmentMapper.selectOrphanAttachments(any(), anyInt()))
                .thenReturn(List.of(row(1L, 10L), row(1L, 11L), row(2L, 12L)), List.of());
        when(attachmentMapper.deleteOrphanByIds(eq(1L), anyList())).thenReturn(2);
        when(attachmentMapper.deleteOrphanByIds(eq(2L), anyList())).thenReturn(1);

        int removed = job.cleanup();

        assertThat(removed).isEqualTo(3);
        verify(attachmentMapper).deleteOrphanByIds(1L, List.of(10L, 11L));
        verify(attachmentMapper).deleteOrphanByIds(2L, List.of(12L));
        verify(attachmentMapper, times(2)).selectOrphanAttachments(any(), anyInt());
        verify(docMetrics).recordOrphanRemoved(3);
    }

    @Test
    void cleanupShouldReturnZeroWhenNothingFound() {
        when(attachmentMapper.selectOrphanAttachments(any(), anyInt())).thenReturn(List.of());

        int removed = job.cleanup();

        assertThat(removed).isZero();
        verify(attachmentMapper, never()).deleteOrphanByIds(any(), anyList());
        verify(docMetrics).recordOrphanRemoved(0);
    }

    @Test
    void cleanupShouldRecordFailureAndContinueOtherTenants() {
        when(attachmentMapper.selectOrphanAttachments(any(), anyInt()))
                .thenReturn(List.of(row(1L, 10L), row(2L, 11L)), List.of());
        when(attachmentMapper.deleteOrphanByIds(eq(1L), anyList())).thenThrow(new IllegalStateException("boom"));
        when(attachmentMapper.deleteOrphanByIds(eq(2L), anyList())).thenReturn(1);

        int removed = job.cleanup();

        assertThat(removed).isEqualTo(1);
        verify(docMetrics).recordOrphanFailed();
        verify(docMetrics).recordOrphanRemoved(1);
    }

    @Test
    void cleanupShouldStopWhenBatchDeletesNothing() {
        when(attachmentMapper.selectOrphanAttachments(any(), anyInt()))
                .thenReturn(List.of(row(1L, 10L)));
        when(attachmentMapper.deleteOrphanByIds(eq(1L), anyList())).thenReturn(0);

        int removed = job.cleanup();

        assertThat(removed).isZero();
        verify(attachmentMapper, times(1)).selectOrphanAttachments(any(), anyInt());
        verify(docMetrics).recordOrphanRemoved(0);
    }

    @Test
    void cleanupShouldUseConfiguredBatchSize() {
        properties.setOrphanCleanupBatchSize(9);
        when(attachmentMapper.selectOrphanAttachments(any(), anyInt())).thenReturn(List.of());

        job.cleanup();

        verify(attachmentMapper).selectOrphanAttachments(any(), eq(9));
    }
}

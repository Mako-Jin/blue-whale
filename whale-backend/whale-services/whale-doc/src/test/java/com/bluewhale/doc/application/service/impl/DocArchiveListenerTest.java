package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocArchiveEvent;
import com.bluewhale.doc.application.service.DocArchiveService;
import com.bluewhale.doc.config.DocProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocArchiveListener} 归档事件处理与租户上下文恢复行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocArchiveListenerTest {

    @Mock
    private DocArchiveService archiveService;

    @Mock
    private DocProperties docProperties;

    @InjectMocks
    private DocArchiveListener listener;

    @BeforeEach
    void setUp() {
        TenantContext.set(5L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void onArchiveShouldSkipWhenArchiveDisabled() {
        when(docProperties.isArchiveEnabled()).thenReturn(false);

        listener.onArchive(new DocArchiveEvent(1L, 9L, 2L));

        verify(archiveService, never()).archiveSnapshot(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(TenantContext.get()).isEqualTo(5L);
    }

    @Test
    void onArchiveShouldUseEventTenantAndRestorePrevious() {
        when(docProperties.isArchiveEnabled()).thenReturn(true);
        when(archiveService.archiveSnapshot(1L, 9L, 2L)).thenReturn(true);

        listener.onArchive(new DocArchiveEvent(1L, 9L, 2L));

        verify(archiveService).archiveSnapshot(1L, 9L, 2L);
        assertThat(TenantContext.get()).isEqualTo(5L);
    }

    @Test
    void onArchiveShouldClearTenantWhenNoneWasSet() {
        TenantContext.clear();
        when(docProperties.isArchiveEnabled()).thenReturn(true);

        listener.onArchive(new DocArchiveEvent(1L, 9L, 2L));

        verify(archiveService).archiveSnapshot(1L, 9L, 2L);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void onArchiveShouldSwallowArchiveFailureAndRestoreTenant() {
        when(docProperties.isArchiveEnabled()).thenReturn(true);
        when(archiveService.archiveSnapshot(1L, 9L, 2L)).thenThrow(new IllegalStateException("archive down"));

        assertThatCode(() -> listener.onArchive(new DocArchiveEvent(1L, 9L, 2L))).doesNotThrowAnyException();
        assertThat(TenantContext.get()).isEqualTo(5L);
    }
}

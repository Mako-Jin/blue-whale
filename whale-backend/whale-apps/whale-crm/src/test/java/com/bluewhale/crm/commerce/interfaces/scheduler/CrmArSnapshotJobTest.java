package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.crm.commerce.application.service.ArReconcileAppService;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.config.CrmArProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmArSnapshotJob} 单元测试：关闭时空转，开启时把同步计数作为执行摘要。
 *
 * @author BlueWhale
 */
class CrmArSnapshotJobTest {

    private final ArReconcileAppService arReconcileAppService = mock(ArReconcileAppService.class);

    private final CrmArProperties arProperties = new CrmArProperties();

    private final CrmArSnapshotJob job = new CrmArSnapshotJob(arReconcileAppService, arProperties);

    @Test
    @DisplayName("开关关闭：不调用 ERP，返回 skipped")
    void shouldSkipWhenDisabled() {
        arProperties.setEnabled(false);

        assertEquals("skipped: ar sync disabled", job.sync());
        verify(arReconcileAppService, never()).syncAll();
    }

    @Test
    @DisplayName("开关开启：返回同步/跳过/失败客户数")
    void shouldReturnSyncCounters() {
        arProperties.setEnabled(true);
        when(arReconcileAppService.syncAll()).thenReturn(
                ArSyncResultView.of(3, 2, 1, "完成 3 个客户，跳过 2 个，失败 1 个"));

        assertEquals("synced=3, skipped=2, failed=1", job.sync());
    }
}

package com.bluewhale.archive.scheduler;

import com.bluewhale.archive.client.TenantDirectoryClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.service.ArchiveFileLockReconcileService;
import com.bluewhale.archive.vo.ArchiveFileLockReconcileVO;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArchiveFileLockReconcileScheduler} 单元测试：游标循环、批次上限、租户上下文清理。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ArchiveFileLockReconcileSchedulerTest {

    private static final long TENANT_ID = 9L;

    @Mock
    private ArchiveFileLockReconcileService reconcileService;

    @Mock
    private TenantDirectoryClient tenantDirectoryClient;

    private ArchiveProperties properties;

    private ArchiveFileLockReconcileScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new ArchiveProperties();
        properties.getLockReconcile().setEnabled(true);
        properties.getLockReconcile().setBatchSize(50);
        properties.getLockReconcile().setMaxBatches(3);
        properties.getLockReconcile().setTenantIds(List.of(TENANT_ID));
        scheduler = new ArchiveFileLockReconcileScheduler(reconcileService, properties, tenantDirectoryClient);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("按游标循环补锁直到 hasMore=false，并清理租户上下文")
    void shouldLoopWithCursorUntilDone() {
        ArchiveFileLockReconcileVO first = result(50, true, 100L);
        ArchiveFileLockReconcileVO second = result(20, false, 200L);
        when(reconcileService.reconcile(50, null)).thenReturn(first);
        when(reconcileService.reconcile(50, 100L)).thenReturn(second);

        scheduler.reconcile();

        verify(reconcileService).reconcile(50, null);
        verify(reconcileService).reconcile(50, 100L);
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("达到单轮批次上限后停止，剩余条目留给下次调度")
    void shouldStopAtMaxBatches() {
        when(reconcileService.reconcile(50, null)).thenReturn(result(50, true, 10L));
        when(reconcileService.reconcile(50, 10L)).thenReturn(result(50, true, 20L));
        when(reconcileService.reconcile(50, 20L)).thenReturn(result(50, true, 30L));

        scheduler.reconcile();

        verify(reconcileService).reconcile(50, null);
        verify(reconcileService).reconcile(50, 10L);
        verify(reconcileService).reconcile(50, 20L);
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("游标未推进时提前结束，避免死循环")
    void shouldStopWhenCursorNotAdvanced() {
        when(reconcileService.reconcile(50, null)).thenReturn(result(50, true, null));

        scheduler.reconcile();

        verify(reconcileService).reconcile(50, null);
    }

    @Test
    @DisplayName("开关关闭时定时入口空转，返回 skipped")
    void shouldSkipWhenDisabled() {
        properties.getLockReconcile().setEnabled(false);

        assertEquals("skipped: lock-reconcile disabled", scheduler.reconcile());
        verify(reconcileService, never()).reconcile(anyInt(), any());
    }

    /**
     * 构造补扫结果
     *
     * @param scanned   扫描条目数
     * @param hasMore   是否还有下一批
     * @param nextAfterId 下一批游标
     * @return 补扫结果
     */
    private ArchiveFileLockReconcileVO result(int scanned, boolean hasMore, Long nextAfterId) {
        ArchiveFileLockReconcileVO vo = new ArchiveFileLockReconcileVO();
        vo.setScannedItems(scanned);
        vo.setHasMore(hasMore);
        vo.setNextAfterId(nextAfterId);
        return vo;
    }
}

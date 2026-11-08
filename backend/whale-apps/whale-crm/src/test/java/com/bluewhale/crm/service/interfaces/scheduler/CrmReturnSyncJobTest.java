package com.bluewhale.crm.service.interfaces.scheduler;

import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 退换货 ERP 同步任务入口测试。
 *
 * @author BlueWhale
 */
class CrmReturnSyncJobTest {

    private ReturnRequestAppService returnRequestAppService;

    private CrmErpProperties erpProperties;

    private CrmReturnSyncJob job;

    @BeforeEach
    void setUp() {
        returnRequestAppService = Mockito.mock(ReturnRequestAppService.class);
        erpProperties = new CrmErpProperties();
        job = new CrmReturnSyncJob(returnRequestAppService, erpProperties);
    }

    @Test
    @DisplayName("ERP 下发关闭：下发与对账均返回 skipped，不调应用服务")
    void shouldSkipWhenDisabled() {
        erpProperties.setEnabled(false);

        assertEquals("skipped: crm erp disabled", job.pushPending());
        assertEquals("skipped: crm erp disabled", job.reconcile());
        Mockito.verifyNoInteractions(returnRequestAppService);
    }

    @Test
    @DisplayName("ERP 下发开启：返回下发与刷新摘要")
    void shouldReturnSummaryWhenEnabled() {
        erpProperties.setEnabled(true);
        when(returnRequestAppService.pushPending()).thenReturn(1);
        when(returnRequestAppService.reconcileAll()).thenReturn(3);

        assertEquals("pushed=1", job.pushPending());
        assertEquals("refreshed=3", job.reconcile());
    }
}

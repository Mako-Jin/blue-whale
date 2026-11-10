package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.config.CrmErpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 商务订单下发 / 对账任务入口测试。
 *
 * @author BlueWhale
 */
class CrmOrderSyncJobTest {

    private SalesOrderAppService salesOrderAppService;

    private CrmErpProperties erpProperties;

    private CrmOrderSyncJob job;

    @BeforeEach
    void setUp() {
        salesOrderAppService = Mockito.mock(SalesOrderAppService.class);
        erpProperties = new CrmErpProperties();
        job = new CrmOrderSyncJob(salesOrderAppService, erpProperties);
    }

    @Test
    @DisplayName("ERP 下发关闭：下发与对账均返回 skipped，不调应用服务")
    void shouldSkipWhenDisabled() {
        erpProperties.setEnabled(false);

        assertEquals("skipped: crm erp disabled", job.pushPending());
        assertEquals("skipped: crm erp disabled", job.reconcile());
        Mockito.verifyNoInteractions(salesOrderAppService);
    }

    @Test
    @DisplayName("ERP 下发开启：返回下发与刷新摘要")
    void shouldReturnSummaryWhenEnabled() {
        erpProperties.setEnabled(true);
        when(salesOrderAppService.pushPending()).thenReturn(2);
        when(salesOrderAppService.reconcile()).thenReturn(5);

        assertEquals("pushed=2", job.pushPending());
        assertEquals("refreshed=5", job.reconcile());
    }
}

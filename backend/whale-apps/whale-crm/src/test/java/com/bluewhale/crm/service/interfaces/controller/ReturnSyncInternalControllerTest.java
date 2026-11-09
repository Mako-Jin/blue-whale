package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReturnSyncInternalController} 单元测试：ERP 开关门控与调度触发透传。
 *
 * @author BlueWhale
 */
class ReturnSyncInternalControllerTest {

    private ReturnRequestAppService returnRequestAppService;

    private CrmErpProperties erpProperties;

    private ReturnSyncInternalController controller;

    @BeforeEach
    void setUp() {
        returnRequestAppService = mock(ReturnRequestAppService.class);
        erpProperties = new CrmErpProperties();
        controller = new ReturnSyncInternalController(returnRequestAppService, erpProperties);
    }

    @Test
    @DisplayName("ERP 集成禁用：下发与对账返回 -1 且不触达应用服务")
    void shouldSkipWhenDisabled() {
        erpProperties.setEnabled(false);

        assertEquals(-1, controller.push().getData());
        assertEquals(-1, controller.reconcile().getData());
        verify(returnRequestAppService, never()).pushPending();
        verify(returnRequestAppService, never()).reconcileAll();
    }

    @Test
    @DisplayName("ERP 集成启用：透传下发与对账结果")
    void shouldDelegateWhenEnabled() {
        erpProperties.setEnabled(true);
        when(returnRequestAppService.pushPending()).thenReturn(3);
        when(returnRequestAppService.reconcileAll()).thenReturn(2);

        assertEquals(3, controller.push().getData());
        assertEquals(2, controller.reconcile().getData());
        verify(returnRequestAppService).pushPending();
        verify(returnRequestAppService).reconcileAll();
    }
}

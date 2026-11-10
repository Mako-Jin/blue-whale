package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerPoolInternalController} 单元测试：回收开关门控与系统操作人透传。
 *
 * @author BlueWhale
 */
class CustomerPoolInternalControllerTest {

    private CustomerPoolAppService poolAppService;

    private CrmProperties crmProperties;

    private CustomerPoolInternalController controller;

    @BeforeEach
    void setUp() {
        poolAppService = mock(CustomerPoolAppService.class);
        crmProperties = new CrmProperties();
        controller = new CustomerPoolInternalController(poolAppService, crmProperties);
    }

    @Test
    @DisplayName("公海回收未启用：返回 -1 且不触达应用服务")
    void shouldSkipWhenDisabled() {
        assertEquals(-1, controller.recycle().getData());
        verify(poolAppService, never()).recycle(any());
    }

    @Test
    @DisplayName("公海回收启用：以系统操作人触发回收并返回回收条数")
    void shouldRecycleWhenEnabled() {
        crmProperties.setPoolRecycleEnabled(true);
        PoolRecycleResultView view = new PoolRecycleResultView();
        view.setRecycledCount(4);
        view.setRuleName("60天未跟进");
        when(poolAppService.recycle(any())).thenReturn(view);

        assertEquals(4, controller.recycle().getData());

        ArgumentCaptor<CrmActor> captor = ArgumentCaptor.forClass(CrmActor.class);
        verify(poolAppService).recycle(captor.capture());
        assertEquals("system", captor.getValue().getUsername());
    }
}

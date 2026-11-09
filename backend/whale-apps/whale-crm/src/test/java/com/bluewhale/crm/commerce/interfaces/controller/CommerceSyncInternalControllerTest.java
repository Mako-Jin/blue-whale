package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.commerce.application.service.ArReconcileAppService;
import com.bluewhale.crm.commerce.application.service.ContractAppService;
import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.config.CrmArProperties;
import com.bluewhale.crm.config.CrmContractProperties;
import com.bluewhale.crm.config.CrmErpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CommerceSyncInternalController} 单元测试：集成开关门控与调度触发透传。
 *
 * @author BlueWhale
 */
class CommerceSyncInternalControllerTest {

    private SalesOrderAppService salesOrderAppService;

    private ArReconcileAppService arReconcileAppService;

    private ContractAppService contractAppService;

    private CommerceSyncInternalController controller;

    private CrmErpProperties erpProperties;

    private CrmArProperties arProperties;

    private CrmContractProperties contractProperties;

    @BeforeEach
    void setUp() {
        salesOrderAppService = mock(SalesOrderAppService.class);
        arReconcileAppService = mock(ArReconcileAppService.class);
        contractAppService = mock(ContractAppService.class);
        erpProperties = new CrmErpProperties();
        arProperties = new CrmArProperties();
        contractProperties = new CrmContractProperties();
        controller = new CommerceSyncInternalController(salesOrderAppService, arReconcileAppService,
                contractAppService, erpProperties, arProperties, contractProperties);
    }

    @Test
    @DisplayName("集成禁用：订单/应收/合同端点均返回 -1 且不触达应用服务")
    void shouldSkipWhenDisabled() {
        assertEquals(-1, controller.orderPush().getData());
        assertEquals(-1, controller.orderReconcile().getData());
        assertEquals(-1, controller.arSync().getData());
        assertEquals(-1, controller.contractReconcile().getData());
        verify(salesOrderAppService, never()).pushPending();
        verify(salesOrderAppService, never()).reconcile();
        verify(arReconcileAppService, never()).syncAll();
        verify(contractAppService, never()).reconcile();
    }

    @Test
    @DisplayName("集成启用：透传订单下发/对账、应收同步与合同对账结果")
    void shouldDelegateWhenEnabled() {
        erpProperties.setEnabled(true);
        arProperties.setEnabled(true);
        contractProperties.setEnabled(true);
        when(salesOrderAppService.pushPending()).thenReturn(1);
        when(salesOrderAppService.reconcile()).thenReturn(2);
        when(arReconcileAppService.syncAll()).thenReturn(arView(5));
        when(contractAppService.reconcile()).thenReturn(7);

        assertEquals(1, controller.orderPush().getData());
        assertEquals(2, controller.orderReconcile().getData());
        assertEquals(5, controller.arSync().getData());
        assertEquals(7, controller.contractReconcile().getData());
    }

    private ArSyncResultView arView(int synced) {
        ArSyncResultView view = new ArSyncResultView();
        view.setSynced(synced);
        view.setMessage("ok");
        return view;
    }
}

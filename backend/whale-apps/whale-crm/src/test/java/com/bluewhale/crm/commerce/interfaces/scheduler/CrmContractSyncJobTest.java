package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.crm.commerce.application.service.ContractAppService;
import com.bluewhale.crm.config.CrmContractProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmContractSyncJob} 单元测试：关闭时空转、开启时返回刷新条数、整轮失败上抛。
 *
 * @author BlueWhale
 */
class CrmContractSyncJobTest {

    private final ContractAppService contractAppService = mock(ContractAppService.class);

    private final CrmContractProperties contractProperties = new CrmContractProperties();

    private final CrmContractSyncJob job = new CrmContractSyncJob(contractAppService, contractProperties);

    @Test
    @DisplayName("开关关闭：不请求合同服务，返回 skipped")
    void shouldSkipWhenDisabled() {
        contractProperties.setEnabled(false);

        assertEquals("skipped: contract sync disabled", job.reconcile());
        verify(contractAppService, never()).reconcile();
    }

    @Test
    @DisplayName("开关开启：返回本轮刷新条数")
    void shouldReturnSyncedCount() {
        contractProperties.setEnabled(true);
        when(contractAppService.reconcile()).thenReturn(5);

        assertEquals("synced=5", job.reconcile());
    }

    @Test
    @DisplayName("整轮失败：异常上抛，避免对账停摆被记成成功")
    void shouldPropagateFailure() {
        contractProperties.setEnabled(true);
        when(contractAppService.reconcile()).thenThrow(new IllegalStateException("合同服务不可用"));

        assertThrows(IllegalStateException.class, job::reconcile);
    }
}

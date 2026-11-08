package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.crm.commerce.application.service.ApprovalReconcileAppService;
import com.bluewhale.crm.config.CrmOrderApprovalProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmApprovalReconcileJob} 单元测试：开关关闭时空转，开启时返回本轮处理单据数。
 *
 * @author BlueWhale
 */
class CrmApprovalReconcileJobTest {

    private final ApprovalReconcileAppService approvalReconcileAppService =
            mock(ApprovalReconcileAppService.class);

    private final CrmOrderApprovalProperties orderApprovalProperties = new CrmOrderApprovalProperties();

    private final CrmApprovalReconcileJob job =
            new CrmApprovalReconcileJob(approvalReconcileAppService, orderApprovalProperties);

    @Test
    @DisplayName("对账关闭：不拉取 workflow，返回 skipped")
    void shouldSkipWhenDisabled() {
        orderApprovalProperties.setReconcileEnabled(false);

        assertEquals("skipped: approval reconcile disabled", job.reconcile());
        verify(approvalReconcileAppService, never()).reconcileAll();
    }

    @Test
    @DisplayName("对账开启：返回本轮处理单据数")
    void shouldReturnReconciledCount() {
        orderApprovalProperties.setReconcileEnabled(true);
        when(approvalReconcileAppService.reconcileAll()).thenReturn(4);

        assertEquals("reconciled=4", job.reconcile());
    }
}

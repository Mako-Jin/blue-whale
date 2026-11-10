package com.bluewhale.crm.customer.interfaces.scheduler;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmPoolRecycleJob} 单元测试：开关关闭时空转、开启时返回回收摘要、失败上抛给调度中心。
 *
 * @author BlueWhale
 */
class CrmPoolRecycleJobTest {

    private final CustomerPoolAppService poolAppService = mock(CustomerPoolAppService.class);

    private final CrmProperties crmProperties = new CrmProperties();

    private final CrmPoolRecycleJob job = new CrmPoolRecycleJob(poolAppService, crmProperties);

    @Test
    @DisplayName("开关关闭：不触发回收，返回 skipped")
    void shouldSkipWhenDisabled() {
        crmProperties.setPoolRecycleEnabled(false);

        assertEquals("skipped: pool-recycle disabled", job.recycle());
        verify(poolAppService, never()).recycle(any(CrmActor.class));
    }

    @Test
    @DisplayName("开关开启：返回规则名与回收数量")
    void shouldReturnRecycleSummary() {
        crmProperties.setPoolRecycleEnabled(true);
        PoolRecycleResultView view = new PoolRecycleResultView();
        view.setRuleName("30天未跟进");
        view.setRecycledCount(7);
        when(poolAppService.recycle(any(CrmActor.class))).thenReturn(view);

        assertEquals("rule=30天未跟进, recycled=7", job.recycle());
    }

    @Test
    @DisplayName("执行失败：异常上抛，调度中心可记为失败并重试")
    void shouldPropagateFailure() {
        crmProperties.setPoolRecycleEnabled(true);
        when(poolAppService.recycle(any(CrmActor.class))).thenThrow(new IllegalStateException("库不可用"));

        assertThrows(IllegalStateException.class, job::recycle);
    }
}

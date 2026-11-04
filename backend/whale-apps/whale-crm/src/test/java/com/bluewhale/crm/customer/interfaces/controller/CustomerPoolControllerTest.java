package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.customer.application.command.PoolAssignCommand;
import com.bluewhale.crm.customer.application.command.PoolRuleSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.OwnerLogView;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import com.bluewhale.crm.customer.application.view.PoolRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerPoolController} 单元测试：领取 / 释放 / 分配 / 回收与规则的接口透传。
 *
 * @author BlueWhale
 */
class CustomerPoolControllerTest {

    private CustomerPoolAppService poolAppService;

    private CustomerPoolController controller;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        poolAppService = mock(CustomerPoolAppService.class);
        controller = new CustomerPoolController(poolAppService);
        request = new MockHttpServletRequest();
        request.addHeader(WebHeaders.USER_ID, "1");
        request.addHeader(WebHeaders.USER_ROLES, "crm_sales_manager");
    }

    @Test
    @DisplayName("领取：透传客户 ID 与操作人")
    void shouldClaim() {
        when(poolAppService.claim(anyLong(), any())).thenReturn(new CustomerView());

        controller.claim(9L, request);

        ArgumentCaptor<CrmActor> captor = ArgumentCaptor.forClass(CrmActor.class);
        verify(poolAppService).claim(anyLong(), captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("释放：备注透传")
    void shouldRelease() {
        controller.release(9L, "长期未跟进", request);

        verify(poolAppService).release(anyLong(), any(), any());
    }

    @Test
    @DisplayName("回收：返回回收数量")
    void shouldRecycle() {
        PoolRecycleResultView view = new PoolRecycleResultView();
        view.setRecycledCount(3);
        when(poolAppService.recycle(any())).thenReturn(view);

        Result<PoolRecycleResultView> result = controller.recycle(request);

        assertEquals(3, result.getData().getRecycledCount());
    }

    @Test
    @DisplayName("规则保存：命令透传")
    void shouldSaveRule() {
        PoolRuleView view = new PoolRuleView();
        view.setRuleName("默认公海规则");
        when(poolAppService.saveRule(any(), any())).thenReturn(view);
        PoolRuleSaveCommand command = new PoolRuleSaveCommand();
        command.setInactiveDays(30);

        Result<PoolRuleView> result = controller.saveRule(command, request);

        assertEquals("默认公海规则", result.getData().getRuleName());
        verify(poolAppService).saveRule(any(), any());
    }

    @Test
    @DisplayName("分配：命令透传")
    void shouldAssign() {
        PoolAssignCommand command = new PoolAssignCommand();
        command.setOwnerId(8L);

        controller.assign(9L, command, request);

        verify(poolAppService).assign(anyLong(), any(), any());
    }

    @Test
    @DisplayName("负责人留痕：空列表包装")
    void shouldListOwnerLogs() {
        when(poolAppService.ownerLogs(anyLong(), any())).thenReturn(List.of());

        Result<List<OwnerLogView>> result = controller.ownerLogs(9L, request);

        assertEquals(0, result.getCode());
        assertEquals(0, result.getData().size());
    }
}

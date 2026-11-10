package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.customer.application.command.FollowUpCreateCommand;
import com.bluewhale.crm.customer.application.service.CustomerFollowUpAppService;
import com.bluewhale.crm.customer.application.view.FollowUpView;
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
 * {@link CustomerFollowUpController} 单元测试：分页参数归一与 append-only 新增。
 *
 * @author BlueWhale
 */
class CustomerFollowUpControllerTest {

    private CustomerFollowUpAppService followUpAppService;

    private CustomerFollowUpController controller;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        followUpAppService = mock(CustomerFollowUpAppService.class);
        controller = new CustomerFollowUpController(followUpAppService);
        request = new MockHttpServletRequest();
        request.addHeader(WebHeaders.USER_ID, "7");
        request.addHeader(WebHeaders.USER_ROLES, "crm_sales");
    }

    @Test
    @DisplayName("分页：pageNum/pageSize 归一后交给应用服务")
    void shouldNormalizePaging() {
        when(followUpAppService.page(anyLong(), any(), any()))
                .thenReturn(PageResult.of(List.of(), 0L, 3L, 10L));
        ArgumentCaptor<PageQuery> pageCaptor = ArgumentCaptor.forClass(PageQuery.class);

        Result<PageResult<FollowUpView>> result = controller.page(9L, null, 3L, null, 10L, request);

        verify(followUpAppService).page(anyLong(), pageCaptor.capture(), any());
        assertEquals(3L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());
        assertEquals(0, result.getData().getTotal());
    }

    @Test
    @DisplayName("分页：单页条数超过上限时截断为 200")
    void shouldCapPageSize() {
        when(followUpAppService.page(anyLong(), any(), any()))
                .thenReturn(PageResult.of(List.of(), 0L, 1L, 200L));
        ArgumentCaptor<PageQuery> pageCaptor = ArgumentCaptor.forClass(PageQuery.class);

        controller.page(9L, null, null, 500L, null, request);

        verify(followUpAppService).page(anyLong(), pageCaptor.capture(), any());
        assertEquals(200L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("新增跟进：命令透传并返回跟进视图")
    void shouldCreate() {
        FollowUpView view = new FollowUpView();
        view.setChannel("CALL");
        when(followUpAppService.create(any(), any())).thenReturn(view);
        FollowUpCreateCommand command = new FollowUpCreateCommand();
        command.setCustomerId(9L);
        command.setChannel("CALL");
        command.setContent("电话沟通");

        Result<FollowUpView> result = controller.create(command, request);

        assertEquals(0, result.getCode());
        assertEquals("CALL", result.getData().getChannel());
        verify(followUpAppService).create(any(), any());
    }
}

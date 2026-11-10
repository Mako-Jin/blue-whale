package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.customer.application.command.CustomerSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.service.CustomerImportExportAppService;
import com.bluewhale.crm.customer.application.service.CustomerMergeAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.interfaces.support.CustomerPageParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerController} 单元测试：身份头解析、分页参数归一与响应包装。
 *
 * @author BlueWhale
 */
class CustomerControllerTest {

    private CustomerAppService customerAppService;

    private CustomerController controller;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        customerAppService = mock(CustomerAppService.class);
        controller = new CustomerController(
                customerAppService,
                mock(CustomerMergeAppService.class),
                mock(CustomerImportExportAppService.class));
        request = new MockHttpServletRequest();
        request.addHeader(WebHeaders.USER_ID, "7");
        request.addHeader(WebHeaders.USERNAME, "sales");
        request.addHeader(WebHeaders.USER_ROLES, "crm_sales");
        request.addHeader(WebHeaders.PERMISSION_CODES, CrmPermissions.CUSTOMER_LIST);
    }

    @Test
    @DisplayName("分页：网关身份头解析为操作人，页码走应用层数据范围")
    void shouldDelegatePageWithActor() {
        when(customerAppService.page(any(), any())).thenReturn(PageResult.of(List.of(), 0L, 1L, 20L));
        CustomerPageParams params = new CustomerPageParams();

        Result<PageResult<CustomerView>> result = controller.page(params, 1L, null, 20L, null, request);

        assertEquals(0, result.getCode());
        assertEquals(20, result.getData().getSize());
        ArgumentCaptor<CrmActor> actorCaptor = ArgumentCaptor.forClass(CrmActor.class);
        verify(customerAppService).page(any(), actorCaptor.capture());
        assertEquals(7L, actorCaptor.getValue().getUserId());
        assertEquals("sales", actorCaptor.getValue().getUsername());
    }

    @Test
    @DisplayName("分页：前端 pageNum/pageSize 命名同样生效，并限制单页上限")
    void shouldNormalizeFrontendPagingParams() {
        when(customerAppService.page(any(), any())).thenReturn(PageResult.of(List.of(), 0L, 2L, 200L));
        ArgumentCaptor<CustomerQuery> queryCaptor = ArgumentCaptor.forClass(CustomerQuery.class);

        controller.page(new CustomerPageParams(), null, 2L, null, 500L, request);

        verify(customerAppService).page(queryCaptor.capture(), any());
        assertEquals(2L, queryCaptor.getValue().getCurrent());
        assertEquals(200L, queryCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("新建客户：命令透传并返回客户视图")
    void shouldCreateCustomer() {
        CustomerView view = new CustomerView();
        view.setId(9L);
        when(customerAppService.create(any(), any())).thenReturn(view);
        CustomerSaveCommand command = new CustomerSaveCommand();
        command.setName("蓝鲸科技");

        Result<CustomerView> result = controller.create(command, request);

        assertEquals(0, result.getCode());
        assertEquals(9L, result.getData().getId());
    }

    @Test
    @DisplayName("删除客户：返回空数据包装")
    void shouldReturnEmptyResultOnDelete() {
        Result<Void> result = controller.delete(9L, request);

        assertEquals(0, result.getCode());
        assertNull(result.getData());
        verify(customerAppService).delete(anyLong(), any());
    }
}

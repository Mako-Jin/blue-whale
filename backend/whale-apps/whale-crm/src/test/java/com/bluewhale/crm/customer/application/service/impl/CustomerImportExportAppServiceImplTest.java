package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.view.CustomerImportPreviewView;
import com.bluewhale.crm.customer.application.view.CustomerImportResultView;
import com.bluewhale.crm.customer.domain.model.Contact;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.repository.CustomerContactRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerImportExportAppServiceImpl} 单元测试：预检只读、重复行跳过、行数上限与导出脱敏。
 *
 * @author BlueWhale
 */
class CustomerImportExportAppServiceImplTest {

    private static final String HEADER = "客户名称,等级,手机,邮箱\n";

    private CustomerRepository customerRepository;

    private CustomerContactRepository contactRepository;

    private CustomerPoolRepository poolRepository;

    private CrmProperties properties;

    private CustomerImportExportAppServiceImpl service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        contactRepository = mock(CustomerContactRepository.class);
        poolRepository = mock(CustomerPoolRepository.class);
        properties = new CrmProperties();
        service = new CustomerImportExportAppServiceImpl(customerRepository, contactRepository,
                poolRepository, properties);
    }

    @Test
    @DisplayName("导入预检：逐行统计 OK/INVALID/DUPLICATE，不落库")
    void shouldPreviewWithoutPersisting() {
        String content = HEADER
                + "蓝鲸科技有限公司,B,13800138000,a@x.com\n"
                + ",Z,123,bad-email\n";

        CustomerImportPreviewView view = service.preview(content, manager());

        assertEquals(2, view.getTotal());
        assertEquals(1, view.getValid());
        assertEquals(1, view.getInvalid());
        verify(customerRepository, never()).insert(any());
        verify(customerRepository, never()).update(any());
    }

    @Test
    @DisplayName("执行导入：文件内重复行只创建一次，其余跳过")
    void shouldSkipDuplicateRowsOnExecute() {
        String content = HEADER
                + "蓝鲸科技有限公司,B,13800138000,a@x.com\n"
                + "蓝鲸科技有限公司,B,13800138001,b@x.com\n";

        CustomerImportResultView result = service.execute(content, manager());

        assertEquals(1, result.getCreated());
        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getFailed());
        verify(customerRepository, times(1)).insert(any());
        verify(poolRepository, times(1)).insertOwnerLog(any());
    }

    @Test
    @DisplayName("导入预检：超过单次行数上限直接拒绝")
    void shouldRejectWhenOverImportLimit() {
        properties.setCustomerImportMaxRows(2);
        String content = HEADER
                + "蓝鲸科技,B,13800138000,a@x.com\n"
                + "鲸鱼科技,B,13800138001,b@x.com\n"
                + "海豚科技,B,13800138002,c@x.com\n";

        BizException exception = assertThrows(BizException.class,
                () -> service.preview(content, manager()));
        assertEquals(CrmErrorCode.CUSTOMER_IMPORT_INVALID, exception.getCode());
        verify(customerRepository, never()).insert(any());
    }

    @Test
    @DisplayName("导出：非全量角色强制按本人过滤，敏感字段默认脱敏")
    void shouldExportWithScopeAndMasking() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCustomerCode("CUS-2026-000001");
        customer.setName("蓝鲸科技");
        customer.setOwnerId(7L);
        customer.setStatus("ACTIVE");
        customer.setLevel("C");
        customer.setPoolStatus(PoolStatus.OWNED.name());
        customer.setCreateTime(LocalDateTime.of(2026, 11, 8, 10, 0));
        Contact contact = new Contact();
        contact.setPhone("13800138000");
        contact.setEmail("a@x.com");
        contact.setPrimary(true);
        when(customerRepository.count(any())).thenReturn(1L);
        when(customerRepository.page(any())).thenReturn(List.of(customer));
        when(contactRepository.list(1L)).thenReturn(List.of(contact));
        CustomerQuery query = new CustomerQuery();
        CrmActor sales = new CrmActor(7L, "sales", Set.of("crm_sales"),
                Set.of(CrmPermissions.CUSTOMER_EXPORT));

        String csv = service.export(query, sales);

        assertEquals(7L, query.getOwnerId());
        assertTrue(csv.contains("138****8000"));
        assertTrue(csv.contains("a****@x.com"));
    }

    private static CrmActor manager() {
        return new CrmActor(1L, "manager", Set.of("crm_sales_manager"),
                Set.of(CrmPermissions.CUSTOMER_IMPORT, CrmPermissions.CUSTOMER_EXPORT));
    }
}

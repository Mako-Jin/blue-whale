package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.view.CustomerMergeResultView;
import com.bluewhale.crm.customer.domain.model.Contact;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerMergeCommand;
import com.bluewhale.crm.customer.domain.model.CustomerMergePreview;
import com.bluewhale.crm.customer.domain.model.CustomerStatus;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.port.MergeReferencePort;
import com.bluewhale.crm.customer.domain.repository.CustomerAddressRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerContactRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerFollowUpRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerProfileRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerMergeAppServiceImpl} 单元测试：合并校验、幂等、主联系人冲突与引用重写。
 *
 * @author BlueWhale
 */
class CustomerMergeAppServiceImplTest {

    private CustomerRepository customerRepository;

    private CustomerContactRepository contactRepository;

    private CustomerAddressRepository addressRepository;

    private CustomerProfileRepository profileRepository;

    private CustomerFollowUpRepository followUpRepository;

    private CustomerPoolRepository poolRepository;

    private MergeReferencePort referencePort;

    private CustomerMergeAppServiceImpl service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        contactRepository = mock(CustomerContactRepository.class);
        addressRepository = mock(CustomerAddressRepository.class);
        profileRepository = mock(CustomerProfileRepository.class);
        followUpRepository = mock(CustomerFollowUpRepository.class);
        poolRepository = mock(CustomerPoolRepository.class);
        referencePort = mock(MergeReferencePort.class);
        service = new CustomerMergeAppServiceImpl(customerRepository, contactRepository, addressRepository,
                profileRepository, followUpRepository, poolRepository, new CrmProperties(),
                List.of(referencePort));
    }

    @Test
    @DisplayName("合并预览：源客户与目标客户不能相同")
    void shouldRejectSameCustomer() {
        CustomerMergeCommand command = command(1L, 1L);

        BizException exception = assertThrows(BizException.class, () -> service.preview(command, manager()));
        assertEquals(CrmErrorCode.CUSTOMER_MERGE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("合并：源客户此前已合并到目标时幂等返回目标视图")
    void shouldReturnIdempotentWhenAlreadyMerged() {
        Customer source = customer(1L);
        source.setStatus(CustomerStatus.MERGED.name());
        source.setMergedIntoId(2L);
        Customer target = customer(2L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(source));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(target));

        CustomerMergeResultView result = service.merge(command(1L, 2L), manager());

        assertTrue(result.isIdempotent());
        assertEquals(2L, result.getTarget().getId());
        verify(referencePort, never()).rewriteReferences(any(), any());
        verify(customerRepository, never()).update(any());
    }

    @Test
    @DisplayName("合并预览：双方均有主联系人时给出冲突警告并统计引用")
    void shouldWarnPrimaryContactConflict() {
        Customer source = customer(1L);
        Customer target = customer(2L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(source));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(target));
        when(contactRepository.list(1L)).thenReturn(List.of(primaryContact(1L)));
        when(contactRepository.list(2L)).thenReturn(List.of(primaryContact(2L)));
        when(referencePort.countReferences(1L, 2L)).thenReturn(Map.of("商机", 2L));

        CustomerMergePreview preview = service.preview(command(1L, 2L), manager());

        assertEquals(2L, preview.getReferences().get("商机"));
        assertTrue(preview.getWarnings().stream().anyMatch(warning -> warning.contains("主联系人")));
        verify(referencePort, never()).rewriteReferences(any(), any());
    }

    @Test
    @DisplayName("执行合并：先重写跨上下文引用，再置源客户 MERGED 只读")
    void shouldRewriteReferencesAndMarkSourceMerged() {
        Customer source = customer(1L);
        Customer target = customer(2L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(source));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(target));
        when(contactRepository.list(1L)).thenReturn(List.of());
        when(contactRepository.list(2L)).thenReturn(List.of());
        when(addressRepository.list(1L)).thenReturn(List.of());
        when(addressRepository.list(2L)).thenReturn(List.of());
        when(profileRepository.findByCustomerId(1L)).thenReturn(Optional.empty());
        when(profileRepository.findByCustomerId(2L)).thenReturn(Optional.empty());
        when(followUpRepository.reassignCustomer(1L, 2L)).thenReturn(3);
        when(referencePort.rewriteReferences(1L, 2L)).thenReturn(Map.of("商机", 2L, "合同", 1L));

        CustomerMergeResultView result = service.merge(command(1L, 2L), manager());

        assertEquals(2L, result.getReferences().get("商机"));
        assertEquals(3, result.getMigratedFollowUps());
        assertEquals(CustomerStatus.MERGED.name(), source.getStatus());
        assertEquals(2L, source.getMergedIntoId());
        verify(referencePort).rewriteReferences(1L, 2L);
        verify(customerRepository).update(source);
    }

    @Test
    @DisplayName("执行合并：目标客户已合并时拒绝")
    void shouldRejectMergedTarget() {
        Customer source = customer(1L);
        Customer target = customer(2L);
        target.setStatus(CustomerStatus.MERGED.name());
        target.setMergedIntoId(3L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(source));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(target));

        BizException exception = assertThrows(BizException.class,
                () -> service.merge(command(1L, 2L), manager()));
        assertEquals(CrmErrorCode.CUSTOMER_MERGE_INVALID, exception.getCode());
        verify(referencePort, never()).rewriteReferences(any(), any());
    }

    private static CustomerMergeCommand command(Long sourceId, Long targetId) {
        CustomerMergeCommand command = new CustomerMergeCommand();
        command.setSourceId(sourceId);
        command.setTargetId(targetId);
        return command;
    }

    private static CrmActor manager() {
        return new CrmActor(1L, "manager", Set.of("crm_sales_manager"),
                Set.of(CrmPermissions.SENSITIVE_VIEW, CrmPermissions.CUSTOMER_MERGE));
    }

    private static Customer customer(Long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("蓝鲸科技" + id);
        customer.setCustomerCode("CUS-2026-00000" + id);
        customer.setStatus(CustomerStatus.ACTIVE.name());
        customer.setOwnerId(1L);
        customer.setLevel("C");
        customer.setPoolStatus(PoolStatus.OWNED.name());
        return customer;
    }

    private static Contact primaryContact(Long customerId) {
        Contact contact = new Contact();
        contact.setId(customerId);
        contact.setCustomerId(customerId);
        contact.setPrimary(true);
        return contact;
    }
}

package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.ContractRenewalCommand;
import com.bluewhale.crm.commerce.application.view.ContractView;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.port.ContractProjection;
import com.bluewhale.crm.commerce.domain.port.ContractSyncPort;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.config.CrmContractProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ContractAppServiceImpl} 单元测试：投影同步、续约字段保留与状态校验。
 *
 * @author BlueWhale
 */
class ContractAppServiceImplTest {

    private ContractLedgerRepository ledgerRepository;

    private ContractSyncPort contractSyncPort;

    private CustomerAppService customerAppService;

    private ContractAppServiceImpl service;

    @BeforeEach
    void setUp() {
        ledgerRepository = mock(ContractLedgerRepository.class);
        contractSyncPort = mock(ContractSyncPort.class);
        customerAppService = mock(CustomerAppService.class);
        service = new ContractAppServiceImpl(ledgerRepository, contractSyncPort, customerAppService,
                new CrmProperties(), new CrmContractProperties());
    }

    @Test
    @DisplayName("按客户同步：新合同落库并继承客户负责人")
    void shouldCreateProjectionWhenAbsent() {
        CustomerDetailView detail = new CustomerDetailView();
        CustomerView customer = new CustomerView();
        customer.setOwnerId(7L);
        customer.setDeptId(9L);
        detail.setCustomer(customer);
        when(customerAppService.detail(anyLong(), any())).thenReturn(detail);
        when(ledgerRepository.findByContractId(100L)).thenReturn(null);
        when(contractSyncPort.listByCustomer(11L)).thenReturn(List.of(projection()));

        int count = service.syncByCustomer(11L, actor());

        assertEquals(1, count);
        ArgumentCaptor<ContractLedger> captor = ArgumentCaptor.forClass(ContractLedger.class);
        verify(ledgerRepository).insert(captor.capture());
        ContractLedger saved = captor.getValue();
        assertEquals(11L, saved.getCustomerId());
        assertEquals(7L, saved.getOwnerId());
        assertEquals(9L, saved.getDeptId());
        assertEquals("PENDING", saved.getRenewalStatus());
        assertEquals(new BigDecimal("500.00"), saved.getAmount());
        assertNotNull(saved.getLastSyncAt());
    }

    @Test
    @DisplayName("重复同步：更新投影但不覆盖本地续约字段")
    void shouldKeepRenewalFieldsWhenProjectionUpdated() {
        CustomerDetailView detail = new CustomerDetailView();
        CustomerView customer = new CustomerView();
        customer.setOwnerId(7L);
        detail.setCustomer(customer);
        when(customerAppService.detail(anyLong(), any())).thenReturn(detail);
        ContractLedger existing = new ContractLedger();
        existing.setId(1L);
        existing.setContractId(100L);
        existing.setCustomerId(11L);
        existing.setRenewalStatus("RENEWED");
        existing.setRenewalRemark("客户已确认续约");
        when(ledgerRepository.findByContractId(100L)).thenReturn(existing);
        when(contractSyncPort.listByCustomer(11L)).thenReturn(List.of(projection()));

        service.syncByCustomer(11L, actor());

        verify(ledgerRepository).updateProjection(existing);
        assertEquals("RENEWED", existing.getRenewalStatus());
        assertEquals("客户已确认续约", existing.getRenewalRemark());
    }

    @Test
    @DisplayName("续约标记：合法状态更新成功")
    void shouldMarkRenewal() {
        ContractLedger ledger = new ContractLedger();
        ledger.setId(1L);
        ledger.setContractId(100L);
        ledger.setCustomerId(11L);
        ledger.setOwnerId(7L);
        ledger.setRenewalStatus("PENDING");
        when(ledgerRepository.findById(1L)).thenReturn(ledger);
        when(ledgerRepository.updateRenewal(any())).thenReturn(true);

        ContractRenewalCommand command = new ContractRenewalCommand();
        command.setRenewalStatus("renewed");
        command.setRenewalRemark("已续签");
        ContractView view = service.markRenewal(1L, command, actor());

        assertEquals("RENEWED", view.getRenewalStatus());
        assertEquals("已续签", view.getRenewalRemark());
        assertNotNull(view.getRenewalAt());
    }

    @Test
    @DisplayName("续约标记：非法状态拒绝")
    void shouldRejectInvalidRenewalStatus() {
        ContractLedger ledger = new ContractLedger();
        ledger.setId(1L);
        ledger.setOwnerId(7L);
        when(ledgerRepository.findById(1L)).thenReturn(ledger);
        ContractRenewalCommand command = new ContractRenewalCommand();
        command.setRenewalStatus("UNKNOWN");

        BizException exception = assertThrows(BizException.class,
                () -> service.markRenewal(1L, command, actor()));
        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
        verify(ledgerRepository, never()).updateRenewal(any());
    }

    @Test
    @DisplayName("跨数据范围：非本人合同按不存在处理")
    void shouldHideContractFromOtherOwner() {
        ContractLedger ledger = new ContractLedger();
        ledger.setId(1L);
        ledger.setOwnerId(8L);
        when(ledgerRepository.findById(1L)).thenReturn(ledger);

        BizException exception = assertThrows(BizException.class,
                () -> service.detail(1L, actor()));
        assertEquals(CrmErrorCode.CONTRACT_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造合同投影。
     *
     * @return 合同投影
     */
    private ContractProjection projection() {
        return new ContractProjection(100L, "HT-2026-001", "年度框架合同", "SALES",
                new BigDecimal("500.00"), "CNY", "EFFECTIVE", LocalDate.now(),
                LocalDate.now().plusMonths(12), "CRM_CUSTOMER", "11", null, null,
                LocalDateTime.now());
    }

    /**
     * 构造销售操作人。
     *
     * @return 操作人
     */
    private CrmActor actor() {
        return new CrmActor(7L, "sales", Set.of("crm_sales"), Set.of());
    }
}

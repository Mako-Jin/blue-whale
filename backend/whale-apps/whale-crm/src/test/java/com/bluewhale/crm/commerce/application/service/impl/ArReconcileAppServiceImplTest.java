package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.commerce.domain.model.ArSnapshot;
import com.bluewhale.crm.commerce.domain.model.ArSnapshotQuery;
import com.bluewhale.crm.commerce.domain.port.ErpReceivablePort;
import com.bluewhale.crm.commerce.domain.port.ErpReceivableSummary;
import com.bluewhale.crm.commerce.domain.repository.ArSnapshotRepository;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.config.CrmArProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArReconcileAppServiceImpl} 单元测试：开关门禁、逐客户降级与快照落库。
 *
 * @author BlueWhale
 */
class ArReconcileAppServiceImplTest {

    private static final CrmActor ACTOR = new CrmActor(7L, "tester", Set.of("crm_admin"), Set.of());

    private ArSnapshotRepository arSnapshotRepository;

    private ContractLedgerRepository contractLedgerRepository;

    private CustomerAppService customerAppService;

    private ErpReceivablePort erpReceivablePort;

    private CrmArProperties arProperties;

    private ArReconcileAppServiceImpl service;

    @BeforeEach
    void setUp() {
        arSnapshotRepository = mock(ArSnapshotRepository.class);
        contractLedgerRepository = mock(ContractLedgerRepository.class);
        customerAppService = mock(CustomerAppService.class);
        erpReceivablePort = mock(ErpReceivablePort.class);
        arProperties = new CrmArProperties();
        service = new ArReconcileAppServiceImpl(arSnapshotRepository, contractLedgerRepository,
                customerAppService, erpReceivablePort, arProperties, new CrmProperties());
    }

    @Test
    @DisplayName("同步开关关闭：直接返回未启用，不访问客户与 ERP")
    void shouldSkipWhenDisabled() {
        ArSyncResultView result = service.sync(ACTOR);

        assertEquals(0, result.getSynced());
        assertEquals("应收对账未启用", result.getMessage());
        verify(contractLedgerRepository, never()).findCustomerIds();
        verify(erpReceivablePort, never()).fetchByCustomerCode(anyString());
    }

    @Test
    @DisplayName("同步：单客户 ERP 失败不中断，其余客户照常落快照")
    void shouldContinueWhenSingleCustomerFails() {
        arProperties.setEnabled(true);
        when(contractLedgerRepository.findCustomerIds()).thenReturn(List.of(21L, 22L));
        when(customerAppService.detail(eq(21L), any(CrmActor.class))).thenReturn(customerDetail("CUS-001"));
        when(customerAppService.detail(eq(22L), any(CrmActor.class))).thenThrow(new IllegalStateException("ERP down"));
        when(erpReceivablePort.fetchByCustomerCode("CUS-001")).thenReturn(summary());

        ArSyncResultView result = service.sync(ACTOR);

        assertEquals(1, result.getSynced());
        assertEquals(1, result.getFailed());
        assertEquals(0, result.getSkipped());
        verify(arSnapshotRepository).upsert(any(ArSnapshot.class));
    }

    @Test
    @DisplayName("同步：ERP 无该客户时跳过，不写快照")
    void shouldSkipEmptySummary() {
        arProperties.setEnabled(true);
        when(contractLedgerRepository.findCustomerIds()).thenReturn(List.of(21L));
        when(customerAppService.detail(eq(21L), any(CrmActor.class))).thenReturn(customerDetail("CUS-001"));
        when(erpReceivablePort.fetchByCustomerCode("CUS-001")).thenReturn(emptySummary());

        ArSyncResultView result = service.sync(ACTOR);

        assertEquals(1, result.getSkipped());
        verify(arSnapshotRepository, never()).upsert(any(ArSnapshot.class));
    }

    @Test
    @DisplayName("同步：按 ERP 汇总填充账龄桶并标记来源")
    void shouldFillAgingBuckets() {
        arProperties.setEnabled(true);
        when(contractLedgerRepository.findCustomerIds()).thenReturn(List.of(21L));
        when(customerAppService.detail(eq(21L), any(CrmActor.class))).thenReturn(customerDetail("CUS-001"));
        when(erpReceivablePort.fetchByCustomerCode("CUS-001")).thenReturn(summary());
        ArgumentCaptor<ArSnapshot> captor = ArgumentCaptor.forClass(ArSnapshot.class);

        service.sync(ACTOR);

        verify(arSnapshotRepository).upsert(captor.capture());
        ArSnapshot snapshot = captor.getValue();
        assertEquals(21L, snapshot.getCustomerId());
        assertEquals("CUS-001", snapshot.getCustomerCode());
        assertEquals(new BigDecimal("120.00"), snapshot.getTotalRemaining());
        assertEquals(new BigDecimal("20.00"), snapshot.getOverdueOver90());
        assertEquals("ERP", snapshot.getSource());
    }

    @Test
    @DisplayName("同步：按 batchSize 截断客户清单")
    void shouldLimitBatchSize() {
        arProperties.setEnabled(true);
        arProperties.setBatchSize(1);
        when(contractLedgerRepository.findCustomerIds()).thenReturn(List.of(21L, 22L));
        when(customerAppService.detail(eq(21L), any(CrmActor.class))).thenReturn(customerDetail("CUS-001"));
        when(erpReceivablePort.fetchByCustomerCode("CUS-001")).thenReturn(summary());

        service.sync(ACTOR);

        verify(customerAppService, never()).detail(eq(22L), any(CrmActor.class));
    }

    @Test
    @DisplayName("分页：非全量数据范围收口到本人负责人")
    void shouldScopeOwnerForNonAdmin() {
        CrmActor sales = new CrmActor(9L, "sales", Set.of("crm_sales"), Set.of());
        ArSnapshotQuery query = new ArSnapshotQuery();
        when(arSnapshotRepository.page(query)).thenReturn(List.of());
        when(arSnapshotRepository.count(query)).thenReturn(0L);

        service.page(query, sales);

        assertEquals(9L, query.getOwnerId());
        verify(arSnapshotRepository).page(query);
        verify(arSnapshotRepository).count(query);
    }

    /**
     * 构造客户详情。
     *
     * @param code 客户编码
     * @return 客户详情
     */
    private CustomerDetailView customerDetail(String code) {
        CustomerView customer = new CustomerView();
        customer.setId(21L);
        customer.setCustomerCode(code);
        customer.setName("蓝鲸科技");
        customer.setOwnerId(5L);
        customer.setDeptId(6L);
        CustomerDetailView detail = new CustomerDetailView();
        detail.setCustomer(customer);
        return detail;
    }

    /**
     * 构造 ERP 空摘要（客户不存在）。
     *
     * @return 空摘要
     */
    private ErpReceivableSummary emptySummary() {
        return new ErpReceivableSummary(null, null, null, null, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    /**
     * 构造 ERP 应收汇总。
     *
     * @return 汇总
     */
    private ErpReceivableSummary summary() {
        return new ErpReceivableSummary(5L, "CUS-001", "蓝鲸科技", "CNY", 2,
                new BigDecimal("120.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("20.00"), List.of());
    }
}

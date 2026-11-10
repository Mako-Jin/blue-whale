package com.bluewhale.erp.analytics.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.analytics.application.command.CockpitRefreshCommand;
import com.bluewhale.erp.analytics.application.view.CockpitView;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.analytics.domain.model.AnalyticsStatus;
import com.bluewhale.erp.analytics.domain.model.CockpitSnapshot;
import com.bluewhale.erp.analytics.domain.model.CostFact;
import com.bluewhale.erp.analytics.domain.model.SalesFact;
import com.bluewhale.erp.analytics.domain.repository.AlertRepository;
import com.bluewhale.erp.analytics.domain.repository.AnalyticsRepository;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnalyticsAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class AnalyticsAppServiceImplTest {

    private final AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);

    private final AlertRepository alertRepository = mock(AlertRepository.class);

    private final AnalyticsAppServiceImpl service =
            new AnalyticsAppServiceImpl(analyticsRepository, alertRepository);

    @Test
    @DisplayName("驾驶舱优先返回有效快照缓存")
    void shouldReturnActiveSnapshot() {
        CockpitSnapshot snapshot = snapshot();
        when(analyticsRepository.findActiveSnapshot("202611")).thenReturn(Optional.of(snapshot));

        CockpitView view = service.getCockpit("202611");

        assertEquals("CPS-202611-0001", view.getSnapshotNo());
        assertEquals(AnalyticsStatus.SNAPSHOT_ACTIVE, view.getStatus());
        verify(analyticsRepository, never()).insertSnapshot(any(CockpitSnapshot.class));
    }

    @Test
    @DisplayName("刷新驾驶舱按收入-成本计算利润并失效旧快照")
    void shouldRefreshCockpit() {
        when(analyticsRepository.findActiveSnapshot(anyString())).thenReturn(Optional.empty());
        when(analyticsRepository.findSnapshot("202611", LocalDate.of(2026, 11, 4)))
                .thenReturn(Optional.empty());
        when(analyticsRepository.nextSnapshotNo("202611")).thenReturn("CPS-202611-0001");
        when(analyticsRepository.listSalesFacts(any(), any())).thenReturn(List.of(
                sales("SO-1", "C001", "客户一", "I001", new BigDecimal("10"), new BigDecimal("6"),
                        new BigDecimal("100")),
                sales("SO-2", "C001", "客户一", "I002", new BigDecimal("10"), new BigDecimal("10"),
                        new BigDecimal("50"))));
        when(analyticsRepository.listCostFacts(any(), any()))
                .thenReturn(List.of(cost("I001", new BigDecimal("30"))));
        when(alertRepository.countOpenEvents()).thenReturn(1L);

        CockpitRefreshCommand command = new CockpitRefreshCommand();
        command.setPeriodCode("202611");
        command.setStatDate(LocalDate.of(2026, 11, 4));
        CockpitView view = service.refreshCockpit(command);

        assertEquals(0, view.getRevenueAmount().compareTo(new BigDecimal("110")));
        assertEquals(0, view.getProfitAmount().compareTo(new BigDecimal("80")));
        assertEquals(2, view.getOrderCount());
        assertEquals(1, view.getAlertCount());
        verify(analyticsRepository).markPeriodStale("202611");
        ArgumentCaptor<CockpitSnapshot> captor = ArgumentCaptor.forClass(CockpitSnapshot.class);
        verify(analyticsRepository).insertSnapshot(captor.capture());
        assertEquals(AnalyticsStatus.SNAPSHOT_ACTIVE, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("统计日期超出期间拒绝刷新")
    void shouldRejectStatDateOutsidePeriod() {
        CockpitRefreshCommand command = new CockpitRefreshCommand();
        command.setPeriodCode("202610");
        command.setStatDate(LocalDate.of(2026, 11, 4));

        BizException exception = assertThrows(BizException.class, () -> service.refreshCockpit(command));

        assertEquals(ErpErrorCode.ANALYTICS_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("多维分析按客户聚合已发货收入")
    void shouldAggregateRevenueByCustomer() {
        when(analyticsRepository.listSalesFacts(any(), any())).thenReturn(List.of(
                sales("SO-1", "C001", "客户一", "I001", new BigDecimal("10"), new BigDecimal("10"),
                        new BigDecimal("100")),
                sales("SO-2", "C001", "客户一", "I002", new BigDecimal("10"), new BigDecimal("5"),
                        new BigDecimal("200"))));

        AnalyticsQuery query = new AnalyticsQuery();
        query.setDimensionType(AnalyticsStatus.DIMENSION_CUSTOMER);
        query.setMetricType(AnalyticsStatus.METRIC_REVENUE);
        query.setFromDate(LocalDate.of(2026, 11, 1));
        query.setToDate(LocalDate.of(2026, 11, 4));
        CubeResultView result = service.cube(query);

        assertEquals(1, result.getRows().size());
        assertEquals("客户一", result.getRows().get(0).getDimensionName());
        assertEquals(0, result.getRows().get(0).getMetricValue().compareTo(new BigDecimal("200")));
        assertEquals(2, result.getRows().get(0).getOrderCount());
        assertTrue(result.getNote() == null);
    }

    @Test
    @DisplayName("成本指标不支持客户维度")
    void shouldRejectCostByCustomer() {
        AnalyticsQuery query = new AnalyticsQuery();
        query.setDimensionType(AnalyticsStatus.DIMENSION_CUSTOMER);
        query.setMetricType(AnalyticsStatus.METRIC_COST);

        BizException exception = assertThrows(BizException.class, () -> service.cube(query));

        assertEquals(ErpErrorCode.ANALYTICS_INVALID, exception.getCode());
    }

    /**
     * 构造快照。
     *
     * @return 快照
     */
    private CockpitSnapshot snapshot() {
        CockpitSnapshot snapshot = new CockpitSnapshot();
        snapshot.setSnapshotNo("CPS-202611-0001");
        snapshot.setPeriodCode("202611");
        snapshot.setStatDate(LocalDate.of(2026, 11, 4));
        snapshot.setRevenueAmount(new BigDecimal("100"));
        snapshot.setCostAmount(new BigDecimal("40"));
        snapshot.setProfitAmount(new BigDecimal("60"));
        snapshot.setOrderCount(3);
        snapshot.setAlertCount(0);
        snapshot.setStatus(AnalyticsStatus.SNAPSHOT_ACTIVE);
        return snapshot;
    }

    /**
     * 构造销售事实。
     *
     * @param docNo 订单号
     * @param customerId 客户ID
     * @param customerName 客户名称
     * @param itemCode 物料编码
     * @param quantity 数量
     * @param shipped 已发数量
     * @param amount 金额
     * @return 销售事实
     */
    private SalesFact sales(String docNo, String customerId, String customerName, String itemCode,
                            BigDecimal quantity, BigDecimal shipped, BigDecimal amount) {
        SalesFact fact = new SalesFact();
        fact.setDocNo(docNo);
        fact.setCustomerId(Long.valueOf(customerId.substring(1)));
        fact.setCustomerName(customerName);
        fact.setItemCode(itemCode);
        fact.setItemName(itemCode);
        fact.setQuantity(quantity);
        fact.setShippedQuantity(shipped);
        fact.setAmount(amount);
        return fact;
    }

    /**
     * 构造成本事实。
     *
     * @param itemCode 物料编码
     * @param amount 金额
     * @return 成本事实
     */
    private CostFact cost(String itemCode, BigDecimal amount) {
        CostFact fact = new CostFact();
        fact.setItemCode(itemCode);
        fact.setItemName(itemCode);
        fact.setAmount(amount);
        return fact;
    }
}

package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.erp.mfg.application.view.MfgBoardView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 制造看板聚合服务单元测试。
 *
 * <p>覆盖字段逐项映射、上限收敛与空数据场景：看板数字必须与仓储查询结果一致，
 * 不允许多算、少算或补默认值。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MfgBoardAppServiceImplTest {

    @Mock
    private ProductionOrderRepository productionOrderRepository;

    @Mock
    private StockRepository stockRepository;

    private MfgBoardAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MfgBoardAppServiceImpl(productionOrderRepository, stockRepository);
    }

    @Test
    @DisplayName("订单与库存字段逐项映射，默认上限 50")
    void shouldMapFieldsWithDefaultLimit() {
        when(productionOrderRepository.page(any(MfgQuery.class))).thenReturn(List.of(order()));
        when(stockRepository.pageBalances(any(ScmQuery.class))).thenReturn(List.of(balance()));

        MfgBoardView view = service.board(null);

        assertNotNull(view.getGeneratedAt());
        assertEquals(1, view.getOrders().size());
        MfgBoardView.OrderLine orderLine = view.getOrders().get(0);
        assertEquals("MO-0001", orderLine.getOrderNo());
        assertEquals("ITEM-01", orderLine.getItemCode());
        assertEquals("蓝鲸样件", orderLine.getItemName());
        assertEquals(0, new BigDecimal("100").compareTo(orderLine.getPlannedQuantity()));
        assertEquals(0, new BigDecimal("40").compareTo(orderLine.getCompletedQuantity()));
        assertEquals("IN_PROGRESS", orderLine.getStatus());
        assertEquals(9001L, orderLine.getTargetWarehouseId());
        assertEquals("成品库", orderLine.getTargetWarehouseName());
        assertEquals("MES-WO-0001", orderLine.getMesWorkOrderCode());

        assertEquals(1, view.getStocks().size());
        MfgBoardView.StockLine stockLine = view.getStocks().get(0);
        assertEquals("ITEM-01", stockLine.getItemCode());
        assertEquals("蓝鲸样件", stockLine.getItemName());
        assertEquals(9001L, stockLine.getWarehouseId());
        assertEquals(0, new BigDecimal("40").compareTo(stockLine.getQuantity()));

        assertEquals(50L, capturedOrderQuery().getSize());
        assertEquals(1L, capturedOrderQuery().getCurrent());
        assertEquals(50L, capturedStockQuery().getSize());
        assertEquals(1L, capturedStockQuery().getCurrent());
    }

    @Test
    @DisplayName("超上限收敛到 200，非正数回落到默认 50")
    void shouldClampLimit() {
        when(productionOrderRepository.page(any(MfgQuery.class))).thenReturn(List.of());
        when(stockRepository.pageBalances(any(ScmQuery.class))).thenReturn(List.of());

        service.board(500);
        assertEquals(200L, capturedOrderQuery().getSize());
        assertEquals(200L, capturedStockQuery().getSize());

        service.board(0);
        assertEquals(50L, capturedOrderQuery().getSize());
        assertEquals(50L, capturedStockQuery().getSize());

        service.board(-3);
        assertEquals(50L, capturedOrderQuery().getSize());
        assertEquals(50L, capturedStockQuery().getSize());
    }

    @Test
    @DisplayName("空数据返回空列表而不是 null")
    void shouldReturnEmptyLists() {
        when(productionOrderRepository.page(any(MfgQuery.class))).thenReturn(List.of());
        when(stockRepository.pageBalances(any(ScmQuery.class))).thenReturn(List.of());

        MfgBoardView view = service.board(10);

        assertTrue(view.getOrders().isEmpty());
        assertTrue(view.getStocks().isEmpty());
        assertNotNull(view.getGeneratedAt());
        assertEquals(10L, capturedOrderQuery().getSize());
    }

    /**
     * 捕获最后一次生产订单查询条件。
     *
     * @return 查询条件
     */
    private MfgQuery capturedOrderQuery() {
        ArgumentCaptor<MfgQuery> captor = ArgumentCaptor.forClass(MfgQuery.class);
        verify(productionOrderRepository, org.mockito.Mockito.atLeastOnce()).page(captor.capture());
        List<MfgQuery> values = captor.getAllValues();
        return values.get(values.size() - 1);
    }

    /**
     * 捕获最后一次库存查询条件。
     *
     * @return 查询条件
     */
    private ScmQuery capturedStockQuery() {
        ArgumentCaptor<ScmQuery> captor = ArgumentCaptor.forClass(ScmQuery.class);
        verify(stockRepository, org.mockito.Mockito.atLeastOnce()).pageBalances(captor.capture());
        List<ScmQuery> values = captor.getAllValues();
        return values.get(values.size() - 1);
    }

    /**
     * 构造生产订单。
     *
     * @return 生产订单
     */
    private ProductionOrder order() {
        ProductionOrder item = new ProductionOrder();
        item.setOrderNo("MO-0001");
        item.setItemCode("ITEM-01");
        item.setItemName("蓝鲸样件");
        item.setPlannedQuantity(new BigDecimal("100"));
        item.setCompletedQuantity(new BigDecimal("40"));
        item.setStatus("IN_PROGRESS");
        item.setTargetWarehouseId(9001L);
        item.setTargetWarehouseName("成品库");
        item.setMesWorkOrderCode("MES-WO-0001");
        return item;
    }

    /**
     * 构造库存余额。
     *
     * @return 库存余额
     */
    private StockBalance balance() {
        StockBalance item = new StockBalance();
        item.setItemCode("ITEM-01");
        item.setItemName("蓝鲸样件");
        item.setWarehouseId(9001L);
        item.setQuantity(new BigDecimal("40"));
        return item;
    }
}

package com.bluewhale.scene.application.service.impl;

import com.bluewhale.scene.application.port.ManufacturingBoardPort;
import com.bluewhale.scene.application.view.MfgBoardView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 3D 看板制造数据服务单元测试。
 *
 * <p>核心约束：端口有数据时透传并标记未降级；端口无数据时显式降级，
 * 且不展示任何数量，避免把数据源不可用伪装成 0 产量。</p>
 *
 * @author BlueWhale
 */
class SceneBoardAppServiceImplTest {

    private final ManufacturingBoardPort manufacturingBoardPort = mock(ManufacturingBoardPort.class);

    private final SceneBoardAppServiceImpl service = new SceneBoardAppServiceImpl(manufacturingBoardPort);

    @Test
    @DisplayName("端口有数据时透传并清除降级标识")
    void shouldPassThroughWhenPortReturnsData() {
        MfgBoardView data = new MfgBoardView();
        data.setDegraded(true);
        data.setMessage("上游残留标识");
        data.setGeneratedAt("2026-11-19T10:00:00");
        MfgBoardView.Order order = new MfgBoardView.Order();
        order.setOrderNo("MO-0001");
        order.setPlannedQuantity(new BigDecimal("100"));
        order.setCompletedQuantity(new BigDecimal("60"));
        data.setOrders(List.of(order));
        when(manufacturingBoardPort.fetch(1L, 50)).thenReturn(Optional.of(data));

        MfgBoardView view = service.manufacturingBoard(1L, null);

        assertFalse(view.isDegraded());
        assertNull(view.getMessage());
        assertEquals("2026-11-19T10:00:00", view.getGeneratedAt());
        assertEquals(1, view.getOrders().size());
        assertEquals("MO-0001", view.getOrders().get(0).getOrderNo());
        assertEquals(0, new BigDecimal("60").compareTo(view.getOrders().get(0).getCompletedQuantity()));
    }

    @Test
    @DisplayName("端口无数据时降级且不返回任何数量")
    void shouldDegradeWhenPortReturnsEmpty() {
        when(manufacturingBoardPort.fetch(1L, 50)).thenReturn(Optional.empty());

        MfgBoardView view = service.manufacturingBoard(1L, null);

        assertTrue(view.isDegraded());
        assertTrue(view.getMessage().contains("降级"));
        assertTrue(view.getOrders().isEmpty());
        assertTrue(view.getStocks().isEmpty());
        assertNull(view.getGeneratedAt());
    }

    @Test
    @DisplayName("上限收敛：超上限取 200，非正数取默认 50")
    void shouldClampLimit() {
        when(manufacturingBoardPort.fetch(1L, 200)).thenReturn(Optional.empty());
        when(manufacturingBoardPort.fetch(1L, 50)).thenReturn(Optional.empty());

        service.manufacturingBoard(1L, 999);
        verify(manufacturingBoardPort).fetch(1L, 200);

        service.manufacturingBoard(1L, 0);
        verify(manufacturingBoardPort).fetch(1L, 50);
    }
}

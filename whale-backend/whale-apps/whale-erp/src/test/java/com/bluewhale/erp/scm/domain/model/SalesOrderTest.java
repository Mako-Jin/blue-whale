package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SalesOrder} 领域规则单元测试。
 *
 * @author BlueWhale
 */
class SalesOrderTest {

    @Test
    @DisplayName("部分发货后状态为部分发货")
    void shouldShipPartially() {
        SalesOrder order = approvedOrder();

        order.ship(Map.of(1, new BigDecimal("2")));

        assertEquals(ScmStatus.PARTIALLY_SHIPPED, order.getStatus());
        assertEquals(0, order.findLine(1).remaining().compareTo(new BigDecimal("3")));
        assertEquals(new BigDecimal("20.00"), order.getShippedAmount());
    }

    @Test
    @DisplayName("全部发货后状态为已发货")
    void shouldShipFully() {
        SalesOrder order = approvedOrder();

        order.ship(Map.of(1, new BigDecimal("5")));

        assertEquals(ScmStatus.SHIPPED, order.getStatus());
    }

    @Test
    @DisplayName("超发拒绝")
    void shouldRejectOverShip() {
        SalesOrder order = approvedOrder();

        BizException exception = assertThrows(BizException.class,
                () -> order.ship(Map.of(1, new BigDecimal("9"))));

        assertEquals(ErpErrorCode.SHIP_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("未审核不可发货")
    void shouldRejectShipBeforeApprove() {
        SalesOrder order = draftOrder();

        BizException exception = assertThrows(BizException.class,
                () -> order.ship(Map.of(1, new BigDecimal("1"))));

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("已发货不可取消")
    void shouldRejectCancelAfterShipment() {
        SalesOrder order = approvedOrder();
        order.ship(Map.of(1, new BigDecimal("5")));

        BizException exception = assertThrows(BizException.class, order::cancel);

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造草稿订单。
     *
     * @return 销售订单
     */
    private SalesOrder draftOrder() {
        SalesOrder order = new SalesOrder();
        order.setStatus(ScmStatus.DRAFT);
        SalesOrderLine line = new SalesOrderLine();
        line.setLineNo(1);
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("10"));
        order.setLines(List.of(line));
        order.calculateTotal();
        return order;
    }

    /**
     * 构造已审核订单。
     *
     * @return 销售订单
     */
    private SalesOrder approvedOrder() {
        SalesOrder order = draftOrder();
        order.approve();
        return order;
    }
}

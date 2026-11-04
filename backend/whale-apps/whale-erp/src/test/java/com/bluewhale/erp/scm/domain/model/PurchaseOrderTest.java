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
 * {@link PurchaseOrder} 领域规则单元测试。
 *
 * @author BlueWhale
 */
class PurchaseOrderTest {

    @Test
    @DisplayName("计算订单总额并校验至少一条分录")
    void shouldCalculateTotal() {
        PurchaseOrder order = draftOrder();

        order.calculateTotal();

        assertEquals(new BigDecimal("50.00"), order.getTotalAmount());
        assertEquals(0, order.getReceivedAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("空分录拒绝建单")
    void shouldRejectEmptyLines() {
        PurchaseOrder order = new PurchaseOrder();

        BizException exception = assertThrows(BizException.class, order::calculateTotal);

        assertEquals(ErpErrorCode.VOUCHER_LINE_EMPTY, exception.getCode());
    }

    @Test
    @DisplayName("草稿审核后变为已审核，重复审核拒绝")
    void shouldApproveOnce() {
        PurchaseOrder order = draftOrder();
        order.calculateTotal();

        order.approve();

        assertEquals(ScmStatus.APPROVED, order.getStatus());
        BizException exception = assertThrows(BizException.class, order::approve);
        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("部分收货后状态为部分收货，剩余数量正确")
    void shouldReceivePartially() {
        PurchaseOrder order = approvedOrder();

        order.receive(Map.of(1, new BigDecimal("2")));

        assertEquals(ScmStatus.PARTIALLY_RECEIVED, order.getStatus());
        assertEquals(0, order.findLine(1).remaining().compareTo(new BigDecimal("3")));
        assertEquals(new BigDecimal("20.00"), order.getReceivedAmount());
    }

    @Test
    @DisplayName("全部收货后状态为已收货")
    void shouldReceiveFully() {
        PurchaseOrder order = approvedOrder();

        order.receive(Map.of(1, new BigDecimal("5")));

        assertEquals(ScmStatus.RECEIVED, order.getStatus());
        assertEquals(0, order.findLine(1).remaining().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("超收拒绝")
    void shouldRejectOverReceive() {
        PurchaseOrder order = approvedOrder();

        BizException exception = assertThrows(BizException.class,
                () -> order.receive(Map.of(1, new BigDecimal("6"))));

        assertEquals(ErpErrorCode.RECEIVE_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("草稿可取消，已收货不可取消")
    void shouldCancelOnlyBeforeReceipt() {
        PurchaseOrder order = draftOrder();
        order.calculateTotal();
        order.approve();
        order.receive(Map.of(1, new BigDecimal("5")));

        BizException exception = assertThrows(BizException.class, order::cancel);

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造草稿订单（一行 5 件、单价 10）。
     *
     * @return 采购订单
     */
    private PurchaseOrder draftOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(ScmStatus.DRAFT);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setLineNo(1);
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("10"));
        order.setLines(List.of(line));
        return order;
    }

    /**
     * 构造已审核订单。
     *
     * @return 采购订单
     */
    private PurchaseOrder approvedOrder() {
        PurchaseOrder order = draftOrder();
        order.calculateTotal();
        order.approve();
        return order;
    }
}

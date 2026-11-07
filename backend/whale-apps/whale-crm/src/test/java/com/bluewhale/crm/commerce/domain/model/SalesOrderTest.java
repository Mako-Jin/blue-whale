package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SalesOrder} 状态机单元测试。
 *
 * @author BlueWhale
 */
class SalesOrderTest {

    @Test
    @DisplayName("提交校验：汇总行金额并锁定物料编码")
    void shouldValidateForSubmit() {
        SalesOrder order = draftOrder();

        order.validateForSubmit();

        assertEquals(new BigDecimal("20.00"), order.getTotalAmount());
    }

    @Test
    @DisplayName("提交校验：物料编码缺失时拒绝")
    void shouldRejectSubmitWithoutItemCode() {
        SalesOrder order = draftOrder();
        order.getLines().get(0).setItemCode(null);

        BizException exception = assertThrows(BizException.class, order::validateForSubmit);

        assertEquals(CrmErrorCode.ORDER_LINE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("提交校验：非草稿或已驳回状态拒绝")
    void shouldRejectSubmitInWrongStatus() {
        SalesOrder order = draftOrder();
        order.setStatus(SalesOrderStatus.APPROVED);

        BizException exception = assertThrows(BizException.class, order::validateForSubmit);

        assertEquals(CrmErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("下发失败：累计尝试次数并截断错误原因")
    void shouldMarkPushFailed() {
        SalesOrder order = draftOrder();
        order.setStatus(SalesOrderStatus.APPROVED);
        order.setPushAttempts(1);

        order.markPushFailed("x".repeat(600));

        assertEquals(SalesOrderStatus.PUSH_FAILED, order.getStatus());
        assertEquals(2, order.getPushAttempts());
        assertEquals(512, order.getLastPushError().length());
    }

    @Test
    @DisplayName("下发中：禁止取消")
    void shouldRejectCancelWhenPushing() {
        SalesOrder order = draftOrder();
        order.setStatus(SalesOrderStatus.PUSHING);

        BizException exception = assertThrows(BizException.class, order::cancel);

        assertEquals(CrmErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("对账采纳：转为已下发并回写 ERP 单号")
    void shouldAdoptErpOrder() {
        SalesOrder order = draftOrder();
        order.setStatus(SalesOrderStatus.PUSH_FAILED);
        order.setLastPushError("timeout");

        order.adoptErpOrder("ERP-SO-1", 66L, "DRAFT", LocalDateTime.now());

        assertEquals(SalesOrderStatus.PUSHED, order.getStatus());
        assertEquals("ERP-SO-1", order.getErpOrderNo());
        assertEquals(66L, order.getErpOrderId());
        assertNull(order.getLastPushError());
    }

    @Test
    @DisplayName("取消：重复取消保持已取消状态")
    void shouldKeepCancelledIdempotent() {
        SalesOrder order = draftOrder();

        order.cancel();
        order.cancel();

        assertEquals(SalesOrderStatus.CANCELLED, order.getStatus());
    }

    /**
     * 构造草稿订单。
     *
     * @return 订单
     */
    private SalesOrder draftOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-20261107-0001");
        order.setCustomerId(21L);
        order.setCustomerCode("CUS-001");
        order.setCurrency("CNY");
        order.setStatus(SalesOrderStatus.DRAFT);
        SalesOrderLine line = new SalesOrderLine();
        line.setLineNo(1);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("10"));
        order.getLines().add(line);
        return order;
    }
}

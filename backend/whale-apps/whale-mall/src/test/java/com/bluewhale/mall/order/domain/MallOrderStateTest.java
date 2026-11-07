package com.bluewhale.mall.order.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单状态机与金额计算测试。
 *
 * @author BlueWhale
 */
class MallOrderStateTest {

    /**
     * 金额由明细重算，且小计按单价 × 数量保留两位。
     */
    @Test
    @DisplayName("金额由明细重算，件数与行数同步更新")
    void recalculateFromItems() {
        MallOrder order = order(OrderStatus.PENDING_PAYMENT);
        order.setItems(new ArrayList<>(List.of(item("19.90", 3), item("5.15", 2))));
        order.recalculate();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("70.00");
        assertThat(order.getTotalQuantity()).isEqualTo(5);
        assertThat(order.getItemCount()).isEqualTo(2);
    }

    /**
     * 明细为空时金额为 0，必须拒绝（不允许 0 元订单落库）。
     */
    @Test
    @DisplayName("空明细不能算出一张 0 元订单")
    void emptyItemsRejected() {
        MallOrder order = order(OrderStatus.PENDING_PAYMENT);
        order.setItems(new ArrayList<>());
        assertThatThrownBy(order::recalculate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_AMOUNT_INVALID));
    }

    /**
     * 待支付 → 已支付 → 已发货 → 已完成。
     */
    @Test
    @DisplayName("正常流转：待支付 → 已支付 → 已发货 → 已完成")
    void happyPath() {
        MallOrder order = order(OrderStatus.PENDING_PAYMENT);
        order.pay("PAY-1", null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPayNo()).isEqualTo("PAY-1");
        order.ship(null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        order.complete(null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getStatus().isFinal()).isTrue();
    }

    /**
     * 已支付订单不能取消（退款是独立流程）。
     */
    @Test
    @DisplayName("已支付订单不允许取消")
    void paidOrderCannotBeCancelled() {
        MallOrder order = order(OrderStatus.PAID);
        assertThatThrownBy(() -> order.cancel("不想要了", null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_STATE_CONFLICT));
    }

    /**
     * 未支付不能发货，未发货不能完成。
     */
    @Test
    @DisplayName("跨状态跳步被拒绝")
    void illegalTransitionsRejected() {
        MallOrder pending = order(OrderStatus.PENDING_PAYMENT);
        assertThatThrownBy(() -> pending.ship(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> pending.complete(null)).isInstanceOf(BizException.class);
        MallOrder paid = order(OrderStatus.PAID);
        assertThatThrownBy(() -> paid.complete(null)).isInstanceOf(BizException.class);
    }

    /**
     * 取消后为终态，不能再支付。
     */
    @Test
    @DisplayName("已取消订单不能再支付")
    void cancelledOrderCannotBePaid() {
        MallOrder order = order(OrderStatus.PENDING_PAYMENT);
        order.cancel("买家取消", null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus().isFinal()).isTrue();
        assertThatThrownBy(() -> order.pay("PAY-2", null)).isInstanceOf(BizException.class);
    }

    /**
     * 支付必须带支付单号。
     */
    @Test
    @DisplayName("支付单号缺失被拒绝")
    void payRequiresPayNo() {
        MallOrder order = order(OrderStatus.PENDING_PAYMENT);
        assertThatThrownBy(() -> order.pay(" ", null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.PAY_CALLBACK_INVALID));
    }

    /**
     * 构造订单。
     *
     * @param status 状态
     * @return 订单
     */
    private MallOrder order(OrderStatus status) {
        MallOrder order = new MallOrder();
        order.setOrderNo("MO20261106000001");
        order.setBuyerUserId(9L);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.ZERO);
        return order;
    }

    /**
     * 构造明细。
     *
     * @param unitPrice 单价
     * @param quantity 数量
     * @return 明细
     */
    private OrderItem item(String unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setSkuId(1L);
        item.setSkuCode("SKU-1");
        item.setSpuName("测试商品");
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        return item;
    }
}

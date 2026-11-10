package com.bluewhale.mall.order.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        assertThat(order.ship(null)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        order.complete(null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getStatus().isFinal()).isTrue();
    }

    /**
     * 拆包再发货：订单已是已发货时幂等，不抛错也不重写发货时间。
     */
    @Test
    @DisplayName("重复发货幂等且保留首次发货时间")
    void shipIsIdempotent() {
        MallOrder order = order(OrderStatus.PAID);
        assertThat(order.ship(null)).isTrue();
        LocalDateTime first = order.getShipTime();

        assertThat(order.ship(null)).isFalse();

        assertThat(order.getShipTime()).isEqualTo(first);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /**
     * 发货单全部作废后退回待发货。
     */
    @Test
    @DisplayName("全部发货单作废后退回待发货")
    void unShipRevertsToPaid() {
        MallOrder order = order(OrderStatus.PAID);
        order.ship(null);

        order.unShip();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getShipTime()).isNull();
        assertThatThrownBy(() -> order(OrderStatus.COMPLETED).unShip())
                .isInstanceOf(BizException.class);
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
    @Test
    @DisplayName("整单退款进入 REFUNDING，期间不可发货")
    void refundPath() {
        MallOrder order = order(OrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("39.80"));
        order.requestRefund();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDING);
        assertThat(order.getStatus().canShip()).isFalse();
        assertThat(order.getStatus().isFinal()).isFalse();
    }

    @Test
    @DisplayName("退款范围：已支付/已发货/已完成可退，待支付与已取消不可退")
    void refundScope() {
        assertThat(OrderStatus.PAID.canRefund()).isTrue();
        assertThat(OrderStatus.SHIPPED.canRefund()).isTrue();
        assertThat(OrderStatus.COMPLETED.canRefund()).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canRefund()).isFalse();
        assertThat(OrderStatus.CANCELLED.canRefund()).isFalse();
        assertThat(OrderStatus.REFUNDING.canRefund()).isFalse();
        assertThatThrownBy(() -> order(OrderStatus.PENDING_PAYMENT).requestRefund())
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_STATE_CONFLICT));
    }

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

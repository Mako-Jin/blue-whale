package com.bluewhale.mall.order.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单计价与分摊（Wave 5.97）。
 *
 * <p>这一组断言守的是一个恒等式：{@code total = goods - discount + freight}，
 * 以及它派生出的两条：各行分摊之和必须等于订单优惠/运费（否则对账查不出原因），
 * 各行实付之和必须等于应付（否则退款会退多或退少）。</p>
 *
 * @author BlueWhale
 */
class MallOrderPricingTest {

    /** 无券无运费时，商品总额与应付相等，分摊全为 0 */
    @Test
    @DisplayName("无优惠无运费：应付等于商品总额")
    void plainOrder() {
        MallOrder order = order(item(new BigDecimal("60.00"), 1), item(new BigDecimal("20.00"), 2));

        order.recalculate();

        assertThat(order.getGoodsAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getFreightAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getTotalQuantity()).isEqualTo(3);
        assertThat(order.getItems()).allSatisfy(line -> {
            assertThat(line.getShareDiscount()).isEqualByComparingTo("0.00");
            assertThat(line.getShareFreight()).isEqualByComparingTo("0.00");
        });
    }

    /** 满减券按行小计比例分摊 */
    @Test
    @DisplayName("优惠按行小计比例分摊")
    void discountSharedByAmount() {
        MallOrder order = order(item(new BigDecimal("60.00"), 1), item(new BigDecimal("20.00"), 2));

        order.recalculate(new BigDecimal("20.00"), null);

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("80.00");
        assertThat(order.getItems().get(0).getShareDiscount()).isEqualByComparingTo("12.00");
        assertThat(order.getItems().get(1).getShareDiscount()).isEqualByComparingTo("8.00");
    }

    /** 三行等额、优惠除不尽时，最后一行吃掉尾差，合计严格等于优惠金额 */
    @Test
    @DisplayName("分摊尾差由末行承担，合计不差钱")
    void allocationTailGoesToLastLine() {
        MallOrder order = order(item(new BigDecimal("10.00"), 1),
                item(new BigDecimal("10.00"), 1), item(new BigDecimal("10.00"), 1));

        order.recalculate(new BigDecimal("10.00"), null);

        BigDecimal shared = order.getItems().stream()
                .map(OrderItem::getShareDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shared).isEqualByComparingTo("10.00");
        assertThat(order.getItems().get(0).getShareDiscount()).isEqualByComparingTo("3.33");
        assertThat(order.getItems().get(1).getShareDiscount()).isEqualByComparingTo("3.33");
        assertThat(order.getItems().get(2).getShareDiscount()).isEqualByComparingTo("3.34");
    }

    /** 运费同样分摊到行：退一件时要能算出该退多少运费 */
    @Test
    @DisplayName("运费按行小计比例分摊")
    void freightSharedByAmount() {
        MallOrder order = order(item(new BigDecimal("60.00"), 1), item(new BigDecimal("20.00"), 2));

        order.recalculate(new BigDecimal("20.00"), new BigDecimal("12.00"));

        assertThat(order.getFreightAmount()).isEqualByComparingTo("12.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("92.00");
        assertThat(order.getItems().get(0).getShareFreight()).isEqualByComparingTo("7.20");
        assertThat(order.getItems().get(1).getShareFreight()).isEqualByComparingTo("4.80");
        BigDecimal netSum = order.getItems().stream()
                .map(OrderItem::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(netSum).isEqualByComparingTo(order.getTotalAmount());
    }

    /** 优惠不能把应付打到 0：支付侧 amount 必须大于 0 */
    @Test
    @DisplayName("优惠封顶到商品总额减 0.01")
    void discountCappedToOneCentPayable() {
        MallOrder order = order(item(new BigDecimal("100.00"), 1));

        order.recalculate(new BigDecimal("200.00"), null);

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("99.99");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.01");
    }

    /** 负数优惠与负数运费按 0 处理，不允许通过负值把应付抬高或压低 */
    @Test
    @DisplayName("负数优惠与运费按 0 处理")
    void negativeValuesIgnored() {
        MallOrder order = order(item(new BigDecimal("100.00"), 1));

        order.recalculate(new BigDecimal("-10.00"), new BigDecimal("-5.00"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getFreightAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("100.00");
    }

    /** 行实付额 = 小计 - 分摊优惠 + 分摊运费，可退金额再减去已退 */
    @Test
    @DisplayName("行实付额与可退金额")
    void lineNetAndRefundableAmount() {
        MallOrder order = order(item(new BigDecimal("60.00"), 1), item(new BigDecimal("20.00"), 2));
        order.recalculate(new BigDecimal("20.00"), new BigDecimal("12.00"));
        OrderItem first = order.getItems().get(0);

        assertThat(first.netAmount()).isEqualByComparingTo("55.20");
        assertThat(first.refundableAmount()).isEqualByComparingTo("55.20");

        first.setRefundedAmount(new BigDecimal("20.00"));
        assertThat(first.refundableAmount()).isEqualByComparingTo("35.20");
    }

    /** 老订单没有分摊数据（列为 null）时不能 NPE */
    @Test
    @DisplayName("分摊字段为空时按 0 处理")
    void nullSharesTreatedAsZero() {
        OrderItem item = item(new BigDecimal("30.00"), 1);
        item.setAmount(new BigDecimal("30.00"));

        assertThat(item.netAmount()).isEqualByComparingTo("30.00");
    }

    /** 明细价格或数量非法直接拒绝，而不是算出一个空订单 */
    @Test
    @DisplayName("非法明细拒绝计价")
    void invalidLinesRejected() {
        MallOrder order = order(item(null, 1));

        assertThatThrownBy(order::recalculate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_AMOUNT_INVALID));
    }

    /**
     * 构造只含明细的订单。
     *
     * @param items 明细
     * @return 订单
     */
    private MallOrder order(OrderItem... items) {
        MallOrder order = new MallOrder();
        for (OrderItem item : items) {
            order.getItems().add(item);
        }
        return order;
    }

    /**
     * 构造明细。
     *
     * @param unitPrice 单价
     * @param quantity  数量
     * @return 明细
     */
    private OrderItem item(BigDecimal unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setSkuId(1L);
        item.setSpuName("测试商品");
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        return item;
    }
}

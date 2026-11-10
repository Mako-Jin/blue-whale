package com.bluewhale.mall.order.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponScope;
import com.bluewhale.mall.coupon.domain.model.CouponType;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderCoupon;
import com.bluewhale.mall.order.domain.model.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 券 + 活动 + 会员价组合计价的订单域口径。
 *
 * <p>守三件事：</p>
 * <ol>
 * <li>对账口径：{@code list_amount = Σ原价×数量}、
 * {@code activity_discount = list_amount - goods_amount}——活动与会员让利
 * 必须能单独对账，不能混进券优惠里说不清；</li>
 * <li>商品券（scope=SKU）优惠只摊命中行、门槛只看命中行小计，
 * 与品类券同一套分摊纪律；</li>
 * <li>总恒等式在任何组合下不变：{@code total = goods - coupon + freight}。</li>
 * </ol>
 *
 * @author BlueWhale
 */
class MallOrderComboPricingTest {

    /** 活动价 + 会员价让利进 list_amount/activity_discount，成交价进 goods_amount */
    @Test
    @DisplayName("组合计价落原价合计与让利合计")
    void comboPricingComputesListAndActivityDiscount() {
        // 行 1：原价 100，会员价 80 成交；行 2：原价即成交价 20
        MallOrder order = order(
                item(101L, "100.00", "80.00", 1),
                item(102L, null, "20.00", 1));

        order.recalculate();

        assertThat(order.getListAmount()).isEqualByComparingTo("120.00");
        assertThat(order.getGoodsAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getActivityDiscount()).isEqualByComparingTo("20.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("100.00");
    }

    /** 老数据没有原价快照：list_amount 恒等 goods_amount，让利为 0，不破坏对账 */
    @Test
    @DisplayName("缺失原价快照时让利口径退化为零")
    void legacyLinesKeepListIdentity() {
        MallOrder order = order(item(101L, null, "30.00", 2));

        order.recalculate();

        assertThat(order.getListAmount()).isEqualByComparingTo(order.getGoodsAmount());
        assertThat(order.getActivityDiscount()).isEqualByComparingTo("0.00");
    }

    /** 商品券优惠只摊命中行，未命中行分摊为 0 */
    @Test
    @DisplayName("商品券优惠只摊命中 SKU 行")
    void skuCouponOnlyHitsMatchedLines() {
        MallOrder order = order(item(101L, null, "60.00", 1), item(102L, null, "40.00", 1));

        order.recalculateWithCoupons(List.of(
                skuCoupon("SKU10", fullReduction("50.00", "10.00"), "101")), null);

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getItems().get(0).getShareDiscount()).isEqualByComparingTo("10.00");
        assertThat(order.getItems().get(1).getShareDiscount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("90.00");
        assertShareInvariant(order);
    }

    /** 商品券门槛按命中行小计：整单够、命中行不够照样拒绝 */
    @Test
    @DisplayName("商品券门槛按命中行小计判定")
    void skuCouponThresholdUsesMatchedSubtotal() {
        MallOrder order = order(item(101L, null, "60.00", 1), item(102L, null, "40.00", 1));

        // 门槛 80 看命中行 101 的小计 60，整单 100 不凑数
        assertThatThrownBy(() -> order.recalculateWithCoupons(List.of(
                skuCoupon("SKU80", fullReduction("80.00", "10.00"), "101")), null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_THRESHOLD_NOT_MET));
    }

    /** 活动/会员价 + 商品券 + 运费全组合：三口径与恒等式同时成立 */
    @Test
    @DisplayName("活动价与商品券叠加守恒等式")
    void activityLineWithSkuCouponKeepsIdentity() {
        // 行 1：原价 100、活动后 80；行 2：原价即成交价 20
        MallOrder order = order(
                item(101L, "100.00", "80.00", 1),
                item(102L, null, "20.00", 1));

        order.recalculateWithCoupons(List.of(
                skuCoupon("SKU10", fullReduction("50.00", "10.00"), "101")),
                new BigDecimal("5.00"));

        assertThat(order.getListAmount()).isEqualByComparingTo("120.00");
        assertThat(order.getActivityDiscount()).isEqualByComparingTo("20.00");
        assertThat(order.getGoodsAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getFreightAmount()).isEqualByComparingTo("5.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("95.00");
        assertShareInvariant(order);
    }

    /**
     * 分摊不变量：Σ行分摊优惠 = 订单优惠额，且每行分摊不超过行小计。
     *
     * @param order 订单
     */
    private void assertShareInvariant(MallOrder order) {
        BigDecimal shared = order.getItems().stream()
                .map(OrderItem::getShareDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shared).isEqualByComparingTo(order.getDiscountAmount());
        for (OrderItem item : order.getItems()) {
            assertThat(item.getShareDiscount().compareTo(item.getAmount())).isLessThanOrEqualTo(0);
        }
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
     * 构造明细（含 SKU 与可选原价快照）。
     *
     * @param skuId     SKU ID
     * @param listPrice 原价（null 模拟老数据）
     * @param unitPrice 成交单价
     * @param quantity  数量
     * @return 明细
     */
    private OrderItem item(Long skuId, String listPrice, String unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setSkuId(skuId);
        item.setSpuName("测试商品");
        item.setListPrice(listPrice == null ? null : new BigDecimal(listPrice));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        return item;
    }

    /**
     * 构造满减券模板并校验。
     *
     * @param threshold 门槛
     * @param face      面额
     * @return 模板
     */
    private Coupon fullReduction(String threshold, String face) {
        Coupon coupon = new Coupon();
        coupon.setCouponCode("C" + threshold + face);
        coupon.setCouponName("满减券");
        coupon.setCouponType(CouponType.FULL_REDUCTION);
        coupon.setThresholdAmount(new BigDecimal(threshold));
        coupon.setFaceAmount(new BigDecimal(face));
        coupon.setPerUserLimit(1);
        coupon.setTotalQty(100);
        coupon.setIssuedQty(0);
        coupon.setValidFrom(LocalDateTime.of(2026, 11, 1, 0, 0));
        coupon.setValidTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        coupon.validate();
        return coupon;
    }

    /**
     * 模板转订单用券明细（SKU 范围快照口径与 OrderAppServiceImpl 一致）。
     *
     * @param code     券编码
     * @param template 模板
     * @param skuIds   适用 SKU ID（逗号分隔）
     * @return 订单用券明细
     */
    private OrderCoupon skuCoupon(String code, Coupon template, String skuIds) {
        OrderCoupon record = new OrderCoupon();
        record.setCouponUserId(55L);
        record.setCouponId(9L);
        record.setCouponCode(code);
        record.setCouponName(template.getCouponName());
        record.setCouponType(template.getCouponType().name());
        record.setScope(CouponScope.SKU.name());
        record.setSkuIds(skuIds);
        record.setRule(template.rule());
        return record;
    }
}

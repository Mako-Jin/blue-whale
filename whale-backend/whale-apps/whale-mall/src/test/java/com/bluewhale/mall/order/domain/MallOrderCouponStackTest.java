package com.bluewhale.mall.order.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponRule;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 多券叠加计价与分摊。
 *
 * <p>守的恒等式与单券计价相同：{@code total = goods - discount + freight}，
 * 且 Σ各行分摊优惠 = Σ各券生效优惠 = 订单 discount_amount。多出来的一层是
 * 「券自身的优惠要能按券对账」——每张券分到行上的钱加起来必须等于该券的
 * discountAmount，否则退款时说不清某张券该退多少。</p>
 *
 * @author BlueWhale
 */
class MallOrderCouponStackTest {

    /** 两张满减券叠加：门槛各看整单、优惠累加、分摊逐券按比例落行 */
    @Test
    @DisplayName("两张满减券叠加并累加分摊")
    void twoCouponsStackAndAllocate() {
        MallOrder order = order(item("60.00", 1, 801L), item("20.00", 1, 802L), item("20.00", 1, 803L));

        order.recalculateWithCoupons(List.of(
                couponRecord("A100", fullReduction("100.00", "20.00"), null),
                couponRecord("B050", fullReduction("50.00", "10.00"), null)),
                new BigDecimal("12.00"));

        assertThat(order.getGoodsAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("30.00");
        assertThat(order.getFreightAmount()).isEqualByComparingTo("12.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("82.00");
        // 券 A 分摊：60/20/20 按比例 12/4/4；券 B：6/2/2；累加后 18/6/6
        assertThat(order.getItems().get(0).getShareDiscount()).isEqualByComparingTo("18.00");
        assertThat(order.getItems().get(1).getShareDiscount()).isEqualByComparingTo("6.00");
        assertThat(order.getItems().get(2).getShareDiscount()).isEqualByComparingTo("6.00");
        assertThat(order.getCoupons()).hasSize(2);
        assertThat(order.getCoupons().get(0).getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getCoupons().get(1).getDiscountAmount()).isEqualByComparingTo("10.00");
        assertShareInvariant(order);
    }

    /** 品类券的优惠只摊命中行，未命中行分摊为 0 */
    @Test
    @DisplayName("品类券优惠只摊命中类目行")
    void categoryCouponOnlyHitsCategoryLines() {
        MallOrder order = order(item("60.00", 1, 901L), item("40.00", 1, 802L));

        order.recalculateWithCoupons(List.of(
                couponRecord("CAT01", fullReduction("50.00", "10.00"), List.of(901L))), null);

        assertThat(order.getGoodsAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getItems().get(0).getShareDiscount()).isEqualByComparingTo("10.00");
        assertThat(order.getItems().get(1).getShareDiscount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("90.00");
        assertShareInvariant(order);
    }

    /** 品类券门槛按品类小计：整单够、品类不够照样拒绝 */
    @Test
    @DisplayName("品类券门槛按命中类目小计判定")
    void categoryThresholdUsesCategorySubtotal() {
        MallOrder order = order(item("60.00", 1, 901L), item("40.00", 1, 802L));

        List<OrderCoupon> coupons = List.of(
                couponRecord("CAT80", fullReduction("80.00", "10.00"), List.of(901L)));

        // 整单 100 够 80，但品类小计只有 60：其他商品凑单不能替品类券抬门槛
        assertThatThrownBy(() -> order.recalculateWithCoupons(coupons, null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_THRESHOLD_NOT_MET));
    }

    /** 品类券与全场券叠加：全场券摊全部行，品类券只摊命中行，行分摊累加 */
    @Test
    @DisplayName("品类券与全场券叠加分摊")
    void categoryAndAllScopeStack() {
        MallOrder order = order(item("60.00", 1, 901L), item("40.00", 1, 802L));

        order.recalculateWithCoupons(List.of(
                couponRecord("ALL01", fullReduction("0.00", "6.00"), null),
                couponRecord("CAT01", fullReduction("0.00", "4.00"), List.of(901L))),
                null);

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
        // 全场券 6 元按 60/40 摊 3.60/2.40；品类券 4 元全落在 901 行：3.60+4.00=7.60 / 2.40
        assertThat(order.getItems().get(0).getShareDiscount()).isEqualByComparingTo("7.60");
        assertThat(order.getItems().get(1).getShareDiscount()).isEqualByComparingTo("2.40");
        assertShareInvariant(order);
    }

    /** 叠加合计封顶：超出按比例缩减，尾差挂最后一张，Σ券优惠 = 封顶额 */
    @Test
    @DisplayName("多券合计优惠封顶并按比例缩减")
    void stackCappedAtGoodsMinusOneCent() {
        MallOrder order = order(item("50.00", 1, 801L), item("50.00", 1, 802L));

        order.recalculateWithCoupons(List.of(
                couponRecord("BIG1", fullReduction("0.00", "80.00"), null),
                couponRecord("BIG2", fullReduction("0.00", "80.00"), null)), null);

        // raw = 160 > cap = 99.99：前一张缩到 49.99（DOWN），末张 50.00
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("99.99");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.01");
        assertThat(order.getCoupons().get(0).getDiscountAmount()).isEqualByComparingTo("49.99");
        assertThat(order.getCoupons().get(1).getDiscountAmount()).isEqualByComparingTo("50.00");
        assertShareInvariant(order);
    }

    /** 用券明细缺计价规则直接拒绝：规则是计价的输入，丢了就别算 */
    @Test
    @DisplayName("用券明细缺规则拒绝计价")
    void missingRuleRejected() {
        MallOrder order = order(item("50.00", 1, 801L));
        OrderCoupon broken = couponRecord("A100", fullReduction("0.00", "10.00"), null);
        broken.setRule(null);

        assertThatThrownBy(() -> order.recalculateWithCoupons(List.of(broken), null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_AMOUNT_INVALID));
    }

    /** 老口径兼容：单券 recalculate(discount, freight) 行为不变（AfterSale 仍在用） */
    @Test
    @DisplayName("单券老口径 recalculate 行为不变")
    void legacyRecalculateUnchanged() {
        MallOrder order = order(item("60.00", 1, 801L), item("20.00", 2, 802L));

        order.recalculate(new BigDecimal("20.00"), new BigDecimal("12.00"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getItems().get(0).getShareDiscount()).isEqualByComparingTo("12.00");
        assertThat(order.getItems().get(1).getShareDiscount()).isEqualByComparingTo("8.00");
        assertThat(order.getCoupons()).isEmpty();
    }

    /**
     * 分摊不变量：Σ行分摊优惠 = 订单优惠额，且每行分摊不超过行小计
     * （超额意味着退款时会出现「退得多于付」）。
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
     * 构造明细。
     *
     * @param unitPrice  单价
     * @param quantity   数量
     * @param categoryId 类目 ID（可空，模拟老数据）
     * @return 明细
     */
    private OrderItem item(String unitPrice, int quantity, Long categoryId) {
        OrderItem item = new OrderItem();
        item.setSkuId(1L);
        item.setSpuName("测试商品");
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        item.setCategoryId(categoryId);
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
     * 模板转订单用券明细（快照口径与 OrderAppServiceImpl 一致）。
     *
     * @param code        券编码
     * @param template    模板
     * @param categoryIds 适用类目（null 表示沿用模板范围）
     * @return 订单用券明细
     */
    private OrderCoupon couponRecord(String code, Coupon template, List<Long> categoryIds) {
        OrderCoupon record = new OrderCoupon();
        record.setCouponUserId(55L);
        record.setCouponId(9L);
        record.setCouponCode(code);
        record.setCouponName(template.getCouponName());
        record.setCouponType(template.getCouponType().name());
        List<Long> scoped = categoryIds == null ? template.getCategoryIds() : categoryIds;
        record.setScope(scoped == null ? CouponScope.ALL.name() : CouponScope.CATEGORY.name());
        record.setCategoryIds(scoped == null ? null
                : scoped.stream().map(String::valueOf).collect(Collectors.joining(",")));
        record.setRule(template.rule());
        return record;
    }
}

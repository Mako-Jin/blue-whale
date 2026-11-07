package com.bluewhale.mall.coupon.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponStatus;
import com.bluewhale.mall.coupon.domain.model.CouponType;
import com.bluewhale.mall.coupon.domain.model.CouponUsability;
import com.bluewhale.mall.coupon.domain.model.CouponUser;
import com.bluewhale.mall.coupon.domain.model.CouponUserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 优惠券领域规则（Wave 5.97）。
 *
 * @author BlueWhale
 */
class MallCouponTest {

    /** 满减券：达到门槛才减，减完不超过商品总额 */
    @Test
    @DisplayName("满减券按面额抵扣且不超过商品总额")
    void fullReductionDiscount() {
        Coupon coupon = fullReduction("100.00", "20.00");
        coupon.validate();

        assertThat(coupon.rule().meetsThreshold(new BigDecimal("99.99"))).isFalse();
        assertThat(coupon.rule().meetsThreshold(new BigDecimal("100.00"))).isTrue();
        assertThat(coupon.rule().discountFor(new BigDecimal("120.00"))).isEqualByComparingTo("20.00");
        // 面额大于商品总额时不能反着给钱
        assertThat(coupon.rule().discountFor(new BigDecimal("10.00"))).isEqualByComparingTo("10.00");
    }

    /** 折扣券按比例打折 */
    @Test
    @DisplayName("折扣券按折扣率计算优惠")
    void discountCoupon() {
        Coupon coupon = discount("0.8500", "1000.00");
        coupon.validate();

        assertThat(coupon.rule().discountFor(new BigDecimal("100.00"))).isEqualByComparingTo("15.00");
    }

    /** 折扣券必须封顶，否则高价订单上的优惠额没人敢认 */
    @Test
    @DisplayName("折扣券优惠受封顶金额约束")
    void discountCouponCapped() {
        Coupon coupon = discount("0.8000", "500.00");
        coupon.validate();

        assertThat(coupon.rule().discountFor(new BigDecimal("10000.00"))).isEqualByComparingTo("500.00");
    }

    /** 折扣率为 1（等于没打折）或越界都拒绝 */
    @Test
    @DisplayName("折扣率越界拒绝")
    void invalidDiscountRateRejected() {
        Coupon full = discount("1.0000", "100.00");
        assertThatThrownBy(full::validate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_INVALID));

        Coupon tiny = discount("0.0010", "100.00");
        assertThatThrownBy(tiny::validate).isInstanceOf(BizException.class);
    }

    /** 折扣券不设封顶直接拒绝 */
    @Test
    @DisplayName("折扣券缺少封顶金额拒绝")
    void discountWithoutCapRejected() {
        Coupon coupon = discount("0.8000", null);

        assertThatThrownBy(coupon::validate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getMessage())
                        .contains("封顶"));
    }

    /** 满减面额必须大于 0 */
    @Test
    @DisplayName("满减面额为 0 拒绝")
    void zeroFaceAmountRejected() {
        Coupon coupon = fullReduction("0.00", "0.00");

        assertThatThrownBy(coupon::validate).isInstanceOf(BizException.class);
    }

    /** 失效时间必须晚于生效时间 */
    @Test
    @DisplayName("有效期倒置拒绝")
    void invalidPeriodRejected() {
        Coupon coupon = fullReduction("0.00", "10.00");
        coupon.setValidFrom(LocalDateTime.of(2026, 11, 10, 0, 0));
        coupon.setValidTo(LocalDateTime.of(2026, 11, 1, 0, 0));

        assertThatThrownBy(coupon::validate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getMessage())
                        .contains("失效时间"));
    }

    /** 每人限领至少 1 张 */
    @Test
    @DisplayName("限领张数为 0 拒绝")
    void zeroPerUserLimitRejected() {
        Coupon coupon = fullReduction("0.00", "10.00");
        coupon.setPerUserLimit(0);

        assertThatThrownBy(coupon::validate).isInstanceOf(BizException.class);
    }

    /** 停用、过期、发完的券都不允许再领取 */
    @Test
    @DisplayName("不可领取的三种情形")
    void notClaimable() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);

        Coupon disabled = fullReduction("0.00", "10.00");
        disabled.setStatus(CouponStatus.DISABLED);
        assertThat(disabled.claimable(now)).isFalse();

        Coupon expired = fullReduction("0.00", "10.00");
        expired.setValidTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(expired.claimable(now)).isFalse();

        Coupon exhausted = fullReduction("0.00", "10.00");
        exhausted.setTotalQty(5);
        exhausted.setIssuedQty(5);
        assertThat(exhausted.claimable(now)).isFalse();

        Coupon ok = fullReduction("0.00", "10.00");
        assertThat(ok.claimable(now)).isTrue();
    }

    /** 用户券落快照：模板改面额后，已领出去的券金额不变 */
    @Test
    @DisplayName("用户券快照不随模板修改而变")
    void issuedCouponKeepsSnapshot() {
        Coupon template = fullReduction("100.00", "50.00");
        template.setId(9L);
        template.validate();
        CouponUser issued = CouponUser.issue(template, 7L, 1);

        template.setFaceAmount(new BigDecimal("5.00"));

        assertThat(issued.getFaceAmount()).isEqualByComparingTo("50.00");
        assertThat(issued.discountFor(new BigDecimal("200.00"))).isEqualByComparingTo("50.00");
        assertThat(issued.getStatus()).isEqualTo(CouponUserStatus.UNUSED);
    }

    /** 可用性判定的四个拒绝分支 */
    @Test
    @DisplayName("可用性判定：已用/过期/渠道不符/未达门槛")
    void usabilityReasons() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);
        CouponUser coupon = issued(fullReduction("100.00", "20.00"));

        assertThat(coupon.check(1L, new BigDecimal("100.00"), now)).isEqualTo(CouponUsability.USABLE);

        coupon.setStatus(CouponUserStatus.USED);
        assertThat(coupon.check(1L, new BigDecimal("100.00"), now)).isEqualTo(CouponUsability.USED);

        CouponUser expired = issued(fullReduction("100.00", "20.00"));
        expired.setValidTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(expired.check(1L, new BigDecimal("100.00"), now)).isEqualTo(CouponUsability.EXPIRED);

        CouponUser scoped = issued(fullReduction("100.00", "20.00"));
        scoped.setChannelId(2L);
        assertThat(scoped.check(1L, new BigDecimal("100.00"), now))
                .isEqualTo(CouponUsability.CHANNEL_MISMATCH);

        CouponUser low = issued(fullReduction("100.00", "20.00"));
        assertThat(low.check(1L, new BigDecimal("99.99"), now))
                .isEqualTo(CouponUsability.THRESHOLD_NOT_MET);
    }

    /** 核销后再核销要报错；回退后可以再次使用 */
    @Test
    @DisplayName("核销与回退闭环")
    void useAndRelease() {
        CouponUser coupon = issued(fullReduction("0.00", "10.00"));
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);

        coupon.markUsed("MO20261107000001", now);
        assertThat(coupon.getStatus()).isEqualTo(CouponUserStatus.USED);
        assertThat(coupon.getOrderNo()).isEqualTo("MO20261107000001");
        assertThatThrownBy(() -> coupon.markUsed("MO20261107000002", now))
                .isInstanceOf(BizException.class);

        coupon.release();
        assertThat(coupon.getStatus()).isEqualTo(CouponUserStatus.UNUSED);
        assertThat(coupon.getOrderNo()).isNull();
        assertThat(coupon.getUsedTime()).isNull();
    }

    /** 对未使用的券重复回退是空操作，不能把状态打回奇怪的值 */
    @Test
    @DisplayName("未使用的券回退是空操作")
    void releaseUnusedIsNoop() {
        CouponUser coupon = issued(fullReduction("0.00", "10.00"));

        coupon.release();

        assertThat(coupon.getStatus()).isEqualTo(CouponUserStatus.UNUSED);
    }

    /**
     * 构造满减券模板。
     *
     * @param threshold 门槛
     * @param face      面额
     * @return 模板
     */
    private Coupon fullReduction(String threshold, String face) {
        Coupon coupon = base();
        coupon.setCouponType(CouponType.FULL_REDUCTION);
        coupon.setThresholdAmount(new BigDecimal(threshold));
        coupon.setFaceAmount(new BigDecimal(face));
        return coupon;
    }

    /**
     * 构造折扣券模板。
     *
     * @param rate 折扣率
     * @param cap  封顶
     * @return 模板
     */
    private Coupon discount(String rate, String cap) {
        Coupon coupon = base();
        coupon.setCouponType(CouponType.DISCOUNT);
        coupon.setThresholdAmount(BigDecimal.ZERO);
        coupon.setDiscountRate(new BigDecimal(rate));
        coupon.setMaxDiscountAmount(cap == null ? null : new BigDecimal(cap));
        return coupon;
    }

    /**
     * 构造模板公共字段。
     *
     * @return 模板
     */
    private Coupon base() {
        Coupon coupon = new Coupon();
        coupon.setCouponCode("c100");
        coupon.setCouponName("满 100 减 20");
        coupon.setPerUserLimit(1);
        coupon.setTotalQty(100);
        coupon.setIssuedQty(0);
        coupon.setValidFrom(LocalDateTime.of(2026, 11, 1, 0, 0));
        coupon.setValidTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        return coupon;
    }

    /**
     * 把模板签发成一张用户券。
     *
     * @param template 模板
     * @return 用户券
     */
    private CouponUser issued(Coupon template) {
        template.setId(1L);
        template.validate();
        return CouponUser.issue(template, 7L, 1);
    }
}

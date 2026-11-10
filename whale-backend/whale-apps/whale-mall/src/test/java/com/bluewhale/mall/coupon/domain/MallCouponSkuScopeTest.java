package com.bluewhale.mall.coupon.domain;

import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponScope;
import com.bluewhale.mall.coupon.domain.model.CouponType;
import com.bluewhale.mall.coupon.domain.model.CouponUser;
import com.bluewhale.mall.coupon.domain.model.CouponUsability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商品券（scope=SKU）：绑定校验、订单命中判定与门槛分母口径。
 *
 * @author BlueWhale
 */
class MallCouponSkuScopeTest {

    /** 商品券必须绑定 SKU；绑定后清空渠道与类目 */
    @Test
    @DisplayName("商品券校验与范围归一")
    void skuScopeValidation() {
        Coupon coupon = fullReduction();
        coupon.setScope(CouponScope.SKU);
        coupon.setSkuIds(List.of(101L, 102L));
        coupon.setChannelId(1L);
        coupon.setCategoryIds(List.of(901L));
        coupon.validate();

        assertThat(coupon.getSkuIds()).containsExactly(101L, 102L);
        assertThat(coupon.getChannelId()).isNull();
        assertThat(coupon.getCategoryIds()).isNull();

        Coupon broken = fullReduction();
        broken.setScope(CouponScope.SKU);
        assertThatThrownBy(broken::validate)
                .isInstanceOf(com.bluewhale.common.core.exception.BizException.class);
    }

    /** 订单 SKU 命中商品券 → 可用；未命中 → SKU_NOT_MATCHED */
    @Test
    @DisplayName("商品券按订单 SKU 集合判定命中")
    void skuScopeMatching() {
        CouponUser userCoupon = issued();
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);

        assertThat(userCoupon.checkForOrder(1L, Set.of(), Set.of(101L, 999L), null, now))
                .isEqualTo(CouponUsability.USABLE);
        assertThat(userCoupon.checkForOrder(1L, Set.of(), Set.of(999L), null, now))
                .isEqualTo(CouponUsability.SKU_NOT_MATCHED);
        assertThat(userCoupon.checkForOrder(2L, Set.of(), Set.of(101L), null, now))
                .isEqualTo(CouponUsability.USABLE);
    }

    /** 商品券门槛分母是命中 SKU 行的小计：整单够、命中行不够照样拒绝 */
    @Test
    @DisplayName("商品券门槛按命中行小计判定")
    void skuThresholdUsesMatchedSubtotal() {
        CouponUser userCoupon = issued();
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);

        assertThat(userCoupon.checkForOrder(1L, Set.of(), Set.of(101L), new BigDecimal("80.00"), now))
                .isEqualTo(CouponUsability.USABLE);
        assertThat(userCoupon.checkForOrder(1L, Set.of(), Set.of(101L), new BigDecimal("79.99"), now))
                .isEqualTo(CouponUsability.THRESHOLD_NOT_MET);
    }

    /**
     * 构造满减券模板并校验（SKU 范围）。
     *
     * @return 模板
     */
    private Coupon fullReduction() {
        Coupon coupon = new Coupon();
        coupon.setCouponCode("SKU01");
        coupon.setCouponName("商品券");
        coupon.setCouponType(CouponType.FULL_REDUCTION);
        coupon.setThresholdAmount(new BigDecimal("80.00"));
        coupon.setFaceAmount(new BigDecimal("10.00"));
        coupon.setPerUserLimit(1);
        coupon.setTotalQty(100);
        coupon.setIssuedQty(0);
        coupon.setValidFrom(LocalDateTime.of(2026, 11, 1, 0, 0));
        coupon.setValidTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        coupon.validate();
        return coupon;
    }

    /**
     * 发放到用户的商品券（快照口径与 CouponUser.issue 一致）。
     *
     * @return 用户券
     */
    private CouponUser issued() {
        CouponUser coupon = new CouponUser();
        coupon.setCouponId(9L);
        coupon.setUserId(7L);
        coupon.setScope(CouponScope.SKU);
        coupon.setSkuIds(List.of(101L, 102L));
        coupon.setCouponType(CouponType.FULL_REDUCTION);
        coupon.setThresholdAmount(new BigDecimal("80.00"));
        coupon.setFaceAmount(new BigDecimal("10.00"));
        coupon.setValidFrom(LocalDateTime.of(2026, 11, 1, 0, 0));
        coupon.setValidTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        coupon.setStatus(com.bluewhale.mall.coupon.domain.model.CouponUserStatus.UNUSED);
        return coupon;
    }
}

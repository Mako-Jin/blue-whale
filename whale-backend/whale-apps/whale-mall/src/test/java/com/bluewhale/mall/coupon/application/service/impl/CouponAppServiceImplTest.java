package com.bluewhale.mall.coupon.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.coupon.application.command.CouponSaveCommand;
import com.bluewhale.mall.coupon.application.view.CouponUserView;
import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponScope;
import com.bluewhale.mall.coupon.domain.model.CouponType;
import com.bluewhale.mall.coupon.domain.model.CouponUser;
import com.bluewhale.mall.coupon.domain.model.CouponUserStatus;
import com.bluewhale.mall.coupon.domain.repository.CouponRepository;
import com.bluewhale.mall.coupon.domain.repository.CouponUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券应用服务（Wave 5.97）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponAppServiceImplTest {

    private static final MallActor BUYER = new MallActor(7L, "buyer", java.util.Set.of(), java.util.Set.of());

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUserRepository couponUserRepository;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private CouponAppServiceImpl couponAppService;

    @BeforeEach
    void setUp() {
        when(properties.getPageLimit()).thenReturn(100);
    }

    /** 首次领取：序号从 1 开始，占用发行量 */
    @Test
    @DisplayName("领券成功并占用发行量")
    void claimSucceeds() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(template()));
        when(couponUserRepository.countByUser(7L, 1L)).thenReturn(0);
        when(couponRepository.occupyIssuedQty(1L)).thenReturn(true);

        CouponUserView view = couponAppService.claim(1L, BUYER);

        assertThat(view.getCouponId()).isEqualTo(1L);
        assertThat(view.getStatus()).isEqualTo(CouponUserStatus.UNUSED.name());
        verify(couponUserRepository).insert(any(CouponUser.class));
    }

    /** 每人限领 1 张：第二张直接拒绝，不再碰发行量 */
    @Test
    @DisplayName("超出每人限领拒绝领取")
    void claimRejectsWhenLimitReached() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(template()));
        when(couponUserRepository.countByUser(7L, 1L)).thenReturn(1);

        assertThatThrownBy(() -> couponAppService.claim(1L, BUYER))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_LIMIT_EXCEEDED));
        verify(couponRepository, never()).occupyIssuedQty(anyLong());
    }

    /** 发行量占不到（已发完）同样拒绝 */
    @Test
    @DisplayName("券已发完拒绝领取")
    void claimRejectsWhenExhausted() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(template()));
        when(couponUserRepository.countByUser(7L, 1L)).thenReturn(0);
        when(couponRepository.occupyIssuedQty(1L)).thenReturn(false);

        assertThatThrownBy(() -> couponAppService.claim(1L, BUYER))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getMessage())
                        .contains("领完"));
    }

    /** 停用的券不再发放 */
    @Test
    @DisplayName("停用的券不可领取")
    void claimRejectsDisabled() {
        Coupon disabled = template();
        disabled.setStatus(com.bluewhale.mall.coupon.domain.model.CouponStatus.DISABLED);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> couponAppService.claim(1L, BUYER))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_STATE_INVALID));
    }

    /** 并发领同一张券撞唯一键：转成「领取过于频繁」，整笔事务回滚 */
    @Test
    @DisplayName("并发领券撞唯一键转业务异常")
    void claimConcurrentDuplicate() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(template()));
        when(couponUserRepository.countByUser(7L, 1L)).thenReturn(0);
        when(couponRepository.occupyIssuedQty(1L)).thenReturn(true);
        when(couponUserRepository.insert(any(CouponUser.class)))
                .thenThrow(new DuplicateKeyException("uk_mall_coupon_user"));

        assertThatThrownBy(() -> couponAppService.claim(1L, BUYER))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_LIMIT_EXCEEDED));
    }

    /** 用别人的券：不解释成「不存在」，直接说清归属 */
    @Test
    @DisplayName("他人的券不可用于本单")
    void requireUsableRejectsOtherOwner() {
        CouponUser other = coupon();
        other.setUserId(99L);
        when(couponUserRepository.findById(5L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> couponAppService.requireUsable(5L, 7L, 1L, null, new BigDecimal("200.00")))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_NOT_FOUND));
    }

    /** 已使用的券不能再用于新订单 */
    @Test
    @DisplayName("已使用的券拒绝复用")
    void requireUsableRejectsUsed() {
        CouponUser used = coupon();
        used.setStatus(CouponUserStatus.USED);
        when(couponUserRepository.findById(5L)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> couponAppService.requireUsable(5L, 7L, 1L, null, new BigDecimal("200.00")))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_STATE_INVALID));
    }

    /** 未达门槛 */
    @Test
    @DisplayName("未达门槛拒绝使用")
    void requireUsableRejectsThreshold() {
        when(couponUserRepository.findById(5L)).thenReturn(Optional.of(coupon()));

        assertThatThrownBy(() -> couponAppService.requireUsable(5L, 7L, 1L, null, new BigDecimal("50.00")))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_THRESHOLD_NOT_MET));
    }

    /** 门槛分母为空（推迟到计价阶段判定）：状态与范围通过即放行 */
    @Test
    @DisplayName("门槛分母为空时不判门槛")
    void requireUsableAllowsNullMatchedGoods() {
        when(couponUserRepository.findById(5L)).thenReturn(Optional.of(coupon()));

        CouponUser coupon = couponAppService.requireUsable(5L, 7L, 1L, null, null);

        assertThat(coupon.getUserId()).isEqualTo(7L);
    }

    /** 品类券：订单类目与券类目无交集拒绝 */
    @Test
    @DisplayName("品类券无命中类目拒绝使用")
    void requireUsableRejectsCategoryMiss() {
        CouponUser scoped = coupon();
        scoped.setScope(CouponScope.CATEGORY);
        scoped.setCategoryIds(List.of(901L));
        when(couponUserRepository.findById(5L)).thenReturn(Optional.of(scoped));

        assertThatThrownBy(() -> couponAppService.requireUsable(5L, 7L, 1L, Set.of(801L), null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_CATEGORY_NOT_MATCHED));
    }

    /** 渠道不符 */
    @Test
    @DisplayName("渠道不符拒绝使用")
    void requireUsableRejectsChannel() {
        CouponUser scoped = coupon();
        scoped.setChannelId(2L);
        when(couponUserRepository.findById(5L)).thenReturn(Optional.of(scoped));

        assertThatThrownBy(() -> couponAppService.requireUsable(5L, 7L, 1L, null, new BigDecimal("200.00")))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_CHANNEL_MISMATCH));
    }

    /** 核销时影响 0 行 = 已被并发占用，必须报错而不是静默成功 */
    @Test
    @DisplayName("核销失败（已被占用）抛业务异常")
    void useFailsWhenAlreadyUsed() {
        when(couponUserRepository.markUsed(eq(5L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> couponAppService.use(5L, "MO20261107000001"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_STATE_INVALID));
    }

    /** 可用券列表：可用的排前面，同组内按可减金额降序 */
    @Test
    @DisplayName("可用券按可用性与优惠金额排序")
    void availableSorted() {
        CouponUser small = coupon();
        small.setId(1L);
        small.setFaceAmount(new BigDecimal("5.00"));
        CouponUser big = coupon();
        big.setId(2L);
        big.setFaceAmount(new BigDecimal("30.00"));
        CouponUser low = coupon();
        low.setId(3L);
        low.setThresholdAmount(new BigDecimal("999.00"));
        when(couponUserRepository.listUsable(eq(7L), any(LocalDateTime.class)))
                .thenReturn(List.of(small, low, big));

        List<CouponUserView> views = couponAppService.available(1L, new BigDecimal("200.00"), null, BUYER);

        assertThat(views).hasSize(3);
        assertThat(views.get(0).getId()).isEqualTo(2L);
        assertThat(views.get(1).getId()).isEqualTo(1L);
        assertThat(views.get(2).getId()).isEqualTo(3L);
        assertThat(views.get(2).getUsability()).isEqualTo("THRESHOLD_NOT_MET");
        assertThat(views.get(2).getUsabilityLabel()).isEqualTo("订单金额未达门槛");
    }

    /** 券编码重复拒绝 */
    @Test
    @DisplayName("券编码重复拒绝创建")
    void createRejectsDuplicateCode() {
        when(couponRepository.existsCode(eq("C100"), any())).thenReturn(true);

        assertThatThrownBy(() -> couponAppService.create(command(), BUYER))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.COUPON_CODE_CONFLICT));
    }

    /**
     * 构造可领取的模板。
     *
     * @return 模板
     */
    private Coupon template() {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setCouponCode("C100");
        coupon.setCouponName("满 100 减 20");
        coupon.setCouponType(CouponType.FULL_REDUCTION);
        coupon.setThresholdAmount(new BigDecimal("100.00"));
        coupon.setFaceAmount(new BigDecimal("20.00"));
        coupon.setPerUserLimit(1);
        coupon.setTotalQty(100);
        coupon.setIssuedQty(0);
        coupon.setValidFrom(LocalDateTime.now().minusDays(1));
        coupon.setValidTo(LocalDateTime.now().plusDays(30));
        return coupon;
    }

    /**
     * 构造一张已签发的用户券。
     *
     * @return 用户券
     */
    private CouponUser coupon() {
        return CouponUser.issue(template(), 7L, 1);
    }

    /**
     * 构造保存命令。
     *
     * @return 命令
     */
    private CouponSaveCommand command() {
        CouponSaveCommand command = new CouponSaveCommand();
        command.setCouponCode("C100");
        command.setCouponName("满 100 减 20");
        command.setCouponType("FULL_REDUCTION");
        command.setThresholdAmount(new BigDecimal("100.00"));
        command.setFaceAmount(new BigDecimal("20.00"));
        command.setPerUserLimit(1);
        command.setTotalQty(100);
        command.setValidFrom(LocalDateTime.now());
        command.setValidTo(LocalDateTime.now().plusDays(30));
        return command;
    }
}

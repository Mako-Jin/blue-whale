package com.bluewhale.mall.coupon.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 优惠券模板。
 *
 * <p>模板只负责「能发什么券、发多少、什么时候能领」，使用判定与金额计算
 * 都在用户券上按<b>快照</b>进行（{@link CouponUser}）。理由：运营把
 * 「满 100 减 50」改成「满 100 减 5」时，已经领出去的券不能跟着变——
 * 那是发出去的承诺。</p>
 *
 * @author BlueWhale
 */
@Data
public class Coupon {

    /** 编码最大长度 */
    public static final int CODE_MAX_LENGTH = 64;

    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 折扣率下限（0.01 = 一折，低于此值等于白送） */
    public static final BigDecimal MIN_DISCOUNT_RATE = new BigDecimal("0.01");

    /** 折扣率上限（不含 1，1 折等于没优惠） */
    public static final BigDecimal MAX_DISCOUNT_RATE = BigDecimal.ONE;

    /** 主键 */
    private Long id;

    /** 券编码（租户内唯一） */
    private String couponCode;

    /** 券名称 */
    private String couponName;

    /** 券类型 */
    private CouponType couponType;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 满减面额 */
    private BigDecimal faceAmount;

    /** 折扣率 */
    private BigDecimal discountRate;

    /** 折扣封顶金额 */
    private BigDecimal maxDiscountAmount;

    /** 适用渠道 ID（空 = 全场通用，仅 CHANNEL 用） */
    private Long channelId;

    /** 适用范围 */
    private CouponScope scope;

    /** 适用类目 ID（仅 CATEGORY 用；品类券门槛与优惠只看命中类目商品行） */
    private List<Long> categoryIds;

    /** 发行量（0 = 不限） */
    private Integer totalQty;

    /** 已发行数量 */
    private Integer issuedQty;

    /** 每人限领张数 */
    private Integer perUserLimit;

    /** 生效时间 */
    private LocalDateTime validFrom;

    /** 失效时间 */
    private LocalDateTime validTo;

    /** 状态 */
    private CouponStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验并规整模板字段。
     */
    public void validate() {
        normalizeText();
        if (couponType == null) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "必须指定券类型");
        }
        thresholdAmount = scale(thresholdAmount);
        if (thresholdAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "使用门槛不能为负");
        }
        validateAmountRule();
        validateScope();
        validatePeriod();
        if (perUserLimit == null || perUserLimit < 1) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "每人限领张数至少为 1");
        }
        totalQty = totalQty == null ? 0 : totalQty;
        if (totalQty < 0) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "发行量不能为负");
        }
        issuedQty = issuedQty == null ? 0 : issuedQty;
        if (status == null) {
            status = CouponStatus.ACTIVE;
        }
    }

    /**
     * 计算优惠的规则快照。
     *
     * @return 计算规则
     */
    public CouponRule rule() {
        return new CouponRule(couponType, thresholdAmount, faceAmount, discountRate, maxDiscountAmount);
    }

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean isActive() {
        return status == null || status == CouponStatus.ACTIVE;
    }

    /**
     * 是否不限量发行。
     *
     * @return 发行量为 0 返回 true
     */
    public boolean unlimited() {
        return totalQty == null || totalQty <= 0;
    }

    /**
     * 是否仍可领取（时间窗口 + 状态 + 余量）。
     *
     * @param now 当前时间
     * @return 可领取返回 true
     */
    public boolean claimable(LocalDateTime now) {
        if (!isActive() || now == null) {
            return false;
        }
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        return unlimited() || (issuedQty == null ? 0 : issuedQty) < totalQty;
    }

    /**
     * 规整编码与名称。
     */
    private void normalizeText() {
        if (couponCode == null || couponCode.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "券编码不能为空");
        }
        couponCode = couponCode.trim().toUpperCase(Locale.ROOT);
        if (couponCode.length() > CODE_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID,
                    "券编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        if (couponName == null || couponName.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "券名称不能为空");
        }
        couponName = couponName.trim();
        if (couponName.length() > NAME_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID,
                    "券名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (remark != null && remark.length() > REMARK_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 按券类型校验面额或折扣率。
     *
     * <p>折扣券强制要求封顶：不封顶的折扣券在高价订单上会算出运营事后
     * 无法追认的优惠额，这一条在模板保存时就挡住，比事后退款便宜得多。</p>
     */
    private void validateAmountRule() {
        if (couponType == CouponType.FULL_REDUCTION) {
            faceAmount = scale(faceAmount);
            if (faceAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "满减面额必须大于 0");
            }
            discountRate = null;
            maxDiscountAmount = null;
            return;
        }
        if (discountRate == null
                || discountRate.compareTo(MIN_DISCOUNT_RATE) < 0
                || discountRate.compareTo(MAX_DISCOUNT_RATE) >= 0) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "折扣率必须在 0.01 到 0.99 之间");
        }
        if (maxDiscountAmount == null || maxDiscountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "折扣券必须设置封顶金额");
        }
        maxDiscountAmount = scale(maxDiscountAmount);
        faceAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 校验适用范围。
     *
     * <p>scope 缺省时按老数据口径回落（channelId 非空即单渠道券）；
     * 品类券必须绑定类目且不允许再绑渠道，单渠道券必须给出渠道——
     * 半配置的券放出去，买家在渠道 B 用了 A 的渠道券，口径就再也说不清了。</p>
     */
    private void validateScope() {
        if (scope == null) {
            scope = channelId == null ? CouponScope.ALL : CouponScope.CHANNEL;
        }
        if (scope == CouponScope.CHANNEL) {
            if (channelId == null) {
                throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "单渠道券必须指定适用渠道");
            }
            categoryIds = null;
            return;
        }
        if (scope == CouponScope.CATEGORY) {
            if (categoryIds == null || categoryIds.isEmpty() || categoryIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "品类券必须绑定至少一个有效类目");
            }
            channelId = null;
            return;
        }
        channelId = null;
        categoryIds = null;
    }

    /**
     * 校验有效期。
     */
    private void validatePeriod() {
        if (validFrom == null || validTo == null) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "必须指定生效与失效时间");
        }
        if (!validTo.isAfter(validFrom)) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID, "失效时间必须晚于生效时间");
        }
    }

    /**
     * 金额规整为两位小数，空值按 0。
     *
     * @param value 金额
     * @return 规整后的金额
     */
    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}

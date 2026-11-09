package com.bluewhale.mall.pricing.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 运费模板。
 *
 * <p>挂在渠道上（{@code mall_channel.freight_template_id}，为空表示不收运费）。
 * 运费在<b>渠道</b>这一层配置而不是商品层，是因为差价运费（同一单里不同商品
 * 走不同模板再累加）需要商品级模板 + 叠加规则，那属于另一套模型。</p>
 *
 * <p>{@link #freightFor} 的门槛判定分母是<b>商品总额（含优惠前）</b>。
 * 用券后金额跌破包邮线就收运费，会让「领了 20 元券反而多付 12 元运费」，
 * 这是最容易被投诉的交互，也是这类系统里最常见的实现错误。</p>
 *
 * @author BlueWhale
 */
@Data
public class FreightTemplate {

    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 主键 */
    private Long id;

    /** 模板名称（租户内唯一） */
    private String templateName;

    /** 计费方式 */
    private FreightChargeType chargeType;

    /** 固定运费金额 */
    private BigDecimal baseAmount;

    /** 包邮门槛 */
    private BigDecimal freeThreshold;

    /** 每件运费（仅 BY_PIECE 用） */
    private BigDecimal piecePrice;

    /** 包邮件数门槛（仅 BY_PIECE 可选，达到则免运费） */
    private Integer pieceFreeThreshold;

    /** 状态 */
    private FreightTemplateStatus status;

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
        if (templateName == null || templateName.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID, "模板名称不能为空");
        }
        templateName = templateName.trim();
        if (templateName.length() > NAME_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID,
                    "模板名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (chargeType == null) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID, "必须指定计费方式");
        }
        if (status == null) {
            status = FreightTemplateStatus.ACTIVE;
        }
        if (remark != null && remark.length() > REMARK_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
        if (chargeType == FreightChargeType.FREE) {
            baseAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            freeThreshold = null;
            piecePrice = null;
            pieceFreeThreshold = null;
            return;
        }
        if (chargeType == FreightChargeType.BY_PIECE) {
            validatePieceCharge();
            return;
        }
        validateAmountCharge();
    }

    /**
     * 校验按件计费参数。
     *
     * <p>按件计费不用整单金额字段：baseAmount/freeThreshold 留着只会让人猜
     * 「到底按件还是按单」，校验时直接清空。</p>
     */
    private void validatePieceCharge() {
        baseAmount = null;
        freeThreshold = null;
        if (piecePrice == null || piecePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID, "每件运费必须大于 0");
        }
        piecePrice = piecePrice.setScale(2, RoundingMode.HALF_UP);
        if (pieceFreeThreshold != null && pieceFreeThreshold < 1) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID, "包邮件数门槛不能小于 1");
        }
    }

    /**
     * 校验固定/满额计费参数（其余类型的 piece 参数清空）。
     */
    private void validateAmountCharge() {
        piecePrice = null;
        pieceFreeThreshold = null;
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID, "运费金额不能为空且不能为负");
        }
        baseAmount = baseAmount.setScale(2, RoundingMode.HALF_UP);
        if (chargeType == FreightChargeType.FIXED) {
            freeThreshold = null;
            return;
        }
        if (freeThreshold == null || freeThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID, "满额包邮门槛必须大于 0");
        }
        freeThreshold = freeThreshold.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 按商品总额计算运费（件数未知场景：FIXED/FREE/THRESHOLD 可用，BY_PIECE 视为 0 件收 0）。
     *
     * @param goodsAmount 商品总额（优惠前，不含运费）
     * @return 运费金额，不小于 0
     */
    public BigDecimal freightFor(BigDecimal goodsAmount) {
        return freightFor(goodsAmount, null);
    }

    /**
     * 按商品总额与件数计算运费。
     *
     * <p>BY_PIECE 用件数计价、可选包邮件数门槛（门槛判定用<b>件数</b>，
     * 与按金额包邮互相独立）；其余类型忽略件数，仍按总额判定。</p>
     *
     * @param goodsAmount 商品总额（优惠前，不含运费）
     * @param quantity    商品件数（可空，仅 BY_PIECE 需要）
     * @return 运费金额，不小于 0
     */
    public BigDecimal freightFor(BigDecimal goodsAmount, Integer quantity) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (chargeType == null || chargeType == FreightChargeType.FREE) {
            return zero;
        }
        if (chargeType == FreightChargeType.BY_PIECE) {
            int pieces = quantity == null ? 0 : quantity;
            if (pieces <= 0) {
                return zero;
            }
            if (pieceFreeThreshold != null && pieces >= pieceFreeThreshold) {
                return zero;
            }
            BigDecimal price = piecePrice == null ? BigDecimal.ZERO : piecePrice;
            return price.multiply(BigDecimal.valueOf(pieces)).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal base = baseAmount == null ? BigDecimal.ZERO : baseAmount;
        if (chargeType == FreightChargeType.FIXED) {
            return base.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal threshold = freeThreshold == null ? BigDecimal.ZERO : freeThreshold;
        BigDecimal amount = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        return amount.compareTo(threshold) >= 0 ? zero : base.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 是否可用于计价（停用的模板不再参与计算）。
     *
     * @return 启用返回 true
     */
    public boolean isActive() {
        return status == null || status == FreightTemplateStatus.ACTIVE;
    }

    /**
     * 规整模板名称为大写无关的唯一形式。
     *
     * @return 去空格后的名称
     */
    public String normalizedName() {
        return templateName == null ? null : templateName.trim().toUpperCase(Locale.ROOT);
    }
}

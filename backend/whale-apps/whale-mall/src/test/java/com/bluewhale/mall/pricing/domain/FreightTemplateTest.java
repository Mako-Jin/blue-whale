package com.bluewhale.mall.pricing.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.pricing.domain.model.FreightChargeType;
import com.bluewhale.mall.pricing.domain.model.FreightTemplate;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 运费模板规则（Wave 5.97）。
 *
 * @author BlueWhale
 */
class FreightTemplateTest {

    /** 全免 */
    @Test
    @DisplayName("FREE 恒为 0，且清掉落库的冗余字段")
    void freeTemplate() {
        FreightTemplate template = template(FreightChargeType.FREE);
        template.setBaseAmount(new BigDecimal("12.00"));

        template.validate();

        assertThat(template.getBaseAmount()).isEqualByComparingTo("0.00");
        assertThat(template.getFreeThreshold()).isNull();
        assertThat(template.freightFor(new BigDecimal("10.00"))).isEqualByComparingTo("0.00");
    }

    /** 固定运费与金额无关 */
    @Test
    @DisplayName("FIXED 不论金额都收固定运费")
    void fixedTemplate() {
        FreightTemplate template = template(FreightChargeType.FIXED);
        template.setBaseAmount(new BigDecimal("12.00"));
        template.validate();

        assertThat(template.freightFor(new BigDecimal("1.00"))).isEqualByComparingTo("12.00");
        assertThat(template.freightFor(new BigDecimal("9999.00"))).isEqualByComparingTo("12.00");
    }

    /** 满额包邮：刚好等于门槛也免运费（买够 99 就该包邮） */
    @Test
    @DisplayName("THRESHOLD 达门槛免运费，差一分照收")
    void thresholdTemplate() {
        FreightTemplate template = template(FreightChargeType.THRESHOLD);
        template.setBaseAmount(new BigDecimal("12.00"));
        template.setFreeThreshold(new BigDecimal("99.00"));
        template.validate();

        assertThat(template.freightFor(new BigDecimal("98.99"))).isEqualByComparingTo("12.00");
        assertThat(template.freightFor(new BigDecimal("99.00"))).isEqualByComparingTo("0.00");
        assertThat(template.freightFor(new BigDecimal("150.00"))).isEqualByComparingTo("0.00");
    }

    /** 满额包邮缺门槛是配置错误，不能悄悄当成固定运费 */
    @Test
    @DisplayName("THRESHOLD 缺少门槛拒绝保存")
    void thresholdWithoutFreeAmountRejected() {
        FreightTemplate template = template(FreightChargeType.THRESHOLD);
        template.setBaseAmount(new BigDecimal("12.00"));

        assertThatThrownBy(template::validate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.FREIGHT_TEMPLATE_INVALID));
    }

    /** 负数运费拒绝 */
    @Test
    @DisplayName("负数运费拒绝保存")
    void negativeAmountRejected() {
        FreightTemplate template = template(FreightChargeType.FIXED);
        template.setBaseAmount(new BigDecimal("-1.00"));

        assertThatThrownBy(template::validate).isInstanceOf(BizException.class);
    }

    /** 名称为空拒绝 */
    @Test
    @DisplayName("空名称拒绝保存")
    void blankNameRejected() {
        FreightTemplate template = template(FreightChargeType.FREE);
        template.setTemplateName("  ");

        assertThatThrownBy(template::validate).isInstanceOf(BizException.class);
    }

    /** 停用的模板不参与计价 */
    @Test
    @DisplayName("停用模板不可用于计价")
    void disabledTemplateInactive() {
        FreightTemplate template = template(FreightChargeType.FIXED);
        template.setStatus(FreightTemplateStatus.DISABLED);

        assertThat(template.isActive()).isFalse();
    }

    /** 按件计费：每件单价 × 件数 */
    @Test
    @DisplayName("BY_PIECE 按件数乘单价计费")
    void pieceTemplateChargesByQuantity() {
        FreightTemplate template = piece("5.00", null);
        template.validate();

        assertThat(template.freightFor(new BigDecimal("10.00"), 3))
                .isEqualByComparingTo("15.00");
        assertThat(template.freightFor(new BigDecimal("10.00"), 1))
                .isEqualByComparingTo("5.00");
        // 件数未知（老调用口径）视为 0 件：宁可少收也不能凭空收费
        assertThat(template.freightFor(new BigDecimal("10.00"), null))
                .isEqualByComparingTo("0.00");
        assertThat(template.freightFor(new BigDecimal("10.00"), 0))
                .isEqualByComparingTo("0.00");
    }

    /** 按件计费的包邮件数门槛 */
    @Test
    @DisplayName("BY_PIECE 达包邮件数门槛免运费")
    void pieceTemplateFreeThreshold() {
        FreightTemplate template = piece("5.00", 10);
        template.validate();

        assertThat(template.freightFor(new BigDecimal("10.00"), 9))
                .isEqualByComparingTo("45.00");
        assertThat(template.freightFor(new BigDecimal("10.00"), 10))
                .isEqualByComparingTo("0.00");
        assertThat(template.freightFor(new BigDecimal("10.00"), 12))
                .isEqualByComparingTo("0.00");
    }

    /** 按件计费的参数校验与冗余清理 */
    @Test
    @DisplayName("BY_PIECE 校验：单价必填、金额字段清空")
    void pieceTemplateValidation() {
        FreightTemplate noPrice = piece(null, 10);
        assertThatThrownBy(noPrice::validate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.FREIGHT_TEMPLATE_INVALID));

        FreightTemplate zeroPrice = piece("0.00", 10);
        assertThatThrownBy(zeroPrice::validate).isInstanceOf(BizException.class);

        FreightTemplate badThreshold = piece("5.00", 0);
        assertThatThrownBy(badThreshold::validate).isInstanceOf(BizException.class);

        // 校验通过后按件模板不应再带整单金额字段
        FreightTemplate ok = piece("5.00", 10);
        ok.setBaseAmount(new BigDecimal("12.00"));
        ok.validate();
        assertThat(ok.getBaseAmount()).isNull();
        assertThat(ok.getFreeThreshold()).isNull();
        assertThat(ok.getPiecePrice()).isEqualByComparingTo("5.00");
    }

    /** 非按件模板清掉件计参数，避免配置两套口径 */
    @Test
    @DisplayName("非 BY_PIECE 模板清空件计参数")
    void amountTemplateClearsPieceFields() {
        FreightTemplate template = template(FreightChargeType.FIXED);
        template.setBaseAmount(new BigDecimal("12.00"));
        template.setPiecePrice(new BigDecimal("5.00"));
        template.setPieceFreeThreshold(5);
        template.validate();

        assertThat(template.getPiecePrice()).isNull();
        assertThat(template.getPieceFreeThreshold()).isNull();
        assertThat(template.freightFor(new BigDecimal("10.00"), 3)).isEqualByComparingTo("12.00");
    }

    /**
     * 构造模板。
     *
     * @param chargeType 计费方式
     * @return 模板
     */
    private FreightTemplate template(FreightChargeType chargeType) {
        FreightTemplate template = new FreightTemplate();
        template.setTemplateName("默认运费");
        template.setChargeType(chargeType);
        return template;
    }

    /**
     * 构造按件计费模板。
     *
     * @param piecePrice        每件运费
     * @param pieceFreeThreshold 包邮件数门槛
     * @return 模板
     */
    private FreightTemplate piece(String piecePrice, Integer pieceFreeThreshold) {
        FreightTemplate template = template(FreightChargeType.BY_PIECE);
        template.setPiecePrice(piecePrice == null ? null : new BigDecimal(piecePrice));
        template.setPieceFreeThreshold(pieceFreeThreshold);
        return template;
    }
}

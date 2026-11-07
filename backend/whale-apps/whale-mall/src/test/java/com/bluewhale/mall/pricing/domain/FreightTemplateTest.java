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
}

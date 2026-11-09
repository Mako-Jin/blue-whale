package com.bluewhale.mall.pricing.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.pricing.domain.model.FreightChargeType;
import com.bluewhale.mall.pricing.domain.model.FreightRegionRate;
import com.bluewhale.mall.pricing.domain.model.FreightTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 按重与区域运费。
 *
 * <p>按重守「首重一口、续重向上取整」的快递口径；区域运费守「命中区域按
 * 区域费率（可带区域包邮门槛）、未命中回落模板基准」——宁可多收退差价，
 * 不可少收漏算。</p>
 *
 * @author BlueWhale
 */
class FreightWeightRegionTest {

    /** 按重：首重内收一笔；超首重按续重步长向上取整累加 */
    @Test
    @DisplayName("按重计费：首重一口、续重向上取整")
    void weightFreightCeilContinuation() {
        FreightTemplate template = weightTemplate("10.00", "1.00", "1.00", "2.00");

        assertThat(template.freightFor(new BigDecimal("50.00"), 1, new BigDecimal("0.80"), null))
                .isEqualByComparingTo("10.00");
        assertThat(template.freightFor(new BigDecimal("50.00"), 1, new BigDecimal("1.00"), null))
                .isEqualByComparingTo("10.00");
        assertThat(template.freightFor(new BigDecimal("50.00"), 1, new BigDecimal("1.01"), null))
                .isEqualByComparingTo("12.00");
        // 2.5kg → 超首重 1.5kg → 两步
        assertThat(template.freightFor(new BigDecimal("50.00"), 1, new BigDecimal("2.50"), null))
                .isEqualByComparingTo("14.00");
    }

    /** 按重：重量未知按首重收（宁可多收退差价）；不配续重参数按首重一口价 */
    @Test
    @DisplayName("按重计费：重量未知或不配续重回落首重价")
    void weightFreightFallbacks() {
        FreightTemplate template = weightTemplate("10.00", "1.00", "1.00", "2.00");
        assertThat(template.freightFor(new BigDecimal("50.00"), 1, null, null))
                .isEqualByComparingTo("10.00");

        FreightTemplate flat = weightTemplate("10.00", "1.00", null, null);
        assertThat(flat.freightFor(new BigDecimal("50.00"), 1, new BigDecimal("9.00"), null))
                .isEqualByComparingTo("10.00");
    }

    /** 区域运费：命中区域按区域费率与区域包邮门槛；未命中回落基准 */
    @Test
    @DisplayName("区域运费：区域费率、区域包邮与兜底")
    void regionalFreightRates() {
        FreightTemplate template = regionalTemplate();

        assertThat(template.freightFor(new BigDecimal("50.00"), 1, null, "浙江省"))
                .isEqualByComparingTo("8.00");
        assertThat(template.freightFor(new BigDecimal("99.99"), 1, null, "浙江省"))
                .isEqualByComparingTo("8.00");
        assertThat(template.freightFor(new BigDecimal("100.00"), 1, null, "浙江省"))
                .isEqualByComparingTo("0.00");
        assertThat(template.freightFor(new BigDecimal("30.00"), 1, null, "新疆"))
                .isEqualByComparingTo("25.00");
        // 未命中区域（未配/空白）回落基准 12.00
        assertThat(template.freightFor(new BigDecimal("30.00"), 1, null, "青海省"))
                .isEqualByComparingTo("12.00");
        assertThat(template.freightFor(new BigDecimal("30.00"), 1, null, null))
                .isEqualByComparingTo("12.00");
    }

    /** 按重模板缺首重参数直接拒绝；区域费率重复或金额为负直接拒绝 */
    @Test
    @DisplayName("按重与区域模板校验")
    void templateValidation() {
        FreightTemplate missingFirst = weightTemplate(null, null, "1.00", "2.00");
        assertThatThrownBy(missingFirst::validate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.FREIGHT_TEMPLATE_INVALID));

        FreightTemplate duplicate = regionalTemplate();
        FreightRegionRate extra = new FreightRegionRate();
        extra.setRegion(" 浙江省 ");
        extra.setAmount(new BigDecimal("8.00"));
        duplicate.getRegionRates().add(extra);
        assertThatThrownBy(duplicate::validate)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.FREIGHT_TEMPLATE_INVALID));
    }

    /**
     * 构造按重模板。
     *
     * @param firstAmount        首重运费（baseAmount）
     * @param firstWeight        首重 kg
     * @param continuedWeight    续重步长 kg
     * @param continuedAmount    续重运费
     * @return 模板
     */
    private FreightTemplate weightTemplate(String firstAmount, String firstWeight,
                                           String continuedWeight, String continuedAmount) {
        FreightTemplate template = new FreightTemplate();
        template.setTemplateName("按重模板");
        template.setChargeType(FreightChargeType.BY_WEIGHT);
        template.setBaseAmount(firstAmount == null ? null : new BigDecimal(firstAmount));
        template.setFirstWeightKg(firstWeight == null ? null : new BigDecimal(firstWeight));
        template.setContinuedWeightKg(continuedWeight == null ? null : new BigDecimal(continuedWeight));
        template.setContinuedAmount(continuedAmount == null ? null : new BigDecimal(continuedAmount));
        if (firstAmount != null) {
            template.validate();
        }
        return template;
    }

    /**
     * 构造区域模板：基准 12.00；浙江 8.00（满 100 包邮）；新疆 25.00。
     *
     * @return 模板
     */
    private FreightTemplate regionalTemplate() {
        FreightTemplate template = new FreightTemplate();
        template.setTemplateName("区域模板");
        template.setChargeType(FreightChargeType.REGIONAL);
        template.setBaseAmount(new BigDecimal("12.00"));
        FreightRegionRate zhejiang = new FreightRegionRate();
        zhejiang.setRegion("浙江省");
        zhejiang.setAmount(new BigDecimal("8.00"));
        zhejiang.setFreeThreshold(new BigDecimal("100.00"));
        FreightRegionRate xinjiang = new FreightRegionRate();
        xinjiang.setRegion("新疆");
        xinjiang.setAmount(new BigDecimal("25.00"));
        template.setRegionRates(new java.util.ArrayList<>(List.of(zhejiang, xinjiang)));
        template.validate();
        return template;
    }
}

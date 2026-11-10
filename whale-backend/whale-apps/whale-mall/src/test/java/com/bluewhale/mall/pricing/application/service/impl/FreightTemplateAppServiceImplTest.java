package com.bluewhale.mall.pricing.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.pricing.application.command.FreightTemplateSaveCommand;
import com.bluewhale.mall.pricing.domain.model.FreightChargeType;
import com.bluewhale.mall.pricing.domain.model.FreightTemplate;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateStatus;
import com.bluewhale.mall.pricing.domain.repository.FreightTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 运费模板应用服务（Wave 5.97）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreightTemplateAppServiceImplTest {

    private static final MallActor OPERATOR =
            new MallActor(1L, "admin", java.util.Set.of(), java.util.Set.of("tenant_admin"));

    @Mock
    private FreightTemplateRepository freightTemplateRepository;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private FreightTemplateAppServiceImpl freightTemplateAppService;

    @BeforeEach
    void setUp() {
        when(properties.getPageLimit()).thenReturn(100);
    }

    /** 渠道没绑模板就不收运费 */
    @Test
    @DisplayName("未绑定模板返回零运费")
    void noTemplateMeansNoFreight() {
        assertThat(freightTemplateAppService.freightFor(null, new BigDecimal("100.00")))
                .isEqualByComparingTo("0.00");
    }

    /** 模板被删或停用按不收运费处理：配置疏漏不该阻断下单 */
    @Test
    @DisplayName("停用模板按零运费处理")
    void disabledTemplateMeansNoFreight() {
        FreightTemplate disabled = template();
        disabled.setStatus(FreightTemplateStatus.DISABLED);
        when(freightTemplateRepository.findById(3L)).thenReturn(Optional.of(disabled));

        assertThat(freightTemplateAppService.freightFor(3L, new BigDecimal("50.00")))
                .isEqualByComparingTo("0.00");
    }

    /** 模板不存在同样按 0 处理，不抛错 */
    @Test
    @DisplayName("模板不存在返回零运费")
    void missingTemplateMeansNoFreight() {
        when(freightTemplateRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(freightTemplateAppService.freightFor(9L, new BigDecimal("50.00")))
                .isEqualByComparingTo("0.00");
    }

    /** 满额包邮正常计费 */
    @Test
    @DisplayName("满额包邮按商品总额判定")
    void thresholdTemplateCalculates() {
        when(freightTemplateRepository.findById(3L)).thenReturn(Optional.of(template()));

        assertThat(freightTemplateAppService.freightFor(3L, new BigDecimal("50.00")))
                .isEqualByComparingTo("12.00");
        assertThat(freightTemplateAppService.freightFor(3L, new BigDecimal("100.00")))
                .isEqualByComparingTo("0.00");
    }

    /** 名称重复拒绝 */
    @Test
    @DisplayName("模板名称重复拒绝创建")
    void createRejectsDuplicateName() {
        when(freightTemplateRepository.existsName(eq("默认运费"), any())).thenReturn(true);

        assertThatThrownBy(() -> freightTemplateAppService.create(command(), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.FREIGHT_TEMPLATE_NAME_CONFLICT));
    }

    /** 未知计费方式直接拒绝，不静默回落 */
    @Test
    @DisplayName("未知计费方式拒绝创建")
    void createRejectsUnknownChargeType() {
        FreightTemplateSaveCommand command = command();
        command.setChargeType("WEIGHT");

        assertThatThrownBy(() -> freightTemplateAppService.create(command, OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.FREIGHT_TEMPLATE_INVALID));
    }

    /** 编辑不存在的模板 */
    @Test
    @DisplayName("编辑不存在的模板抛业务异常")
    void updateMissingTemplate() {
        when(freightTemplateRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> freightTemplateAppService.update(9L, command(), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.FREIGHT_TEMPLATE_NOT_FOUND));
    }

    /**
     * 构造满 99 包邮、否则 12 元的模板。
     *
     * @return 模板
     */
    private FreightTemplate template() {
        FreightTemplate template = new FreightTemplate();
        template.setId(3L);
        template.setTemplateName("默认运费");
        template.setChargeType(FreightChargeType.THRESHOLD);
        template.setBaseAmount(new BigDecimal("12.00"));
        template.setFreeThreshold(new BigDecimal("99.00"));
        template.setStatus(FreightTemplateStatus.ACTIVE);
        return template;
    }

    /**
     * 构造保存命令。
     *
     * @return 命令
     */
    private FreightTemplateSaveCommand command() {
        FreightTemplateSaveCommand command = new FreightTemplateSaveCommand();
        command.setTemplateName("默认运费");
        command.setChargeType("THRESHOLD");
        command.setBaseAmount(new BigDecimal("12.00"));
        command.setFreeThreshold(new BigDecimal("99.00"));
        return command;
    }
}

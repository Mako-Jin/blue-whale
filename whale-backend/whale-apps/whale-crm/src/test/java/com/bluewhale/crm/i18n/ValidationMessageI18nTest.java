package com.bluewhale.crm.i18n;

import com.bluewhale.crm.customer.application.command.CustomerSaveCommand;
import com.bluewhale.crm.sales.application.command.SalesTargetSaveCommand;
import com.bluewhale.i18n.WhaleI18nAutoConfiguration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.i18n.LocaleContextHolder;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM 校验文案国际化链路测试。
 *
 * <p>验证 whale-apps 应用接入 whale-i18n-sdk 后，DTO 上的 {@code {validation.crm.*}}
 * 占位符按请求 Locale 解析：中文命中迁移保留的原文，英文命中新增的 en_US 词条。
 * 这是"加依赖 + 加词条 + 改注解"闭环生效的回归钉子。</p>
 *
 * @author BlueWhale
 */
class ValidationMessageI18nTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WhaleI18nAutoConfiguration.class,
                    ValidationAutoConfiguration.class));

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("必填校验文案按语言解析：客户名称")
    void resolvesRequiredMessageByLocale() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            Validator validator = context.getBean(Validator.class);
            CustomerSaveCommand request = new CustomerSaveCommand();
            request.setName(null);

            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertThat(messageOf(validator, request, "name")).isEqualTo("客户名称不能为空");

            LocaleContextHolder.setLocale(Locale.US);
            assertThat(messageOf(validator, request, "name")).isEqualTo("Customer name must not be empty");
        });
    }

    @Test
    @DisplayName("数值下界校验文案按语言解析：目标金额")
    void resolvesMinMessageByLocale() {
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);
            SalesTargetSaveCommand request = new SalesTargetSaveCommand();
            request.setPeriod("2026-01");
            request.setTargetAmount(new BigDecimal("-1"));

            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertThat(messageOf(validator, request, "targetAmount")).isEqualTo("目标金额不得为负数");

            LocaleContextHolder.setLocale(Locale.US);
            assertThat(messageOf(validator, request, "targetAmount")).isEqualTo("Target amount must not be negative");
        });
    }

    private static String messageOf(Validator validator, Object target, String property) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        return violations.stream()
                .filter(violation -> property.equals(violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未命中字段校验: " + property));
    }
}

package com.bluewhale.forum.i18n;

import com.bluewhale.forum.dto.BoardSaveRequest;
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

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO 校验文案国际化链路测试。
 *
 * <p>验证 Spring Boot 4 的 {@code ValidationAutoConfiguration} 会把容器内
 * {@code messageSource} 包装为 {@code MessageSourceMessageInterpolator}：DTO 上的
 * {@code {validation.xxx}} 占位符在参数校验时按 {@link LocaleContextHolder}
 * 的当前语言解析，中文与英文资源分别命中。</p>
 *
 * @author BlueWhale
 */
class ValidationMessageI18nTest {

    /**
     * 最小上下文：i18n SDK（负责扫描 classpath:i18n/forum/messages*）+ Boot 参数校验
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WhaleI18nAutoConfiguration.class,
                    ValidationAutoConfiguration.class));

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("校验文案按请求语言解析：zh-CN 命中中文原文，en-US 命中英文翻译")
    void resolvesValidationMessageByLocale() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            Validator validator = context.getBean(Validator.class);

            BoardSaveRequest request = new BoardSaveRequest();
            request.setCode(null);
            request.setName("general");

            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertThat(messageOf(validator, request, "code")).isEqualTo("版块编码不能为空");

            LocaleContextHolder.setLocale(Locale.US);
            assertThat(messageOf(validator, request, "code")).isEqualTo("Board code must not be blank");
        });
    }

    @Test
    @DisplayName("数值区间校验共用同一文案键，两种语言均可解析")
    void resolvesRangeMessageByLocale() {
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);

            BoardSaveRequest request = new BoardSaveRequest();
            request.setCode("general");
            request.setName("综合版");
            request.setAuditMode(9);

            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertThat(messageOf(validator, request, "auditMode")).isEqualTo("审核模式只能是 0 或 1");

            LocaleContextHolder.setLocale(Locale.US);
            assertThat(messageOf(validator, request, "auditMode")).isEqualTo("Audit mode must be 0 or 1");
        });
    }

    /**
     * 取指定字段的首个校验消息
     *
     * @param validator 校验器
     * @param target    待校验对象
     * @param property  字段名
     * @return 校验消息
     */
    private static String messageOf(Validator validator, Object target, String property) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        return violations.stream()
                .filter(violation -> property.equals(violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未命中字段校验: " + property));
    }
}

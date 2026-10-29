package com.bluewhale.common.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * XSS 校验器：命中脚本标签、事件处理器或危险协议即判定不通过。
 *
 * <p>空值视为合法（是否必填由 {@code @NotBlank} 等标准约束负责）。</p>
 *
 * @author BlueWhale
 */
public class XssValidator implements ConstraintValidator<CheckXss, String> {

    /** 危险片段：脚本标签、事件属性、危险协议、可执行表达式与高危标签 */
    private static final Pattern[] DANGEROUS_PATTERNS = {
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\son(click|dblclick|load|error|mouseover|mouseout|focus|blur|change|submit"
                    + "|keydown|keyup|keypress|input)\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:javascript|vbscript)\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*(?:iframe|object|embed|applet|frame|frameset|meta|link|style)[^>]*>",
                    Pattern.CASE_INSENSITIVE)
    };

    /**
     * 校验文本是否包含 XSS 风险片段
     *
     * @param value 待校验文本
     * @param context 校验上下文
     * @return 安全返回 {@code true}
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return false;
            }
        }
        return true;
    }
}

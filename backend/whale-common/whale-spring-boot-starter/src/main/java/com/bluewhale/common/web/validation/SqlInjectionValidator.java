package com.bluewhale.common.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * SQL 注入校验器：命中注释符、联合查询、恒等式条件或危险 DDL/DML 组合即判定不通过。
 *
 * <p>空值视为合法（是否必填由 {@code @NotBlank} 等标准约束负责）。</p>
 *
 * @author BlueWhale
 */
public class SqlInjectionValidator implements ConstraintValidator<CheckSqlInjection, String> {

    /** 注入特征：SQL 注释、联合查询、恒等式条件、堆叠语句、危险关键字组合 */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(?:--\\s|/\\*.*?\\*/|;\\s*(?:select|insert|update|delete|drop|truncate|alter)\\b"
                    + "|\\bunion\\s+(?:all\\s+)?select\\b"
                    + "|\\b(?:or|and)\\s+\\d+\\s*=\\s*\\d+"
                    + "|\\b(?:or|and)\\s+'[^']*'\\s*=\\s*'[^']*'"
                    + "|\\bexec(?:ute)?\\s*\\(|\\bxp_cmdshell\\b)",
            Pattern.DOTALL);

    /**
     * 校验文本是否包含 SQL 注入特征
     *
     * @param value 待校验文本
     * @param context 校验上下文
     * @return 安全返回 {@code true}
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return !containsSqlInjection(value);
    }

    /**
     * 判断文本是否包含 SQL 注入特征（供业务代码复用）
     *
     * @param input 待检测文本，可为 {@code null}
     * @return 包含注入特征返回 {@code true}
     */
    public static boolean containsSqlInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }
}

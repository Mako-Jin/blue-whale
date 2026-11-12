package com.bluewhale.auth.application.model;

import com.bluewhale.auth.application.port.PasswordPolicyConfig;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 口令强度策略值对象（REQ-SEC-021）：创建 / 更新 / 自助改密 / 管理员重置四条入口统一校验。
 *
 * <p>纯内存校验，不依赖 Spring / 持久化；由 {@link PasswordPolicyConfig} 组装，
 * 规则失败统一以 {@link BizException}（PARAM_ERROR）返回中文文案。</p>
 *
 * @author BlueWhale
 */
public final class PasswordPolicy {

    /** 大写字母 */
    private static final Pattern UPPER_PATTERN = Pattern.compile("[A-Z]");
    /** 小写字母 */
    private static final Pattern LOWER_PATTERN = Pattern.compile("[a-z]");
    /** 数字 */
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    /** 特殊字符（非字母数字） */
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[^A-Za-z0-9]");

    /** 常见弱口令（小写比较，命中即拒绝） */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "123456", "12345678", "123456789", "1234567890", "password", "passw0rd",
            "qwerty", "qwerty123", "admin123", "admin888", "admin@123", "abc123456",
            "iloveyou", "welcome", "letmein", "changeme", "change_me");

    /** 最小长度 */
    private final int minLength;
    /** 最大长度 */
    private final int maxLength;
    /** 是否要求大写字母 */
    private final boolean requireUpper;
    /** 是否要求小写字母 */
    private final boolean requireLower;
    /** 是否要求数字 */
    private final boolean requireDigit;
    /** 是否要求特殊字符 */
    private final boolean requireSpecial;

    /**
     * 私有构造器：由 {@link #from(PasswordPolicyConfig)} 组装
     *
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @param requireUpper 是否要求大写字母
     * @param requireLower 是否要求小写字母
     * @param requireDigit 是否要求数字
     * @param requireSpecial 是否要求特殊字符
     */
    private PasswordPolicy(int minLength, int maxLength, boolean requireUpper, boolean requireLower,
                           boolean requireDigit, boolean requireSpecial) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.requireUpper = requireUpper;
        this.requireLower = requireLower;
        this.requireDigit = requireDigit;
        this.requireSpecial = requireSpecial;
    }

    /**
     * 由配置组装策略值对象
     *
     * @param properties 口令策略配置
     * @return 策略值对象
     */
    public static PasswordPolicy from(PasswordPolicyConfig properties) {
        return new PasswordPolicy(properties.getMinLength(), properties.getMaxLength(),
                properties.isRequireUpper(), properties.isRequireLower(),
                properties.isRequireDigit(), properties.isRequireSpecial());
    }

    /**
     * 校验明文口令是否满足策略
     *
     * @param rawPassword 明文口令
     * @param username 所属用户名（口令不得包含用户名，防止「用户名+数字」式弱口令）
     */
    public void validate(String rawPassword, String username) {
        requireLength(rawPassword);
        requireCharacterClasses(rawPassword);
        requireNotWeakOrUsername(rawPassword, username);
    }

    /**
     * 校验非空与长度
     *
     * @param rawPassword 明文口令
     */
    private void requireLength(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令不能为空");
        }
        if (rawPassword.length() < minLength) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令长度至少 " + minLength + " 位");
        }
        if (rawPassword.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令长度不能超过 " + maxLength + " 位");
        }
    }

    /**
     * 校验字符类别要求
     *
     * @param rawPassword 明文口令
     */
    private void requireCharacterClasses(String rawPassword) {
        requireClass(requireUpper, UPPER_PATTERN, rawPassword, "口令需包含大写字母");
        requireClass(requireLower, LOWER_PATTERN, rawPassword, "口令需包含小写字母");
        requireClass(requireDigit, DIGIT_PATTERN, rawPassword, "口令需包含数字");
        requireClass(requireSpecial, SPECIAL_PATTERN, rawPassword, "口令需包含特殊字符");
    }

    /**
     * 校验单一字符类别要求
     *
     * @param required 是否要求该类字符
     * @param pattern 该类字符正则
     * @param rawPassword 明文口令
     * @param message 不满足时的提示文案
     */
    private static void requireClass(boolean required, Pattern pattern, String rawPassword, String message) {
        if (required && !pattern.matcher(rawPassword).find()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
        }
    }

    /**
     * 校验弱口令与用户名包含
     *
     * @param rawPassword 明文口令
     * @param username 用户名（可空）
     */
    private static void requireNotWeakOrUsername(String rawPassword, String username) {
        String normalized = rawPassword.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令过于常见，请更换更复杂的口令");
        }
        if (username != null && !username.isBlank()
                && normalized.contains(username.toLowerCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "口令不得包含用户名");
        }
    }
}

package com.bluewhale.common.security.crypto;

import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Locale;

/**
 * 配置加密设置（REQ-SEC-020）：承载 {@code ENC(...)} 密文的开关、密钥、算法与前缀/后缀。
 *
 * <p>密钥来源优先级：配置项 {@code whale.config.encryption.password} → 环境变量
 * {@code WHALE_CONFIG_ENC_KEY}。密钥只允许由环境变量、K8s Secret 或挂载文件注入，
 * 禁止写入 yml、配置中心明文或日志。</p>
 *
 * @author BlueWhale
 */
public final class ConfigEncryptionSettings {

    /** 开关配置键：关闭后完全不包装属性源 */
    public static final String KEY_ENABLED = "whale.config.encryption.enabled";
    /** 密钥配置键（优先级低于环境变量，便于本地临时调试） */
    public static final String KEY_PASSWORD = "whale.config.encryption.password";
    /** 算法配置键 */
    public static final String KEY_ALGORITHM = "whale.config.encryption.algorithm";
    /** 密文前缀配置键 */
    public static final String KEY_PREFIX = "whale.config.encryption.prefix";
    /** 密文后缀配置键 */
    public static final String KEY_SUFFIX = "whale.config.encryption.suffix";
    /** 密钥最小长度配置键 */
    public static final String KEY_MIN_KEY_LENGTH = "whale.config.encryption.min-key-length";
    /** 密钥环境变量名 */
    public static final String ENV_KEY = "WHALE_CONFIG_ENC_KEY";
    /** 默认算法：AES-256 + HMAC-SHA512（Jasypt 要求 IV 随机） */
    public static final String DEFAULT_ALGORITHM = "PBEWithHMACSHA512AndAES_256";
    /** 默认密文前缀 */
    public static final String DEFAULT_PREFIX = "ENC(";
    /** 默认密文后缀 */
    public static final String DEFAULT_SUFFIX = ")";
    /** 默认密钥最小长度 */
    public static final int DEFAULT_MIN_KEY_LENGTH = 16;

    /** 占位关键词（小写包含匹配），模板值常在后面追加用途后缀（如 {@code change_me_user_pwd}） */
    private static final List<String> PLACEHOLDER_KEYWORDS = List.of("change_me", "changeme", "change-me");

    /** 占位整值（小写完全匹配），避免误伤含该单词的正常随机密钥 */
    private static final List<String> PLACEHOLDER_VALUES = List.of("password", "todo");

    /** 是否启用密文解密 */
    private final boolean enabled;

    /** 加密密钥（不参与日志输出） */
    private final String password;

    /** PBE 算法名 */
    private final String algorithm;

    /** 密文前缀 */
    private final String prefix;

    /** 密文后缀 */
    private final String suffix;

    /** 密钥最小长度 */
    private final int minKeyLength;

    /**
     * 私有构造器：由 {@link #from(Environment)} 与 {@link #of(String)} 组装
     *
     * @param enabled 是否启用密文解密
     * @param password 加密密钥（可空）
     * @param algorithm PBE 算法名
     * @param prefix 密文前缀
     * @param suffix 密文后缀
     * @param minKeyLength 密钥最小长度
     */
    private ConfigEncryptionSettings(boolean enabled, String password, String algorithm, String prefix, String suffix,
                                     int minKeyLength) {
        this.enabled = enabled;
        this.password = password;
        this.algorithm = algorithm;
        this.prefix = prefix;
        this.suffix = suffix;
        this.minKeyLength = minKeyLength;
    }

    /**
     * 从运行环境读取加密设置（EPP 在包装属性源之前调用，此时读到的是原始值）
     *
     * @param environment 可配置环境
     * @return 配置加密设置
     */
    public static ConfigEncryptionSettings from(Environment environment) {
        boolean enabled = readBoolean(environment.getProperty(KEY_ENABLED));
        String password = environment.getProperty(KEY_PASSWORD);
        if (!hasText(password)) {
            password = environment.getProperty(ENV_KEY);
        }
        String algorithm = environment.getProperty(KEY_ALGORITHM, DEFAULT_ALGORITHM);
        String prefix = environment.getProperty(KEY_PREFIX, DEFAULT_PREFIX);
        String suffix = environment.getProperty(KEY_SUFFIX, DEFAULT_SUFFIX);
        int minKeyLength = readInt(environment.getProperty(KEY_MIN_KEY_LENGTH), DEFAULT_MIN_KEY_LENGTH);
        return new ConfigEncryptionSettings(enabled, trimToNull(password), algorithm, prefix, suffix, minKeyLength);
    }

    /**
     * 以显式密钥构造设置（供加密工具与单测使用，默认算法/前后缀）
     *
     * @param password 加密密钥
     * @return 配置加密设置
     */
    public static ConfigEncryptionSettings of(String password) {
        return new ConfigEncryptionSettings(true, trimToNull(password), DEFAULT_ALGORITHM, DEFAULT_PREFIX, DEFAULT_SUFFIX,
                DEFAULT_MIN_KEY_LENGTH);
    }

    /**
     * 校验密钥可用于解密，不合格时抛出包含修复指引的异常（不输出密钥本身）
     */
    public void requireUsableKey() {
        if (!hasText(password)) {
            throw new IllegalStateException("检测到配置中的 ENC(...) 密文，但未提供加密密钥。"
                    + "请通过环境变量 " + ENV_KEY + " 注入（禁止写入 yml/配置中心明文），或关闭 "
                    + KEY_ENABLED + " 并移除密文配置。");
        }
        if (password.length() < minKeyLength) {
            throw new IllegalStateException("配置加密密钥长度不足：" + KEY_PASSWORD + " 至少需要 " + minKeyLength
                    + " 个字符（当前 " + password.length() + " 个），请更换更长的密钥。");
        }
        if (isPlaceholder(password)) {
            throw new IllegalStateException("配置加密密钥仍为占位值，请通过 " + ENV_KEY + " 注入真实密钥。");
        }
    }

    /**
     * 判定密钥是否仍为占位值：大小写不敏感，容许前后缀（如 {@code change_me_user_pwd}），
     * 只要命中占位关键词即视为未替换的模板值。
     *
     * @param value 密钥原文
     * @return 占位值返回 true
     */
    private static boolean isPlaceholder(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_VALUES.contains(normalized)
                || PLACEHOLDER_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * 判定给定文本是否为 {@code ENC(...)} 密文
     *
     * @param value 待判定文本
     * @return 是密文返回 true
     */
    public boolean isEncrypted(String value) {
        return value != null && value.length() > prefix.length() + suffix.length()
                && value.startsWith(prefix) && value.endsWith(suffix);
    }

    /**
     * 截取 {@code ENC(...)} 中的密文主体
     *
     * @param value 完整密文文本
     * @return 去除前后缀后的密文
     */
    public String unwrap(String value) {
        return value.substring(prefix.length(), value.length() - suffix.length());
    }

    /**
     * 判断是否启用密文解密
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取加密密钥
     *
     * @return 密钥；未配置时为 {@code null}
     */
    public String getPassword() {
        return password;
    }

    /**
     * 获取 PBE 算法名
     *
     * @return 算法名
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * 获取密文前缀
     *
     * @return 前缀
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * 获取密文后缀
     *
     * @return 后缀
     */
    public String getSuffix() {
        return suffix;
    }

    /**
     * 解析布尔配置，缺省按 true 处理（默认开启，避免误关）
     *
     * @param raw 原始配置值
     * @return 布尔值
     */
    private static boolean readBoolean(String raw) {
        return !hasText(raw) || Boolean.parseBoolean(raw.trim());
    }

    /**
     * 解析整数配置，非法值回退默认
     *
     * @param raw 原始配置值
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private static int readInt(String raw, int defaultValue) {
        if (!hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /**
     * 去除首尾空白，空串归一为 {@code null}
     *
     * @param value 原始文本
     * @return 归一化文本
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判断文本非空白
     *
     * @param value 原始文本
     * @return 非空白返回 true
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

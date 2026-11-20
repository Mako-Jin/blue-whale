package com.bluewhale.alert.support;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 告警级别归一化工具。
 *
 * <p>入站（Alertmanager severity 标签）与内网创建（调用方传值）原先各自实现了一套
 * 映射，未知值回落也不同（{@code INFO} / {@code WARN}）。这里统一映射规则，
 * 回落值由调用方显式传入，避免两处逻辑漂移。</p>
 *
 * @author BlueWhale
 */
public final class AlertLevels {

    /** 提示级 */
    public static final String INFO = "INFO";

    /** 警告级 */
    public static final String WARN = "WARN";

    /** 错误级 */
    public static final String ERROR = "ERROR";

    /** 严重级 */
    public static final String CRITICAL = "CRITICAL";

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private AlertLevels() {
    }

    /**
     * 归一化告警级别。
     *
     * <p>{@code WARNING} 与 {@code WARN} 等价；其余未知值回落 {@code fallback}。</p>
     *
     * @param raw      原始级别（大小写、空白不敏感）
     * @param fallback 无法识别时的回落级别
     * @return {@code INFO} / {@code WARN} / {@code ERROR} / {@code CRITICAL}
     */
    public static String normalize(String raw, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case INFO -> INFO;
            case WARN, "WARNING" -> WARN;
            case ERROR -> ERROR;
            case CRITICAL -> CRITICAL;
            default -> fallback;
        };
    }
}

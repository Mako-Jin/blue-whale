package com.bluewhale.common.core.i18n;

/**
 * 语言标签归一化与协商工具（纯 Java，无框架依赖）。
 *
 * <p>网关与各服务共用同一套口径：只支持简体中文与美式英语，其余一律回落
 * {@link #DEFAULT_LANGUAGE}。这样网关注入的 {@code X-Lang}、服务端
 * {@code LocaleContextHolder} 与前端语言切换三处不会出现方言式漂移
 * （如 {@code zh_CN} / {@code zh-Hans} / {@code en} 各自为政）。</p>
 *
 * @author BlueWhale
 */
public final class LocaleSupport {

    /** 默认语言：简体中文 */
    public static final String DEFAULT_LANGUAGE = "zh-CN";

    /** 简体中文 */
    public static final String CHINESE = "zh-CN";

    /** 美式英语 */
    public static final String ENGLISH = "en-US";

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private LocaleSupport() {
    }

    /**
     * 归一化单个语言标签。
     *
     * @param tag 原始语言标签，如 {@code zh_CN}、{@code en}、{@code en-GB}
     * @return 归一化后的支持语言；不支持时返回 {@code null}
     */
    public static String normalize(String tag) {
        if (tag == null) {
            return null;
        }
        String value = tag.trim().replace('_', '-').toLowerCase();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("en")) {
            return ENGLISH;
        }
        if (value.startsWith("zh")) {
            return CHINESE;
        }
        return null;
    }

    /**
     * 从 {@code Accept-Language} 头按顺序取首个受支持的语言。
     *
     * @param header 请求头原文，可为 {@code null}
     * @return 受支持语言；无有效项时返回默认语言
     */
    public static String fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        for (String part : header.split(",")) {
            String tag = part.trim();
            int qualityIndex = tag.indexOf(';');
            if (qualityIndex >= 0) {
                tag = tag.substring(0, qualityIndex).trim();
            }
            String normalized = normalize(tag);
            if (normalized != null) {
                return normalized;
            }
        }
        return DEFAULT_LANGUAGE;
    }

    /**
     * 解析最终语言：显式 {@code X-Lang} 优先，其次 {@code Accept-Language}，最后默认中文。
     *
     * @param langHeader           {@code X-Lang} 头
     * @param acceptLanguageHeader {@code Accept-Language} 头
     * @return 归一化后的受支持语言
     */
    public static String resolve(String langHeader, String acceptLanguageHeader) {
        String explicit = normalize(langHeader);
        if (explicit != null) {
            return explicit;
        }
        return fromAcceptLanguage(acceptLanguageHeader);
    }
}

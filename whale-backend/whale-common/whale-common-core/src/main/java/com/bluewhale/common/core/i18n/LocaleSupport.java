package com.bluewhale.common.core.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /**
     * 默认 Locale：显式构造，绝不取 JVM 默认。
     *
     * <p>与 {@link #DEFAULT_LANGUAGE} 同源。非 Web 线程 / 未过 LocaleContextFilter
     * 的路径（定时任务、消息消费者、单测）统一用此常量，避免 JVM 语言为 en 时
     * 隐式漂移到英文，与前端/网关默认口径不一致。</p>
     */
    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag(DEFAULT_LANGUAGE);

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
     * 带权语言候选：归一化后的语言 + q 权重 + 原始出现顺序。
     *
     * @param language 受支持语言
     * @param quality  q 权重（0.0 ~ 1.0）
     * @param order    在请求头中的出现序号，用于同权重时保持稳定
     */
    private record Candidate(String language, double quality, int order) {
    }

    /**
     * 从 {@code Accept-Language} 头按 HTTP 协商语义取最优受支持语言。
     *
     * <p>解析每项的 {@code ;q=} 权重：缺省为 1.0，{@code q=0} 表示明确拒绝，
     * 按权重降序选取（同权重按出现顺序）。修复前只按出现顺序取首个，
     * 会把 {@code zh-CN;q=0, en-US;q=0.9} 误判为中文——与 RFC 9110 协商语义相悖。</p>
     *
     * @param header 请求头原文，可为 {@code null}
     * @return 受支持语言；无有效项时返回默认语言
     */
    public static String fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        List<Candidate> candidates = new ArrayList<>();
        int order = 0;
        for (String part : header.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String[] segments = entry.split(";");
            String tag = segments[0].trim();
            double quality = 1.0;
            for (int i = 1; i < segments.length; i++) {
                String parameter = segments[i].trim();
                if (parameter.regionMatches(true, 0, "q=", 0, 2)) {
                    quality = parseQuality(parameter.substring(2));
                    break;
                }
            }
            String normalized = normalize(tag);
            if (normalized != null && quality > 0.0) {
                candidates.add(new Candidate(normalized, quality, order));
            }
            order++;
        }
        if (candidates.isEmpty()) {
            return DEFAULT_LANGUAGE;
        }
        // 稳定排序：权重降序；同权重按出现顺序（order 升序）
        candidates.sort((a, b) -> {
            int byQuality = Double.compare(b.quality(), a.quality());
            return byQuality != 0 ? byQuality : Integer.compare(a.order(), b.order());
        });
        return candidates.get(0).language();
    }

    /**
     * 解析 q 权重，非法值按 0 处理（视为不可接受）。
     *
     * @param raw q= 之后的文本
     * @return 0.0 ~ 1.0 的权重
     */
    private static double parseQuality(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return value >= 0.0 && value <= 1.0 ? value : 0.0;
        } catch (NumberFormatException ex) {
            return 0.0;
        }
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

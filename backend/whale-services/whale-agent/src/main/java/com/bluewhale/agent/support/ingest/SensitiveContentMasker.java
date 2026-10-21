package com.bluewhale.agent.support.ingest;

import java.util.regex.Pattern;

/**
 * 敏感内容脱敏：入库前遮蔽明显的密钥类键值。
 *
 * <p>仅处理 {@code password / passwd / secret / token / accessKey} 等键的赋值片段，
 * 保留键名便于检索，值统一替换为 {@code ***}。</p>
 *
 * @author BlueWhale
 */
public final class SensitiveContentMasker {

    /** 敏感键值对（仅遮蔽值） */
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|access[_-]?key|api[_-]?key)\\b\\s*[:=]\\s*[\"']?([^\"'\\s,;)}{]+)");

    /** 工具类不允许实例化 */
    private SensitiveContentMasker() {
    }

    /**
     * 脱敏文本。
     *
     * @param text 原文
     * @return 脱敏后的文本；入参为空返回空串
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return SECRET_ASSIGNMENT.matcher(text).replaceAll("$1=***");
    }
}

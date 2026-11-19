package com.bluewhale.message.support;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量模板渲染：把 {@code ${key}} 替换为 payload 中的值。
 *
 * <p>M0 不引入模板引擎，避免为简单占位符增加依赖与注入面；未知占位符保留原样，
 * 便于生产者发现模板与变量不匹配。</p>
 *
 * <p>替换必须<b>单遍</b>完成：按占位符正则一次性扫描模板并逐段替换，不把替换结果
 * 重新投入下一轮扫描。否则变量值里若携带 {@code ${other}}，会被后续轮次继续替换，
 * 形成模板注入（用户可通过 payload 改写模板其它占位符）。</p>
 *
 * @author BlueWhale
 */
public final class TemplateRenderer {

    /** 占位符语法：{@code ${key}}，键名不含右花括号 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TemplateRenderer() {
    }

    /**
     * 渲染模板。
     *
     * @param template 模板文本
     * @param payload  变量
     * @return 渲染结果；模板为空时返回空串
     */
    public static String render(String template, Map<String, Object> payload) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        if (payload == null || payload.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = payload.containsKey(key)
                    ? Objects.toString(payload.get(key), "")
                    : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

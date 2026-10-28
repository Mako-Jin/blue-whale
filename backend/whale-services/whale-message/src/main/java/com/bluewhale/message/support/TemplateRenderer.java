package com.bluewhale.message.support;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * 轻量模板渲染：把 {@code ${key}} 替换为 payload 中的值。
 *
 * <p>M0 不引入模板引擎，避免为简单占位符增加依赖与注入面；未知占位符保留原样，
 * 便于生产者发现模板与变量不匹配。</p>
 *
 * @author BlueWhale
 */
public final class TemplateRenderer {

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
        String result = template;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result = result.replace("${" + entry.getKey() + "}",
                    Objects.toString(entry.getValue(), ""));
        }
        return result;
    }
}

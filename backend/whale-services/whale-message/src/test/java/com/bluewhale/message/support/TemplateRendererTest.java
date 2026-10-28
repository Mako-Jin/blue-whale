package com.bluewhale.message.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TemplateRenderer} 单元测试。
 *
 * @author BlueWhale
 */
class TemplateRendererTest {

    @Test
    @DisplayName("替换已知占位符，未知占位符保留")
    void shouldRenderKnownAndKeepUnknown() {
        String result = TemplateRenderer.render("你好 ${name}，${missing}", Map.of("name", "蓝鲸"));

        assertEquals("你好 蓝鲸，${missing}", result);
    }

    @Test
    @DisplayName("变量为空时原样返回")
    void shouldKeepTemplateWhenPayloadEmpty() {
        assertEquals("你好 ${name}", TemplateRenderer.render("你好 ${name}", Map.of()));
        assertEquals("你好 ${name}", TemplateRenderer.render("你好 ${name}", null));
    }

    @Test
    @DisplayName("空模板返回空串")
    void shouldReturnEmptyForBlankTemplate() {
        assertEquals("", TemplateRenderer.render(null, Map.of("name", "蓝鲸")));
        assertEquals("", TemplateRenderer.render("  ", Map.of("name", "蓝鲸")));
    }

    @Test
    @DisplayName("null 变量值替换为空串")
    void shouldRenderNullValueAsEmpty() {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("name", null);

        assertEquals("你好 ", TemplateRenderer.render("你好 ${name}", payload));
    }
}

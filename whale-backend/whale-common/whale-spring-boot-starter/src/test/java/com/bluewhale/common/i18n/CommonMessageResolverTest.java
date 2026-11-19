package com.bluewhale.common.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 公共错误资源包解析测试。
 */
class CommonMessageResolverTest {

    private final CommonMessageResolver resolver = new CommonMessageResolver();

    @Test
    @DisplayName("英文环境命中英文文案，中文环境命中中文文案")
    void shouldResolveByLocale() {
        assertEquals("Resource not found",
                resolver.resolve("common.error.not-found", null, Locale.US, "资源不存在"));
        assertEquals("资源不存在",
                resolver.resolve("common.error.not-found", null, Locale.SIMPLIFIED_CHINESE, "资源不存在"));
    }

    @Test
    @DisplayName("占位参数按语言插值")
    void shouldFormatArguments() {
        assertEquals("Missing parameter: page",
                resolver.resolve("common.error.missing-parameter", new Object[]{"page"}, Locale.US, null));
        assertEquals("缺少参数: page",
                resolver.resolve("common.error.missing-parameter", new Object[]{"page"},
                        Locale.SIMPLIFIED_CHINESE, null));
    }

    @Test
    @DisplayName("键为空或未命中时返回调用方兜底文案")
    void shouldFallbackWhenMissing() {
        assertEquals("兜底", resolver.resolve(null, null, Locale.US, "兜底"));
        assertEquals("兜底", resolver.resolve(" ", null, Locale.US, "兜底"));
        assertEquals("兜底", resolver.resolve("not.exists.key", null, Locale.US, "兜底"));
    }
}

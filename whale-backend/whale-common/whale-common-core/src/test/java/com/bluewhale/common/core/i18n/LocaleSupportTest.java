package com.bluewhale.common.core.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 语言标签归一化与协商测试。
 */
class LocaleSupportTest {

    @Test
    @DisplayName("归一化：中文与英文的常见变体统一到 zh-CN / en-US")
    void shouldNormalizeCommonVariants() {
        assertEquals(LocaleSupport.CHINESE, LocaleSupport.normalize("zh_CN"));
        assertEquals(LocaleSupport.CHINESE, LocaleSupport.normalize("zh-Hans"));
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.normalize("en"));
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.normalize("en-GB"));
        assertNull(LocaleSupport.normalize("ja-JP"));
        assertNull(LocaleSupport.normalize(" "));
        assertNull(LocaleSupport.normalize(null));
    }

    @Test
    @DisplayName("Accept-Language：按顺序取首个受支持语言，均不支持时回落中文")
    void shouldPickFirstSupportedAcceptLanguage() {
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.fromAcceptLanguage("en-US,en;q=0.9"));
        assertEquals(LocaleSupport.CHINESE, LocaleSupport.fromAcceptLanguage("ja-JP,zh-TW;q=0.8"));
        assertEquals(LocaleSupport.DEFAULT_LANGUAGE, LocaleSupport.fromAcceptLanguage("ja-JP"));
        assertEquals(LocaleSupport.DEFAULT_LANGUAGE, LocaleSupport.fromAcceptLanguage(null));
    }

    @Test
    @DisplayName("解析：显式 X-Lang 优先于 Accept-Language")
    void shouldPreferExplicitLanguageHeader() {
        assertEquals(LocaleSupport.CHINESE, LocaleSupport.resolve("zh-CN", "en-US"));
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.resolve("en_GB", "zh-CN"));
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.resolve("ja-JP", "en-US"));
        assertEquals(LocaleSupport.DEFAULT_LANGUAGE, LocaleSupport.resolve(null, null));
    }
}

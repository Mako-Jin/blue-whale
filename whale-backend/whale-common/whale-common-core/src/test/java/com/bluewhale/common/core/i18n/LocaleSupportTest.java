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
        assertEquals(LocaleSupport.DEFAULT_LANGUAGE, LocaleSupport.fromAcceptLanguage("  "));
        assertEquals(LocaleSupport.DEFAULT_LANGUAGE, LocaleSupport.fromAcceptLanguage(null));
    }

    @Test
    @DisplayName("Accept-Language 协商：按 q 权重降序，q=0 视为拒绝，缺省为 1.0")
    void shouldHonorQualityWeights() {
        // q=0 的中文被拒绝，即便排在前也应选英文（修复前的 bug）
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.fromAcceptLanguage("zh-CN;q=0, en-US;q=0.9"));
        // 权重高的优先，即便出现顺序靠后
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.fromAcceptLanguage("zh-CN;q=0.5, en-US;q=0.9"));
        assertEquals(LocaleSupport.CHINESE, LocaleSupport.fromAcceptLanguage("en-US;q=0.4, zh-CN;q=0.8"));
        // 同权重按出现顺序（稳定）
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.fromAcceptLanguage("en-US;q=0.8, zh-CN;q=0.8"));
        assertEquals(LocaleSupport.CHINESE, LocaleSupport.fromAcceptLanguage("zh-CN;q=0.8, en-US;q=0.8"));
        // 非法 q 视为不可接受；全被拒绝时回落默认
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.fromAcceptLanguage("zh-CN;q=oops, en-US;q=0.7"));
        assertEquals(LocaleSupport.DEFAULT_LANGUAGE,
                LocaleSupport.fromAcceptLanguage("zh-CN;q=0, en-US;q=0"));
        // q 超出 [0,1] 范围视为不可接受
        assertEquals(LocaleSupport.CHINESE, LocaleSupport.fromAcceptLanguage("en-US;q=1.5, zh-CN;q=0.8"));
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.fromAcceptLanguage("zh-CN;q=-0.2, en-US;q=0.6"));
        // 空的头项与无 q 的扩展参数（如 ;foo=bar）不影响解析
        assertEquals(LocaleSupport.ENGLISH, LocaleSupport.fromAcceptLanguage("en-US;foo=bar,, zh-CN;q=0.3"));
    }

    @Test
    @DisplayName("默认 Locale 显式为 zh-CN，不随 JVM 默认语言漂移")
    void defaultLocaleShouldBeExplicitChinese() {
        assertEquals("zh", LocaleSupport.DEFAULT_LOCALE.getLanguage());
        assertEquals("CN", LocaleSupport.DEFAULT_LOCALE.getCountry());
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

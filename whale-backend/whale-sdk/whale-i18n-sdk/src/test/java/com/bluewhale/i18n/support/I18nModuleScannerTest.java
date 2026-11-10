package com.bluewhale.i18n.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link I18nModuleScanner} 模块文案扫描单元测试
 *
 * @author BlueWhale
 */
class I18nModuleScannerTest {

    private static final String SCAN_PATTERN = "classpath*:i18n/**/messages.properties";

    private static final String DEFAULT_BASENAME = "classpath:i18n/messages";

    @Test
    @DisplayName("scan：识别 auth/file 模块文案并追加全局兜底 basename")
    void shouldScanModulesAndAppendDefault() {
        I18nModuleScanner scanner = new I18nModuleScanner(SCAN_PATTERN, DEFAULT_BASENAME, true);

        List<String> basenames = scanner.scan();

        assertTrue(basenames.contains("classpath:i18n/auth/messages"));
        assertTrue(basenames.contains("classpath:i18n/file/messages"));
        assertEquals(DEFAULT_BASENAME, basenames.get(basenames.size() - 1));
    }

    @Test
    @DisplayName("scan：扫描关闭时仅返回全局兜底 basename")
    void shouldReturnDefaultWhenScanDisabled() {
        I18nModuleScanner scanner = new I18nModuleScanner(SCAN_PATTERN, DEFAULT_BASENAME, false);

        List<String> basenames = scanner.scan();

        assertEquals(List.of(DEFAULT_BASENAME), basenames);
    }

    @Test
    @DisplayName("scan：无匹配模块时仅返回全局兜底 basename")
    void shouldReturnDefaultWhenNoModuleMatched() {
        I18nModuleScanner scanner = new I18nModuleScanner("classpath*:i18n/not-exists/messages.properties",
                DEFAULT_BASENAME, true);

        List<String> basenames = scanner.scan();

        assertEquals(List.of(DEFAULT_BASENAME), basenames);
    }

    @Test
    @DisplayName("scan：结果去重且按字典序排列")
    void shouldDeduplicateAndSort() {
        I18nModuleScanner scanner = new I18nModuleScanner(SCAN_PATTERN, DEFAULT_BASENAME, true);

        List<String> basenames = scanner.scan();

        assertEquals(basenames.size(), basenames.stream().distinct().count());
        List<String> modulePart = basenames.subList(0, basenames.size() - 1);
        assertEquals(modulePart.stream().sorted().toList(), modulePart);
    }
}

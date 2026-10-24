package com.bluewhale.tools.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link StringUtils} 单元测试
 *
 * @author BlueWhale
 */
class StringUtilsTest {

    /**
     * 空白判定：null、空串、纯空白均为空白
     */
    @Test
    @DisplayName("isBlank 覆盖 null / 空串 / 纯空白 / 非空白")
    void shouldJudgeBlank() {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank("   "));
        assertFalse(StringUtils.isBlank("a"));
        assertFalse(StringUtils.isBlank(" a "));
    }

    /**
     * 非空白判定为空白判定的反向
     */
    @Test
    @DisplayName("isNotBlank 与 isBlank 互为反向")
    void shouldJudgeNotBlank() {
        assertFalse(StringUtils.isNotBlank(null));
        assertFalse(StringUtils.isNotBlank(""));
        assertFalse(StringUtils.isNotBlank(" \t "));
        assertTrue(StringUtils.isNotBlank("a"));
    }

    /**
     * 空串判定：纯空白不算空
     */
    @Test
    @DisplayName("isEmpty 只判定 null 与长度 0")
    void shouldJudgeEmpty() {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isEmpty("a"));
    }

    /**
     * 非空判定
     */
    @Test
    @DisplayName("isNotEmpty 与 isEmpty 互为反向")
    void shouldJudgeNotEmpty() {
        assertFalse(StringUtils.isNotEmpty(null));
        assertFalse(StringUtils.isNotEmpty(""));
        assertTrue(StringUtils.isNotEmpty(" "));
        assertTrue(StringUtils.isNotEmpty("a"));
    }

    /**
     * 空白时回落默认值
     */
    @Test
    @DisplayName("defaultIfBlank 仅在空白时回落默认值")
    void shouldUseDefaultWhenBlank() {
        assertEquals("fallback", StringUtils.defaultIfBlank(null, "fallback"));
        assertEquals("fallback", StringUtils.defaultIfBlank("  ", "fallback"));
        assertEquals("value", StringUtils.defaultIfBlank("value", "fallback"));
        assertNull(StringUtils.defaultIfBlank(null, null));
    }

    /**
     * 去空白后空白返回 null
     */
    @Test
    @DisplayName("trimToNull 去空白后为空返回 null")
    void shouldTrimToNull() {
        assertNull(StringUtils.trimToNull(null));
        assertNull(StringUtils.trimToNull("   "));
        assertEquals("abc", StringUtils.trimToNull("  abc  "));
    }
}

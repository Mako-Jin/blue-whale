package com.bluewhale.tools.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AssertX} 单元测试：五类断言的通过与失败两条路径。
 *
 * @author BlueWhale
 */
class AssertXTest {

    @Test
    @DisplayName("isTrue：表达式为 false 时抛出 IllegalArgumentException 并带上消息")
    void shouldThrowWhenExpressionFalse() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AssertX.isTrue(false, "条件不成立"));

        assertEquals("条件不成立", ex.getMessage());
        assertDoesNotThrow(() -> AssertX.isTrue(true, "条件不成立"));
    }

    @Test
    @DisplayName("notNull：null 抛异常，非 null 通过")
    void shouldRejectNullObject() {
        assertThrows(IllegalArgumentException.class, () -> AssertX.notNull(null, "对象不能为空"));

        assertDoesNotThrow(() -> AssertX.notNull(new Object(), "对象不能为空"));
    }

    @Test
    @DisplayName("isNull：非 null 抛异常，null 通过")
    void shouldRejectNonNullObject() {
        assertThrows(IllegalArgumentException.class, () -> AssertX.isNull("x", "对象必须为空"));

        assertDoesNotThrow(() -> AssertX.isNull(null, "对象必须为空"));
    }

    @Test
    @DisplayName("notBlank：null、空串、纯空白都视为空白并抛异常")
    void shouldRejectBlankText() {
        assertThrows(IllegalArgumentException.class, () -> AssertX.notBlank(null, "文本不能为空"));
        assertThrows(IllegalArgumentException.class, () -> AssertX.notBlank("", "文本不能为空"));
        assertThrows(IllegalArgumentException.class, () -> AssertX.notBlank("   ", "文本不能为空"));

        assertDoesNotThrow(() -> AssertX.notBlank("  content ", "文本不能为空"));
    }

    @Test
    @DisplayName("isBlank：非空白文本抛异常，空白文本通过")
    void shouldRejectNotBlankText() {
        assertThrows(IllegalArgumentException.class, () -> AssertX.isBlank("x", "文本必须为空"));

        assertDoesNotThrow(() -> AssertX.isBlank(null, "文本必须为空"));
        assertDoesNotThrow(() -> AssertX.isBlank("  ", "文本必须为空"));
    }
}

package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FormJson} JSON 嵌套深度护栏单元测试。
 *
 * @author BlueWhale
 */
class FormJsonTest {

    @Test
    @DisplayName("assertDepth：常规对象与数组放行")
    void shouldAllowNormalJson() {
        assertDoesNotThrow(() -> FormJson.assertDepth("{\"a\":[1,2,{\"b\":\"c\"}]}", "测试"));
        assertDoesNotThrow(() -> FormJson.assertDepth(null, "测试"));
        assertDoesNotThrow(() -> FormJson.assertDepth("", "测试"));
    }

    @Test
    @DisplayName("assertDepth：形如 JSON 的普通文本（非对象）也按括号计数放行")
    void shouldAllowPlainText() {
        assertDoesNotThrow(() -> FormJson.assertDepth("hello (world) [ok]", "测试"));
    }

    @Test
    @DisplayName("assertDepth：恰好 64 层放行，65 层拒绝")
    void shouldRejectTooDeepJson() {
        String depth64 = "[".repeat(FormJson.MAX_DEPTH) + "]".repeat(FormJson.MAX_DEPTH);
        assertDoesNotThrow(() -> FormJson.assertDepth(depth64, "测试"));

        String depth65 = "[".repeat(FormJson.MAX_DEPTH + 1) + "]".repeat(FormJson.MAX_DEPTH + 1);
        BizException e = assertThrows(BizException.class, () -> FormJson.assertDepth(depth65, "测试"));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("assertDepth：字符串字面量内的括号不计数（含转义引号）")
    void shouldIgnoreBracketsInStrings() {
        String json = "{\"a\":\"[[[[[[[[[[[[[[[[[[[[\",\"b\":\"\\\"]\\\"\"}";
        assertDoesNotThrow(() -> FormJson.assertDepth(json, "测试"));
    }
}

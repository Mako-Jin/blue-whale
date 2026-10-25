package com.bluewhale.pm.domain.service;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ProjectKeyPolicy} 项目键规则单元测试。
 *
 * @author BlueWhale
 */
class ProjectKeyPolicyTest {

    @Test
    @DisplayName("normalize：去空白并转大写")
    void shouldNormalizeToUpperCase() {
        assertEquals("BW", ProjectKeyPolicy.normalize(" bw "));
        assertEquals("BW2", ProjectKeyPolicy.normalize("bw2"));
    }

    @Test
    @DisplayName("normalize：单字符拒绝")
    void shouldRejectTooShortKey() {
        BizException exception = assertThrows(BizException.class, () -> ProjectKeyPolicy.normalize("A"));
        assertEquals(400, exception.getCode());
    }

    @Test
    @DisplayName("normalize：超长键拒绝")
    void shouldRejectTooLongKey() {
        assertThrows(BizException.class, () -> ProjectKeyPolicy.normalize("ABCDEFGHIJK"));
    }

    @Test
    @DisplayName("normalize：非字母数字拒绝")
    void shouldRejectIllegalCharacter() {
        assertThrows(BizException.class, () -> ProjectKeyPolicy.normalize("BW-1"));
    }

    @Test
    @DisplayName("normalize：空值拒绝")
    void shouldRejectBlankKey() {
        assertThrows(BizException.class, () -> ProjectKeyPolicy.normalize("   "));
        assertThrows(BizException.class, () -> ProjectKeyPolicy.normalize(null));
    }
}

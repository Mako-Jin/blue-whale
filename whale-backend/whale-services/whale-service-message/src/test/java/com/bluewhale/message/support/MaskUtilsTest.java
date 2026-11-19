package com.bluewhale.message.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MaskUtils} 脱敏规则单元测试。
 *
 * @author BlueWhale
 */
class MaskUtilsTest {

    @Test
    @DisplayName("短信接收人按手机号脱敏")
    void shouldMaskPhoneForSms() {
        assertEquals("138****0000", MaskUtils.maskRecipient("SMS", "13800000000"));
    }

    @Test
    @DisplayName("邮件接收人只保留本地部分首字符")
    void shouldMaskEmail() {
        assertEquals("a***@bluewhale.com", MaskUtils.maskRecipient("EMAIL", "alice@bluewhale.com"));
    }

    @Test
    @DisplayName("IM 用户ID不属于敏感信息，原样返回")
    void shouldKeepImRecipient() {
        assertEquals("1001", MaskUtils.maskRecipient("IM", "1001"));
    }

    @Test
    @DisplayName("空值原样返回")
    void shouldKeepBlankValue() {
        assertNull(MaskUtils.maskRecipient("SMS", null));
        assertEquals("", MaskUtils.maskRecipient("EMAIL", ""));
    }
}

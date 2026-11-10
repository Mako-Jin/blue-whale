package com.bluewhale.agent.support.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 敏感内容脱敏测试。
 *
 * @author BlueWhale
 */
class SensitiveContentMaskerTest {

    @Test
    @DisplayName("遮蔽密码/令牌类字段值")
    void shouldMaskSecretValues() {
        String masked = SensitiveContentMasker.mask("password: abc123\ntoken=xyz\nname: 正常值");
        assertEquals("password=***\ntoken=***\nname: 正常值", masked);
        assertFalse(masked.contains("abc123"));
        assertFalse(masked.contains("xyz"));
    }
}

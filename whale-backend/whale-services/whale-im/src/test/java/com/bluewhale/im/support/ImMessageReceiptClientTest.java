package com.bluewhale.im.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ImMessageReceiptClient} 标识识别单元测试。
 *
 * @author BlueWhale
 */
class ImMessageReceiptClientTest {

    @Test
    @DisplayName("只识别消息服务生成的 msg-<消息ID>-<用户ID> 形态")
    void shouldRecognizeMessageServiceClientId() {
        assertTrue(ImMessageReceiptClient.isMessageServiceClientId("msg-1234567890123456789-9876543210987654321"));
        assertFalse(ImMessageReceiptClient.isMessageServiceClientId("pm-ASSIGN-101-1"));
        assertFalse(ImMessageReceiptClient.isMessageServiceClientId("msg-abc-1"));
        assertFalse(ImMessageReceiptClient.isMessageServiceClientId(null));
    }

    @Test
    @DisplayName("标识归属用户需与接收人一致才上报送达")
    void shouldMatchUserInClientId() {
        assertTrue(ImMessageReceiptClient.matchesUser("msg-1-900", 900L));
        assertFalse(ImMessageReceiptClient.matchesUser("msg-1-900", 901L));
        assertFalse(ImMessageReceiptClient.matchesUser("msg-1-900", null));
        assertFalse(ImMessageReceiptClient.matchesUser(null, 900L));
    }
}

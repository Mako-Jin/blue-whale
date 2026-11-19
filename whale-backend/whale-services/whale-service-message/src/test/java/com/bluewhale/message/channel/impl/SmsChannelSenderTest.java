package com.bluewhale.message.channel.impl;

import com.bluewhale.message.config.MessageChannelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SmsChannelSender} 回执标识解析单元测试。
 *
 * @author BlueWhale
 */
class SmsChannelSenderTest {

    private final MessageChannelProperties properties = new MessageChannelProperties();

    private final SmsChannelSender sender = new SmsChannelSender(properties, new ObjectMapper());

    @Test
    @DisplayName("按默认路径 data.messageId 解析供应商消息标识")
    void shouldResolveProviderMessageId() {
        String body = "{\"code\":0,\"message\":\"ok\",\"data\":{\"messageId\":\"sms-1001\"}}";

        assertEquals("sms-1001", sender.resolveProviderMessageId(properties.getSms(), body));
    }

    @Test
    @DisplayName("业务码非成功值时按失败处理")
    void shouldRejectBusinessFailure() {
        String body = "{\"code\":500,\"message\":\"gateway error\"}";

        assertThrows(IllegalStateException.class,
                () -> sender.resolveProviderMessageId(properties.getSms(), body));
    }

    @Test
    @DisplayName("非 JSON 响应不解析标识，保持 2xx 即成功")
    void shouldReturnNullForNonJsonResponse() {
        assertNull(sender.resolveProviderMessageId(properties.getSms(), "OK"));
        assertNull(sender.resolveProviderMessageId(properties.getSms(), ""));
    }
}

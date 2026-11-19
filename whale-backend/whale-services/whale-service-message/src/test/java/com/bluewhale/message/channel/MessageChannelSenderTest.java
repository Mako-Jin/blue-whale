package com.bluewhale.message.channel;

import com.bluewhale.message.channel.impl.InAppChannelSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MessageChannelSender} 默认能力单元测试。
 *
 * @author BlueWhale
 */
class MessageChannelSenderTest {

    @Test
    @DisplayName("批量发送默认实现逐条调用 send 并按入参顺序返回")
    void sendBatchShouldDelegateToSendInOrder() {
        MessageChannelSender sender = new InAppChannelSender();
        List<ChannelMessage> messages = List.of(
                ChannelMessage.builder().messageId(1L).build(),
                ChannelMessage.builder().messageId(2L).build());

        List<ChannelResult> results = sender.sendBatch(messages);

        assertEquals(2, results.size());
        assertEquals("1", results.get(0).getProviderMessageId());
        assertEquals("2", results.get(1).getProviderMessageId());
    }
}

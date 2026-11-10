package com.bluewhale.message.channel.impl;

import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.enums.MessageChannel;
import org.springframework.stereotype.Component;

/**
 * 站内信渠道：消息本身已落 {@code msg_record}，查询接口即可读取，因此无需外部调用。
 *
 * @author BlueWhale
 */
@Component
public class InAppChannelSender implements MessageChannelSender {

    @Override
    public MessageChannel channel() {
        return MessageChannel.INAPP;
    }

    @Override
    public ChannelResult send(ChannelMessage message) {
        return ChannelResult.sent(String.valueOf(message.getMessageId()));
    }
}

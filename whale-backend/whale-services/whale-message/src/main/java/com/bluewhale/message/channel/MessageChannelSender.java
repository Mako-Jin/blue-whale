package com.bluewhale.message.channel;

import com.bluewhale.message.enums.MessageChannel;

/**
 * 消息渠道适配器（输出端口）。
 *
 * <p>实现类只负责把一条已渲染好的消息翻译成渠道协议；不加载聚合、不决定业务状态。
 * 新增渠道时实现本接口并注册为 Spring Bean 即可，发送内核无需修改。</p>
 *
 * @author BlueWhale
 */
public interface MessageChannelSender {

    /**
     * 支持的渠道。
     *
     * @return 渠道
     */
    MessageChannel channel();

    /**
     * 发送单条消息给单个接收人。
     *
     * @param message 渠道发送上下文
     * @return 发送结果
     */
    ChannelResult send(ChannelMessage message);
}

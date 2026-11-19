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

    /**
     * 主动查询渠道侧的投递状态（可选能力）。
     *
     * <p>默认返回 {@code null} 表示渠道不提供主动查询：发送内核只在有实现时调用，
     * 未实现不会影响发送主流程。</p>
     *
     * @param providerMessageId 渠道侧消息标识
     * @return 渠道侧状态；不支持主动查询时为 {@code null}
     */
    default ChannelResult queryStatus(String providerMessageId) {
        return null;
    }

    /**
     * 是否支持回执上报（送达/退信）。
     *
     * <p>回执受理入口据此拒绝"渠道本不支持回执却上报"的脏数据。</p>
     *
     * @return 支持返回 {@code true}
     */
    default boolean supportsReceipt() {
        return false;
    }

    /**
     * 是否支持已读回执。
     *
     * <p>不支持已读的渠道上报 {@code READ} 时按脏数据拒绝，避免已读率被虚增。</p>
     *
     * @return 支持返回 {@code true}
     */
    default boolean supportsReadReceipt() {
        return false;
    }

    /**
     * 单条内容长度上限（字符）。
     *
     * <p>渲染/发送侧按渠道上限截断，避免各实现散落魔法值。默认不限制。</p>
     *
     * @return 内容上限
     */
    default int maxContentLength() {
        return Integer.MAX_VALUE;
    }
}

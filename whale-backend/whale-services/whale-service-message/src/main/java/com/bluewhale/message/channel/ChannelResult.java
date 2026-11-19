package com.bluewhale.message.channel;

import com.bluewhale.message.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 渠道发送结果。
 *
 * @author BlueWhale
 */
@Data
@AllArgsConstructor
public class ChannelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递状态 */
    private DeliveryStatus status;

    /** 渠道回执标识（可选） */
    private String providerMessageId;

    /** 失败/跳过原因（可选） */
    private String errorMessage;

    /**
     * 已提交渠道。
     *
     * @param providerMessageId 渠道回执标识
     * @return 成功结果
     */
    public static ChannelResult sent(String providerMessageId) {
        return new ChannelResult(DeliveryStatus.SENT, providerMessageId, null);
    }

    /**
     * mock 模式未发起真实请求。
     *
     * @return mock 结果
     */
    public static ChannelResult mocked() {
        return new ChannelResult(DeliveryStatus.MOCKED, null, "mock mode");
    }

    /**
     * 渠道未启用/缺少接收信息。
     *
     * @param reason 跳过原因
     * @return 跳过结果
     */
    public static ChannelResult skipped(String reason) {
        return new ChannelResult(DeliveryStatus.SKIPPED, null, reason);
    }

    /**
     * 渠道调用失败。
     *
     * @param reason 失败原因
     * @return 失败结果
     */
    public static ChannelResult failed(String reason) {
        return new ChannelResult(DeliveryStatus.FAILED, null, reason);
    }

    /**
     * 消息已过期，未发起投递。
     *
     * @param reason 说明
     * @return 过期结果
     */
    public static ChannelResult expired(String reason) {
        return new ChannelResult(DeliveryStatus.EXPIRED, null, reason);
    }
}

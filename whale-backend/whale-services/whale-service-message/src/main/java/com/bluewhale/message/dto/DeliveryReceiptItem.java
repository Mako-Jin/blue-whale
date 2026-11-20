package com.bluewhale.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单条渠道回执。
 *
 * @author BlueWhale
 */
@Data
public class DeliveryReceiptItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道编码：INAPP / EMAIL / SMS / IM */
    @NotBlank(message = "{validation.message.receipt.channel-required}")
    @Size(max = 16, message = "{validation.message.receipt.channel-size}")
    private String channel;

    /** 渠道回执唯一ID；缺省时由渠道+提供方消息ID+状态+回执时间派生 */
    @Size(max = 96, message = "{validation.message.receipt.receipt-id-size}")
    private String receiptId;

    /**
     * 渠道侧消息标识，用于匹配投递记录
     *
     * <p>各渠道取值：EMAIL = 邮件 {@code Message-ID} 头；SMS = 网关响应中的
     * {@code providerIdField}（默认 {@code data.messageId}）；IM = 投递时的 {@code clientMsgId}；
     * INAPP = 渠道发送回写的消息标识。</p>
     */
    @Size(max = 128, message = "{validation.message.receipt.provider-message-id-size}")
    private String providerMessageId;

    /**
     * 接收人标识（可选），仅用于未携带 {@code providerMessageId} 时的兜底匹配
     *
     * <p>与消息投递记录 {@code msg_delivery.recipient} 同口径：EMAIL 填邮箱、SMS 填手机号、
     * IM 填用户ID、INAPP 填用户ID。</p>
     */
    @Size(max = 160, message = "{validation.message.receipt.recipient-size}")
    private String recipient;

    /** 回执状态：DELIVERED / READ / BOUNCED（参数校验阶段拒绝非法值，避免整批事务回滚） */
    @NotBlank(message = "{validation.message.receipt.status-required}")
    @Size(max = 16, message = "{validation.message.receipt.status-size}")
    @Pattern(regexp = "DELIVERED|READ|BOUNCED", message = "{validation.message.receipt.status-pattern}")
    private String status;

    /** 渠道回执时间；缺省取上报时刻 */
    private LocalDateTime receiptTime;

    /** 退信/失败错误码 */
    @Size(max = 64, message = "{validation.message.receipt.error-code-size}")
    private String errorCode;

    /** 退信/失败说明 */
    @Size(max = 512, message = "{validation.message.receipt.error-message-size}")
    private String errorMessage;

    /** 渠道原始报文（截断保留，便于排障） */
    @Size(max = 4000, message = "{validation.message.receipt.raw-payload-size}")
    private String rawPayload;
}

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
    @NotBlank(message = "回执渠道不能为空")
    @Size(max = 16, message = "回执渠道长度不能超过 16")
    private String channel;

    /** 渠道回执唯一ID；缺省时由渠道+提供方消息ID+状态+回执时间派生 */
    @Size(max = 96, message = "回执ID长度不能超过 96")
    private String receiptId;

    /**
     * 渠道侧消息标识，用于匹配投递记录
     *
     * <p>各渠道取值：EMAIL = 邮件 {@code Message-ID} 头；SMS = 网关响应中的
     * {@code providerIdField}（默认 {@code data.messageId}）；IM = 投递时的 {@code clientMsgId}；
     * INAPP = 渠道发送回写的消息标识。</p>
     */
    @Size(max = 128, message = "渠道消息标识长度不能超过 128")
    private String providerMessageId;

    /**
     * 接收人标识（可选），仅用于未携带 {@code providerMessageId} 时的兜底匹配
     *
     * <p>与消息投递记录 {@code msg_delivery.recipient} 同口径：EMAIL 填邮箱、SMS 填手机号、
     * IM 填用户ID、INAPP 填用户ID。</p>
     */
    @Size(max = 160, message = "接收人标识长度不能超过 160")
    private String recipient;

    /** 回执状态：DELIVERED / READ / BOUNCED（参数校验阶段拒绝非法值，避免整批事务回滚） */
    @NotBlank(message = "回执状态不能为空")
    @Size(max = 16, message = "回执状态长度不能超过 16")
    @Pattern(regexp = "DELIVERED|READ|BOUNCED", message = "回执状态仅支持 DELIVERED / READ / BOUNCED")
    private String status;

    /** 渠道回执时间；缺省取上报时刻 */
    private LocalDateTime receiptTime;

    /** 退信/失败错误码 */
    @Size(max = 64, message = "错误码长度不能超过 64")
    private String errorCode;

    /** 退信/失败说明 */
    @Size(max = 512, message = "错误说明长度不能超过 512")
    private String errorMessage;

    /** 渠道原始报文（截断保留，便于排障） */
    @Size(max = 4000, message = "原始报文长度不能超过 4000")
    private String rawPayload;
}

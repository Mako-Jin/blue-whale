package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道送达回执回调命令（仅内网：whale-message）。
 *
 * @author BlueWhale
 */
@Data
public class DeliveryReceiptCommand {

    /** 消息服务消息ID（回执匹配主键） */
    private Long messageId;

    /** 消息服务投递记录ID（仅用于排障留痕） */
    private Long deliveryId;

    /** 回执流水ID（仅用于排障留痕） */
    private Long receiptId;

    /** 业务标识（消息服务侧为触达任务ID，作为 messageId 缺失时的兜底匹配依据） */
    @Size(max = 64, message = "{validation.crm.delivery-receipt.biz-id-size}")
    private String bizId;

    /** 渠道：EMAIL / SMS / INBOX / IM */
    @NotBlank(message = "{validation.crm.delivery-receipt.channel-required}")
    @Size(max = 16, message = "{validation.crm.delivery-receipt.channel-size}")
    private String channel;

    /** 回执状态：DELIVERED / READ / BOUNCED */
    @NotBlank(message = "{validation.crm.delivery-receipt.status-required}")
    @Size(max = 16, message = "{validation.crm.delivery-receipt.status-size}")
    private String status;

    /** 收件人（messageId 缺失时的兜底匹配依据） */
    @Size(max = 255, message = "{validation.crm.delivery-receipt.recipient-size}")
    private String recipient;

    /** 渠道回执时间 */
    private LocalDateTime receiptTime;

    /** 退信/失败错误码 */
    @Size(max = 64, message = "{validation.crm.delivery-receipt.error-code-size}")
    private String errorCode;

    /** 退信/失败说明 */
    @Size(max = 512, message = "{validation.crm.delivery-receipt.error-message-size}")
    private String errorMessage;
}

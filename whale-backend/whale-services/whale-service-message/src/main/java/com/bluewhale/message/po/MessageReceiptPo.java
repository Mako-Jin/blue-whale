package com.bluewhale.message.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 渠道回执流水实体，对应表 {@code msg_receipt}。
 *
 * <p>一条回执一行，按 {@code receiptKey} 幂等去重；未匹配到投递记录的回执同样落库，
 * 便于运维排查"渠道说有回执、平台没有对应投递"的错配。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_receipt")
public class MessageReceiptPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 回执幂等键（租户内唯一） */
    private String receiptKey;

    /** 渠道：INAPP / EMAIL / SMS / IM */
    private String channel;

    /** 渠道侧消息标识 */
    private String providerMessageId;

    /** 匹配到的投递记录ID */
    private Long deliveryId;

    /** 匹配到的消息ID */
    private Long messageId;

    /** 回执状态：DELIVERED / READ / BOUNCED / UNMATCHED */
    private String receiptStatus;

    /** 匹配结果：MATCHED / UNMATCHED */
    private String matchStatus;

    /** 渠道回执时间 */
    private LocalDateTime receiptTime;

    /** 返信错误码 */
    private String errorCode;

    /** 返信错误说明 */
    private String errorMessage;

    /** 渠道原始回执报文 */
    private String rawPayload;
}

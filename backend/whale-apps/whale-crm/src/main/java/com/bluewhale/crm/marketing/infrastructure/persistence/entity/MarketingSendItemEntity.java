package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批量触达明细实体，对应表 {@code crm_marketing_send_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_marketing_send_item")
public class MarketingSendItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 触达任务 ID */
    private Long sendId;

    /** 营销活动 ID */
    private Long campaignId;

    /** CRM 客户 ID */
    private Long customerId;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名快照 */
    private String contactName;

    /** 收件人 */
    private String recipient;

    /** 触达渠道 */
    private String channel;

    /** 明细状态 */
    private String status;

    /** 消息服务消息ID（回执匹配键） */
    private Long msgId;

    /** 失败原因 */
    private String error;

    /** 发送时间 */
    private LocalDateTime sentAt;

    /** 渠道确认送达时间 */
    private LocalDateTime deliveredAt;

    /** 已读标记：0 未读 / 1 已读 */
    private Integer readFlag;
}

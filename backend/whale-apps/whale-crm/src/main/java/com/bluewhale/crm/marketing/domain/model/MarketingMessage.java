package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

/**
 * 待投递的营销消息。
 *
 * @author BlueWhale
 */
@Data
public class MarketingMessage {

    /** 触达任务 ID */
    private Long sendId;

    /** 营销活动 ID */
    private Long campaignId;

    /** CRM 客户 ID */
    private Long customerId;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名 */
    private String contactName;

    /** 收件人（邮箱 / 手机号 / 用户标识） */
    private String recipient;

    /** 触达渠道 */
    private SendChannel channel;

    /** 消息模板编码 */
    private String templateCode;

    /** 消息主题 */
    private String subject;

    /** 内容文档 ID */
    private Long contentDocId;
}

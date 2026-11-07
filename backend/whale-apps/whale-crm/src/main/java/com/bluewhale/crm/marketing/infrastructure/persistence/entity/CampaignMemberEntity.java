package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动人群成员实体，对应表 {@code crm_campaign_member}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_campaign_member")
public class CampaignMemberEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 人群快照 ID */
    private Long audienceId;

    /** CRM 客户 ID */
    private Long customerId;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名快照 */
    private String contactName;

    /** 邮箱哈希 */
    private String emailHash;

    /** 手机号哈希 */
    private String phoneHash;

    /** 是否排除 */
    private Boolean excluded;

    /** 排除原因 */
    private String excludeReason;
}

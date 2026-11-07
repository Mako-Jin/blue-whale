package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动人群成员（快照明细）。
 *
 * @author BlueWhale
 */
@Data
public class CampaignMember {

    /** 主键 */
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

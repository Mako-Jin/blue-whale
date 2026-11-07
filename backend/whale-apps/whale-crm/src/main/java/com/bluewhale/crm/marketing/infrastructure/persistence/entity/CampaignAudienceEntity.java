package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 活动人群快照实体，对应表 {@code crm_campaign_audience}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_campaign_audience")
public class CampaignAudienceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 营销活动 ID */
    private Long campaignId;

    /** 快照编号 */
    private String snapshotNo;

    /** 人群条件 JSON */
    private String criteriaJson;

    /** 成员数量 */
    private Integer memberCount;

    /** 是否锁定 */
    private Boolean locked;

    /** 锁定时间 */
    private LocalDateTime lockedAt;
}

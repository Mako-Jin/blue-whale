package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 营销活动实体，对应表 {@code crm_campaign}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_campaign")
public class CampaignEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 活动编码 */
    private String campaignCode;

    /** 活动名称 */
    private String campaignName;

    /** 活动类型 */
    private String campaignType;

    /** 活动状态 */
    private String status;

    /** 主要渠道 */
    private String channel;

    /** 预算 */
    private BigDecimal budget;

    /** 实际费用 */
    private BigDecimal actualCost;

    /** 计划开始日期 */
    private LocalDate plannedStart;

    /** 计划结束日期 */
    private LocalDate plannedEnd;

    /** 人群快照 ID */
    private Long audienceId;

    /** 目标人群数量 */
    private Integer audienceCount;

    /** 归因线索数量 */
    private Integer attributedLeadCount;

    /** 归因赢单金额 */
    private BigDecimal attributedWonAmount;

    /** 投入产出比 */
    private BigDecimal roi;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}

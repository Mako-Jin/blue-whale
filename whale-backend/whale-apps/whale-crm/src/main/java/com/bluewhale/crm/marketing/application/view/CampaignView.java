package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.Campaign;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 营销活动视图。
 *
 * @author BlueWhale
 */
@Data
public class CampaignView {

    /** 活动 ID */
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由聚合构建视图。
     *
     * @param campaign 活动聚合
     * @return 视图
     */
    public static CampaignView from(Campaign campaign) {
        CampaignView view = new CampaignView();
        view.setId(campaign.getId());
        view.setCampaignCode(campaign.getCampaignCode());
        view.setCampaignName(campaign.getCampaignName());
        view.setCampaignType(campaign.getCampaignType() == null ? null : campaign.getCampaignType().name());
        view.setStatus(campaign.getStatus() == null ? null : campaign.getStatus().name());
        view.setChannel(campaign.getChannel());
        view.setBudget(campaign.getBudget());
        view.setActualCost(campaign.getActualCost());
        view.setPlannedStart(campaign.getPlannedStart());
        view.setPlannedEnd(campaign.getPlannedEnd());
        view.setAudienceId(campaign.getAudienceId());
        view.setAudienceCount(campaign.getAudienceCount());
        view.setAttributedLeadCount(campaign.getAttributedLeadCount());
        view.setAttributedWonAmount(campaign.getAttributedWonAmount());
        view.setRoi(campaign.getRoi());
        view.setOwnerId(campaign.getOwnerId());
        view.setDeptId(campaign.getDeptId());
        view.setCreateTime(campaign.getCreateTime());
        view.setUpdateTime(campaign.getUpdateTime());
        return view;
    }
}

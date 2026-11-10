package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.CampaignAudience;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动人群快照视图。
 *
 * @author BlueWhale
 */
@Data
public class CampaignAudienceView {

    /** 快照 ID */
    private Long id;

    /** 活动 ID */
    private Long campaignId;

    /** 快照编号 */
    private String snapshotNo;

    /** 人群条件 JSON */
    private String criteriaJson;

    /** 成员数量 */
    private Integer memberCount;

    /** 是否已锁定 */
    private Boolean locked;

    /** 锁定时间 */
    private LocalDateTime lockedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由聚合构建视图。
     *
     * @param audience 人群快照
     * @return 视图
     */
    public static CampaignAudienceView from(CampaignAudience audience) {
        CampaignAudienceView view = new CampaignAudienceView();
        view.setId(audience.getId());
        view.setCampaignId(audience.getCampaignId());
        view.setSnapshotNo(audience.getSnapshotNo());
        view.setCriteriaJson(audience.getCriteriaJson());
        view.setMemberCount(audience.getMemberCount());
        view.setLocked(audience.getLocked());
        view.setLockedAt(audience.getLockedAt());
        view.setCreateTime(audience.getCreateTime());
        return view;
    }
}

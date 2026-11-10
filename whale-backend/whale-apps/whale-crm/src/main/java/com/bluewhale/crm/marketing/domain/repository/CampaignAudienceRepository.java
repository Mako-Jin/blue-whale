package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.CampaignAudience;

/**
 * 活动人群快照仓储。
 *
 * @author BlueWhale
 */
public interface CampaignAudienceRepository {

    /**
     * 新增人群快照。
     *
     * @param audience 人群快照
     */
    void insert(CampaignAudience audience);

    /**
     * 更新人群快照。
     *
     * @param audience 人群快照
     */
    void update(CampaignAudience audience);

    /**
     * 按主键查询人群快照。
     *
     * @param id 主键
     * @return 快照；不存在返回 null
     */
    CampaignAudience findById(Long id);

    /**
     * 查询活动最新人群快照。
     *
     * @param campaignId 活动 ID
     * @return 快照；不存在返回 null
     */
    CampaignAudience findLatestByCampaign(Long campaignId);
}

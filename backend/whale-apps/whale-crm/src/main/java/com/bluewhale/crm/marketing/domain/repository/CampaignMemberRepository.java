package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.CampaignMember;

import java.util.List;

/**
 * 活动人群成员仓储。
 *
 * @author BlueWhale
 */
public interface CampaignMemberRepository {

    /**
     * 批量新增人群成员。
     *
     * @param members 成员列表
     */
    void insertBatch(List<CampaignMember> members);

    /**
     * 查询快照成员。
     *
     * @param audienceId 人群快照 ID
     * @return 成员列表
     */
    List<CampaignMember> findByAudience(Long audienceId);

    /**
     * 逻辑删除快照成员（仅置删除标记，不物理删除）。
     *
     * @param audienceId 人群快照 ID
     */
    void deleteByAudience(Long audienceId);
}

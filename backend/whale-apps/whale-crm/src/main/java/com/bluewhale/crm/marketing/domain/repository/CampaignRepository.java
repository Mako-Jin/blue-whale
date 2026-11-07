package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.Campaign;
import com.bluewhale.crm.marketing.domain.model.CampaignQuery;

import java.util.List;

/**
 * 营销活动仓储。
 *
 * @author BlueWhale
 */
public interface CampaignRepository {

    /**
     * 分页查询营销活动。
     *
     * @param query 查询条件
     * @return 活动列表
     */
    List<Campaign> page(CampaignQuery query);

    /**
     * 统计营销活动数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CampaignQuery query);

    /**
     * 按主键查询活动。
     *
     * @param id 主键
     * @return 活动；不存在返回 null
     */
    Campaign findById(Long id);

    /**
     * 新增活动。
     *
     * @param campaign 活动
     */
    void insert(Campaign campaign);

    /**
     * 更新活动。
     *
     * @param campaign 活动
     */
    void update(Campaign campaign);

    /**
     * 统计指定前缀的活动编码数量（生成流水号）。
     *
     * @param prefix 编码前缀
     * @return 数量
     */
    long countByCodePrefix(String prefix);
}

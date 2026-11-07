package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.MarketingSendItem;
import com.bluewhale.crm.marketing.domain.model.SendChannel;

import java.util.List;

/**
 * 批量触达明细仓储。
 *
 * @author BlueWhale
 */
public interface MarketingSendItemRepository {

    /**
     * 批量新增明细。
     *
     * @param items 明细列表
     */
    void insertBatch(List<MarketingSendItem> items);

    /**
     * 查询触达任务明细。
     *
     * @param sendId 触达任务 ID
     * @return 明细列表
     */
    List<MarketingSendItem> findBySend(Long sendId);

    /**
     * 更新明细。
     *
     * @param item 明细
     */
    void update(MarketingSendItem item);

    /**
     * 判断同一活动同一联系人在同一渠道是否已成功触达（幂等键）。
     *
     * @param campaignId 活动 ID
     * @param contactId 联系人 ID
     * @param channel 渠道
     * @return 已触达返回 true
     */
    boolean existsByCampaignContactChannel(Long campaignId, Long contactId, SendChannel channel);
}

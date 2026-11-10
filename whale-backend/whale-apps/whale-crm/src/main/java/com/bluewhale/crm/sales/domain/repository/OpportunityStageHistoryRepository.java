package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.crm.sales.domain.model.OpportunityStageHistory;

import java.util.List;

/**
 * 商机阶段历史仓储端口（append-only）。
 *
 * @author BlueWhale
 */
public interface OpportunityStageHistoryRepository {

    /**
     * 查询商机阶段历史（按时间升序）。
     *
     * @param opportunityId 商机 ID
     * @return 阶段历史
     */
    List<OpportunityStageHistory> listByOpportunity(Long opportunityId);

    /**
     * 追加阶段历史。
     *
     * @param history 阶段历史
     */
    void insert(OpportunityStageHistory history);
}

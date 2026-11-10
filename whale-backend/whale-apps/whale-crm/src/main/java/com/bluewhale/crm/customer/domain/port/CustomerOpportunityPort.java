package com.bluewhale.crm.customer.domain.port;

import com.bluewhale.crm.customer.domain.model.CustomerOpportunitySummary;

/**
 * 客户商机摘要端口。
 *
 * <p>客户上下文不直接访问商机表；由集成上下文实现端口并做只读投影，
 * 保证六上下文的持久化边界不被穿透。</p>
 *
 * @author BlueWhale
 */
public interface CustomerOpportunityPort {

    /**
     * 汇总指定客户的商机。
     *
     * @param customerId 客户 ID
     * @return 商机摘要；商机服务不可用时返回空摘要
     */
    CustomerOpportunitySummary summarize(Long customerId);
}

package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.crm.sales.domain.model.LeadActivity;

import java.util.List;

/**
 * 线索活动仓储端口（append-only，不提供更新 / 删除）。
 *
 * @author BlueWhale
 */
public interface LeadActivityRepository {

    /**
     * 分页查询线索活动。
     *
     * @param leadId 线索 ID
     * @param pageQuery 分页参数
     * @return 当前页活动
     */
    List<LeadActivity> page(Long leadId, PageQuery pageQuery);

    /**
     * 统计线索活动数。
     *
     * @param leadId 线索 ID
     * @return 总数
     */
    long count(Long leadId);

    /**
     * 追加活动。
     *
     * @param activity 活动
     */
    void insert(LeadActivity activity);
}

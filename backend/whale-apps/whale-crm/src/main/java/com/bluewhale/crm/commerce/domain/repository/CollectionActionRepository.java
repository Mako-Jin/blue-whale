package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.CollectionAction;

import java.util.List;

/**
 * 催收记录仓储端口（追加式）。
 *
 * @author BlueWhale
 */
public interface CollectionActionRepository {

    /**
     * 追加催收记录。
     *
     * @param action 记录
     */
    void insert(CollectionAction action);

    /**
     * 查询指定计划的催收记录。
     *
     * @param planId 计划 ID
     * @return 记录列表（按时间倒序）
     */
    List<CollectionAction> findByPlan(Long planId);
}

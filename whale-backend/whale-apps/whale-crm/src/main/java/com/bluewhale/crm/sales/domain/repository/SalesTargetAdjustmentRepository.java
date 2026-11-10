package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.crm.sales.domain.model.SalesTargetAdjustment;

import java.util.List;

/**
 * 销售目标调整记录仓储端口。
 *
 * @author BlueWhale
 */
public interface SalesTargetAdjustmentRepository {

    /**
     * 新增调整记录。
     *
     * @param adjustment 调整记录
     */
    void insert(SalesTargetAdjustment adjustment);

    /**
     * 按目标查询调整记录，按调整时间倒序。
     *
     * @param targetId 目标 ID
     * @return 调整记录
     */
    List<SalesTargetAdjustment> listByTarget(Long targetId);
}

package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.crm.sales.domain.model.ForecastAdjustment;

import java.util.List;

/**
 * 预测调整记录仓储端口。
 *
 * @author BlueWhale
 */
public interface ForecastAdjustmentRepository {

    /**
     * 新增调整记录。
     *
     * @param adjustment 调整记录
     */
    void insert(ForecastAdjustment adjustment);

    /**
     * 按快照查询调整记录，按调整时间倒序。
     *
     * @param snapshotId 快照 ID
     * @return 调整记录
     */
    List<ForecastAdjustment> listBySnapshot(Long snapshotId);
}

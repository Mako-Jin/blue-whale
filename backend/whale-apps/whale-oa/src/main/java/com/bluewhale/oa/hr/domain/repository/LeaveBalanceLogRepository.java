package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.LeaveBalanceLog;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLogQuery;

import java.util.List;

/**
 * 假期额度流水仓储（只追加）。
 *
 * @author BlueWhale
 */
public interface LeaveBalanceLogRepository {

    /**
     * 分页查询流水。
     *
     * @param query 查询条件
     * @return 当前页流水
     */
    List<LeaveBalanceLog> page(LeaveBalanceLogQuery query);

    /**
     * 统计流水数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(LeaveBalanceLogQuery query);

    /**
     * 新增流水。
     *
     * @param log 流水
     */
    void insert(LeaveBalanceLog log);
}

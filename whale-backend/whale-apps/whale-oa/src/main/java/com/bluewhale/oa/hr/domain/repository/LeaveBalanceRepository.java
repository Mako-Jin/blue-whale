package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.LeaveBalance;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceQuery;

import java.util.List;
import java.util.Optional;

/**
 * 年度假期额度仓储。
 *
 * @author BlueWhale
 */
public interface LeaveBalanceRepository {

    /**
     * 分页查询额度。
     *
     * @param query 查询条件
     * @return 当前页额度
     */
    List<LeaveBalance> page(LeaveBalanceQuery query);

    /**
     * 统计额度数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(LeaveBalanceQuery query);

    /**
     * 按主键查询额度。
     *
     * @param id 主键
     * @return 额度
     */
    Optional<LeaveBalance> findById(Long id);

    /**
     * 按用户 + 类型 + 年份查询额度。
     *
     * @param userId 用户ID
     * @param leaveTypeId 假期类型ID
     * @param balanceYear 额度年份
     * @return 额度
     */
    Optional<LeaveBalance> findByUserAndTypeAndYear(Long userId, Long leaveTypeId, Integer balanceYear);

    /**
     * 查询某用户某年度的全部额度。
     *
     * @param userId 用户ID
     * @param balanceYear 额度年份
     * @return 额度列表
     */
    List<LeaveBalance> listByUserAndYear(Long userId, Integer balanceYear);

    /**
     * 新增额度。
     *
     * @param balance 额度
     */
    void insert(LeaveBalance balance);

    /**
     * 更新额度。
     *
     * @param balance 额度
     */
    void update(LeaveBalance balance);
}

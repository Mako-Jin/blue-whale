package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.LeaveBalanceAdjustCommand;
import com.bluewhale.oa.hr.application.view.LeaveBalanceLogView;
import com.bluewhale.oa.hr.application.view.LeaveBalanceView;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLogQuery;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceQuery;

import java.math.BigDecimal;
import java.util.List;

/**
 * 年度假期额度应用服务。
 *
 * <p>{@code freeze/release/deduct} 供请假申请服务调用，均会写入额度流水。</p>
 *
 * @author BlueWhale
 */
public interface LeaveBalanceAppService {

    /**
     * 分页查询额度。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<LeaveBalanceView> page(LeaveBalanceQuery query);

    /**
     * 查询某用户某年度的全部额度。
     *
     * @param userId 用户ID
     * @param year 额度年份
     * @return 额度列表
     */
    List<LeaveBalanceView> myBalance(Long userId, Integer year);

    /**
     * 按启用假期类型初始化某用户某年度额度（幂等）。
     *
     * @param userId 用户ID
     * @param year 额度年份
     * @return 初始化后的额度列表
     */
    List<LeaveBalanceView> initForUser(Long userId, Integer year);

    /**
     * 调整总额度。
     *
     * @param id 额度主键
     * @param command 调整命令
     * @return 调整后的额度
     */
    LeaveBalanceView adjust(Long id, LeaveBalanceAdjustCommand command);

    /**
     * 分页查询额度流水。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<LeaveBalanceLogView> logs(LeaveBalanceLogQuery query);

    /**
     * 冻结额度（提交申请）。
     *
     * @param userId 用户ID
     * @param leaveTypeId 假期类型ID
     * @param year 额度年份
     * @param days 冻结天数
     * @param allowNegative 假期类型是否允许负额度
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     */
    void freeze(Long userId, Long leaveTypeId, Integer year, BigDecimal days,
                boolean allowNegative, String bizType, Long bizId);

    /**
     * 释放冻结额度（驳回或撤销）。
     *
     * @param userId 用户ID
     * @param leaveTypeId 假期类型ID
     * @param year 额度年份
     * @param days 释放天数
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     */
    void release(Long userId, Long leaveTypeId, Integer year, BigDecimal days, String bizType, Long bizId);

    /**
     * 扣减额度（审批通过）。
     *
     * @param userId 用户ID
     * @param leaveTypeId 假期类型ID
     * @param year 额度年份
     * @param days 扣减天数
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     */
    void deduct(Long userId, Long leaveTypeId, Integer year, BigDecimal days, String bizType, Long bizId);
}

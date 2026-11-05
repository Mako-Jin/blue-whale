package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.LeaveBalanceAdjustCommand;
import com.bluewhale.oa.hr.application.service.LeaveBalanceAppService;
import com.bluewhale.oa.hr.application.view.LeaveBalanceLogView;
import com.bluewhale.oa.hr.application.view.LeaveBalanceView;
import com.bluewhale.oa.hr.domain.model.LeaveBalance;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceAction;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLog;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceLogQuery;
import com.bluewhale.oa.hr.domain.model.LeaveBalanceQuery;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.repository.LeaveBalanceLogRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveBalanceRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 年度假期额度应用服务实现。
 *
 * <p>所有额度变动均写入 {@code oa_leave_balance_log}，记录变更前后快照。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceAppServiceImpl implements LeaveBalanceAppService {

    private final LeaveBalanceRepository leaveBalanceRepository;

    private final LeaveBalanceLogRepository leaveBalanceLogRepository;

    private final LeaveTypeRepository leaveTypeRepository;

    @Override
    public PageResult<LeaveBalanceView> page(LeaveBalanceQuery query) {
        List<LeaveBalanceView> records = leaveBalanceRepository.page(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, leaveBalanceRepository.count(query),
                query.getCurrent(), query.getSize());
    }

    @Override
    public List<LeaveBalanceView> myBalance(Long userId, Integer year) {
        return leaveBalanceRepository.listByUserAndYear(userId, year).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LeaveBalanceView> initForUser(Long userId, Integer year) {
        if (userId == null || year == null) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID, "用户ID与额度年份不能为空");
        }
        List<LeaveType> types = leaveTypeRepository.listEnabled();
        for (LeaveType type : types) {
            if (leaveBalanceRepository.findByUserAndTypeAndYear(userId, type.getId(), year).isPresent()) {
                continue;
            }
            LeaveBalance balance = new LeaveBalance();
            balance.setUserId(userId);
            balance.setLeaveTypeId(type.getId());
            balance.setBalanceYear(year);
            balance.setTotalDays(type.getAnnualQuota() == null ? BigDecimal.ZERO : type.getAnnualQuota());
            balance.setUsedDays(BigDecimal.ZERO);
            balance.setFrozenDays(BigDecimal.ZERO);
            balance.setRemark("系统按假期类型默认额度初始化");
            leaveBalanceRepository.insert(balance);
            writeLog(balance, LeaveBalanceAction.INIT, balance.getTotalDays(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, "初始化年度额度");
        }
        log.info("假期额度初始化完成: userId={}, year={}, types={}", userId, year, types.size());
        return myBalance(userId, year);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveBalanceView adjust(Long id, LeaveBalanceAdjustCommand command) {
        LeaveBalance balance = requireById(id);
        BigDecimal beforeTotal = balance.getTotalDays();
        balance.setRemark(command.getRemark());
        balance.adjust(command.getTotalDays());
        leaveBalanceRepository.update(balance);
        BigDecimal diff = command.getTotalDays().subtract(beforeTotal == null ? BigDecimal.ZERO : beforeTotal).abs();
        writeLog(balance, LeaveBalanceAction.ADJUST, diff,
                beforeTotal, balance.getUsedDays(), balance.getFrozenDays(), null, null,
                command.getRemark() == null ? "调整年度额度" : command.getRemark());
        log.info("假期额度已调整: id={}, before={}, after={}", id, beforeTotal, command.getTotalDays());
        return toView(balance);
    }

    @Override
    public PageResult<LeaveBalanceLogView> logs(LeaveBalanceLogQuery query) {
        List<LeaveBalanceLogView> records = leaveBalanceLogRepository.page(query).stream()
                .map(this::toLogView).toList();
        return PageResult.of(records, leaveBalanceLogRepository.count(query),
                query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long userId, Long leaveTypeId, Integer year, BigDecimal days,
                       boolean allowNegative, String bizType, Long bizId) {
        LeaveBalance balance = requireBalance(userId, leaveTypeId, year);
        BigDecimal beforeTotal = balance.getTotalDays();
        BigDecimal beforeUsed = balance.getUsedDays();
        BigDecimal beforeFrozen = balance.getFrozenDays();
        balance.freeze(days, allowNegative);
        leaveBalanceRepository.update(balance);
        writeLog(balance, LeaveBalanceAction.FREEZE, days,
                beforeTotal, beforeUsed, beforeFrozen, bizType, bizId, "提交申请冻结额度");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long userId, Long leaveTypeId, Integer year, BigDecimal days, String bizType, Long bizId) {
        LeaveBalance balance = requireBalance(userId, leaveTypeId, year);
        BigDecimal beforeTotal = balance.getTotalDays();
        BigDecimal beforeUsed = balance.getUsedDays();
        BigDecimal beforeFrozen = balance.getFrozenDays();
        balance.release(days);
        leaveBalanceRepository.update(balance);
        writeLog(balance, LeaveBalanceAction.RELEASE, days,
                beforeTotal, beforeUsed, beforeFrozen, bizType, bizId, "驳回或撤销释放冻结");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long userId, Long leaveTypeId, Integer year, BigDecimal days, String bizType, Long bizId) {
        LeaveBalance balance = requireBalance(userId, leaveTypeId, year);
        BigDecimal beforeTotal = balance.getTotalDays();
        BigDecimal beforeUsed = balance.getUsedDays();
        BigDecimal beforeFrozen = balance.getFrozenDays();
        balance.deduct(days);
        leaveBalanceRepository.update(balance);
        writeLog(balance, LeaveBalanceAction.DEDUCT, days,
                beforeTotal, beforeUsed, beforeFrozen, bizType, bizId, "审批通过扣减额度");
    }

    /**
     * 按主键查询额度，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 额度
     */
    private LeaveBalance requireById(Long id) {
        return leaveBalanceRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_NOT_FOUND,
                        "假期额度不存在，id=" + id));
    }

    /**
     * 按用户 + 类型 + 年份查询额度，缺失时提示先初始化。
     *
     * @param userId 用户ID
     * @param leaveTypeId 假期类型ID
     * @param year 额度年份
     * @return 额度
     */
    private LeaveBalance requireBalance(Long userId, Long leaveTypeId, Integer year) {
        if (userId == null || leaveTypeId == null || year == null) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID,
                    "用户ID、假期类型与额度年份不能为空");
        }
        return leaveBalanceRepository.findByUserAndTypeAndYear(userId, leaveTypeId, year)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_NOT_FOUND,
                        "假期额度不存在，请先初始化后再提交申请"));
    }

    /**
     * 写入额度流水。
     *
     * @param balance 额度（变更后）
     * @param action 动作
     * @param days 变动天数
     * @param beforeTotal 变更前总额度
     * @param beforeUsed 变更前已使用
     * @param beforeFrozen 变更前冻结
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     * @param remark 备注
     */
    private void writeLog(LeaveBalance balance, LeaveBalanceAction action, BigDecimal days,
                          BigDecimal beforeTotal, BigDecimal beforeUsed, BigDecimal beforeFrozen,
                          String bizType, Long bizId, String remark) {
        LeaveBalanceLog logEntry = new LeaveBalanceLog();
        logEntry.setBalanceId(balance.getId());
        logEntry.setUserId(balance.getUserId());
        logEntry.setLeaveTypeId(balance.getLeaveTypeId());
        logEntry.setBalanceYear(balance.getBalanceYear());
        logEntry.setAction(action);
        logEntry.setDays(days == null ? BigDecimal.ZERO : days);
        logEntry.setBeforeTotal(orZero(beforeTotal));
        logEntry.setAfterTotal(orZero(balance.getTotalDays()));
        logEntry.setBeforeUsed(orZero(beforeUsed));
        logEntry.setAfterUsed(orZero(balance.getUsedDays()));
        logEntry.setBeforeFrozen(orZero(beforeFrozen));
        logEntry.setAfterFrozen(orZero(balance.getFrozenDays()));
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);
        leaveBalanceLogRepository.insert(logEntry);
    }

    /**
     * 空值转 0。
     *
     * @param value 数值
     * @return 非空数值
     */
    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 领域模型转视图。
     *
     * @param balance 额度
     * @return 额度视图
     */
    private LeaveBalanceView toView(LeaveBalance balance) {
        LeaveBalanceView view = new LeaveBalanceView();
        view.setId(balance.getId());
        view.setUserId(balance.getUserId());
        view.setLeaveTypeId(balance.getLeaveTypeId());
        view.setBalanceYear(balance.getBalanceYear());
        view.setTotalDays(balance.getTotalDays());
        view.setUsedDays(balance.getUsedDays());
        view.setFrozenDays(balance.getFrozenDays());
        view.setAvailableDays(balance.availableDays());
        view.setRemark(balance.getRemark());
        view.setCreateTime(balance.getCreateTime());
        view.setUpdateTime(balance.getUpdateTime());
        return view;
    }

    /**
     * 领域模型转流水视图。
     *
     * @param logEntry 流水
     * @return 流水视图
     */
    private LeaveBalanceLogView toLogView(LeaveBalanceLog logEntry) {
        LeaveBalanceLogView view = new LeaveBalanceLogView();
        view.setId(logEntry.getId());
        view.setBalanceId(logEntry.getBalanceId());
        view.setUserId(logEntry.getUserId());
        view.setLeaveTypeId(logEntry.getLeaveTypeId());
        view.setBalanceYear(logEntry.getBalanceYear());
        view.setAction(logEntry.getAction() == null ? null : logEntry.getAction().name());
        view.setDays(logEntry.getDays());
        view.setBeforeTotal(logEntry.getBeforeTotal());
        view.setAfterTotal(logEntry.getAfterTotal());
        view.setBeforeUsed(logEntry.getBeforeUsed());
        view.setAfterUsed(logEntry.getAfterUsed());
        view.setBeforeFrozen(logEntry.getBeforeFrozen());
        view.setAfterFrozen(logEntry.getAfterFrozen());
        view.setBizType(logEntry.getBizType());
        view.setBizId(logEntry.getBizId());
        view.setOperatorId(logEntry.getOperatorId());
        view.setRemark(logEntry.getRemark());
        view.setCreateTime(logEntry.getCreateTime());
        return view;
    }
}

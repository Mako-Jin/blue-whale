package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.LeaveSaveCommand;
import com.bluewhale.oa.hr.application.command.LeaveStatusCommand;
import com.bluewhale.oa.hr.application.service.LeaveAppService;
import com.bluewhale.oa.hr.application.service.LeaveBalanceAppService;
import com.bluewhale.oa.hr.application.service.WorkCalendarAppService;
import com.bluewhale.oa.hr.application.view.LeaveView;
import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;
import com.bluewhale.oa.hr.domain.model.LeaveStatus;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 请假申请应用服务实现。
 *
 * <p>M1A-2 关闭请假闭环：创建时按工作日历计算天数、校验时间重叠并冻结额度；
 * 审批通过扣减额度，驳回或撤销释放冻结；重复同步同一终态幂等返回。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveAppServiceImpl implements LeaveAppService {

    /** 请假业务类型，用于额度流水 */
    private static final String BIZ_TYPE_LEAVE = "LEAVE";

    /** 请假天数精度：与 oa_leave.days 的 DECIMAL(5,1) 对齐 */
    private static final int DAYS_SCALE = 1;

    private final LeaveRepository leaveRepository;

    private final LeaveTypeRepository leaveTypeRepository;

    private final EmployeeProfileRepository employeeProfileRepository;

    private final WorkCalendarAppService workCalendarAppService;

    private final LeaveBalanceAppService leaveBalanceAppService;

    @Override
    public PageResult<LeaveView> page(LeaveQuery query) {
        List<LeaveView> records = leaveRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, leaveRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public LeaveView detail(Long id) {
        return toView(requireLeave(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveView create(LeaveSaveCommand command) {
        Leave leave = new Leave();
        apply(leave, command);
        leave.ensureValidPeriod();
        assertCodeUnique(command.getCode(), null);
        LeaveType leaveType = resolveLeaveType(command.getLeaveType());
        leave.setLeaveTypeId(leaveType.getId());
        if (command.getApplicantId() != null) {
            employeeProfileRepository.findByUserId(command.getApplicantId())
                    .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_NOT_FOUND,
                            "申请人档案不存在，userId=" + command.getApplicantId()));
            leave.setApplicantId(command.getApplicantId());
        }
        leave.setDays(calculateDays(leave));
        assertNoOverlap(leave, null);
        leave.submit();
        leaveRepository.insert(leave);
        if (leave.getApplicantId() != null) {
            leaveBalanceAppService.freeze(leave.getApplicantId(), leave.getLeaveTypeId(),
                    leave.getStartDate().getYear(), leave.getDays(), leaveType.allowNegative(),
                    BIZ_TYPE_LEAVE, leave.getId());
        }
        log.info("请假申请已创建: id={}, code={}, days={}", leave.getId(), leave.getCode(), leave.getDays());
        return toView(leave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveView update(Long id, LeaveSaveCommand command) {
        Leave leave = requireEditable(id);
        Long oldApplicantId = leave.getApplicantId();
        Long oldLeaveTypeId = leave.getLeaveTypeId();
        BigDecimal oldDays = leave.getDays();
        LocalDate oldStartDate = leave.getStartDate();

        apply(leave, command);
        leave.ensureValidPeriod();
        assertCodeUnique(command.getCode(), id);
        LeaveType leaveType = resolveLeaveType(command.getLeaveType());
        leave.setLeaveTypeId(leaveType.getId());
        if (command.getApplicantId() != null) {
            employeeProfileRepository.findByUserId(command.getApplicantId())
                    .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_NOT_FOUND,
                            "申请人档案不存在，userId=" + command.getApplicantId()));
            leave.setApplicantId(command.getApplicantId());
        }
        leave.setDays(calculateDays(leave));
        assertNoOverlap(leave, id);
        if (oldApplicantId != null && oldLeaveTypeId != null && oldDays != null && oldStartDate != null) {
            leaveBalanceAppService.release(oldApplicantId, oldLeaveTypeId, oldStartDate.getYear(),
                    oldDays, BIZ_TYPE_LEAVE, id);
        }
        if (leave.getApplicantId() != null) {
            leaveBalanceAppService.freeze(leave.getApplicantId(), leave.getLeaveTypeId(),
                    leave.getStartDate().getYear(), leave.getDays(), leaveType.allowNegative(),
                    BIZ_TYPE_LEAVE, leave.getId());
        }
        leaveRepository.update(leave);
        log.info("请假申请已更新: id={}, code={}, days={}", id, leave.getCode(), leave.getDays());
        return toView(leave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, LeaveStatusCommand command) {
        Leave leave = requireLeave(id);
        LeaveStatus target = LeaveStatus.of(command.getStatus());
        if (!leave.changeStatus(target)) {
            log.info("请假申请状态未变化，幂等返回: id={}, status={}", id, target);
            return;
        }
        applyBalanceEffect(leave, target);
        leaveRepository.update(leave);
        log.info("请假申请状态已变更: id={}, status={}", id, leave.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long leaveId, String result) {
        LeaveStatus target = LeaveStatus.of(result);
        if (target == LeaveStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_STATUS_CONFLICT, "审批结果不能为待审批");
        }
        LeaveStatusCommand command = new LeaveStatusCommand();
        command.setStatus(target.name());
        updateStatus(leaveId, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Leave leave = requireEditable(id);
        if (leave.getApplicantId() != null && leave.getLeaveTypeId() != null
                && leave.getDays() != null && leave.getStartDate() != null) {
            leaveBalanceAppService.release(leave.getApplicantId(), leave.getLeaveTypeId(),
                    leave.getStartDate().getYear(), leave.getDays(), BIZ_TYPE_LEAVE, id);
        }
        leaveRepository.deleteById(id);
        log.info("请假申请已删除: id={}", id);
    }

    /**
     * 按工作日历计算请假天数。
     *
     * <p>整天计 1，AM/PM 半天计 0.5，非工作日不计；同一天 AM → PM 计整天。</p>
     *
     * @param leave 申请
     * @return 请假天数
     */
    private BigDecimal calculateDays(Leave leave) {
        LocalDate startDate = leave.getStartDate();
        LocalDate endDate = leave.getEndDate();
        BigDecimal total = BigDecimal.ZERO;
        if (startDate.equals(endDate)) {
            boolean fullDay = Leave.PERIOD_AM.equals(leave.getStartPeriod())
                    && Leave.PERIOD_PM.equals(leave.getEndPeriod());
            total = workCalendarAppService.workdayFraction(startDate, fullDay ? null : leave.getStartPeriod());
        } else {
            total = total.add(workCalendarAppService.workdayFraction(startDate,
                    Leave.PERIOD_AM.equals(leave.getStartPeriod()) ? null : leave.getStartPeriod()));
            total = total.add(workCalendarAppService.workdayFraction(endDate,
                    Leave.PERIOD_PM.equals(leave.getEndPeriod()) ? null : leave.getEndPeriod()));
            for (LocalDate date = startDate.plusDays(1); !date.isAfter(endDate.minusDays(1));
                 date = date.plusDays(1)) {
                total = total.add(workCalendarAppService.workdayFraction(date, null));
            }
        }
        if (total.signum() <= 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_INVALID, "请假区间内没有工作日，请调整日期");
        }
        return total.setScale(DAYS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 解析启用的假期类型。
     *
     * @param code 假期类型编码
     * @return 假期类型
     */
    private LeaveType resolveLeaveType(String code) {
        LeaveType leaveType = leaveTypeRepository.findByCode(code)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_NOT_FOUND,
                        "假期类型不存在: " + code));
        if (!leaveType.enabled()) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_INVALID, "假期类型已停用: " + code);
        }
        return leaveType;
    }

    /**
     * 校验同一申请人同一假期类型时间区间不重叠。
     *
     * @param leave 申请
     * @param excludeId 更新时排除自身
     */
    private void assertNoOverlap(Leave leave, Long excludeId) {
        List<Leave> overlapping = leaveRepository.listOverlapping(leave.getApplicant(),
                leave.getLeaveType(), leave.getStartDate(), leave.getEndDate(), excludeId);
        if (!overlapping.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_PERIOD_OVERLAP,
                    "所选时段与已有申请重叠，请调整请假时间");
        }
    }

    /**
     * 按目标状态应用额度副作用。
     *
     * @param leave 申请
     * @param target 目标状态
     */
    private void applyBalanceEffect(Leave leave, LeaveStatus target) {
        if (leave.getApplicantId() == null || leave.getLeaveTypeId() == null
                || leave.getDays() == null || leave.getStartDate() == null) {
            return;
        }
        int year = leave.getStartDate().getYear();
        if (target == LeaveStatus.APPROVED) {
            leaveBalanceAppService.deduct(leave.getApplicantId(), leave.getLeaveTypeId(), year,
                    leave.getDays(), BIZ_TYPE_LEAVE, leave.getId());
        } else if (target == LeaveStatus.REJECTED || target == LeaveStatus.CANCELLED) {
            leaveBalanceAppService.release(leave.getApplicantId(), leave.getLeaveTypeId(), year,
                    leave.getDays(), BIZ_TYPE_LEAVE, leave.getId());
        }
    }

    /**
     * 查询申请，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 申请
     */
    private Leave requireLeave(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.LEAVE_NOT_FOUND,
                        "请假申请不存在，id=" + id));
    }

    /**
     * 查询并校验申请处于可编辑状态。
     *
     * @param id 主键
     * @return 申请
     */
    private Leave requireEditable(Long id) {
        Leave leave = requireLeave(id);
        leave.ensureEditable();
        return leave;
    }

    /**
     * 校验申请单号在租户内唯一。
     *
     * @param code 申请单号
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        if (leaveRepository.existsByCode(code, excludeId)) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_INVALID, "申请单号已存在");
        }
    }

    /**
     * 命令字段写入申请模型。
     *
     * @param leave 申请
     * @param command 保存命令
     */
    private void apply(Leave leave, LeaveSaveCommand command) {
        leave.setCode(command.getCode());
        leave.setApplicant(command.getApplicant());
        leave.setLeaveType(command.getLeaveType());
        leave.setStartDate(command.getStartDate());
        leave.setStartPeriod(StringUtils.hasText(command.getStartPeriod())
                ? command.getStartPeriod() : Leave.PERIOD_AM);
        leave.setEndDate(command.getEndDate());
        leave.setEndPeriod(StringUtils.hasText(command.getEndPeriod())
                ? command.getEndPeriod() : Leave.PERIOD_PM);
        leave.setHandoverUserId(command.getHandoverUserId());
        leave.setDays(command.getDays());
        leave.setReason(command.getReason());
        leave.setRemark(command.getRemark());
    }

    /**
     * 领域模型转视图。
     *
     * @param leave 申请
     * @return 申请视图
     */
    private LeaveView toView(Leave leave) {
        LeaveView view = new LeaveView();
        view.setId(leave.getId());
        view.setCode(leave.getCode());
        view.setApplicant(leave.getApplicant());
        view.setApplicantId(leave.getApplicantId());
        view.setLeaveType(leave.getLeaveType());
        view.setLeaveTypeId(leave.getLeaveTypeId());
        view.setStartDate(leave.getStartDate());
        view.setStartPeriod(leave.getStartPeriod());
        view.setEndDate(leave.getEndDate());
        view.setEndPeriod(leave.getEndPeriod());
        view.setHandoverUserId(leave.getHandoverUserId());
        view.setApprovalInstanceId(leave.getApprovalInstanceId());
        view.setDays(leave.getDays());
        view.setReason(leave.getReason());
        view.setStatus(leave.getStatus() == null ? null : leave.getStatus().name());
        view.setRemark(leave.getRemark());
        view.setCreateTime(leave.getCreateTime());
        view.setUpdateTime(leave.getUpdateTime());
        return view;
    }
}

package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.OvertimeSaveCommand;
import com.bluewhale.oa.hr.application.command.OvertimeStatusCommand;
import com.bluewhale.oa.hr.application.service.LeaveBalanceAppService;
import com.bluewhale.oa.hr.application.service.OvertimeAppService;
import com.bluewhale.oa.hr.application.view.OvertimeView;
import com.bluewhale.oa.hr.domain.model.Overtime;
import com.bluewhale.oa.hr.domain.model.OvertimeCompensation;
import com.bluewhale.oa.hr.domain.model.OvertimeQuery;
import com.bluewhale.oa.hr.domain.model.OvertimeStatus;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import com.bluewhale.oa.hr.domain.repository.OvertimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 加班申请应用服务实现。
 *
 * <p>加班时长由服务端按起止时间计算（半小时精度），同日时段不可重叠，
 * 单月累计不超过配置上限（默认 36 小时）；审批通过且补偿方式为调休时，
 * 按 8 小时 = 1 天、0.5 天向下取整调入调休额度，重复同步不会重复入账。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeAppServiceImpl implements OvertimeAppService {

    /** 加班业务类型，用于额度流水 */
    private static final String BIZ_TYPE_OVERTIME = "OVERTIME";

    /** 调休假期类型编码 */
    private static final String COMP_TIME_LEAVE_TYPE = "COMP_TIME";

    /** 每小时分钟数 */
    private static final long MINUTES_PER_HOUR = 60L;

    /** 每个工作日折算小时数 */
    private static final BigDecimal HOURS_PER_WORKDAY = BigDecimal.valueOf(8);

    /** 最小加班时长（小时） */
    private static final BigDecimal MIN_HOURS = new BigDecimal("0.5");

    /** 加班时长精度 */
    private static final int HOURS_SCALE = 1;

    /** 调休天数精度 */
    private static final int DAYS_SCALE = 1;

    /** 默认月度加班上限（小时） */
    private static final String DEFAULT_MONTHLY_LIMIT = "36";

    private final OvertimeRepository overtimeRepository;

    private final EmployeeProfileRepository employeeProfileRepository;

    private final LeaveTypeRepository leaveTypeRepository;

    private final LeaveBalanceAppService leaveBalanceAppService;

    /** 单月加班时长上限（小时） */
    @Value("${whale.oa.overtime.monthly-limit-hours:" + DEFAULT_MONTHLY_LIMIT + "}")
    private BigDecimal monthlyLimitHours;

    @Override
    public PageResult<OvertimeView> page(OvertimeQuery query) {
        List<OvertimeView> records = overtimeRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, overtimeRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public OvertimeView detail(Long id) {
        return toView(requireOvertime(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OvertimeView create(OvertimeSaveCommand command) {
        Overtime overtime = new Overtime();
        apply(overtime, command);
        overtime.ensureValidPeriod();
        assertCodeUnique(command.getCode(), null);
        assertProfileExists(command.getApplicantId());
        overtime.setHours(calculateHours(overtime));
        assertNoOverlap(overtime, null);
        assertMonthlyLimit(overtime, null);
        overtime.submit();
        overtimeRepository.insert(overtime);
        log.info("加班申请已创建: id={}, code={}, hours={}", overtime.getId(), overtime.getCode(), overtime.getHours());
        return toView(overtime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OvertimeView update(Long id, OvertimeSaveCommand command) {
        Overtime overtime = requireEditable(id);
        apply(overtime, command);
        overtime.ensureValidPeriod();
        assertCodeUnique(command.getCode(), id);
        assertProfileExists(command.getApplicantId());
        overtime.setHours(calculateHours(overtime));
        assertNoOverlap(overtime, id);
        assertMonthlyLimit(overtime, id);
        overtimeRepository.update(overtime);
        log.info("加班申请已更新: id={}, code={}, hours={}", id, overtime.getCode(), overtime.getHours());
        return toView(overtime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, OvertimeStatusCommand command) {
        Overtime overtime = requireOvertime(id);
        OvertimeStatus target = OvertimeStatus.of(command.getStatus());
        if (!overtime.changeStatus(target)) {
            log.info("加班申请状态未变化，幂等返回: id={}, status={}", id, target);
            return;
        }
        overtimeRepository.update(overtime);
        if (target == OvertimeStatus.APPROVED) {
            grantCompTime(overtime);
        }
        log.info("加班申请状态已变更: id={}, status={}", id, overtime.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long overtimeId, String result) {
        OvertimeStatus target = OvertimeStatus.of(result);
        if (target == OvertimeStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_STATUS_CONFLICT, "审批结果不能为待审批");
        }
        OvertimeStatusCommand command = new OvertimeStatusCommand();
        command.setStatus(target.name());
        updateStatus(overtimeId, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Overtime overtime = requireEditable(id);
        overtimeRepository.deleteById(overtime.getId());
        log.info("加班申请已删除: id={}", id);
    }

    /**
     * 按起止时间计算加班时长（0.1 小时精度，最小 0.5 小时）。
     *
     * @param overtime 申请
     * @return 加班时长
     */
    private BigDecimal calculateHours(Overtime overtime) {
        long minutes = ChronoUnit.MINUTES.between(overtime.getStartTime(), overtime.getEndTime());
        BigDecimal hours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), HOURS_SCALE, RoundingMode.HALF_UP);
        if (hours.compareTo(MIN_HOURS) < 0) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_INVALID, "单次加班时长不能少于 0.5 小时");
        }
        return hours;
    }

    /**
     * 校验同一申请人同一天加班时段不重叠。
     *
     * @param overtime 申请
     * @param excludeId 更新时排除自身
     */
    private void assertNoOverlap(Overtime overtime, Long excludeId) {
        List<Overtime> sameDay = overtimeRepository.listByApplicantAndDate(
                overtime.getApplicant(), overtime.getOvertimeDate(), excludeId);
        for (Overtime existing : sameDay) {
            if (existing.getStartTime() == null || existing.getEndTime() == null) {
                continue;
            }
            if (existing.getStartTime().isBefore(overtime.getEndTime())
                    && overtime.getStartTime().isBefore(existing.getEndTime())) {
                throw OaErrorCode.exception(OaErrorCode.OVERTIME_PERIOD_OVERLAP,
                        "所选时段与当天已有加班申请重叠，请调整时间");
            }
        }
    }

    /**
     * 校验单月累计加班时长不超过配置上限。
     *
     * @param overtime 申请
     * @param excludeId 更新时排除自身
     */
    private void assertMonthlyLimit(Overtime overtime, Long excludeId) {
        LocalDate monthStart = overtime.getOvertimeDate().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        BigDecimal registered = overtimeRepository.sumHours(
                overtime.getApplicant(), monthStart, monthEnd, excludeId);
        BigDecimal limit = monthlyLimitHours == null ? new BigDecimal(DEFAULT_MONTHLY_LIMIT) : monthlyLimitHours;
        if (registered.add(overtime.getHours()).compareTo(limit) > 0) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_MONTHLY_LIMIT,
                    "本月加班时长将超过上限 " + limit.toPlainString() + " 小时（已登记 "
                            + registered.toPlainString() + " 小时）");
        }
    }

    /**
     * 审批通过后按补偿方式调入调休额度（幂等由状态机保证，异常仅告警不阻断审批）。
     *
     * @param overtime 申请
     */
    private void grantCompTime(Overtime overtime) {
        if (overtime.getCompensation() != OvertimeCompensation.COMP_TIME
                || overtime.getApplicantId() == null || overtime.getHours() == null) {
            return;
        }
        try {
            LeaveType leaveType = leaveTypeRepository.findByCode(COMP_TIME_LEAVE_TYPE).orElse(null);
            if (leaveType == null) {
                log.warn("加班调休入账跳过：未找到调休假期类型，overtimeId={}", overtime.getId());
                return;
            }
            BigDecimal days = hoursToDays(overtime.getHours());
            if (days.signum() <= 0) {
                log.info("加班时长不足以折算调休，跳过入账: overtimeId={}, hours={}",
                        overtime.getId(), overtime.getHours());
                return;
            }
            leaveBalanceAppService.grant(overtime.getApplicantId(), leaveType.getId(),
                    overtime.getOvertimeDate().getYear(), days, BIZ_TYPE_OVERTIME, overtime.getId());
            log.info("加班调休已入账: overtimeId={}, userId={}, days={}",
                    overtime.getId(), overtime.getApplicantId(), days);
        } catch (RuntimeException exception) {
            log.warn("加班调休入账失败: overtimeId={}, error={}", overtime.getId(), exception.getMessage());
        }
    }

    /**
     * 加班小时折算调休天数：8 小时 = 1 天，按 0.5 天向下取整。
     *
     * @param hours 加班小时
     * @return 调休天数
     */
    private BigDecimal hoursToDays(BigDecimal hours) {
        BigDecimal rawDays = hours.divide(HOURS_PER_WORKDAY, 2, RoundingMode.DOWN);
        BigDecimal halves = rawDays.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.DOWN);
        return halves.divide(BigDecimal.valueOf(2), DAYS_SCALE, RoundingMode.DOWN);
    }

    /**
     * 查询申请，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 申请
     */
    private Overtime requireOvertime(Long id) {
        return overtimeRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.OVERTIME_NOT_FOUND,
                        "加班申请不存在，id=" + id));
    }

    /**
     * 查询并校验申请处于可编辑状态。
     *
     * @param id 主键
     * @return 申请
     */
    private Overtime requireEditable(Long id) {
        Overtime overtime = requireOvertime(id);
        overtime.ensureEditable();
        return overtime;
    }

    /**
     * 校验申请单号在租户内唯一。
     *
     * @param code 申请单号
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        if (overtimeRepository.existsByCode(code, excludeId)) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_INVALID, "申请单号已存在");
        }
    }

    /**
     * 校验申请人扩展档案存在。
     *
     * @param applicantId 申请人用户ID，可为空（历史调用方）
     */
    private void assertProfileExists(Long applicantId) {
        if (applicantId == null) {
            return;
        }
        employeeProfileRepository.findByUserId(applicantId)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_NOT_FOUND,
                        "申请人档案不存在，userId=" + applicantId));
    }

    /**
     * 命令字段写入申请模型。
     *
     * @param overtime 申请
     * @param command 保存命令
     */
    private void apply(Overtime overtime, OvertimeSaveCommand command) {
        overtime.setCode(command.getCode());
        overtime.setApplicant(command.getApplicant());
        overtime.setApplicantId(command.getApplicantId());
        overtime.setOvertimeDate(command.getOvertimeDate());
        overtime.setStartTime(command.getStartTime());
        overtime.setEndTime(command.getEndTime());
        overtime.setCompensation(StringUtils.hasText(command.getCompensation())
                ? OvertimeCompensation.of(command.getCompensation()) : OvertimeCompensation.COMP_TIME);
        overtime.setProjectName(command.getProjectName());
        overtime.setReason(command.getReason());
        overtime.setRemark(command.getRemark());
    }

    /**
     * 领域模型转视图。
     *
     * @param overtime 申请
     * @return 申请视图
     */
    private OvertimeView toView(Overtime overtime) {
        OvertimeView view = new OvertimeView();
        view.setId(overtime.getId());
        view.setCode(overtime.getCode());
        view.setApplicant(overtime.getApplicant());
        view.setApplicantId(overtime.getApplicantId());
        view.setOvertimeDate(overtime.getOvertimeDate());
        view.setStartTime(overtime.getStartTime());
        view.setEndTime(overtime.getEndTime());
        view.setHours(overtime.getHours());
        view.setCompensation(overtime.getCompensation() == null ? null : overtime.getCompensation().name());
        view.setProjectName(overtime.getProjectName());
        view.setReason(overtime.getReason());
        view.setStatus(overtime.getStatus() == null ? null : overtime.getStatus().name());
        view.setApprovalInstanceId(overtime.getApprovalInstanceId());
        view.setRemark(overtime.getRemark());
        view.setCreateTime(overtime.getCreateTime());
        view.setUpdateTime(overtime.getUpdateTime());
        return view;
    }
}

package com.bluewhale.mes.workforce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.masterdata.entity.MesRoutingOperation;
import com.bluewhale.mes.masterdata.entity.MesRoutingSnapshot;
import com.bluewhale.mes.masterdata.entity.MesStation;
import com.bluewhale.mes.masterdata.entity.MesTeam;
import com.bluewhale.mes.masterdata.entity.MesWorkCenter;
import com.bluewhale.mes.masterdata.mapper.MesRoutingOperationMapper;
import com.bluewhale.mes.masterdata.mapper.MesRoutingSnapshotMapper;
import com.bluewhale.mes.masterdata.mapper.MesStationMapper;
import com.bluewhale.mes.masterdata.mapper.MesTeamMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import com.bluewhale.mes.workforce.dto.MesHandoverConfirmRequest;
import com.bluewhale.mes.workforce.dto.MesHandoverQueryRequest;
import com.bluewhale.mes.workforce.dto.MesHandoverSaveRequest;
import com.bluewhale.mes.workforce.dto.MesPerformanceAggregateRequest;
import com.bluewhale.mes.workforce.dto.MesPerformanceLockRequest;
import com.bluewhale.mes.workforce.dto.MesPerformanceQueryRequest;
import com.bluewhale.mes.workforce.dto.MesShiftAssignmentQueryRequest;
import com.bluewhale.mes.workforce.dto.MesShiftAssignmentSaveRequest;
import com.bluewhale.mes.workforce.dto.MesSkillMatrixQueryRequest;
import com.bluewhale.mes.workforce.dto.MesSkillMatrixSaveRequest;
import com.bluewhale.mes.workforce.dto.MesTeamMemberQueryRequest;
import com.bluewhale.mes.workforce.dto.MesTeamMemberSaveRequest;
import com.bluewhale.mes.workforce.dto.MesTeamMemberStatusRequest;
import com.bluewhale.mes.workforce.dto.MesWorkHourQueryRequest;
import com.bluewhale.mes.workforce.dto.MesWorkHourSaveRequest;
import com.bluewhale.mes.workforce.entity.MesHandover;
import com.bluewhale.mes.workforce.entity.MesPerformanceStat;
import com.bluewhale.mes.workforce.entity.MesShiftAssignment;
import com.bluewhale.mes.workforce.entity.MesSkillMatrix;
import com.bluewhale.mes.workforce.entity.MesTeamMember;
import com.bluewhale.mes.workforce.entity.MesWorkHour;
import com.bluewhale.mes.workforce.mapper.MesHandoverMapper;
import com.bluewhale.mes.workforce.mapper.MesPerformanceStatMapper;
import com.bluewhale.mes.workforce.mapper.MesShiftAssignmentMapper;
import com.bluewhale.mes.workforce.mapper.MesSkillMatrixMapper;
import com.bluewhale.mes.workforce.mapper.MesTeamMemberMapper;
import com.bluewhale.mes.workforce.mapper.MesWorkHourMapper;
import com.bluewhale.mes.workforce.service.MesWorkforceService;
import com.bluewhale.mes.workforce.vo.MesHandoverVO;
import com.bluewhale.mes.workforce.vo.MesPerformanceLockVO;
import com.bluewhale.mes.workforce.vo.MesPerformanceStatVO;
import com.bluewhale.mes.workforce.vo.MesShiftAssignmentVO;
import com.bluewhale.mes.workforce.vo.MesSkillMatrixVO;
import com.bluewhale.mes.workforce.vo.MesTeamMemberVO;
import com.bluewhale.mes.workforce.vo.MesWorkHourVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MES 人员绩效应用服务实现
 *
 * <p>错误码约定（MES-8xxx 段，用于日志与接口文档追溯，异常仍沿用通用状态码）：
 * MES-8001 班组不存在、MES-8002 班组成员重复、MES-8003 主操作员冲突、MES-8004 排班重复、
 * MES-8005 交接单重复确认、MES-8006 任务不存在、MES-8007 报工来源缺少报工、MES-8008 时间区间非法、
 * MES-8009 净工时为负、MES-8010 工时重复、MES-8011 工时不存在、MES-8012 期间编码非法、
 * MES-8013 月度已锁定、MES-8014 无月度绩效可锁定、MES-8015 技能记录重复。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MesWorkforceServiceImpl implements MesWorkforceService {

    /** 日统计层级 */
    private static final String LEVEL_DAILY = "DAILY";

    /** 月统计层级 */
    private static final String LEVEL_MONTHLY = "MONTHLY";

    /** 启用状态 */
    private static final String STATUS_ENABLED = "ENABLED";

    /** 停用状态 */
    private static final String STATUS_DISABLED = "DISABLED";

    /** 排班已分配状态 */
    private static final String SHIFT_ASSIGNED = "ASSIGNED";

    /** 排班已取消状态 */
    private static final String SHIFT_CANCELLED = "CANCELLED";

    /** 交接草稿状态 */
    private static final String HANDOVER_DRAFT = "DRAFT";

    /** 交接已确认状态 */
    private static final String HANDOVER_CONFIRMED = "CONFIRMED";

    /** 工时有效状态 */
    private static final String WORK_HOUR_VALID = "VALID";

    /** 工时作废状态 */
    private static final String WORK_HOUR_VOID = "VOID";

    /** 工时来源：手工 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 工时来源：报工 */
    private static final String SOURCE_REPORT = "REPORT";

    /** 报工提交状态 */
    private static final String REPORT_SUBMITTED = "SUBMITTED";

    /** 工艺路线发布状态 */
    private static final String ROUTING_RELEASED = "RELEASED";

    /** 日期间格式 */
    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 月份格式 */
    private static final DateTimeFormatter MONTHLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /** 每分钟秒数 */
    private static final BigDecimal SECONDS_PER_MINUTE = new BigDecimal("60");

    /** 比率保留小数位 */
    private static final int RATIO_SCALE = 6;

    private final MesTeamMemberMapper teamMemberMapper;

    private final MesShiftAssignmentMapper shiftAssignmentMapper;

    private final MesHandoverMapper handoverMapper;

    private final MesWorkHourMapper workHourMapper;

    private final MesPerformanceStatMapper performanceStatMapper;

    private final MesSkillMatrixMapper skillMatrixMapper;

    private final MesTeamMapper teamMapper;

    private final MesStationMapper stationMapper;

    private final MesWorkCenterMapper workCenterMapper;

    private final MesWorkOrderTaskMapper taskMapper;

    private final MesProductionReportMapper reportMapper;

    private final MesRoutingSnapshotMapper routingSnapshotMapper;

    private final MesRoutingOperationMapper routingOperationMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesTeamMemberVO saveTeamMember(MesTeamMemberSaveRequest request) {
        MesTeam team = requireTeam(request.getTeamId());
        MesTeamMember existing = request.getId() == null ? null : teamMemberMapper.selectById(request.getId());
        if (request.getId() != null && existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.team-member-found", "班组成员不存在：{0}", request.getId());
        }
        assertTeamMemberUnique(request.getTeamId(), request.getEmployeeId(), request.getId());
        MesTeamMember member = existing == null ? new MesTeamMember() : existing;
        member.setTeamId(team.getId());
        member.setTeamCode(team.getCode());
        member.setEmployeeId(request.getEmployeeId());
        member.setEmployeeName(request.getEmployeeName());
        member.setPrimaryOperator(request.getPrimaryOperator() == null ? 0 : request.getPrimaryOperator());
        member.setSkillLevel(StringUtils.hasText(request.getSkillLevel()) ? request.getSkillLevel() : "JUNIOR");
        member.setJoinDate(request.getJoinDate());
        member.setLeaveDate(request.getLeaveDate());
        member.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_ENABLED);
        member.setRemark(request.getRemark());
        if (existing == null) {
            teamMemberMapper.insert(member);
        } else {
            teamMemberMapper.updateById(member);
        }
        return toTeamMemberVO(member);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesTeamMemberVO> pageTeamMembers(MesTeamMemberQueryRequest request) {
        LambdaQueryWrapper<MesTeamMember> wrapper = new LambdaQueryWrapper<MesTeamMember>()
                .eq(request.getTeamId() != null, MesTeamMember::getTeamId, request.getTeamId())
                .eq(request.getEmployeeId() != null, MesTeamMember::getEmployeeId, request.getEmployeeId())
                .like(StringUtils.hasText(request.getEmployeeName()), MesTeamMember::getEmployeeName,
                        request.getEmployeeName())
                .eq(StringUtils.hasText(request.getStatus()), MesTeamMember::getStatus, request.getStatus())
                .eq(request.getPrimaryOperator() != null, MesTeamMember::getPrimaryOperator,
                        request.getPrimaryOperator())
                .orderByDesc(MesTeamMember::getCreateTime);
        Page<MesTeamMember> page = teamMemberMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toTeamMemberVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesTeamMemberVO changeTeamMemberStatus(Long id, MesTeamMemberStatusRequest request) {
        MesTeamMember member = requireTeamMember(id);
        member.setStatus(Boolean.TRUE.equals(request.getEnabled()) ? STATUS_ENABLED : STATUS_DISABLED);
        teamMemberMapper.updateById(member);
        return toTeamMemberVO(member);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesShiftAssignmentVO saveShiftAssignment(MesShiftAssignmentSaveRequest request) {
        MesTeam team = requireTeam(request.getTeamId());
        MesStation station = requireStation(request.getStationId());
        assertShiftTime(request.getStartAt(), request.getEndAt());
        MesShiftAssignment existing = request.getId() == null ? null : shiftAssignmentMapper.selectById(request.getId());
        if (request.getId() != null && existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.shift-schedule-record-found", "排班记录不存在：{0}", request.getId());
        }
        if (existing != null && SHIFT_CANCELLED.equals(existing.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.cancelled-shift-schedule-cannot-edit", "已取消的排班不可编辑");
        }
        assertShiftUnique(request, existing);
        assertPrimaryOperatorUnique(request, existing);
        MesShiftAssignment assignment = existing == null ? new MesShiftAssignment() : existing;
        assignment.setShiftDate(request.getShiftDate());
        assignment.setShiftCode(request.getShiftCode());
        assignment.setTeamId(team.getId());
        assignment.setTeamCode(team.getCode());
        assignment.setStationId(station.getId());
        assignment.setStationCode(station.getCode());
        Long workCenterId = request.getWorkCenterId() == null ? station.getWorkCenterId()
                : request.getWorkCenterId();
        assignment.setWorkCenterId(workCenterId);
        if (workCenterId != null) {
            MesWorkCenter workCenter = workCenterMapper.selectById(workCenterId);
            assignment.setWorkCenterCode(workCenter == null ? null : workCenter.getCode());
        } else {
            assignment.setWorkCenterCode(null);
        }
        assignment.setEmployeeId(request.getEmployeeId());
        assignment.setEmployeeName(request.getEmployeeName());
        assignment.setPrimaryOperator(request.getPrimaryOperator() == null ? 0 : request.getPrimaryOperator());
        assignment.setStartAt(request.getStartAt());
        assignment.setEndAt(request.getEndAt());
        assignment.setStatus(SHIFT_ASSIGNED);
        assignment.setRemark(request.getRemark());
        if (existing == null) {
            shiftAssignmentMapper.insert(assignment);
        } else {
            shiftAssignmentMapper.updateById(assignment);
        }
        return toShiftAssignmentVO(assignment);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesShiftAssignmentVO> pageShiftAssignments(MesShiftAssignmentQueryRequest request) {
        LambdaQueryWrapper<MesShiftAssignment> wrapper = new LambdaQueryWrapper<MesShiftAssignment>()
                .eq(request.getShiftDate() != null, MesShiftAssignment::getShiftDate, request.getShiftDate())
                .eq(StringUtils.hasText(request.getShiftCode()), MesShiftAssignment::getShiftCode,
                        request.getShiftCode())
                .eq(request.getTeamId() != null, MesShiftAssignment::getTeamId, request.getTeamId())
                .eq(request.getStationId() != null, MesShiftAssignment::getStationId, request.getStationId())
                .eq(request.getEmployeeId() != null, MesShiftAssignment::getEmployeeId, request.getEmployeeId())
                .eq(StringUtils.hasText(request.getStatus()), MesShiftAssignment::getStatus, request.getStatus())
                .orderByDesc(MesShiftAssignment::getShiftDate)
                .orderByAsc(MesShiftAssignment::getShiftCode);
        Page<MesShiftAssignment> page = shiftAssignmentMapper.selectPage(
                newPage(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toShiftAssignmentVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesShiftAssignmentVO cancelShiftAssignment(Long id) {
        MesShiftAssignment assignment = shiftAssignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.shift-schedule-record-found-2", "排班记录不存在：{0}", id);
        }
        if (!SHIFT_CANCELLED.equals(assignment.getStatus())) {
            assignment.setStatus(SHIFT_CANCELLED);
            shiftAssignmentMapper.updateById(assignment);
        }
        return toShiftAssignmentVO(assignment);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesHandoverVO createHandover(MesHandoverSaveRequest request) {
        MesTeam team = requireTeam(request.getTeamId());
        String handoverNo = StringUtils.hasText(request.getHandoverNo())
                ? request.getHandoverNo() : MesCodeGenerator.nextNo("HO");
        assertHandoverUnique(handoverNo);
        MesHandover handover = new MesHandover();
        handover.setHandoverNo(handoverNo);
        handover.setTeamId(team.getId());
        handover.setTeamCode(team.getCode());
        handover.setFromShiftCode(request.getFromShiftCode());
        handover.setToShiftCode(request.getToShiftCode());
        handover.setHandoverDate(request.getHandoverDate());
        handover.setFromEmployeeId(request.getFromEmployeeId());
        handover.setFromEmployeeName(request.getFromEmployeeName());
        handover.setToEmployeeId(request.getToEmployeeId());
        handover.setToEmployeeName(request.getToEmployeeName());
        handover.setContent(request.getContent());
        handover.setPendingIssues(request.getPendingIssues());
        handover.setStatus(HANDOVER_DRAFT);
        handover.setRemark(request.getRemark());
        handoverMapper.insert(handover);
        return toHandoverVO(handover);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesHandoverVO> pageHandovers(MesHandoverQueryRequest request) {
        LambdaQueryWrapper<MesHandover> wrapper = new LambdaQueryWrapper<MesHandover>()
                .like(StringUtils.hasText(request.getHandoverNo()), MesHandover::getHandoverNo,
                        request.getHandoverNo())
                .eq(request.getTeamId() != null, MesHandover::getTeamId, request.getTeamId())
                .eq(request.getHandoverDate() != null, MesHandover::getHandoverDate, request.getHandoverDate())
                .eq(StringUtils.hasText(request.getStatus()), MesHandover::getStatus, request.getStatus())
                .orderByDesc(MesHandover::getHandoverDate)
                .orderByDesc(MesHandover::getCreateTime);
        Page<MesHandover> page = handoverMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toHandoverVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesHandoverVO confirmHandover(Long id, MesHandoverConfirmRequest request) {
        MesHandover handover = handoverMapper.selectById(id);
        if (handover == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.shift-handover-record-found", "交接班记录不存在：{0}", id);
        }
        if (HANDOVER_CONFIRMED.equals(handover.getStatus())) {
            // MES-8005 同一交接单重复确认
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.handover-single-confirmation-duplicate-confirmation", "交接单已确认，请勿重复确认");
        }
        handover.setStatus(HANDOVER_CONFIRMED);
        handover.setConfirmedBy(request.getConfirmedBy());
        handover.setConfirmedAt(LocalDateTime.now());
        handoverMapper.updateById(handover);
        return toHandoverVO(handover);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkHourVO registerWorkHour(MesWorkHourSaveRequest request) {
        MesWorkOrderTask task = taskMapper.selectById(request.getTaskId());
        if (task == null) {
            // MES-8006 任务在租户内不可见或不存在
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.task-found", "任务不存在：{0}", request.getTaskId());
        }
        String source = StringUtils.hasText(request.getSource()) ? request.getSource() : SOURCE_MANUAL;
        assertNetSeconds(request, source);
        assertWorkHourUnique(request);
        MesWorkHour workHour = new MesWorkHour();
        workHour.setEmployeeId(request.getEmployeeId());
        workHour.setEmployeeName(request.getEmployeeName());
        workHour.setWorkDate(request.getWorkDate());
        workHour.setTaskId(task.getId());
        workHour.setTaskNo(task.getTaskNo());
        workHour.setWorkOrderId(task.getWorkOrderId());
        workHour.setWorkOrderCode(task.getWorkOrderCode());
        workHour.setStartAt(request.getStartAt());
        workHour.setEndAt(request.getEndAt());
        workHour.setPauseSeconds(request.getPauseSeconds() == null ? 0L : request.getPauseSeconds());
        workHour.setNetSeconds(netSeconds(request));
        workHour.setSource(source);
        workHour.setStatus(WORK_HOUR_VALID);
        workHour.setRemark(request.getRemark());
        workHourMapper.insert(workHour);
        return toWorkHourVO(workHour);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesWorkHourVO> pageWorkHours(MesWorkHourQueryRequest request) {
        LambdaQueryWrapper<MesWorkHour> wrapper = new LambdaQueryWrapper<MesWorkHour>()
                .eq(request.getEmployeeId() != null, MesWorkHour::getEmployeeId, request.getEmployeeId())
                .eq(request.getWorkDate() != null, MesWorkHour::getWorkDate, request.getWorkDate())
                .eq(request.getTaskId() != null, MesWorkHour::getTaskId, request.getTaskId())
                .eq(StringUtils.hasText(request.getSource()), MesWorkHour::getSource, request.getSource())
                .eq(StringUtils.hasText(request.getStatus()), MesWorkHour::getStatus, request.getStatus())
                .orderByDesc(MesWorkHour::getWorkDate)
                .orderByDesc(MesWorkHour::getStartAt);
        Page<MesWorkHour> page = workHourMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toWorkHourVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkHourVO voidWorkHour(Long id) {
        MesWorkHour workHour = workHourMapper.selectById(id);
        if (workHour == null) {
            // MES-8011 跨租户同 ID 记录不可见，统一按不存在处理
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.work-hours-record-found", "工时记录不存在：{0}", id);
        }
        if (!WORK_HOUR_VOID.equals(workHour.getStatus())) {
            workHour.setStatus(WORK_HOUR_VOID);
            workHourMapper.updateById(workHour);
        }
        return toWorkHourVO(workHour);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesPerformanceStatVO> pagePerformance(MesPerformanceQueryRequest request) {
        LambdaQueryWrapper<MesPerformanceStat> wrapper = new LambdaQueryWrapper<MesPerformanceStat>()
                .eq(StringUtils.hasText(request.getStatLevel()), MesPerformanceStat::getStatLevel,
                        request.getStatLevel())
                .eq(request.getEmployeeId() != null, MesPerformanceStat::getEmployeeId, request.getEmployeeId())
                .eq(request.getTeamId() != null, MesPerformanceStat::getTeamId, request.getTeamId())
                .eq(StringUtils.hasText(request.getPeriodCode()), MesPerformanceStat::getPeriodCode,
                        request.getPeriodCode())
                .eq(request.getWorkDate() != null, MesPerformanceStat::getWorkDate, request.getWorkDate())
                .eq(request.getLocked() != null, MesPerformanceStat::getLocked, request.getLocked())
                .orderByDesc(MesPerformanceStat::getWorkDate)
                .orderByAsc(MesPerformanceStat::getEmployeeId);
        Page<MesPerformanceStat> page = performanceStatMapper.selectPage(
                newPage(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toPerformanceVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MesPerformanceStatVO> aggregatePerformance(MesPerformanceAggregateRequest request) {
        String level = StringUtils.hasText(request.getStatLevel()) ? request.getStatLevel() : LEVEL_MONTHLY;
        LocalDate start = resolvePeriodStart(level, request.getPeriodCode());
        LocalDate end = LEVEL_DAILY.equals(level) ? start : start.withDayOfMonth(start.lengthOfMonth());
        assertPeriodUnlocked(level, request.getPeriodCode(), request.getTeamId());
        List<MesWorkHour> workHours = workHourMapper.selectList(new LambdaQueryWrapper<MesWorkHour>()
                .eq(MesWorkHour::getStatus, WORK_HOUR_VALID)
                .between(MesWorkHour::getWorkDate, start, end)
                .orderByAsc(MesWorkHour::getEmployeeId));
        Map<Long, Long> taskNetSeconds = new LinkedHashMap<>();
        Map<Long, Set<Long>> taskEmployees = new LinkedHashMap<>();
        for (MesWorkHour row : workHours) {
            if (row.getTaskId() == null) {
                continue;
            }
            taskNetSeconds.merge(row.getTaskId(), row.getNetSeconds() == null ? 0L : row.getNetSeconds(),
                    Long::sum);
            taskEmployees.computeIfAbsent(row.getTaskId(), key -> new LinkedHashSet<>()).add(row.getEmployeeId());
        }
        Map<Long, Integer> taskEmployeeCounts = new LinkedHashMap<>();
        taskEmployees.forEach((taskId, employees) -> taskEmployeeCounts.put(taskId, employees.size()));
        Map<Long, PerformanceTotals> taskTotals = loadTaskTotals(taskEmployees.keySet(), start, end);
        Map<Long, List<MesWorkHour>> grouped = workHours.stream()
                .collect(Collectors.groupingBy(MesWorkHour::getEmployeeId, LinkedHashMap::new,
                        Collectors.toList()));
        List<MesPerformanceStatVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<MesWorkHour>> entry : grouped.entrySet()) {
            MesPerformanceStat stat = buildPerformanceStat(entry.getKey(), entry.getValue(), level,
                    request.getPeriodCode(), start, taskNetSeconds, taskEmployeeCounts, taskTotals);
            if (request.getTeamId() != null && !request.getTeamId().equals(stat.getTeamId())) {
                continue;
            }
            upsertPerformanceStat(stat);
            result.add(toPerformanceVO(stat));
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesPerformanceLockVO lockPerformance(MesPerformanceLockRequest request) {
        List<MesPerformanceStat> stats = performanceStatMapper.selectList(
                new LambdaQueryWrapper<MesPerformanceStat>()
                        .eq(MesPerformanceStat::getStatLevel, LEVEL_MONTHLY)
                        .eq(MesPerformanceStat::getPeriodCode, request.getPeriodCode())
                        .eq(request.getTeamId() != null, MesPerformanceStat::getTeamId, request.getTeamId()));
        if (stats.isEmpty()) {
            // MES-8014 期间无月统计不可锁定
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.monthly-performance-statistics-period-cannot-lock", "该期间暂无月度绩效统计，无法锁定");
        }
        LocalDateTime now = LocalDateTime.now();
        int lockedCount = 0;
        int alreadyLocked = 0;
        for (MesPerformanceStat stat : stats) {
            if (Integer.valueOf(1).equals(stat.getLocked())) {
                alreadyLocked++;
                continue;
            }
            stat.setLocked(1);
            stat.setLockedAt(now);
            stat.setLockedBy(request.getLockedBy());
            performanceStatMapper.updateById(stat);
            lockedCount++;
        }
        MesPerformanceLockVO vo = new MesPerformanceLockVO();
        vo.setPeriodCode(request.getPeriodCode());
        vo.setLockedCount(lockedCount);
        vo.setAlreadyLockedCount(alreadyLocked);
        vo.setLockedAt(now);
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSkillMatrixVO saveSkill(MesSkillMatrixSaveRequest request) {
        MesSkillMatrix existing = request.getId() == null ? null : skillMatrixMapper.selectById(request.getId());
        if (request.getId() != null && existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.skill-record-found", "技能记录不存在：{0}", request.getId());
        }
        assertSkillUnique(request.getEmployeeId(), request.getSkillCode(), request.getId());
        MesSkillMatrix skill = existing == null ? new MesSkillMatrix() : existing;
        skill.setEmployeeId(request.getEmployeeId());
        skill.setEmployeeName(request.getEmployeeName());
        skill.setSkillCode(request.getSkillCode());
        skill.setSkillName(request.getSkillName());
        skill.setSkillLevel(StringUtils.hasText(request.getSkillLevel()) ? request.getSkillLevel() : "JUNIOR");
        skill.setCertifiedAt(request.getCertifiedAt());
        skill.setExpiryAt(request.getExpiryAt());
        skill.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_ENABLED);
        skill.setRemark(request.getRemark());
        if (existing == null) {
            skillMatrixMapper.insert(skill);
        } else {
            skillMatrixMapper.updateById(skill);
        }
        return toSkillMatrixVO(skill);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesSkillMatrixVO> pageSkills(MesSkillMatrixQueryRequest request) {
        LambdaQueryWrapper<MesSkillMatrix> wrapper = new LambdaQueryWrapper<MesSkillMatrix>()
                .eq(request.getEmployeeId() != null, MesSkillMatrix::getEmployeeId, request.getEmployeeId())
                .like(StringUtils.hasText(request.getSkillCode()), MesSkillMatrix::getSkillCode,
                        request.getSkillCode())
                .eq(StringUtils.hasText(request.getSkillLevel()), MesSkillMatrix::getSkillLevel,
                        request.getSkillLevel())
                .eq(StringUtils.hasText(request.getStatus()), MesSkillMatrix::getStatus, request.getStatus())
                .orderByDesc(MesSkillMatrix::getCreateTime);
        Page<MesSkillMatrix> page = skillMatrixMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toSkillMatrixVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 校验班组存在
     *
     * @param teamId 班组 ID
     * @return 班组实体
     */
    private MesTeam requireTeam(Long teamId) {
        MesTeam team = teamMapper.selectById(teamId);
        if (team == null) {
            // MES-8001 班组不存在或跨租户不可见
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.team-found", "班组不存在：{0}", teamId);
        }
        return team;
    }

    /**
     * 校验班组成员存在
     *
     * @param id 成员 ID
     * @return 成员实体
     */
    private MesTeamMember requireTeamMember(Long id) {
        MesTeamMember member = teamMemberMapper.selectById(id);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.team-member-found-2", "班组成员不存在：{0}", id);
        }
        return member;
    }

    /**
     * 校验工位存在
     *
     * @param stationId 工位 ID
     * @return 工位实体
     */
    private MesStation requireStation(Long stationId) {
        MesStation station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.workforce.workstation-found", "工位不存在：{0}", stationId);
        }
        return station;
    }

    /**
     * 校验班组成员唯一
     *
     * @param teamId 班组 ID
     * @param employeeId 员工 ID
     * @param excludeId 排除的成员 ID
     */
    private void assertTeamMemberUnique(Long teamId, Long employeeId, Long excludeId) {
        long count = teamMemberMapper.selectCount(new LambdaQueryWrapper<MesTeamMember>()
                .eq(MesTeamMember::getTeamId, teamId)
                .eq(MesTeamMember::getEmployeeId, employeeId)
                .ne(excludeId != null, MesTeamMember::getId, excludeId));
        if (count > 0) {
            // MES-8002 同班组同员工唯一
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.employee-already-in-team", "该员工已在班组中：{0}", employeeId);
        }
    }

    /**
     * 校验排班时间区间
     *
     * @param startAt 开始时间
     * @param endAt 结束时间
     */
    private void assertShiftTime(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            // MES-8008 结束时间必须晚于开始时间
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.shift-schedule-end-time-later-than", "排班结束时间必须晚于开始时间");
        }
    }

    /**
     * 校验排班记录唯一
     *
     * @param request 保存请求
     * @param existing 既有排班（编辑场景）
     */
    private void assertShiftUnique(MesShiftAssignmentSaveRequest request, MesShiftAssignment existing) {
        long count = shiftAssignmentMapper.selectCount(new LambdaQueryWrapper<MesShiftAssignment>()
                .eq(MesShiftAssignment::getStationId, request.getStationId())
                .eq(MesShiftAssignment::getShiftDate, request.getShiftDate())
                .eq(MesShiftAssignment::getShiftCode, request.getShiftCode())
                .eq(MesShiftAssignment::getEmployeeId, request.getEmployeeId())
                .ne(existing != null, MesShiftAssignment::getId, existing == null ? null : existing.getId()));
        if (count > 0) {
            // MES-8004 同工位班次员工唯一
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.employee-already-scheduled-workstation-shift", "该员工已在此工位班次排班");
        }
    }

    /**
     * 校验主操作员唯一
     *
     * @param request 保存请求
     * @param existing 既有排班（编辑场景）
     */
    private void assertPrimaryOperatorUnique(MesShiftAssignmentSaveRequest request, MesShiftAssignment existing) {
        if (request.getPrimaryOperator() == null || request.getPrimaryOperator() != 1) {
            return;
        }
        long count = shiftAssignmentMapper.selectCount(new LambdaQueryWrapper<MesShiftAssignment>()
                .eq(MesShiftAssignment::getStationId, request.getStationId())
                .eq(MesShiftAssignment::getShiftDate, request.getShiftDate())
                .eq(MesShiftAssignment::getShiftCode, request.getShiftCode())
                .eq(MesShiftAssignment::getPrimaryOperator, 1)
                .ne(MesShiftAssignment::getStatus, SHIFT_CANCELLED)
                .ne(existing != null, MesShiftAssignment::getId, existing == null ? null : existing.getId()));
        if (count > 0) {
            // MES-8003 同一工位班次仅允许一名主操作
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.primary-operator-already-exists-workstation-shift", "该工位班次已存在主操作员");
        }
    }

    /**
     * 校验交接单号唯一
     *
     * @param handoverNo 交接单号
     */
    private void assertHandoverUnique(String handoverNo) {
        long count = handoverMapper.selectCount(new LambdaQueryWrapper<MesHandover>()
                .eq(MesHandover::getHandoverNo, handoverNo));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.handover-number-already-exists", "交接单号已存在：{0}", handoverNo);
        }
    }

    /**
     * 校验工时净秒数
     *
     * @param request 工时登记请求
     * @param source 工时来源
     */
    private void assertNetSeconds(MesWorkHourSaveRequest request, String source) {
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            // MES-8008 结束时间必须晚于开始时间
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.work-hours-end-time-later-than", "工时结束时间必须晚于开始时间");
        }
        if (netSeconds(request) < 0) {
            // MES-8009 净工时 = 结束-开始-暂停，必须非负
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.net-work-hours-negative", "净工时不能为负");
        }
        if (SOURCE_REPORT.equals(source) && !hasReport(request.getTaskId(), request.getWorkDate())) {
            // MES-8007 报工来源必须存在当日报工
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.work-report-task-on-day-source", "该任务当日无报工记录，不能登记报工来源工时");
        }
    }

    /**
     * 计算净工时秒数
     *
     * @param request 工时登记请求
     * @return 净工时秒数
     */
    private long netSeconds(MesWorkHourSaveRequest request) {
        long pause = request.getPauseSeconds() == null ? 0L : request.getPauseSeconds();
        return Duration.between(request.getStartAt(), request.getEndAt()).getSeconds() - pause;
    }

    /**
     * 判断任务当日是否已有报工
     *
     * @param taskId 任务 ID
     * @param workDate 工作日期
     * @return 是否存在报工
     */
    private boolean hasReport(Long taskId, LocalDate workDate) {
        return reportMapper.selectCount(new LambdaQueryWrapper<MesProductionReport>()
                .eq(MesProductionReport::getTaskId, taskId)
                .ge(MesProductionReport::getReportedAt, workDate.atStartOfDay())
                .lt(MesProductionReport::getReportedAt, workDate.plusDays(1).atStartOfDay())) > 0;
    }

    /**
     * 校验工时记录唯一
     *
     * @param request 工时登记请求
     */
    private void assertWorkHourUnique(MesWorkHourSaveRequest request) {
        long count = workHourMapper.selectCount(new LambdaQueryWrapper<MesWorkHour>()
                .eq(MesWorkHour::getEmployeeId, request.getEmployeeId())
                .eq(MesWorkHour::getTaskId, request.getTaskId())
                .eq(MesWorkHour::getStartAt, request.getStartAt()));
        if (count > 0) {
            // MES-8010 同员工同任务同开始时间唯一
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.work-hours-employee-at-task-start", "该员工此任务开始时间的工时已存在");
        }
    }

    /**
     * 解析统计期间起始日期
     *
     * @param level 统计层级
     * @param periodCode 期间编码
     * @return 起始日期
     */
    private LocalDate resolvePeriodStart(String level, String periodCode) {
        try {
            if (LEVEL_DAILY.equals(level)) {
                return LocalDate.parse(periodCode, DAILY_FORMAT);
            }
            return YearMonth.parse(periodCode, MONTHLY_FORMAT).atDay(1);
        } catch (DateTimeParseException exception) {
            // MES-8012 期间编码与统计层级不匹配
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.invalid-period-code", "期间编码不合法：{0}", periodCode);
        }
    }

    /**
     * 校验统计期间未锁定
     *
     * @param level 统计层级
     * @param periodCode 期间编码
     * @param teamId 班组 ID（可空）
     */
    private void assertPeriodUnlocked(String level, String periodCode, Long teamId) {
        String monthCode = LEVEL_MONTHLY.equals(level) ? periodCode : periodCode.substring(0, 6);
        long locked = performanceStatMapper.selectCount(new LambdaQueryWrapper<MesPerformanceStat>()
                .eq(MesPerformanceStat::getStatLevel, LEVEL_MONTHLY)
                .eq(MesPerformanceStat::getPeriodCode, monthCode)
                .eq(MesPerformanceStat::getLocked, 1)
                .eq(teamId != null, MesPerformanceStat::getTeamId, teamId));
        if (locked > 0) {
            // MES-8013 月度锁定后禁止重算
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.period-locked-forbidden-recalculate", "期间已锁定，禁止重算");
        }
    }

    /**
     * 构建单员工绩效统计
     *
     * @param employeeId 员工 ID
     * @param rows 员工工时记录
     * @param level 统计层级
     * @param periodCode 期间编码
     * @param start 起始日期
     * @param taskNetSeconds 任务维度总净工时
     * @param taskEmployeeCounts 任务参与员工数
     * @param taskTotals 任务维度报工汇总
     * @return 绩效统计实体
     */
    private MesPerformanceStat buildPerformanceStat(Long employeeId, List<MesWorkHour> rows, String level,
                                                    String periodCode, LocalDate start,
                                                    Map<Long, Long> taskNetSeconds,
                                                    Map<Long, Integer> taskEmployeeCounts,
                                                    Map<Long, PerformanceTotals> taskTotals) {
        MesPerformanceStat stat = new MesPerformanceStat();
        stat.setStatLevel(level);
        stat.setEmployeeId(employeeId);
        stat.setEmployeeName(rows.get(0).getEmployeeName());
        stat.setWorkDate(start);
        stat.setPeriodCode(periodCode);
        long workSeconds = 0L;
        Map<Long, Long> employeeTaskSeconds = new LinkedHashMap<>();
        for (MesWorkHour row : rows) {
            workSeconds += row.getNetSeconds() == null ? 0L : row.getNetSeconds();
            if (row.getTaskId() != null) {
                employeeTaskSeconds.merge(row.getTaskId(),
                        row.getNetSeconds() == null ? 0L : row.getNetSeconds(), Long::sum);
            }
        }
        stat.setWorkSeconds(workSeconds);
        fillTeam(stat, employeeId);
        PerformanceTotals totals = allocateTotals(employeeTaskSeconds, taskNetSeconds, taskEmployeeCounts,
                taskTotals);
        stat.setProductionQuantity(totals.production);
        stat.setQualifiedQuantity(totals.qualified);
        stat.setDefectQuantity(totals.defect);
        stat.setStandardSeconds(totals.standardSeconds);
        stat.setEfficiency(ratio(BigDecimal.valueOf(totals.standardSeconds), BigDecimal.valueOf(workSeconds)));
        stat.setPassRate(ratio(totals.qualified, totals.production));
        stat.setLocked(0);
        return stat;
    }

    /**
     * 填充班组信息
     *
     * @param stat 绩效统计实体
     * @param employeeId 员工 ID
     */
    private void fillTeam(MesPerformanceStat stat, Long employeeId) {
        MesTeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<MesTeamMember>()
                .eq(MesTeamMember::getEmployeeId, employeeId)
                .eq(MesTeamMember::getStatus, STATUS_ENABLED)
                .orderByAsc(MesTeamMember::getId)
                .last("LIMIT 1"));
        if (member != null) {
            stat.setTeamId(member.getTeamId());
            stat.setTeamCode(member.getTeamCode());
        }
    }

    /**
     * 按任务汇总期间报工数量与标准工时
     *
     * @param taskIds 任务 ID 集合
     * @param start 起始日期
     * @param end 截止日期
     * @return 任务维度汇总结果
     */
    private Map<Long, PerformanceTotals> loadTaskTotals(Collection<Long> taskIds, LocalDate start, LocalDate end) {
        Map<Long, PerformanceTotals> totalsByTask = new LinkedHashMap<>();
        if (taskIds.isEmpty()) {
            return totalsByTask;
        }
        List<MesProductionReport> reports = reportMapper.selectList(new LambdaQueryWrapper<MesProductionReport>()
                .in(MesProductionReport::getTaskId, taskIds)
                .eq(MesProductionReport::getStatus, REPORT_SUBMITTED)
                .ge(MesProductionReport::getReportedAt, start.atStartOfDay())
                .lt(MesProductionReport::getReportedAt, end.plusDays(1).atStartOfDay()));
        for (MesProductionReport report : reports) {
            if (report.getTaskId() == null) {
                continue;
            }
            PerformanceTotals totals = totalsByTask.computeIfAbsent(report.getTaskId(),
                    key -> new PerformanceTotals());
            totals.production = totals.production.add(report.getReportQuantity() == null
                    ? BigDecimal.ZERO : report.getReportQuantity());
            totals.qualified = totals.qualified.add(report.getQualifiedQuantity() == null
                    ? BigDecimal.ZERO : report.getQualifiedQuantity());
            totals.defect = totals.defect.add(report.getDefectQuantity() == null
                    ? BigDecimal.ZERO : report.getDefectQuantity());
        }
        return totalsByTask;
    }

    /**
     * 按工时占比把任务报工分摊到员工（D13 多人报工口径）
     *
     * @param employeeTaskSeconds 员工任务维度净工时
     * @param taskNetSeconds 任务维度总净工时
     * @param taskEmployeeCounts 任务参与员工数
     * @param taskTotals 任务维度报工汇总
     * @return 员工维度汇总结果
     */
    private PerformanceTotals allocateTotals(Map<Long, Long> employeeTaskSeconds,
                                             Map<Long, Long> taskNetSeconds,
                                             Map<Long, Integer> taskEmployeeCounts,
                                             Map<Long, PerformanceTotals> taskTotals) {
        PerformanceTotals totals = new PerformanceTotals();
        Map<Long, BigDecimal> allocatedByTask = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> entry : employeeTaskSeconds.entrySet()) {
            PerformanceTotals taskTotal = taskTotals.get(entry.getKey());
            if (taskTotal == null) {
                continue;
            }
            BigDecimal rate = allocationRate(entry.getValue(), taskNetSeconds.get(entry.getKey()),
                    taskEmployeeCounts.get(entry.getKey()));
            BigDecimal production = taskTotal.production.multiply(rate).setScale(6, RoundingMode.HALF_UP);
            totals.production = totals.production.add(production);
            totals.qualified = totals.qualified.add(
                    taskTotal.qualified.multiply(rate).setScale(6, RoundingMode.HALF_UP));
            totals.defect = totals.defect.add(
                    taskTotal.defect.multiply(rate).setScale(6, RoundingMode.HALF_UP));
            if (production.signum() > 0) {
                allocatedByTask.merge(entry.getKey(), production, BigDecimal::add);
            }
        }
        totals.standardSeconds = sumStandardSeconds(allocatedByTask);
        return totals;
    }

    /**
     * 计算员工在任务上的分摊比例
     *
     * <p>任务总净工时大于 0 时按工时占比分摊；全部为 0 时在参与员工间均分。</p>
     *
     * @param employeeSeconds 员工净工时
     * @param taskSeconds 任务总净工时
     * @param employeeCount 任务参与员工数
     * @return 分摊比例（8 位小数）
     */
    private BigDecimal allocationRate(long employeeSeconds, Long taskSeconds, Integer employeeCount) {
        if (taskSeconds != null && taskSeconds > 0) {
            return BigDecimal.valueOf(employeeSeconds)
                    .divide(BigDecimal.valueOf(taskSeconds), 8, RoundingMode.HALF_UP);
        }
        int count = employeeCount == null || employeeCount <= 0 ? 1 : employeeCount;
        return BigDecimal.ONE.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    /**
     * 按任务数量折算标准工时秒数
     *
     * <p>标准工时取已发布工艺路线快照的工序标准分钟 × 报工数量，缺少快照时按 0 计。</p>
     *
     * @param quantityByTask 任务数量
     * @return 标准工时秒数
     */
    private long sumStandardSeconds(Map<Long, BigDecimal> quantityByTask) {
        if (quantityByTask.isEmpty()) {
            return 0L;
        }
        Set<Long> routingIds = releasedRoutingIds();
        if (routingIds.isEmpty()) {
            return 0L;
        }
        List<MesWorkOrderTask> tasks = taskMapper.selectBatchIds(quantityByTask.keySet());
        long total = 0L;
        for (MesWorkOrderTask task : tasks) {
            BigDecimal quantity = quantityByTask.get(task.getId());
            total += standardSeconds(task.getOperationCode(), quantity, routingIds);
        }
        return total;
    }

    /**
     * 查询已发布工艺路线快照 ID
     *
     * @return 工艺路线快照 ID 集合
     */
    private Set<Long> releasedRoutingIds() {
        List<MesRoutingSnapshot> snapshots = routingSnapshotMapper.selectList(
                new LambdaQueryWrapper<MesRoutingSnapshot>()
                        .eq(MesRoutingSnapshot::getStatus, ROUTING_RELEASED));
        return snapshots.stream().map(MesRoutingSnapshot::getId).collect(Collectors.toSet());
    }

    /**
     * 计算单个任务的标准工时秒数
     *
     * @param operationCode 工序编码
     * @param quantity 数量
     * @param routingIds 工艺路线快照 ID 集合
     * @return 标准工时秒数
     */
    private long standardSeconds(String operationCode, BigDecimal quantity, Set<Long> routingIds) {
        if (!StringUtils.hasText(operationCode) || quantity == null || quantity.signum() <= 0) {
            return 0L;
        }
        MesRoutingOperation operation = routingOperationMapper.selectOne(
                new LambdaQueryWrapper<MesRoutingOperation>()
                        .in(MesRoutingOperation::getRoutingId, routingIds)
                        .eq(MesRoutingOperation::getOperationCode, operationCode)
                        .orderByAsc(MesRoutingOperation::getSeqNo)
                        .last("LIMIT 1"));
        if (operation == null || operation.getStandardMinutes() == null) {
            return 0L;
        }
        return operation.getStandardMinutes().multiply(quantity).multiply(SECONDS_PER_MINUTE)
                .setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 写入或更新绩效统计
     *
     * @param stat 绩效统计实体
     */
    private void upsertPerformanceStat(MesPerformanceStat stat) {
        MesPerformanceStat existing = performanceStatMapper.selectOne(
                new LambdaQueryWrapper<MesPerformanceStat>()
                        .eq(MesPerformanceStat::getStatLevel, stat.getStatLevel())
                        .eq(MesPerformanceStat::getEmployeeId, stat.getEmployeeId())
                        .eq(MesPerformanceStat::getPeriodCode, stat.getPeriodCode())
                        .eq(MesPerformanceStat::getWorkDate, stat.getWorkDate()));
        if (existing == null) {
            performanceStatMapper.insert(stat);
            return;
        }
        existing.setEmployeeName(stat.getEmployeeName());
        existing.setTeamId(stat.getTeamId());
        existing.setTeamCode(stat.getTeamCode());
        existing.setProductionQuantity(stat.getProductionQuantity());
        existing.setQualifiedQuantity(stat.getQualifiedQuantity());
        existing.setDefectQuantity(stat.getDefectQuantity());
        existing.setWorkSeconds(stat.getWorkSeconds());
        existing.setStandardSeconds(stat.getStandardSeconds());
        existing.setEfficiency(stat.getEfficiency());
        existing.setPassRate(stat.getPassRate());
        performanceStatMapper.updateById(existing);
        stat.setId(existing.getId());
    }

    /**
     * 计算比率
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 比率（分母非正时返回 0）
     */
    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 构造分页参数
     *
     * @param current 当前页
     * @param size 每页条数
     * @return 分页参数
     */
    private <T> Page<T> newPage(Long current, Long size) {
        long pageNo = current == null || current < 1 ? 1L : current;
        long pageSize = size == null || size < 1 ? 10L : Math.min(size, 1000L);
        return new Page<>(pageNo, pageSize);
    }

    /**
     * 校验技能记录唯一
     *
     * @param employeeId 员工 ID
     * @param skillCode 技能编码
     * @param excludeId 排除的技能记录 ID
     */
    private void assertSkillUnique(Long employeeId, String skillCode, Long excludeId) {
        long count = skillMatrixMapper.selectCount(new LambdaQueryWrapper<MesSkillMatrix>()
                .eq(MesSkillMatrix::getEmployeeId, employeeId)
                .eq(MesSkillMatrix::getSkillCode, skillCode)
                .ne(excludeId != null, MesSkillMatrix::getId, excludeId));
        if (count > 0) {
            // MES-8015 同员工同技能编码唯一
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.workforce.skill-already-registered-employee", "该员工此技能已登记：{0}", skillCode);
        }
    }

    /**
     * 转换为班组成员视图
     *
     * @param member 成员实体
     * @return 成员视图
     */
    private MesTeamMemberVO toTeamMemberVO(MesTeamMember member) {
        MesTeamMemberVO vo = new MesTeamMemberVO();
        vo.setId(member.getId());
        vo.setTeamId(member.getTeamId());
        vo.setTeamCode(member.getTeamCode());
        vo.setEmployeeId(member.getEmployeeId());
        vo.setEmployeeName(member.getEmployeeName());
        vo.setPrimaryOperator(member.getPrimaryOperator());
        vo.setSkillLevel(member.getSkillLevel());
        vo.setJoinDate(member.getJoinDate());
        vo.setLeaveDate(member.getLeaveDate());
        vo.setStatus(member.getStatus());
        vo.setRemark(member.getRemark());
        vo.setCreateTime(member.getCreateTime());
        return vo;
    }

    /**
     * 转换为排班视图
     *
     * @param assignment 排班实体
     * @return 排班视图
     */
    private MesShiftAssignmentVO toShiftAssignmentVO(MesShiftAssignment assignment) {
        MesShiftAssignmentVO vo = new MesShiftAssignmentVO();
        vo.setId(assignment.getId());
        vo.setShiftDate(assignment.getShiftDate());
        vo.setShiftCode(assignment.getShiftCode());
        vo.setTeamId(assignment.getTeamId());
        vo.setTeamCode(assignment.getTeamCode());
        vo.setStationId(assignment.getStationId());
        vo.setStationCode(assignment.getStationCode());
        vo.setWorkCenterId(assignment.getWorkCenterId());
        vo.setWorkCenterCode(assignment.getWorkCenterCode());
        vo.setEmployeeId(assignment.getEmployeeId());
        vo.setEmployeeName(assignment.getEmployeeName());
        vo.setPrimaryOperator(assignment.getPrimaryOperator());
        vo.setStartAt(assignment.getStartAt());
        vo.setEndAt(assignment.getEndAt());
        vo.setStatus(assignment.getStatus());
        vo.setRemark(assignment.getRemark());
        vo.setCreateTime(assignment.getCreateTime());
        return vo;
    }

    /**
     * 转换为交接班视图
     *
     * @param handover 交接班实体
     * @return 交接班视图
     */
    private MesHandoverVO toHandoverVO(MesHandover handover) {
        MesHandoverVO vo = new MesHandoverVO();
        vo.setId(handover.getId());
        vo.setHandoverNo(handover.getHandoverNo());
        vo.setTeamId(handover.getTeamId());
        vo.setTeamCode(handover.getTeamCode());
        vo.setFromShiftCode(handover.getFromShiftCode());
        vo.setToShiftCode(handover.getToShiftCode());
        vo.setHandoverDate(handover.getHandoverDate());
        vo.setFromEmployeeId(handover.getFromEmployeeId());
        vo.setFromEmployeeName(handover.getFromEmployeeName());
        vo.setToEmployeeId(handover.getToEmployeeId());
        vo.setToEmployeeName(handover.getToEmployeeName());
        vo.setContent(handover.getContent());
        vo.setPendingIssues(handover.getPendingIssues());
        vo.setStatus(handover.getStatus());
        vo.setConfirmedBy(handover.getConfirmedBy());
        vo.setConfirmedAt(handover.getConfirmedAt());
        vo.setRemark(handover.getRemark());
        vo.setCreateTime(handover.getCreateTime());
        return vo;
    }

    /**
     * 转换为工时视图
     *
     * @param workHour 工时实体
     * @return 工时视图
     */
    private MesWorkHourVO toWorkHourVO(MesWorkHour workHour) {
        MesWorkHourVO vo = new MesWorkHourVO();
        vo.setId(workHour.getId());
        vo.setEmployeeId(workHour.getEmployeeId());
        vo.setEmployeeName(workHour.getEmployeeName());
        vo.setWorkDate(workHour.getWorkDate());
        vo.setTaskId(workHour.getTaskId());
        vo.setTaskNo(workHour.getTaskNo());
        vo.setWorkOrderId(workHour.getWorkOrderId());
        vo.setWorkOrderCode(workHour.getWorkOrderCode());
        vo.setStartAt(workHour.getStartAt());
        vo.setEndAt(workHour.getEndAt());
        vo.setPauseSeconds(workHour.getPauseSeconds());
        vo.setNetSeconds(workHour.getNetSeconds());
        vo.setSource(workHour.getSource());
        vo.setStatus(workHour.getStatus());
        vo.setRemark(workHour.getRemark());
        vo.setCreateTime(workHour.getCreateTime());
        return vo;
    }

    /**
     * 转换为绩效统计视图
     *
     * @param stat 绩效统计实体
     * @return 绩效统计视图
     */
    private MesPerformanceStatVO toPerformanceVO(MesPerformanceStat stat) {
        MesPerformanceStatVO vo = new MesPerformanceStatVO();
        vo.setId(stat.getId());
        vo.setStatLevel(stat.getStatLevel());
        vo.setEmployeeId(stat.getEmployeeId());
        vo.setEmployeeName(stat.getEmployeeName());
        vo.setTeamId(stat.getTeamId());
        vo.setTeamCode(stat.getTeamCode());
        vo.setWorkDate(stat.getWorkDate());
        vo.setPeriodCode(stat.getPeriodCode());
        vo.setProductionQuantity(stat.getProductionQuantity());
        vo.setQualifiedQuantity(stat.getQualifiedQuantity());
        vo.setDefectQuantity(stat.getDefectQuantity());
        vo.setWorkSeconds(stat.getWorkSeconds());
        vo.setStandardSeconds(stat.getStandardSeconds());
        vo.setEfficiency(stat.getEfficiency());
        vo.setPassRate(stat.getPassRate());
        vo.setLocked(stat.getLocked());
        vo.setLockedAt(stat.getLockedAt());
        vo.setLockedBy(stat.getLockedBy());
        vo.setCreateTime(stat.getCreateTime());
        return vo;
    }

    /**
     * 转换为技能矩阵视图
     *
     * @param skill 技能实体
     * @return 技能视图
     */
    private MesSkillMatrixVO toSkillMatrixVO(MesSkillMatrix skill) {
        MesSkillMatrixVO vo = new MesSkillMatrixVO();
        vo.setId(skill.getId());
        vo.setEmployeeId(skill.getEmployeeId());
        vo.setEmployeeName(skill.getEmployeeName());
        vo.setSkillCode(skill.getSkillCode());
        vo.setSkillName(skill.getSkillName());
        vo.setSkillLevel(skill.getSkillLevel());
        vo.setCertifiedAt(skill.getCertifiedAt());
        vo.setExpiryAt(skill.getExpiryAt());
        vo.setStatus(skill.getStatus());
        vo.setRemark(skill.getRemark());
        vo.setCreateTime(skill.getCreateTime());
        return vo;
    }

    /**
     * 绩效汇总中间结果
     *
     * @author BlueWhale
     */
    private static final class PerformanceTotals {

        /** 产量 */
        private BigDecimal production = BigDecimal.ZERO;

        /** 合格数量 */
        private BigDecimal qualified = BigDecimal.ZERO;

        /** 不良数量 */
        private BigDecimal defect = BigDecimal.ZERO;

        /** 标准工时秒数 */
        private long standardSeconds;
    }
}

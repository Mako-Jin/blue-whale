package com.bluewhale.mes.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.dispatch.service.MesTaskProgressService;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderTaskVO;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.equipment.service.MesEquipmentService;
import com.bluewhale.mes.equipment.vo.MesEquipmentGateVO;
import com.bluewhale.mes.execution.dto.MesExceptionActionRequest;
import com.bluewhale.mes.execution.dto.MesExceptionCreateRequest;
import com.bluewhale.mes.execution.dto.MesMaterialIssueRequest;
import com.bluewhale.mes.execution.dto.MesMaterialReturnRequest;
import com.bluewhale.mes.execution.dto.MesReportQueryRequest;
import com.bluewhale.mes.execution.dto.MesReportRequest;
import com.bluewhale.mes.execution.dto.MesTaskActionRequest;
import com.bluewhale.mes.execution.entity.MesExceptionAction;
import com.bluewhale.mes.execution.entity.MesExceptionEvent;
import com.bluewhale.mes.execution.entity.MesMaterialIssue;
import com.bluewhale.mes.execution.entity.MesMaterialIssueLine;
import com.bluewhale.mes.execution.entity.MesMaterialReturn;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.entity.MesReportDefect;
import com.bluewhale.mes.execution.entity.MesWipSnapshot;
import com.bluewhale.mes.execution.mapper.MesExceptionActionMapper;
import com.bluewhale.mes.execution.mapper.MesExceptionEventMapper;
import com.bluewhale.mes.execution.mapper.MesMaterialIssueLineMapper;
import com.bluewhale.mes.execution.mapper.MesMaterialIssueMapper;
import com.bluewhale.mes.execution.mapper.MesMaterialReturnMapper;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.execution.mapper.MesReportDefectMapper;
import com.bluewhale.mes.execution.mapper.MesWipSnapshotMapper;
import com.bluewhale.mes.execution.service.MesExecutionService;
import com.bluewhale.mes.execution.vo.MesExceptionVO;
import com.bluewhale.mes.execution.vo.MesProductionReportVO;
import com.bluewhale.mes.execution.vo.MesWipVO;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.bluewhale.mes.masterdata.entity.MesReasonCode;
import com.bluewhale.mes.masterdata.entity.MesStation;
import com.bluewhale.mes.masterdata.mapper.MesReasonCodeMapper;
import com.bluewhale.mes.masterdata.mapper.MesStationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * MES 生产执行应用服务实现
 *
 * <p>数量口径：报工 = 合格 + 不良；累计报工不得超过已派工；在制品 = 已派工 − 已完工 − 已报废。
 * 任务状态迁移：PLANNED → DISPATCHED（派工）→ IN_PROGRESS ⇄ PAUSED → COMPLETED / SCRAPPED。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MesExecutionServiceImpl implements MesExecutionService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 任务状态：已派工 */
    private static final String TASK_DISPATCHED = "DISPATCHED";

    /** 任务状态：执行中 */
    private static final String TASK_IN_PROGRESS = "IN_PROGRESS";

    /** 任务状态：已暂停 */
    private static final String TASK_PAUSED = "PAUSED";

    /** 任务状态：已完工 */
    private static final String TASK_COMPLETED = "COMPLETED";

    /** 任务状态：已报废 */
    private static final String TASK_SCRAPPED = "SCRAPPED";

    /** 报工状态：已提交 */
    private static final String REPORT_SUBMITTED = "SUBMITTED";

    /** 异常状态：待处理 */
    private static final String EXCEPTION_OPEN = "OPEN";

    /** 异常状态：已确认 */
    private static final String EXCEPTION_ACKNOWLEDGED = "ACKNOWLEDGED";

    /** 异常状态：已解决 */
    private static final String EXCEPTION_RESOLVED = "RESOLVED";

    /** 异常状态：已关闭 */
    private static final String EXCEPTION_CLOSED = "CLOSED";

    /** 默认领料类型 */
    private static final String ISSUE_TYPE_ISSUE = "ISSUE";

    /** 补料类型 */
    private static final String ISSUE_TYPE_SUPPLEMENT = "SUPPLEMENT";

    private final MesWorkOrderTaskMapper taskMapper;

    private final MesWorkOrderMapper mesWorkOrderMapper;

    private final MesProductionReportMapper reportMapper;

    private final MesReportDefectMapper reportDefectMapper;

    private final MesWipSnapshotMapper wipMapper;

    private final MesExceptionEventMapper exceptionMapper;

    private final MesExceptionActionMapper exceptionActionMapper;

    private final MesMaterialIssueMapper materialIssueMapper;

    private final MesMaterialIssueLineMapper materialIssueLineMapper;

    private final MesMaterialReturnMapper materialReturnMapper;

    private final MesStationMapper stationMapper;

    private final MesReasonCodeMapper reasonCodeMapper;

    private final MesTaskProgressService progressService;

    private final MesEquipmentService equipmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderTaskVO start(Long taskId, MesTaskActionRequest request) {
        MesWorkOrderTask task = requireTask(taskId);
        assertStatus(task, Set.of(TASK_DISPATCHED), "开工");
        if (request != null && StringUtils.hasText(request.getEquipmentCode())) {
            MesEquipmentGateVO gate = equipmentService.gate(request.getEquipmentCode());
            if (!Boolean.TRUE.equals(gate.getPassed())) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "开工门禁未通过：" + gate.getMessage());
            }
        }
        task.setStatus(TASK_IN_PROGRESS);
        updateTask(task);
        progressService.refreshWorkOrder(task.getWorkOrderId());
        return toTaskVo(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderTaskVO pause(Long taskId, MesTaskActionRequest request) {
        MesWorkOrderTask task = requireTask(taskId);
        assertStatus(task, Set.of(TASK_IN_PROGRESS), "暂停");
        task.setStatus(TASK_PAUSED);
        updateTask(task);
        progressService.refreshWorkOrder(task.getWorkOrderId());
        return toTaskVo(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderTaskVO resume(Long taskId, MesTaskActionRequest request) {
        MesWorkOrderTask task = requireTask(taskId);
        assertStatus(task, Set.of(TASK_PAUSED), "恢复");
        task.setStatus(TASK_IN_PROGRESS);
        updateTask(task);
        progressService.refreshWorkOrder(task.getWorkOrderId());
        return toTaskVo(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderTaskVO complete(Long taskId, MesTaskActionRequest request) {
        MesWorkOrderTask task = requireTask(taskId);
        assertStatus(task, Set.of(TASK_IN_PROGRESS, TASK_PAUSED), "完工");
        BigDecimal reported = zeroIfNull(task.getReportedQuantity());
        if (reported.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务尚未报工，不能完工");
        }
        task.setCompletedQuantity(zeroIfNull(task.getQualifiedQuantity()));
        task.setStatus(TASK_COMPLETED);
        updateTask(task);
        updateWip(task);
        progressService.refreshWorkOrder(task.getWorkOrderId());
        return toTaskVo(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderTaskVO scrap(Long taskId, MesTaskActionRequest request) {
        MesWorkOrderTask task = requireTask(taskId);
        assertStatus(task, Set.of(TASK_DISPATCHED, TASK_IN_PROGRESS, TASK_PAUSED), "报废");
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报废数量必须大于 0");
        }
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报废必须填写原因");
        }
        BigDecimal dispatched = zeroIfNull(task.getDispatchedQuantity());
        BigDecimal used = zeroIfNull(task.getCompletedQuantity())
                .add(zeroIfNull(task.getScrappedQuantity()))
                .add(request.getQuantity());
        if (used.compareTo(dispatched) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报废数量超过任务剩余可执行数量");
        }
        task.setScrappedQuantity(zeroIfNull(task.getScrappedQuantity()).add(request.getQuantity()));
        if (zeroIfNull(task.getCompletedQuantity()).add(task.getScrappedQuantity()).compareTo(dispatched) >= 0) {
            task.setStatus(TASK_SCRAPPED);
        }
        updateTask(task);
        updateWip(task);
        progressService.refreshWorkOrder(task.getWorkOrderId());
        return toTaskVo(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesProductionReportVO report(MesReportRequest request) {
        MesWorkOrderTask task = requireTask(request.getTaskId());
        assertStatus(task, Set.of(TASK_IN_PROGRESS, TASK_PAUSED), "报工");
        BigDecimal total = request.getQualifiedQuantity().add(request.getDefectQuantity());
        if (total.compareTo(request.getReportQuantity()) != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "合格数量 + 不良数量必须等于报工数量");
        }
        BigDecimal reported = zeroIfNull(task.getReportedQuantity()).add(request.getReportQuantity());
        if (reported.compareTo(zeroIfNull(task.getDispatchedQuantity())) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "累计报工数量超过已派工数量");
        }
        validateDefects(request);
        MesProductionReport report = buildReport(task, request);
        reportMapper.insert(report);
        insertDefects(report.getId(), request.getDefects());
        task.setReportedQuantity(reported);
        task.setQualifiedQuantity(zeroIfNull(task.getQualifiedQuantity()).add(request.getQualifiedQuantity()));
        task.setDefectQuantity(zeroIfNull(task.getDefectQuantity()).add(request.getDefectQuantity()));
        updateTask(task);
        updateWip(task);
        progressService.refreshWorkOrder(task.getWorkOrderId());
        return toReportVo(report);
    }

    @Override
    public PageResult<MesProductionReportVO> pageReports(MesReportQueryRequest request) {
        LambdaQueryWrapper<MesProductionReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getTaskId() != null, MesProductionReport::getTaskId, request.getTaskId());
        wrapper.like(StringUtils.hasText(request.getTaskNo()), MesProductionReport::getTaskNo, request.getTaskNo());
        wrapper.eq(request.getWorkOrderId() != null, MesProductionReport::getWorkOrderId, request.getWorkOrderId());
        wrapper.like(StringUtils.hasText(request.getWorkOrderCode()),
                MesProductionReport::getWorkOrderCode, request.getWorkOrderCode());
        wrapper.like(StringUtils.hasText(request.getReportNo()), MesProductionReport::getReportNo, request.getReportNo());
        wrapper.orderByDesc(MesProductionReport::getReportedAt);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MesProductionReport> page = reportMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<MesProductionReportVO> records = page.getRecords().stream().map(this::toReportVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<MesWipVO> wip(Long workOrderId) {
        LambdaQueryWrapper<MesWipSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(workOrderId != null, MesWipSnapshot::getWorkOrderId, workOrderId);
        wrapper.orderByDesc(MesWipSnapshot::getSnapshotTime);
        return wipMapper.selectList(wrapper).stream().map(this::toWipVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesExceptionVO raiseException(MesExceptionCreateRequest request) {
        MesWorkOrderTask task = request.getTaskId() == null ? null : requireTask(request.getTaskId());
        Long workOrderId = task != null ? task.getWorkOrderId() : request.getWorkOrderId();
        if (workOrderId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "异常必须关联工单或任务");
        }
        requireWorkOrder(workOrderId);
        MesExceptionEvent event = buildException(workOrderId, request);
        exceptionMapper.insert(event);
        recordAction(event.getId(), "REPORT", null, EXCEPTION_OPEN, request.getRemark());
        return toExceptionVo(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesExceptionVO handleException(Long id, MesExceptionActionRequest request) {
        MesExceptionEvent event = requireException(id);
        String from = event.getStatus();
        String to = transitionException(event, request);
        if (!StringUtils.hasText(event.getResolution()) && StringUtils.hasText(request.getResolution())) {
            event.setResolution(request.getResolution());
        }
        if (EXCEPTION_RESOLVED.equals(to) && event.getResolvedAt() == null) {
            event.setResolvedAt(LocalDateTime.now());
        }
        if (exceptionMapper.updateById(event) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "异常单已被其他用户修改，请刷新后重试");
        }
        recordAction(event.getId(), request.getActionType(), from, to, request.getRemark());
        return toExceptionVo(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String issueMaterial(MesMaterialIssueRequest request) {
        requireWorkOrder(request.getWorkOrderId());
        verifyTaskBelongsToOrder(request.getTaskId(), request.getWorkOrderId());
        String issueType = request.getIssueType() == null ? ISSUE_TYPE_ISSUE : request.getIssueType();
        if (ISSUE_TYPE_SUPPLEMENT.equals(issueType) && !StringUtils.hasText(request.getReason())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "补料必须填写补料原因");
        }
        MesMaterialIssue issue = new MesMaterialIssue();
        issue.setIssueNo(MesCodeGenerator.nextNo("MIS"));
        issue.setWorkOrderId(request.getWorkOrderId());
        issue.setTaskId(request.getTaskId());
        issue.setWarehouseCode(request.getWarehouseCode());
        issue.setIssueType(issueType);
        issue.setStatus("ISSUED");
        issue.setIssuedAt(LocalDateTime.now());
        issue.setReason(request.getReason());
        issue.setRemark(request.getRemark());
        materialIssueMapper.insert(issue);
        for (MesMaterialIssueRequest.Line line : request.getLines()) {
            MesMaterialIssueLine entity = new MesMaterialIssueLine();
            entity.setIssueId(issue.getId());
            entity.setItemCode(line.getItemCode());
            entity.setItemName(line.getItemName());
            entity.setQuantity(line.getQuantity());
            entity.setUom(line.getUom());
            entity.setBatchNo(line.getBatchNo());
            materialIssueLineMapper.insert(entity);
        }
        return issue.getIssueNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String returnMaterial(MesMaterialReturnRequest request) {
        requireWorkOrder(request.getWorkOrderId());
        verifyTaskBelongsToOrder(request.getTaskId(), request.getWorkOrderId());
        if (request.getIssueId() != null) {
            MesMaterialIssue issue = materialIssueMapper.selectById(request.getIssueId());
            if (issue == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "原领料单不存在：" + request.getIssueId());
            }
            if (!issue.getWorkOrderId().equals(request.getWorkOrderId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "原领料单不属于当前工单");
            }
        }
        MesMaterialReturn entity = new MesMaterialReturn();
        entity.setReturnNo(MesCodeGenerator.nextNo("MRT"));
        entity.setWorkOrderId(request.getWorkOrderId());
        entity.setTaskId(request.getTaskId());
        entity.setIssueId(request.getIssueId());
        entity.setWarehouseCode(request.getWarehouseCode());
        entity.setItemCode(request.getItemCode());
        entity.setItemName(request.getItemName());
        entity.setQuantity(request.getQuantity());
        entity.setUom(request.getUom());
        entity.setBatchNo(request.getBatchNo());
        entity.setReason(request.getReason());
        entity.setStatus("RETURNED");
        entity.setReturnedAt(LocalDateTime.now());
        materialReturnMapper.insert(entity);
        return entity.getReturnNo();
    }

    /**
     * 校验不良明细合计
     *
     * @param request 报工请求
     */
    private void validateDefects(MesReportRequest request) {
        if (request.getDefects() == null || request.getDefects().isEmpty()) {
            return;
        }
        BigDecimal total = request.getDefects().stream()
                .map(MesReportRequest.DefectItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(request.getDefectQuantity()) != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不良明细合计必须等于不良数量");
        }
    }

    /**
     * 构建报工实体
     *
     * @param task    任务
     * @param request 报工请求
     * @return 报工实体
     */
    private MesProductionReport buildReport(MesWorkOrderTask task, MesReportRequest request) {
        MesProductionReport report = new MesProductionReport();
        report.setReportNo(MesCodeGenerator.nextNo("RPT"));
        report.setTaskId(task.getId());
        report.setTaskNo(task.getTaskNo());
        report.setWorkOrderId(task.getWorkOrderId());
        report.setWorkOrderCode(task.getWorkOrderCode());
        report.setOperationCode(task.getOperationCode());
        report.setStationId(request.getStationId());
        report.setStationCode(stationCode(request.getStationId()));
        report.setReportQuantity(request.getReportQuantity());
        report.setQualifiedQuantity(request.getQualifiedQuantity());
        report.setDefectQuantity(request.getDefectQuantity());
        report.setStatus(REPORT_SUBMITTED);
        report.setReporter(request.getReporter());
        report.setReportedAt(LocalDateTime.now());
        report.setRemark(request.getRemark());
        return report;
    }

    /**
     * 写入不良明细
     *
     * @param reportId 报工 ID
     * @param defects  不良明细
     */
    private void insertDefects(Long reportId, List<MesReportRequest.DefectItem> defects) {
        if (defects == null) {
            return;
        }
        for (MesReportRequest.DefectItem item : defects) {
            MesReportDefect defect = new MesReportDefect();
            defect.setReportId(reportId);
            defect.setDefectCode(item.getDefectCode());
            defect.setDefectName(StringUtils.hasText(item.getDefectName())
                    ? item.getDefectName() : item.getDefectCode());
            defect.setQuantity(item.getQuantity());
            reportDefectMapper.insert(defect);
        }
    }

    /**
     * 查询工位编码快照
     *
     * @param stationId 工位 ID
     * @return 工位编码，不存在时返回 {@code null}
     */
    private String stationCode(Long stationId) {
        if (stationId == null) {
            return null;
        }
        MesStation station = stationMapper.selectById(stationId);
        return station == null ? null : station.getCode();
    }

    /**
     * 重算并保存任务在制品快照
     *
     * @param task 任务
     */
    private void updateWip(MesWorkOrderTask task) {
        LambdaQueryWrapper<MesWipSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWipSnapshot::getTaskId, task.getId()).last("LIMIT 1");
        MesWipSnapshot snapshot = wipMapper.selectOne(wrapper);
        if (snapshot == null) {
            snapshot = new MesWipSnapshot();
            snapshot.setWorkOrderId(task.getWorkOrderId());
            snapshot.setTaskId(task.getId());
            snapshot.setTaskNo(task.getTaskNo());
            snapshot.setOperationCode(task.getOperationCode());
        }
        BigDecimal dispatched = zeroIfNull(task.getDispatchedQuantity());
        BigDecimal completed = zeroIfNull(task.getCompletedQuantity());
        BigDecimal scrapped = zeroIfNull(task.getScrappedQuantity());
        BigDecimal wip = dispatched.subtract(completed).subtract(scrapped);
        if (wip.compareTo(BigDecimal.ZERO) < 0) {
            wip = BigDecimal.ZERO;
        }
        snapshot.setDispatchedQuantity(dispatched);
        snapshot.setCompletedQuantity(completed);
        snapshot.setScrappedQuantity(scrapped);
        snapshot.setWipQuantity(wip);
        snapshot.setSnapshotTime(LocalDateTime.now());
        if (snapshot.getId() == null) {
            wipMapper.insert(snapshot);
        } else {
            wipMapper.updateById(snapshot);
        }
    }

    /**
     * 构建异常事件
     *
     * @param workOrderId 工单 ID
     * @param request     异常请求
     * @return 异常实体
     */
    private MesExceptionEvent buildException(Long workOrderId, MesExceptionCreateRequest request) {
        MesExceptionEvent event = new MesExceptionEvent();
        event.setExceptionNo(MesCodeGenerator.nextNo("EXC"));
        event.setWorkOrderId(workOrderId);
        event.setTaskId(request.getTaskId());
        event.setExceptionType(request.getExceptionType());
        event.setReasonCode(request.getReasonCode());
        event.setReasonName(reasonName(request.getReasonCode()));
        event.setDescription(request.getDescription());
        event.setStatus(EXCEPTION_OPEN);
        event.setOccurredAt(LocalDateTime.now());
        event.setEscalationLevel(0);
        event.setRemark(request.getRemark());
        return event;
    }

    /**
     * 查询原因名称快照
     *
     * @param reasonCode 原因编码
     * @return 原因名称
     */
    private String reasonName(String reasonCode) {
        if (!StringUtils.hasText(reasonCode)) {
            return null;
        }
        LambdaQueryWrapper<MesReasonCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesReasonCode::getCode, reasonCode).last("LIMIT 1");
        MesReasonCode reason = reasonCodeMapper.selectOne(wrapper);
        return reason == null ? reasonCode : reason.getName();
    }

    /**
     * 执行异常状态迁移
     *
     * @param event   异常事件
     * @param request 处理请求
     * @return 目标状态
     */
    private String transitionException(MesExceptionEvent event, MesExceptionActionRequest request) {
        String action = request.getActionType();
        String current = event.getStatus();
        if ("ACKNOWLEDGE".equals(action)) {
            if (!EXCEPTION_OPEN.equals(current)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "仅待处理异常可以确认");
            }
            event.setStatus(EXCEPTION_ACKNOWLEDGED);
            return EXCEPTION_ACKNOWLEDGED;
        }
        if ("RESOLVE".equals(action)) {
            if (!EXCEPTION_OPEN.equals(current) && !EXCEPTION_ACKNOWLEDGED.equals(current)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "当前异常状态不允许解决");
            }
            if (!StringUtils.hasText(request.getResolution()) && !StringUtils.hasText(event.getResolution())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "解决异常必须填写处理结论");
            }
            event.setStatus(EXCEPTION_RESOLVED);
            return EXCEPTION_RESOLVED;
        }
        if (!EXCEPTION_RESOLVED.equals(current)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅已解决异常可以关闭");
        }
        if (!StringUtils.hasText(request.getResolution()) && !StringUtils.hasText(event.getResolution())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "关闭异常必须填写处理结论");
        }
        event.setStatus(EXCEPTION_CLOSED);
        return EXCEPTION_CLOSED;
    }

    /**
     * 记录异常处理动作
     *
     * @param exceptionId 异常 ID
     * @param actionType  动作
     * @param from        原状态
     * @param to          新状态
     * @param remark      备注
     */
    private void recordAction(Long exceptionId, String actionType, String from, String to, String remark) {
        MesExceptionAction action = new MesExceptionAction();
        action.setExceptionId(exceptionId);
        action.setActionType(actionType);
        action.setFromStatus(from);
        action.setToStatus(to);
        action.setActionTime(LocalDateTime.now());
        action.setRemark(remark);
        exceptionActionMapper.insert(action);
    }

    /**
     * 校验任务归属工单
     *
     * @param taskId      任务 ID
     * @param workOrderId 工单 ID
     */
    private void verifyTaskBelongsToOrder(Long taskId, Long workOrderId) {
        if (taskId == null) {
            return;
        }
        MesWorkOrderTask task = requireTask(taskId);
        if (!task.getWorkOrderId().equals(workOrderId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务不属于当前工单");
        }
    }

    /**
     * 加载任务，不存在时抛 404
     *
     * @param taskId 任务 ID
     * @return 任务
     */
    private MesWorkOrderTask requireTask(Long taskId) {
        MesWorkOrderTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在：" + taskId);
        }
        return task;
    }

    /**
     * 加载工单，不存在时抛 404
     *
     * @param workOrderId 工单 ID
     * @return 工单
     */
    private MesWorkOrder requireWorkOrder(Long workOrderId) {
        MesWorkOrder workOrder = workOrderId == null ? null : mesWorkOrderMapper.selectById(workOrderId);
        if (workOrder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在：" + workOrderId);
        }
        return workOrder;
    }

    /**
     * 加载异常，不存在时抛 404
     *
     * @param id 异常 ID
     * @return 异常事件
     */
    private MesExceptionEvent requireException(Long id) {
        MesExceptionEvent event = id == null ? null : exceptionMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "异常单不存在：" + id);
        }
        return event;
    }

    /**
     * 校验任务当前状态
     *
     * @param task    任务
     * @param allowed 允许状态
     * @param action  动作名称
     */
    private void assertStatus(MesWorkOrderTask task, Set<String> allowed, String action) {
        if (!allowed.contains(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "任务当前状态【" + task.getStatus() + "】不允许" + action);
        }
    }

    /**
     * 更新任务（乐观锁冲突时报错）
     *
     * @param task 任务
     */
    private void updateTask(MesWorkOrderTask task) {
        if (taskMapper.updateById(task) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 空值转零
     *
     * @param value 数值
     * @return 非空数值
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 任务实体转视图
     *
     * @param task 任务
     * @return 任务视图
     */
    private MesWorkOrderTaskVO toTaskVo(MesWorkOrderTask task) {
        MesWorkOrderTaskVO vo = new MesWorkOrderTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setWorkOrderId(task.getWorkOrderId());
        vo.setWorkOrderCode(task.getWorkOrderCode());
        vo.setErpOrderNo(task.getErpOrderNo());
        vo.setSeqNo(task.getSeqNo());
        vo.setOperationCode(task.getOperationCode());
        vo.setOperationName(task.getOperationName());
        vo.setWorkCenterId(task.getWorkCenterId());
        vo.setWorkCenterCode(task.getWorkCenterCode());
        vo.setPlannedQuantity(task.getPlannedQuantity());
        vo.setDispatchedQuantity(task.getDispatchedQuantity());
        vo.setReportedQuantity(task.getReportedQuantity());
        vo.setQualifiedQuantity(task.getQualifiedQuantity());
        vo.setDefectQuantity(task.getDefectQuantity());
        vo.setCompletedQuantity(task.getCompletedQuantity());
        vo.setScrappedQuantity(task.getScrappedQuantity());
        vo.setStatus(task.getStatus());
        vo.setPriority(task.getPriority());
        vo.setPlannedStart(task.getPlannedStart());
        vo.setPlannedEnd(task.getPlannedEnd());
        vo.setRemark(task.getRemark());
        vo.setVersion(task.getVersion());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    /**
     * 报工实体转视图
     *
     * @param report 报工实体
     * @return 报工视图
     */
    private MesProductionReportVO toReportVo(MesProductionReport report) {
        MesProductionReportVO vo = new MesProductionReportVO();
        vo.setId(report.getId());
        vo.setReportNo(report.getReportNo());
        vo.setTaskId(report.getTaskId());
        vo.setTaskNo(report.getTaskNo());
        vo.setWorkOrderId(report.getWorkOrderId());
        vo.setWorkOrderCode(report.getWorkOrderCode());
        vo.setOperationCode(report.getOperationCode());
        vo.setStationId(report.getStationId());
        vo.setStationCode(report.getStationCode());
        vo.setReportQuantity(report.getReportQuantity());
        vo.setQualifiedQuantity(report.getQualifiedQuantity());
        vo.setDefectQuantity(report.getDefectQuantity());
        vo.setStatus(report.getStatus());
        vo.setReporter(report.getReporter());
        vo.setReportedAt(report.getReportedAt());
        vo.setRemark(report.getRemark());
        return vo;
    }

    /**
     * 在制品实体转视图
     *
     * @param snapshot 在制品快照
     * @return 在制品视图
     */
    private MesWipVO toWipVo(MesWipSnapshot snapshot) {
        MesWipVO vo = new MesWipVO();
        vo.setTaskId(snapshot.getTaskId());
        vo.setTaskNo(snapshot.getTaskNo());
        vo.setWorkOrderId(snapshot.getWorkOrderId());
        vo.setOperationCode(snapshot.getOperationCode());
        vo.setDispatchedQuantity(snapshot.getDispatchedQuantity());
        vo.setCompletedQuantity(snapshot.getCompletedQuantity());
        vo.setScrappedQuantity(snapshot.getScrappedQuantity());
        vo.setWipQuantity(snapshot.getWipQuantity());
        vo.setSnapshotTime(snapshot.getSnapshotTime());
        return vo;
    }

    /**
     * 异常实体转视图
     *
     * @param event 异常事件
     * @return 异常视图
     */
    private MesExceptionVO toExceptionVo(MesExceptionEvent event) {
        MesExceptionVO vo = new MesExceptionVO();
        vo.setId(event.getId());
        vo.setExceptionNo(event.getExceptionNo());
        vo.setWorkOrderId(event.getWorkOrderId());
        vo.setTaskId(event.getTaskId());
        vo.setExceptionType(event.getExceptionType());
        vo.setReasonCode(event.getReasonCode());
        vo.setReasonName(event.getReasonName());
        vo.setDescription(event.getDescription());
        vo.setStatus(event.getStatus());
        vo.setReportedBy(event.getReportedBy());
        vo.setOccurredAt(event.getOccurredAt());
        vo.setResolvedAt(event.getResolvedAt());
        vo.setResolution(event.getResolution());
        vo.setEscalationLevel(event.getEscalationLevel());
        return vo;
    }
}

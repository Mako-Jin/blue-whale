package com.bluewhale.mes.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.converter.MesConverter;
import com.bluewhale.mes.dispatch.dto.MesDispatchRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskQueryRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskSplitRequest;
import com.bluewhale.mes.dispatch.dto.MesWorkOrderReceiveRequest;
import com.bluewhale.mes.dispatch.entity.MesDispatchLine;
import com.bluewhale.mes.dispatch.entity.MesDispatchOrder;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesDispatchLineMapper;
import com.bluewhale.mes.dispatch.mapper.MesDispatchOrderMapper;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.dispatch.service.MesDispatchService;
import com.bluewhale.mes.dispatch.service.MesTaskProgressService;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderProgressVO;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderTaskVO;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.execution.entity.MesExceptionEvent;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.mapper.MesExceptionEventMapper;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.bluewhale.mes.masterdata.entity.MesProductionLine;
import com.bluewhale.mes.masterdata.entity.MesRoutingOperation;
import com.bluewhale.mes.masterdata.entity.MesRoutingSnapshot;
import com.bluewhale.mes.masterdata.entity.MesTeam;
import com.bluewhale.mes.masterdata.entity.MesWorkCenter;
import com.bluewhale.mes.masterdata.mapper.MesProductionLineMapper;
import com.bluewhale.mes.masterdata.mapper.MesRoutingOperationMapper;
import com.bluewhale.mes.masterdata.mapper.MesRoutingSnapshotMapper;
import com.bluewhale.mes.masterdata.mapper.MesTeamMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import com.bluewhale.mes.vo.MesWorkOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * MES 调度派工应用服务实现
 *
 * <p>职责：ERP 工单接收（幂等）、工单拆分（工序任务）、批量派工/撤回、任务分页与生产看板。
 * 与执行上下文通过工单进度回流服务解耦，不直接写报工数据。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesDispatchServiceImpl implements MesDispatchService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 工单状态：已接收 */
    private static final String ORDER_RECEIVED = "RECEIVED";

    /** 工单状态：已派工 */
    private static final String ORDER_DISPATCHED = "DISPATCHED";

    /** 工单状态：已取消 */
    private static final String ORDER_CANCELLED = "CANCELLED";

    /** 工单状态：已关闭 */
    private static final String ORDER_CLOSED = "CLOSED";

    /** 任务状态：待派工 */
    private static final String TASK_PLANNED = "PLANNED";

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

    /** 启用状态 */
    private static final String STATUS_ENABLED = "ENABLED";

    /** 派工单状态：已派工 */
    private static final String DISPATCH_DISPATCHED = "DISPATCHED";

    /** 派工单状态：已撤回 */
    private static final String DISPATCH_WITHDRAWN = "WITHDRAWN";

    /** 优先级：普通 */
    private static final String PRIORITY_NORMAL = "NORMAL";

    private final MesWorkOrderMapper mesWorkOrderMapper;

    private final MesWorkOrderTaskMapper taskMapper;

    private final MesDispatchOrderMapper dispatchOrderMapper;

    private final MesDispatchLineMapper dispatchLineMapper;

    private final MesProductionLineMapper lineMapper;

    private final MesTeamMapper teamMapper;

    private final MesWorkCenterMapper workCenterMapper;

    private final MesRoutingSnapshotMapper routingSnapshotMapper;

    private final MesRoutingOperationMapper routingOperationMapper;

    private final MesProductionReportMapper reportMapper;

    private final MesExceptionEventMapper exceptionMapper;

    private final MesTaskProgressService progressService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderVO receive(MesWorkOrderReceiveRequest request) {
        String erpOrderNo = request.getErpOrderNo().trim();
        LambdaQueryWrapper<MesWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWorkOrder::getErpOrderNo, erpOrderNo).last("LIMIT 1");
        MesWorkOrder existing = mesWorkOrderMapper.selectOne(wrapper);
        if (existing != null) {
            return updateReceived(existing, request);
        }
        MesWorkOrder workOrder = new MesWorkOrder();
        workOrder.setCode(StringUtils.hasText(request.getCode())
                ? request.getCode().trim() : ("MES-" + erpOrderNo));
        workOrder.setName(StringUtils.hasText(request.getName())
                ? request.getName().trim() : defaultName(request.getItemName(), workOrder.getCode()));
        workOrder.setStatus(ORDER_RECEIVED);
        workOrder.setPriority(normalizePriority(request.getPriority()));
        workOrder.setVersion(1);
        applyReceiveFields(workOrder, request);
        validatePlanWindow(workOrder.getPlannedStart(), workOrder.getPlannedEnd());
        mesWorkOrderMapper.insert(workOrder);
        return MesConverter.INSTANCE.toWorkOrderVo(workOrder);
    }

    @Override
    public MesWorkOrderProgressVO progress(String erpOrderNo) {
        MesWorkOrder workOrder = mesWorkOrderMapper.selectOne(new LambdaQueryWrapper<MesWorkOrder>()
                .eq(MesWorkOrder::getErpOrderNo, erpOrderNo)
                .last("LIMIT 1"));
        if (workOrder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MES 工单不存在: " + erpOrderNo);
        }
        List<MesWorkOrderTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<MesWorkOrderTask>()
                .eq(MesWorkOrderTask::getWorkOrderId, workOrder.getId())
                .orderByAsc(MesWorkOrderTask::getSeqNo));
        MesWorkOrderProgressVO vo = new MesWorkOrderProgressVO();
        vo.setErpOrderNo(workOrder.getErpOrderNo());
        vo.setWorkOrderId(workOrder.getId());
        vo.setWorkOrderCode(workOrder.getCode());
        vo.setStatus(workOrder.getStatus());
        vo.setPlannedQuantity(workOrder.getPlannedQuantity());
        BigDecimal reported = BigDecimal.ZERO;
        BigDecimal qualified = BigDecimal.ZERO;
        BigDecimal completed = BigDecimal.ZERO;
        List<MesWorkOrderProgressVO.Task> taskViews = new ArrayList<>();
        for (MesWorkOrderTask task : tasks) {
            reported = reported.add(task.getReportedQuantity() == null ? BigDecimal.ZERO : task.getReportedQuantity());
            qualified = qualified.add(task.getQualifiedQuantity() == null ? BigDecimal.ZERO : task.getQualifiedQuantity());
            completed = completed.add(task.getCompletedQuantity() == null ? BigDecimal.ZERO : task.getCompletedQuantity());
            MesWorkOrderProgressVO.Task view = new MesWorkOrderProgressVO.Task();
            view.setTaskNo(task.getTaskNo());
            view.setSeqNo(task.getSeqNo());
            view.setOperationCode(task.getOperationCode());
            view.setPlannedQuantity(task.getPlannedQuantity());
            view.setReportedQuantity(task.getReportedQuantity());
            view.setQualifiedQuantity(task.getQualifiedQuantity());
            view.setCompletedQuantity(task.getCompletedQuantity());
            view.setStatus(task.getStatus());
            taskViews.add(view);
        }
        vo.setReportedQuantity(reported);
        vo.setQualifiedQuantity(qualified);
        vo.setCompletedQuantity(completed);
        vo.setTasks(taskViews);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MesWorkOrderTaskVO> split(Long workOrderId, MesTaskSplitRequest request) {
        MesWorkOrder workOrder = requireWorkOrder(workOrderId);
        if (ORDER_CANCELLED.equals(workOrder.getStatus()) || ORDER_CLOSED.equals(workOrder.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工单已取消或关闭，不允许拆分");
        }
        if (countTasks(workOrderId) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工单已拆分，请勿重复操作");
        }
        List<MesTaskSplitRequest.Item> items = request.getItems() == null || request.getItems().isEmpty()
                ? deriveItems(workOrder) : request.getItems();
        validateSplitItems(items, workOrder);
        List<MesWorkOrderTaskVO> result = new ArrayList<>(items.size());
        for (MesTaskSplitRequest.Item item : items) {
            MesWorkOrderTask task = buildTask(workOrder, item);
            taskMapper.insert(task);
            result.add(toTaskVo(task));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String dispatch(MesDispatchRequest request) {
        List<MesWorkOrderTask> tasks = loadDispatchTasks(request.getTaskIds());
        Long workOrderId = tasks.get(0).getWorkOrderId();
        if (tasks.stream().anyMatch(task -> !Objects.equals(workOrderId, task.getWorkOrderId()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "一次派工仅允许同一工单的任务");
        }
        for (MesWorkOrderTask task : tasks) {
            if (!TASK_PLANNED.equals(task.getStatus())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "任务" + task.getTaskNo() + "不是待派工状态");
            }
        }
        MesProductionLine line = requireLine(request.getLineId());
        MesTeam team = request.getTeamId() == null ? null : requireTeam(request.getTeamId());
        MesDispatchOrder order = buildDispatchOrder(tasks, line, team, request.getRemark());
        dispatchOrderMapper.insert(order);
        insertDispatchLines(order, tasks);
        updateTasksDispatched(tasks);
        markWorkOrderDispatched(workOrderId);
        return order.getDispatchNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long dispatchOrderId) {
        MesDispatchOrder order = dispatchOrderId == null ? null : dispatchOrderMapper.selectById(dispatchOrderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "派工单不存在：" + dispatchOrderId);
        }
        if (!DISPATCH_DISPATCHED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "派工单当前状态不允许撤回");
        }
        LambdaQueryWrapper<MesDispatchLine> lineWrapper = new LambdaQueryWrapper<>();
        lineWrapper.eq(MesDispatchLine::getDispatchOrderId, order.getId());
        List<MesDispatchLine> lines = dispatchLineMapper.selectList(lineWrapper);
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "派工单无明细，无法撤回");
        }
        for (MesDispatchLine line : lines) {
            withdrawTask(line);
            line.setStatus(DISPATCH_WITHDRAWN);
            dispatchLineMapper.updateById(line);
        }
        order.setStatus(DISPATCH_WITHDRAWN);
        order.setWithdrawnAt(LocalDateTime.now());
        dispatchOrderMapper.updateById(order);
        progressService.refreshWorkOrder(order.getWorkOrderId());
    }

    @Override
    public PageResult<MesWorkOrderTaskVO> pageTasks(MesTaskQueryRequest request) {
        LambdaQueryWrapper<MesWorkOrderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getTaskNo()), MesWorkOrderTask::getTaskNo, request.getTaskNo());
        wrapper.eq(request.getWorkOrderId() != null, MesWorkOrderTask::getWorkOrderId, request.getWorkOrderId());
        wrapper.like(StringUtils.hasText(request.getWorkOrderCode()),
                MesWorkOrderTask::getWorkOrderCode, request.getWorkOrderCode());
        wrapper.like(StringUtils.hasText(request.getOperationCode()),
                MesWorkOrderTask::getOperationCode, request.getOperationCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesWorkOrderTask::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getPriority()), MesWorkOrderTask::getPriority, request.getPriority());
        wrapper.orderByDesc(MesWorkOrderTask::getCreateTime).orderByAsc(MesWorkOrderTask::getSeqNo);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MesWorkOrderTask> page = taskMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<MesWorkOrderTaskVO> records = page.getRecords().stream().map(this::toTaskVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MesBoardVO board(Long lineId, Long teamId) {
        LocalDate boardDate = LocalDate.now();
        List<Long> scopedTaskIds = scopedTaskIds(lineId, teamId);
        List<MesWorkOrderTask> tasks = loadBoardTasks(scopedTaskIds);
        LocalDateTime dayStart = boardDate.atStartOfDay();
        LocalDateTime dayEnd = boardDate.plusDays(1).atStartOfDay();
        MesBoardVO board = new MesBoardVO();
        board.setBoardDate(boardDate);
        board.setTargetQuantity(sumTarget(tasks, dayStart, dayEnd));
        board.setCompletedQuantity(sumReports(scopedTaskIds, dayStart, dayEnd, true));
        board.setDefectQuantity(sumReports(scopedTaskIds, dayStart, dayEnd, false));
        board.setAchievementRate(achievementRate(board.getCompletedQuantity(), board.getTargetQuantity()));
        board.setExceptionCount(countOpenExceptions(scopedTaskIds));
        board.setActiveTaskCount(tasks.stream()
                .filter(task -> TASK_IN_PROGRESS.equals(task.getStatus()) || TASK_PAUSED.equals(task.getStatus()))
                .count());
        board.setActiveTasks(tasks.stream()
                .filter(task -> TASK_IN_PROGRESS.equals(task.getStatus()) || TASK_PAUSED.equals(task.getStatus()))
                .limit(20)
                .map(this::toTaskVo)
                .toList());
        return board;
    }

    /**
     * 幂等更新已存在的 ERP 工单
     *
     * @param workOrder 已存在工单
     * @param request   下发请求
     * @return 工单视图
     */
    private MesWorkOrderVO updateReceived(MesWorkOrder workOrder, MesWorkOrderReceiveRequest request) {
        if (!ORDER_RECEIVED.equals(workOrder.getStatus())) {
            if (workOrder.getPlannedQuantity() != null
                    && workOrder.getPlannedQuantity().compareTo(request.getPlannedQuantity()) != 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工单已派工，不允许修改计划数量");
            }
            return MesConverter.INSTANCE.toWorkOrderVo(workOrder);
        }
        applyReceiveFields(workOrder, request);
        validatePlanWindow(workOrder.getPlannedStart(), workOrder.getPlannedEnd());
        mesWorkOrderMapper.updateById(workOrder);
        return MesConverter.INSTANCE.toWorkOrderVo(workOrder);
    }

    /**
     * 写入 ERP 下发字段
     *
     * @param workOrder 工单
     * @param request   下发请求
     */
    private void applyReceiveFields(MesWorkOrder workOrder, MesWorkOrderReceiveRequest request) {
        workOrder.setErpOrderNo(request.getErpOrderNo().trim());
        workOrder.setItemCode(request.getItemCode());
        workOrder.setItemName(request.getItemName());
        workOrder.setPlannedQuantity(request.getPlannedQuantity());
        workOrder.setUom(request.getUom());
        workOrder.setRoutingVersion(request.getRoutingVersion());
        workOrder.setPriority(normalizePriority(request.getPriority()));
        workOrder.setPlannedStart(request.getPlannedStart());
        workOrder.setPlannedEnd(request.getPlannedEnd());
        workOrder.setRemark(request.getRemark());
    }

    /**
     * 校验计划时间窗口
     *
     * @param plannedStart 计划开工时间
     * @param plannedEnd   计划完工时间
     */
    private void validatePlanWindow(LocalDateTime plannedStart, LocalDateTime plannedEnd) {
        if (plannedStart != null && plannedEnd != null && plannedEnd.isBefore(plannedStart)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "计划完工时间不能早于计划开工时间");
        }
    }

    /**
     * 默认工单名称
     *
     * @param itemName 物料名称
     * @param code     工单编码
     * @return 名称
     */
    private String defaultName(String itemName, String code) {
        return StringUtils.hasText(itemName) ? itemName : code;
    }

    /**
     * 归一化优先级
     *
     * @param priority 优先级
     * @return NORMAL 或 URGENT
     */
    private String normalizePriority(String priority) {
        return "URGENT".equalsIgnoreCase(priority) ? "URGENT" : PRIORITY_NORMAL;
    }

    /**
     * 按物料查找已发布工艺路线并生成拆分项
     *
     * @param workOrder 工单
     * @return 拆分项
     */
    private List<MesTaskSplitRequest.Item> deriveItems(MesWorkOrder workOrder) {
        MesRoutingSnapshot snapshot = findReleasedRouting(workOrder);
        LambdaQueryWrapper<MesRoutingOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesRoutingOperation::getRoutingId, snapshot.getId());
        wrapper.orderByAsc(MesRoutingOperation::getSeqNo);
        List<MesRoutingOperation> operations = routingOperationMapper.selectList(wrapper);
        if (operations.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工艺路线无工序，请手工拆分");
        }
        List<MesTaskSplitRequest.Item> items = new ArrayList<>(operations.size());
        for (MesRoutingOperation operation : operations) {
            MesTaskSplitRequest.Item item = new MesTaskSplitRequest.Item();
            item.setSeqNo(operation.getSeqNo());
            item.setOperationCode(operation.getOperationCode());
            item.setOperationName(operation.getOperationName());
            item.setWorkCenterCode(operation.getWorkCenterCode());
            item.setPlannedQuantity(workOrder.getPlannedQuantity());
            item.setPlannedStart(workOrder.getPlannedStart());
            item.setPlannedEnd(workOrder.getPlannedEnd());
            items.add(item);
        }
        return items;
    }

    /**
     * 查找物料已发布工艺路线（优先匹配工单携带版本）
     *
     * @param workOrder 工单
     * @return 工艺路线快照
     */
    private MesRoutingSnapshot findReleasedRouting(MesWorkOrder workOrder) {
        LambdaQueryWrapper<MesRoutingSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesRoutingSnapshot::getItemCode, workOrder.getItemCode());
        wrapper.eq(MesRoutingSnapshot::getStatus, "RELEASED");
        if (StringUtils.hasText(workOrder.getRoutingVersion())) {
            wrapper.eq(MesRoutingSnapshot::getVersion, workOrder.getRoutingVersion());
        }
        wrapper.orderByDesc(MesRoutingSnapshot::getId);
        List<MesRoutingSnapshot> snapshots = routingSnapshotMapper.selectList(wrapper);
        if (snapshots.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "未找到物料【" + workOrder.getItemCode() + "】的已发布工艺路线，请手工拆分");
        }
        return snapshots.get(0);
    }

    /**
     * 校验拆分项：工序顺序唯一、每道工序分量之和等于工单计划量
     *
     * @param items     拆分项
     * @param workOrder 工单
     */
    private void validateSplitItems(List<MesTaskSplitRequest.Item> items, MesWorkOrder workOrder) {
        Set<Integer> seqNos = new LinkedHashSet<>();
        Map<Integer, BigDecimal> quantityBySeq = new java.util.TreeMap<>();
        for (MesTaskSplitRequest.Item item : items) {
            if (!seqNos.add(item.getSeqNo())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工序顺序重复：" + item.getSeqNo());
            }
            quantityBySeq.merge(item.getSeqNo(), item.getPlannedQuantity(), BigDecimal::add);
        }
        BigDecimal planned = workOrder.getPlannedQuantity() == null ? BigDecimal.ZERO : workOrder.getPlannedQuantity();
        quantityBySeq.forEach((seqNo, quantity) -> {
            if (quantity.compareTo(planned) != 0) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "工序顺序 " + seqNo + " 的计划数量合计应等于工单计划数量 " + planned);
            }
        });
    }

    /**
     * 构建任务实体
     *
     * @param workOrder 工单
     * @param item      拆分项
     * @return 任务实体
     */
    private MesWorkOrderTask buildTask(MesWorkOrder workOrder, MesTaskSplitRequest.Item item) {
        MesWorkOrderTask task = new MesWorkOrderTask();
        task.setTaskNo(MesCodeGenerator.nextNo("TASK"));
        task.setWorkOrderId(workOrder.getId());
        task.setWorkOrderCode(workOrder.getCode());
        task.setErpOrderNo(workOrder.getErpOrderNo());
        task.setSeqNo(item.getSeqNo());
        task.setOperationCode(item.getOperationCode());
        task.setOperationName(StringUtils.hasText(item.getOperationName())
                ? item.getOperationName() : item.getOperationCode());
        applyWorkCenter(task, item.getWorkCenterCode());
        task.setPlannedQuantity(item.getPlannedQuantity());
        task.setDispatchedQuantity(BigDecimal.ZERO);
        task.setReportedQuantity(BigDecimal.ZERO);
        task.setQualifiedQuantity(BigDecimal.ZERO);
        task.setDefectQuantity(BigDecimal.ZERO);
        task.setCompletedQuantity(BigDecimal.ZERO);
        task.setScrappedQuantity(BigDecimal.ZERO);
        task.setStatus(TASK_PLANNED);
        task.setPriority(workOrder.getPriority() == null ? PRIORITY_NORMAL : workOrder.getPriority());
        task.setPlannedStart(item.getPlannedStart());
        task.setPlannedEnd(item.getPlannedEnd());
        task.setVersion(1);
        return task;
    }

    /**
     * 填充任务工作中心（编码为空时留空）
     *
     * @param task           任务
     * @param workCenterCode 工作中心编码
     */
    private void applyWorkCenter(MesWorkOrderTask task, String workCenterCode) {
        if (!StringUtils.hasText(workCenterCode)) {
            return;
        }
        LambdaQueryWrapper<MesWorkCenter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWorkCenter::getCode, workCenterCode);
        wrapper.eq(MesWorkCenter::getStatus, STATUS_ENABLED).last("LIMIT 1");
        MesWorkCenter workCenter = workCenterMapper.selectOne(wrapper);
        if (workCenter == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作中心不存在或已停用：" + workCenterCode);
        }
        task.setWorkCenterId(workCenter.getId());
        task.setWorkCenterCode(workCenter.getCode());
    }

    /**
     * 统计工单已有任务数
     *
     * @param workOrderId 工单 ID
     * @return 任务数
     */
    private long countTasks(Long workOrderId) {
        LambdaQueryWrapper<MesWorkOrderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWorkOrderTask::getWorkOrderId, workOrderId);
        return taskMapper.selectCount(wrapper);
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
     * 加载并校验派工任务
     *
     * @param taskIds 任务 ID 列表
     * @return 任务列表
     */
    private List<MesWorkOrderTask> loadDispatchTasks(List<Long> taskIds) {
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(taskIds));
        List<MesWorkOrderTask> tasks = taskMapper.selectBatchIds(distinctIds);
        if (tasks.size() != distinctIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "部分派工任务不存在");
        }
        return tasks;
    }

    /**
     * 加载并校验产线
     *
     * @param lineId 产线 ID
     * @return 产线
     */
    private MesProductionLine requireLine(Long lineId) {
        MesProductionLine line = lineId == null ? null : lineMapper.selectById(lineId);
        if (line == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "产线不存在：" + lineId);
        }
        if (!STATUS_ENABLED.equals(line.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "产线已停用：" + line.getCode());
        }
        return line;
    }

    /**
     * 加载并校验班组
     *
     * @param teamId 班组 ID
     * @return 班组
     */
    private MesTeam requireTeam(Long teamId) {
        MesTeam team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "班组不存在：" + teamId);
        }
        if (!STATUS_ENABLED.equals(team.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "班组已停用：" + team.getCode());
        }
        return team;
    }

    /**
     * 构建派工单
     *
     * @param tasks 任务列表
     * @param line  产线
     * @param team  班组，可为空
     * @param remark 备注
     * @return 派工单
     */
    private MesDispatchOrder buildDispatchOrder(List<MesWorkOrderTask> tasks, MesProductionLine line,
                                                MesTeam team, String remark) {
        MesDispatchOrder order = new MesDispatchOrder();
        order.setDispatchNo(MesCodeGenerator.nextNo("DSP"));
        order.setWorkOrderId(tasks.get(0).getWorkOrderId());
        order.setWorkOrderCode(tasks.get(0).getWorkOrderCode());
        order.setLineId(line.getId());
        order.setLineCode(line.getCode());
        if (team != null) {
            order.setTeamId(team.getId());
            order.setTeamCode(team.getCode());
        }
        order.setTaskCount(tasks.size());
        order.setDispatchQuantity(tasks.stream()
                .map(this::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        order.setStatus(DISPATCH_DISPATCHED);
        order.setDispatchedAt(LocalDateTime.now());
        order.setRemark(remark);
        return order;
    }

    /**
     * 计算任务待派工数量
     *
     * @param task 任务
     * @return 待派工数量
     */
    private BigDecimal remainingQuantity(MesWorkOrderTask task) {
        BigDecimal planned = task.getPlannedQuantity() == null ? BigDecimal.ZERO : task.getPlannedQuantity();
        BigDecimal dispatched = task.getDispatchedQuantity() == null ? BigDecimal.ZERO : task.getDispatchedQuantity();
        BigDecimal remaining = planned.subtract(dispatched);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务" + task.getTaskNo() + "已全部派工");
        }
        return remaining;
    }

    /**
     * 写入派工明细
     *
     * @param order 派工单
     * @param tasks 任务列表
     */
    private void insertDispatchLines(MesDispatchOrder order, List<MesWorkOrderTask> tasks) {
        for (MesWorkOrderTask task : tasks) {
            MesDispatchLine line = new MesDispatchLine();
            line.setDispatchOrderId(order.getId());
            line.setTaskId(task.getId());
            line.setTaskNo(task.getTaskNo());
            line.setOperationCode(task.getOperationCode());
            line.setDispatchQuantity(remainingQuantity(task));
            line.setStatus(DISPATCH_DISPATCHED);
            dispatchLineMapper.insert(line);
        }
    }

    /**
     * 更新任务为已派工
     *
     * @param tasks 任务列表
     */
    private void updateTasksDispatched(List<MesWorkOrderTask> tasks) {
        for (MesWorkOrderTask task : tasks) {
            task.setDispatchedQuantity(task.getPlannedQuantity());
            task.setStatus(TASK_DISPATCHED);
            if (taskMapper.updateById(task) == 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "任务已被其他用户修改，请刷新后重试");
            }
        }
    }

    /**
     * 标记工单为已派工
     *
     * @param workOrderId 工单 ID
     */
    private void markWorkOrderDispatched(Long workOrderId) {
        MesWorkOrder workOrder = requireWorkOrder(workOrderId);
        if (ORDER_RECEIVED.equals(workOrder.getStatus())) {
            workOrder.setStatus(ORDER_DISPATCHED);
            if (mesWorkOrderMapper.updateById(workOrder) == 0) {
                log.warn("工单状态更新冲突，忽略: id={}", workOrderId);
            }
        }
    }

    /**
     * 撤回单个任务派工
     *
     * @param line 派工明细
     */
    private void withdrawTask(MesDispatchLine line) {
        MesWorkOrderTask task = line.getTaskId() == null ? null : taskMapper.selectById(line.getTaskId());
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "派工任务不存在：" + line.getTaskNo());
        }
        if (!TASK_DISPATCHED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务" + task.getTaskNo() + "已开工，不允许撤回");
        }
        if (task.getReportedQuantity() != null && task.getReportedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务" + task.getTaskNo() + "已有报工，不允许撤回");
        }
        task.setDispatchedQuantity(BigDecimal.ZERO);
        task.setStatus(TASK_PLANNED);
        if (taskMapper.updateById(task) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 看板任务范围：按产线/班组反查已派工任务
     *
     * @param lineId 产线 ID
     * @param teamId 班组 ID
     * @return 任务 ID 列表；无条件时返回 {@code null}
     */
    private List<Long> scopedTaskIds(Long lineId, Long teamId) {
        if (lineId == null && teamId == null) {
            return null;
        }
        LambdaQueryWrapper<MesDispatchOrder> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(lineId != null, MesDispatchOrder::getLineId, lineId);
        orderWrapper.eq(teamId != null, MesDispatchOrder::getTeamId, teamId);
        orderWrapper.eq(MesDispatchOrder::getStatus, DISPATCH_DISPATCHED);
        List<MesDispatchOrder> orders = dispatchOrderMapper.selectList(orderWrapper);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(MesDispatchOrder::getId).toList();
        LambdaQueryWrapper<MesDispatchLine> lineWrapper = new LambdaQueryWrapper<>();
        lineWrapper.in(MesDispatchLine::getDispatchOrderId, orderIds);
        lineWrapper.eq(MesDispatchLine::getStatus, DISPATCH_DISPATCHED);
        return dispatchLineMapper.selectList(lineWrapper).stream()
                .map(MesDispatchLine::getTaskId).distinct().toList();
    }

    /**
     * 加载看板任务
     *
     * @param taskIds 任务范围；{@code null} 表示全部
     * @return 任务列表
     */
    private List<MesWorkOrderTask> loadBoardTasks(List<Long> taskIds) {
        if (taskIds != null && taskIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<MesWorkOrderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(taskIds != null, MesWorkOrderTask::getId, taskIds);
        return taskMapper.selectList(wrapper);
    }

    /**
     * 计算今日目标数量：计划开工时间在今日或未设置的待执行任务
     *
     * @param tasks    任务
     * @param dayStart 日起点
     * @param dayEnd   日终点
     * @return 目标数量
     */
    private BigDecimal sumTarget(List<MesWorkOrderTask> tasks, LocalDateTime dayStart, LocalDateTime dayEnd) {
        return tasks.stream()
                .filter(task -> !TASK_COMPLETED.equals(task.getStatus()) && !TASK_SCRAPPED.equals(task.getStatus()))
                .filter(task -> task.getPlannedStart() == null
                        || (!task.getPlannedStart().isBefore(dayStart) && task.getPlannedStart().isBefore(dayEnd)))
                .map(task -> task.getPlannedQuantity() == null ? BigDecimal.ZERO : task.getPlannedQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 汇总当日报工数量
     *
     * @param taskIds     任务范围；{@code null} 表示全部
     * @param dayStart    日起点
     * @param dayEnd      日终点
     * @param qualified   是否统计合格数，否则统计不良数
     * @return 数量
     */
    private BigDecimal sumReports(List<Long> taskIds, LocalDateTime dayStart, LocalDateTime dayEnd, boolean qualified) {
        if (taskIds != null && taskIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        LambdaQueryWrapper<MesProductionReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(MesProductionReport::getReportedAt, dayStart);
        wrapper.lt(MesProductionReport::getReportedAt, dayEnd);
        wrapper.in(taskIds != null, MesProductionReport::getTaskId, taskIds);
        return reportMapper.selectList(wrapper).stream()
                .map(report -> qualified
                        ? (report.getQualifiedQuantity() == null ? BigDecimal.ZERO : report.getQualifiedQuantity())
                        : (report.getDefectQuantity() == null ? BigDecimal.ZERO : report.getDefectQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 统计未关闭异常数
     *
     * @param taskIds 任务范围；{@code null} 表示全部
     * @return 异常数
     */
    private Long countOpenExceptions(List<Long> taskIds) {
        if (taskIds != null && taskIds.isEmpty()) {
            return 0L;
        }
        LambdaQueryWrapper<MesExceptionEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(MesExceptionEvent::getStatus, "CLOSED");
        wrapper.in(taskIds != null, MesExceptionEvent::getTaskId, taskIds);
        return exceptionMapper.selectCount(wrapper);
    }

    /**
     * 计算达成率
     *
     * @param completed 完成数量
     * @param target    目标数量
     * @return 百分比数值
     */
    private BigDecimal achievementRate(BigDecimal completed, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return completed.multiply(BigDecimal.valueOf(100))
                .divide(target, 2, RoundingMode.HALF_UP);
    }

    /**
     * 任务实体转视图
     *
     * @param task 任务
     * @return 视图
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
}

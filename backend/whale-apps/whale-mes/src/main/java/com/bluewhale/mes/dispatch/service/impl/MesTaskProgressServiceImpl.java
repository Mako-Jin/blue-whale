package com.bluewhale.mes.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mes.common.outbox.service.MesOutboxService;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.dispatch.service.MesTaskProgressService;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单进度回流服务实现
 *
 * <p>汇总口径：任一任务执行中 → 工单 IN_PROGRESS；任一任务已派工 → 工单 DISPATCHED；
 * 全部任务完成/报废 → 工单 COMPLETED 并写发件箱完成回传事件（按工单幂等）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesTaskProgressServiceImpl implements MesTaskProgressService {

    /** 已接收 */
    private static final String ORDER_RECEIVED = "RECEIVED";

    /** 已派工 */
    private static final String ORDER_DISPATCHED = "DISPATCHED";

    /** 生产中 */
    private static final String ORDER_IN_PROGRESS = "IN_PROGRESS";

    /** 已暂停 */
    private static final String ORDER_PAUSED = "PAUSED";

    /** 已完工 */
    private static final String ORDER_COMPLETED = "COMPLETED";

    /** 任务执行中 / 暂停 */
    private static final Set<String> TASK_ACTIVE = Set.of("IN_PROGRESS", "PAUSED");

    /** 任务已派工 */
    private static final String TASK_DISPATCHED = "DISPATCHED";

    /** 任务已完工 */
    private static final String TASK_COMPLETED = "COMPLETED";

    /** 任务已报废 */
    private static final String TASK_SCRAPPED = "SCRAPPED";

    /** 工单允许的状态迁移 */
    private static final Map<String, Set<String>> ORDER_TRANSITIONS = Map.of(
            ORDER_RECEIVED, Set.of(ORDER_DISPATCHED, ORDER_IN_PROGRESS, ORDER_COMPLETED),
            ORDER_DISPATCHED, Set.of(ORDER_IN_PROGRESS, ORDER_COMPLETED),
            ORDER_IN_PROGRESS, Set.of(ORDER_PAUSED, ORDER_COMPLETED),
            ORDER_PAUSED, Set.of(ORDER_IN_PROGRESS, ORDER_COMPLETED),
            ORDER_COMPLETED, Set.of(),
            "CLOSED", Set.of(),
            "CANCELLED", Set.of());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MesWorkOrderTaskMapper taskMapper;

    private final MesWorkOrderMapper mesWorkOrderMapper;

    private final MesOutboxService outboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshWorkOrder(Long workOrderId) {
        MesWorkOrder workOrder = workOrderId == null ? null : mesWorkOrderMapper.selectById(workOrderId);
        if (workOrder == null) {
            return;
        }
        LambdaQueryWrapper<MesWorkOrderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWorkOrderTask::getWorkOrderId, workOrderId);
        List<MesWorkOrderTask> tasks = taskMapper.selectList(wrapper);
        if (tasks.isEmpty()) {
            return;
        }
        String target = deriveOrderStatus(tasks);
        if (target == null || ORDER_TRANSITIONS.getOrDefault(workOrder.getStatus(), Set.of()).contains(target)) {
            applyStatus(workOrder, target);
        }
    }

    /**
     * 从任务集合推导工单目标状态
     *
     * @param tasks 工单任务
     * @return 目标状态，保持原状态时返回 {@code null}
     */
    private String deriveOrderStatus(List<MesWorkOrderTask> tasks) {
        boolean allFinished = tasks.stream()
                .allMatch(task -> TASK_COMPLETED.equals(task.getStatus()) || TASK_SCRAPPED.equals(task.getStatus()));
        if (allFinished) {
            return ORDER_COMPLETED;
        }
        if (tasks.stream().anyMatch(task -> TASK_ACTIVE.contains(task.getStatus()))) {
            return ORDER_IN_PROGRESS;
        }
        if (tasks.stream().anyMatch(task -> TASK_DISPATCHED.equals(task.getStatus()))) {
            return ORDER_DISPATCHED;
        }
        return null;
    }

    /**
     * 更新工单状态并在完工时写入发件箱
     *
     * @param workOrder 工单
     * @param target    目标状态
     */
    private void applyStatus(MesWorkOrder workOrder, String target) {
        if (target == null || target.equals(workOrder.getStatus())) {
            return;
        }
        String from = workOrder.getStatus();
        workOrder.setStatus(target);
        if (mesWorkOrderMapper.updateById(workOrder) == 0) {
            log.warn("工单状态回流冲突，忽略本次更新: id={}, from={}, to={}", workOrder.getId(), from, target);
            return;
        }
        log.info("工单状态回流: id={}, {} -> {}", workOrder.getId(), from, target);
        if (ORDER_COMPLETED.equals(target)) {
            enqueueCompleted(workOrder);
        }
    }

    /**
     * 写入完工回传事件（按工单幂等）
     *
     * @param workOrder 已完工工单
     */
    private void enqueueCompleted(MesWorkOrder workOrder) {
        BigDecimal completed = BigDecimal.ZERO;
        LambdaQueryWrapper<MesWorkOrderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWorkOrderTask::getWorkOrderId, workOrder.getId());
        for (MesWorkOrderTask task : taskMapper.selectList(wrapper)) {
            completed = completed.add(task.getCompletedQuantity() == null
                    ? BigDecimal.ZERO : task.getCompletedQuantity());
        }
        String payload;
        try {
            payload = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "erpOrderNo", workOrder.getErpOrderNo() == null ? "" : workOrder.getErpOrderNo(),
                    "workOrderId", workOrder.getId(),
                    "workOrderCode", workOrder.getCode(),
                    "itemCode", workOrder.getItemCode() == null ? "" : workOrder.getItemCode(),
                    "completedQuantity", completed,
                    "uom", workOrder.getUom() == null ? "" : workOrder.getUom()));
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "完工回传报文序列化失败");
        }
        outboxService.enqueue("mes:work-order-completed:" + workOrder.getId(),
                "WorkOrderCompleted", "mes_work_order", String.valueOf(workOrder.getId()), payload);
    }
}

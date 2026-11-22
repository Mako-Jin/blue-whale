package com.bluewhale.mes.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.dispatch.dto.MesScheduleActionRequest;
import com.bluewhale.mes.dispatch.dto.MesScheduleGenerateRequest;
import com.bluewhale.mes.dispatch.dto.MesScheduleQueryRequest;
import com.bluewhale.mes.dispatch.entity.MesSchedulePlan;
import com.bluewhale.mes.dispatch.entity.MesScheduleSlot;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesSchedulePlanMapper;
import com.bluewhale.mes.dispatch.mapper.MesScheduleSlotMapper;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.dispatch.service.MesSchedulingService;
import com.bluewhale.mes.dispatch.vo.MesScheduleGenerateVO;
import com.bluewhale.mes.dispatch.vo.MesSchedulePlanVO;
import com.bluewhale.mes.dispatch.vo.MesScheduleSlotVO;
import com.bluewhale.mes.masterdata.entity.MesShift;
import com.bluewhale.mes.masterdata.entity.MesWorkCenter;
import com.bluewhale.mes.masterdata.mapper.MesShiftMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MES 有限产能排程服务实现。
 *
 * <p>不做 APS 优化：按工作中心串行分配任务，急单优先、计划完工早者优先。
 * 同一工作中心每天的可用产能被当天全部任务共享，分配满即顺延到次日。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesSchedulingServiceImpl implements MesSchedulingService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 计划状态：草稿 */
    private static final String PLAN_DRAFT = "DRAFT";

    /** 计划状态：已锁定 */
    private static final String PLAN_LOCKED = "LOCKED";

    /** 计划状态：已取消 */
    private static final String PLAN_CANCELLED = "CANCELLED";

    /** 时段状态：已预留 */
    private static final String SLOT_RESERVED = "RESERVED";

    /** 时段状态：已释放 */
    private static final String SLOT_RELEASED = "RELEASED";

    /** 主数据状态：启用 */
    private static final String DATA_ENABLED = "ENABLED";

    /** 可排程任务状态 */
    private static final Set<String> SCHEDULABLE_STATUS = Set.of(
            "PLANNED", "DISPATCHED", "IN_PROGRESS", "PAUSED");

    /** 默认排程天数 */
    private static final int DEFAULT_DAYS = 7;

    private final MesSchedulePlanMapper planMapper;

    private final MesScheduleSlotMapper slotMapper;

    private final MesWorkOrderTaskMapper taskMapper;

    private final MesWorkCenterMapper workCenterMapper;

    private final MesShiftMapper shiftMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesScheduleGenerateVO generate(MesScheduleGenerateRequest request) {
        LocalDate startDate = request.getPlanDate() == null ? LocalDate.now() : request.getPlanDate();
        int days = request.getDays() == null ? DEFAULT_DAYS : request.getDays();
        LocalDate endDate = startDate.plusDays(days - 1L);
        MesShift shift = shiftMapper.selectOne(new LambdaQueryWrapper<MesShift>()
                .eq(MesShift::getStatus, DATA_ENABLED)
                .orderByAsc(MesShift::getId)
                .last("LIMIT 1"));
        if (shift == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.dispatch.configured-available-shift-cannot-generate-scheduling", "未配置可用班次，无法生成排程");
        }
        LambdaQueryWrapper<MesWorkCenter> centerWrapper = new LambdaQueryWrapper<MesWorkCenter>()
                .eq(MesWorkCenter::getStatus, DATA_ENABLED);
        if (request.getWorkCenterIds() != null && !request.getWorkCenterIds().isEmpty()) {
            centerWrapper.in(MesWorkCenter::getId, request.getWorkCenterIds());
        }
        List<MesWorkCenter> centers = workCenterMapper.selectList(centerWrapper);
        if (centers.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.dispatch.configured-available-work-center-cannot-generate", "未配置可用工作中心，无法生成排程");
        }
        Set<Long> reservedTaskIds = slotMapper.selectList(new LambdaQueryWrapper<MesScheduleSlot>()
                        .eq(MesScheduleSlot::getStatus, SLOT_RESERVED)
                        .between(MesScheduleSlot::getScheduleDate, startDate, endDate))
                .stream().map(MesScheduleSlot::getTaskId).collect(Collectors.toSet());
        Set<Long> centerIds = centers.stream().map(MesWorkCenter::getId).collect(Collectors.toSet());
        List<MesWorkOrderTask> tasks = new ArrayList<>(taskMapper.selectList(new LambdaQueryWrapper<MesWorkOrderTask>()
                .in(MesWorkOrderTask::getStatus, SCHEDULABLE_STATUS)
                .in(MesWorkOrderTask::getWorkCenterId, centerIds)));
        tasks.removeIf(task -> reservedTaskIds.contains(task.getId()) || remainingQuantity(task).signum() <= 0);
        Map<Long, List<MesWorkOrderTask>> tasksByCenter = new HashMap<>();
        for (MesWorkOrderTask task : tasks) {
            tasksByCenter.computeIfAbsent(task.getWorkCenterId(), key -> new ArrayList<>()).add(task);
        }
        Map<Long, MesSchedulePlan> plansByOrder = new HashMap<>();
        GenerateStats stats = new GenerateStats();
        for (MesWorkCenter center : centers) {
            allocateCenter(center, tasksByCenter.get(center.getId()), shift,
                    startDate, days, request.getPlannedBy(), plansByOrder, stats);
        }
        log.info("有限产能排程生成: startDate={}, days={}, plans={}, slots={}, unscheduled={}",
                startDate, days, stats.planCount, stats.slotCount, stats.unscheduledTasks);
        MesScheduleGenerateVO vo = new MesScheduleGenerateVO();
        vo.setPlanCount(stats.planCount);
        vo.setSlotCount(stats.slotCount);
        vo.setUnscheduledTasks(stats.unscheduledTasks);
        vo.setMessage(stats.unscheduledTasks > 0
                ? "部分任务在排程窗口内容量不足，剩余数量未排入"
                : "排程生成完成");
        return vo;
    }

    /**
     * 在单个工作中心内按日产能分配任务
     *
     * @param center        工作中心
     * @param centerTasks   该中心待排任务（可为空）
     * @param shift         班次
     * @param startDate     起始日期
     * @param days          天数
     * @param plannedBy     排程人
     * @param plansByOrder  工单计划缓存
     * @param stats         统计
     */
    private void allocateCenter(MesWorkCenter center, List<MesWorkOrderTask> centerTasks, MesShift shift,
                                LocalDate startDate, int days, String plannedBy,
                                Map<Long, MesSchedulePlan> plansByOrder, GenerateStats stats) {
        BigDecimal capacity = center.getCapacityPerShift();
        if (capacity == null || capacity.signum() <= 0 || centerTasks == null) {
            return;
        }
        centerTasks.sort(taskComparator());
        BigDecimal[] used = new BigDecimal[days];
        Arrays.fill(used, BigDecimal.ZERO);
        for (MesWorkOrderTask task : centerTasks) {
            BigDecimal remaining = remainingQuantity(task);
            for (int day = 0; day < days && remaining.signum() > 0; day++) {
                BigDecimal available = capacity.subtract(used[day]);
                if (available.signum() <= 0) {
                    continue;
                }
                BigDecimal allocated = remaining.min(available);
                MesSchedulePlan plan = planForTask(task, startDate, plannedBy, plansByOrder, stats);
                insertSlot(plan, task, center, shift, startDate.plusDays(day), capacity, allocated, stats);
                used[day] = used[day].add(allocated);
                remaining = remaining.subtract(allocated);
            }
            if (remaining.signum() > 0) {
                stats.unscheduledTasks++;
            }
        }
    }

    /**
     * 取（或创建）任务所属工单的排程计划
     *
     * @param task          任务
     * @param startDate     起始日期
     * @param plannedBy     排程人
     * @param plansByOrder  工单计划缓存
     * @param stats         统计
     * @return 排程计划
     */
    private MesSchedulePlan planForTask(MesWorkOrderTask task, LocalDate startDate, String plannedBy,
                                        Map<Long, MesSchedulePlan> plansByOrder, GenerateStats stats) {
        MesSchedulePlan plan = plansByOrder.get(task.getWorkOrderId());
        if (plan == null) {
            plan = createPlan(task, startDate, plannedBy);
            planMapper.insert(plan);
            plansByOrder.put(task.getWorkOrderId(), plan);
            stats.planCount++;
        }
        return plan;
    }

    /**
     * 写入排程时段
     *
     * @param plan       计划
     * @param task       任务
     * @param center     工作中心
     * @param shift      班次
     * @param scheduleDate 排程日期
     * @param capacity   班次产能
     * @param allocated  分配数量
     * @param stats      统计
     */
    private void insertSlot(MesSchedulePlan plan, MesWorkOrderTask task, MesWorkCenter center, MesShift shift,
                            LocalDate scheduleDate, BigDecimal capacity, BigDecimal allocated, GenerateStats stats) {
        MesScheduleSlot slot = new MesScheduleSlot();
        slot.setPlanId(plan.getId());
        slot.setTaskId(task.getId());
        slot.setWorkCenterId(center.getId());
        slot.setShiftId(shift.getId());
        slot.setScheduleDate(scheduleDate);
        slot.setCapacityQuantity(capacity);
        slot.setAllocatedQuantity(allocated);
        slot.setStatus(SLOT_RESERVED);
        slotMapper.insert(slot);
        stats.slotCount++;
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesSchedulePlanVO> page(MesScheduleQueryRequest request) {
        LambdaQueryWrapper<MesSchedulePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getPlanDate() != null, MesSchedulePlan::getPlanDate, request.getPlanDate());
        wrapper.like(StringUtils.hasText(request.getWorkOrderCode()),
                MesSchedulePlan::getWorkOrderCode, request.getWorkOrderCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesSchedulePlan::getStatus, request.getStatus());
        wrapper.orderByDesc(MesSchedulePlan::getPlanDate).orderByDesc(MesSchedulePlan::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent(), 1L);
        Page<MesSchedulePlan> page = planMapper.selectPage(new Page<>(current, size), wrapper);
        List<MesSchedulePlanVO> records = page.getRecords().stream().map(this::toPlanVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    public MesSchedulePlanVO detail(Long id) {
        MesSchedulePlan plan = requirePlan(id);
        List<MesScheduleSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<MesScheduleSlot>()
                .eq(MesScheduleSlot::getPlanId, id)
                .orderByAsc(MesScheduleSlot::getScheduleDate)
                .orderByAsc(MesScheduleSlot::getId));
        Map<Long, MesWorkOrderTask> taskMap = new HashMap<>();
        if (!slots.isEmpty()) {
            Set<Long> taskIds = slots.stream().map(MesScheduleSlot::getTaskId).collect(Collectors.toSet());
            taskMapper.selectList(new LambdaQueryWrapper<MesWorkOrderTask>()
                            .in(MesWorkOrderTask::getId, taskIds))
                    .forEach(task -> taskMap.put(task.getId(), task));
        }
        MesSchedulePlanVO vo = toPlanVo(plan);
        vo.setSlots(slots.stream().map(slot -> toSlotVo(slot, taskMap.get(slot.getTaskId()))).toList());
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSchedulePlanVO lock(Long id, MesScheduleActionRequest request) {
        MesSchedulePlan plan = requirePlan(id);
        if (PLAN_LOCKED.equals(plan.getStatus())) {
            return toPlanVo(plan);
        }
        if (!PLAN_DRAFT.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.dispatch.draft-scheduling-can-locked", "仅草稿排程可锁定：{0}", plan.getPlanNo());
        }
        plan.setStatus(PLAN_LOCKED);
        if (request != null && StringUtils.hasText(request.getRemark())) {
            plan.setRemark(request.getRemark());
        }
        if (planMapper.updateById(plan) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.dispatch.scheduling-plan-was-modified-refresh-retry", "排程计划已被其他用户修改，请刷新后重试");
        }
        return toPlanVo(plan);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSchedulePlanVO cancel(Long id, MesScheduleActionRequest request) {
        MesSchedulePlan plan = requirePlan(id);
        if (PLAN_CANCELLED.equals(plan.getStatus())) {
            return toPlanVo(plan);
        }
        if (!PLAN_DRAFT.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.dispatch.draft-scheduling-can-cancel-locked-scheduling", "仅草稿排程可取消，已锁定排程不可回滚：{0}",
                          plan.getPlanNo());
        }
        plan.setStatus(PLAN_CANCELLED);
        if (request != null && StringUtils.hasText(request.getRemark())) {
            plan.setRemark(request.getRemark());
        }
        if (planMapper.updateById(plan) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.dispatch.scheduling-plan-was-modified-refresh-retry-2", "排程计划已被其他用户修改，请刷新后重试");
        }
        List<MesScheduleSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<MesScheduleSlot>()
                .eq(MesScheduleSlot::getPlanId, id)
                .eq(MesScheduleSlot::getStatus, SLOT_RESERVED));
        for (MesScheduleSlot slot : slots) {
            slot.setStatus(SLOT_RELEASED);
            slotMapper.updateById(slot);
        }
        log.info("取消排程计划: planNo={}, releasedSlots={}", plan.getPlanNo(), slots.size());
        return toPlanVo(plan);
    }

    /**
     * 创建排程计划（一个工单在排程窗口内对应一个计划）
     *
     * @param task      任务
     * @param planDate  排程日期
     * @param plannedBy 排程人
     * @return 计划实体
     */
    private MesSchedulePlan createPlan(MesWorkOrderTask task, LocalDate planDate, String plannedBy) {
        MesSchedulePlan plan = new MesSchedulePlan();
        plan.setPlanNo(MesCodeGenerator.nextNo("SCH"));
        plan.setPlanDate(planDate);
        plan.setWorkOrderId(task.getWorkOrderId());
        plan.setWorkOrderCode(task.getWorkOrderCode());
        plan.setStatus(PLAN_DRAFT);
        plan.setPlannedBy(plannedBy);
        return plan;
    }

    /**
     * 计算任务剩余待排数量
     *
     * @param task 任务
     * @return 剩余数量
     */
    private BigDecimal remainingQuantity(MesWorkOrderTask task) {
        BigDecimal planned = task.getPlannedQuantity() == null ? BigDecimal.ZERO : task.getPlannedQuantity();
        BigDecimal completed = task.getCompletedQuantity() == null ? BigDecimal.ZERO : task.getCompletedQuantity();
        BigDecimal remaining = planned.subtract(completed);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    /**
     * 任务排序：急单优先，其次计划完工早者优先，最后按 ID 稳定排序
     *
     * @return 比较器
     */
    private Comparator<MesWorkOrderTask> taskComparator() {
        return Comparator
                .comparing((MesWorkOrderTask task) -> "URGENT".equals(task.getPriority()) ? 0 : 1)
                .thenComparing(task -> task.getPlannedEnd() == null ? java.time.LocalDateTime.MAX
                        : task.getPlannedEnd())
                .thenComparing(MesWorkOrderTask::getId);
    }

    /**
     * 加载排程计划，不存在即报错
     *
     * @param id 计划 ID
     * @return 计划实体
     */
    private MesSchedulePlan requirePlan(Long id) {
        MesSchedulePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.dispatch.scheduling-plan-found", "排程计划不存在：{0}", id);
        }
        return plan;
    }

    /**
     * 计划实体转视图
     *
     * @param plan 计划实体
     * @return 计划视图
     */
    private MesSchedulePlanVO toPlanVo(MesSchedulePlan plan) {
        MesSchedulePlanVO vo = new MesSchedulePlanVO();
        vo.setId(plan.getId());
        vo.setPlanNo(plan.getPlanNo());
        vo.setPlanDate(plan.getPlanDate());
        vo.setWorkOrderId(plan.getWorkOrderId());
        vo.setWorkOrderCode(plan.getWorkOrderCode());
        vo.setStatus(plan.getStatus());
        vo.setPlannedBy(plan.getPlannedBy());
        vo.setRemark(plan.getRemark());
        vo.setCreateTime(plan.getCreateTime());
        return vo;
    }

    /**
     * 时段实体转视图
     *
     * @param slot 时段实体
     * @param task 任务（可为空）
     * @return 时段视图
     */
    private MesScheduleSlotVO toSlotVo(MesScheduleSlot slot, MesWorkOrderTask task) {
        MesScheduleSlotVO vo = new MesScheduleSlotVO();
        vo.setId(slot.getId());
        vo.setPlanId(slot.getPlanId());
        vo.setTaskId(slot.getTaskId());
        vo.setWorkCenterId(slot.getWorkCenterId());
        vo.setShiftId(slot.getShiftId());
        vo.setScheduleDate(slot.getScheduleDate());
        vo.setCapacityQuantity(slot.getCapacityQuantity());
        vo.setAllocatedQuantity(slot.getAllocatedQuantity());
        vo.setStatus(slot.getStatus());
        if (task != null) {
            vo.setTaskNo(task.getTaskNo());
            vo.setOperationCode(task.getOperationCode());
            vo.setOperationName(task.getOperationName());
            vo.setWorkCenterCode(task.getWorkCenterCode());
        }
        return vo;
    }

    /**
     * 排程生成统计。
     */
    private static final class GenerateStats {

        /** 生成的计划数 */
        private int planCount;

        /** 生成的时段数 */
        private int slotCount;

        /** 未完全排入的任务数 */
        private int unscheduledTasks;
    }
}

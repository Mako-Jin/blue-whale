package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.quality.dto.MesInspectionDefectRequest;
import com.bluewhale.mes.quality.dto.MesInspectionJudgeRequest;
import com.bluewhale.mes.quality.dto.MesInspectionPlanItemRequest;
import com.bluewhale.mes.quality.dto.MesInspectionPlanQueryRequest;
import com.bluewhale.mes.quality.dto.MesInspectionPlanSaveRequest;
import com.bluewhale.mes.quality.dto.MesInspectionResultItemRequest;
import com.bluewhale.mes.quality.dto.MesInspectionResultSubmitRequest;
import com.bluewhale.mes.quality.dto.MesInspectionTaskCreateRequest;
import com.bluewhale.mes.quality.dto.MesInspectionTaskQueryRequest;
import com.bluewhale.mes.quality.entity.MesInspectionDefect;
import com.bluewhale.mes.quality.entity.MesInspectionPlan;
import com.bluewhale.mes.quality.entity.MesInspectionPlanItem;
import com.bluewhale.mes.quality.entity.MesInspectionResult;
import com.bluewhale.mes.quality.entity.MesInspectionTask;
import com.bluewhale.mes.quality.entity.MesNonconformingRecord;
import com.bluewhale.mes.quality.entity.MesReworkOrder;
import com.bluewhale.mes.quality.entity.MesSpcSample;
import com.bluewhale.mes.quality.mapper.MesDispositionMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionDefectMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionPlanItemMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionPlanMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionResultMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionTaskMapper;
import com.bluewhale.mes.quality.mapper.MesNonconformingRecordMapper;
import com.bluewhale.mes.quality.mapper.MesReworkOrderMapper;
import com.bluewhale.mes.quality.mapper.MesSpcSampleMapper;
import com.bluewhale.mes.quality.service.MesInspectionService;
import com.bluewhale.mes.quality.service.MesNcrService;
import com.bluewhale.mes.quality.vo.MesFirstArticleGateVO;
import com.bluewhale.mes.quality.vo.MesInspectionDefectVO;
import com.bluewhale.mes.quality.vo.MesInspectionPlanItemVO;
import com.bluewhale.mes.quality.vo.MesInspectionPlanVO;
import com.bluewhale.mes.quality.vo.MesInspectionResultVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskDetailVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskVO;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MES 车间检验应用服务实现
 *
 * <p>检验项按计划项逐项录入，判定前必须覆盖全部计划项；判定 {@code FAILED} 时同事务生成 NCR。
 * 首件门禁取同工单同工序最近一次首件检验，最近一次未通过即阻断批量释放。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesInspectionServiceImpl implements MesInspectionService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 检验类型：首件 */
    private static final String TYPE_FIRST_ARTICLE = "FIRST_ARTICLE";

    /** 状态：待检 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已录入 */
    private static final String STATUS_INSPECTED = "INSPECTED";

    /** 状态：合格 */
    private static final String STATUS_PASSED = "PASSED";

    /** 状态：不合格 */
    private static final String STATUS_FAILED = "FAILED";

    /** 状态：取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 计划状态：启用 */
    private static final String PLAN_ENABLED = "ENABLED";

    /** 结果：合格 */
    private static final String RESULT_OK = "OK";

    /** 结果：不合格 */
    private static final String RESULT_NG = "NG";

    private final MesInspectionPlanMapper planMapper;

    private final MesInspectionPlanItemMapper planItemMapper;

    private final MesInspectionTaskMapper taskMapper;

    private final MesInspectionResultMapper resultMapper;

    private final MesInspectionDefectMapper defectMapper;

    private final MesNonconformingRecordMapper ncrMapper;

    private final MesDispositionMapper dispositionMapper;

    private final MesReworkOrderMapper reworkMapper;

    private final MesSpcSampleMapper spcSampleMapper;

    private final MesNcrService ncrService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesInspectionPlanVO savePlan(MesInspectionPlanSaveRequest request) {
        validatePlanItems(request.getItems());
        MesInspectionPlan plan;
        if (request.getId() == null) {
            plan = new MesInspectionPlan();
            plan.setPlanNo(StringUtils.hasText(request.getPlanNo())
                    ? request.getPlanNo() : MesCodeGenerator.nextNo("QCP"));
        } else {
            plan = planMapper.selectById(request.getId());
            if (plan == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "检验计划不存在：" + request.getId());
            }
            if (taskMapper.selectCount(new LambdaQueryWrapper<MesInspectionTask>()
                    .eq(MesInspectionTask::getPlanId, plan.getId())) > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "检验计划已产生检验任务，不可修改");
            }
        }
        assertPlanNoUnique(plan.getId(), plan.getPlanNo());
        plan.setPlanName(request.getPlanName());
        plan.setInspectionType(request.getInspectionType());
        plan.setItemCode(request.getItemCode());
        plan.setOperationCode(request.getOperationCode());
        plan.setLineId(request.getLineId());
        plan.setLineCode(request.getLineCode());
        plan.setSampleSize(request.getSampleSize() == null ? 1 : request.getSampleSize());
        plan.setFrequencyMinutes(request.getFrequencyMinutes() == null ? 0 : request.getFrequencyMinutes());
        plan.setAutoNcr(request.getAutoNcr() == null ? 1 : request.getAutoNcr());
        plan.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : PLAN_ENABLED);
        plan.setRemark(request.getRemark());
        if (plan.getId() == null) {
            planMapper.insert(plan);
        } else {
            if (planMapper.updateById(plan) == 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "检验计划已被其他用户修改，请刷新后重试");
            }
            planItemMapper.delete(new LambdaQueryWrapper<MesInspectionPlanItem>()
                    .eq(MesInspectionPlanItem::getPlanId, plan.getId()));
        }
        for (MesInspectionPlanItemRequest itemRequest : request.getItems()) {
            planItemMapper.insert(buildPlanItem(plan.getId(), itemRequest));
        }
        log.info("检验计划保存: planNo={}, type={}, items={}",
                plan.getPlanNo(), plan.getInspectionType(), request.getItems().size());
        return getPlan(plan.getId());
    }

    @Override
    public PageResult<MesInspectionPlanVO> pagePlans(MesInspectionPlanQueryRequest request) {
        LambdaQueryWrapper<MesInspectionPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getPlanNo()), MesInspectionPlan::getPlanNo, request.getPlanNo());
        wrapper.like(StringUtils.hasText(request.getPlanName()), MesInspectionPlan::getPlanName, request.getPlanName());
        wrapper.eq(StringUtils.hasText(request.getInspectionType()),
                MesInspectionPlan::getInspectionType, request.getInspectionType());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesInspectionPlan::getStatus, request.getStatus());
        wrapper.orderByDesc(MesInspectionPlan::getCreateTime);
        Page<MesInspectionPlan> page = planMapper.selectPage(pageOf(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toPlanVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MesInspectionPlanVO getPlan(Long id) {
        MesInspectionPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "检验计划不存在：" + id);
        }
        MesInspectionPlanVO vo = toPlanVo(plan);
        List<MesInspectionPlanItem> items = listPlanItems(plan.getId());
        vo.setItems(items.stream().map(this::toPlanItemVo).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesInspectionTaskVO createTask(MesInspectionTaskCreateRequest request) {
        MesInspectionPlan plan = resolvePlan(request);
        List<MesInspectionPlanItem> items = listPlanItems(plan.getId());
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验计划未配置检验项：" + plan.getPlanNo());
        }
        MesInspectionTask task = new MesInspectionTask();
        task.setTaskNo(MesCodeGenerator.nextNo("QCT"));
        task.setTaskType(request.getTaskType());
        task.setPlanId(plan.getId());
        task.setPlanNo(plan.getPlanNo());
        task.setWorkOrderId(request.getWorkOrderId());
        task.setWorkOrderCode(request.getWorkOrderCode());
        task.setExecTaskId(request.getExecTaskId());
        task.setExecTaskNo(request.getExecTaskNo());
        task.setOperationCode(StringUtils.hasText(request.getOperationCode())
                ? request.getOperationCode() : plan.getOperationCode());
        task.setLineId(request.getLineId() == null ? plan.getLineId() : request.getLineId());
        task.setLineCode(StringUtils.hasText(request.getLineCode())
                ? request.getLineCode() : plan.getLineCode());
        task.setStationCode(request.getStationCode());
        task.setBatchNo(request.getBatchNo());
        task.setSampleSize(request.getSampleSize() == null
                ? plan.getSampleSize() : request.getSampleSize());
        task.setInspectedQuantity(0);
        task.setStatus(STATUS_PENDING);
        task.setInspector(request.getInspector());
        task.setRemark(request.getRemark());
        taskMapper.insert(task);
        log.info("检验任务创建: taskNo={}, type={}, planNo={}", task.getTaskNo(), task.getTaskType(), plan.getPlanNo());
        return toTaskVo(task);
    }

    @Override
    public PageResult<MesInspectionTaskVO> pageTasks(MesInspectionTaskQueryRequest request) {
        LambdaQueryWrapper<MesInspectionTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getTaskNo()), MesInspectionTask::getTaskNo, request.getTaskNo());
        wrapper.eq(StringUtils.hasText(request.getTaskType()), MesInspectionTask::getTaskType, request.getTaskType());
        wrapper.eq(request.getWorkOrderId() != null, MesInspectionTask::getWorkOrderId, request.getWorkOrderId());
        wrapper.like(StringUtils.hasText(request.getWorkOrderCode()),
                MesInspectionTask::getWorkOrderCode, request.getWorkOrderCode());
        wrapper.eq(StringUtils.hasText(request.getOperationCode()),
                MesInspectionTask::getOperationCode, request.getOperationCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesInspectionTask::getStatus, request.getStatus());
        wrapper.orderByDesc(MesInspectionTask::getCreateTime);
        Page<MesInspectionTask> page = taskMapper.selectPage(pageOf(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toTaskVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MesInspectionTaskDetailVO getTask(Long id) {
        MesInspectionTask task = loadTask(id);
        List<MesInspectionResult> results = resultMapper.selectList(new LambdaQueryWrapper<MesInspectionResult>()
                .eq(MesInspectionResult::getTaskId, id)
                .orderByAsc(MesInspectionResult::getItemNo)
                .orderByAsc(MesInspectionResult::getSampleNo));
        List<MesInspectionDefect> defects = defectMapper.selectList(new LambdaQueryWrapper<MesInspectionDefect>()
                .eq(MesInspectionDefect::getTaskId, id)
                .orderByAsc(MesInspectionDefect::getId));
        int planItemCount = task.getPlanId() == null ? 0 : listPlanItems(task.getPlanId()).size();
        int completedItemCount = (int) results.stream()
                .map(MesInspectionResult::getPlanItemId).filter(planItemId -> planItemId != null).distinct().count();
        MesInspectionTaskDetailVO detail = new MesInspectionTaskDetailVO();
        detail.setTask(toTaskVo(task));
        detail.setResults(results.stream().map(this::toResultVo).toList());
        detail.setDefects(defects.stream().map(this::toDefectVo).toList());
        detail.setPlanItemCount(planItemCount);
        detail.setCompletedItemCount(completedItemCount);
        detail.setJudgeReady(planItemCount > 0 && completedItemCount >= planItemCount
                && STATUS_INSPECTED.equals(task.getStatus()));
        detail.setQueryTime(LocalDateTime.now());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesInspectionTaskDetailVO submitResults(Long id, MesInspectionResultSubmitRequest request) {
        MesInspectionTask task = loadTask(id);
        if (!STATUS_PENDING.equals(task.getStatus()) && !STATUS_INSPECTED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前状态不可录入检验结果：" + task.getStatus());
        }
        Map<Long, MesInspectionPlanItem> itemMap = planItemMap(task.getPlanId());
        for (MesInspectionResultItemRequest itemRequest : request.getResults()) {
            MesInspectionPlanItem planItem = itemMap.get(itemRequest.getPlanItemId());
            if (planItem == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "检验项不属于该计划：" + itemRequest.getPlanItemId());
            }
            assertMeasuredValue(planItem, itemRequest);
            resultMapper.insert(buildResult(task, planItem, itemRequest, request.getInspector()));
        }
        if (request.getDefects() != null) {
            for (MesInspectionDefectRequest defectRequest : request.getDefects()) {
                defectMapper.insert(buildDefect(task.getId(), defectRequest));
            }
        }
        List<MesInspectionResult> results = listResults(task.getId());
        task.setInspectedQuantity((int) results.stream()
                .map(MesInspectionResult::getPlanItemId).filter(planItemId -> planItemId != null).distinct().count());
        task.setStatus(STATUS_INSPECTED);
        if (StringUtils.hasText(request.getInspector())) {
            task.setInspector(request.getInspector());
        }
        if (taskMapper.updateById(task) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验任务已被其他用户修改，请刷新后重试");
        }
        return getTask(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesInspectionTaskVO judge(Long id, MesInspectionJudgeRequest request) {
        MesInspectionTask task = loadTask(id);
        if (STATUS_PASSED.equals(task.getStatus()) || STATUS_FAILED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验任务已判定，不可重复判定");
        }
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验任务已取消，不可判定");
        }
        List<MesInspectionPlanItem> items = listPlanItems(task.getPlanId());
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验计划未配置检验项，不可判定");
        }
        List<MesInspectionResult> results = listResults(task.getId());
        Set<Long> completedItemIds = new HashSet<>();
        results.forEach(result -> {
            if (result.getPlanItemId() != null) {
                completedItemIds.add(result.getPlanItemId());
            }
        });
        if (completedItemIds.size() < items.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "检验项未完成，不可判定：应检 " + items.size() + " 项，已检 " + completedItemIds.size() + " 项");
        }
        boolean failed = results.stream().anyMatch(result -> RESULT_NG.equals(result.getResult()))
                || !defectMapper.selectList(new LambdaQueryWrapper<MesInspectionDefect>()
                .eq(MesInspectionDefect::getTaskId, task.getId())).isEmpty();
        task.setStatus(failed ? STATUS_FAILED : STATUS_PASSED);
        task.setJudge(request.getJudge());
        task.setJudgedAt(LocalDateTime.now());
        task.setConclusion(request.getConclusion());
        task.setInspectedQuantity(completedItemIds.size());
        if (failed) {
            Long ncrId = ncrService.createFromInspectionTask(task);
            task.setNcrId(ncrId);
            log.warn("检验不合格，已生成 NCR: taskNo={}, ncrId={}", task.getTaskNo(), ncrId);
        }
        if (taskMapper.updateById(task) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验任务已被其他用户修改，请刷新后重试");
        }
        return toTaskVo(task);
    }

    @Override
    public MesFirstArticleGateVO firstArticleGate(Long workOrderId, String operationCode) {
        MesFirstArticleGateVO vo = new MesFirstArticleGateVO();
        vo.setWorkOrderId(workOrderId);
        vo.setOperationCode(operationCode);
        List<MesInspectionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<MesInspectionTask>()
                .eq(MesInspectionTask::getTaskType, TYPE_FIRST_ARTICLE)
                .eq(workOrderId != null, MesInspectionTask::getWorkOrderId, workOrderId)
                .eq(StringUtils.hasText(operationCode), MesInspectionTask::getOperationCode, operationCode)
                .ne(MesInspectionTask::getStatus, STATUS_CANCELLED)
                .orderByDesc(MesInspectionTask::getId)
                .last("LIMIT 1"));
        if (tasks.isEmpty()) {
            vo.setPassed(false);
            vo.setStatus(null);
            vo.setMessage("未找到首件检验任务，禁止批量释放");
            return vo;
        }
        MesInspectionTask latest = tasks.get(0);
        vo.setTaskNo(latest.getTaskNo());
        vo.setStatus(latest.getStatus());
        vo.setJudgedAt(latest.getJudgedAt());
        if (STATUS_PASSED.equals(latest.getStatus())) {
            vo.setPassed(true);
            vo.setMessage("首件检验合格，允许批量生产");
        } else {
            vo.setPassed(false);
            vo.setMessage("首件检验未通过（" + latest.getStatus() + "），禁止批量释放");
        }
        return vo;
    }

    @Override
    public MesQualityBoardVO board(String boardDate, Long lineId) {
        LocalDate date = resolveBoardDate(boardDate);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        MesQualityBoardVO vo = new MesQualityBoardVO();
        vo.setBoardDate(date.toString());
        vo.setInspectionTaskCount(countTasks(start, end, null, lineId));
        vo.setPendingTaskCount(countTasks(start, end, STATUS_PENDING, lineId));
        vo.setFailedTaskCount(countTasks(start, end, STATUS_FAILED, lineId));
        long passed = countTasks(start, end, STATUS_PASSED, lineId);
        long failed = vo.getFailedTaskCount();
        vo.setFirstArticlePassRate(passRate(passed, failed));
        vo.setNcrOpenCount(ncrMapper.selectCount(new LambdaQueryWrapper<MesNonconformingRecord>()
                .ne(MesNonconformingRecord::getStatus, "CLOSED")));
        vo.setNcrClosedCount(ncrMapper.selectCount(new LambdaQueryWrapper<MesNonconformingRecord>()
                .eq(MesNonconformingRecord::getStatus, "CLOSED")));
        vo.setDispositionCount(dispositionMapper.selectCount(null));
        vo.setReworkOpenCount(reworkMapper.selectCount(new LambdaQueryWrapper<MesReworkOrder>()
                .ne(MesReworkOrder::getStatus, "COMPLETED")
                .ne(MesReworkOrder::getStatus, "CANCELLED")));
        vo.setSpcAlertCount(spcSampleMapper.selectCount(new LambdaQueryWrapper<MesSpcSample>()
                .eq(MesSpcSample::getAlertFlag, 1)));
        return vo;
    }

    /**
     * 校验检验项：序号唯一、上下限有序
     *
     * @param items 检验项请求
     */
    private void validatePlanItems(List<MesInspectionPlanItemRequest> items) {
        Set<Integer> itemNos = new HashSet<>();
        for (MesInspectionPlanItemRequest item : items) {
            if (!itemNos.add(item.getItemNo())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "检验项序号重复：" + item.getItemNo());
            }
            if (item.getMinValue() != null && item.getMaxValue() != null
                    && item.getMinValue().compareTo(item.getMaxValue()) > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "检验项上下限不合法：" + item.getItemName());
            }
        }
    }

    /**
     * 计划编号租户内唯一校验
     *
     * @param id     计划 ID（新增为 {@code null}）
     * @param planNo 计划编号
     */
    private void assertPlanNoUnique(Long id, String planNo) {
        LambdaQueryWrapper<MesInspectionPlan> wrapper = new LambdaQueryWrapper<MesInspectionPlan>()
                .eq(MesInspectionPlan::getPlanNo, planNo)
                .ne(id != null, MesInspectionPlan::getId, id);
        if (planMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检验计划编号已存在：" + planNo);
        }
    }

    /**
     * 构建检验项实体
     *
     * @param planId      计划 ID
     * @param itemRequest 检验项请求
     * @return 检验项实体
     */
    private MesInspectionPlanItem buildPlanItem(Long planId, MesInspectionPlanItemRequest itemRequest) {
        MesInspectionPlanItem item = new MesInspectionPlanItem();
        item.setPlanId(planId);
        item.setItemNo(itemRequest.getItemNo());
        item.setItemName(itemRequest.getItemName());
        item.setInspectionMethod(StringUtils.hasText(itemRequest.getInspectionMethod())
                ? itemRequest.getInspectionMethod() : "MEASURE");
        item.setStandardText(itemRequest.getStandardText());
        item.setMinValue(itemRequest.getMinValue());
        item.setMaxValue(itemRequest.getMaxValue());
        item.setUnit(itemRequest.getUnit());
        item.setCriticalFlag(itemRequest.getCriticalFlag() == null ? 0 : itemRequest.getCriticalFlag());
        item.setSortNo(itemRequest.getSortNo() == null ? itemRequest.getItemNo() : itemRequest.getSortNo());
        return item;
    }

    /**
     * 解析检验计划：指定 ID 优先，否则按类型与工序匹配启用计划
     *
     * @param request 任务创建请求
     * @return 检验计划
     */
    private MesInspectionPlan resolvePlan(MesInspectionTaskCreateRequest request) {
        if (request.getPlanId() != null) {
            MesInspectionPlan plan = planMapper.selectById(request.getPlanId());
            if (plan == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "检验计划不存在：" + request.getPlanId());
            }
            if (!PLAN_ENABLED.equals(plan.getStatus())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "检验计划未启用：" + plan.getPlanNo());
            }
            return plan;
        }
        List<MesInspectionPlan> plans = planMapper.selectList(new LambdaQueryWrapper<MesInspectionPlan>()
                .eq(MesInspectionPlan::getInspectionType, request.getTaskType())
                .eq(MesInspectionPlan::getStatus, PLAN_ENABLED)
                .eq(StringUtils.hasText(request.getOperationCode()),
                        MesInspectionPlan::getOperationCode, request.getOperationCode())
                .eq(request.getLineId() != null, MesInspectionPlan::getLineId, request.getLineId())
                .orderByAsc(MesInspectionPlan::getId));
        if (plans.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "未找到可用检验计划：type=" + request.getTaskType()
                            + ", operation=" + request.getOperationCode());
        }
        return plans.get(0);
    }

    /**
     * 加载检验任务
     *
     * @param id 任务 ID
     * @return 检验任务
     */
    private MesInspectionTask loadTask(Long id) {
        MesInspectionTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "检验任务不存在：" + id);
        }
        return task;
    }

    /**
     * 查询计划的检验项
     *
     * @param planId 计划 ID
     * @return 检验项列表
     */
    private List<MesInspectionPlanItem> listPlanItems(Long planId) {
        if (planId == null) {
            return List.of();
        }
        return planItemMapper.selectList(new LambdaQueryWrapper<MesInspectionPlanItem>()
                .eq(MesInspectionPlanItem::getPlanId, planId)
                .orderByAsc(MesInspectionPlanItem::getSortNo)
                .orderByAsc(MesInspectionPlanItem::getItemNo));
    }

    /**
     * 检验项 ID → 实体映射
     *
     * @param planId 计划 ID
     * @return 映射
     */
    private Map<Long, MesInspectionPlanItem> planItemMap(Long planId) {
        Map<Long, MesInspectionPlanItem> map = new HashMap<>();
        listPlanItems(planId).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    /**
     * 查询任务的检验结果
     *
     * @param taskId 任务 ID
     * @return 结果列表
     */
    private List<MesInspectionResult> listResults(Long taskId) {
        return resultMapper.selectList(new LambdaQueryWrapper<MesInspectionResult>()
                .eq(MesInspectionResult::getTaskId, taskId));
    }

    /**
     * 校验实测值：测量型检验项 OK 结果不得越出上下限
     *
     * @param planItem    检验项
     * @param itemRequest 结果请求
     */
    private void assertMeasuredValue(MesInspectionPlanItem planItem, MesInspectionResultItemRequest itemRequest) {
        BigDecimal value = itemRequest.getNumericValue();
        if (!RESULT_OK.equals(itemRequest.getResult()) || value == null) {
            return;
        }
        if (planItem.getMinValue() != null && value.compareTo(planItem.getMinValue()) < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "实测值低于下限：" + planItem.getItemName() + " " + value.toPlainString());
        }
        if (planItem.getMaxValue() != null && value.compareTo(planItem.getMaxValue()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "实测值高于上限：" + planItem.getItemName() + " " + value.toPlainString());
        }
    }

    /**
     * 构建检验结果实体
     *
     * @param task        检验任务
     * @param planItem    检验项
     * @param itemRequest 结果请求
     * @param inspector   检验员
     * @return 结果实体
     */
    private MesInspectionResult buildResult(MesInspectionTask task, MesInspectionPlanItem planItem,
                                            MesInspectionResultItemRequest itemRequest, String inspector) {
        MesInspectionResult result = new MesInspectionResult();
        result.setTaskId(task.getId());
        result.setPlanItemId(planItem.getId());
        result.setItemNo(planItem.getItemNo());
        result.setItemName(planItem.getItemName());
        result.setSampleNo(itemRequest.getSampleNo() == null ? 1 : itemRequest.getSampleNo());
        result.setMeasuredValue(itemRequest.getMeasuredValue());
        result.setNumericValue(itemRequest.getNumericValue());
        result.setResult(itemRequest.getResult());
        result.setInspector(inspector);
        result.setRemark(itemRequest.getRemark());
        return result;
    }

    /**
     * 构建检验不良实体
     *
     * @param taskId        任务 ID
     * @param defectRequest 不良请求
     * @return 不良实体
     */
    private MesInspectionDefect buildDefect(Long taskId, MesInspectionDefectRequest defectRequest) {
        MesInspectionDefect defect = new MesInspectionDefect();
        defect.setTaskId(taskId);
        defect.setDefectCode(defectRequest.getDefectCode());
        defect.setDefectName(defectRequest.getDefectName());
        defect.setQuantity(defectRequest.getQuantity() == null ? BigDecimal.ONE : defectRequest.getQuantity());
        defect.setSeverity(StringUtils.hasText(defectRequest.getSeverity())
                ? defectRequest.getSeverity() : "MAJOR");
        defect.setDescription(defectRequest.getDescription());
        return defect;
    }

    /**
     * 解析看板统计日期
     *
     * @param boardDate 日期文本
     * @return 统计日期
     */
    private LocalDate resolveBoardDate(String boardDate) {
        if (!StringUtils.hasText(boardDate)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(boardDate);
        } catch (DateTimeParseException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "统计日期格式应为 yyyy-MM-dd：" + boardDate);
        }
    }

    /**
     * 统计当日检验任务数
     *
     * @param start  起始时间
     * @param end    结束时间
     * @param status 状态（空则不限）
     * @param lineId 产线 ID（空则不限）
     * @return 任务数
     */
    private long countTasks(LocalDateTime start, LocalDateTime end, String status, Long lineId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<MesInspectionTask>()
                .ge(MesInspectionTask::getCreateTime, start)
                .lt(MesInspectionTask::getCreateTime, end)
                .eq(status != null, MesInspectionTask::getStatus, status)
                .eq(lineId != null, MesInspectionTask::getLineId, lineId));
    }

    /**
     * 计算首件通过率
     *
     * @param passed 通过数
     * @param failed 不合格数
     * @return 通过率（0–100，保留两位小数）
     */
    private BigDecimal passRate(long passed, long failed) {
        long total = passed + failed;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(passed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * 构建分页对象
     *
     * @param current 当前页
     * @param size    每页条数
     * @return 分页对象
     */
    private <T> Page<T> pageOf(Long current, Long size) {
        long pageSize = Math.min(Math.max(size == null ? 20L : size, 1L), MAX_PAGE_SIZE);
        long pageCurrent = Math.max(current == null ? 1L : current, 1L);
        return new Page<>(pageCurrent, pageSize);
    }

    /**
     * 计划实体转视图
     *
     * @param plan 计划实体
     * @return 计划视图
     */
    private MesInspectionPlanVO toPlanVo(MesInspectionPlan plan) {
        MesInspectionPlanVO vo = new MesInspectionPlanVO();
        vo.setId(plan.getId());
        vo.setPlanNo(plan.getPlanNo());
        vo.setPlanName(plan.getPlanName());
        vo.setInspectionType(plan.getInspectionType());
        vo.setItemCode(plan.getItemCode());
        vo.setOperationCode(plan.getOperationCode());
        vo.setLineId(plan.getLineId());
        vo.setLineCode(plan.getLineCode());
        vo.setSampleSize(plan.getSampleSize());
        vo.setFrequencyMinutes(plan.getFrequencyMinutes());
        vo.setAutoNcr(plan.getAutoNcr());
        vo.setStatus(plan.getStatus());
        vo.setRemark(plan.getRemark());
        vo.setCreateTime(plan.getCreateTime());
        return vo;
    }

    /**
     * 检验项实体转视图
     *
     * @param item 检验项实体
     * @return 检验项视图
     */
    private MesInspectionPlanItemVO toPlanItemVo(MesInspectionPlanItem item) {
        MesInspectionPlanItemVO vo = new MesInspectionPlanItemVO();
        vo.setId(item.getId());
        vo.setPlanId(item.getPlanId());
        vo.setItemNo(item.getItemNo());
        vo.setItemName(item.getItemName());
        vo.setInspectionMethod(item.getInspectionMethod());
        vo.setStandardText(item.getStandardText());
        vo.setMinValue(item.getMinValue());
        vo.setMaxValue(item.getMaxValue());
        vo.setUnit(item.getUnit());
        vo.setCriticalFlag(item.getCriticalFlag());
        vo.setSortNo(item.getSortNo());
        return vo;
    }

    /**
     * 检验任务实体转视图
     *
     * @param task 任务实体
     * @return 任务视图
     */
    private MesInspectionTaskVO toTaskVo(MesInspectionTask task) {
        MesInspectionTaskVO vo = new MesInspectionTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskType(task.getTaskType());
        vo.setPlanId(task.getPlanId());
        vo.setPlanNo(task.getPlanNo());
        vo.setWorkOrderId(task.getWorkOrderId());
        vo.setWorkOrderCode(task.getWorkOrderCode());
        vo.setExecTaskId(task.getExecTaskId());
        vo.setExecTaskNo(task.getExecTaskNo());
        vo.setOperationCode(task.getOperationCode());
        vo.setLineId(task.getLineId());
        vo.setLineCode(task.getLineCode());
        vo.setStationCode(task.getStationCode());
        vo.setBatchNo(task.getBatchNo());
        vo.setSampleSize(task.getSampleSize());
        vo.setInspectedQuantity(task.getInspectedQuantity());
        vo.setStatus(task.getStatus());
        vo.setInspector(task.getInspector());
        vo.setJudge(task.getJudge());
        vo.setJudgedAt(task.getJudgedAt());
        vo.setConclusion(task.getConclusion());
        vo.setNcrId(task.getNcrId());
        vo.setRemark(task.getRemark());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    /**
     * 检验结果实体转视图
     *
     * @param result 结果实体
     * @return 结果视图
     */
    private MesInspectionResultVO toResultVo(MesInspectionResult result) {
        MesInspectionResultVO vo = new MesInspectionResultVO();
        vo.setId(result.getId());
        vo.setTaskId(result.getTaskId());
        vo.setPlanItemId(result.getPlanItemId());
        vo.setItemNo(result.getItemNo());
        vo.setItemName(result.getItemName());
        vo.setSampleNo(result.getSampleNo());
        vo.setMeasuredValue(result.getMeasuredValue());
        vo.setNumericValue(result.getNumericValue());
        vo.setResult(result.getResult());
        vo.setInspector(result.getInspector());
        vo.setRemark(result.getRemark());
        vo.setCreateTime(result.getCreateTime());
        return vo;
    }

    /**
     * 检验不良实体转视图
     *
     * @param defect 不良实体
     * @return 不良视图
     */
    private MesInspectionDefectVO toDefectVo(MesInspectionDefect defect) {
        MesInspectionDefectVO vo = new MesInspectionDefectVO();
        vo.setId(defect.getId());
        vo.setTaskId(defect.getTaskId());
        vo.setDefectCode(defect.getDefectCode());
        vo.setDefectName(defect.getDefectName());
        vo.setQuantity(defect.getQuantity());
        vo.setSeverity(defect.getSeverity());
        vo.setDescription(defect.getDescription());
        vo.setCreateTime(defect.getCreateTime());
        return vo;
    }
}

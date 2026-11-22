package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.change.client.PdmChgWorkflowClient;
import com.bluewhale.pdm.change.dto.PdmChgOrderAssessRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderSaveRequest;
import com.bluewhale.pdm.change.dto.PdmChgTaskSaveRequest;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.entity.PdmChgTask;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.mapper.PdmChgTaskMapper;
import com.bluewhale.pdm.change.service.PdmChgOrderService;
import com.bluewhale.pdm.change.support.PdmChgObjectType;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.change.support.PdmChgTaskStatus;
import com.bluewhale.pdm.change.support.PdmChgApprovalSource;
import com.bluewhale.pdm.change.support.PdmChgApprovalStatus;
import com.bluewhale.pdm.change.support.PdmChgBizType;
import com.bluewhale.pdm.change.support.PdmChgTraceAction;
import com.bluewhale.pdm.change.support.PdmChgTraceRecorder;
import com.bluewhale.pdm.change.vo.PdmChgImpactObjectVO;
import com.bluewhale.pdm.change.vo.PdmChgOrderVO;
import com.bluewhale.pdm.change.vo.PdmChgTaskVO;
import com.bluewhale.pdm.support.PdmVersionNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 变更单服务实现
 *
 * <p>ECO 只能由已通过 ECR 创建并快照其影响对象；评估、提交、审批、执行、完成、关闭
 * 均由服务层统一校验状态。执行入口只允许已通过状态，任务只登记结果版本号，
 * 不覆盖已发布版本。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmChgOrderServiceImpl implements PdmChgOrderService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认排序号 */
    private static final int DEFAULT_SORT_NO = 0;

    /** 编号随机后缀上界（四位数） */
    private static final int RANDOM_SUFFIX_BOUND = 10_000;

    /** 变更单编号前缀 */
    private static final String ECO_NO_PREFIX = "EO-";

    /** 编号随机后缀格式 */
    private static final String ECO_NO_RANDOM_FORMAT = "%04d";

    /** 编号时间戳格式 */
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 影响对象序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 未审批执行错误标识 */
    private static final String CHANGE_NOT_APPROVED_MESSAGE =
            "变更单未审批通过，不可执行（CHANGE_NOT_APPROVED）";

    private final PdmChgOrderMapper chgOrderMapper;

    private final PdmChgTaskMapper chgTaskMapper;

    private final PdmChgRequestMapper chgRequestMapper;

    private final PdmChgTraceRecorder traceRecorder;

    private final PdmChgWorkflowClient workflowClient;

    @Override
    public PageResult<PdmChgOrderVO> page(PdmChgOrderQueryRequest request) {
        LambdaQueryWrapper<PdmChgOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getEcoNo()), PdmChgOrder::getEcoNo, request.getEcoNo());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmChgOrder::getStatus, request.getStatus());
        wrapper.eq(request.getEcrId() != null, PdmChgOrder::getEcrId, request.getEcrId());
        wrapper.eq(request.getInitiatorId() != null, PdmChgOrder::getInitiatorId, request.getInitiatorId());
        wrapper.orderByDesc(PdmChgOrder::getCreateTime).orderByDesc(PdmChgOrder::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmChgOrder> page = chgOrderMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmChgOrderVO> records = page.getRecords().stream()
                .map(entity -> toVo(entity, List.of()))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmChgOrderVO detail(Long id) {
        PdmChgOrder entity = requireOrder(id);
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO create(PdmChgOrderSaveRequest request, Long userId) {
        PdmChgRequest ecr = requireApprovedRequest(request.getEcrId());
        PdmChgOrder entity = new PdmChgOrder();
        entity.setEcoNo(newEcoNo());
        entity.setEcrId(ecr.getId());
        entity.setTitle(request.getTitle().trim());
        entity.setImpactAssessment(blankToNull(request.getImpactAssessment()));
        entity.setImpactJson(ecr.getImpactJson());
        entity.setExecutePlan(blankToNull(request.getExecutePlan()));
        entity.setStatus(PdmChgOrderStatus.DRAFT);
        entity.setInitiatorId(userId);
        chgOrderMapper.insert(entity);
        traceRecorder.recordImpact(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.CREATE, userId, entity.getImpactJson(), "创建变更单");
        log.info("变更单已创建: id={}, ecoNo={}, ecrId={}", entity.getId(), entity.getEcoNo(), ecr.getId());
        return toVo(entity, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO update(Long id, PdmChgOrderSaveRequest request) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.DRAFT, PdmChgOrderStatus.ASSESSING);
        entity.setTitle(request.getTitle().trim());
        entity.setImpactAssessment(blankToNull(request.getImpactAssessment()));
        entity.setExecutePlan(blankToNull(request.getExecutePlan()));
        chgOrderMapper.updateById(entity);
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.DRAFT);
        chgTaskMapper.delete(new LambdaQueryWrapper<PdmChgTask>().eq(PdmChgTask::getEcoId, entity.getId()));
        chgOrderMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO assess(Long id, PdmChgOrderAssessRequest request) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.DRAFT);
        if (!StringUtils.hasText(request.getImpactAssessment())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.impact-assessment-empty", "影响评估不能为空");
        }
        entity.setImpactAssessment(request.getImpactAssessment().trim());
        entity.setExecutePlan(blankToNull(request.getExecutePlan()));
        entity.setStatus(PdmChgOrderStatus.ASSESSING);
        chgOrderMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.ASSESS, null, request.getImpactAssessment().trim());
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO addTask(Long id, PdmChgTaskSaveRequest request) {
        PdmChgOrder entity = requireEditable(id);
        PdmChgTask task = new PdmChgTask();
        task.setEcoId(entity.getId());
        applyTask(task, request);
        task.setStatus(PdmChgTaskStatus.PENDING);
        chgTaskMapper.insert(task);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO updateTask(Long id, Long taskId, PdmChgTaskSaveRequest request) {
        PdmChgOrder entity = requireEditable(id);
        PdmChgTask task = requireTask(entity.getId(), taskId);
        applyTask(task, request);
        chgTaskMapper.updateById(task);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO deleteTask(Long id, Long taskId) {
        PdmChgOrder entity = requireEditable(id);
        PdmChgTask task = requireTask(entity.getId(), taskId);
        chgTaskMapper.deleteById(task.getId());
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO submit(Long id) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.ASSESSING);
        if (!StringUtils.hasText(entity.getImpactAssessment())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.impact-assessment-empty-2", "影响评估不能为空");
        }
        long taskCount = chgTaskMapper.selectCount(
                new LambdaQueryWrapper<PdmChgTask>().eq(PdmChgTask::getEcoId, entity.getId()));
        if (taskCount == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-task-empty", "变更任务不能为空");
        }
        entity.setStatus(PdmChgOrderStatus.APPROVING);
        entity.setSubmitTime(LocalDateTime.now());
        startApproval(entity);
        chgOrderMapper.updateById(entity);
        traceRecorder.recordImpact(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.SUBMIT, entity.getInitiatorId(), entity.getImpactJson(), "提交变更单审批");
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO approve(Long id, String comment, Long userId) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.APPROVING);
        entity.setStatus(PdmChgOrderStatus.APPROVED);
        entity.setApproveBy(userId);
        entity.setApproveTime(LocalDateTime.now());
        entity.setApproveComment(blankToNull(comment));
        chgOrderMapper.updateById(entity);
        traceRecorder.recordImpact(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.APPROVE, userId, entity.getImpactJson(), "变更单审批通过");
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO reject(Long id, String comment, Long userId) {
        if (!StringUtils.hasText(comment)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.reject-fill-in-approval-comment", "驳回必须填写审批意见");
        }
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.APPROVING);
        entity.setStatus(PdmChgOrderStatus.REJECTED);
        entity.setApproveBy(userId);
        entity.setApproveTime(LocalDateTime.now());
        entity.setApproveComment(comment.trim());
        chgOrderMapper.updateById(entity);
        traceRecorder.recordImpact(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.REJECT, userId, entity.getImpactJson(), comment.trim());
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO execute(Long id) {
        PdmChgOrder entity = requireOrder(id);
        if (!PdmChgOrderStatus.APPROVED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, CHANGE_NOT_APPROVED_MESSAGE);
        }
        entity.setStatus(PdmChgOrderStatus.EXECUTING);
        entity.setExecuteTime(LocalDateTime.now());
        chgOrderMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.EXECUTE, null, "开始执行变更单");
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO completeTask(Long id, Long taskId, String resultVersionNo) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.EXECUTING);
        PdmChgTask task = requireTask(entity.getId(), taskId);
        if (PdmChgTaskStatus.COMPLETED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-task-complete-cannot-duplicate-complete", "变更任务已完成，不可重复完成");
        }
        task.setStatus(PdmChgTaskStatus.COMPLETED);
        task.setResultVersionNo(PdmVersionNo.parse(resultVersionNo).value());
        task.setCompleteTime(LocalDateTime.now());
        chgTaskMapper.updateById(task);
        traceRecorder.recordObject(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                task.getTargetType(), task.getTargetId(), task.getTargetCode(),
                PdmChgTraceAction.TASK_COMPLETE, null, task.getResultVersionNo(), null,
                "变更任务完成：" + task.getTaskName());
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO complete(Long id) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.EXECUTING);
        long unfinished = chgTaskMapper.selectCount(new LambdaQueryWrapper<PdmChgTask>()
                .eq(PdmChgTask::getEcoId, entity.getId())
                .ne(PdmChgTask::getStatus, PdmChgTaskStatus.COMPLETED));
        if (unfinished > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.exist-complete-change-task-cannot-complete", "存在未完成的变更任务，不能完成变更单");
        }
        entity.setStatus(PdmChgOrderStatus.COMPLETED);
        entity.setCompleteTime(LocalDateTime.now());
        chgOrderMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.COMPLETE, null, "变更单执行完成");
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO cancel(Long id) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.DRAFT, PdmChgOrderStatus.ASSESSING,
                PdmChgOrderStatus.APPROVING, PdmChgOrderStatus.APPROVED);
        entity.setStatus(PdmChgOrderStatus.CANCELLED);
        chgOrderMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.CANCEL, null, "取消变更单");
        return toVo(entity, tasksOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgOrderVO close(Long id) {
        PdmChgOrder entity = requireStatus(id, PdmChgOrderStatus.COMPLETED,
                PdmChgOrderStatus.REJECTED, PdmChgOrderStatus.CANCELLED);
        entity.setStatus(PdmChgOrderStatus.CLOSED);
        entity.setCloseTime(LocalDateTime.now());
        chgOrderMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECO, entity.getId(), entity.getEcoNo(),
                PdmChgTraceAction.CLOSE, null, "关闭变更单");
        return toVo(entity, tasksOf(id));
    }

    /**
     * 尝试发起 OA 审批；不可用时降级人工审批，不阻断提交。
     *
     * @param entity 变更单
     */
    private void startApproval(PdmChgOrder entity) {
        try {
            Long instanceId = workflowClient.start(TenantContext.get(), PdmChgBizType.ECO, entity.getId(),
                    entity.getEcoNo(), "变更单审批 " + entity.getEcoNo(), entity.getInitiatorId());
            if (instanceId == null) {
                entity.setApprovalSource(PdmChgApprovalSource.MANUAL);
                return;
            }
            entity.setApprovalInstanceId(instanceId);
            entity.setApprovalStatus(PdmChgApprovalStatus.RUNNING);
            entity.setApprovalSyncTime(LocalDateTime.now());
            entity.setApprovalSource(PdmChgApprovalSource.OA);
        } catch (Exception ex) {
            log.warn("变更单发起 OA 审批失败，降级人工审批: ecoNo={}, reason={}",
                    entity.getEcoNo(), ex.getMessage());
            entity.setApprovalSource(PdmChgApprovalSource.MANUAL);
        }
    }

    /**
     * 校验来源 ECR 已审批通过。
     *
     * @param ecrId 变更申请ID
     * @return 变更申请实体
     */
    private PdmChgRequest requireApprovedRequest(Long ecrId) {
        if (ecrId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.source-change-application-empty", "来源变更申请不能为空");
        }
        PdmChgRequest ecr = chgRequestMapper.selectById(ecrId);
        if (ecr == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.source-change-application-found", "来源变更申请不存在");
        }
        if (!PdmChgRequestStatus.APPROVED.equals(ecr.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.approval-pass-change-application-can-creation", "仅审批通过的变更申请可创建变更单");
        }
        return ecr;
    }

    /**
     * 查询变更单，不存在报参数错误。
     *
     * @param id 变更单ID
     * @return 变更单实体
     */
    private PdmChgOrder requireOrder(Long id) {
        PdmChgOrder entity = chgOrderMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-order-found", "变更单不存在");
        }
        return entity;
    }

    /**
     * 查询指定状态的变更单。
     *
     * @param id      变更单ID
     * @param allowed 允许的状态
     * @return 变更单实体
     */
    private PdmChgOrder requireStatus(Long id, String... allowed) {
        PdmChgOrder entity = requireOrder(id);
        for (String status : allowed) {
            if (status.equals(entity.getStatus())) {
                return entity;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.status-change-order-allow-operation", "变更单当前状态不允许该操作：{0}", entity.getStatus());
    }

    /**
     * 查询可编辑状态的变更单。
     *
     * @param id 变更单ID
     * @return 变更单实体
     */
    private PdmChgOrder requireEditable(Long id) {
        return requireStatus(id, PdmChgOrderStatus.DRAFT, PdmChgOrderStatus.ASSESSING);
    }

    /**
     * 查询属于指定变更单的任务。
     *
     * @param ecoId  变更单ID
     * @param taskId 任务ID
     * @return 任务实体
     */
    private PdmChgTask requireTask(Long ecoId, Long taskId) {
        PdmChgTask task = chgTaskMapper.selectById(taskId);
        if (task == null || !ecoId.equals(task.getEcoId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-task-found", "变更任务不存在");
        }
        return task;
    }

    /**
     * 任务请求写入实体。
     *
     * @param task    任务实体
     * @param request 任务请求
     */
    private static void applyTask(PdmChgTask task, PdmChgTaskSaveRequest request) {
        task.setTaskName(request.getTaskName().trim());
        task.setTaskType(request.getTaskType().trim());
        task.setTargetType(StringUtils.hasText(request.getTargetType())
                ? PdmChgObjectType.requireValid(request.getTargetType()) : null);
        task.setTargetId(request.getTargetId());
        task.setTargetCode(blankToNull(request.getTargetCode()));
        task.setPlanDesc(blankToNull(request.getPlanDesc()));
        task.setOwnerId(request.getOwnerId());
        task.setSortNo(request.getSortNo() == null ? DEFAULT_SORT_NO : request.getSortNo());
    }

    /**
     * 查询变更单下的任务，按排序号升序。
     *
     * @param ecoId 变更单ID
     * @return 任务列表
     */
    private List<PdmChgTask> tasksOf(Long ecoId) {
        return chgTaskMapper.selectList(new LambdaQueryWrapper<PdmChgTask>()
                .eq(PdmChgTask::getEcoId, ecoId)
                .orderByAsc(PdmChgTask::getSortNo)
                .orderByAsc(PdmChgTask::getId));
    }

    /**
     * 实体转视图。
     *
     * @param entity 变更单实体
     * @param tasks  任务列表
     * @return 变更单视图
     */
    private PdmChgOrderVO toVo(PdmChgOrder entity, List<PdmChgTask> tasks) {
        PdmChgOrderVO vo = new PdmChgOrderVO();
        vo.setId(entity.getId());
        vo.setEcoNo(entity.getEcoNo());
        vo.setEcrId(entity.getEcrId());
        vo.setTitle(entity.getTitle());
        vo.setImpactAssessment(entity.getImpactAssessment());
        vo.setImpactObjects(parseImpact(entity.getImpactJson()));
        vo.setExecutePlan(entity.getExecutePlan());
        vo.setStatus(entity.getStatus());
        vo.setInitiatorId(entity.getInitiatorId());
        vo.setSubmitTime(entity.getSubmitTime());
        vo.setApproveBy(entity.getApproveBy());
        vo.setApproveTime(entity.getApproveTime());
        vo.setApproveComment(entity.getApproveComment());
        vo.setExecuteTime(entity.getExecuteTime());
        vo.setCompleteTime(entity.getCompleteTime());
        vo.setCloseTime(entity.getCloseTime());
        vo.setCreateTime(entity.getCreateTime());
        vo.setApprovalInstanceId(entity.getApprovalInstanceId());
        vo.setApprovalStatus(entity.getApprovalStatus());
        vo.setApprovalSyncTime(entity.getApprovalSyncTime());
        vo.setApprovalSource(entity.getApprovalSource());
        vo.setTasks(tasks.stream().map(PdmChgOrderServiceImpl::toTaskVo).toList());
        return vo;
    }

    /**
     * 任务实体转视图。
     *
     * @param entity 任务实体
     * @return 任务视图
     */
    private static PdmChgTaskVO toTaskVo(PdmChgTask entity) {
        PdmChgTaskVO vo = new PdmChgTaskVO();
        vo.setId(entity.getId());
        vo.setEcoId(entity.getEcoId());
        vo.setTaskName(entity.getTaskName());
        vo.setTaskType(entity.getTaskType());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setTargetCode(entity.getTargetCode());
        vo.setPlanDesc(entity.getPlanDesc());
        vo.setOwnerId(entity.getOwnerId());
        vo.setStatus(entity.getStatus());
        vo.setResultVersionNo(entity.getResultVersionNo());
        vo.setSortNo(entity.getSortNo());
        vo.setCompleteTime(entity.getCompleteTime());
        return vo;
    }

    /**
     * 解析影响对象快照 JSON。
     *
     * @param json JSON 文本
     * @return 影响对象列表
     */
    private List<PdmChgImpactObjectVO> parseImpact(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PdmChgImpactObjectVO.class));
        } catch (Exception ex) {
            log.warn("变更单影响对象解析失败", ex);
            return List.of();
        }
    }

    /**
     * 空白字符串转 null。
     *
     * @param value 原文本
     * @return 去空白文本或 null
     */
    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 生成变更单编号：EO-yyyyMMddHHmmss-四位随机数。
     *
     * @return 变更单编号
     */
    private static String newEcoNo() {
        return ECO_NO_PREFIX + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-"
                + String.format(ECO_NO_RANDOM_FORMAT, ThreadLocalRandom.current().nextInt(RANDOM_SUFFIX_BOUND));
    }
}

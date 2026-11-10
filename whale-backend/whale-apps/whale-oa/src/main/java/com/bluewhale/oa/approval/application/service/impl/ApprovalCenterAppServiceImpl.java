package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalActionCommand;
import com.bluewhale.oa.approval.application.command.ApprovalAddSignCommand;
import com.bluewhale.oa.approval.application.command.ApprovalCenterQuery;
import com.bluewhale.oa.approval.application.command.ApprovalResultCommand;
import com.bluewhale.oa.approval.application.command.ApprovalRollbackCommand;
import com.bluewhale.oa.approval.application.command.ApprovalTransferCommand;
import com.bluewhale.oa.approval.application.command.ApprovalUrgeCommand;
import com.bluewhale.oa.approval.application.event.ApprovalUrgedEvent;
import com.bluewhale.oa.approval.application.service.ApprovalBusinessDetailProvider;
import com.bluewhale.oa.approval.application.service.ApprovalCenterAppService;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalBusinessDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalCcView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceRowView;
import com.bluewhale.oa.approval.application.view.ApprovalTaskView;
import com.bluewhale.oa.approval.application.view.ApprovalTimelineTaskView;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.WorkflowCcQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowCcSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskSnapshot;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批中心应用服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCenterAppServiceImpl implements ApprovalCenterAppService {

    /** Workflow 状态：运行中 */
    private static final int STATUS_RUNNING = 0;

    /** Workflow 状态：已通过 */
    private static final int STATUS_APPROVED = 1;

    /** Workflow 状态：已驳回 */
    private static final int STATUS_REJECTED = 2;

    /** Workflow 状态：已撤销 */
    private static final int STATUS_CANCELLED = 3;

    /** 动作：同意 */
    private static final String ACTION_APPROVE = "APPROVE";

    /** 动作：驳回 */
    private static final String ACTION_REJECT = "REJECT";

    /** 抄送明细单页上限 */
    private static final long CC_PAGE_SIZE = 200L;

    /** 默认分页条数 */
    private static final long DEFAULT_PAGE_SIZE = 10L;

    private final WorkflowApprovalGateway workflowApprovalGateway;

    private final ApprovalInstanceRepository approvalInstanceRepository;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    private final ObjectProvider<ApprovalBusinessDetailProvider> businessDetailProviders;

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public PageResult<ApprovalInstanceRowView> pageInitiated(ApprovalActor actor, ApprovalCenterQuery query) {
        requireActor(actor);
        WorkflowInstanceQuery instanceQuery = toInstanceQuery(query);
        instanceQuery.setInitiator(actor.getUsername());
        PageResult<WorkflowInstanceSnapshot> page = workflowApprovalGateway.pageInstances(instanceQuery);
        Map<Long, ApprovalInstance> localMap = loadLocalInstances(page.getRecords().stream()
                .map(WorkflowInstanceSnapshot::getId).toList());
        List<ApprovalInstanceRowView> rows = page.getRecords().stream()
                .map(instance -> toRowView(instance, localMap.get(instance.getId())))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<ApprovalTaskView> pagePending(ApprovalActor actor, ApprovalCenterQuery query) {
        requireActor(actor);
        WorkflowTaskQuery taskQuery = toTaskQuery(query);
        taskQuery.setAssignee(actor.getUsername());
        taskQuery.setStatus(STATUS_RUNNING);
        return pageTasks(taskQuery, actor);
    }

    @Override
    public PageResult<ApprovalTaskView> pageApproved(ApprovalActor actor, ApprovalCenterQuery query) {
        requireActor(actor);
        WorkflowTaskQuery taskQuery = toTaskQuery(query);
        taskQuery.setHandled(Boolean.TRUE);
        taskQuery.setActorAccount(actor.getUsername());
        taskQuery.setActorId(actor.getUserId());
        return pageTasks(taskQuery, actor);
    }

    @Override
    public PageResult<ApprovalCcView> pageCc(ApprovalActor actor, ApprovalCenterQuery query) {
        requireActor(actor);
        WorkflowCcQuery ccQuery = new WorkflowCcQuery();
        ccQuery.setRecipientCandidates(recipientCandidates(actor));
        applyPage(ccQuery, query);
        PageResult<WorkflowCcSnapshot> page = workflowApprovalGateway.pageCcs(ccQuery);
        Map<Long, ApprovalInstance> localMap = loadLocalInstances(page.getRecords().stream()
                .map(WorkflowCcSnapshot::getInstanceId).toList());
        List<ApprovalCcView> rows = page.getRecords().stream()
                .map(cc -> toCcView(cc, localMap.get(cc.getInstanceId())))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ApprovalDetailView detail(Long workflowInstanceId, ApprovalActor actor) {
        return buildDetail(workflowInstanceId, actor, false);
    }

    @Override
    public PageResult<ApprovalInstanceRowView> pageMonitor(ApprovalCenterQuery query) {
        WorkflowInstanceQuery instanceQuery = toInstanceQuery(query);
        PageResult<WorkflowInstanceSnapshot> page = workflowApprovalGateway.pageInstances(instanceQuery);
        Map<Long, ApprovalInstance> localMap = loadLocalInstances(page.getRecords().stream()
                .map(WorkflowInstanceSnapshot::getId).toList());
        List<ApprovalInstanceRowView> rows = page.getRecords().stream()
                .map(instance -> toRowView(instance, localMap.get(instance.getId())))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ApprovalDetailView monitorDetail(Long workflowInstanceId) {
        return buildDetail(workflowInstanceId, null, true);
    }

    @Override
    public void approve(Long workflowInstanceId, Long taskId, ApprovalActionCommand command, ApprovalActor actor) {
        complete(workflowInstanceId, taskId, command, actor, ACTION_APPROVE);
    }

    @Override
    public void reject(Long workflowInstanceId, Long taskId, ApprovalActionCommand command, ApprovalActor actor) {
        complete(workflowInstanceId, taskId, command, actor, ACTION_REJECT);
    }

    @Override
    public void transfer(Long workflowInstanceId, Long taskId, ApprovalTransferCommand command, ApprovalActor actor) {
        WorkflowTaskSnapshot task = requireRunningOwnTask(workflowInstanceId, taskId, actor);
        String target = command.getTargetUser().trim();
        if (Objects.equals(target, actor.getUsername())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_RULE_INVALID, "转办目标不能是本人");
        }
        workflowApprovalGateway.transferTask(task.getId(), target);
    }

    @Override
    public void addSign(Long workflowInstanceId, Long taskId, ApprovalAddSignCommand command, ApprovalActor actor) {
        WorkflowTaskSnapshot task = requireRunningOwnTask(workflowInstanceId, taskId, actor);
        List<String> assignees = command.getAssignees().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (assignees.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_ACTION_FORBIDDEN, "加签账号不能为空");
        }
        workflowApprovalGateway.addSign(task.getId(), assignees);
    }

    @Override
    public void rollback(Long workflowInstanceId, Long taskId, ApprovalRollbackCommand command, ApprovalActor actor) {
        WorkflowTaskSnapshot task = requireRunningOwnTask(workflowInstanceId, taskId, actor);
        workflowApprovalGateway.rollbackTask(task.getId(), command.getTargetNodeKey().trim());
    }

    @Override
    public void urge(Long workflowInstanceId, ApprovalUrgeCommand command, ApprovalActor actor) {
        WorkflowInstanceSnapshot instance = requireInstance(workflowInstanceId);
        requireInitiator(instance, actor);
        if (!isRunning(instance)) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_STATUS_CONFLICT, "审批已结束，不能催办");
        }
        workflowApprovalGateway.urge(workflowInstanceId, actor.getUsername(),
                command.getTargetAccount(), command.getComment());
        applicationEventPublisher.publishEvent(new ApprovalUrgedEvent(workflowInstanceId,
                command.getTargetAccount(), command.getComment(), actor.getUsername()));
    }

    @Override
    public void cancel(Long workflowInstanceId, ApprovalActor actor) {
        WorkflowInstanceSnapshot instance = requireInstance(workflowInstanceId);
        requireInitiator(instance, actor);
        if (Objects.equals(instance.getStatus(), STATUS_CANCELLED)) {
            return;
        }
        if (!isRunning(instance)) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_STATUS_CONFLICT, "审批已结束，不能撤销");
        }
        workflowApprovalGateway.cancelInstance(workflowInstanceId);
        syncLocalStatus(workflowInstanceId, STATUS_CANCELLED);
    }

    @Override
    public void markCcRead(Long ccId, ApprovalActor actor) {
        requireActor(actor);
        WorkflowCcSnapshot cc = workflowApprovalGateway.getCc(ccId);
        if (!recipientCandidates(actor).contains(cc.getRecipient())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_CC_NOT_FOUND);
        }
        workflowApprovalGateway.markCcRead(ccId);
    }

    /**
     * 完成同意/驳回动作（含归属校验与幂等）。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @param actor 当前用户
     * @param action 动作
     */
    private void complete(Long workflowInstanceId, Long taskId, ApprovalActionCommand command,
                          ApprovalActor actor, String action) {
        WorkflowInstanceSnapshot instance = requireInstance(workflowInstanceId);
        WorkflowTaskSnapshot task = requireTask(instance, taskId);
        requireOwner(task, actor);
        Integer expected = ACTION_APPROVE.equals(action) ? STATUS_APPROVED : STATUS_REJECTED;
        if (!Objects.equals(task.getStatus(), STATUS_RUNNING)) {
            if (Objects.equals(task.getStatus(), expected)) {
                return;
            }
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_STATUS_CONFLICT, "任务已处理，不能重复操作");
        }
        workflowApprovalGateway.completeTask(taskId, action, command.getComment(), command.getVariables(),
                actor.getUserId() == null ? null : String.valueOf(actor.getUserId()), actor.getUsername());
        refreshLocal(workflowInstanceId);
    }

    /**
     * 组装审批详情。
     *
     * @param workflowInstanceId 流程实例ID
     * @param actor 当前用户，监控模式可为空
     * @param monitor 是否监控模式
     * @return 详情
     */
    private ApprovalDetailView buildDetail(Long workflowInstanceId, ApprovalActor actor, boolean monitor) {
        WorkflowInstanceSnapshot instance = requireInstance(workflowInstanceId);
        List<WorkflowCcSnapshot> ccs = loadCcs(workflowInstanceId);
        if (!monitor) {
            requireActor(actor);
            if (!isParticipant(instance, ccs, actor)) {
                throw OaErrorCode.exception(OaErrorCode.APPROVAL_INSTANCE_NOT_FOUND);
            }
        }
        ApprovalInstance local = approvalInstanceRepository.findByWorkflowInstanceId(workflowInstanceId).orElse(null);
        ApprovalDetailView view = new ApprovalDetailView();
        view.setInstance(toRowView(instance, local));
        view.setTasks(toTimeline(instance.getTasks(), actor));
        view.setCcs(ccs.stream().map(cc -> toCcView(cc, local)).toList());
        view.setBusinessDetail(resolveBusinessDetail(local));
        String username = actor == null ? null : actor.getUsername();
        boolean initiator = StringUtils.hasText(username) && Objects.equals(username, instance.getInitiator());
        view.setCanCancel(initiator && isRunning(instance));
        view.setCanUrge(initiator && isRunning(instance));
        return view;
    }

    /**
     * 分页查询任务并补充业务信息。
     *
     * @param query 任务查询
     * @param actor 当前用户
     * @return 任务分页
     */
    private PageResult<ApprovalTaskView> pageTasks(WorkflowTaskQuery query, ApprovalActor actor) {
        PageResult<WorkflowTaskSnapshot> page = workflowApprovalGateway.pageTasks(query);
        Map<Long, ApprovalInstance> localMap = loadLocalInstances(page.getRecords().stream()
                .map(WorkflowTaskSnapshot::getInstanceId).toList());
        List<ApprovalTaskView> rows = page.getRecords().stream()
                .map(task -> toTaskView(task, localMap.get(task.getInstanceId()), actor))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 校验任务是否存在。
     *
     * @param instance 流程实例
     * @param taskId 任务ID
     * @return 任务
     */
    private WorkflowTaskSnapshot requireTask(WorkflowInstanceSnapshot instance, Long taskId) {
        if (instance.getTasks() == null) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_TASK_NOT_FOUND);
        }
        return instance.getTasks().stream().filter(task -> Objects.equals(task.getId(), taskId)).findFirst()
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_TASK_NOT_FOUND));
    }

    /**
     * 校验任务归属当前用户。
     *
     * @param task 任务
     * @param actor 当前用户
     */
    private void requireOwner(WorkflowTaskSnapshot task, ApprovalActor actor) {
        requireActor(actor);
        if (!Objects.equals(task.getAssignee(), actor.getUsername())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_ACTION_FORBIDDEN);
        }
    }

    /**
     * 校验待办任务归属当前用户。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param actor 当前用户
     * @return 任务
     */
    private WorkflowTaskSnapshot requireRunningOwnTask(Long workflowInstanceId, Long taskId, ApprovalActor actor) {
        WorkflowTaskSnapshot task = requireTask(requireInstance(workflowInstanceId), taskId);
        requireOwner(task, actor);
        if (!Objects.equals(task.getStatus(), STATUS_RUNNING)) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_STATUS_CONFLICT, "任务已处理，不能重复操作");
        }
        return task;
    }

    /**
     * 校验当前用户是发起人。
     *
     * @param instance 流程实例
     * @param actor 当前用户
     */
    private void requireInitiator(WorkflowInstanceSnapshot instance, ApprovalActor actor) {
        requireActor(actor);
        if (!Objects.equals(instance.getInitiator(), actor.getUsername())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_ACTION_FORBIDDEN);
        }
    }

    /**
     * 校验当前用户上下文完整。
     *
     * @param actor 当前用户
     */
    private void requireActor(ApprovalActor actor) {
        if (actor == null || !StringUtils.hasText(actor.getUsername())) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_ACTION_FORBIDDEN);
        }
    }

    /**
     * 判断当前用户是否在审批参与者范围内。
     *
     * @param instance 流程实例
     * @param ccs 抄送记录
     * @param actor 当前用户
     * @return 参与返回 {@code true}
     */
    private boolean isParticipant(WorkflowInstanceSnapshot instance, List<WorkflowCcSnapshot> ccs, ApprovalActor actor) {
        if (Objects.equals(instance.getInitiator(), actor.getUsername())) {
            return true;
        }
        if (instance.getTasks() != null && instance.getTasks().stream().anyMatch(task ->
                Objects.equals(task.getAssignee(), actor.getUsername())
                        || Objects.equals(task.getOwnerId(), actor.getUsername())
                        || Objects.equals(task.getDelegateFrom(), actor.getUsername()))) {
            return true;
        }
        Set<String> candidates = new LinkedHashSet<>(recipientCandidates(actor));
        return ccs.stream().anyMatch(cc -> candidates.contains(cc.getRecipient()));
    }

    /**
     * 查询流程实例。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 流程实例
     */
    private WorkflowInstanceSnapshot requireInstance(Long workflowInstanceId) {
        if (workflowInstanceId == null) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_INSTANCE_NOT_FOUND);
        }
        return workflowApprovalGateway.getInstance(workflowInstanceId);
    }

    /**
     * 查询实例全部抄送（单页上限 200）。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 抄送记录
     */
    private List<WorkflowCcSnapshot> loadCcs(Long workflowInstanceId) {
        WorkflowCcQuery query = new WorkflowCcQuery();
        query.setInstanceId(workflowInstanceId);
        query.setCurrent(1L);
        query.setSize(CC_PAGE_SIZE);
        return workflowApprovalGateway.pageCcs(query).getRecords();
    }

    /**
     * 批量加载本地审批实例。
     *
     * @param workflowInstanceIds 流程实例ID集合
     * @return 以流程实例ID为键的本地实例
     */
    private Map<Long, ApprovalInstance> loadLocalInstances(List<Long> workflowInstanceIds) {
        List<Long> ids = workflowInstanceIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return approvalInstanceRepository.findByWorkflowInstanceIds(ids).stream()
                .collect(Collectors.toMap(ApprovalInstance::getWorkflowInstanceId, Function.identity(),
                        (left, right) -> left));
    }

    /**
     * 动作成功后刷新本地实例终态（失败仅告警）。
     *
     * @param workflowInstanceId 流程实例ID
     */
    private void refreshLocal(Long workflowInstanceId) {
        try {
            WorkflowInstanceSnapshot instance = workflowApprovalGateway.getInstance(workflowInstanceId);
            syncLocalStatus(workflowInstanceId, instance.getStatus());
        } catch (RuntimeException exception) {
            log.warn("刷新审批实例状态失败, workflowInstanceId={}: {}", workflowInstanceId, exception.getMessage());
        }
    }

    /**
     * 同步本地审批实例终态（幂等，仅 RUNNING 迁移）。
     *
     * @param workflowInstanceId 流程实例ID
     * @param workflowStatus Workflow 状态
     */
    private void syncLocalStatus(Long workflowInstanceId, Integer workflowStatus) {
        String target = localStatusName(workflowStatus);
        if (!StringUtils.hasText(target)) {
            return;
        }
        approvalInstanceRepository.findByWorkflowInstanceId(workflowInstanceId).ifPresent(local -> {
            if (!local.running()) {
                return;
            }
            ApprovalResultCommand command = new ApprovalResultCommand();
            command.setStatus(target);
            approvalInstanceAppService.syncResult(local.getId(), command);
        });
    }

    /**
     * 映射 Workflow 状态到本地状态名。
     *
     * @param workflowStatus Workflow 状态
     * @return 本地状态名；运行中返回 {@code null}
     */
    private String localStatusName(Integer workflowStatus) {
        if (workflowStatus == null) {
            return null;
        }
        return switch (workflowStatus) {
            case STATUS_APPROVED -> "APPROVED";
            case STATUS_REJECTED -> "REJECTED";
            case STATUS_CANCELLED -> "CANCELLED";
            default -> null;
        };
    }

    /**
     * 解析业务详情。
     *
     * @param local 本地审批实例
     * @return 业务详情或 {@code null}
     */
    private ApprovalBusinessDetailView resolveBusinessDetail(ApprovalInstance local) {
        if (local == null || local.getBusinessId() == null || !StringUtils.hasText(local.getBusinessType())) {
            return null;
        }
        return businessDetailProviders.orderedStream()
                .filter(provider -> provider.supports(local.getBusinessType()))
                .findFirst()
                .map(provider -> provider.detail(local.getBusinessType(), local.getBusinessId()))
                .orElse(null);
    }

    /**
     * 构造抄送收件人候选（账号 + 用户ID文本）。
     *
     * @param actor 当前用户
     * @return 候选列表
     */
    private List<String> recipientCandidates(ApprovalActor actor) {
        List<String> candidates = new ArrayList<>(2);
        if (StringUtils.hasText(actor.getUsername())) {
            candidates.add(actor.getUsername());
        }
        if (actor.getUserId() != null) {
            candidates.add(String.valueOf(actor.getUserId()));
        }
        return candidates;
    }

    /**
     * 映射查询条件到流程实例查询。
     *
     * @param query 审批中心查询
     * @return 流程实例查询
     */
    private WorkflowInstanceQuery toInstanceQuery(ApprovalCenterQuery query) {
        WorkflowInstanceQuery instanceQuery = new WorkflowInstanceQuery();
        instanceQuery.setTitle(query.getKeyword());
        instanceQuery.setStatus(query.getStatus());
        instanceQuery.setDefinitionCode(query.getDefinitionCode());
        instanceQuery.setCurrent(Math.max(1L, query.getCurrent()));
        instanceQuery.setSize(pageSize(query.getSize()));
        return instanceQuery;
    }

    /**
     * 映射查询条件到任务查询。
     *
     * @param query 审批中心查询
     * @return 任务查询
     */
    private WorkflowTaskQuery toTaskQuery(ApprovalCenterQuery query) {
        WorkflowTaskQuery taskQuery = new WorkflowTaskQuery();
        taskQuery.setStatus(query.getStatus());
        taskQuery.setCurrent(Math.max(1L, query.getCurrent()));
        taskQuery.setSize(pageSize(query.getSize()));
        return taskQuery;
    }

    /**
     * 写入分页参数。
     *
     * @param target 抄送查询
     * @param query 审批中心查询
     */
    private void applyPage(WorkflowCcQuery target, ApprovalCenterQuery query) {
        target.setCurrent(Math.max(1L, query.getCurrent()));
        target.setSize(pageSize(query.getSize()));
    }

    /**
     * 规范化分页条数。
     *
     * @param size 原始条数
     * @return 1~200
     */
    private long pageSize(long size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, 200L);
    }

    /**
     * 判断实例是否运行中。
     *
     * @param instance 流程实例
     * @return 运行中返回 {@code true}
     */
    private boolean isRunning(WorkflowInstanceSnapshot instance) {
        return Objects.equals(instance.getStatus(), STATUS_RUNNING);
    }

    /**
     * 映射实例行视图。
     *
     * @param instance 流程实例
     * @param local 本地审批实例
     * @return 行视图
     */
    private ApprovalInstanceRowView toRowView(WorkflowInstanceSnapshot instance, ApprovalInstance local) {
        ApprovalInstanceRowView view = new ApprovalInstanceRowView();
        view.setWorkflowInstanceId(instance.getId());
        view.setDefinitionCode(instance.getDefinitionCode());
        view.setTitle(instance.getTitle());
        view.setStatus(instance.getStatus());
        view.setStatusName(statusName(instance.getStatus()));
        view.setCurrentNodeKey(instance.getCurrentNodeKey());
        view.setInitiator(instance.getInitiator());
        view.setCreateTime(instance.getCreateTime());
        view.setUpdateTime(instance.getUpdateTime());
        if (local != null) {
            view.setBusinessType(local.getBusinessType());
            view.setBusinessTitle(local.getTitle());
            view.setBusinessCode(local.getBusinessCode());
            view.setBusinessUrl(local.getBusinessUrl());
        }
        return view;
    }

    /**
     * 映射任务视图。
     *
     * @param task 任务
     * @param local 本地审批实例
     * @param actor 当前用户
     * @return 任务视图
     */
    private ApprovalTaskView toTaskView(WorkflowTaskSnapshot task, ApprovalInstance local, ApprovalActor actor) {
        ApprovalTaskView view = new ApprovalTaskView();
        view.setId(task.getId());
        view.setInstanceId(task.getInstanceId());
        view.setNodeKey(task.getNodeKey());
        view.setNodeName(task.getNodeName());
        view.setAssignee(task.getAssignee());
        view.setOwnerId(task.getOwnerId());
        view.setStatus(task.getStatus());
        view.setComment(task.getComment());
        view.setCreateTime(task.getCreateTime());
        view.setUpdateTime(task.getUpdateTime());
        view.setCanOperate(Objects.equals(task.getStatus(), STATUS_RUNNING)
                && Objects.equals(task.getAssignee(), actor.getUsername()));
        if (local != null) {
            view.setBusinessType(local.getBusinessType());
            view.setBusinessTitle(local.getTitle());
            view.setBusinessCode(local.getBusinessCode());
            view.setBusinessUrl(local.getBusinessUrl());
            view.setInstanceStatus(local.getStatus() == null ? null : local.getStatus().name());
            view.setInitiatorName(local.getInitiatorName());
        }
        return view;
    }

    /**
     * 映射抄送视图。
     *
     * @param cc 抄送记录
     * @param local 本地审批实例
     * @return 抄送视图
     */
    private ApprovalCcView toCcView(WorkflowCcSnapshot cc, ApprovalInstance local) {
        ApprovalCcView view = new ApprovalCcView();
        view.setId(cc.getId());
        view.setInstanceId(cc.getInstanceId());
        view.setNodeKey(cc.getNodeKey());
        view.setNodeName(cc.getNodeName());
        view.setReadFlag(cc.getReadFlag());
        view.setReadTime(cc.getReadTime());
        view.setCreateTime(cc.getCreateTime());
        if (local != null) {
            view.setBusinessType(local.getBusinessType());
            view.setBusinessTitle(local.getTitle());
            view.setBusinessCode(local.getBusinessCode());
            view.setBusinessUrl(local.getBusinessUrl());
            view.setInstanceStatus(local.getStatus() == null ? null : local.getStatus().name());
            view.setInitiatorName(local.getInitiatorName());
        }
        return view;
    }

    /**
     * 映射时间线任务。
     *
     * @param tasks 任务列表
     * @param actor 当前用户
     * @return 时间线
     */
    private List<ApprovalTimelineTaskView> toTimeline(List<WorkflowTaskSnapshot> tasks, ApprovalActor actor) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        String username = actor == null ? null : actor.getUsername();
        return tasks.stream().map(task -> {
            ApprovalTimelineTaskView view = new ApprovalTimelineTaskView();
            view.setId(task.getId());
            view.setNodeKey(task.getNodeKey());
            view.setNodeName(task.getNodeName());
            view.setAssignee(task.getAssignee());
            view.setOwnerId(task.getOwnerId());
            view.setDelegateFrom(task.getDelegateFrom());
            view.setStatus(task.getStatus());
            view.setComment(task.getComment());
            view.setCreateTime(task.getCreateTime());
            view.setUpdateTime(task.getUpdateTime());
            view.setCanOperate(Objects.equals(task.getStatus(), STATUS_RUNNING)
                    && Objects.equals(task.getAssignee(), username));
            return view;
        }).toList();
    }

    /**
     * 映射状态名称。
     *
     * @param status Workflow 状态
     * @return 状态名称
     */
    private String statusName(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case STATUS_RUNNING -> "RUNNING";
            case STATUS_APPROVED -> "APPROVED";
            case STATUS_REJECTED -> "REJECTED";
            case STATUS_CANCELLED -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }
}

package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.request.TaskRejectRequest;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ProcessPaths;
import com.bluewhale.workflow.support.RejectTarget;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.support.machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 驳回（退回）命令：按目标把流程退回发起人或上游审批节点。
 *
 * <p>与旧 {@link RejectCommand}（实例终止）并存：公开驳回走本命令，内网回调/内部接口仍走终止语义。
 * 退回后实例保持运行中，驳回任务保留「已驳回」痕迹，其余待办取消，由目标处理人重新处理。</p>
 *
 * <p>会签节点由策略自动决定是否退回：</p>
 * <ul>
 *   <li>{@code ALL}：一人驳回即退回；</li>
 *   <li>{@code ANY}：已有人同意或仍有待办时继续等待，全部驳回才退回；</li>
 *   <li>{@code RATIO:n}：无法达到阈值才退回，否则继续等待。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnCommand {

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessTaskMapper processTaskMapper;

    private final TaskCommandSupport taskCommandSupport;

    private final InstanceModelLoader instanceModelLoader;

    private final TaskActivationCommand taskActivationCommand;

    private final CounterSignCommand counterSignCommand;

    /**
     * 驳回任务：退回发起人或上游审批节点。
     *
     * @param id 任务ID
     * @param request 驳回请求（目标必填）
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(Long id, TaskRejectRequest request) {
        if (request == null || request.getTarget() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择驳回目标");
        }
        ProcessTaskPo task = taskCommandSupport.requirePendingTask(id);
        ProcessInstancePo instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        if (WorkflowConstants.RESUBMIT_NODE_KEY.equals(task.getNodeKey())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "发起节点不支持驳回");
        }
        ProcessModel model = ProcessModelParser.parse(instanceModelLoader.resolveInstanceJson(instance));
        String nodeKey = resolveCurrentKey(task, model, instance);
        ProcessModelNode node = model.node(nodeKey);
        if (node == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前节点不存在，无法驳回");
        }
        boolean counterSign = node.getType() == ProcessNodeType.COUNTER_SIGN;
        // 非会签节点先校验目标，避免在无效目标上留下「已驳回」痕迹
        ReturnPlan plan = counterSign ? null : resolvePlan(model, nodeKey, request);
        task.setStatus(WorkflowConstants.TASK_REJECTED);
        task.setComment(request.getComment());
        if (!taskCommandSupport.transitionFromPending(task, WorkflowConstants.TASK_REJECTED, request.getComment())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务已处理");
        }
        if (counterSign) {
            if (!counterSignCommand.shouldReturnOnReject(instance.getId(), nodeKey, node)) {
                log.info("会签驳回继续等待: instanceId={}, nodeKey={}, taskId={}",
                        instance.getId(), nodeKey, task.getId());
                return;
            }
            plan = nearestPreviousPlan(model, nodeKey);
            log.info("会签驳回触发退回: instanceId={}, nodeKey={}, toInitiator={}",
                    instance.getId(), nodeKey, plan.toInitiator());
        }
        execute(instance, model, plan);
        log.info("任务已驳回并退回: instanceId={}, taskId={}, targetNode={}",
                instance.getId(), task.getId(),
                plan.toInitiator() ? WorkflowConstants.RESUBMIT_NODE_KEY : plan.node().getKey());
    }

    /**
     * 非会签节点的退回目标解析与校验。
     *
     * @param model 流程模型
     * @param nodeKey 当前节点键
     * @param request 驳回请求
     * @return 退回计划
     */
    private ReturnPlan resolvePlan(ProcessModel model, String nodeKey, TaskRejectRequest request) {
        RejectTarget target = request.getTarget();
        if (target == RejectTarget.INITIATOR) {
            return ReturnPlan.ofInitiator();
        }
        if (target == RejectTarget.PREVIOUS) {
            List<ProcessModelNode> previous = ProcessPaths.nearestPreviousApprovalNodes(model, nodeKey);
            if (previous.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "首节点不支持驳回到上一节点");
            }
            if (previous.size() > 1) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上一节点不唯一，请选择指定节点");
            }
            return ReturnPlan.ofNode(previous.get(0));
        }
        return ReturnPlan.ofNode(requireAncestor(model, nodeKey, request.getTargetNodeKey()));
    }

    /**
     * 会签失败后的自动退回目标：唯一上一层审批节点，否则退回发起人。
     *
     * @param model 流程模型
     * @param nodeKey 会签节点键
     * @return 退回计划
     */
    private static ReturnPlan nearestPreviousPlan(ProcessModel model, String nodeKey) {
        List<ProcessModelNode> previous = ProcessPaths.nearestPreviousApprovalNodes(model, nodeKey);
        return previous.size() == 1 ? ReturnPlan.ofNode(previous.get(0)) : ReturnPlan.ofInitiator();
    }

    /**
     * 校验指定节点：必须存在、为审批节点、且是当前节点的上游节点。
     *
     * @param model 流程模型
     * @param nodeKey 当前节点键
     * @param targetNodeKey 指定节点键
     * @return 目标节点
     */
    private static ProcessModelNode requireAncestor(ProcessModel model, String nodeKey, String targetNodeKey) {
        if (!StringUtils.hasText(targetNodeKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择驳回节点");
        }
        ProcessModelNode target = model.node(targetNodeKey.trim());
        if (target == null || target.getType() == null || !target.getType().isApproval()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "驳回目标必须为审批节点");
        }
        if (target.getKey().equals(nodeKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不能驳回到当前节点");
        }
        boolean ancestor = ProcessPaths.ancestorApprovalNodes(model, nodeKey).stream()
                .anyMatch(item -> item.getKey().equals(target.getKey()));
        if (!ancestor) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "只能驳回到当前节点的上游审批节点");
        }
        return target;
    }

    /**
     * 执行退回：取消全部待办并把当前节点指向目标。
     *
     * @param instance 流程实例
     * @param model 流程模型
     * @param plan 退回计划
     */
    private void execute(ProcessInstancePo instance, ProcessModel model, ReturnPlan plan) {
        taskCommandSupport.cancelPendingTasks(instance.getId());
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setActiveNodeKeys(null);
        if (plan.toInitiator()) {
            returnToInitiator(instance, model);
            return;
        }
        ProcessModelNode target = plan.node();
        instance.setCurrentIndex(model.indexOf(target.getKey()));
        instance.setCurrentNodeKey(target.getKey());
        processInstanceMapper.updateById(instance);
        taskActivationCommand.activate(instance, model, TransitionResult.at(List.of(target)));
    }

    /**
     * 退回发起人：实例停在入口，生成一条「重新提交」待办。
     *
     * @param instance 流程实例
     * @param model 流程模型
     */
    private void returnToInitiator(ProcessInstancePo instance, ProcessModel model) {
        String initiator = instance.getInitiator() == null ? "" : instance.getInitiator().trim();
        if (initiator.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程实例缺少发起人，无法退回");
        }
        String entryKey = model.entryKey();
        instance.setCurrentIndex(model.indexOf(entryKey));
        instance.setCurrentNodeKey(entryKey);
        processInstanceMapper.updateById(instance);
        ProcessTaskPo resubmit = new ProcessTaskPo();
        resubmit.setInstanceId(instance.getId());
        resubmit.setNodeIndex(model.indexOf(entryKey));
        resubmit.setNodeKey(WorkflowConstants.RESUBMIT_NODE_KEY);
        resubmit.setNodeName(WorkflowConstants.RESUBMIT_NODE_NAME);
        resubmit.setAssignee(initiator);
        resubmit.setOwnerId(initiator);
        resubmit.setStatus(WorkflowConstants.TASK_PENDING);
        processTaskMapper.insert(resubmit);
    }

    /**
     * 解析任务对应的节点键：优先任务快照，其次实例当前节点，最后回落下标。
     *
     * @param task 待办任务
     * @param model 流程模型
     * @param instance 流程实例
     * @return 节点键；无法解析返回 {@code null}
     */
    private static String resolveCurrentKey(ProcessTaskPo task, ProcessModel model, ProcessInstancePo instance) {
        if (StringUtils.hasText(task.getNodeKey())) {
            return task.getNodeKey();
        }
        if (StringUtils.hasText(instance.getCurrentNodeKey())) {
            return instance.getCurrentNodeKey();
        }
        int index = instance.getCurrentIndex() == null ? 0 : instance.getCurrentIndex();
        ProcessModelNode node = model.nodeAt(index);
        return node == null ? null : node.getKey();
    }

    /**
     * 退回计划：退回发起人或指定节点。
     *
     * @param toInitiator 是否退回发起人
     * @param node 目标审批节点（退回发起人时为空）
     */
    private record ReturnPlan(boolean toInitiator, ProcessModelNode node) {

        /**
         * 退回发起人。
         *
         * @return 计划
         */
        static ReturnPlan ofInitiator() {
            return new ReturnPlan(true, null);
        }

        /**
         * 退回指定审批节点。
         *
         * @param node 目标审批节点
         * @return 计划
         */
        static ReturnPlan ofNode(ProcessModelNode node) {
            return new ReturnPlan(false, node);
        }
    }
}

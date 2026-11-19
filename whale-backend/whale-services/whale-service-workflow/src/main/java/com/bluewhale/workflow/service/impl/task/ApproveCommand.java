package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.request.ApprovalRequest;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessVariables;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 同意任务命令：更新任务状态 → 会签策略判定 → 并行分支收敛 → 推进状态机。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ApproveCommand {

    private final ProcessInstanceMapper processInstanceMapper;

    private final TaskCommandSupport taskCommandSupport;

    private final InstanceModelLoader instanceModelLoader;

    private final CounterSignCommand counterSignCommand;

    private final TaskActivationCommand taskActivationCommand;

    /**
     * 同意任务并推进流程。
     *
     * @param id 任务ID
     * @param request 审批请求，可为 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, ApprovalRequest request) {
        String comment = request == null ? null : request.getComment();
        Map<String, Object> variables = request == null ? null : request.getVariables();
        ProcessTaskPo task = taskCommandSupport.requirePendingTask(id);
        ProcessInstancePo instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        task.setStatus(WorkflowConstants.TASK_APPROVED);
        task.setComment(comment);
        if (!taskCommandSupport.transitionFromPending(task, WorkflowConstants.TASK_APPROVED, comment)) {
            // 并发场景下状态已被其它请求推进，避免重复推进状态机
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.task.already-processed", "任务已处理");
        }
        ProcessModel model = ProcessModelParser.parse(instanceModelLoader.resolveInstanceJson(instance));
        Map<String, Object> merged = ProcessVariables.merge(instance.getVariablesJson(), variables);
        boolean variablesChanged = variables != null && !variables.isEmpty();
        if (variablesChanged) {
            instance.setVariablesJson(ProcessVariables.write(merged));
        }
        if (WorkflowConstants.RESUBMIT_NODE_KEY.equals(task.getNodeKey())) {
            // 发起人重新提交：不审批入口节点，直接从入口推进到首个审批节点
            TransitionResult result = new StateMachine(model, merged).advance(model.entryKey());
            applyResult(instance, model, result);
            return;
        }
        String nodeKey = resolveCurrentKey(task, model, instance);
        ProcessModelNode node = model.node(nodeKey);
        if (counterSignCommand.awaitSameNode(instance.getId(), nodeKey, node)) {
            processInstanceMapper.updateById(instance);
            return;
        }
        // 并行分支可能同时审批：以旧的活动节点集合为条件更新，避免互相覆盖
        String remaining = taskCommandSupport.removeActiveKey(instance, nodeKey);
        if (!remaining.isEmpty()) {
            if (variablesChanged) {
                processInstanceMapper.updateById(instance);
            }
            return;
        }
        TransitionResult result = new StateMachine(model, merged).approve(nodeKey);
        applyResult(instance, model, result);
    }

    /**
     * 解析任务对应的节点键：优先任务快照 {@code nodeKey}，其次实例 {@code current_node_key}，
     * 最后回落 {@code currentIndex} 下标（旧行）。
     *
     * @param task 待办任务
     * @param model 流程模型
     * @param instance 流程实例
     * @return 当前节点键，无法解析返回 {@code null}
     */
    private static String resolveCurrentKey(ProcessTaskPo task, ProcessModel model, ProcessInstancePo instance) {
        if (task.getNodeKey() != null && !task.getNodeKey().isBlank()) {
            return task.getNodeKey();
        }
        return resolveCurrentKey(model, instance);
    }

    /**
     * 解析实例当前节点键：优先 {@code current_node_key}，为空时回落 {@code currentIndex} 下标（旧行）。
     *
     * @param model 流程模型
     * @param instance 流程实例
     * @return 当前节点键，无法解析返回 {@code null}
     */
    private static String resolveCurrentKey(ProcessModel model, ProcessInstancePo instance) {
        if (instance.getCurrentNodeKey() != null && !instance.getCurrentNodeKey().isBlank()) {
            return instance.getCurrentNodeKey();
        }
        int index = instance.getCurrentIndex() == null ? 0 : instance.getCurrentIndex();
        ProcessModelNode node = model.nodeAt(index);
        return node == null ? null : node.getKey();
    }

    /**
     * 应用状态机推进结果：停在审批节点则更新当前节点并建任务，否则实例置为已通过。
     *
     * @param instance 流程实例
     * @param model 流程模型
     * @param result 迁移结果
     */
    private void applyResult(ProcessInstancePo instance, ProcessModel model, TransitionResult result) {
        if (result.isUserTask()) {
            int nextIndex = model.indexOf(result.getState());
            instance.setCurrentIndex(nextIndex);
            instance.setCurrentNodeKey(result.getState());
            instance.setActiveNodeKeys(result.getNodes().size() > 1 ? joinKeys(result.getNodes()) : null);
            processInstanceMapper.updateById(instance);
            taskActivationCommand.activate(instance, model, result);
            return;
        }
        instance.setStatus(WorkflowConstants.INSTANCE_APPROVED);
        instance.setActiveNodeKeys(null);
        instance.setFinishedAt(LocalDateTime.now());
        if (result.getState() != null) {
            instance.setCurrentNodeKey(result.getState());
        }
        processInstanceMapper.updateById(instance);
    }

    /**
     * 拼接并行活动节点键（逗号分隔）。
     *
     * @param nodes 审批节点列表
     * @return 逗号分隔的节点键
     */
    private static String joinKeys(List<ProcessModelNode> nodes) {
        return nodes.stream().map(ProcessModelNode::getKey)
                .collect(java.util.stream.Collectors.joining(","));
    }
}

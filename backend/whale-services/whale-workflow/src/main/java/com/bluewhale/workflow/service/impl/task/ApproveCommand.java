package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.workflow.dto.ApprovalRequest;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessVariables;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final ProcessTaskMapper processTaskMapper;

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
        ProcessTask task = taskCommandSupport.requirePendingTask(id);
        ProcessInstance instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        task.setStatus(TaskConstants.TASK_APPROVED);
        task.setComment(comment);
        processTaskMapper.updateById(task);
        ProcessModel model = ProcessModelParser.parse(instanceModelLoader.resolveInstanceJson(instance));
        Map<String, Object> merged = ProcessVariables.merge(instance.getVariablesJson(), variables);
        if (variables != null && !variables.isEmpty()) {
            instance.setVariablesJson(ProcessVariables.write(merged));
        }
        String nodeKey = resolveCurrentKey(task, model, instance);
        ProcessModelNode node = model.node(nodeKey);
        if (counterSignCommand.awaitSameNode(instance.getId(), nodeKey, node)) {
            processInstanceMapper.updateById(instance);
            return;
        }
        String remaining = removeActiveKey(instance.getActiveNodeKeys(), nodeKey);
        if (!remaining.isEmpty()) {
            instance.setActiveNodeKeys(remaining);
            processInstanceMapper.updateById(instance);
            return;
        }
        if (instance.getActiveNodeKeys() != null && !instance.getActiveNodeKeys().isBlank()) {
            instance.setActiveNodeKeys(null);
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
    private static String resolveCurrentKey(ProcessTask task, ProcessModel model, ProcessInstance instance) {
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
    private static String resolveCurrentKey(ProcessModel model, ProcessInstance instance) {
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
    private void applyResult(ProcessInstance instance, ProcessModel model, TransitionResult result) {
        if (result.isUserTask()) {
            int nextIndex = model.indexOf(result.getState());
            instance.setCurrentIndex(nextIndex);
            instance.setCurrentNodeKey(result.getState());
            instance.setActiveNodeKeys(result.getNodes().size() > 1 ? joinKeys(result.getNodes()) : null);
            processInstanceMapper.updateById(instance);
            taskActivationCommand.activate(instance, model, result);
            return;
        }
        instance.setStatus(TaskConstants.INSTANCE_APPROVED);
        instance.setActiveNodeKeys(null);
        if (result.getState() != null) {
            instance.setCurrentNodeKey(result.getState());
        }
        processInstanceMapper.updateById(instance);
    }

    /**
     * 从并行活动节点键串中移除指定节点键。
     *
     * @param activeNodeKeys 逗号分隔的活动节点键，可为 {@code null}
     * @param nodeKey 待移除节点键
     * @return 剩余键串（无剩余返回空串）
     */
    private static String removeActiveKey(String activeNodeKeys, String nodeKey) {
        if (activeNodeKeys == null || activeNodeKeys.isBlank()) {
            return "";
        }
        StringBuilder remaining = new StringBuilder();
        for (String part : activeNodeKeys.split(",")) {
            String key = part.trim();
            if (key.isEmpty() || key.equals(nodeKey)) {
                continue;
            }
            if (remaining.length() > 0) {
                remaining.append(',');
            }
            remaining.append(key);
        }
        return remaining.toString();
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

package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessPaths;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.support.machine.TransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 回退命令：取消当前待办并在指定审批节点重建待办。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class RollbackCommand {

    private final ProcessInstanceMapper processInstanceMapper;

    private final TaskCommandSupport taskCommandSupport;

    private final TaskActivationCommand taskActivationCommand;

    private final InstanceModelLoader instanceModelLoader;

    /**
     * 回退到指定审批节点：取消当前待办，在目标节点重建待办。
     *
     * @param id 任务ID
     * @param targetNodeKey 目标节点键（必须为审批类节点）
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, String targetNodeKey) {
        ProcessTaskPo task = taskCommandSupport.requirePendingTask(id);
        ProcessInstancePo instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        ProcessModel model = ProcessModelParser.parse(instanceModelLoader.resolveInstanceJson(instance));
        ProcessModelNode target = model.node(targetNodeKey);
        if (target == null || target.getType() == null || !target.getType().isApproval()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.task.rollback-target-not-approval",
                    "回退目标必须为审批节点");
        }
        String currentKey = resolveCurrentKey(task, model, instance);
        if (target.getKey().equals(currentKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.task.rollback-to-current", "不能回退到当前节点");
        }
        boolean ancestor = ProcessPaths.ancestorApprovalNodes(model, currentKey).stream()
                .anyMatch(item -> item.getKey().equals(target.getKey()));
        if (!ancestor) {
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.task.rollback-not-upstream",
                    "只能回退到当前节点的上游审批节点");
        }
        taskCommandSupport.cancelPendingTasks(instance.getId());
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setCurrentIndex(model.indexOf(target.getKey()));
        instance.setCurrentNodeKey(target.getKey());
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        taskActivationCommand.activate(instance, model, TransitionResult.at(List.of(target)));
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
        if (task.getNodeKey() != null && !task.getNodeKey().isBlank()) {
            return task.getNodeKey();
        }
        if (instance.getCurrentNodeKey() != null && !instance.getCurrentNodeKey().isBlank()) {
            return instance.getCurrentNodeKey();
        }
        int index = instance.getCurrentIndex() == null ? 0 : instance.getCurrentIndex();
        ProcessModelNode node = model.nodeAt(index);
        return node == null ? null : node.getKey();
    }
}

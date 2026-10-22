package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
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

    private final InstanceModelLoader instanceModelLoader;

    /**
     * 回退到指定审批节点：取消当前待办，在目标节点重建待办。
     *
     * @param id 任务ID
     * @param targetNodeKey 目标节点键（必须为审批类节点）
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, String targetNodeKey) {
        ProcessTask task = taskCommandSupport.requirePendingTask(id);
        ProcessInstance instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        ProcessModel model = ProcessModelParser.parse(instanceModelLoader.resolveInstanceJson(instance));
        ProcessModelNode target = model.node(targetNodeKey);
        if (target == null || target.getType() == null || !target.getType().isApproval()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "回退目标必须为审批节点");
        }
        taskCommandSupport.cancelPendingTasks(instance.getId());
        instance.setStatus(TaskConstants.INSTANCE_RUNNING);
        instance.setCurrentIndex(model.indexOf(target.getKey()));
        instance.setCurrentNodeKey(target.getKey());
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        taskCommandSupport.createTasks(instance.getId(), model, List.of(target));
    }
}

package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.support.WorkflowConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 驳回任务命令：任务与实例置为驳回态，并取消同实例其余待办。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class RejectCommand {

    private final ProcessInstanceMapper processInstanceMapper;

    private final TaskCommandSupport taskCommandSupport;

    /**
     * 驳回任务：实例「已驳回」，同实例其余待办置「已取消」。
     *
     * @param id 任务ID
     * @param comment 审批意见，可为 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String comment) {
        ProcessTaskPo task = taskCommandSupport.requirePendingTask(id);
        ProcessInstancePo instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        task.setStatus(WorkflowConstants.TASK_REJECTED);
        task.setComment(comment);
        if (!taskCommandSupport.transitionFromPending(task, WorkflowConstants.TASK_REJECTED, comment)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务已处理");
        }
        instance.setStatus(WorkflowConstants.INSTANCE_REJECTED);
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        taskCommandSupport.cancelPendingTasks(instance.getId());
    }
}

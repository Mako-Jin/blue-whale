package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
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

    private final ProcessTaskMapper processTaskMapper;

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
        ProcessTask task = taskCommandSupport.requirePendingTask(id);
        ProcessInstance instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        task.setStatus(TaskConstants.TASK_REJECTED);
        task.setComment(comment);
        processTaskMapper.updateById(task);
        instance.setStatus(TaskConstants.INSTANCE_REJECTED);
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        taskCommandSupport.cancelPendingTasks(instance.getId());
    }
}

package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.WorkflowConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 待办任务命令层共享支撑：任务/实例校验与待办取消。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TaskCommandSupport {

    private final ProcessTaskMapper processTaskMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    /**
     * 校验并返回待办任务：不存在抛 {@code NOT_FOUND}，已处理抛参数错误。
     *
     * @param id 任务ID
     * @return 待办任务实体
     */
    public ProcessTask requirePendingTask(Long id) {
        ProcessTask task = processTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (task.getStatus() == null || task.getStatus() != WorkflowConstants.TASK_PENDING) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务已处理");
        }
        return task;
    }

    /**
     * 校验并返回运行中的流程实例：不存在抛 {@code NOT_FOUND}，非运行中抛参数错误。
     *
     * @param instanceId 流程实例ID
     * @return 流程实例实体
     */
    public ProcessInstance requireRunningInstance(Long instanceId) {
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (instance.getStatus() == null || instance.getStatus() != WorkflowConstants.INSTANCE_RUNNING) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "实例非运行中");
        }
        return instance;
    }

    /**
     * 以「仍为待办」为条件推进任务状态（CAS）：并发请求下仅有一个调用方成功。
     *
     * @param task 待推进的任务（需已校验为待办）
     * @param targetStatus 目标状态
     * @param comment 审批意见，可为 {@code null}
     * @return 推进成功返回 {@code true}；状态已被并发修改返回 {@code false}
     */
    public boolean transitionFromPending(ProcessTask task, int targetStatus, String comment) {
        ProcessTask update = new ProcessTask();
        update.setStatus(targetStatus);
        update.setComment(comment);
        LambdaUpdateWrapper<ProcessTask> wrapper = new LambdaUpdateWrapper<ProcessTask>()
                .eq(ProcessTask::getId, task.getId())
                .eq(ProcessTask::getStatus, WorkflowConstants.TASK_PENDING);
        return processTaskMapper.update(update, wrapper) > 0;
    }

    /**
     * 将实例下所有待办任务置为「已取消」。
     *
     * @param instanceId 流程实例ID
     */
    public void cancelPendingTasks(Long instanceId) {
        ProcessTask update = new ProcessTask();
        update.setStatus(WorkflowConstants.TASK_CANCELED);
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getStatus, WorkflowConstants.TASK_PENDING);
        processTaskMapper.update(update, wrapper);
    }
}

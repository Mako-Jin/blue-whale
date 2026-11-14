package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.WorkflowConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 待办任务命令层共享支撑：任务/实例校验与待办取消。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TaskCommandSupport {

    /** 活动节点 CAS 重试次数（含首次） */
    private static final int CAS_RETRY_TIMES = 2;

    private final ProcessTaskMapper processTaskMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    /**
     * 校验并返回待办任务：不存在抛 {@code NOT_FOUND}，已处理抛参数错误。
     *
     * @param id 任务ID
     * @return 待办任务实体
     */
    public ProcessTaskPo requirePendingTask(Long id) {
        ProcessTaskPo task = processTaskMapper.selectById(id);
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
    public ProcessInstancePo requireRunningInstance(Long instanceId) {
        ProcessInstancePo instance = processInstanceMapper.selectById(instanceId);
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
    public boolean transitionFromPending(ProcessTaskPo task, int targetStatus, String comment) {
        ProcessTaskPo update = new ProcessTaskPo();
        update.setStatus(targetStatus);
        update.setComment(comment);
        LambdaUpdateWrapper<ProcessTaskPo> wrapper = new LambdaUpdateWrapper<ProcessTaskPo>()
                .eq(ProcessTaskPo::getId, task.getId())
                .eq(ProcessTaskPo::getStatus, WorkflowConstants.TASK_PENDING);
        return processTaskMapper.update(update, wrapper) > 0;
    }

    /**
     * 以「活动节点集合仍为读取时的值」为条件移除指定节点键（CAS）。
     *
     * <p>并行分支同时审批时，两个事务可能读到相同的 {@code active_node_keys}；
     * 本方法用旧值作为 WHERE 条件，失败后重读实例重试一次，避免后写覆盖先写。</p>
     *
     * @param instance 运行中的实例（读取时的快照，方法内会更新其活动节点字段）
     * @param nodeKey  要移除的节点键
     * @return 移除后剩余的活动节点键（逗号分隔；空串表示已无并行分支）
     * @throws BizException 重试后仍被并发修改
     */
    public String removeActiveKey(ProcessInstancePo instance, String nodeKey) {
        if (instance.getActiveNodeKeys() == null) {
            // 单待办实例无并行分支，无需条件更新
            return "";
        }
        for (int attempt = 0; attempt < CAS_RETRY_TIMES; attempt++) {
            String expected = instance.getActiveNodeKeys();
            String remaining = removeKey(expected, nodeKey);
            LambdaUpdateWrapper<ProcessInstancePo> wrapper = new LambdaUpdateWrapper<ProcessInstancePo>()
                    .set(ProcessInstancePo::getActiveNodeKeys, remaining.isEmpty() ? null : remaining)
                    .eq(ProcessInstancePo::getId, instance.getId())
                    .eq(ProcessInstancePo::getActiveNodeKeys, expected);
            if (processInstanceMapper.update(null, wrapper) > 0) {
                instance.setActiveNodeKeys(remaining.isEmpty() ? null : remaining);
                return remaining;
            }
            ProcessInstancePo latest = requireRunningInstance(instance.getId());
            instance.setActiveNodeKeys(latest.getActiveNodeKeys());
        }
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程实例状态已变更，请重试");
    }

    /**
     * 从逗号分隔的活动节点键中移除指定键
     *
     * @param keys    活动节点键（可空）
     * @param nodeKey 要移除的节点键
     * @return 剩余节点键（逗号分隔；无剩余返回空串）
     */
    private static String removeKey(String keys, String nodeKey) {
        if (keys == null || keys.isBlank()) {
            return "";
        }
        return Arrays.stream(keys.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty() && !key.equals(nodeKey))
                .collect(Collectors.joining(","));
    }

    /**
     * 将实例下所有待办任务置为「已取消」。
     *
     * @param instanceId 流程实例ID
     */
    public void cancelPendingTasks(Long instanceId) {
        ProcessTaskPo update = new ProcessTaskPo();
        update.setStatus(WorkflowConstants.TASK_CANCELED);
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, instanceId);
        wrapper.eq(ProcessTaskPo::getStatus, WorkflowConstants.TASK_PENDING);
        processTaskMapper.update(update, wrapper);
    }
}

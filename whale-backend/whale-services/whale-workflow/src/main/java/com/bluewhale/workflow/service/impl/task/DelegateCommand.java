package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 委派命令：把任务临时交给目标人处理，并由原审批人完成确认。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DelegateCommand {

    private final ProcessTaskMapper processTaskMapper;

    private final TaskCommandSupport taskCommandSupport;

    /**
     * 委派任务：任务临时交给目标人处理，原审批人记入 {@code delegateFrom}。
     *
     * @param id 任务ID
     * @param targetUser 目标人账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void delegate(Long id, String targetUser) {
        ProcessTask task = taskCommandSupport.requirePendingTask(id);
        String target = requireTargetUser(targetUser);
        if (task.getDelegateFrom() == null || task.getDelegateFrom().isBlank()) {
            task.setDelegateFrom(task.getAssignee());
        }
        task.setAssignee(target);
        processTaskMapper.updateById(task);
    }

    /**
     * 委派处理完成：当前任务置「已取消」并向原审批人新建待办。
     *
     * @param id 任务ID
     * @param comment 处理意见，可为 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long id, String comment) {
        ProcessTask task = taskCommandSupport.requirePendingTask(id);
        String origin = task.getDelegateFrom();
        if (origin == null || origin.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务不是委派任务");
        }
        task.setStatus(TaskConstants.TASK_CANCELED);
        task.setComment(comment);
        processTaskMapper.updateById(task);
        ProcessTask restored = new ProcessTask();
        restored.setInstanceId(task.getInstanceId());
        restored.setNodeIndex(task.getNodeIndex());
        restored.setNodeName(task.getNodeName());
        restored.setNodeKey(task.getNodeKey());
        restored.setFormId(task.getFormId());
        restored.setFormCode(task.getFormCode());
        restored.setParentNodeKey(task.getParentNodeKey());
        restored.setSignType(task.getSignType());
        restored.setSignPolicy(task.getSignPolicy());
        restored.setOwnerId(task.getOwnerId());
        restored.setAssignee(origin);
        restored.setStatus(TaskConstants.TASK_PENDING);
        processTaskMapper.insert(restored);
    }

    /**
     * 校验委派目标人。
     *
     * @param targetUser 目标人账号
     * @return 去空白后的目标人
     * @throws BizException 为空时抛出 {@code PARAM_ERROR}
     */
    private static String requireTargetUser(String targetUser) {
        if (targetUser == null || targetUser.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "目标人不能为空");
        }
        return targetUser.trim();
    }
}

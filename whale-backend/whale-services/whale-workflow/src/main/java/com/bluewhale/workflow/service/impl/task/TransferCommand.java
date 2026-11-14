package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 转办命令：任务审批人改为目标人，原始责任人保留在 {@code ownerId}。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TransferCommand {

    private final ProcessTaskMapper processTaskMapper;

    private final TaskCommandSupport taskCommandSupport;

    /**
     * 转办任务。
     *
     * @param id 任务ID
     * @param targetUser 目标人账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, String targetUser) {
        ProcessTask task = taskCommandSupport.requirePendingTask(id);
        String target = requireTargetUser(targetUser);
        if (task.getOwnerId() == null || task.getOwnerId().isBlank()) {
            task.setOwnerId(task.getAssignee());
        }
        task.setAssignee(target);
        processTaskMapper.updateById(task);
    }

    /**
     * 校验转办目标人。
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

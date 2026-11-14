package com.bluewhale.workflow.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ApprovalRequest;
import com.bluewhale.workflow.dto.ProcessTaskQueryRequest;
import com.bluewhale.workflow.dto.TaskCompleteRequest;
import com.bluewhale.workflow.service.ProcessTaskService;
import com.bluewhale.workflow.service.impl.task.AddSignCommand;
import com.bluewhale.workflow.service.impl.task.ApproveCommand;
import com.bluewhale.workflow.service.impl.task.CompleteCommand;
import com.bluewhale.workflow.service.impl.task.DelegateCommand;
import com.bluewhale.workflow.service.impl.task.RejectCommand;
import com.bluewhale.workflow.service.impl.task.RollbackCommand;
import com.bluewhale.workflow.service.impl.task.TaskQueryCommand;
import com.bluewhale.workflow.service.impl.task.TransferCommand;
import com.bluewhale.workflow.support.WorkflowMetrics;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 待办任务服务实现：编排/委派门面，各动作由 {@code service.impl.task} 下的命令类承载。
 *
 * <p>接口方法签名与语义保持不变；事务边界由各命令方法上的
 * {@code @Transactional} 声明，经 Spring 代理生效（门面与命令为不同 Bean，不存在同类自调用）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessTaskServiceImpl implements ProcessTaskService {

    private final TaskQueryCommand taskQueryCommand;

    private final ApproveCommand approveCommand;

    private final RejectCommand rejectCommand;

    private final TransferCommand transferCommand;

    private final DelegateCommand delegateCommand;

    private final RollbackCommand rollbackCommand;

    private final AddSignCommand addSignCommand;

    private final CompleteCommand completeCommand;

    private final WorkflowMetrics workflowMetrics;

    @Override
    public PageResult<ProcessTaskVO> page(ProcessTaskQueryRequest request) {
        return taskQueryCommand.page(request);
    }

    @Override
    public void approve(Long id, ApprovalRequest request) {
        approveCommand.approve(id, request);
        recordAction(WorkflowMetrics.TASK_APPROVE, id);
    }

    @Override
    public void reject(Long id, String comment) {
        rejectCommand.reject(id, comment);
        recordAction(WorkflowMetrics.TASK_REJECT, id);
    }

    @Override
    public void transfer(Long id, String targetUser) {
        transferCommand.transfer(id, targetUser);
        recordAction(WorkflowMetrics.TASK_TRANSFER, id);
    }

    @Override
    public void delegate(Long id, String targetUser) {
        delegateCommand.delegate(id, targetUser);
        recordAction(WorkflowMetrics.TASK_DELEGATE, id);
    }

    @Override
    public void resolve(Long id, String comment) {
        delegateCommand.resolve(id, comment);
        recordAction(WorkflowMetrics.TASK_RESOLVE, id);
    }

    @Override
    public void rollback(Long id, String targetNodeKey) {
        rollbackCommand.rollback(id, targetNodeKey);
        recordAction(WorkflowMetrics.TASK_ROLLBACK, id);
    }

    @Override
    public void addSign(Long id, List<String> assignees) {
        addSignCommand.addSign(id, assignees);
        recordAction(WorkflowMetrics.TASK_ADD_SIGN, id);
    }

    @Override
    public void completeInternal(Long id, TaskCompleteRequest request) {
        completeCommand.complete(id, request);
        recordAction(WorkflowMetrics.TASK_COMPLETE, id);
    }

    /**
     * 记录任务动作指标与日志（仅在命令执行成功后调用）。
     *
     * @param action 动作
     * @param taskId 任务ID
     */
    private void recordAction(String action, Long taskId) {
        workflowMetrics.recordTaskAction(action);
        log.info("待办任务动作完成: action={}, taskId={}", action, taskId);
    }
}

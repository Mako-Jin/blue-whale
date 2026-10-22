package com.bluewhale.workflow.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ApprovalRequest;
import com.bluewhale.workflow.dto.ProcessTaskQueryRequest;
import com.bluewhale.workflow.service.ProcessTaskService;
import com.bluewhale.workflow.service.impl.task.AddSignCommand;
import com.bluewhale.workflow.service.impl.task.ApproveCommand;
import com.bluewhale.workflow.service.impl.task.DelegateCommand;
import com.bluewhale.workflow.service.impl.task.RejectCommand;
import com.bluewhale.workflow.service.impl.task.RollbackCommand;
import com.bluewhale.workflow.service.impl.task.TaskQueryCommand;
import com.bluewhale.workflow.service.impl.task.TransferCommand;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
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

    @Override
    public PageResult<ProcessTaskVO> page(ProcessTaskQueryRequest request) {
        return taskQueryCommand.page(request);
    }

    @Override
    public void approve(Long id, ApprovalRequest request) {
        approveCommand.approve(id, request);
    }

    @Override
    public void reject(Long id, String comment) {
        rejectCommand.reject(id, comment);
    }

    @Override
    public void transfer(Long id, String targetUser) {
        transferCommand.transfer(id, targetUser);
    }

    @Override
    public void delegate(Long id, String targetUser) {
        delegateCommand.delegate(id, targetUser);
    }

    @Override
    public void resolve(Long id, String comment) {
        delegateCommand.resolve(id, comment);
    }

    @Override
    public void rollback(Long id, String targetNodeKey) {
        rollbackCommand.rollback(id, targetNodeKey);
    }

    @Override
    public void addSign(Long id, List<String> assignees) {
        addSignCommand.addSign(id, assignees);
    }
}

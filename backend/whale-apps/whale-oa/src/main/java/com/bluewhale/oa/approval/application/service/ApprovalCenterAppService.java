package com.bluewhale.oa.approval.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalActionCommand;
import com.bluewhale.oa.approval.application.command.ApprovalAddSignCommand;
import com.bluewhale.oa.approval.application.command.ApprovalCenterQuery;
import com.bluewhale.oa.approval.application.command.ApprovalRollbackCommand;
import com.bluewhale.oa.approval.application.command.ApprovalTransferCommand;
import com.bluewhale.oa.approval.application.command.ApprovalUrgeCommand;
import com.bluewhale.oa.approval.application.view.ApprovalCcView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceRowView;
import com.bluewhale.oa.approval.application.view.ApprovalTaskView;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;

/**
 * 审批中心应用服务。
 *
 * @author BlueWhale
 */
public interface ApprovalCenterAppService {

    /**
     * 我发起的审批。
     *
     * @param actor 当前用户
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ApprovalInstanceRowView> pageInitiated(ApprovalActor actor, ApprovalCenterQuery query);

    /**
     * 待我审批。
     *
     * @param actor 当前用户
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ApprovalTaskView> pagePending(ApprovalActor actor, ApprovalCenterQuery query);

    /**
     * 我已审批。
     *
     * @param actor 当前用户
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ApprovalTaskView> pageApproved(ApprovalActor actor, ApprovalCenterQuery query);

    /**
     * 抄送我的。
     *
     * @param actor 当前用户
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ApprovalCcView> pageCc(ApprovalActor actor, ApprovalCenterQuery query);

    /**
     * 审批详情。
     *
     * @param workflowInstanceId 流程实例ID
     * @param actor 当前用户
     * @return 详情
     */
    ApprovalDetailView detail(Long workflowInstanceId, ApprovalActor actor);

    /**
     * 同意任务。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @param actor 当前用户
     */
    void approve(Long workflowInstanceId, Long taskId, ApprovalActionCommand command, ApprovalActor actor);

    /**
     * 驳回任务。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @param actor 当前用户
     */
    void reject(Long workflowInstanceId, Long taskId, ApprovalActionCommand command, ApprovalActor actor);

    /**
     * 转办任务。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @param actor 当前用户
     */
    void transfer(Long workflowInstanceId, Long taskId, ApprovalTransferCommand command, ApprovalActor actor);

    /**
     * 加签。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @param actor 当前用户
     */
    void addSign(Long workflowInstanceId, Long taskId, ApprovalAddSignCommand command, ApprovalActor actor);

    /**
     * 退回。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @param actor 当前用户
     */
    void rollback(Long workflowInstanceId, Long taskId, ApprovalRollbackCommand command, ApprovalActor actor);

    /**
     * 催办。
     *
     * @param workflowInstanceId 流程实例ID
     * @param command 命令
     * @param actor 当前用户
     */
    void urge(Long workflowInstanceId, ApprovalUrgeCommand command, ApprovalActor actor);

    /**
     * 撤销。
     *
     * @param workflowInstanceId 流程实例ID
     * @param actor 当前用户
     */
    void cancel(Long workflowInstanceId, ApprovalActor actor);

    /**
     * 标记抄送已读。
     *
     * @param ccId 抄送记录ID
     * @param actor 当前用户
     */
    void markCcRead(Long ccId, ApprovalActor actor);

    /**
     * 流程监控分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ApprovalInstanceRowView> pageMonitor(ApprovalCenterQuery query);

    /**
     * 流程监控详情（只读）。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 详情
     */
    ApprovalDetailView monitorDetail(Long workflowInstanceId);
}

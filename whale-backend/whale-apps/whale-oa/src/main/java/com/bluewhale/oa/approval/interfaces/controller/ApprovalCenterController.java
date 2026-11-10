package com.bluewhale.oa.approval.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.approval.application.command.ApprovalActionCommand;
import com.bluewhale.oa.approval.application.command.ApprovalAddSignCommand;
import com.bluewhale.oa.approval.application.command.ApprovalCenterQuery;
import com.bluewhale.oa.approval.application.command.ApprovalRollbackCommand;
import com.bluewhale.oa.approval.application.command.ApprovalTransferCommand;
import com.bluewhale.oa.approval.application.command.ApprovalUrgeCommand;
import com.bluewhale.oa.approval.application.service.ApprovalCenterAppService;
import com.bluewhale.oa.approval.application.view.ApprovalCcView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceRowView;
import com.bluewhale.oa.approval.application.view.ApprovalTaskView;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 审批中心接口。
 *
 * <p>普通员工只持有 OA 审批权限，Workflow 调用与数据范围校验全部由 OA 后端完成。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/approval/center")
@RequiredArgsConstructor
public class ApprovalCenterController {

    /** 审计对象：审批任务 */
    private static final String TASK_TARGET = "oa_approval_task";

    /** 审计对象：审批实例 */
    private static final String INSTANCE_TARGET = "oa_approval_instance";

    /** 审计对象：抄送 */
    private static final String CC_TARGET = "oa_approval_cc";

    private final ApprovalCenterAppService approvalCenterAppService;

    /**
     * 我发起的审批分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/initiated/page")
    @RequirePermission("sys:oa:approval:list")
    public Result<PageResult<ApprovalInstanceRowView>> pageInitiated(ApprovalCenterQuery query) {
        return Result.ok(approvalCenterAppService.pageInitiated(currentActor(), query));
    }

    /**
     * 待我审批分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/pending/page")
    @RequirePermission("sys:oa:approval:list")
    public Result<PageResult<ApprovalTaskView>> pagePending(ApprovalCenterQuery query) {
        return Result.ok(approvalCenterAppService.pagePending(currentActor(), query));
    }

    /**
     * 我已审批分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/approved/page")
    @RequirePermission("sys:oa:approval:list")
    public Result<PageResult<ApprovalTaskView>> pageApproved(ApprovalCenterQuery query) {
        return Result.ok(approvalCenterAppService.pageApproved(currentActor(), query));
    }

    /**
     * 抄送我的分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/cc/page")
    @RequirePermission("sys:oa:approval:list")
    public Result<PageResult<ApprovalCcView>> pageCc(ApprovalCenterQuery query) {
        return Result.ok(approvalCenterAppService.pageCc(currentActor(), query));
    }

    /**
     * 审批详情。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 详情
     */
    @GetMapping("/instance/{workflowInstanceId}")
    @RequirePermission("sys:oa:approval:view")
    public Result<ApprovalDetailView> detail(@PathVariable Long workflowInstanceId) {
        return Result.ok(approvalCenterAppService.detail(workflowInstanceId, currentActor()));
    }

    /**
     * 同意任务。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @return 空结果
     */
    @PostMapping("/instance/{workflowInstanceId}/task/{taskId}/approve")
    @RequirePermission("sys:oa:approval:action")
    @AuditLog(operation = "同意审批任务", type = OperateType.UPDATE, targetType = TASK_TARGET, targetId = "#taskId")
    public Result<Void> approve(@PathVariable Long workflowInstanceId, @PathVariable Long taskId,
                                @Valid @RequestBody ApprovalActionCommand command) {
        approvalCenterAppService.approve(workflowInstanceId, taskId, command, currentActor());
        return Result.ok();
    }

    /**
     * 驳回任务。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @return 空结果
     */
    @PostMapping("/instance/{workflowInstanceId}/task/{taskId}/reject")
    @RequirePermission("sys:oa:approval:action")
    @AuditLog(operation = "驳回审批任务", type = OperateType.UPDATE, targetType = TASK_TARGET, targetId = "#taskId")
    public Result<Void> reject(@PathVariable Long workflowInstanceId, @PathVariable Long taskId,
                               @Valid @RequestBody ApprovalActionCommand command) {
        approvalCenterAppService.reject(workflowInstanceId, taskId, command, currentActor());
        return Result.ok();
    }

    /**
     * 转办任务。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @return 空结果
     */
    @PostMapping("/instance/{workflowInstanceId}/task/{taskId}/transfer")
    @RequirePermission("sys:oa:approval:action")
    @AuditLog(operation = "转办审批任务", type = OperateType.UPDATE, targetType = TASK_TARGET, targetId = "#taskId")
    public Result<Void> transfer(@PathVariable Long workflowInstanceId, @PathVariable Long taskId,
                                 @Valid @RequestBody ApprovalTransferCommand command) {
        approvalCenterAppService.transfer(workflowInstanceId, taskId, command, currentActor());
        return Result.ok();
    }

    /**
     * 加签。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @return 空结果
     */
    @PostMapping("/instance/{workflowInstanceId}/task/{taskId}/add-sign")
    @RequirePermission("sys:oa:approval:action")
    @AuditLog(operation = "审批加签", type = OperateType.UPDATE, targetType = TASK_TARGET, targetId = "#taskId")
    public Result<Void> addSign(@PathVariable Long workflowInstanceId, @PathVariable Long taskId,
                                @Valid @RequestBody ApprovalAddSignCommand command) {
        approvalCenterAppService.addSign(workflowInstanceId, taskId, command, currentActor());
        return Result.ok();
    }

    /**
     * 退回任务。
     *
     * @param workflowInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param command 命令
     * @return 空结果
     */
    @PostMapping("/instance/{workflowInstanceId}/task/{taskId}/rollback")
    @RequirePermission("sys:oa:approval:action")
    @AuditLog(operation = "退回审批任务", type = OperateType.UPDATE, targetType = TASK_TARGET, targetId = "#taskId")
    public Result<Void> rollback(@PathVariable Long workflowInstanceId, @PathVariable Long taskId,
                                 @Valid @RequestBody ApprovalRollbackCommand command) {
        approvalCenterAppService.rollback(workflowInstanceId, taskId, command, currentActor());
        return Result.ok();
    }

    /**
     * 催办实例。
     *
     * @param workflowInstanceId 流程实例ID
     * @param command 命令
     * @return 空结果
     */
    @PostMapping("/instance/{workflowInstanceId}/urge")
    @RequirePermission("sys:oa:approval:urge")
    @AuditLog(operation = "催办审批实例", type = OperateType.UPDATE, targetType = INSTANCE_TARGET,
            targetId = "#workflowInstanceId")
    public Result<Void> urge(@PathVariable Long workflowInstanceId, @Valid @RequestBody ApprovalUrgeCommand command) {
        approvalCenterAppService.urge(workflowInstanceId, command, currentActor());
        return Result.ok();
    }

    /**
     * 撤销实例。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 空结果
     */
    @PostMapping("/instance/{workflowInstanceId}/cancel")
    @RequirePermission("sys:oa:approval:cancel")
    @AuditLog(operation = "撤销审批实例", type = OperateType.UPDATE, targetType = INSTANCE_TARGET,
            targetId = "#workflowInstanceId")
    public Result<Void> cancel(@PathVariable Long workflowInstanceId) {
        approvalCenterAppService.cancel(workflowInstanceId, currentActor());
        return Result.ok();
    }

    /**
     * 标记抄送已读。
     *
     * @param ccId 抄送记录ID
     * @return 空结果
     */
    @PostMapping("/cc/{ccId}/read")
    @RequirePermission("sys:oa:approval:list")
    @AuditLog(operation = "标记抄送已读", type = OperateType.UPDATE, targetType = CC_TARGET, targetId = "#ccId")
    public Result<Void> markCcRead(@PathVariable Long ccId) {
        approvalCenterAppService.markCcRead(ccId, currentActor());
        return Result.ok();
    }

    /**
     * 构造当前用户上下文。
     *
     * @return 当前用户
     */
    private ApprovalActor currentActor() {
        return new ApprovalActor(RequestContextHolder.getUserId(), RequestContextHolder.getUsername());
    }
}

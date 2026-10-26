package com.bluewhale.workflow.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.dto.ApprovalRequest;
import com.bluewhale.workflow.dto.ProcessTaskQueryRequest;
import com.bluewhale.workflow.dto.TaskAddSignRequest;
import com.bluewhale.workflow.dto.TaskRollbackRequest;
import com.bluewhale.workflow.dto.TaskTransferRequest;
import com.bluewhale.workflow.service.ProcessTaskService;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办任务管理接口。
 *
 * <p>服务内路径前缀 {@code /workflow/task}；经网关按 {@code Path=/workflow/**} 规则转发，
 * 外部路径与本文档一致。全部接口受 {@code @RequirePermission("sys:workflow:task:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow/task")
@RequiredArgsConstructor
public class ProcessTaskController {

    private final ProcessTaskService processTaskService;

    /**
     * 分页查询待办任务。
     *
     * @param request 查询条件：assignee 等值、status 等值、instanceId 等值
     * @return 待办任务分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:workflow:task:list")
    public Result<PageResult<ProcessTaskVO>> page(ProcessTaskQueryRequest request) {
        return Result.ok(processTaskService.page(request));
    }

    /**
     * 同意任务（推进下一节点；末节点同意则实例通过）。
     *
     * @param id 任务ID
     * @param request 审批意见与流程变量请求，可为空
     * @return 空结果
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:workflow:task:approve")
    public Result<Void> approve(@PathVariable Long id, @RequestBody(required = false) ApprovalRequest request) {
        processTaskService.approve(id, request);
        return Result.ok();
    }

    /**
     * 驳回任务（实例驳回并取消其余待办）。
     *
     * @param id 任务ID
     * @param request 审批意见请求，可为空
     * @return 空结果
     */
    @PostMapping("/{id}/reject")
    @RequirePermission("sys:workflow:task:approve")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) ApprovalRequest request) {
        processTaskService.reject(id, request == null ? null : request.getComment());
        return Result.ok();
    }

    /**
     * 转办任务（责任人变更，原审批人记录到 {@code owner_id}）。
     *
     * @param id 任务ID
     * @param request 目标人请求
     * @return 空结果
     */
    @PutMapping("/{id}/transfer")
    @RequirePermission("sys:workflow:task:transfer")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TaskTransferRequest request) {
        processTaskService.transfer(id, request.getTargetUser());
        return Result.ok();
    }

    /**
     * 委派任务（处理后需委派人 {@code resolve} 确认）。
     *
     * @param id 任务ID
     * @param request 目标人请求
     * @return 空结果
     */
    @PutMapping("/{id}/delegate")
    @RequirePermission("sys:workflow:task:delegate")
    public Result<Void> delegate(@PathVariable Long id, @Valid @RequestBody TaskTransferRequest request) {
        processTaskService.delegate(id, request.getTargetUser());
        return Result.ok();
    }

    /**
     * 完成委派（被委派人处理后交回委派人确认）。
     *
     * @param id 任务ID
     * @param request 意见请求，可为空
     * @return 空结果
     */
    @PutMapping("/{id}/resolve")
    @RequirePermission("sys:workflow:task:delegate")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody(required = false) ApprovalRequest request) {
        processTaskService.resolve(id, request == null ? null : request.getComment());
        return Result.ok();
    }

    /**
     * 回退到指定审批节点（取消当前待办并重建目标节点待办）。
     *
     * @param id 任务ID
     * @param request 回退目标节点请求
     * @return 空结果
     */
    @PostMapping("/{id}/rollback")
    @RequirePermission("sys:workflow:task:rollback")
    public Result<Void> rollback(@PathVariable Long id, @Valid @RequestBody TaskRollbackRequest request) {
        processTaskService.rollback(id, request.getTargetNodeKey());
        return Result.ok();
    }

    /**
     * 加签：为当前节点追加同级审批人，追加人全部同意后节点才完成。
     *
     * @param id 任务ID
     * @param request 加签人请求
     * @return 空结果
     */
    @PostMapping("/{id}/add-sign")
    @RequirePermission("sys:workflow:task:addsign")
    public Result<Void> addSign(@PathVariable Long id, @Valid @RequestBody TaskAddSignRequest request) {
        processTaskService.addSign(id, request.getAssignees());
        return Result.ok();
    }
}

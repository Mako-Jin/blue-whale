package com.bluewhale.workflow.controller;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.workflow.request.ApprovalRequest;
import com.bluewhale.workflow.request.ProcessTaskQueryRequest;
import com.bluewhale.workflow.request.TaskAddSignRequest;
import com.bluewhale.workflow.request.TaskCompleteRequest;
import com.bluewhale.workflow.request.TaskRollbackRequest;
import com.bluewhale.workflow.request.TaskTransferRequest;
import com.bluewhale.workflow.request.UrgeRequest;
import com.bluewhale.workflow.service.ProcessTaskService;
import com.bluewhale.workflow.service.ProcessUrgeService;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import com.bluewhale.workflow.vo.ProcessUrgeVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程任务内网接口（读写）。
 *
 * <p>服务内路径前缀 {@code /workflow/v1/internal/task}，供 OA 审批中心等内部服务调用；
 * 网关 {@code internal-deny} 对 {@code /workflow/v1/internal/**} 统一返回 403，外部不可达，
 * 故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/workflow/v{version}/internal/task", version = "1")
@RequiredArgsConstructor
public class WorkflowInternalTaskController {

    private final ProcessTaskService processTaskService;

    private final ProcessUrgeService processUrgeService;

    /**
     * 内网完成任务（幂等）。
     *
     * @param id      任务ID
     * @param request 完成请求（操作人/动作/意见/变量/来源）
     * @return 空结果
     */
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @Valid @RequestBody TaskCompleteRequest request) {
        processTaskService.completeInternal(id, request);
        return Result.ok();
    }

    /**
     * 分页查询任务（支持「待我审批」「我已审批」视角）。
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<ProcessTaskVO>> page(ProcessTaskQueryRequest request) {
        return Result.ok(processTaskService.page(request));
    }

    /**
     * 同意任务。
     *
     * @param id 任务ID
     * @param request 审批请求，可为空
     * @return 空结果
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody(required = false) ApprovalRequest request) {
        processTaskService.approve(id, request);
        return Result.ok();
    }

    /**
     * 驳回任务。
     *
     * @param id 任务ID
     * @param request 审批请求，可为空
     * @return 空结果
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) ApprovalRequest request) {
        processTaskService.reject(id, request == null ? null : request.getComment());
        return Result.ok();
    }

    /**
     * 转办任务。
     *
     * @param id 任务ID
     * @param request 目标人请求
     * @return 空结果
     */
    @PutMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TaskTransferRequest request) {
        processTaskService.transfer(id, request.getTargetUser());
        return Result.ok();
    }

    /**
     * 委派任务。
     *
     * @param id 任务ID
     * @param request 目标人请求
     * @return 空结果
     */
    @PutMapping("/{id}/delegate")
    public Result<Void> delegate(@PathVariable Long id, @Valid @RequestBody TaskTransferRequest request) {
        processTaskService.delegate(id, request.getTargetUser());
        return Result.ok();
    }

    /**
     * 完成委派。
     *
     * @param id 任务ID
     * @param request 意见请求，可为空
     * @return 空结果
     */
    @PutMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id,
                                @RequestBody(required = false) ApprovalRequest request) {
        processTaskService.resolve(id, request == null ? null : request.getComment());
        return Result.ok();
    }

    /**
     * 回退到指定审批节点。
     *
     * @param id 任务ID
     * @param request 回退请求
     * @return 空结果
     */
    @PostMapping("/{id}/rollback")
    public Result<Void> rollback(@PathVariable Long id, @Valid @RequestBody TaskRollbackRequest request) {
        processTaskService.rollback(id, request.getTargetNodeKey());
        return Result.ok();
    }

    /**
     * 加签。
     *
     * @param id 任务ID
     * @param request 加签请求
     * @return 空结果
     */
    @PostMapping("/{id}/add-sign")
    public Result<Void> addSign(@PathVariable Long id, @Valid @RequestBody TaskAddSignRequest request) {
        processTaskService.addSign(id, request.getAssignees());
        return Result.ok();
    }

    /**
     * 催办流程实例（1 小时内同人仅允许一次）。
     *
     * @param instanceId 流程实例ID
     * @param request 催办请求（催办人由 X-Username 覆盖）
     * @param httpRequest HTTP 请求（读取身份头）
     * @return 空结果
     */
    @PostMapping("/{instanceId}/urge")
    public Result<Void> urge(@PathVariable Long instanceId, @Valid @RequestBody UrgeRequest request,
                             HttpServletRequest httpRequest) {
        String urger = WebHeaders.username(httpRequest);
        if (!StringUtils.hasText(urger)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.urge.urger-missing", "催办人身份缺失");
        }
        request.setUrger(urger);
        processUrgeService.urge(instanceId, request);
        return Result.ok();
    }

    /**
     * 分页查询催办记录。
     *
     * @param instanceId 流程实例ID
     * @param current 页码
     * @param size 每页条数
     * @return 催办记录分页结果
     */
    @GetMapping("/urge/page")
    public Result<PageResult<ProcessUrgeVO>> urgePage(
            @RequestParam("instanceId") Long instanceId,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.ok(processUrgeService.page(instanceId, current, size));
    }
}

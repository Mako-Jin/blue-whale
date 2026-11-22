package com.bluewhale.oa.approval.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.approval.application.command.ApprovalCenterQuery;
import com.bluewhale.oa.approval.application.service.ApprovalCenterAppService;
import com.bluewhale.oa.approval.application.view.ApprovalDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceRowView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 审批流程监控接口。
 *
 * <p>仅持有 {@code sys:oa:approval:monitor} 的管理人员可用，只读。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/approval/monitor", version = "1")
@RequiredArgsConstructor
public class ApprovalMonitorController {

    private final ApprovalCenterAppService approvalCenterAppService;

    /**
     * 流程实例分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:approval:monitor")
    public Result<PageResult<ApprovalInstanceRowView>> page(ApprovalCenterQuery query) {
        return Result.ok(approvalCenterAppService.pageMonitor(query));
    }

    /**
     * 流程实例详情（只读）。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 详情
     */
    @GetMapping("/instance/{workflowInstanceId}")
    @RequirePermission("sys:oa:approval:monitor")
    public Result<ApprovalDetailView> detail(@PathVariable Long workflowInstanceId) {
        return Result.ok(approvalCenterAppService.monitorDetail(workflowInstanceId));
    }
}

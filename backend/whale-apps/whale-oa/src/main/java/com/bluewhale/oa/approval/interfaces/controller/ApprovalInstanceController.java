package com.bluewhale.oa.approval.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.command.ApprovalResultCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.approval.domain.model.ApprovalInstanceQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 审批实例接口。
 *
 * <p>普通员工只持有 OA 审批权限，不需要 {@code sys:workflow:*}；Workflow 调用由 OA 内网门面代理。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/approval/instance")
@RequiredArgsConstructor
public class ApprovalInstanceController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_approval_instance";

    private final ApprovalInstanceAppService approvalInstanceAppService;

    /**
     * 分页查询审批实例。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:approval:list")
    public Result<PageResult<ApprovalInstanceView>> page(ApprovalInstanceQuery query) {
        return Result.ok(approvalInstanceAppService.page(query));
    }

    /**
     * 查询审批实例详情。
     *
     * @param id 主键ID
     * @return 审批实例详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:approval:view")
    public Result<ApprovalInstanceView> detail(@PathVariable Long id) {
        return Result.ok(approvalInstanceAppService.detail(id));
    }

    /**
     * 按业务单据查询审批实例。
     *
     * @param businessType 业务类型
     * @param businessId 业务单据ID
     * @return 审批实例详情
     */
    @GetMapping("/by-business")
    @RequirePermission("sys:oa:approval:view")
    public Result<ApprovalInstanceView> findByBusiness(@RequestParam String businessType,
                                                       @RequestParam Long businessId) {
        return Result.ok(approvalInstanceAppService.findByBusiness(businessType, businessId));
    }

    /**
     * 登记审批实例（业务单据提交后调用，幂等）。
     *
     * @param command 登记命令
     * @return 审批实例详情
     */
    @PostMapping
    @RequirePermission("sys:oa:approval:register")
    @AuditLog(operation = "登记审批实例", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ApprovalInstanceView> register(@Valid @RequestBody ApprovalInstanceRegisterCommand command) {
        return Result.ok(approvalInstanceAppService.register(command));
    }

    /**
     * 绑定 Workflow 流程实例。
     *
     * @param id 审批实例ID
     * @param workflowInstanceId Workflow 流程实例ID
     * @return 审批实例详情
     */
    @PutMapping("/{id}/workflow")
    @RequirePermission("sys:oa:approval:register")
    @AuditLog(operation = "绑定流程实例", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ApprovalInstanceView> attachWorkflow(@PathVariable Long id,
                                                       @RequestParam Long workflowInstanceId) {
        return Result.ok(approvalInstanceAppService.attachWorkflow(id, workflowInstanceId));
    }

    /**
     * 同步审批结果（幂等）。
     *
     * @param id 审批实例ID
     * @param command 结果命令
     * @return 审批实例详情
     */
    @PutMapping("/{id}/result")
    @RequirePermission("sys:oa:approval:result")
    @AuditLog(operation = "同步审批结果", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ApprovalInstanceView> syncResult(@PathVariable Long id,
                                                   @Valid @RequestBody ApprovalResultCommand command) {
        return Result.ok(approvalInstanceAppService.syncResult(id, command));
    }
}

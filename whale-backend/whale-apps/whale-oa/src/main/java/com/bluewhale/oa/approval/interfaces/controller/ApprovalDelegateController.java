package com.bluewhale.oa.approval.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.approval.application.command.ApprovalDelegateRuleCommand;
import com.bluewhale.oa.approval.application.service.ApprovalDelegateAppService;
import com.bluewhale.oa.approval.application.view.ApprovalDelegateRuleView;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 审批代理设置接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/approval/delegate", version = "1")
@RequiredArgsConstructor
public class ApprovalDelegateController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "oa_approval_delegate";

    private final ApprovalDelegateAppService approvalDelegateAppService;

    /**
     * 分页查询本人代理规则。
     *
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:approval:delegate")
    public Result<PageResult<ApprovalDelegateRuleView>> page(@RequestParam(defaultValue = "1") long current,
                                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(approvalDelegateAppService.page(currentActor(), current, size));
    }

    /**
     * 新建代理规则。
     *
     * @param command 命令
     * @return 规则
     */
    @PostMapping
    @RequirePermission("sys:oa:approval:delegate")
    @AuditLog(operation = "新建审批代理规则", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ApprovalDelegateRuleView> create(@Valid @RequestBody ApprovalDelegateRuleCommand command) {
        return Result.ok(approvalDelegateAppService.create(command, currentActor()));
    }

    /**
     * 修改代理规则。
     *
     * @param id 规则ID
     * @param command 命令
     * @return 规则
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:approval:delegate")
    @AuditLog(operation = "修改审批代理规则", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ApprovalDelegateRuleView> update(@PathVariable Long id,
                                                   @Valid @RequestBody ApprovalDelegateRuleCommand command) {
        return Result.ok(approvalDelegateAppService.update(id, command, currentActor()));
    }

    /**
     * 删除代理规则。
     *
     * @param id 规则ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:approval:delegate")
    @AuditLog(operation = "删除审批代理规则", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        approvalDelegateAppService.delete(id, currentActor());
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

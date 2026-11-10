package com.bluewhale.oa.approval.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.approval.application.command.ApprovalRouteRuleSaveCommand;
import com.bluewhale.oa.approval.application.service.ApprovalRouteRuleAppService;
import com.bluewhale.oa.approval.application.view.ApprovalRouteRuleView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 审批人解析规则接口。
 *
 * <p>规则决定发起审批时部门经理 / HR / 角色等动态审批人的解析方式；
 * 解析结果在发起时快照到审批实例，后续组织变更不影响在途流程。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/approval/route-rule")
@RequiredArgsConstructor
public class ApprovalRouteRuleController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_approval_route_rule";

    private final ApprovalRouteRuleAppService approvalRouteRuleAppService;

    /**
     * 查询业务类型下的审批人规则。
     *
     * @param businessType 业务类型
     * @return 规则列表
     */
    @GetMapping("/list")
    @RequirePermission("sys:oa:approval:rule:list")
    public Result<List<ApprovalRouteRuleView>> list(@RequestParam String businessType) {
        return Result.ok(approvalRouteRuleAppService.list(businessType));
    }

    /**
     * 保存审批人规则（存在则更新）。
     *
     * @param command 保存命令
     * @return 规则详情
     */
    @PostMapping
    @RequirePermission("sys:oa:approval:rule:edit")
    @AuditLog(operation = "保存审批人规则", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<ApprovalRouteRuleView> save(@Valid @RequestBody ApprovalRouteRuleSaveCommand command) {
        return Result.ok(approvalRouteRuleAppService.save(command));
    }

    /**
     * 删除审批人规则。
     *
     * @param id 主键ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:approval:rule:edit")
    @AuditLog(operation = "删除审批人规则", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        approvalRouteRuleAppService.delete(id);
        return Result.ok();
    }
}

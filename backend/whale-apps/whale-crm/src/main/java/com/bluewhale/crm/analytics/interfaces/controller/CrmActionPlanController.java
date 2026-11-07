package com.bluewhale.crm.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.analytics.application.command.ActionItemCloseCommand;
import com.bluewhale.crm.analytics.application.service.ActionPlanAppService;
import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemQuery;
import com.bluewhale.crm.analytics.domain.model.ActionRule;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRM 行动计划接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm")
@RequiredArgsConstructor
public class CrmActionPlanController {

    private final ActionPlanAppService actionPlanAppService;

    /**
     * 分页查询行动项。
     *
     * @param ruleCode 规则编码
     * @param status 状态
     * @param priority 优先级
     * @param refType 关联类型
     * @param customerId 客户 ID
     * @param ownerId 负责人
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/action-plan/page")
    @RequirePermission(CrmPermissions.ACTION_PLAN)
    public Result<PageResult<ActionItem>> page(
            @RequestParam(value = "ruleCode", required = false) String ruleCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "refType", required = false) String refType,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ActionItemQuery query = new ActionItemQuery();
        query.setRuleCode(ruleCode);
        query.setStatus(status);
        query.setPriority(priority);
        query.setRefType(refType);
        query.setCustomerId(customerId);
        query.setOwnerId(ownerId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(actionPlanAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 生成行动项。
     *
     * @param request 当前请求
     * @return 新增条数
     */
    @PostMapping("/action-plan/generate")
    @RequirePermission(CrmPermissions.ACTION_PLAN)
    @AuditLog(operation = "生成行动计划", type = OperateType.CREATE, targetType = "crm_action_item")
    public Result<Integer> generate(HttpServletRequest request) {
        return Result.ok(actionPlanAppService.generate(CrmActorContext.from(request)));
    }

    /**
     * 手工关闭行动项。
     *
     * @param id 行动项 ID
     * @param command 关闭命令
     * @param request 当前请求
     * @return 更新后的行动项
     */
    @PostMapping("/action-plan/{id}/close")
    @RequirePermission(CrmPermissions.ACTION_PLAN)
    @AuditLog(operation = "关闭行动项", type = OperateType.UPDATE, targetType = "crm_action_item")
    public Result<ActionItem> closeItem(@PathVariable("id") Long id,
                                        @Valid @RequestBody ActionItemCloseCommand command,
                                        HttpServletRequest request) {
        return Result.ok(actionPlanAppService.closeItem(id, command.getReason(),
                CrmActorContext.from(request)));
    }

    /**
     * 查询全部行动规则。
     *
     * @param request 当前请求
     * @return 规则列表
     */
    @GetMapping("/action-rule")
    @RequirePermission(CrmPermissions.ACTION_PLAN)
    public Result<List<ActionRule>> listRules(HttpServletRequest request) {
        return Result.ok(actionPlanAppService.listRules(CrmActorContext.from(request)));
    }

    /**
     * 关闭行动规则。
     *
     * @param id 规则 ID
     * @param request 当前请求
     * @return 更新后的规则
     */
    @PostMapping("/action-rule/{id}/close")
    @RequirePermission(CrmPermissions.ACTION_PLAN)
    @AuditLog(operation = "关闭行动规则", type = OperateType.UPDATE, targetType = "crm_action_rule")
    public Result<ActionRule> closeRule(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(actionPlanAppService.closeRule(id, CrmActorContext.from(request)));
    }
}

package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.customer.application.command.PoolAssignCommand;
import com.bluewhale.crm.customer.application.command.PoolRuleSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.OwnerLogView;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import com.bluewhale.crm.customer.application.view.PoolRuleView;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.CustomerPageParams;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户公海接口：查询、领取、释放、分配、回收与规则维护。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/pool")
@RequiredArgsConstructor
public class CustomerPoolController {

    private final CustomerPoolAppService poolAppService;

    /**
     * 公海分页。
     *
     * @param params 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 公海客户分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.POOL_VIEW)
    public Result<PageResult<CustomerView>> page(
            @ModelAttribute CustomerPageParams params,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CustomerQuery query = params.toQuery();
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(poolAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 领取公海客户。
     *
     * @param id 客户 ID
     * @param request 当前请求
     * @return 客户视图
     */
    @PostMapping("/{id}/claim")
    @RequirePermission(CrmPermissions.POOL_CLAIM)
    @AuditLog(operation = "领取公海客户", type = OperateType.UPDATE,
            targetType = "crm_customer", targetId = "#id")
    public Result<CustomerView> claim(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(poolAppService.claim(id, CrmActorContext.from(request)));
    }

    /**
     * 释放客户到公海。
     *
     * @param id 客户 ID
     * @param remark 备注
     * @param request 当前请求
     * @return 客户视图
     */
    @PostMapping("/{id}/release")
    @RequirePermission(CrmPermissions.POOL_RELEASE)
    @AuditLog(operation = "释放客户到公海", type = OperateType.UPDATE,
            targetType = "crm_customer", targetId = "#id")
    public Result<CustomerView> release(@PathVariable("id") Long id,
                                        @RequestParam(value = "remark", required = false) String remark,
                                        HttpServletRequest request) {
        return Result.ok(poolAppService.release(id, remark, CrmActorContext.from(request)));
    }

    /**
     * 分配客户负责人。
     *
     * @param id 客户 ID
     * @param command 分配命令
     * @param request 当前请求
     * @return 客户视图
     */
    @PostMapping("/{id}/assign")
    @RequirePermission(CrmPermissions.POOL_ASSIGN)
    @AuditLog(operation = "分配客户负责人", type = OperateType.UPDATE,
            targetType = "crm_customer", targetId = "#id")
    public Result<CustomerView> assign(@PathVariable("id") Long id,
                                       @Valid @RequestBody PoolAssignCommand command,
                                       HttpServletRequest request) {
        return Result.ok(poolAppService.assign(id, command, CrmActorContext.from(request)));
    }

    /**
     * 按规则回收客户到公海。
     *
     * @param request 当前请求
     * @return 回收结果
     */
    @PostMapping("/recycle")
    @RequirePermission(CrmPermissions.POOL_RECYCLE)
    @AuditLog(operation = "手动回收公海客户", type = OperateType.UPDATE, targetType = "crm_customer")
    public Result<PoolRecycleResultView> recycle(HttpServletRequest request) {
        return Result.ok(poolAppService.recycle(CrmActorContext.from(request)));
    }

    /**
     * 查询公海规则。
     *
     * @param request 当前请求
     * @return 规则视图
     */
    @GetMapping("/rule")
    @RequirePermission(CrmPermissions.POOL_RULE)
    public Result<PoolRuleView> getRule(HttpServletRequest request) {
        return Result.ok(poolAppService.getRule(CrmActorContext.from(request)));
    }

    /**
     * 保存公海规则。
     *
     * @param command 规则命令
     * @param request 当前请求
     * @return 规则视图
     */
    @PutMapping("/rule")
    @RequirePermission(CrmPermissions.POOL_RULE)
    @AuditLog(operation = "保存公海规则", type = OperateType.UPDATE,
            targetType = "crm_customer_pool_rule", targetId = "#command.id")
    public Result<PoolRuleView> saveRule(@RequestBody PoolRuleSaveCommand command,
                                        HttpServletRequest request) {
        return Result.ok(poolAppService.saveRule(command, CrmActorContext.from(request)));
    }

    /**
     * 查询客户负责人变更历史。
     *
     * @param id 客户 ID
     * @param request 当前请求
     * @return 留痕列表
     */
    @GetMapping("/{id}/owner-logs")
    @RequirePermission(CrmPermissions.POOL_VIEW)
    public Result<List<OwnerLogView>> ownerLogs(@PathVariable("id") Long id,
                                                HttpServletRequest request) {
        return Result.ok(poolAppService.ownerLogs(id, CrmActorContext.from(request)));
    }
}

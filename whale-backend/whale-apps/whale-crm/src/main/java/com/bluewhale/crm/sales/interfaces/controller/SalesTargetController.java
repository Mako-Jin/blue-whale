package com.bluewhale.crm.sales.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.sales.application.command.SalesTargetSaveCommand;
import com.bluewhale.crm.sales.application.command.SalesTargetUpdateCommand;
import com.bluewhale.crm.sales.application.service.SalesTargetAppService;
import com.bluewhale.crm.sales.application.view.SalesTargetAdjustmentView;
import com.bluewhale.crm.sales.application.view.SalesTargetView;
import com.bluewhale.crm.sales.application.view.TargetCompletionView;
import com.bluewhale.crm.sales.domain.model.SalesTargetQuery;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

/**
 * 销售目标接口（M2 增量）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/sales-target")
@RequiredArgsConstructor
public class SalesTargetController {

    private final SalesTargetAppService salesTargetAppService;

    /**
     * 销售目标分页。
     *
     * @param period 周期
     * @param periodType 周期类型
     * @param scopeType 范围类型
     * @param ownerId 负责人
     * @param deptId 部门
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 目标分页
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.TARGET_LIST)
    public Result<PageResult<SalesTargetView>> page(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "scopeType", required = false) String scopeType,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        SalesTargetQuery query = new SalesTargetQuery();
        query.setPeriod(period);
        query.setPeriodType(periodType);
        query.setScopeType(scopeType);
        query.setOwnerId(ownerId);
        query.setDeptId(deptId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(salesTargetAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 销售目标详情。
     *
     * @param id 目标 ID
     * @param request 当前请求
     * @return 目标详情
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.TARGET_LIST)
    public Result<SalesTargetView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(salesTargetAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 销售目标完成情况。
     *
     * @param id 目标 ID
     * @param request 当前请求
     * @return 完成情况
     */
    @GetMapping("/{id}/completion")
    @RequirePermission(CrmPermissions.TARGET_LIST)
    public Result<TargetCompletionView> completion(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(salesTargetAppService.completion(id, CrmActorContext.from(request)));
    }

    /**
     * 目标调整记录。
     *
     * @param id 目标 ID
     * @param request 当前请求
     * @return 调整记录
     */
    @GetMapping("/{id}/adjustments")
    @RequirePermission(CrmPermissions.TARGET_LIST)
    public Result<List<SalesTargetAdjustmentView>> adjustments(@PathVariable("id") Long id,
                                                              HttpServletRequest request) {
        return Result.ok(salesTargetAppService.adjustments(id, CrmActorContext.from(request)));
    }

    /**
     * 新建销售目标。
     *
     * @param command 新建命令
     * @param request 当前请求
     * @return 目标详情
     */
    @PostMapping
    @RequirePermission(CrmPermissions.TARGET_MANAGE)
    @AuditLog(operation = "新建销售目标", type = OperateType.CREATE, targetType = "crm_sales_target")
    public Result<SalesTargetView> create(@Valid @RequestBody SalesTargetSaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(salesTargetAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 调整销售目标。
     *
     * @param id 目标 ID
     * @param command 调整命令
     * @param request 当前请求
     * @return 目标详情
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.TARGET_MANAGE)
    @AuditLog(operation = "调整销售目标", type = OperateType.UPDATE,
            targetType = "crm_sales_target", targetId = "#id")
    public Result<SalesTargetView> update(@PathVariable("id") Long id,
                                          @Valid @RequestBody SalesTargetUpdateCommand command,
                                          HttpServletRequest request) {
        return Result.ok(salesTargetAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 删除销售目标。
     *
     * @param id 目标 ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission(CrmPermissions.TARGET_DELETE)
    @AuditLog(operation = "删除销售目标", type = OperateType.DELETE,
            targetType = "crm_sales_target", targetId = "#id")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        salesTargetAppService.delete(id, CrmActorContext.from(request));
        return Result.ok();
    }
}

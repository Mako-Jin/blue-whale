package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.SalesOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.ShipCommand;
import com.bluewhale.erp.scm.application.service.SalesAppService;
import com.bluewhale.erp.scm.application.view.SalesOrderView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售订单接口：查询、建单、审核、发货、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/scm/sales-order")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesAppService salesAppService;

    /**
     * 销售订单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 订单分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:sales:list")
    public Result<PageResult<SalesOrderView>> page(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(salesAppService.page(query));
    }

    /**
     * 销售订单详情。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:sales:list")
    public Result<SalesOrderView> detail(@PathVariable Long id) {
        return Result.ok(salesAppService.detail(id));
    }

    /**
     * 新增销售订单。
     *
     * @param command 创建命令
     * @return 订单视图
     */
    @PostMapping
    @RequirePermission("sys:erp:sales:add")
    @AuditLog(operation = "新增销售订单", targetType = "erp_scm_sales_order", type = OperateType.CREATE)
    public Result<SalesOrderView> create(@Valid @RequestBody SalesOrderCreateCommand command) {
        return Result.ok(salesAppService.create(command));
    }

    /**
     * 审核销售订单。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:erp:sales:approve")
    @AuditLog(operation = "审核销售订单", targetType = "erp_scm_sales_order", type = OperateType.UPDATE)
    public Result<SalesOrderView> approve(@PathVariable Long id) {
        return Result.ok(salesAppService.approve(id));
    }

    /**
     * 销售发货。
     *
     * @param id 订单 ID
     * @param command 发货命令
     * @param request 当前请求
     * @return 订单视图
     */
    @PostMapping("/{id}/ship")
    @RequirePermission("sys:erp:sales:ship")
    @AuditLog(operation = "销售发货", targetType = "erp_scm_sales_order", type = OperateType.UPDATE)
    public Result<SalesOrderView> ship(@PathVariable Long id, @Valid @RequestBody ShipCommand command,
                                       HttpServletRequest request) {
        return Result.ok(salesAppService.ship(id, command, ErpUserContext.userId(request)));
    }

    /**
     * 取消销售订单。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:sales:cancel")
    @AuditLog(operation = "取消销售订单", targetType = "erp_scm_sales_order", type = OperateType.UPDATE)
    public Result<SalesOrderView> cancel(@PathVariable Long id) {
        return Result.ok(salesAppService.cancel(id));
    }
}

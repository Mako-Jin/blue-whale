package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.commerce.application.command.SalesOrderCreateCommand;
import com.bluewhale.crm.commerce.application.command.SalesOrderUpdateCommand;
import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.commerce.application.view.SalesOrderView;
import com.bluewhale.crm.commerce.domain.model.SalesOrderQuery;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
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
 * 商务订单接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/order", version = "1")
@RequiredArgsConstructor
public class CrmSalesOrderController {

    private final SalesOrderAppService salesOrderAppService;

    /**
     * 分页查询商务订单。
     *
     * @param keyword 关键字
     * @param customerId 客户 ID
     * @param status 状态
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.ORDER_LIST)
    public Result<PageResult<SalesOrderView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        SalesOrderQuery query = new SalesOrderQuery();
        query.setKeyword(keyword);
        query.setCustomerId(customerId);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(salesOrderAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 查看订单详情。
     *
     * @param id 订单 ID
     * @param request 当前请求
     * @return 订单视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.ORDER_DETAIL)
    public Result<SalesOrderView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(salesOrderAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建商务订单。
     *
     * @param command 创建命令
     * @param request 当前请求
     * @return 订单视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.ORDER_CREATE)
    @AuditLog(operation = "新建商务订单", type = OperateType.CREATE, targetType = "crm_sales_order")
    public Result<SalesOrderView> create(@Valid @RequestBody SalesOrderCreateCommand command,
                                         HttpServletRequest request) {
        return Result.ok(salesOrderAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 编辑草稿订单。
     *
     * @param id 订单 ID
     * @param command 编辑命令
     * @param request 当前请求
     * @return 订单视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.ORDER_UPDATE)
    @AuditLog(operation = "编辑商务订单", type = OperateType.UPDATE, targetType = "crm_sales_order",
            targetId = "#id")
    public Result<SalesOrderView> update(@PathVariable("id") Long id,
                                         @Valid @RequestBody SalesOrderUpdateCommand command,
                                         HttpServletRequest request) {
        return Result.ok(salesOrderAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 提交审批。
     *
     * @param id 订单 ID
     * @param request 当前请求
     * @return 订单视图
     */
    @PostMapping("/{id}/submit")
    @RequirePermission(CrmPermissions.ORDER_SUBMIT)
    @AuditLog(operation = "提交商务订单审批", type = OperateType.UPDATE, targetType = "crm_sales_order",
            targetId = "#id")
    public Result<SalesOrderView> submit(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(salesOrderAppService.submit(id, CrmActorContext.from(request)));
    }

    /**
     * 取消订单。
     *
     * @param id 订单 ID
     * @param request 当前请求
     * @return 订单视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(CrmPermissions.ORDER_CANCEL)
    @AuditLog(operation = "取消商务订单", type = OperateType.UPDATE, targetType = "crm_sales_order",
            targetId = "#id")
    public Result<SalesOrderView> cancel(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(salesOrderAppService.cancel(id, CrmActorContext.from(request)));
    }

    /**
     * 重试下发。
     *
     * @param id 订单 ID
     * @param request 当前请求
     * @return 订单视图
     */
    @PostMapping("/{id}/retry")
    @RequirePermission(CrmPermissions.ORDER_RETRY)
    @AuditLog(operation = "重试下发商务订单", type = OperateType.UPDATE, targetType = "crm_sales_order",
            targetId = "#id")
    public Result<SalesOrderView> retry(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(salesOrderAppService.retry(id, CrmActorContext.from(request)));
    }

    /**
     * 手动触发对账。
     *
     * @return 刷新条数
     */
    @PostMapping("/reconcile")
    @RequirePermission(CrmPermissions.ORDER_RECONCILE)
    @AuditLog(operation = "对账商务订单履约状态", type = OperateType.OTHER, targetType = "crm_sales_order")
    public Result<Integer> reconcile() {
        return Result.ok(salesOrderAppService.reconcile());
    }
}

package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.PurchaseOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.ReceiveCommand;
import com.bluewhale.erp.scm.application.service.PurchaseAppService;
import com.bluewhale.erp.scm.application.view.PurchaseOrderView;
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
 * 采购订单接口：查询、建单、审核、收货、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/scm/purchase-order", version = "1")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseAppService purchaseAppService;

    /**
     * 采购订单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 订单分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:purchase:list")
    public Result<PageResult<PurchaseOrderView>> page(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(purchaseAppService.page(query));
    }

    /**
     * 采购订单详情。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:purchase:list")
    public Result<PurchaseOrderView> detail(@PathVariable Long id) {
        return Result.ok(purchaseAppService.detail(id));
    }

    /**
     * 新增采购订单。
     *
     * @param command 创建命令
     * @return 订单视图
     */
    @PostMapping
    @RequirePermission("sys:erp:purchase:add")
    @AuditLog(operation = "新增采购订单", targetType = "erp_scm_purchase_order", type = OperateType.CREATE)
    public Result<PurchaseOrderView> create(@Valid @RequestBody PurchaseOrderCreateCommand command) {
        return Result.ok(purchaseAppService.create(command));
    }

    /**
     * 审核采购订单。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:erp:purchase:approve")
    @AuditLog(operation = "审核采购订单", targetType = "erp_scm_purchase_order", type = OperateType.UPDATE)
    public Result<PurchaseOrderView> approve(@PathVariable Long id) {
        return Result.ok(purchaseAppService.approve(id));
    }

    /**
     * 采购收货。
     *
     * @param id 订单 ID
     * @param command 收货命令
     * @param request 当前请求
     * @return 订单视图
     */
    @PostMapping("/{id}/receive")
    @RequirePermission("sys:erp:purchase:receive")
    @AuditLog(operation = "采购收货", targetType = "erp_scm_purchase_order", type = OperateType.UPDATE)
    public Result<PurchaseOrderView> receive(@PathVariable Long id, @Valid @RequestBody ReceiveCommand command,
                                             HttpServletRequest request) {
        return Result.ok(purchaseAppService.receive(id, command, ErpUserContext.userId(request)));
    }

    /**
     * 取消采购订单。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:purchase:cancel")
    @AuditLog(operation = "取消采购订单", targetType = "erp_scm_purchase_order", type = OperateType.UPDATE)
    public Result<PurchaseOrderView> cancel(@PathVariable Long id) {
        return Result.ok(purchaseAppService.cancel(id));
    }
}

package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.PurchaseRequisitionCreateCommand;
import com.bluewhale.erp.scm.application.service.PurchaseRequisitionAppService;
import com.bluewhale.erp.scm.application.view.PurchaseRequisitionView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
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
 * 采购申请接口：查询、建单、审核、转单、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/scm/purchase-requisition")
@RequiredArgsConstructor
public class PurchaseRequisitionController {

    private final PurchaseRequisitionAppService purchaseRequisitionAppService;

    /**
     * 采购申请分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:purchase-requisition:list")
    public Result<PageResult<PurchaseRequisitionView>> page(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(purchaseRequisitionAppService.page(query));
    }

    /**
     * 采购申请详情。
     *
     * @param id 主键
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:purchase-requisition:detail")
    public Result<PurchaseRequisitionView> detail(@PathVariable Long id) {
        return Result.ok(purchaseRequisitionAppService.detail(id));
    }

    /**
     * 新增采购申请单。
     *
     * @param command 创建命令
     * @return 视图
     */
    @PostMapping
    @RequirePermission("sys:erp:purchase-requisition:create")
    @AuditLog(operation = "新增采购申请单", targetType = "erp_scm_purchase_requisition",
            type = OperateType.CREATE)
    public Result<PurchaseRequisitionView> create(
            @Valid @RequestBody PurchaseRequisitionCreateCommand command) {
        return Result.ok(purchaseRequisitionAppService.create(command));
    }

    /**
     * 审核采购申请单。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/approve")
    @RequirePermission("sys:erp:purchase-requisition:approve")
    @AuditLog(operation = "审核采购申请单", targetType = "erp_scm_purchase_requisition",
            type = OperateType.UPDATE)
    public Result<PurchaseRequisitionView> approve(@PathVariable Long id) {
        return Result.ok(purchaseRequisitionAppService.approve(id));
    }

    /**
     * 采购申请转采购订单。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/convert")
    @RequirePermission("sys:erp:purchase-requisition:convert")
    @AuditLog(operation = "采购申请转采购订单", targetType = "erp_scm_purchase_requisition",
            type = OperateType.UPDATE)
    public Result<PurchaseRequisitionView> convert(@PathVariable Long id) {
        return Result.ok(purchaseRequisitionAppService.convert(id));
    }

    /**
     * 取消采购申请单。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:purchase-requisition:cancel")
    @AuditLog(operation = "取消采购申请单", targetType = "erp_scm_purchase_requisition",
            type = OperateType.UPDATE)
    public Result<PurchaseRequisitionView> cancel(@PathVariable Long id) {
        return Result.ok(purchaseRequisitionAppService.cancel(id));
    }
}

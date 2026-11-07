package com.bluewhale.oa.administrative.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.service.PurchaseAppService;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 采购申请接口。
 *
 * <p>审批通过后推送 ERP 采购订单草稿；推送失败可手工重试。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/purchase")
@RequiredArgsConstructor
public class PurchaseController {

    private static final String TARGET_TYPE = "oa_purchase_request";

    private final PurchaseAppService purchaseAppService;

    @GetMapping("/page")
    @RequirePermission("sys:oa:purchase:list")
    public Result<PageResult<AdministrativeViews.PurchaseView>> page(AdministrativeQuery query) {
        return Result.ok(purchaseAppService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:oa:purchase:list")
    public Result<AdministrativeViews.PurchaseView> detail(@PathVariable Long id) {
        return Result.ok(purchaseAppService.detail(id));
    }

    @PostMapping
    @RequirePermission("sys:oa:purchase:add")
    @AuditLog(operation = "新建采购申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<AdministrativeViews.PurchaseView> create(
            @Valid @RequestBody AdministrativeCommands.PurchaseSaveCommand command) {
        return Result.ok(purchaseAppService.create(command));
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:oa:purchase:edit")
    @AuditLog(operation = "更新采购申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AdministrativeViews.PurchaseView> update(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.PurchaseSaveCommand command) {
        return Result.ok(purchaseAppService.update(id, command));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:purchase:delete")
    @AuditLog(operation = "删除采购申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseAppService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:purchase:edit")
    @AuditLog(operation = "变更采购状态", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.PurchaseStatusCommand command) {
        purchaseAppService.updateStatus(id, command);
        return Result.ok();
    }

    @PostMapping("/{id}/push-retry")
    @RequirePermission("sys:oa:purchase:push")
    @AuditLog(operation = "重试推送采购订单", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<AdministrativeViews.PurchaseView> retryPush(@PathVariable Long id) {
        return Result.ok(purchaseAppService.retryPush(id));
    }
}

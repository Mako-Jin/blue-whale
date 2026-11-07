package com.bluewhale.oa.administrative.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.service.SealUseAppService;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.gateway.ContractSealGateway;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 用章申请接口。
 *
 * <p>印章授权由 Contract 内网只读接口校验；审批通过后仅可登记一次。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/seal")
@RequiredArgsConstructor
public class SealUseController {

    private static final String TARGET_TYPE = "oa_seal_use";

    private final SealUseAppService sealUseAppService;

    @GetMapping("/page")
    @RequirePermission("sys:oa:seal:list")
    public Result<PageResult<AdministrativeViews.SealUseView>> page(AdministrativeQuery query) {
        return Result.ok(sealUseAppService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:oa:seal:list")
    public Result<AdministrativeViews.SealUseView> detail(@PathVariable Long id) {
        return Result.ok(sealUseAppService.detail(id));
    }

    @PostMapping
    @RequirePermission("sys:oa:seal:add")
    @AuditLog(operation = "新建用章申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<AdministrativeViews.SealUseView> create(
            @Valid @RequestBody AdministrativeCommands.SealUseSaveCommand command) {
        return Result.ok(sealUseAppService.create(command));
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:oa:seal:edit")
    @AuditLog(operation = "更新用章申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AdministrativeViews.SealUseView> update(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.SealUseSaveCommand command) {
        return Result.ok(sealUseAppService.update(id, command));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:seal:delete")
    @AuditLog(operation = "删除用章申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        sealUseAppService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:seal:edit")
    @AuditLog(operation = "变更用章状态", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.SealUseStatusCommand command) {
        sealUseAppService.updateStatus(id, command);
        return Result.ok();
    }

    @PostMapping("/{id}/register")
    @RequirePermission("sys:oa:seal:register")
    @AuditLog(operation = "用印登记", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AdministrativeViews.SealUseView> register(@PathVariable Long id) {
        return Result.ok(sealUseAppService.register(id, RequestContextHolder.getUserId()));
    }

    @GetMapping("/available-seals")
    @RequirePermission("sys:oa:seal:list")
    public Result<List<ContractSealGateway.SealInfo>> availableSeals(
            @RequestParam(required = false) Long userId) {
        Long target = userId == null ? RequestContextHolder.getUserId() : userId;
        return Result.ok(sealUseAppService.availableSeals(target));
    }
}

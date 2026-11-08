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

    /**
     * 分页查询用章申请。
     *
     * @param query 查询条件
     * @return 用章申请分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:seal:list")
    public Result<PageResult<AdministrativeViews.SealUseView>> page(AdministrativeQuery query) {
        return Result.ok(sealUseAppService.page(query));
    }

    /**
     * 用章申请详情。
     *
     * @param id 用章申请ID
     * @return 用章申请详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:seal:list")
    public Result<AdministrativeViews.SealUseView> detail(@PathVariable Long id) {
        return Result.ok(sealUseAppService.detail(id));
    }

    /**
     * 新建用章申请并登记审批。
     *
     * @param command 用章申请保存命令
     * @return 新建后的用章申请
     */
    @PostMapping
    @RequirePermission("sys:oa:seal:add")
    @AuditLog(operation = "新建用章申请", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<AdministrativeViews.SealUseView> create(
            @Valid @RequestBody AdministrativeCommands.SealUseSaveCommand command) {
        return Result.ok(sealUseAppService.create(command));
    }

    /**
     * 修改用章申请。
     *
     * @param id      用章申请ID
     * @param command 用章申请保存命令
     * @return 修改后的用章申请
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:seal:edit")
    @AuditLog(operation = "更新用章申请", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AdministrativeViews.SealUseView> update(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.SealUseSaveCommand command) {
        return Result.ok(sealUseAppService.update(id, command));
    }

    /**
     * 删除用章申请。
     *
     * @param id 用章申请ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:seal:delete")
    @AuditLog(operation = "删除用章申请", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        sealUseAppService.delete(id);
        return Result.ok();
    }

    /**
     * 变更用章申请状态。
     *
     * @param id      用章申请ID
     * @param command 状态变更命令
     * @return 空结果
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:seal:edit")
    @AuditLog(operation = "变更用章状态", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.SealUseStatusCommand command) {
        sealUseAppService.updateStatus(id, command);
        return Result.ok();
    }

    /**
     * 登记用印，仅审批通过的申请可登记且只登记一次。
     *
     * @param id 用章申请ID
     * @return 登记后的用章申请
     */
    @PostMapping("/{id}/register")
    @RequirePermission("sys:oa:seal:register")
    @AuditLog(operation = "用印登记", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AdministrativeViews.SealUseView> register(@PathVariable Long id) {
        return Result.ok(sealUseAppService.register(id, RequestContextHolder.getUserId()));
    }

    /**
     * 查询指定用户可用的印章列表，用户ID为空时取当前登录人。
     *
     * @param userId 用户ID，可为空
     * @return 可用印章列表
     */
    @GetMapping("/available-seals")
    @RequirePermission("sys:oa:seal:list")
    public Result<List<ContractSealGateway.SealInfo>> availableSeals(
            @RequestParam(required = false) Long userId) {
        Long target = userId == null ? RequestContextHolder.getUserId() : userId;
        return Result.ok(sealUseAppService.availableSeals(target));
    }
}

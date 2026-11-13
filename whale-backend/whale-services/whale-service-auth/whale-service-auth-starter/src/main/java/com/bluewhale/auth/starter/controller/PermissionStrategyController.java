package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SavePermissionStrategyCommand;
import com.bluewhale.auth.application.model.query.StrategyPageQuery;
import com.bluewhale.auth.application.service.PermissionStrategyAppService;
import com.bluewhale.auth.interfaces.api.PermissionStrategyApi;
import com.bluewhale.auth.interfaces.dto.request.PermissionStrategySaveRequest;
import com.bluewhale.auth.interfaces.dto.response.PermissionStrategyResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.PermissionStrategyAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限策略接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class PermissionStrategyController implements PermissionStrategyApi {

    private final PermissionStrategyAppService permissionStrategyAppService;
    private final PermissionStrategyAssembler permissionStrategyAssembler;

    /**
     * 分页查询权限策略
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字
     * @param strategyType 类型过滤
     * @return 策略分页结果
     */
    @Override
    @RequirePermission("sys:perm:strategy:list")
    public Result<PageResult<PermissionStrategyResponse>> page(PageQuery pageQuery,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String strategyType) {
        return Result.ok(permissionStrategyAssembler.toPage(permissionStrategyAppService.page(
                new StrategyPageQuery(pageQuery.getCurrent(), pageQuery.getSize(), keyword, strategyType))));
    }

    /**
     * 新增权限策略
     *
     * @param request 保存请求
     * @return 新建策略
     */
    @Override
    @RequirePermission("sys:perm:strategy:add")
    @AuditLog(operation = "新增权限策略", type = OperateType.CREATE, targetType = "sys_permission_strategy")
    public Result<PermissionStrategyResponse> create(@Valid @RequestBody PermissionStrategySaveRequest request) {
        SavePermissionStrategyCommand command = permissionStrategyAssembler.toCommand(request);
        return Result.ok(permissionStrategyAssembler.toResponse(permissionStrategyAppService.create(command)));
    }

    /**
     * 编辑权限策略（策略编码不可变）
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的策略
     */
    @Override
    @RequirePermission("sys:perm:strategy:edit")
    @AuditLog(operation = "编辑权限策略", type = OperateType.UPDATE, targetType = "sys_permission_strategy",
            targetId = "#id")
    public Result<PermissionStrategyResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody PermissionStrategySaveRequest request) {
        SavePermissionStrategyCommand command = permissionStrategyAssembler.toCommand(request);
        return Result.ok(permissionStrategyAssembler.toResponse(permissionStrategyAppService.update(id, command)));
    }

    /**
     * 删除权限策略
     *
     * @param id 主键
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:perm:strategy:delete")
    @AuditLog(operation = "删除权限策略", type = OperateType.DELETE, targetType = "sys_permission_strategy",
            targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        permissionStrategyAppService.delete(id);
        return Result.ok();
    }

    /**
     * 设置为默认策略（同租户仅一条，其余自动取消）
     *
     * @param id 主键
     * @return 设置后的策略
     */
    @Override
    @RequirePermission("sys:perm:strategy:set-default")
    @AuditLog(operation = "设置默认权限策略", type = OperateType.UPDATE, targetType = "sys_permission_strategy",
            targetId = "#id")
    public Result<PermissionStrategyResponse> setDefault(@PathVariable Long id) {
        return Result.ok(permissionStrategyAssembler.toResponse(permissionStrategyAppService.setDefault(id)));
    }
}

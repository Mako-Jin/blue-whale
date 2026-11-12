package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.PermissionAppService;
import com.bluewhale.auth.application.model.command.SavePermissionCommand;
import com.bluewhale.auth.application.model.query.PermissionPageQuery;
import com.bluewhale.auth.interfaces.api.PermissionApi;
import com.bluewhale.auth.interfaces.dto.request.PermissionRequest;
import com.bluewhale.auth.interfaces.dto.response.PermissionResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.PermissionAssembler;
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
 * 权限点接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class PermissionController implements PermissionApi {

    private final PermissionAppService permissionAppService;

    private final PermissionAssembler permissionAssembler;

    /**
     * 分页查询权限点
     *
     * @param pageQuery 分页参数（当前页与每页条数）
     * @param keyword 权限编码或名称模糊匹配关键字（可空）
     * @param permType 权限类型精确过滤条件（可空）
     * @return 权限点分页结果
     */
    @Override
    @RequirePermission("sys:permission:list")
    public Result<PageResult<PermissionResponse>> page(PageQuery pageQuery,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String permType) {
        return Result.ok(permissionAssembler.toPage(permissionAppService.page(
                new PermissionPageQuery(pageQuery.getCurrent(), pageQuery.getSize(), keyword, permType))));
    }

    /**
     * 权限点详情
     *
     * @param id 权限点 ID
     * @return 权限点响应
     */
    @Override
    @RequirePermission("sys:permission:list")
    public Result<PermissionResponse> detail(@PathVariable Long id) {
        return Result.ok(permissionAssembler.toResponse(permissionAppService.getDetail(id)));
    }

    /**
     * 创建权限点
     *
     * @param request 权限点请求参数
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:permission:add")
    @AuditLog(operation = "创建权限点", type = OperateType.CREATE, targetType = "sys_permission")
    public Result<Void> create(@Valid @RequestBody PermissionRequest request) {
        permissionAppService.create(toCommand(request));
        return Result.ok();
    }

    /**
     * 更新权限点
     *
     * @param id 权限点 ID
     * @param request 权限点请求参数
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:permission:edit")
    @AuditLog(operation = "更新权限点", type = OperateType.UPDATE, targetType = "sys_permission",
            targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        permissionAppService.update(id, toCommand(request));
        return Result.ok();
    }

    /**
     * 删除权限点
     *
     * @param id 权限点 ID
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:permission:delete")
    @AuditLog(operation = "删除权限点", type = OperateType.DELETE, targetType = "sys_permission",
            targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        permissionAppService.delete(id);
        return Result.ok();
    }

    /**
     * 请求参数转换为应用层命令
     *
     * @param request 权限点请求参数
     * @return 保存权限点命令
     */
    private SavePermissionCommand toCommand(PermissionRequest request) {
        return new SavePermissionCommand(request.getParentId(), request.getPermCode(), request.getPermName(),
                request.getPermType(), request.getPath(), request.getMethod(), request.getSortNo(),
                request.getStatus());
    }
}
